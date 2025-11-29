package com.example.hearhome.data.remote

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

// --- Data Transfer Objects (DTOs) for API Communication ---

@Serializable
data class FriendRequest(val senderId: Int, val receiverId: Int)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class GenericResponse(val success: Boolean, val message: String)

@Serializable
data class JoinSpaceRequest(val userId: Int, val inviteCode: String)

@Serializable
data class CreateSpaceRequest(
    val name: String,
    val type: String,
    val description: String? = null,
    val creatorId: Int,
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
    val userRole: String? = null,    // ADDED
    val userStatus: String? = null  // ADDED
)

@Serializable
data class ApiSpacePost(
    val id: Int = 0,
    val spaceId: Int,
    val authorId: Int,
    val content: String,
    val location: String? = null,
    val timestamp: Long = 0,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val status: String = "normal"
)


// --- API Service Singleton ---

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

    // --- Couple Functions ---

    suspend fun getCoupleRequests(userId: Int): HttpResponse {
        return client.get("$BASE_URL/couples/requests/$userId")
    }

    suspend fun acceptCoupleRequest(requestId: Int): HttpResponse {
        return client.post("$BASE_URL/couples/accept/$requestId")
    }

    suspend fun rejectCoupleRequest(requestId: Int): HttpResponse {
        return client.post("$BASE_URL/couples/reject/$requestId")
    }

    // --- Space Member Management Functions ---

    suspend fun approveMember(memberId: Int): HttpResponse {
        return client.post("$BASE_URL/space/members/approve/$memberId")
    }

    suspend fun rejectMember(memberId: Int): HttpResponse {
        return client.post("$BASE_URL/space/members/reject/$memberId")
    }

    suspend fun removeMember(memberId: Int): HttpResponse {
        return client.delete("$BASE_URL/space/members/$memberId")
    }

    suspend fun leaveSpace(spaceId: Int, userId: Int): HttpResponse {
        return client.post("$BASE_URL/space/leave/$spaceId") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("userId" to userId))
        }
    }
}
