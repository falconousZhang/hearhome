package com.example.hearhome.data.remote

import com.example.hearhome.data.local.Couple
import com.example.hearhome.data.local.Friend
import com.example.hearhome.data.local.Message
import com.example.hearhome.data.local.User
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FriendRequest(val senderId: Int, val receiverId: Int)

@Serializable
data class CoupleRequest(val requesterId: Int, val partnerId: Int)

object ApiService {
    private const val BASE_URL = "http://121.37.136.244:8080"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    // ============================ 忘记密码流程 ============================

    /** Step1：获取密保问题 */
    suspend fun fetchResetQuestion(email: String): HttpResponse =
        client.post("$BASE_URL/users/reset-question") {
            contentType(ContentType.Application.Json)
            setBody(ResetQuestionRequest(email.trim()))
        }

    /** Step3：提交答案 + 新密码 */
    suspend fun resetPasswordByAnswer(email: String, answer: String, newPassword: String): HttpResponse =
        client.post("$BASE_URL/users/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(email.trim(), answer.trim(), newPassword))
        }

    // ============================ 个人中心：密码/密保 ============================
    /** 修改密码 */
    suspend fun updatePassword(
        email: String,
        oldPassword: String,
        securityAnswer: String,
        newPassword: String
    ): HttpResponse =
        client.post("$BASE_URL/users/update-password") {
            contentType(ContentType.Application.Json)
            setBody(UpdatePasswordRequest(email, oldPassword, securityAnswer, newPassword))
        }

    /** 设置/修改密保 */
    suspend fun updateSecurityQuestion(
        email: String,
        password: String,
        question: String,
        answer: String
    ): HttpResponse =
        client.post("$BASE_URL/users/update-security-question") {
            contentType(ContentType.Application.Json)
            setBody(UpdateSecurityQuestionRequest(email, password, question, answer))
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

    suspend fun sendMessage(message: Message): HttpResponse {
        return client.post("$BASE_URL/messages") {
            contentType(ContentType.Application.Json)
            setBody(message)
        }
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
}


