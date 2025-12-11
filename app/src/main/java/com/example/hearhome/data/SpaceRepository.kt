package com.example.hearhome.data

import com.example.hearhome.data.local.Space
import com.example.hearhome.data.local.SpaceDao
import com.example.hearhome.data.local.SpaceMember
import com.example.hearhome.data.remote.ApiSpace
import com.example.hearhome.data.remote.ApiSpaceMember
import com.example.hearhome.data.remote.ApiSpacePost
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.data.remote.CreateSpaceRequest
import com.example.hearhome.data.remote.JoinSpaceRequest
import com.example.hearhome.ui.space.JoinSpaceResult
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

class SpaceRepository(private val spaceDao: SpaceDao) {

    /**
     * [FIXED] The core of the fix for the "empty member list" bug.
     * This now correctly calls the new, robust DAO transaction.
     */
    suspend fun refreshSpaces(userId: Int) {
        println("[DEBUG] SpaceRepository: Attempting to refresh spaces for userId: $userId")
        try {
            val response = ApiService.getSpaces(userId)
            if (response.status == HttpStatusCode.OK) {
                val apiSpaces: List<ApiSpace> = response.body()
                println("[DIAGNOSTIC] SpaceRepository: Fetched ${apiSpaces.size} spaces from network.")

                // 1. Get current members to identify owner spaces and pending memberships
                val currentMembers = spaceDao.getSpaceMembersByUserId(userId)
                val ownerSpaceIds = currentMembers
                    .filter { it.role == "owner" }
                    .map { it.spaceId }
                    .toSet()
                
                // 保护 pending 状态：找出所有 status = "pending" 的成员记录
                val pendingMemberships = currentMembers
                    .filter { it.status == "pending" }
                    .map { it.spaceId }
                    .toSet()
                
                println("[DEBUG] SpaceRepository: Found owner spaces: $ownerSpaceIds")
                println("[DEBUG] SpaceRepository: Found pending memberships in spaces: $pendingMemberships")

                // 2. Separate the received data into two lists: spaces and memberships.
                // PROTECTION 3: Preserve local checkInIntervalSeconds (not synced from server yet)
                val localSpaces = apiSpaces.map { apiSpace ->
                    val baseSpace = apiSpace.toLocalSpace()
                    
                    // Check if there's an existing local space with checkInIntervalSeconds
                    val existingSpace = spaceDao.getSpaceById(apiSpace.id)
                    if (existingSpace != null && existingSpace.checkInIntervalSeconds > 0) {
                        println("[DEBUG] SpaceRepository: Preserving checkInIntervalSeconds=${existingSpace.checkInIntervalSeconds} for space ${apiSpace.id}")
                        baseSpace.copy(checkInIntervalSeconds = existingSpace.checkInIntervalSeconds)
                    } else {
                        baseSpace
                    }
                }
                
                val localMembers = apiSpaces.map { apiSpace ->
                    // 先查询本地是否已有该成员记录
                    val existingMember = currentMembers.find { it.spaceId == apiSpace.id }
                    val baseMember = apiSpace.toLocalMember(userId, existingMember)
                    
                    // 3. PROTECTION 1: If user is owner locally, keep the owner role
                    if (ownerSpaceIds.contains(apiSpace.id) && baseMember.role == "member") {
                        println("[DEBUG] SpaceRepository: Protecting owner role for space ${apiSpace.id}")
                        baseMember.copy(role = "owner")
                    } else {
                        baseMember
                    }
                    
                    // 移除 PROTECTION 2: 现在服务器正确返回 userStatus，应该信任服务器的状态
                    // 不再强制保持本地的 pending 状态，允许服务器的 active 状态同步到本地
                }

                // 5. Call the DAO method to sync both lists in one transaction.
                spaceDao.syncUserSpacesAndMembers(userId, localSpaces, localMembers)
                println("[DEBUG] SpaceRepository: Successfully synced spaces and memberships.")
            } else {
                println("[ERROR] SpaceRepository: Failed to refresh spaces. Server returned ${response.status}")
            }
        } catch (e: Exception) {
            println("[ERROR] SpaceRepository: Exception during refreshSpaces: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun createSpace(space: Space, partnerId: Int? = null): Space? {
        println("[DEBUG] SpaceRepository: Attempting to create space: ${space.name}, partnerId: $partnerId")
        return try {
            val createRequest = CreateSpaceRequest(
                name = space.name,
                type = space.type,
                description = space.description,
                creatorId = space.creatorId,
                partnerId = partnerId,
                coverColor = space.coverColor
            )

            val response = ApiService.createSpace(createRequest)

            if (response.status == HttpStatusCode.Created) {
                println("[DEBUG] SpaceRepository: Successfully created space on network.")
                val createdApiSpace: ApiSpace = response.body()
                val newLocalSpace = createdApiSpace.toLocalSpace()
                
                spaceDao.insertSpace(newLocalSpace)

                val creatorMembership = SpaceMember(
                    spaceId = newLocalSpace.id,
                    userId = newLocalSpace.creatorId,
                    role = "owner",
                    status = "active"
                )
                spaceDao.addSpaceMember(creatorMembership)

                // 诊断：立即验证已保存的成员记录
                val savedMember = spaceDao.getSpaceMember(newLocalSpace.id, newLocalSpace.creatorId)
                println("[DEBUG] SpaceRepository: ========== After saving creator membership ==========")
                println("[DEBUG] SpaceRepository: Inserted: ${creatorMembership}")
                println("[DEBUG] SpaceRepository: Retrieved: ${savedMember}")
                println("[DEBUG] SpaceRepository: Space ID=${newLocalSpace.id}, Creator ID=${newLocalSpace.creatorId}")
                println("[DEBUG] SpaceRepository: ===========================================================")

                newLocalSpace
            } else {
                println("[ERROR] SpaceRepository: Failed to create space. Server returned ${response.status}")
                null
            }
        } catch (e: Exception) {
            println("[ERROR] SpaceRepository: Exception during createSpace: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun joinSpaceByCode(userId: Int, inviteCode: String): JoinSpaceResult {
        println("[DEBUG] SpaceRepository: Attempting to join space with code: $inviteCode for userId: $userId")
        return try {
            val request = JoinSpaceRequest(userId = userId, inviteCode = inviteCode)
            val response = ApiService.joinSpace(request)
            println("[DEBUG] SpaceRepository: joinSpace response status: ${response.status}")

            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseText = response.bodyAsText()
                    try {
                        val space: ApiSpace = Json.decodeFromString(responseText)
                        val localSpace = space.toLocalSpace()
                        
                        // 插入空间
                        spaceDao.insertSpace(localSpace)
                        
                        // 重要：立即创建一条 status = "pending" 的成员记录
                        // 这样申请者就不会立即看到空间内容，直到 owner 批准
                        val pendingMembership = SpaceMember(
                            spaceId = localSpace.id,
                            userId = userId,
                            role = "member",
                            status = "pending"  // 设置为待审核
                        )
                        spaceDao.addSpaceMember(pendingMembership)
                        println("[DEBUG] SpaceRepository: Created pending membership for userId=$userId in spaceId=${localSpace.id}")
                        
                        // 注意：不要在这里调用 refreshSpaces()，因为服务器可能不返回 userStatus，
                        // 会导致刚设置的 pending 状态被覆盖为 active
                        // refreshSpaces(userId)
                        
                        JoinSpaceResult.Joined(localSpace, "Successfully joined space! Waiting for approval...")
                    } catch (e: Exception) {
                        JoinSpaceResult.Failure(responseText)
                    }
                }
                HttpStatusCode.Conflict -> {
                    val errorBody = response.bodyAsText()
                    JoinSpaceResult.Failure(errorBody)
                }
                HttpStatusCode.NotFound -> {
                    JoinSpaceResult.Failure("Invite code is invalid or the space does not exist.")
                }
                else -> {
                    JoinSpaceResult.Failure("An unexpected error occurred: ${response.status}")
                }
            }
        } catch (e: Exception) {
            println("[ERROR] SpaceRepository: Exception during joinSpaceByCode: ${e.message}")
            e.printStackTrace()
            JoinSpaceResult.Failure("Network error. Please check your connection and try again.")
        }
    }

    suspend fun getSpaceMessages(spaceId: Int): List<ApiSpacePost> {
        // ... (implementation unchanged)
        return emptyList()
    }

    suspend fun sendSpaceMessage(spaceId: Int, authorId: Int, content: String): ApiSpacePost? {
        // ... (implementation unchanged)
        return null
    }
}

// --- Mapping Functions ---

fun ApiSpace.toLocalSpace(): Space {
    return Space(
        id = this.id,
        name = this.name,
        type = this.type,
        description = this.description,
        creatorId = this.creatorId,
        inviteCode = this.inviteCode,
        coverColor = this.coverColor,
        createdAt = this.createdAt,
        status = this.status,
        checkInIntervalSeconds = this.checkInIntervalSeconds
    )
}

/**
 * [ADDED] New mapping function to convert an ApiSpace into a local SpaceMember.
 * [FIXED] 使用服务器返回的 userMemberId，确保本地和远程的 memberId 一致
 */
fun ApiSpace.toLocalMember(currentUserId: Int, existingMember: SpaceMember? = null): SpaceMember {
    return SpaceMember(
        id = this.userMemberId ?: existingMember?.id ?: 0, // 优先使用服务器返回的memberId，确保一致性
        spaceId = this.id,
        userId = currentUserId,
        role = this.userRole ?: existingMember?.role ?: "member", // 优先使用服务器返回的角色
        status = this.userStatus ?: existingMember?.status ?: "active", // 优先使用服务器返回的状态
        nickname = existingMember?.nickname, // 保留本地昵称
        joinedAt = existingMember?.joinedAt ?: System.currentTimeMillis() // 保留本地加入时间
    )
}

fun ApiSpaceMember.toLocalSpaceMember(): SpaceMember {
    return SpaceMember(
        id = this.id,
        spaceId = this.spaceId,
        userId = this.userId,
        role = this.role,
        nickname = this.nickname,
        joinedAt = this.joinedAt,
        status = this.status
    )
}

fun Space.toApiSpace(): ApiSpace {
    return ApiSpace(
        id = this.id,
        name = this.name,
        type = this.type,
        description = this.description,
        creatorId = this.creatorId,
        inviteCode = this.inviteCode,
        coverColor = this.coverColor,
        createdAt = this.createdAt,
        status = this.status,
        checkInIntervalSeconds = this.checkInIntervalSeconds
    )
}
