package com.example.plugins

import com.example.*
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.*
import io.ktor.server.http.content.files
import io.ktor.server.http.content.static
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jakarta.mail.Message.RecipientType
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.serialization.Serializable
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.time.Duration
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import io.ktor.http.content.*

// =============== 工具：本地 SHA-256（避免额外依赖） ===============
private fun sha256(s: String): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
private val EMAIL_REGEX =
    Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z0-9.-]+$")

private const val MAX_EMAIL_LENGTH = 254

private fun validateEmail(emailRaw: String): String? {
    val email = emailRaw.trim()
    if (email.isBlank()) return "邮箱不能为空"
    if (email.length > MAX_EMAIL_LENGTH) return "邮箱长度不能超过 $MAX_EMAIL_LENGTH 个字符"
    if (!EMAIL_REGEX.matches(email)) return "邮箱格式不正确"
    return null
}

private val ENGLISH_LETTER_REGEX = Regex("[A-Za-z]")
private val CHINESE_CHAR_REGEX = Regex("[\\p{IsHan}]")

private fun validatePasswordFormat(passwordRaw: String, fieldLabel: String = "密码"): String? {
    val password = passwordRaw.trim()
    if (!ENGLISH_LETTER_REGEX.containsMatchIn(password)) return "${fieldLabel}必须包含英文字母"
    if (CHINESE_CHAR_REGEX.containsMatchIn(password)) return "${fieldLabel}不能包含中文字符"
    return null
}

// ---------------------------------------------------------------------------------
// --- 1. 数据传输对象 (Data Transfer Objects - DTOs)
// ---------------------------------------------------------------------------------
@Serializable
data class JoinSpaceRequest(
    val userId: Int,
    val inviteCode: String,
    val nickname: String? = null // 新增字段
)
@Serializable
data class User(
    val uid: Int = 0,
    val email: String,
    val password: String, // 仅用于注册和登录时的接收，不应在常规API响应中返回
    val secQuestion: String = "",
    val secAnswerHash: String = "",
    val nickname: String = "",
    val gender: String = "Not specified",
    val avatarColor: String = "#CCCCCC",
    val relationshipStatus: String = "single",
    val partnerId: Int? = null
)

@Serializable
data class Space(
    val id: Int = 0,
    val name: String,
    val type: String,
    val description: String? = null,
    val creatorId: Int,
    val inviteCode: String,
    val coverColor: String = "#FF9800",
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "active",
    val checkInIntervalSeconds: Long = 0,
    // [MODIFIED] 移除 @Transient 注解，允许序列化这两个字段
    val userRole: String? = null,
    val userStatus: String? = null,
    val userMemberId: Int? = null  // [ADDED] space_members 表的 id
)

@Serializable
data class Couple(
    val id: Int = 0,
    val requesterId: Int,
    val partnerId: Int,
    val status: String = "pending", // "pending", "accepted", "rejected"
    val createdAt: Long,
    var requesterRemark: String? = null,
    var partnerRemark: String? = null
)

@Serializable
data class Friend(
    val id: Int = 0,
    val senderId: Int,
    val receiverId: Int,
    val status: String = "pending", // "pending", "accepted", "rejected"
    val senderRemark: String? = null,
    val receiverRemark: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Message(
    val id: Int = 0,
    val senderId: Int,
    val receiverId: Int,
    val content: String? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val audioDuration: Long? = null,
    val timestamp: Long,
    val isRead: Boolean = false
)

@Serializable
data class Anniversary(
    val id: Int = 0,
    val spaceId: Int,
    val name: String,
    val dateMillis: Long,
    val style: String = "simple",
    val creatorUserId: Int,
    val status: String = "pending", // "pending", "active"
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class CreateAnniversaryRequest(
    val spaceId: Int,
    val name: String,
    val dateMillis: Long,
    val style: String,
    val creatorUserId: Int
)

@Serializable
data class SpacePost(
    val id: Int = 0,
    val spaceId: Int,
    val authorId: Int,
    val content: String,
    val images: String? = null,
    val location: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val status: String = "normal" // "normal", "deleted", "system"
)

@Serializable
data class PostComment(
    val id: Int = 0,
    val postId: Int,
    val authorId: Int,
    val content: String,
    val replyToUserId: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "normal" // "normal", "deleted"
)

@Serializable
data class PostLike(
    val id: Int = 0,
    val postId: Int,
    val userId: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 动态提醒实体 - 用于 @提醒 功能
 *
 * 业务逻辑：
 * 1. 发布动态时，发布者可以选择提醒特定用户查看
 * 2. 被提醒用户可以选择"已读"（接受打卡）或"忽略"（拒绝打卡）
 * 3. 如果超过设定时间未响应，状态变为 expired
 *
 * 状态说明：
 * - pending: 待响应，用户尚未操作
 * - viewed: 已读，用户选择了"已读"表示接受打卡
 * - ignored: 忽略，用户选择了"忽略"表示拒绝打卡
 * - expired: 已超时，用户在规定时间内未响应
 */
@Serializable
data class PostMention(
    val id: Int = 0,
    val postId: Int,
    val mentionedUserId: Int,
    val mentionerUserId: Int,
    val timeoutSeconds: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val viewedAt: Long? = null,
    val lastNotifiedAt: Long? = null,
    val status: String = "pending" // pending, viewed, ignored, expired
)

/**
 * PostMention 的创建请求 DTO
 */
@Serializable
data class CreatePostMentionRequest(
    val postId: Int,
    val mentionedUserIds: List<Int>, // 可以批量@多人
    val mentionerUserId: Int,
    val timeoutSeconds: Long
)

/**
 * PostMention 的更新超时时间请求 DTO
 */
@Serializable
data class UpdateMentionTimeoutRequest(
    val timeoutSeconds: Long
)

@Serializable
data class SpaceMember(
    val id: Int = 0,
    val spaceId: Int,
    val userId: Int,
    val role: String = "member", // "owner", "admin", "member"
    val nickname: String? = null,
    val joinedAt: Long = System.currentTimeMillis(),
    val status: String = "active" // "active", "pending", "left"
)

@Serializable
data class SpacePetAttributes(
    val mood: Int = 50,
    val health: Int = 80,
    val energy: Int = 60,
    val hydration: Int = 60,
    val intimacy: Int = 50
)

@Serializable
data class SpacePet(
    val id: Int = 0,
    val spaceId: Int,
    val name: String,
    val type: String = "pet",
    val attributes: SpacePetAttributes,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SpacePetRequest(
    val name: String? = null,
    val type: String? = null,
    val attributes: SpacePetAttributes
)

private fun ResultSet.toSpacePet(): SpacePet = SpacePet(
    id = getInt("id"),
    spaceId = getInt("spaceId"),
    name = getString("name"),
    type = getString("type"),
    attributes = SpacePetAttributes(
        mood = getInt("mood"),
        health = getInt("health"),
        energy = getInt("energy"),
        hydration = getInt("hydration"),
        intimacy = getInt("intimacy")
    ),
    updatedAt = getLong("updatedAt")
)
@Serializable
data class CreateSpaceRequest(
    val name: String,
    val type: String,
    val description: String? = null,
    val creatorId: Int,
    val partnerId: Int? = null, // [新增] 用于情侣空间
    val coverColor: String = "#FF9800"
)
@Serializable
data class GenericResponse(val success: Boolean, val message: String)

// --- 为特定请求创建的DTO ---
@Serializable
data class LoginRequest(val email: String, val password: String, val emailCode: String? = null)

@Serializable
data class ResetQuestionRequest(val email: String)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val answer: String? = null,
    val newPassword: String,
    val confirmPassword: String? = null,
    val newEmail: String? = null,
    val emailCode: String? = null,
    val method: String? = null
)

@Serializable
data class SecurityQuestionResponse(val question: String, val status: String = STATUS_USE_SECURITY_QUESTION)

@Serializable
data class LikeRequest(val userId: Int)

// === 个人中心 / 登录页修改用 ===
@Serializable
data class UpdatePasswordRequest(
    val email: String,
    val oldPassword: String,
    val securityAnswer: String? = null,
    val newPassword: String,
    val emailCode: String? = null
)

@Serializable
data class UpdateSecurityQuestionRequest(
    val email: String,
    val password: String,
    val question: String,
    val answer: String,
    val emailCode: String? = null
)

// ---------------------------------------------------------------------------------
// --- 1.1 安全状态码与邮箱验证码风控引擎 ---------------------------------------------
// ---------------------------------------------------------------------------------

private const val STATUS_NEED_EMAIL_VERIFICATION = "NEED_EMAIL_VERIFICATION"
private const val STATUS_USE_SECURITY_QUESTION = "USE_SECURITY_QUESTION"
private const val STATUS_INVALID_CODE = "INVALID_CODE"
private const val STATUS_CODE_EXPIRED = "CODE_EXPIRED"
private const val STATUS_CODE_REQUIRED = "CODE_REQUIRED"
private const val STATUS_TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS"

enum class VerificationOperation { LOGIN, CHANGE_PASSWORD, CHANGE_SECURITY_QUESTION, RESET_PASSWORD }

private fun parseVerificationOperation(raw: String?): VerificationOperation? {
    val key = raw?.trim()?.uppercase() ?: return null
    return when (key) {
        "LOGIN" -> VerificationOperation.LOGIN
        "CHANGE_PASSWORD", "UPDATE_PASSWORD" -> VerificationOperation.CHANGE_PASSWORD
        "CHANGE_SECURITY_QUESTION", "UPDATE_SECURITY_QUESTION" -> VerificationOperation.CHANGE_SECURITY_QUESTION
        "RESET_PASSWORD" -> VerificationOperation.RESET_PASSWORD
        else -> null
    }
}

@Serializable
data class EmailCodeRequest(val email: String, val operation: String)

@Serializable
data class VerificationHint(
    val success: Boolean,
    val status: String,
    val message: String,
    val operation: String,
    val expiresInSeconds: Long? = null,
    val cooldownSeconds: Long? = null,
    val attemptsLeft: Int? = null
)

private data class EmailCodeEntry(
    val code: String,
    val operation: VerificationOperation,
    var expiresAt: Long,
    var attemptsLeft: Int,
    var used: Boolean = false,
    var lastSentAt: Long
)

private interface Mailer {
    fun sendCode(to: String, code: String, operation: VerificationOperation)
}

private class SmtpMailer(
    private val username: String,
    private val appPassword: String,
    private val host: String,
    private val port: String,
    private val logger: org.slf4j.Logger
) : Mailer {
    override fun sendCode(to: String, code: String, operation: VerificationOperation) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", host)
            put("mail.smtp.port", port)
        }

        val session = Session.getInstance(props, object : jakarta.mail.Authenticator() {
            override fun getPasswordAuthentication(): jakarta.mail.PasswordAuthentication {
                return jakarta.mail.PasswordAuthentication(username, appPassword)
            }
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(username))
            setRecipients(RecipientType.TO, InternetAddress.parse(to))
            subject = "LoveHome 安全验证码"
            setText(
                "操作: ${operation.name}\n验证码: $code\n10分钟内有效，请勿泄露。"
            )
        }

        Transport.send(message)
        logger.info("验证码已发送至 $to，操作：${operation.name}")
    }
}

private class EmailVerificationManager(
    private val mailer: Mailer,
    private val logger: org.slf4j.Logger
) {
    private val codes = ConcurrentHashMap<String, EmailCodeEntry>()
    private val codeTtl = Duration.ofMinutes(10).toMillis()
    private val resendCooldown = Duration.ofSeconds(60).toMillis()
    private val maxAttempts = 5

    private fun key(email: String, op: VerificationOperation) = "${email.lowercase()}:${op.name}"

    fun requestCode(email: String, op: VerificationOperation): VerificationHint {
        val now = System.currentTimeMillis()
        val k = key(email, op)
        val entry = codes[k]
        if (entry != null && !entry.used && entry.expiresAt > now && now - entry.lastSentAt < resendCooldown) {
            val retryAfter = (resendCooldown - (now - entry.lastSentAt)) / 1000
            return VerificationHint(false, STATUS_TOO_MANY_REQUESTS, "请稍后再请求验证码", op.name, cooldownSeconds = retryAfter)
        }

        val code = Random.nextInt(100000, 999999).toString()
        val newEntry = EmailCodeEntry(code, op, now + codeTtl, maxAttempts, false, now)
        codes[k] = newEntry

        try {
            mailer.sendCode(email, code, op)
        } catch (ex: Exception) {
            logger.error("发送邮件验证码失败：${ex.message}", ex)
            return VerificationHint(false, STATUS_TOO_MANY_REQUESTS, "发送验证码失败，请稍后再试", op.name)
        }

        return VerificationHint(true, STATUS_NEED_EMAIL_VERIFICATION, "已发送验证码", op.name, expiresInSeconds = codeTtl / 1000, cooldownSeconds = resendCooldown / 1000)
    }

    fun verify(email: String, op: VerificationOperation, provided: String?): VerificationHint {
        val now = System.currentTimeMillis()
        val k = key(email, op)
        val entry = codes[k] ?: return VerificationHint(false, STATUS_CODE_REQUIRED, "需要先获取验证码", op.name)

        if (entry.used) return VerificationHint(false, STATUS_INVALID_CODE, "验证码已被使用", op.name, attemptsLeft = entry.attemptsLeft)
        if (entry.expiresAt < now) {
            codes.remove(k)
            return VerificationHint(false, STATUS_CODE_EXPIRED, "验证码已过期", op.name)
        }
        if (entry.attemptsLeft <= 0) return VerificationHint(false, STATUS_INVALID_CODE, "尝试次数过多，请重新获取", op.name, attemptsLeft = 0)
        if (provided.isNullOrBlank()) return VerificationHint(false, STATUS_CODE_REQUIRED, "请输入验证码", op.name, attemptsLeft = entry.attemptsLeft)

        return if (provided.trim() == entry.code) {
            entry.used = true
            codes.remove(k)
            VerificationHint(true, "OK", "验证通过", op.name)
        } else {
            entry.attemptsLeft -= 1
            VerificationHint(false, STATUS_INVALID_CODE, "验证码错误", op.name, attemptsLeft = entry.attemptsLeft)
        }
    }
}

private data class RiskProfile(
    var lastLoginAt: Long = System.currentTimeMillis(),
    var lastLoginIp: String? = null
)

private class RiskEngine {
    private val profiles = ConcurrentHashMap<String, RiskProfile>()
    private val inactivityThreshold = Duration.ofDays(7).toMillis()

    fun recordLogin(email: String, ip: String?) {
        val profile = profiles.getOrPut(email.lowercase()) { RiskProfile() }
        profile.lastLoginAt = System.currentTimeMillis()
        profile.lastLoginIp = ip
    }

    fun needEmailVerification(email: String, ip: String?, op: VerificationOperation): Boolean {
        val profile = profiles[email.lowercase()]
        // 登录场景：首次无画像时不强制验证码，避免阻塞初次登录；其他操作保持谨慎
        if (profile == null) return op != VerificationOperation.LOGIN
        val longInactive = System.currentTimeMillis() - profile.lastLoginAt > inactivityThreshold
        val ipChanged = !profile.lastLoginIp.isNullOrBlank() && ip != null && profile.lastLoginIp != ip
        return longInactive || ipChanged
    }
}

// ---------------------------------------------------------------------------------
// --- 2. 主路由配置
// ---------------------------------------------------------------------------------

fun Application.configureRouting() {

    val logger = this.log
    val smtpUser = System.getenv("EMAIL_SMTP_USER") ?: "1326319051@qq.com"
    val smtpAppPassword = System.getenv("EMAIL_SMTP_PASSWORD") ?: ""
    val smtpHost = System.getenv("EMAIL_SMTP_HOST") ?: "smtp.qq.com"
    val smtpPort = System.getenv("EMAIL_SMTP_PORT") ?: "587"
    val isDev = (System.getenv("ENV") ?: "").equals("DEV", ignoreCase = true)

    val mailer: Mailer = if (smtpAppPassword.isNotBlank()) {
        SmtpMailer(smtpUser, smtpAppPassword, smtpHost, smtpPort, logger)
    } else {
        object : Mailer {
            override fun sendCode(to: String, code: String, operation: VerificationOperation) {
                logger.warn("未配置 SMTP 凭据，已跳过向 $to 发送 ${operation.name} 验证码")
            }
        }
    }

    val verificationManager = EmailVerificationManager(mailer, logger)
    val riskEngine = RiskEngine()

    fun statusForHint(hint: VerificationHint): HttpStatusCode = when (hint.status) {
        "OK" -> HttpStatusCode.OK
        STATUS_INVALID_CODE -> HttpStatusCode.BadRequest
        STATUS_TOO_MANY_REQUESTS -> HttpStatusCode.TooManyRequests
        STATUS_NEED_EMAIL_VERIFICATION, STATUS_CODE_REQUIRED, STATUS_CODE_EXPIRED -> HttpStatusCode.Forbidden
        else -> HttpStatusCode.BadRequest
    }

    // --- 数据库连接 ---
    val dbHost = "127.0.0.1"
    val dbPort = 3306
    val dbName = "perfect"
    val dbUser = "appuser"
    val dbPassword = "Zcw205306" // 【重要】建议从配置文件读取

    val jdbcUrl = "jdbc:mysql://$dbHost:$dbPort/$dbName?user=$dbUser&password=$dbPassword&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    var connection: Connection? = null

    try {
        Class.forName("com.mysql.cj.jdbc.Driver")
        connection = DriverManager.getConnection(jdbcUrl)
        log.info(">>>>>> 数据库连接成功！<<<<<<")
    } catch (e: Exception) {
        log.error("!!!!!! 数据库连接失败！错误：${e.message} !!!!!!")
        e.printStackTrace()
    }

    // 确保连接可用；若被数据库空闲断开则自动重连
    fun ensureConnection(): Connection {
        synchronized(this) {
            if (connection == null || connection!!.isClosed) {
                connection = DriverManager.getConnection(jdbcUrl)
                log.info(">>>>>> 数据库连接已重新建立。<<<<<<")
            }
            return connection!!
        }
    }

    environment.monitor.subscribe(ApplicationStopping) {
        connection?.close()
        log.info(">>>>>> 数据库连接已正常关闭。<<<<<<")
    }

    // --- 数据库操作辅助函数 ---
    fun <T> executeQuery(sql: String, setup: java.sql.PreparedStatement.() -> Unit, mapper: (ResultSet) -> T): List<T> {
        val list = mutableListOf<T>()
        val conn = ensureConnection()
        conn.prepareStatement(sql).use { statement ->
            statement.setup()
            statement.executeQuery().use { rs -> while (rs.next()) { list.add(mapper(rs)) } }
        }
        return list
    }

    fun executeUpdate(sql: String, setup: java.sql.PreparedStatement.() -> Unit): Int {
        val conn = ensureConnection()
        conn.prepareStatement(sql).use { statement ->
            statement.setup()
            return statement.executeUpdate()
        }
    }

    fun executeInsert(sql: String, setup: java.sql.PreparedStatement.() -> Unit): Int {
        val conn = ensureConnection()
        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setup()
            statement.executeUpdate()
            statement.generatedKeys.use { if (it.next()) return it.getInt(1) }
        }
        return -1
    }

    // --- 路由定义 ---
    routing {
        get("/") { call.respondText("欢迎使用 HearHome 应用后端！") }

        post("/upload/image") {
            try {
                val multipart = call.receiveMultipart()
                var imageUrl: String? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val originalFileName = part.originalFileName ?: "image_${System.currentTimeMillis()}.jpg"
                        val fileName = "${System.currentTimeMillis()}_$originalFileName"

                        val uploadDir = File("uploads")
                        if (!uploadDir.exists()) uploadDir.mkdirs()

                        val file = File(uploadDir, fileName)

                        part.streamProvider().use { input ->
                            file.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val baseUrl = "http://${call.request.host()}:${call.request.port()}"
                        imageUrl = "$baseUrl/uploads/$fileName"
                    }
                    part.dispose()
                }

                if (imageUrl == null)
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "未找到文件"))
                else
                    call.respond(mapOf("imageUrl" to imageUrl))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        post("/upload/audio") {
            try {
                val multipart = call.receiveMultipart()
                var audioUrl: String? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val originalFileName = part.originalFileName ?: "audio_${System.currentTimeMillis()}.m4a"
                        val fileName = "${System.currentTimeMillis()}_$originalFileName"

                        val uploadDir = File("uploads")
                        if (!uploadDir.exists()) uploadDir.mkdirs()

                        val file = File(uploadDir, fileName)

                        part.streamProvider().use { input ->
                            file.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val baseUrl = "http://${call.request.host()}:${call.request.port()}"
                        audioUrl = "$baseUrl/uploads/$fileName"
                    }
                    part.dispose()
                }

                if (audioUrl == null)
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "未找到文件"))
                else
                    call.respond(mapOf("audioUrl" to audioUrl))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        // 静态资源访问
        static("/uploads") {
            files("uploads")
        }

        // 兼容前端调用的安全邮箱验证码接口（security/email-code/...）
        route("/security/email-code") {
            post("/request") {
                val req = call.receive<EmailCodeRequest>()
                val op = parseVerificationOperation(req.operation)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "未知的操作类型"))

                val hint = verificationManager.requestCode(req.email.trim(), op)
                val status = if (hint.success) HttpStatusCode.OK else statusForHint(hint)
                call.respond(status, hint)
            }

            post("/verify") {
                val body = call.receive<Map<String, String>>()
                val email = body["email"]?.trim().orEmpty()
                val operation = body["purpose"]?.trim()
                val code = body["code"]?.trim().orEmpty()

                if (email.isBlank() || operation.isNullOrBlank() || code.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "邮箱、操作或验证码不能为空"))
                }

                val op = parseVerificationOperation(operation)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "未知的操作类型"))

                val hint = verificationManager.verify(email, op, code)
                val status = statusForHint(hint)
                call.respond(status, hint)
            }
        }
        // --- User & Auth Routes ---
        route("/users") {
            post("/email-code/request") {
                val req = call.receive<EmailCodeRequest>()
                val op = parseVerificationOperation(req.operation)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "未知的操作类型"))

                val hint = verificationManager.requestCode(req.email.trim(), op)
                val status = if (hint.success) HttpStatusCode.OK else HttpStatusCode.TooManyRequests
                call.respond(status, hint)
            }

            post("/register") {
                val user = call.receive<User>()

                // ① 先做邮箱格式 + 长度校验，防止脏数据入库
                val emailError = validateEmail(user.email)
                if (emailError != null) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        GenericResponse(false, emailError)
                    )
                }

                val passwordError = validatePasswordFormat(user.password)
                if (passwordError != null) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        GenericResponse(false, passwordError)
                    )
                }

                val sanitizedPassword = user.password.trim()

                try {
                    // ② 再做邮箱是否已注册检查（保留你原有 SQL）
                    val existing = executeQuery(
                        "SELECT uid FROM users WHERE email = ?",
                        {
                            setString(1, user.email.trim())
                        }
                    ) { rs ->
                        rs.getInt("uid")
                    }.firstOrNull()

                    if (existing != null) {
                        // 建议这里一起改成中文提示
                        return@post call.respond(
                            HttpStatusCode.Conflict,
                            GenericResponse(false, "该邮箱已注册")
                        )
                    }

                    // ③ 保留你原来密保答案哈希逻辑
                    val answerHash =
                        if (user.secAnswerHash.matches(Regex("^[0-9a-fA-F]{64}$")))
                            user.secAnswerHash
                        else
                            sha256(user.secAnswerHash)

                    // ④ 保留原来的插入 users 语句（这里示例，用你自己的 SQL 替换）
                    val newId = executeInsert(
                        """
            INSERT INTO users (email, password, nickname, gender, avatarColor, relationshipStatus, secQuestion, secAnswerHash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
                        {
                            setString(1, user.email.trim())
                            setString(2, sanitizedPassword)          // 实际上你可能还有密码加密逻辑
                            setString(3, user.nickname)
                            setString(4, user.gender ?: "single")
                            setString(5, user.avatarColor ?: "#FFB6C1")
                            setString(6, user.relationshipStatus ?: "single")
                            setString(7, user.secQuestion)
                            setString(8, answerHash)
                        }
                    )

                    if (newId != -1) {
                        // 返回时清空密码字段，避免明文泄露
                        call.respond(
                            HttpStatusCode.Created,
                            user.copy(uid = newId, password = "")
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            GenericResponse(false, "创建用户失败")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        GenericResponse(false, e.message ?: "注册失败")
                    )
                }
            }


            get("/profile/{id}") {
                val userId = call.parameters["id"]?.toIntOrNull()
                if (userId == null) {
                    return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的用户ID"))
                }
                try {
                    val user = executeQuery(
                        "SELECT uid, email, nickname, gender, avatarColor, relationshipStatus, partnerId FROM users WHERE uid = ?",
                        { setInt(1, userId) }
                    ) { rs ->
                        User(
                            uid = rs.getInt("uid"),
                            email = rs.getString("email"),
                            password = "",
                            nickname = rs.getString("nickname"),
                            gender = rs.getString("gender"),
                            avatarColor = rs.getString("avatarColor"),
                            relationshipStatus = rs.getString("relationshipStatus"),
                            partnerId = rs.getInt("partnerId").takeIf { !rs.wasNull() }
                        )
                    }.firstOrNull()

                    if (user != null) call.respond(user) else call.respond(HttpStatusCode.NotFound, GenericResponse(false, "未找到用户"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString()))
                }
            }

            post("/login") {
                val req = call.receive<LoginRequest>()
                try {
                    val user = executeQuery("SELECT * FROM users WHERE email = ? AND password = ?", { setString(1, req.email); setString(2, req.password) }) { rs ->
                        User(rs.getInt("uid"), rs.getString("email"), "", rs.getString("secQuestion"), "", rs.getString("nickname"), rs.getString("gender"), rs.getString("avatarColor"), rs.getString("relationshipStatus"), rs.getInt("partnerId").takeIf { !rs.wasNull() })
                    }.firstOrNull()
                    if (user == null) return@post call.respond(HttpStatusCode.Unauthorized, GenericResponse(false, "邮箱或密码不正确。"))

                    val needEmailCheck = riskEngine.needEmailVerification(user.email, call.request.local.remoteHost, VerificationOperation.LOGIN)
                    if (needEmailCheck) {
                        val hint = if (req.emailCode.isNullOrBlank()) {
                            verificationManager.requestCode(user.email, VerificationOperation.LOGIN)
                        } else {
                            verificationManager.verify(user.email, VerificationOperation.LOGIN, req.emailCode)
                        }
                        if (!hint.success || hint.status != "OK") {
                            return@post call.respond(statusForHint(hint), hint)
                        }
                    }

                    riskEngine.recordLogin(user.email, call.request.local.remoteHost)
                    call.respond(user)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // ===== 找回密码：步骤1 取密保问题 =====
            post("/reset-question") {
                try {
                    val req = call.receive<ResetQuestionRequest>()
                    val question = executeQuery(
                        "SELECT secQuestion FROM users WHERE email = ?",
                        { setString(1, req.email.trim()) }
                    ) { rs -> rs.getString("secQuestion") }
                        .firstOrNull()
                        ?: return@post call.respond(HttpStatusCode.NotFound, GenericResponse(false, "该邮箱未注册"))

                    if (question.isNullOrBlank()) {
                        return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "该账号未设置密保问题"))
                    }
                    call.respond(SecurityQuestionResponse(question = question))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "服务器错误"))
                }
            }

            // ===== 找回密码：步骤2 校验答案并重置 =====
            post("/reset-password") {
                try {
                    val req = call.receive<ResetPasswordRequest>()
                    val email = req.email.trim()
                    if (email.isBlank()) {
                        return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "邮箱不能为空"))
                    }
                    if (!req.newPassword.isNullOrBlank() && !req.confirmPassword.isNullOrBlank() && req.confirmPassword != req.newPassword) {
                        return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "两次输入的密码不一致"))
                    }

                    val method = (req.method ?: "EMAIL").uppercase()
                    if (method == "SECURITY_QUESTION") {
                        val row = executeQuery(
                            "SELECT secAnswerHash, secQuestion FROM users WHERE email = ?",
                            { setString(1, email) }
                        ) { rs -> rs.getString("secAnswerHash") to (rs.getString("secQuestion") ?: "") }
                            .firstOrNull()
                            ?: return@post call.respond(HttpStatusCode.NotFound, GenericResponse(false, "该邮箱未注册"))

                        val (hashInDb, question) = row
                        if (hashInDb.isNullOrBlank() || question.isBlank()) {
                            return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "账户未设置密保问题"))
                        }

                        if (req.answer.isNullOrBlank()) {
                            return@post call.respond(HttpStatusCode.OK, SecurityQuestionResponse(question))
                        }

                        val ok = sha256(req.answer.trim()) == hashInDb || req.answer.trim() == hashInDb
                        if (!ok) return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "密保答案不正确"))

                        val targetEmail = req.newEmail?.trim().takeUnless { it.isNullOrBlank() } ?: email
                        if (targetEmail != email) {
                            val exists = executeQuery(
                                "SELECT 1 FROM users WHERE email = ? LIMIT 1",
                                { setString(1, targetEmail) }
                            ) { 1 }.isNotEmpty()
                            if (exists) return@post call.respond(HttpStatusCode.Conflict, GenericResponse(false, "新邮箱已被占用"))
                        }

                        // 若仅需提前验证答案（新密码未提供），直接返回成功提示，避免用户到下一步才发现错误
                        if (req.newPassword.isNullOrBlank()) {
                            return@post call.respond(HttpStatusCode.OK, GenericResponse(true, "密保答案正确，可继续设置新密码"))
                        }

                        val sanitizedPassword = req.newPassword.trim()
                        val newPasswordError = validatePasswordFormat(sanitizedPassword, "新密码")
                        if (newPasswordError != null) {
                            return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, newPasswordError))
                        }
                        val updated = executeUpdate(
                            "UPDATE users SET password = ?, email = ? WHERE email = ?",
                            {
                                setString(1, sanitizedPassword)
                                setString(2, targetEmail)
                                setString(3, email)
                            }
                        )
                        if (updated > 0) call.respond(HttpStatusCode.OK, GenericResponse(true, "密码已重置，请使用新邮箱/新密码登录"))
                        else call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "密码重置失败"))
                    } else {
                        val hint = if (req.emailCode.isNullOrBlank()) {
                            verificationManager.requestCode(email, VerificationOperation.RESET_PASSWORD)
                        } else {
                            verificationManager.verify(email, VerificationOperation.RESET_PASSWORD, req.emailCode)
                        }

                        if (!hint.success || hint.status != "OK") {
                            return@post call.respond(statusForHint(hint), hint)
                        }

                        val sanitizedPassword = req.newPassword.trim()
                        val newPasswordError = validatePasswordFormat(sanitizedPassword, "新密码")
                        if (newPasswordError != null) {
                            return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, newPasswordError))
                        }
                        val updated = executeUpdate(
                            "UPDATE users SET password = ? WHERE email = ?",
                            { setString(1, sanitizedPassword); setString(2, email) }
                        )
                        if (updated > 0) call.respond(HttpStatusCode.OK, GenericResponse(true, "密码已重置"))
                        else call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "密码重置失败"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "服务器错误"))
                }
            }

            // 兼容前端找回密码获取验证码的旧路径
            post("/reset-password/code") {
                val body = call.receive<Map<String, String>>()
                val email = body["email"]?.trim().orEmpty()
                if (email.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "邮箱不能为空"))
                val hint = verificationManager.requestCode(email, VerificationOperation.RESET_PASSWORD)
                val status = if (hint.success) HttpStatusCode.OK else statusForHint(hint)
                call.respond(status, hint)
            }

            // 额外兼容路径，避免 404（部分前端可能调用 send-code）
            post("/reset-password/send-code") {
                val body = call.receive<Map<String, String>>()
                val email = body["email"]?.trim().orEmpty()
                if (email.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "邮箱不能为空"))
                val hint = verificationManager.requestCode(email, VerificationOperation.RESET_PASSWORD)
                val status = if (hint.success) HttpStatusCode.OK else statusForHint(hint)
                call.respond(status, hint)
            }

            // ===== 个人中心：修改密码 =====
            post("/update-password") {
                val req: UpdatePasswordRequest = when {
                    call.request.contentType().match(ContentType.Application.Json) -> call.receive()
                    else -> {
                        val p = call.receiveParameters()
                        UpdatePasswordRequest(
                            email = p["email"] ?: "",
                            oldPassword = p["oldPassword"] ?: "",
                            securityAnswer = p["securityAnswer"],
                            newPassword = p["newPassword"] ?: "",
                            emailCode = p["emailCode"]
                        )
                    }
                }

                try {
                    val email = req.email.trim()
                    if (email.isBlank() || req.oldPassword.isBlank() || req.newPassword.isBlank()) {
                        return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "邮箱、旧密码、新密码不能为空"))
                    }

                    val sanitizedPassword = req.newPassword.trim()
                    val newPasswordError = validatePasswordFormat(sanitizedPassword, "新密码")
                    if (newPasswordError != null) {
                        return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, newPasswordError))
                    }

                    val row = executeQuery(
                        "SELECT password, secAnswerHash FROM users WHERE email = ?",
                        { setString(1, email) }
                    ) { rs -> rs.getString("password") to (rs.getString("secAnswerHash") ?: "") }
                        .firstOrNull()
                        ?: return@post call.respond(HttpStatusCode.NotFound, GenericResponse(false, "未找到用户"))

                    val (dbPwd, dbAnsHash) = row
                    if (dbPwd != req.oldPassword) return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "旧密码不正确"))

                    // 安全问题不再作为必填；如提供则校验
                    val answerProvided = !req.securityAnswer.isNullOrBlank()
                    if (answerProvided && dbAnsHash.isNotBlank()) {
                        val ansOk = (sha256(req.securityAnswer.trim()) == dbAnsHash) || (req.securityAnswer.trim() == dbAnsHash)
                        if (!ansOk) return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "安全问题回答错误"))
                    }

                    val op = VerificationOperation.CHANGE_PASSWORD
                    val forceRisk = isDev && call.request.headers["X-Force-Risk"]?.lowercase() == "true"
                    val needEmailCheck = forceRisk || riskEngine.needEmailVerification(email, call.request.local.remoteHost, op)
                    if (needEmailCheck) {
                        val hint = if (req.emailCode.isNullOrBlank()) {
                            verificationManager.requestCode(email, op)
                        } else {
                            verificationManager.verify(email, op, req.emailCode)
                        }

                        if (!hint.success || hint.status != "OK") {
                            return@post call.respond(statusForHint(hint), hint)
                        }
                    }

                    val updated = executeUpdate(
                        "UPDATE users SET password = ? WHERE email = ?",
                        { setString(1, sanitizedPassword); setString(2, email) }
                    )
                    if (updated > 0) call.respond(HttpStatusCode.OK, GenericResponse(true, "密码更新成功"))
                    else call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "密码更新失败"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "服务器错误"))
                }
            }

            // ===== 个人中心：设置/修改密保 =====
            post("/update-security-question") {
                val req: UpdateSecurityQuestionRequest = when {
                    call.request.contentType().match(ContentType.Application.Json) -> call.receive()
                    else -> {
                        val p = call.receiveParameters()
                        UpdateSecurityQuestionRequest(
                            email = p["email"] ?: "",
                            password = p["password"] ?: "",
                            question = p["question"] ?: "",
                            answer = p["answer"] ?: "",
                            emailCode = p["emailCode"]
                        )
                    }
                }

                try {
                    val email = req.email.trim()
                    if (email.isBlank() || req.password.isBlank() || req.question.isBlank() || req.answer.isBlank()) {
                        return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "密码、问题和答案均不能为空"))
                    }

                    val dbPwd = executeQuery(
                        "SELECT password FROM users WHERE email = ?",
                        { setString(1, email) }
                    ) { rs -> rs.getString("password") }
                        .firstOrNull()
                        ?: return@post call.respond(HttpStatusCode.NotFound, GenericResponse(false, "未找到用户"))

                    if (dbPwd != req.password) return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "当前密码不正确"))

                    val op = VerificationOperation.CHANGE_SECURITY_QUESTION
                    val forceRisk = isDev && call.request.headers["X-Force-Risk"]?.lowercase() == "true"
                    val needEmailCheck = forceRisk || riskEngine.needEmailVerification(email, call.request.local.remoteHost, op)
                    if (needEmailCheck) {
                        val hint = if (req.emailCode.isNullOrBlank()) {
                            verificationManager.requestCode(email, op)
                        } else {
                            verificationManager.verify(email, op, req.emailCode)
                        }
                        if (!hint.success || hint.status != "OK") {
                            return@post call.respond(statusForHint(hint), hint)
                        }
                    }

                    val updated = executeUpdate(
                        "UPDATE users SET secQuestion = ?, secAnswerHash = ? WHERE email = ?",
                        {
                            setString(1, req.question.trim())
                            setString(2, sha256(req.answer.trim()))
                            setString(3, email)
                        }
                    )
                    if (updated > 0) call.respond(HttpStatusCode.OK, GenericResponse(true, "私密问题更新成功"))
                    else call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "私密问题更新失败"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "服务器错误"))
                }
            }
            // PUT /users/avatar/{id} - 更新用户头像样式
            put("/avatar/{id}") {
                @Serializable
                data class UpdateAvatarRequest(val avatarColor: String)

                val userId = call.parameters["id"]?.toIntOrNull()
                if (userId == null) {
                    return@put call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "Invalid user ID"))
                }

                try {
                    val req = call.receive<UpdateAvatarRequest>()

                    // 验证头像数据格式
                    // 支持的格式：
                    // - 纯色：#RRGGBB
                    // - 渐变：gradient:#RRGGBB:#RRGGBB
                    // - 马赛克：mosaic:#RRGGBB
                    // - 波点：dots:#RRGGBB
                    // - 条纹：stripes:#RRGGBB
                    // - 菱形：diamond:#RRGGBB
                    val validPrefixes = listOf("gradient:", "mosaic:", "dots:", "stripes:", "diamond:")
                    val isValidFormat = req.avatarColor.startsWith("#") ||
                            validPrefixes.any { prefix ->
                                req.avatarColor.startsWith(prefix) &&
                                        req.avatarColor.removePrefix(prefix).split(":").all {
                                            it.startsWith("#") && it.length in listOf(4, 7, 9)
                                        }
                            }

                    if (!isValidFormat) {
                        return@put call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "Invalid avatar format"))
                    }

                    val updatedRows = executeUpdate(
                        "UPDATE users SET avatarColor = ? WHERE uid = ?",
                        { setString(1, req.avatarColor); setInt(2, userId) }
                    )

                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "Avatar updated successfully"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "User not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString()))
                }
            }
        }

        // --- Space Routes ---
        route("/space") {
            get {
                val userId = call.request.queryParameters["userId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "缺少 userId 查询参数"))
                try {
                    // [MODIFIED] The SQL query now also fetches the user's role, status and member id for each space.
                    val sql = """
                        SELECT s.*, sm.id as userMemberId, sm.role as userRole, sm.status as userStatus 
                        FROM spaces s 
                        JOIN space_members sm ON s.id = sm.spaceId 
                        WHERE sm.userId = ? AND s.status = 'active'
                    """
                    val spaces = executeQuery(sql, { setInt(1, userId) }) { rs ->
                        Space(
                            id = rs.getInt("id"),
                            name = rs.getString("name"),
                            type = rs.getString("type"),
                            description = rs.getString("description"),
                            creatorId = rs.getInt("creatorId"),
                            inviteCode = rs.getString("inviteCode"),
                            coverColor = rs.getString("coverColor"),
                            createdAt = rs.getLong("createdAt"),
                            status = rs.getString("status"),
                            checkInIntervalSeconds = rs.getLong("checkInIntervalSeconds"),
                            // [ADDED] Populate the new dynamic fields
                            userMemberId = rs.getInt("userMemberId"),
                            userRole = rs.getString("userRole"),
                            userStatus = rs.getString("userStatus")
                        )
                    }
                    call.respond(spaces)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "数据库错误")) }
            }

            post {
                val request = call.receive<CreateSpaceRequest>()
                try {
                    var inviteCode: String
                    do {
                        inviteCode = Random.nextInt(100000, 999999).toString()
                        val codeExists = executeQuery("SELECT id FROM spaces WHERE inviteCode = ?", { setString(1, inviteCode) }) { it.getInt(1) }.isNotEmpty()
                    } while (codeExists)

                    // --- 核心逻辑：根据空间类型处理 ---
                    if (request.type == "couple") {
                        val partnerId = request.partnerId ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "创建情侣空间必须指定伴侣ID"))
                        val creatorId = request.creatorId

                        // 检查双方是否已在其他情侣空间
                        val creatorInSpace = executeQuery("SELECT s.id FROM spaces s JOIN space_members sm ON s.id = sm.spaceId WHERE sm.userId = ? AND s.type = 'couple' AND s.status = 'active'", { setInt(1, creatorId) }) { it.getInt(1) }.firstOrNull()
                        if (creatorInSpace != null) return@post call.respond(HttpStatusCode.Conflict, GenericResponse(false, "您已在情侣空间中"))

                        val partnerInSpace = executeQuery("SELECT s.id FROM spaces s JOIN space_members sm ON s.id = sm.spaceId WHERE sm.userId = ? AND s.type = 'couple' AND s.status = 'active'", { setInt(1, partnerId) }) { it.getInt(1) }.firstOrNull()
                        if (partnerInSpace != null) return@post call.respond(HttpStatusCode.Conflict, GenericResponse(false, "对方已在情侣空间中"))

                        // 开始事务
                        connection?.autoCommit = false

                        // 1. 创建空间
                        val newSpaceId = executeInsert("INSERT INTO spaces (name, type, description, creatorId, inviteCode, coverColor, createdAt, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", {
                            setString(1, request.name); setString(2, request.type); setString(3, request.description); setInt(4, creatorId); setString(5, inviteCode); setString(6, request.coverColor); setLong(7, System.currentTimeMillis()); setString(8, "active")
                        })

                        if (newSpaceId == -1) {
                            connection?.rollback()
                            return@post call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "创建空间失败"))
                        }

                        // 2. 查询双方昵称
                        val creatorNickname = executeQuery("SELECT nickname FROM users WHERE uid = ?", { setInt(1, creatorId) }) { it.getString("nickname") }.firstOrNull() ?: "用户$creatorId"
                        val partnerNickname = executeQuery("SELECT nickname FROM users WHERE uid = ?", { setInt(1, partnerId) }) { it.getString("nickname") }.firstOrNull() ?: "用户$partnerId"

                        // 3. 添加双方为成员
                        executeUpdate("INSERT INTO space_members (spaceId, userId, role, status, joinedAt, nickname) VALUES (?, ?, 'owner', 'active', ?, ?)", {
                            setInt(1, newSpaceId); setInt(2, creatorId); setLong(3, System.currentTimeMillis()); setString(4, creatorNickname)
                        })
                        executeUpdate("INSERT INTO space_members (spaceId, userId, role, status, joinedAt, nickname) VALUES (?, ?, 'owner', 'active', ?, ?)", {
                            setInt(1, newSpaceId); setInt(2, partnerId); setLong(3, System.currentTimeMillis()); setString(4, partnerNickname)
                        })

                        // 4. 更新双方用户表中的情侣关系状态
                        executeUpdate("UPDATE users SET relationshipStatus = 'in_relationship', partnerId = ? WHERE uid = ?", {
                            setInt(1, partnerId); setInt(2, creatorId)
                        })
                        executeUpdate("UPDATE users SET relationshipStatus = 'in_relationship', partnerId = ? WHERE uid = ?", {
                            setInt(1, creatorId); setInt(2, partnerId)
                        })

                        // 5. (可选) 清理旧的或冲突的 couple 表记录 - 这里可以根据你的业务逻辑添加

                        connection?.commit()

                        val newSpace = Space(id = newSpaceId, name = request.name, type = request.type, description = request.description, creatorId = request.creatorId, inviteCode = inviteCode, coverColor = request.coverColor)
                        call.respond(HttpStatusCode.Created, newSpace)

                    } else { // family 或其他类型的空间
                        val newSpaceId = executeInsert("INSERT INTO spaces (name, type, description, creatorId, inviteCode, coverColor, createdAt, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", {
                            setString(1, request.name); setString(2, request.type); setString(3, request.description); setInt(4, request.creatorId); setString(5, inviteCode); setString(6, request.coverColor); setLong(7, System.currentTimeMillis()); setString(8, "active")
                        })

                        if (newSpaceId != -1) {
                            // 查询创建者昵称
                            val creatorNickname = executeQuery("SELECT nickname FROM users WHERE uid = ?", { setInt(1, request.creatorId) }) { it.getString("nickname") }.firstOrNull() ?: "用户${request.creatorId}"
                            executeUpdate("INSERT INTO space_members (spaceId, userId, role, status, joinedAt, nickname) VALUES (?, ?, 'owner', 'active', ?, ?)", {
                                setInt(1, newSpaceId); setInt(2, request.creatorId); setLong(3, System.currentTimeMillis()); setString(4, creatorNickname)
                            })
                            val newSpace = Space(id = newSpaceId, name = request.name, type = request.type, description = request.description, creatorId = request.creatorId, inviteCode = inviteCode, coverColor = request.coverColor)
                            call.respond(HttpStatusCode.Created, newSpace)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "创建空间失败"))
                        }
                    }
                } catch (e: Exception) {
                    connection?.rollback()
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "未知错误"))
                } finally {
                    connection?.autoCommit = true
                }
            }

            post("/join") {
                val request = call.receive<JoinSpaceRequest>()

                try {
                    // 1. 验证邀请码是否存在且空间是活跃的
                    val space = executeQuery(
                        "SELECT * FROM spaces WHERE inviteCode = ? AND status = 'active' LIMIT 1",
                        { setString(1, request.inviteCode) }
                    ) { rs ->
                        Space(rs.getInt("id"), rs.getString("name"), rs.getString("type"), rs.getString("description"), rs.getInt("creatorId"), rs.getString("inviteCode"), rs.getString("coverColor"), rs.getLong("createdAt"), rs.getString("status"), rs.getLong("checkInIntervalSeconds"))
                    }.firstOrNull()

                    if (space == null) {
                        return@post call.respond(HttpStatusCode.NotFound, GenericResponse(false, "邀请码无效或空间已关闭"))
                    }

                    // 2. 检查用户是否已经是该空间的成员
                    val existingMember = executeQuery(
                        "SELECT * FROM space_members WHERE spaceId = ? AND userId = ? LIMIT 1",
                        { setInt(1, space.id); setInt(2, request.userId) }
                    ) { rs ->
                        SpaceMember(
                            id = rs.getInt("id"),
                            spaceId = rs.getInt("spaceId"),
                            userId = rs.getInt("userId"),
                            role = rs.getString("role"),
                            nickname = rs.getString("nickname"),
                            joinedAt = rs.getLong("joinedAt"),
                            status = rs.getString("status")
                        )
                    }.firstOrNull()

                    if (existingMember != null) {
                        val spaceWithUser = space.copy(
                            userRole = existingMember.role,
                            userStatus = existingMember.status,
                            userMemberId = existingMember.id
                        )
                        return@post call.respond(HttpStatusCode.OK, spaceWithUser)
                    }

                    // 3. 根据空间类型处理加入逻辑
                    val userNickname = request.nickname ?: executeQuery("SELECT nickname FROM users WHERE uid = ?", { setInt(1, request.userId) }) { it.getString("nickname") }.firstOrNull() ?: "鐢ㄦ埛${request.userId}"
                    var role = "member"
                    var status = "active"
                    var memberId = -1

                    when (space.type) {
                        "couple" -> {
                            // 检查情侣空间是否已满（服务器端核心逻辑）
                            val memberCount = executeQuery("SELECT COUNT(*) FROM space_members WHERE spaceId = ? AND status = 'active'", { setInt(1, space.id) }) { it.getInt(1) }.first()
                            if (memberCount >= 2) {
                                return@post call.respond(HttpStatusCode.Conflict, GenericResponse(false, "该情侣空间已满员"))
                            }
                            // 对于情侣空间，通常需要更复杂的逻辑，比如检查双方关系，这里简化为直接加入
                            role = "owner"
                            status = "active"
                            memberId = executeInsert("INSERT INTO space_members (spaceId, userId, role, status, joinedAt, nickname) VALUES (?, ?, 'owner', 'active', ?, ?)", {
                                setInt(1, space.id); setInt(2, request.userId); setLong(3, System.currentTimeMillis()); setString(4, userNickname)
                            })
                        }
                        "family" -> {
                            // 家族空间需要管理员审核
                            role = "member"
                            status = "pending"
                            memberId = executeInsert("INSERT INTO space_members (spaceId, userId, role, status, joinedAt, nickname) VALUES (?, ?, 'member', 'pending', ?, ?)", {
                                setInt(1, space.id); setInt(2, request.userId); setLong(3, System.currentTimeMillis()); setString(4, userNickname)
                            })
                        }
                        else -> {
                            // 其他类型的空间，默认直接加入
                            role = "member"
                            status = "active"
                            memberId = executeInsert("INSERT INTO space_members (spaceId, userId, role, status, joinedAt, nickname) VALUES (?, ?, 'member', 'active', ?, ?)", {
                                setInt(1, space.id); setInt(2, request.userId); setLong(3, System.currentTimeMillis()); setString(4, userNickname)
                            })
                        }
                    }

                    // 4. 成功加入，返回加入的空间信息
                    val spaceWithUser = space.copy(
                        userRole = role,
                        userStatus = status,
                        userMemberId = memberId
                    )
                    call.respond(HttpStatusCode.OK, spaceWithUser)

                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "处理加入请求时发生未知错误"))
                }
            }

            // --- 获取空间成员列表 ---
            get("/{spaceId}/members") {
                val spaceId = call.parameters["spaceId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的空间ID"))

                try {
                    val members = executeQuery(
                        "SELECT * FROM space_members WHERE spaceId = ?",
                        { setInt(1, spaceId) }
                    ) { rs ->
                        SpaceMember(
                            id = rs.getInt("id"),
                            spaceId = rs.getInt("spaceId"),
                            userId = rs.getInt("userId"),
                            role = rs.getString("role"),
                            nickname = rs.getString("nickname"),
                            joinedAt = rs.getLong("joinedAt"),
                            status = rs.getString("status")
                        )
                    }
                    call.respond(HttpStatusCode.OK, members)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "获取成员列表失败: ${e.message}"))
                }
            }

            // --- Space Member Management Routes ---
            post("/members/approve/{memberId}") {
                val memberId = call.parameters["memberId"]?.toIntOrNull()
                if (memberId == null) {
                    call.respond(HttpStatusCode.BadRequest, "无效的成员ID")
                    return@post
                }

                try {
                    log.info("approveMember called with memberId=$memberId")

                    val rowsAffected = executeUpdate(
                        "UPDATE space_members SET status = 'active' WHERE id = ?",
                        { setInt(1, memberId) }
                    )

                    log.info("UPDATE executed, rows affected: $rowsAffected")

                    if (rowsAffected > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "成员审核通过"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "未找到成员"))
                    }
                } catch (e: Exception) {
                    log.error("approveMember exception: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "成员审批失败：${e.message}"))
                }
            }

            post("/members/reject/{memberId}") {
                val memberId = call.parameters["memberId"]?.toIntOrNull()
                if (memberId == null) {
                    call.respond(HttpStatusCode.BadRequest, "无效的成员ID")
                    return@post
                }

                try {
                    log.info("rejectMember called with memberId=$memberId")

                    val rowsAffected = executeUpdate(
                        "DELETE FROM space_members WHERE id = ?",
                        { setInt(1, memberId) }
                    )

                    log.info("DELETE executed, rows affected: $rowsAffected")

                    if (rowsAffected > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "成员已拒绝"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "未找到成员"))
                    }
                } catch (e: Exception) {
                    log.error("rejectMember exception: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "成员拒绝失败：${e.message}"))
                }
            }
            delete("/members/{memberId}") {
                val memberId = call.parameters["memberId"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的成员ID"))
                try {
                    // 删除成员记录
                    val deletedRows = executeUpdate("DELETE FROM space_members WHERE id = ?", { setInt(1, memberId) })
                    if (deletedRows > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "成员已移除"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "成员不存在"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "移除失败"))
                }
            }

            post("/leave/{spaceId}") {
                val spaceId = call.parameters["spaceId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的空间ID"))
                val req = call.receive<Map<String, Int>>()
                val userId = req["userId"] ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "缺少 userId"))

                try {
                    // 删除用户的成员记录
                    val deletedRows = executeUpdate("DELETE FROM space_members WHERE spaceId = ? AND userId = ?", {
                        setInt(1, spaceId)
                        setInt(2, userId)
                    })
                    if (deletedRows > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "已离开空间"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "您不是该空间的成员"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "离开空间失败"))
                }
            }

            // --- 打卡间隔设置 ---
            // 更新空间的打卡间隔时间
            put("/{spaceId}/checkin-interval") {
                val spaceId = call.parameters["spaceId"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的空间ID"))
                val req = call.receive<Map<String, Long>>()
                val checkInIntervalSeconds = req["checkInIntervalSeconds"] ?: return@put call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "缺少 checkInIntervalSeconds"))

                try {
                    val updatedRows = executeUpdate(
                        "UPDATE spaces SET checkInIntervalSeconds = ? WHERE id = ?",
                        { setLong(1, checkInIntervalSeconds); setInt(2, spaceId) }
                    )
                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "打卡间隔已更新"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "空间不存在"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "更新失败"))
                }
            }

            // 获取空间的打卡间隔时间
            get("/{spaceId}/checkin-interval") {
                val spaceId = call.parameters["spaceId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的空间ID"))

                try {
                    val interval = executeQuery(
                        "SELECT checkInIntervalSeconds FROM spaces WHERE id = ?",
                        { setInt(1, spaceId) }
                    ) { it.getLong("checkInIntervalSeconds") }.firstOrNull()

                    if (interval != null) {
                        call.respond(mapOf("checkInIntervalSeconds" to interval))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "空间不存在"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "查询失败"))
                }
            }

            route("/{spaceId}/pet") {
                get {
                    val spaceId = call.parameters["spaceId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的空间ID"))
                    try {
                        val pet = executeQuery(
                            "SELECT * FROM space_pets WHERE spaceId = ?",
                            { setInt(1, spaceId) }
                        ) { it.toSpacePet() }.firstOrNull()

                        if (pet != null) {
                            call.respond(pet)
                        } else {
                            call.respond(HttpStatusCode.NotFound, GenericResponse(false, "该空间暂无宠物数据"))
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "查询宠物信息失败"))
                    }
                }

                post {
                    val spaceId = call.parameters["spaceId"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的空间ID"))
                    val request = call.receive<SpacePetRequest>()
                    try {
                        val existsSpace = executeQuery(
                            "SELECT 1 FROM spaces WHERE id = ? AND status = 'active'",
                            { setInt(1, spaceId) }
                        ) { it.getInt(1) }.isNotEmpty()
                        if (!existsSpace) {
                            return@post call.respond(HttpStatusCode.NotFound, GenericResponse(false, "空间不存在"))
                        }

                        val now = System.currentTimeMillis()
                        val resolvedName = request.name?.takeIf { it.isNotBlank() } ?: "萌宠"
                        val resolvedType = request.type?.takeIf { it.isNotBlank() } ?: "pet"
                        val attributes = request.attributes

                        val existingPet = executeQuery(
                            "SELECT * FROM space_pets WHERE spaceId = ?",
                            { setInt(1, spaceId) }
                        ) { it.toSpacePet() }.firstOrNull()

                        if (existingPet == null) {
                            val newId = executeInsert(
                                "INSERT INTO space_pets (spaceId, name, type, mood, health, energy, hydration, intimacy, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                {
                                    setInt(1, spaceId);
                                    setString(2, resolvedName);
                                    setString(3, resolvedType);
                                    setInt(4, attributes.mood);
                                    setInt(5, attributes.health);
                                    setInt(6, attributes.energy);
                                    setInt(7, attributes.hydration);
                                    setInt(8, attributes.intimacy);
                                    setLong(9, now)
                                }
                            )

                            val created = SpacePet(
                                id = newId,
                                spaceId = spaceId,
                                name = resolvedName,
                                type = resolvedType,
                                attributes = attributes,
                                updatedAt = now
                            )
                            call.respond(HttpStatusCode.Created, created)
                        } else {
                            executeUpdate(
                                "UPDATE space_pets SET name = ?, type = ?, mood = ?, health = ?, energy = ?, hydration = ?, intimacy = ?, updatedAt = ? WHERE id = ?",
                                {
                                    setString(1, resolvedName);
                                    setString(2, resolvedType);
                                    setInt(3, attributes.mood);
                                    setInt(4, attributes.health);
                                    setInt(5, attributes.energy);
                                    setInt(6, attributes.hydration);
                                    setInt(7, attributes.intimacy);
                                    setLong(8, now);
                                    setInt(9, existingPet.id)
                                }
                            )
                            val updated = existingPet.copy(
                                name = resolvedName,
                                type = resolvedType,
                                attributes = attributes,
                                updatedAt = now
                            )
                            call.respond(HttpStatusCode.OK, updated)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "保存宠物信息失败"))
                    }
                }
            }
            // --- 解散空间 --------------------------------------------------------------------------------------------wdz
            delete("/{spaceId}") {
                val spaceId = call.parameters["spaceId"]?.toIntOrNull()
                    ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        GenericResponse(false, "无效的空间ID")
                    )

                // 目前没有登录态，只能靠前端把当前用户 ID 传过来
                val body = call.receive<Map<String, Int>>()
                val requesterId = body["userId"]
                    ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        GenericResponse(false, "缺少 userId")
                    )

                try {
                    // 1. 确认空间存在并且是 active
                    val spaceInfo = executeQuery(
                        "SELECT type FROM spaces WHERE id = ? AND status = 'active'",
                        { setInt(1, spaceId) }
                    ) { rs ->
                        rs.getString("type")
                    }.firstOrNull()
                        ?: return@delete call.respond(
                            HttpStatusCode.NotFound,
                            GenericResponse(false, "空间不存在或已被解散")
                        )

                    val spaceType = spaceInfo

                    // 2. 检查请求者在该空间的角色（必须是 owner 或 admin）
                    val role = executeQuery(
                        "SELECT role FROM space_members WHERE spaceId = ? AND userId = ? AND status = 'active' LIMIT 1",
                        {
                            setInt(1, spaceId)
                            setInt(2, requesterId)
                        }
                    ) { rs -> rs.getString("role") }.firstOrNull()
                        ?: return@delete call.respond(
                            HttpStatusCode.Forbidden,
                            GenericResponse(false, "您不是该空间的成员")
                        )

                    if (role != "owner" && role != "admin") {
                        return@delete call.respond(
                            HttpStatusCode.Forbidden,
                            GenericResponse(false, "只有空间所有者或管理员可以解散空间")
                        )
                    }

                    // 3. 解散空间：这里推荐软删除（标记为 archived），避免误删数据
                    val rows = executeUpdate(
                        "UPDATE spaces SET status = 'archived' WHERE id = ?",
                        { setInt(1, spaceId) }
                    )

                    if (rows <= 0) {
                        return@delete call.respond(
                            HttpStatusCode.InternalServerError,
                            GenericResponse(false, "解散空间失败")
                        )
                    }

                    // 4. 将所有成员标记为 left（如果你想直接删掉 space_members 也可以）
                    executeUpdate(
                        "UPDATE space_members SET status = 'left' WHERE spaceId = ?",
                        { setInt(1, spaceId) }
                    )

                    // 如果是情侣空间，还可以顺带处理情侣关系：
                    if (spaceType == "couple") {
                        // 按你自己的 couples 表结构处理，这里只是示例
                        executeUpdate(
                            "UPDATE couples SET status = 'breakup' WHERE spaceId = ?",
                            { setInt(1, spaceId) }
                        )
                    }

                    call.respond(HttpStatusCode.OK, GenericResponse(true, "空间已解散"))

                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        GenericResponse(false, e.message ?: "解散空间失败")
                    )
                }
            }
            // --- 邀请好友加入空间（仅 owner/admin）--------------------------------------------------------------wdz
            post("/{spaceId}/invite") {
                val spaceId = call.parameters["spaceId"]?.toIntOrNull()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        GenericResponse(false, "无效的空间ID")
                    )

                val body = call.receive<Map<String, Int>>()
                val inviterId = body["inviterId"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        GenericResponse(false, "缺少 inviterId")
                    )
                val friendId = body["friendId"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        GenericResponse(false, "缺少 friendId")
                    )

                try {
                    // 1. 确认空间存在且有效
                    val spaceType = executeQuery(
                        "SELECT type FROM spaces WHERE id = ? AND status = 'active'",
                        { setInt(1, spaceId) }
                    ) { rs -> rs.getString("type") }.firstOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            GenericResponse(false, "空间不存在或已被解散")
                        )

                    if (spaceType == "couple") {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            GenericResponse(false, "情侣空间不能邀请额外成员")
                        )
                    }

                    // 2. 检查 inviter 是否是该空间的 owner/admin
                    val inviterRole = executeQuery(
                        "SELECT role FROM space_members WHERE spaceId = ? AND userId = ? AND status = 'active' LIMIT 1",
                        {
                            setInt(1, spaceId)
                            setInt(2, inviterId)
                        }
                    ) { rs -> rs.getString("role") }.firstOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.Forbidden,
                            GenericResponse(false, "您不是该空间的成员")
                        )

                    if (inviterRole != "owner" && inviterRole != "admin") {
                        return@post call.respond(
                            HttpStatusCode.Forbidden,
                            GenericResponse(false, "只有空间所有者或管理员可以邀请成员")
                        )
                    }

                    // 3. 检查好友是否已经在空间里
                    val existingStatus = executeQuery(
                        "SELECT status FROM space_members WHERE spaceId = ? AND userId = ? LIMIT 1",
                        {
                            setInt(1, spaceId)
                            setInt(2, friendId)
                        }
                    ) { rs -> rs.getString("status") }.firstOrNull()

                    if (existingStatus == "active") {
                        return@post call.respond(
                            HttpStatusCode.Conflict,
                            GenericResponse(false, "该用户已经在空间中")
                        )
                    }
                    if (existingStatus == "pending") {
                        return@post call.respond(
                            HttpStatusCode.OK,
                            GenericResponse(true, "已向该成员发送过加入请求")
                        )
                    }

                    // 4. 查好友的昵称
                    // 4. 查好友的昵称（修正字段名 uid）
                    val nickname = executeQuery(
                        "SELECT nickname FROM users WHERE uid = ?",
                        { setInt(1, friendId) }
                    ) { rs -> rs.getString("nickname") }.firstOrNull() ?: "用户$friendId"


                    // 5. 直接加入（active），不走审核流程
                    executeUpdate(
                        "INSERT INTO space_members (spaceId, userId, role, status, joinedAt, nickname) VALUES (?, ?, 'member', 'active', ?, ?)",
                        {
                            setInt(1, spaceId)
                            setInt(2, friendId)
                            setLong(3, System.currentTimeMillis())
                            setString(4, nickname)
                        }
                    )

                    call.respond(HttpStatusCode.OK, GenericResponse(true, "已成功邀请好友加入空间"))

                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        GenericResponse(false, e.message ?: "邀请成员失败")
                    )
                }
            }
            // --- 结束--------------------------------------------------------------------------------------------wdz
            // 获取空间详情（包含打卡间隔）
            get("/{spaceId}") {
                val spaceId = call.parameters["spaceId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的空间ID"))

                try {
                    val space = executeQuery(
                        "SELECT * FROM spaces WHERE id = ?",
                        { setInt(1, spaceId) }
                    ) { rs ->
                        Space(
                            id = rs.getInt("id"),
                            name = rs.getString("name"),
                            type = rs.getString("type"),
                            description = rs.getString("description"),
                            creatorId = rs.getInt("creatorId"),
                            inviteCode = rs.getString("inviteCode"),
                            coverColor = rs.getString("coverColor"),
                            createdAt = rs.getLong("createdAt"),
                            status = rs.getString("status"),
                            checkInIntervalSeconds = rs.getLong("checkInIntervalSeconds")
                        )
                    }.firstOrNull()

                    if (space != null) {
                        call.respond(space)
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "空间不存在"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message ?: "查询失败"))
                }
            }
        }

        // --- Friend Routes ---
        route("/friends") {
            get("/all/{userId}") {
                val userId = call.parameters["userId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                try {
                    val friends = executeQuery("SELECT * FROM friends WHERE (senderId = ? OR receiverId = ?) AND status = 'accepted'", { setInt(1, userId); setInt(2, userId) }) { rs -> Friend(rs.getInt("id"), rs.getInt("senderId"), rs.getInt("receiverId"), rs.getString("status"), rs.getString("senderRemark"), rs.getString("receiverRemark"), rs.getLong("createdAt")) }
                    call.respond(friends)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
            get("/pending/{userId}") {
                val userId = call.parameters["userId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                try {
                    val requests = executeQuery("SELECT * FROM friends WHERE receiverId = ? AND status = 'pending'", { setInt(1, userId) }) { rs -> Friend(rs.getInt("id"), rs.getInt("senderId"), rs.getInt("receiverId"), rs.getString("status"), createdAt = rs.getLong("createdAt")) }
                    call.respond(requests)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
            post("/request") {
                val req = call.receive<Map<String, Int>>(); val senderId = req["senderId"]; val receiverId = req["receiverId"]; if (senderId == null || receiverId == null) return@post call.respond(HttpStatusCode.BadRequest)
                try { executeInsert("INSERT INTO friends (senderId, receiverId, status, createdAt) VALUES (?, ?, 'pending', ?)", { setInt(1, senderId); setInt(2, receiverId); setLong(3, System.currentTimeMillis()) }); call.respond(HttpStatusCode.Created, GenericResponse(true, "好友请求已发送。"))
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
            post("/accept/{requestId}") {
                val requestId = call.parameters["requestId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                try { executeUpdate("UPDATE friends SET status = 'accepted' WHERE id = ?", { setInt(1, requestId) }); call.respond(HttpStatusCode.OK, GenericResponse(true, "好友请求已接受。"))
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
            post("/reject/{requestId}") {
                val requestId = call.parameters["requestId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                try { executeUpdate("UPDATE friends SET status = 'rejected' WHERE id = ?", { setInt(1, requestId) }); call.respond(HttpStatusCode.OK, GenericResponse(true, "好友请求已拒绝。"))
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            delete("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                try {
                    val deletedRows = executeUpdate("DELETE FROM friends WHERE id = ?", { setInt(1, id) })
                    if (deletedRows > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "好友已删除。"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "未找到好友关系。"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString()))
                }
            }
        }

        // --- Couple Routes ---
        route("/couples") {
            get("/{userId}") {
                val userId = call.parameters["userId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                try {
                    val couple = executeQuery("SELECT * FROM couples WHERE (requesterId = ? OR partnerId = ?) AND status = 'accepted'", { setInt(1, userId); setInt(2, userId) }) { rs -> Couple(rs.getInt("id"), rs.getInt("requesterId"), rs.getInt("partnerId"), rs.getString("status"), rs.getLong("createdAt")) }.firstOrNull()
                    if (couple != null) call.respond(couple) else call.respond(HttpStatusCode.NotFound)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
            get("/requests/{userId}") {
                val userId = call.parameters["userId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                try { val requests = executeQuery("SELECT * FROM couples WHERE partnerId = ? AND status = 'pending'", { setInt(1, userId) }) { rs -> Couple(rs.getInt("id"), rs.getInt("requesterId"), rs.getInt("partnerId"), rs.getString("status"), rs.getLong("createdAt")) }; call.respond(requests)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
            post("/request") {
                val req = call.receive<Map<String, Int>>(); val requesterId = req["requesterId"]; val partnerId = req["partnerId"]; if (requesterId == null || partnerId == null) return@post call.respond(HttpStatusCode.BadRequest)
                try { executeInsert("INSERT INTO couples (requesterId, partnerId, status, createdAt) VALUES (?, ?, 'pending', ?)", { setInt(1, requesterId); setInt(2, partnerId); setLong(3, System.currentTimeMillis()) }); call.respond(HttpStatusCode.Created, GenericResponse(true, "情侣请求已发送。"))
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
            post("/accept/{requestId}") {
                val requestId = call.parameters["requestId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                try {
                    connection?.autoCommit = false

                    // 1. 更新情侣请求状态
                    executeUpdate("UPDATE couples SET status = 'accepted' WHERE id = ?", { setInt(1, requestId) })

                    // 2. 获取双方 ID
                    val couple = executeQuery("SELECT requesterId, partnerId FROM couples WHERE id = ?", { setInt(1, requestId) }) { rs ->
                        rs.getInt("requesterId") to rs.getInt("partnerId")
                    }.first()
                    val requesterId = couple.first
                    val partnerId = couple.second

                    // 3. 更新双方用户的情侣状态
                    executeUpdate("UPDATE users SET relationshipStatus = 'in_relationship', partnerId = ? WHERE uid = ?", {
                        setInt(1, partnerId); setInt(2, requesterId)
                    })
                    executeUpdate("UPDATE users SET relationshipStatus = 'in_relationship', partnerId = ? WHERE uid = ?", {
                        setInt(1, requesterId); setInt(2, partnerId)
                    })

                    // 4. 检查是否已有情侣空间
                    val existingCoupleSpace = executeQuery(
                        """SELECT s.id FROM spaces s
                           JOIN space_members sm1 ON s.id = sm1.spaceId AND sm1.userId = ?
                           JOIN space_members sm2 ON s.id = sm2.spaceId AND sm2.userId = ?
                           WHERE s.type = 'couple' AND s.status = 'active' LIMIT 1""",
                        { setInt(1, requesterId); setInt(2, partnerId) }
                    ) { it.getInt("id") }.firstOrNull()

                    if (existingCoupleSpace == null) {
                        // 5. 自动创建情侣空间
                        var inviteCode: String
                        do {
                            inviteCode = kotlin.random.Random.nextInt(100000, 999999).toString()
                            val codeExists = executeQuery("SELECT id FROM spaces WHERE inviteCode = ?", { setString(1, inviteCode) }) { it.getInt(1) }.isNotEmpty()
                        } while (codeExists)

                        // 获取双方昵称
                        val requesterNickname = executeQuery("SELECT nickname FROM users WHERE uid = ?", { setInt(1, requesterId) }) { it.getString("nickname") }.firstOrNull() ?: "用户$requesterId"
                        val partnerNickname = executeQuery("SELECT nickname FROM users WHERE uid = ?", { setInt(1, partnerId) }) { it.getString("nickname") }.firstOrNull() ?: "用户$partnerId"

                        // 创建空间
                        val spaceName = "${requesterNickname}和${partnerNickname}的情侣空间"
                        val newSpaceId = executeInsert(
                            "INSERT INTO spaces (name, type, description, creatorId, inviteCode, coverColor, createdAt, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            {
                                setString(1, spaceName)
                                setString(2, "couple")
                                setString(3, "由情侣关系自动创建")
                                setInt(4, requesterId)
                                setString(5, inviteCode)
                                setString(6, "#FF69B4") // 粉色
                                setLong(7, System.currentTimeMillis())
                                setString(8, "active")
                            }
                        )

                        if (newSpaceId != -1) {
                            // 添加双方为空间成员
                            executeUpdate(
                                "INSERT INTO space_members (spaceId, userId, role, status, joinedAt, nickname) VALUES (?, ?, 'owner', 'active', ?, ?)",
                                { setInt(1, newSpaceId); setInt(2, requesterId); setLong(3, System.currentTimeMillis()); setString(4, requesterNickname) }
                            )
                            executeUpdate(
                                "INSERT INTO space_members (spaceId, userId, role, status, joinedAt, nickname) VALUES (?, ?, 'owner', 'active', ?, ?)",
                                { setInt(1, newSpaceId); setInt(2, partnerId); setLong(3, System.currentTimeMillis()); setString(4, partnerNickname) }
                            )
                            log.info("Auto-created couple space $newSpaceId for users $requesterId and $partnerId")
                        }
                    }

                    connection?.commit()
                    call.respond(HttpStatusCode.OK, GenericResponse(true, "情侣请求已接受。"))
                } catch (e: Exception) {
                    connection?.rollback()
                    log.error("接受情侣请求失败：${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString()))
                } finally {
                    connection?.autoCommit = true
                }
            }
            post("/reject/{requestId}") {
                val requestId = call.parameters["requestId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                try { executeUpdate("UPDATE couples SET status = 'rejected' WHERE id = ?", { setInt(1, requestId) }); call.respond(HttpStatusCode.OK, GenericResponse(true, "情侣请求已拒绝。"))
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
            delete("/{userId}") {
                val userId = call.parameters["userId"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                try {
                    connection?.autoCommit = false
                    val couple = executeQuery("SELECT id, requesterId, partnerId FROM couples WHERE (requesterId = ? OR partnerId = ?) AND status = 'accepted'", { setInt(1, userId); setInt(2, userId) }) { rs -> Triple(rs.getInt("id"), rs.getInt("requesterId"), rs.getInt("partnerId")) }.firstOrNull()

                    if (couple != null) {
                        executeUpdate("DELETE FROM couples WHERE id = ?", { setInt(1, couple.first) })
                        executeUpdate("UPDATE users SET relationshipStatus = 'single', partnerId = NULL WHERE uid = ?", { setInt(1, couple.second) })
                        executeUpdate("UPDATE users SET relationshipStatus = 'single', partnerId = NULL WHERE uid = ?", { setInt(1, couple.third) })
                        connection?.commit()
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "关系已解除。"))
                    } else { connection?.rollback(); call.respond(HttpStatusCode.NotFound, GenericResponse(false, "未找到有效的关系。")) }
                } catch (e: Exception) { connection?.rollback(); call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString()))
                } finally { connection?.autoCommit = true }
            }
        }

        // --- Post & Comment Routes ---
        route("/posts") {
            get("/{spaceId}") {
                val spaceId = call.parameters["spaceId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                try {
                    val posts = executeQuery(
                        "SELECT * FROM space_posts WHERE spaceId = ? AND status IN ('normal','system') ORDER BY timestamp DESC",
                        { setInt(1, spaceId) }
                    ) { rs ->
                        SpacePost(
                            id = rs.getInt("id"),
                            spaceId = rs.getInt("spaceId"),
                            authorId = rs.getInt("authorId"),
                            content = rs.getString("content"),
                            images = rs.getString("images"),
                            location = rs.getString("location"),
                            timestamp = rs.getLong("timestamp"),
                            likeCount = rs.getInt("likeCount"),
                            commentCount = rs.getInt("commentCount"),
                            status = rs.getString("status")
                        )
                    }
                    call.respond(posts)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
            post {
                val post = call.receive<SpacePost>()
                try {
                    val now = System.currentTimeMillis()
                    val normalizedStatus = post.status.ifBlank { "normal" }
                    val newId = executeInsert(
                        "INSERT INTO space_posts (spaceId, authorId, content, images, location, status, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        {
                            setInt(1, post.spaceId)
                            setInt(2, post.authorId)
                            setString(3, post.content)
                            setString(4, post.images)
                            setString(5, post.location)
                            setString(6, normalizedStatus)
                            setLong(7, now)
                        }
                    )
                    if (newId != -1) {
                        call.respond(
                            HttpStatusCode.Created,
                            post.copy(
                                id = newId,
                                status = normalizedStatus,
                                timestamp = now
                            )
                        )
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "发布动态失败。"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString()))
                }
            }
            post("/like/{postId}") {
                val postId = call.parameters["postId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<LikeRequest>()
                try {
                    connection?.autoCommit = false
                    val existingLike = executeQuery("SELECT id FROM post_likes WHERE postId = ? AND userId = ?", { setInt(1, postId); setInt(2, req.userId) }) { it.getInt("id") }.firstOrNull()

                    if (existingLike != null) {
                        executeUpdate("DELETE FROM post_likes WHERE id = ?", { setInt(1, existingLike) })
                        executeUpdate("UPDATE space_posts SET likeCount = likeCount - 1 WHERE id = ? AND likeCount > 0", { setInt(1, postId) })
                    } else {
                        executeInsert("INSERT INTO post_likes (postId, userId, timestamp) VALUES (?, ?, ?)", { setInt(1, postId); setInt(2, req.userId); setLong(3, System.currentTimeMillis()) })
                        executeUpdate("UPDATE space_posts SET likeCount = likeCount + 1 WHERE id = ?", { setInt(1, postId) })
                    }
                    connection?.commit()
                    call.respond(HttpStatusCode.OK, GenericResponse(true, "点赞状态已更新。"))
                } catch (e: Exception) {
                    connection?.rollback()
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString()))
                } finally { connection?.autoCommit = true }
            }
            get("/comments/{postId}") {
                val postId = call.parameters["postId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                try {
                    val comments = executeQuery(
                        "SELECT * FROM post_comments WHERE postId = ? AND (status IS NULL OR TRIM(status) <> 'deleted') ORDER BY timestamp ASC",
                        { setInt(1, postId) }
                    ) { rs ->
                        val normalizedStatus = rs.getString("status")?.takeIf { it.isNotBlank() } ?: "normal"
                        PostComment(
                            rs.getInt("id"),
                            rs.getInt("postId"),
                            rs.getInt("authorId"),
                            rs.getString("content"),
                            rs.getInt("replyToUserId").takeIf { !rs.wasNull() },
                            rs.getLong("timestamp"),
                            normalizedStatus
                        )
                    }
                    call.respond(comments)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
            post("/comment") {
                val comment = call.receive<PostComment>()
                try {
                    connection?.autoCommit = false
                    val now = System.currentTimeMillis()
                    val normalizedStatus = comment.status.ifBlank { "normal" }

                    val newId = executeInsert(
                        "INSERT INTO post_comments (postId, authorId, content, replyToUserId, timestamp, status) VALUES (?, ?, ?, ?, ?, ?)",
                        {
                            setInt(1, comment.postId)
                            setInt(2, comment.authorId)
                            setString(3, comment.content)
                            if (comment.replyToUserId != null) setInt(4, comment.replyToUserId) else setNull(4, java.sql.Types.INTEGER)
                            setLong(5, now)
                            setString(6, normalizedStatus)
                        }
                    )

                    if (newId != -1) {
                        executeUpdate("UPDATE space_posts SET commentCount = commentCount + 1 WHERE id = ?", { setInt(1, comment.postId) })
                        connection?.commit()
                        call.respond(HttpStatusCode.Created, comment.copy(id = newId, timestamp = now, status = normalizedStatus))
                    } else {
                        connection?.rollback()
                        call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "发布评论失败。"))
                    }
                } catch (e: Exception) {
                    connection?.rollback()
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString()))
                } finally { connection?.autoCommit = true }
            }
        }

        // --- Post Mention (@提醒) Routes ---
        route("/mentions") {
            // 创建提醒 - 可批量创建
            post {
                val request = call.receive<CreatePostMentionRequest>()
                try {
                    val currentTime = System.currentTimeMillis()
                    val createdMentions = mutableListOf<PostMention>()

                    connection?.autoCommit = false

                    for (userId in request.mentionedUserIds) {
                        // 检查是否已存在相同的提醒
                        val existing = executeQuery(
                            "SELECT id FROM post_mentions WHERE postId = ? AND mentionedUserId = ?",
                            { setInt(1, request.postId); setInt(2, userId) }
                        ) { it.getInt("id") }.firstOrNull()

                        if (existing == null) {
                            val newId = executeInsert(
                                "INSERT INTO post_mentions (postId, mentionedUserId, mentionerUserId, timeoutSeconds, createdAt, status) VALUES (?, ?, ?, ?, ?, ?)",
                                {
                                    setInt(1, request.postId)
                                    setInt(2, userId)
                                    setInt(3, request.mentionerUserId)
                                    setLong(4, request.timeoutSeconds)
                                    setLong(5, currentTime)
                                    setString(6, "pending")
                                }
                            )
                            if (newId != -1) {
                                createdMentions.add(PostMention(
                                    id = newId,
                                    postId = request.postId,
                                    mentionedUserId = userId,
                                    mentionerUserId = request.mentionerUserId,
                                    timeoutSeconds = request.timeoutSeconds,
                                    createdAt = currentTime
                                ))
                            }
                        }
                    }

                    connection?.commit()
                    call.respond(HttpStatusCode.Created, createdMentions)
                } catch (e: Exception) {
                    connection?.rollback()
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString()))
                } finally { connection?.autoCommit = true }
            }

            // 获取用户收到的所有提醒
            get("/user/{userId}") {
                val userId = call.parameters["userId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的用户ID"))
                try {
                    val mentions = executeQuery(
                        "SELECT * FROM post_mentions WHERE mentionedUserId = ? ORDER BY createdAt DESC",
                        { setInt(1, userId) }
                    ) { rs ->
                        PostMention(
                            id = rs.getInt("id"),
                            postId = rs.getInt("postId"),
                            mentionedUserId = rs.getInt("mentionedUserId"),
                            mentionerUserId = rs.getInt("mentionerUserId"),
                            timeoutSeconds = rs.getLong("timeoutSeconds"),
                            createdAt = rs.getLong("createdAt"),
                            viewedAt = rs.getLong("viewedAt").takeIf { !rs.wasNull() },
                            lastNotifiedAt = rs.getLong("lastNotifiedAt").takeIf { !rs.wasNull() },
                            status = rs.getString("status")
                        )
                    }
                    call.respond(mentions)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 获取用户的待处理提醒
            get("/user/{userId}/pending") {
                val userId = call.parameters["userId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的用户ID"))
                try {
                    val mentions = executeQuery(
                        "SELECT * FROM post_mentions WHERE mentionedUserId = ? AND status = 'pending' ORDER BY createdAt DESC",
                        { setInt(1, userId) }
                    ) { rs ->
                        PostMention(
                            id = rs.getInt("id"),
                            postId = rs.getInt("postId"),
                            mentionedUserId = rs.getInt("mentionedUserId"),
                            mentionerUserId = rs.getInt("mentionerUserId"),
                            timeoutSeconds = rs.getLong("timeoutSeconds"),
                            createdAt = rs.getLong("createdAt"),
                            viewedAt = rs.getLong("viewedAt").takeIf { !rs.wasNull() },
                            lastNotifiedAt = rs.getLong("lastNotifiedAt").takeIf { !rs.wasNull() },
                            status = rs.getString("status")
                        )
                    }
                    call.respond(mentions)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 获取动态的所有提醒
            get("/post/{postId}") {
                val postId = call.parameters["postId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的动态ID"))
                try {
                    val mentions = executeQuery(
                        "SELECT * FROM post_mentions WHERE postId = ? ORDER BY createdAt DESC",
                        { setInt(1, postId) }
                    ) { rs ->
                        PostMention(
                            id = rs.getInt("id"),
                            postId = rs.getInt("postId"),
                            mentionedUserId = rs.getInt("mentionedUserId"),
                            mentionerUserId = rs.getInt("mentionerUserId"),
                            timeoutSeconds = rs.getLong("timeoutSeconds"),
                            createdAt = rs.getLong("createdAt"),
                            viewedAt = rs.getLong("viewedAt").takeIf { !rs.wasNull() },
                            lastNotifiedAt = rs.getLong("lastNotifiedAt").takeIf { !rs.wasNull() },
                            status = rs.getString("status")
                        )
                    }
                    call.respond(mentions)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 标记为已读 (接受打卡)
            post("/{mentionId}/viewed") {
                val mentionId = call.parameters["mentionId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的提及ID"))
                try {
                    val currentTime = System.currentTimeMillis()
                    val updatedRows = executeUpdate(
                        "UPDATE post_mentions SET status = 'viewed', viewedAt = ? WHERE id = ? AND status = 'pending'",
                        { setLong(1, currentTime); setInt(2, mentionId) }
                    )
                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "提醒已标记为已读"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "提醒不存在或已处理"))
                    }
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 标记为忽略 (拒绝打卡)
            post("/{mentionId}/ignored") {
                val mentionId = call.parameters["mentionId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的提及ID"))
                try {
                    val currentTime = System.currentTimeMillis()
                    val updatedRows = executeUpdate(
                        "UPDATE post_mentions SET status = 'ignored', viewedAt = ? WHERE id = ? AND status = 'pending'",
                        { setLong(1, currentTime); setInt(2, mentionId) }
                    )
                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "提醒已标记为忽略"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "提醒不存在或已处理"))
                    }
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 标记为超时
            post("/{mentionId}/expired") {
                val mentionId = call.parameters["mentionId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的提及ID"))
                try {
                    val updatedRows = executeUpdate(
                        "UPDATE post_mentions SET status = 'expired' WHERE id = ? AND status = 'pending'",
                        { setInt(1, mentionId) }
                    )
                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "提醒已标记为超时"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "提醒不存在或已处理"))
                    }
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 更新超时时间 (作者修改)
            put("/{mentionId}/timeout") {
                val mentionId = call.parameters["mentionId"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的提及ID"))
                val request = call.receive<UpdateMentionTimeoutRequest>()
                try {
                    val currentTime = System.currentTimeMillis()
                    // 更新超时时间和创建时间（重新开始计时）
                    val updatedRows = executeUpdate(
                        "UPDATE post_mentions SET timeoutSeconds = ?, createdAt = ?, status = 'pending' WHERE id = ?",
                        { setLong(1, request.timeoutSeconds); setLong(2, currentTime); setInt(3, mentionId) }
                    )
                    if (updatedRows > 0) {
                        // 返回更新后的提醒
                        val mention = executeQuery(
                            "SELECT * FROM post_mentions WHERE id = ?",
                            { setInt(1, mentionId) }
                        ) { rs ->
                            PostMention(
                                id = rs.getInt("id"),
                                postId = rs.getInt("postId"),
                                mentionedUserId = rs.getInt("mentionedUserId"),
                                mentionerUserId = rs.getInt("mentionerUserId"),
                                timeoutSeconds = rs.getLong("timeoutSeconds"),
                                createdAt = rs.getLong("createdAt"),
                                viewedAt = rs.getLong("viewedAt").takeIf { !rs.wasNull() },
                                lastNotifiedAt = rs.getLong("lastNotifiedAt").takeIf { !rs.wasNull() },
                                status = rs.getString("status")
                            )
                        }.firstOrNull()

                        if (mention != null) {
                            call.respond(HttpStatusCode.OK, mention)
                        } else {
                            call.respond(HttpStatusCode.OK, GenericResponse(true, "超时时间已更新"))
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "提醒不存在"))
                    }
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 删除提醒 (作者删除)
            delete("/{mentionId}") {
                val mentionId = call.parameters["mentionId"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的提及ID"))
                try {
                    val deletedRows = executeUpdate("DELETE FROM post_mentions WHERE id = ?", { setInt(1, mentionId) })
                    if (deletedRows > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "提醒已删除"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "提醒不存在"))
                    }
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 获取用户在指定空间的未读提醒数量
            get("/user/{userId}/space/{spaceId}/count") {
                val userId = call.parameters["userId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的用户ID"))
                val spaceId = call.parameters["spaceId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的空间ID"))
                try {
                    val count = executeQuery(
                        """SELECT COUNT(*) as cnt FROM post_mentions pm 
                           JOIN space_posts sp ON pm.postId = sp.id 
                           WHERE pm.mentionedUserId = ? AND sp.spaceId = ? AND pm.status = 'pending'""",
                        { setInt(1, userId); setInt(2, spaceId) }
                    ) { it.getInt("cnt") }.firstOrNull() ?: 0

                    call.respond(mapOf("count" to count))
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 更新最后通知时间
            post("/{mentionId}/notified") {
                val mentionId = call.parameters["mentionId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的提及ID"))
                try {
                    val currentTime = System.currentTimeMillis()
                    val updatedRows = executeUpdate(
                        "UPDATE post_mentions SET lastNotifiedAt = ? WHERE id = ?",
                        { setLong(1, currentTime); setInt(2, mentionId) }
                    )
                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, GenericResponse(true, "通知时间已更新"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, GenericResponse(false, "提醒不存在"))
                    }
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
        }

        // --- Anniversary Routes ---
        route("/anniversaries") {
            // 创建纪念日
            post {
                val request = call.receive<CreateAnniversaryRequest>()
                try {
                    val createdAt = System.currentTimeMillis()
                    val newId = executeInsert("INSERT INTO anniversaries (spaceId, name, dateMillis, style, creatorUserId, status, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)", {
                        setInt(1, request.spaceId); setString(2, request.name); setLong(3, request.dateMillis); setString(4, request.style); setInt(5, request.creatorUserId); setString(6, "pending"); setLong(7, createdAt)
                    })
                    if (newId != -1) {
                        // 返回完整的 Anniversary 对象
                        val newAnniversary = Anniversary(
                            id = newId,
                            spaceId = request.spaceId,
                            name = request.name,
                            dateMillis = request.dateMillis,
                            style = request.style,
                            creatorUserId = request.creatorUserId,
                            status = "pending",
                            createdAt = createdAt
                        )
                        call.respond(HttpStatusCode.Created, newAnniversary)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "创建纪念日失败。"))
                    }
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 获取指定空间的纪念日列表 (修改路径以匹配前端)
            get("/space/{spaceId}") {
                val spaceId = call.parameters["spaceId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的空间ID"))
                try {
                    val anniversaries = executeQuery("SELECT * FROM anniversaries WHERE spaceId = ? ORDER BY dateMillis ASC", { setInt(1, spaceId) }) { rs ->
                        Anniversary(rs.getInt("id"), rs.getInt("spaceId"), rs.getString("name"), rs.getLong("dateMillis"), rs.getString("style"), rs.getInt("creatorUserId"), rs.getString("status"), rs.getLong("createdAt"))
                    }
                    call.respond(anniversaries)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 确认纪念日 (修改路径以匹配前端)
            post("/{anniversaryId}/confirm") {
                val anniversaryId = call.parameters["anniversaryId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的纪念日ID"))
                try {
                    executeUpdate("UPDATE anniversaries SET status = 'active' WHERE id = ?", { setInt(1, anniversaryId) })
                    call.respond(HttpStatusCode.OK, GenericResponse(true, "纪念日已确认。"))
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // 删除纪念日
            delete("/{anniversaryId}") {
                val anniversaryId = call.parameters["anniversaryId"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, GenericResponse(false, "无效的纪念日ID"))
                try {
                    executeUpdate("DELETE FROM anniversaries WHERE id = ?", { setInt(1, anniversaryId) })
                    call.respond(HttpStatusCode.OK, GenericResponse(true, "纪念日已删除。"))
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
        }

        // --- Message Routes ---
        route("/messages") {
            // --- (修改) GET /{userId1}/{userId2} ---
            get("/{userId1}/{userId2}") {
                val userId1 = call.parameters["userId1"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId2 = call.parameters["userId2"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                try {
                    val messages = executeQuery("SELECT * FROM messages WHERE (senderId = ? AND receiverId = ?) OR (senderId = ? AND receiverId = ?) ORDER BY timestamp ASC", {
                        setInt(1, userId1); setInt(2, userId2); setInt(3, userId2); setInt(4, userId1)
                    }) { rs ->
                        // 在这里添加 imageUrl
                        Message(
                            id = rs.getInt("id"),
                            senderId = rs.getInt("senderId"),
                            receiverId = rs.getInt("receiverId"),
                            content = rs.getString("content"),
                            imageUrl = rs.getString("imageUrl"), // 读取 imageUrl 字段
                            audioUrl = rs.getString("audioUrl"),
                            audioDuration = rs.getLong("audioDuration").takeIf { !rs.wasNull() },
                            timestamp = rs.getLong("timestamp"),
                            isRead = rs.getBoolean("isRead")
                        )
                    }
                    call.respond(messages)
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }

            // --- (修改) POST / ---
            post {
                val message = call.receive<Message>()
                try {
                    // 在 SQL 语句中添加 imageUrl, audioUrl, audioDuration
                    val newId = executeInsert("INSERT INTO messages (senderId, receiverId, content, imageUrl, audioUrl, audioDuration, timestamp, isRead) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", {
                        setInt(1, message.senderId)
                        setInt(2, message.receiverId)
                        setString(3, message.content)
                        setString(4, message.imageUrl) // 设置 imageUrl 的值
                        setString(5, message.audioUrl)
                        if (message.audioDuration != null) setLong(6, message.audioDuration) else setNull(6, java.sql.Types.BIGINT)
                        setLong(7, System.currentTimeMillis())
                        setBoolean(8, false)
                    })
                    if (newId != -1) call.respond(HttpStatusCode.Created, message.copy(id = newId))
                    else call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, "发送消息失败。"))
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, GenericResponse(false, e.message.toString())) }
            }
        }
    }
}
