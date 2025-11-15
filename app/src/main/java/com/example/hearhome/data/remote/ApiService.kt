package com.example.hearhome.data.remote

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
}

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class GenericResponse(val success: Boolean, val message: String)
