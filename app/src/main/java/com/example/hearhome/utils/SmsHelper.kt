package com.example.hearhome.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 短信发送工具类
 * 用于发送通知短信
 * 
 * 注意：需要集成第三方短信服务商API（如阿里云、腾讯云等）
 * 这里提供基础框架，实际使用时需要替换为具体的短信服务商API
 */
object SmsHelper {
    
    // 短信服务配置（示例，需要替换为实际的服务商配置）
    private const val SMS_API_URL = "https://api.sms-provider.com/send"
    private const val SMS_API_KEY = "your_api_key_here"
    private const val SMS_API_SECRET = "your_api_secret_here"
    
    /**
     * 发送短信（通用方法）
     * @param phoneNumber 手机号
     * @param message 短信内容
     * @return 是否发送成功
     */
    suspend fun sendSms(
        phoneNumber: String,
        message: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 这里是示例代码，实际需要根据短信服务商的API文档实现
            val url = URL(SMS_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            
            // 构建请求参数（根据实际API调整）
            val params = buildString {
                append("api_key=${URLEncoder.encode(SMS_API_KEY, "UTF-8")}")
                append("&phone=${URLEncoder.encode(phoneNumber, "UTF-8")}")
                append("&message=${URLEncoder.encode(message, "UTF-8")}")
            }
            
            // 发送请求
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(params)
                writer.flush()
            }
            
            // 读取响应
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val response = reader.readText()
                    // 根据实际API响应判断是否成功
                    return@withContext response.contains("success", ignoreCase = true)
                }
            }
            
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 使用模板发送短信
     * @param phoneNumber 手机号
     * @param templateCode 模板代码
     * @param params 模板参数
     * @return 是否发送成功
     */
    suspend fun sendTemplateMessage(
        phoneNumber: String,
        templateCode: String,
        params: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 这里需要根据实际短信服务商的模板API实现
            // 示例：阿里云短信服务、腾讯云短信服务等
            
            // 暂时返回false，提示需要实现
            println("SMS Template: $templateCode, Phone: $phoneNumber, Params: $params")
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 发送评论通知短信
     */
    suspend fun sendCommentNotificationSms(
        phoneNumber: String,
        userName: String,
        postPreview: String
    ): Boolean {
        val message = "【心家】$userName 评论了你的动态：$postPreview，快来查看吧！"
        return sendSms(phoneNumber, message)
    }
    
    /**
     * 发送点赞通知短信
     */
    suspend fun sendLikeNotificationSms(
        phoneNumber: String,
        userName: String,
        postPreview: String
    ): Boolean {
        val message = "【心家】$userName 点赞了你的动态：$postPreview"
        return sendSms(phoneNumber, message)
    }
    
    /**
     * 发送新动态通知短信
     */
    suspend fun sendNewPostNotificationSms(
        phoneNumber: String,
        spaceName: String,
        userName: String,
        postPreview: String
    ): Boolean {
        val message = "【心家】「$spaceName」有新动态：$userName 发布了「$postPreview」"
        return sendSms(phoneNumber, message)
    }
    
    /**
     * 发送空间加入申请通知短信
     */
    suspend fun sendSpaceJoinRequestSms(
        phoneNumber: String,
        spaceName: String,
        userName: String
    ): Boolean {
        val message = "【心家】$userName 申请加入空间「$spaceName」，请及时处理。"
        return sendSms(phoneNumber, message)
    }
    
    /**
     * 验证手机号格式
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // 简单的中国大陆手机号验证
        val regex = Regex("^1[3-9]\\d{9}$")
        return regex.matches(phoneNumber)
    }
    
    /**
     * 格式化手机号（隐藏中间4位）
     */
    fun formatPhoneNumber(phoneNumber: String): String {
        return if (phoneNumber.length == 11) {
            "${phoneNumber.substring(0, 3)}****${phoneNumber.substring(7)}"
        } else {
            phoneNumber
        }
    }
}
