package com.example.hearhome.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * 邮件发送工具类
 * 用于发送通知邮件
 * 
 * 安全改进：
 * - 使用加密存储管理邮箱密码
 * - 支持从安全配置中读取SMTP设置
 * - 不在代码中硬编码敏感信息
 * 
 * 注意：需要在 build.gradle 中添加以下依赖：
 * implementation("com.sun.mail:android-mail:1.6.7")
 * implementation("com.sun.mail:android-activation:1.6.7")
 * implementation("androidx.security:security-crypto:1.1.0-alpha06")
 */
object EmailHelper {
    
    /**
     * 发送邮件
     * @param context 上下文（用于读取配置）
     * @param toEmail 收件人邮箱
     * @param subject 邮件主题
     * @param body 邮件内容
     * @return 是否发送成功
     */
    suspend fun sendEmail(
        context: Context,
        toEmail: String,
        subject: String,
        body: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 检查配置是否完整
            if (!SecureConfigManager.isEmailConfigured(context)) {
                println("邮件配置未完成，请先配置SMTP信息")
                return@withContext false
            }
            
            // 从安全配置中读取邮件设置
            val smtpHost = SecureConfigManager.getSmtpHost(context)
            val smtpPort = SecureConfigManager.getSmtpPort(context)
            val emailFrom = SecureConfigManager.getEmailFrom(context)
            val password = SecureConfigManager.getEmailPassword(context)
            
            val properties = Properties().apply {
                put("mail.smtp.host", smtpHost)
                put("mail.smtp.port", smtpPort)
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
            }
            
            val session = Session.getInstance(properties, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(emailFrom, password)
                }
            })
            
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(emailFrom))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                this.subject = subject
                setText(body, "UTF-8", "html")
            }
            
            Transport.send(message)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 发送评论通知邮件
     */
    suspend fun sendCommentNotificationEmail(
        context: Context,
        toEmail: String,
        userName: String,
        postContent: String,
        commentContent: String
    ): Boolean {
        val subject = "心家 - 收到新评论"
        val body = """
            <html>
            <body>
                <h2>你收到了一条新评论</h2>
                <p><strong>$userName</strong> 评论了你的动态：</p>
                <blockquote style="background: #f5f5f5; padding: 10px; border-left: 3px solid #ff6b6b;">
                    $postContent
                </blockquote>
                <p>评论内容：</p>
                <blockquote style="background: #f5f5f5; padding: 10px; border-left: 3px solid #4ecdc4;">
                    $commentContent
                </blockquote>
                <p>快来查看吧！</p>
                <hr>
                <p style="color: #999; font-size: 12px;">这是一封系统自动发送的邮件，请勿回复。</p>
            </body>
            </html>
        """.trimIndent()
        
        return sendEmail(context, toEmail, subject, body)
    }
    
    /**
     * 发送点赞通知邮件
     */
    suspend fun sendLikeNotificationEmail(
        context: Context,
        toEmail: String,
        userName: String,
        postContent: String
    ): Boolean {
        val subject = "心家 - 收到新点赞"
        val body = """
            <html>
            <body>
                <h2>你的动态收到了点赞</h2>
                <p><strong>$userName</strong> 点赞了你的动态：</p>
                <blockquote style="background: #f5f5f5; padding: 10px; border-left: 3px solid #ff6b6b;">
                    $postContent
                </blockquote>
                <hr>
                <p style="color: #999; font-size: 12px;">这是一封系统自动发送的邮件，请勿回复。</p>
            </body>
            </html>
        """.trimIndent()
        
        return sendEmail(context, toEmail, subject, body)
    }
    
    /**
     * 发送新动态通知邮件
     */
    suspend fun sendNewPostNotificationEmail(
        context: Context,
        toEmail: String,
        spaceName: String,
        userName: String,
        postContent: String
    ): Boolean {
        val subject = "心家 - 空间有新动态"
        val body = """
            <html>
            <body>
                <h2>「$spaceName」有新动态</h2>
                <p><strong>$userName</strong> 发布了新动态：</p>
                <blockquote style="background: #f5f5f5; padding: 10px; border-left: 3px solid #4ecdc4;">
                    $postContent
                </blockquote>
                <p>快来查看吧！</p>
                <hr>
                <p style="color: #999; font-size: 12px;">这是一封系统自动发送的邮件，请勿回复。</p>
            </body>
            </html>
        """.trimIndent()
        
        return sendEmail(context, toEmail, subject, body)
    }
    
    /**
     * 发送空间加入申请通知邮件
     */
    suspend fun sendSpaceJoinRequestEmail(
        context: Context,
        toEmail: String,
        spaceName: String,
        userName: String
    ): Boolean {
        val subject = "心家 - 新的加入申请"
        val body = """
            <html>
            <body>
                <h2>收到新的空间加入申请</h2>
                <p><strong>$userName</strong> 申请加入空间「$spaceName」</p>
                <p>请及时处理该申请。</p>
                <hr>
                <p style="color: #999; font-size: 12px;">这是一封系统自动发送的邮件，请勿回复。</p>
            </body>
            </html>
        """.trimIndent()
        
        return sendEmail(context, toEmail, subject, body)
    }
}
