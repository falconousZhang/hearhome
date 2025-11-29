package com.example.hearhome.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.SpaceRepository
import com.example.hearhome.data.local.*
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.data.remote.ApiSpacePost
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 空间管理 ViewModel
 * [MODIFIED] 现在使用 SpaceRepository 来处理所有网络操作
 */
class SpaceViewModel(
    private val repository: SpaceRepository,
    private val spaceDao: SpaceDao,
    private val userDao: UserDao,
    private val coupleDao: CoupleDao,
    private val currentUserId: Int
) : ViewModel() {

    // --- Core Properties ---

    val mySpaces: StateFlow<List<Space>> = spaceDao.getSpacesJoinedByUser(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // --- [ADDED] Properties for Space Messages ---
    private val _spaceMessages = MutableStateFlow<List<ApiSpacePost>>(emptyList())
    val spaceMessages: StateFlow<List<ApiSpacePost>> = _spaceMessages.asStateFlow()

    // --- Original Properties (unchanged) ---
    private val _currentSpace = MutableStateFlow<Space?>(null)
    val currentSpace: StateFlow<Space?> = _currentSpace.asStateFlow()

    private val _spaceMembers = MutableStateFlow<List<SpaceMemberInfo>>(emptyList())
    val spaceMembers: StateFlow<List<SpaceMemberInfo>> = _spaceMembers.asStateFlow()

    private val _pendingMembers = MutableStateFlow<List<SpaceMemberInfo>>(emptyList())
    val pendingMembers: StateFlow<List<SpaceMemberInfo>> = _pendingMembers.asStateFlow()

    private val _currentUserRole = MutableStateFlow<String?>(null)
    val currentUserRole: StateFlow<String?> = _currentUserRole.asStateFlow()

    init {
        refreshMySpaces()
    }

    fun refreshMySpaces() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.refreshSpaces(currentUserId)
            } catch (e: Exception) {
                _error.value = "刷新空间列表失败: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    suspend fun createSpace(
        name: String,
        type: String,
        description: String? = null,
        partnerUserId: Int? = null
    ): CreateSpaceResult {
        _isLoading.value = true
        val spaceToCreate = Space(
            name = name,
            type = type,
            description = description,
            creatorId = currentUserId,
            inviteCode = "" // Server generates this
        )
        val newSpace = repository.createSpace(spaceToCreate)
        _isLoading.value = false
        return if (newSpace != null) {
            CreateSpaceResult.Success(newSpace.id, "空间创建成功！")
        } else {
            CreateSpaceResult.Failure("创建空间失败。服务器拒绝了该请求或发生网络错误。")
        }
    }

    suspend fun joinSpaceByCode(inviteCode: String): JoinSpaceResult {
        _isLoading.value = true
        val result = repository.joinSpaceByCode(currentUserId, inviteCode)
        _isLoading.value = false
        return result
    }
    
    /**
     * [ADDED] Sends a new message in the current space.
     */
    fun sendSpaceMessage(content: String) {
        viewModelScope.launch {
            val spaceId = _currentSpace.value?.id ?: return@launch

            val newMessage = repository.sendSpaceMessage(
                spaceId = spaceId,
                authorId = currentUserId,
                content = content
            )

            if (newMessage != null) {
                // Optimistic update: add the new message to the list immediately
                _spaceMessages.update { it + newMessage }
            } else {
                _error.value = "发送消息失败。"
            }
        }
    }

    /**
     * [MODIFIED] When a space is selected, now also loads its messages.
     */
    fun selectSpace(spaceId: Int) {
        viewModelScope.launch {
            val space = spaceDao.getSpaceById(spaceId)
            _currentSpace.value = space

            if (space != null) {
                // 先加载当前用户的角色（重要，必须优先加载）
                loadCurrentUserRole(spaceId)
                
                // 加载消息、成员列表
                loadSpaceMessages(space.id)
                loadSpaceMembers(spaceId)
                
                // 获取已更新的角色值
                val role = _currentUserRole.value
                println("[DEBUG SpaceViewModel selectSpace] After loading, role=$role")
                if (role == "admin" || role == "owner") {
                    println("[DEBUG SpaceViewModel selectSpace] Loading pending members for admin/owner")
                    // loadPendingMembers 是 suspend 函数，必须等待其完成
                    loadPendingMembers(spaceId)
                } else {
                    println("[DEBUG SpaceViewModel selectSpace] User is not admin/owner, skipping pending members")
                }
            } else {
                // Clear messages when no space is selected
                _spaceMessages.value = emptyList()
                _currentUserRole.value = null
            }
        }
    }

    /**
     * [ADDED] Loads messages (as posts) for a given space.
     */
    private fun loadSpaceMessages(spaceId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _spaceMessages.value = repository.getSpaceMessages(spaceId)
            _isLoading.value = false
        }
    }

    // =========================================================================
    // The functions below are from your original code and are not yet refactored
    // =========================================================================

    private suspend fun loadSpaceMembers(spaceId: Int) {
        val members = spaceDao.getSpaceMembers(spaceId)
        val memberInfoList = members.mapNotNull { member ->
            userDao.getUserById(member.userId)?.let { user ->
                SpaceMemberInfo(member, user)
            }
        }
        _spaceMembers.value = memberInfoList
    }

    private suspend fun loadCurrentUserRole(spaceId: Int) {
        val member = spaceDao.getSpaceMember(spaceId, currentUserId)
        val roleFromDb = member?.role
        _currentUserRole.value = roleFromDb
        
        // 详细诊断日志
        println("[DEBUG SpaceViewModel] ========== loadCurrentUserRole Diagnostic ==========")
        println("[DEBUG SpaceViewModel] Query: spaceId=$spaceId, userId=$currentUserId")
        println("[DEBUG SpaceViewModel] Result: member=$member")
        println("[DEBUG SpaceViewModel] Result: roleFromDb=$roleFromDb")
        println("[DEBUG SpaceViewModel] Result: _currentUserRole.value=${_currentUserRole.value}")
        
        // 列出该空间的所有成员以验证数据
        val allMembers = spaceDao.getSpaceMembers(spaceId)
        println("[DEBUG SpaceViewModel] All members in space $spaceId:")
        allMembers.forEach { m ->
            println("[DEBUG SpaceViewModel]   - userId=${m.userId}, role=${m.role}, status=${m.status}, id=${m.id}")
        }
        println("[DEBUG SpaceViewModel] =================================================")
    }

    private suspend fun loadPendingMembers(spaceId: Int) {
        println("[DEBUG SpaceViewModel] loadPendingMembers: Starting for spaceId=$spaceId")
        val pending = spaceDao.getPendingMembers(spaceId)
        println("[DEBUG SpaceViewModel] loadPendingMembers: Found ${pending.size} pending members")
        pending.forEach { member ->
            println("[DEBUG SpaceViewModel] loadPendingMembers: pending member userId=${member.userId}, role=${member.role}, status=${member.status}")
        }
        
        val pendingInfoList = pending.mapNotNull { member ->
            var user = userDao.getUserById(member.userId)
            if (user == null) {
                println("[DEBUG SpaceViewModel] loadPendingMembers: User ${member.userId} not found in local DB, fetching from API")
                try {
                    // 从 API 获取用户信息
                    val response = ApiService.getProfile(member.userId)
                    if (response.status == HttpStatusCode.OK) {
                        val apiUser: User = response.body<User>()
                        // 保存到本地数据库
                        userDao.insert(apiUser)
                        println("[DEBUG SpaceViewModel] loadPendingMembers: Fetched and saved user ${member.userId} from API")
                        user = apiUser
                    } else {
                        println("[ERROR SpaceViewModel] loadPendingMembers: API returned ${response.status} for user ${member.userId}")
                    }
                } catch (e: Exception) {
                    println("[ERROR SpaceViewModel] loadPendingMembers: Failed to fetch user ${member.userId} from API: ${e.message}")
                }
            }
            
            user?.let { u ->
                println("[DEBUG SpaceViewModel] loadPendingMembers: Mapping pending member userId=${member.userId} to user ${u.nickname}")
                SpaceMemberInfo(member, u)
            }
        }
        println("[DEBUG SpaceViewModel] loadPendingMembers: Final list has ${pendingInfoList.size} items")
        _pendingMembers.value = pendingInfoList
    }

    suspend fun dissolveSpace(spaceId: Int): DissolveSpaceResult {
        // TODO: Refactor to call repository.dissolveSpace(spaceId)
        refreshMySpaces()
        return DissolveSpaceResult.Failure("Not implemented for online mode yet.")
    }

    fun approveMember(memberId: Int) {
        viewModelScope.launch {
            try {
                val response = ApiService.approveMember(memberId)
                if (response.status == HttpStatusCode.OK) {
                    // 刷新待审核成员列表
                    _currentSpace.value?.id?.let { loadPendingMembers(it) }
                } else {
                    _error.value = "批准成员失败"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "批准成员失败: ${e.message}"
            }
        }
    }

    fun rejectMember(memberId: Int) {
        viewModelScope.launch {
            try {
                val response = ApiService.rejectMember(memberId)
                if (response.status == HttpStatusCode.OK) {
                    // 刷新待审核成员列表
                    _currentSpace.value?.id?.let { loadPendingMembers(it) }
                } else {
                    _error.value = "拒绝成员失败"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "拒绝成员失败: ${e.message}"
            }
        }
    }

    fun removeMember(memberId: Int) {
        viewModelScope.launch {
            try {
                val response = ApiService.removeMember(memberId)
                if (response.status == HttpStatusCode.OK) {
                    // 刷新成员列表
                    _currentSpace.value?.id?.let { spaceId ->
                        loadSpaceMembers(spaceId)
                    }
                } else {
                    _error.value = "移除成员失败"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "移除成员失败: ${e.message}"
            }
        }
    }

    fun leaveSpace(spaceId: Int) {
        viewModelScope.launch {
            try {
                val response = ApiService.leaveSpace(spaceId, currentUserId)
                if (response.status == HttpStatusCode.OK) {
                    refreshMySpaces()
                } else {
                    _error.value = "离开空间失败"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "离开空间失败: ${e.message}"
            }
        }
    }

    suspend fun updateSpace(spaceId: Int, name: String, description: String?): Boolean {
        // TODO: Refactor to call repository
        return true
    }
}

// --- Helper classes from your original file (unchanged) ---

data class SpaceMemberInfo(
    val member: SpaceMember,
    val user: User
)

sealed class CreateSpaceResult {
    data class Success(val spaceId: Int, val message: String? = null) : CreateSpaceResult()
    data class RequestSent(val message: String) : CreateSpaceResult()
    data class Failure(val message: String) : CreateSpaceResult()
}

sealed class JoinSpaceResult {
    data class Joined(val space: Space, val message: String? = null) : JoinSpaceResult()
    data class RequestPending(val space: Space, val message: String) : JoinSpaceResult()
    data class Failure(val message: String) : JoinSpaceResult()
}

sealed class DissolveSpaceResult {
    data class Success(val message: String) : DissolveSpaceResult()
    data class Failure(val message: String) : DissolveSpaceResult()
}
