@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.example.hearhome.data.remote

import android.annotation.SuppressLint
import com.example.hearhome.data.local.Couple
import com.example.hearhome.data.local.Friend
import com.example.hearhome.data.local.Message
import com.example.hearhome.data.local.User
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import io.ktor.client.call.body
import com.example.hearhome.data.local.Space
import com.example.hearhome.data.local.PostComment

// --- Data Transfer Objects (DTOs) for API Communication ---
// DTOs like LoginRequest, GenericResponse, etc., are defined in other files within this package
// and have been removed from here to resolve redeclaration errors.

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class FriendRequest(val senderId: Int, val receiverId: Int)

@Serializable
data class JoinSpaceRequest(
    val userId: Int,
    val inviteCode: String,
    val nickname: String? = null // MODIFIED: Added nickname based on server code
)

@Serializable
data class CreateSpaceRequest(
    val name: String,
    val type: String,
    val description: String? = null,
    val creatorId: Int,
    val partnerId: Int? = null,
    val coverColor: String = "#FF9800"
)

/**
 * [MODIFIED] Represents a Space object from the network.
 * Now includes the user's role and status for that specific space.
 */
@Serializable
data class ApiSpace(
    val id: Int = 0,
    val name: String,
    val type: String,
    val description: String? = null,
    val creatorId: Int,
    val inviteCode: String = "",
    val coverColor: String = "#FF9800",
    val createdAt: Long = 0,
    val status: String = "active",
    val checkInIntervalSeconds: Long = 0,
    val userMemberId: Int? = null,   // ADDED - space_members 表的 id
    val userRole: String? = null,    // ADDED
    val userStatus: String? = null  // ADDED
)

@Serializable
data class ApiSpaceMember(
    val id: Int = 0,
    val spaceId: Int,
    val userId: Int,
    val role: String = "member",
    val nickname: String? = null,
    val joinedAt: Long = System.currentTimeMillis(),
    val status: String = "active"
)

@Serializable
data class ApiSpacePost(
    val id: Int = 0,
    val spaceId: Int,
    val authorId: Int,
    val content: String,
    val images: String? = null, // Field for images, usually a JSON array of URLs
    val location: String? = null,
    val timestamp: Long = 0,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val status: String = "normal"
)

@Serializable
data class SpacePostUpdate(
    val type: String = "created",
    val post: ApiSpacePost
)

@Serializable
data class ApiMessage(
    val id: Int = 0,
    val senderId: Int,
    val receiverId: Int,
    val content: String? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val audioDuration: Long? = null,
    val timestamp: Long = 0,
    val isRead: Boolean = false   // ← 必须添加
)

/**
 * PostMention 的网络传输版本
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
    val status: String = "pending"
)

@Serializable
data class ApiPetAttributes(
    val mood: Int = 50,
    val health: Int = 80,
    val energy: Int = 60,
    val hydration: Int = 60,
    val intimacy: Int = 50
)

@Serializable
data class ApiSpacePet(
    val id: Int = 0,
    val spaceId: Int,
    val name: String,
    val type: String = "pet",
    val attributes: ApiPetAttributes,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ApiSpacePetRequest(
    val name: String? = null,
    val type: String? = null,
    val attributes: ApiPetAttributes
)

@Serializable
data class CreatePostCommentRequest(
    val postId: Int,
    val authorId: Int,
    val content: String,
    val replyToUserId: Int? = null
)


// --- API Service Singleton ---

@Serializable
data class CoupleRequest(val requesterId: Int, val partnerId: Int)

object ApiService {
    private const val FORCE_RISK_HEADER = "X-Force-Risk"
    private const val FORCE_RISK_VALUE = "true"
    private const val BASE_URL = "http://121.37.136.244:8080"   //http://10.0.2.2:8080

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets)
    }

    private fun buildWsUrl(path: String): String {
        val normalizedBase = BASE_URL.trimEnd('/')
        val protocol = if (normalizedBase.startsWith("https")) "wss" else "ws"
        return "$protocol://${normalizedBase.removePrefix("http://").removePrefix("https://")}/$path"
    }

    /**
     * NEW: Uploads an image to the server.
     * @param fileName The name of the file.
     * @param fileBytes The raw byte data of the image.
     * @return The server's response, which should contain the imageUrl.
     */
    suspend fun uploadImage(imageBytes: ByteArray, fileName: String): HttpResponse {
        return client.submitFormWithBinaryData(
            url = "$BASE_URL/upload/image",
            formData = formData {
                append("image", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }
        )
    }

    suspend fun uploadAudio(audioBytes: ByteArray, fileName: String): HttpResponse {
        return client.submitFormWithBinaryData(
            url = "$BASE_URL/upload/audio",
            formData = formData {
                append("audio", audioBytes, Headers.build {
                    append(HttpHeaders.ContentType, "audio/mp4")
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }
        )
    }


    // ============================ 忘记密码流程 ============================

    /** Step1：获取密保问题 */
    suspend fun fetchResetQuestion(email: String): HttpResponse =
        client.post("$BASE_URL/users/reset-question") {
            contentType(ContentType.Application.Json)
            setBody(ResetQuestionRequest(email.trim()))
        }

    /** Step0：发送重置验证码 */
    suspend fun sendResetPasswordCode(email: String): HttpResponse =
        client.post("$BASE_URL/users/reset-password/send-code") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordSendCodeRequest(email.trim()))
        }

    /** Step3：安全问题路径，提交答案 + 新密码 + 新邮箱 */
    suspend fun resetPasswordByAnswer(
        email: String,
        answer: String,
        newPassword: String,
        confirmPassword: String,
        newEmail: String?
    ): HttpResponse =
        client.post("$BASE_URL/users/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(email.trim(), answer.trim(), newPassword, confirmPassword, newEmail, method = "SECURITY_QUESTION"))
        }

    /** Step3（邮箱验证码路径）：提交验证码 + 新密码 + 确认密码 */
    suspend fun resetPasswordByEmailCode(email: String, code: String, newPassword: String, confirmPassword: String): HttpResponse =
        client.post("$BASE_URL/users/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordByEmailRequest(email.trim(), code.trim(), newPassword, confirmPassword))
        }

    // ============================ 个人中心：密码/密保 ============================
    /** 修改密码 */
    suspend fun updatePassword(
        email: String,
        oldPassword: String,
        newPassword: String,
        securityAnswer: String? = null,
        emailCode: String? = null,
        verificationToken: String? = null
    ): HttpResponse =
        client.post("$BASE_URL/users/update-password") {
            header(FORCE_RISK_HEADER, FORCE_RISK_VALUE)
            contentType(ContentType.Application.Json)
            setBody(UpdatePasswordRequest(email, oldPassword, newPassword, securityAnswer, emailCode, verificationToken))
        }

    /** 设置/修改密保 */
    suspend fun updateSecurityQuestion(
        email: String,
        password: String,
        question: String,
        answer: String,
        emailCode: String? = null,
        verificationToken: String? = null
    ): HttpResponse =
        client.post("$BASE_URL/users/update-security-question") {
            header(FORCE_RISK_HEADER, FORCE_RISK_VALUE)
            contentType(ContentType.Application.Json)
            setBody(UpdateSecurityQuestionRequest(email, password, question, answer, emailCode, verificationToken))
        }

    /** 风控触发：请求邮箱验证码 */
    suspend fun requestEmailVerification(email: String, purpose: String): HttpResponse =
        client.post("$BASE_URL/security/email-code/request") {
            contentType(ContentType.Application.Json)
            setBody(EmailVerificationRequest(email.trim(), purpose))
        }

    /** 提交邮箱验证码 */
    suspend fun verifyEmailCode(email: String, purpose: String, code: String): HttpResponse =
        client.post("$BASE_URL/security/email-code/verify") {
            contentType(ContentType.Application.Json)
            setBody(EmailVerificationConfirmRequest(email.trim(), purpose, code.trim()))
        }

    suspend fun register(user: User): HttpResponse {
        return client.post("$BASE_URL/users/register") {
            contentType(ContentType.Application.Json)
            setBody(user)
        }
    }

    suspend fun login(loginRequest: LoginRequest): HttpResponse {
        return client.post("$BASE_URL/users/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }
    }

    suspend fun getProfile(userId: Int): HttpResponse {
        return client.get("$BASE_URL/users/profile/$userId")
    }

    /**
     * 更新用户头像颜色
     * @param userId 用户ID
     * @param avatarColor 新的头像颜色（十六进制格式，如 #FF5722）
     */
    suspend fun updateAvatarColor(userId: Int, avatarColor: String): HttpResponse {
        return client.put("$BASE_URL/users/avatar/$userId") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("avatarColor" to avatarColor))
        }
    }

    suspend fun searchUserById(userId: Int): HttpResponse {
        return client.get("$BASE_URL/users/profile/$userId")
    }

    suspend fun getFriends(userId: Int): HttpResponse {
        return client.get("$BASE_URL/friends/all/$userId")
    }

    suspend fun getFriendRequests(userId: Int): HttpResponse {
        return client.get("$BASE_URL/friends/pending/$userId")
    }

    suspend fun sendFriendRequest(senderId: Int, receiverId: Int): HttpResponse {
        return client.post("$BASE_URL/friends/request") {
            contentType(ContentType.Application.Json)
            setBody(FriendRequest(senderId, receiverId))
        }
    }

    suspend fun acceptFriendRequest(requestId: Int): HttpResponse {
        return client.post("$BASE_URL/friends/accept/$requestId")
    }

    suspend fun rejectFriendRequest(requestId: Int): HttpResponse {
        return client.post("$BASE_URL/friends/reject/$requestId")
    }

    suspend fun deleteFriend(friendshipId: Int): HttpResponse {
        return client.delete("$BASE_URL/friends/$friendshipId")
    }

    suspend fun getMessages(userId1: Int, userId2: Int): HttpResponse {
        return client.get("$BASE_URL/messages/$userId1/$userId2")
    }

    suspend fun sendMessage(message: ApiMessage): HttpResponse {
        return client.post("$BASE_URL/messages") {
            contentType(ContentType.Application.Json)
            setBody(message)
        }
    }

    // --- Space Functions ---

    suspend fun createSpace(request: CreateSpaceRequest): HttpResponse {
        return client.post("$BASE_URL/space") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getSpaces(userId: Int): HttpResponse {
        return client.get("$BASE_URL/space") {
            parameter("userId", userId)
        }
    }

    suspend fun joinSpace(request: JoinSpaceRequest): HttpResponse {
        return client.post("$BASE_URL/space/join") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getSpacePet(spaceId: Int): ApiSpacePet {
        return client.get("$BASE_URL/space/$spaceId/pet").body()
    }

    suspend fun saveSpacePet(spaceId: Int, request: ApiSpacePetRequest): ApiSpacePet {
        return client.post("$BASE_URL/space/$spaceId/pet") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getSpaceMembers(spaceId: Int): List<ApiSpaceMember> {
        return client.get("$BASE_URL/space/$spaceId/members").body()
    }

    // --- Space Post Functions ---

    suspend fun getPosts(spaceId: Int): HttpResponse {
        return client.get("$BASE_URL/posts/$spaceId")
    }

    suspend fun createPost(post: ApiSpacePost): HttpResponse {
        return client.post("$BASE_URL/posts") {
            contentType(ContentType.Application.Json)
            setBody(post)
        }
    }

    /** 获取评论列表 */
    suspend fun getComments(postId: Int): List<PostComment> {
        val url = "$BASE_URL/posts/comments/$postId"
        println("[ApiService.getComments] requesting: $url")
        val responseBody = client.get(url).bodyAsText()
        println("[ApiService.getComments] raw response body: $responseBody")
        
        return runCatching {
            Json.decodeFromString<List<PostComment>>(responseBody)
        }.getOrElse { parseError ->
            println("[ApiService.getComments] direct parse failed: ${parseError.message}")
            // 尝试作为包装对象解析
            try {
                val json = Json.decodeFromString<JsonObject>(responseBody)
                println("[ApiService.getComments] parsed as JsonObject")
                val dataField = json["data"]
                if (dataField != null) {
                    println("[ApiService.getComments] found 'data' field, attempting to parse")
                    return@getOrElse Json.decodeFromString<List<PostComment>>(dataField.toString())
                }
            } catch (e: Exception) {
                println("[ApiService.getComments] parse attempt failed: ${e.message}")
                e.printStackTrace()
            }
            emptyList()
        }
    }

    /** 创建评论（服务器返回创建后的评论含 id/timestamp） */
    suspend fun createComment(request: CreatePostCommentRequest): PostComment {
        return client.post("$BASE_URL/posts/comment") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /** 点赞/取消点赞（服务端内部切换） */
    suspend fun toggleLike(postId: Int, userId: Int): HttpResponse {
        return client.post("$BASE_URL/posts/like/$postId") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("userId" to userId))
        }
    }

    suspend fun connectPostUpdates(spaceId: Int): DefaultClientWebSocketSession {
        val wsUrl = buildWsUrl("ws/space/$spaceId/posts")
        return client.webSocketSession { url(wsUrl) }
    }

    // ==================== 情侣关系相关 API ====================

    /**
     * 获取当前用户的情侣关系（已接受的）
     * @param userId 用户ID
     * @return 情侣关系信息（如果存在）
     */
    suspend fun getCouple(userId: Int): HttpResponse {
        return client.get("$BASE_URL/couples/$userId")
    }

    /**
     * 获取当前用户收到的待处理情侣请求
     * @param userId 用户ID
     * @return 待处理的情侣请求列表
     */
    suspend fun getCoupleRequests(userId: Int): HttpResponse {
        return client.get("$BASE_URL/couples/requests/$userId")
    }

    /**
     * 发送情侣关系请求
     * @param requesterId 请求发起者ID
     * @param partnerId 目标伴侣ID
     * @return 响应结果
     */
    suspend fun sendCoupleRequest(requesterId: Int, partnerId: Int): HttpResponse {
        return client.post("$BASE_URL/couples/request") {
            contentType(ContentType.Application.Json)
            setBody(CoupleRequest(requesterId, partnerId))
        }
    }

    /**
     * 接受情侣关系请求
     * @param requestId 请求ID
     * @return 响应结果
     */
    suspend fun acceptCoupleRequest(requestId: Int): HttpResponse {
        return client.post("$BASE_URL/couples/accept/$requestId")
    }

    /**
     * 拒绝情侣关系请求
     * @param requestId 请求ID
     * @return 响应结果
     */
    suspend fun rejectCoupleRequest(requestId: Int): HttpResponse {
        return client.post("$BASE_URL/couples/reject/$requestId")
    }


    /**
     * 解除情侣关系
     * @param userId 用户ID（发起解除的一方）
     * @return 响应结果
     */
    suspend fun breakupCouple(userId: Int): HttpResponse {
        return client.delete("$BASE_URL/couples/$userId")
    }

    // --- Space Member Management Functions ---

    suspend fun approveMember(memberId: Int): HttpResponse {
        val url = "${BASE_URL}space/members/approve/$memberId"
        println("[DEBUG ApiService] approveMember URL: $url")
        return client.post(url) {
            // 显式设置 Content-Type，即使请求体为空
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun rejectMember(memberId: Int): HttpResponse {
        val url = "${BASE_URL}space/members/reject/$memberId"
        println("[DEBUG ApiService] rejectMember URL: $url")
        return client.post(url)
    }

    suspend fun removeMember(memberId: Int): HttpResponse {
        return client.delete("${BASE_URL}space/members/$memberId")
    }

    suspend fun leaveSpace(spaceId: Int, userId: Int): HttpResponse {
        return client.post("$BASE_URL/space/leave/$spaceId") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("userId" to userId))
        }
    }

    // --- Post Mention (@提醒) Functions ---

    /**
     * 创建@提醒（批量）
     * @param postId 动态ID
     * @param mentionedUserIds 被提醒的用户ID列表
     * @param mentionerUserId 发起提醒的用户ID
     * @param timeoutSeconds 超时时间（秒）
     * @return 创建的提醒列表
     */
    suspend fun createMentions(
        postId: Int,
        mentionedUserIds: List<Int>,
        mentionerUserId: Int,
        timeoutSeconds: Long
    ): List<PostMention> {
        return client.post("$BASE_URL/mentions") {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "postId" to postId,
                "mentionedUserIds" to mentionedUserIds,
                "mentionerUserId" to mentionerUserId,
                "timeoutSeconds" to timeoutSeconds
            ))
        }.body()
    }

    /**
     * 获取用户收到的所有提醒
     */
    suspend fun getUserMentions(userId: Int): List<PostMention> {
        return client.get("$BASE_URL/mentions/user/$userId").body()
    }

    /**
     * 获取用户的待处理提醒
     */
    suspend fun getUserPendingMentions(userId: Int): List<PostMention> {
        return client.get("$BASE_URL/mentions/user/$userId/pending").body()
    }

    /**
     * 获取动态的所有提醒
     */
    suspend fun getPostMentions(postId: Int): List<PostMention> {
        return client.get("$BASE_URL/mentions/post/$postId").body()
    }

    /**
     * 标记提醒为已读
     */
    suspend fun markMentionViewed(mentionId: Int): HttpResponse {
        return client.post("$BASE_URL/mentions/$mentionId/viewed")
    }

    /**
     * 标记提醒为忽略
     */
    suspend fun markMentionIgnored(mentionId: Int): HttpResponse {
        return client.post("$BASE_URL/mentions/$mentionId/ignored")
    }

    /**
     * 标记提醒为超时
     */
    suspend fun markMentionExpired(mentionId: Int): HttpResponse {
        return client.post("$BASE_URL/mentions/$mentionId/expired")
    }

    /**
     * 更新提醒的超时时间
     */
    suspend fun updateMentionTimeout(mentionId: Int, timeoutSeconds: Long): PostMention {
        return client.put("$BASE_URL/mentions/$mentionId/timeout") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("timeoutSeconds" to timeoutSeconds))
        }.body()
    }

    /**
     * 删除提醒
     */
    suspend fun deleteMention(mentionId: Int): HttpResponse {
        return client.delete("$BASE_URL/mentions/$mentionId")
    }

    /**
     * 获取用户在指定空间的未读提醒数量
     */
    suspend fun getUserSpaceMentionCount(userId: Int, spaceId: Int): Int {
        val response: Map<String, Int> = client.get("$BASE_URL/mentions/user/$userId/space/$spaceId/count").body()
        return response["count"] ?: 0
    }

    /**
     * 更新最后通知时间
     */
    suspend fun updateMentionNotified(mentionId: Int): HttpResponse {
        return client.post("$BASE_URL/mentions/$mentionId/notified")
    }

    // --- Space Check-In Interval Functions ---
    /***********************************wdz********************************************
     * 解散空间
     */
    suspend fun dissolveSpace(spaceId: Int, userId: Int): HttpResponse {
        return client.delete("$BASE_URL/space/$spaceId") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("userId" to userId))
        }
    }

    /**
     * 邀请好友加入空间
     */
    suspend fun inviteFriendToSpace(
        spaceId: Int,
        inviterId: Int,
        friendId: Int
    ): HttpResponse {
        return client.post("$BASE_URL/space/$spaceId/invite") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "inviterId" to inviterId,
                    "friendId" to friendId
                )
            )
        }
    }
    /*********************************************************wdz********************
     * 更新空间的打卡间隔时间
     */
    suspend fun updateSpaceCheckInInterval(spaceId: Int, checkInIntervalSeconds: Long): HttpResponse {
        return client.put("$BASE_URL/space/$spaceId/checkin-interval") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("checkInIntervalSeconds" to checkInIntervalSeconds))
        }
    }

    /**
     * 获取空间的打卡间隔时间
     */
    suspend fun getSpaceCheckInInterval(spaceId: Int): Long {
        val response: Map<String, Long> = client.get("$BASE_URL/space/$spaceId/checkin-interval").body()
        return response["checkInIntervalSeconds"] ?: 0L
    }

    /**
     * 获取空间详情（包含打卡间隔）
     */
    suspend fun getSpaceById(spaceId: Int): Space {
        return client.get("$BASE_URL/space/$spaceId").body()
    }

    // ==================== Anniversary (纪念日) API ====================

    /**
     * 创建纪念日
     */
    suspend fun createAnniversary(request: CreateAnniversaryRequest): HttpResponse {
        return client.post("$BASE_URL/anniversaries") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    /**
     * 获取指定空间下的纪念日列表
     */
    suspend fun getAnniversaries(spaceId: Int): List<ApiAnniversary> {
        return client.get("$BASE_URL/anniversaries/space/$spaceId").body()
    }

    /**
     * 确认纪念日 (状态变更为 confirmed)
     */
    suspend fun confirmAnniversary(anniversaryId: Int): HttpResponse {
        return client.post("$BASE_URL/anniversaries/$anniversaryId/confirm")
    }

    /**
     * 删除纪念日
     */
    suspend fun deleteAnniversary(anniversaryId: Int): HttpResponse {
        return client.delete("$BASE_URL/anniversaries/$anniversaryId")
    }
}
