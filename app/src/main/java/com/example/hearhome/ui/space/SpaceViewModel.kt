package com.example.hearhome.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.SpaceRepository
import com.example.hearhome.data.toLocalSpaceMember
import com.example.hearhome.data.local.*
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.data.remote.ApiSpacePost
import com.example.hearhome.data.remote.ApiSpaceMember
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
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
    
    // 当前选中的空间ID，用于观察空间变化
    private val _selectedSpaceId = MutableStateFlow<Int?>(null)

    private val _spaceMembers = MutableStateFlow<List<SpaceMemberInfo>>(emptyList())
    val spaceMembers: StateFlow<List<SpaceMemberInfo>> = _spaceMembers.asStateFlow()

    private val _pendingMembers = MutableStateFlow<List<SpaceMemberInfo>>(emptyList())
    val pendingMembers: StateFlow<List<SpaceMemberInfo>> = _pendingMembers.asStateFlow()

    private val _currentUserRole = MutableStateFlow<String?>(null)
    val currentUserRole: StateFlow<String?> = _currentUserRole.asStateFlow()

    init {
        refreshMySpaces()
        
        // 观察当前选中空间的变化（通过 Flow 实时更新）
        viewModelScope.launch {
            _selectedSpaceId.filterNotNull().flatMapLatest { spaceId ->
                spaceDao.getSpaceByIdFlow(spaceId)
            }.collect { space ->
                _currentSpace.value = space
            }
        }
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
        val newSpace = repository.createSpace(spaceToCreate, partnerUserId)
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
     * 使用 Flow 观察空间变化，确保打卡间隔等设置能够实时更新
     */
    fun selectSpace(spaceId: Int) {
        // 设置选中的空间ID，触发 Flow 观察
        _selectedSpaceId.value = spaceId
        
        viewModelScope.launch {
            // 先从远程服务器刷新空间数据，确保成员状态是最新的
            try {
                repository.refreshSpaces(currentUserId)
            } catch (e: Exception) {
                println("[DEBUG SpaceViewModel] Failed to refresh spaces: ${e.message}")
            }
            // 同步该空间的成员列表，确保能看到待审核的其他用户
            syncSpaceMembersFromServer(spaceId)
            
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
        // 先从服务器同步最新成员列表，确保能拿到其他用户的 pending 记录
        syncSpaceMembersFromServer(spaceId)
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

    private suspend fun syncSpaceMembersFromServer(spaceId: Int) {
        try {
            val remoteMembers: List<ApiSpaceMember> = ApiService.getSpaceMembers(spaceId)
            val localMembers = remoteMembers.map { it.toLocalSpaceMember() }
            spaceDao.replaceMembersForSpace(spaceId, localMembers)
            println("[DEBUG SpaceViewModel] syncSpaceMembersFromServer: synced ${localMembers.size} members for space $spaceId")
        } catch (e: Exception) {
            println("[ERROR SpaceViewModel] Failed to sync members from server: ${e.message}")
        }
    }

    suspend fun dissolveSpace(spaceId: Int): DissolveSpaceResult {
        // TODO: Refactor to call repository.dissolveSpace(spaceId)
        refreshMySpaces()
        return DissolveSpaceResult.Failure("Not implemented for online mode yet.")
    }

    fun approveMember(memberId: Int) {
        viewModelScope.launch {
            try {
                println("[DEBUG SpaceViewModel] approveMember called with memberId=$memberId")
                val response = ApiService.approveMember(memberId)
                val responseBody = response.bodyAsText()
                println("[DEBUG SpaceViewModel] approveMember response status: ${response.status}")
                println("[DEBUG SpaceViewModel] approveMember response body: $responseBody")
                
                when (response.status) {
                    HttpStatusCode.OK -> {
                        println("[DEBUG SpaceViewModel] Approval successful, response: $responseBody")
                        
                        // 如果后端返回成功，直接在本地数据库中更新该成员状态为 active
                        // 注意：不能依赖 refreshSpaces()，因为它只同步当前用户的记录，不会同步其他用户的记录
                        spaceDao.updateMemberStatus(memberId, "active")
                        println("[DEBUG SpaceViewModel] Updated member status to active in local DB")
                        
                        // 刷新待审核成员列表和正式成员列表
                        _currentSpace.value?.id?.let { spaceId ->
                            println("[DEBUG SpaceViewModel] Refreshing members for spaceId=$spaceId")
                            loadPendingMembers(spaceId)
                            loadSpaceMembers(spaceId)
                        }
                    }
                    HttpStatusCode.NotFound -> {
                        // 成员不存在，从本地数据库删除该记录
                        println("[DEBUG SpaceViewModel] Member not found, deleting from local DB")
                        spaceDao.rejectMember(memberId)
                        
                        // 刷新待审核成员列表
                        _currentSpace.value?.id?.let { spaceId ->
                            loadPendingMembers(spaceId)
                            loadSpaceMembers(spaceId)
                        }
                        _error.value = "该成员已被处理或不存在"
                    }
                    else -> {
                        println("[ERROR SpaceViewModel] approveMember failed with status: ${response.status}")
                        _error.value = "批准成员失败: $responseBody"
                    }
                }
            } catch (e: Exception) {
                println("[ERROR SpaceViewModel] approveMember exception: ${e.message}")
                e.printStackTrace()
                _error.value = "批准成员失败: ${e.message}"
            }
        }
    }

    fun rejectMember(memberId: Int) {
        viewModelScope.launch {
            try {
                println("[DEBUG SpaceViewModel] rejectMember called with memberId=$memberId")
                val response = ApiService.rejectMember(memberId)
                val responseBody = response.bodyAsText()
                println("[DEBUG SpaceViewModel] rejectMember response status: ${response.status}")
                println("[DEBUG SpaceViewModel] rejectMember response body: $responseBody")
                
                when (response.status) {
                    HttpStatusCode.OK -> {
                        // 直接在本地数据库中删除该成员记录
                        spaceDao.rejectMember(memberId)
                        println("[DEBUG SpaceViewModel] Deleted member from local DB")
                        
                        // 刷新待审核成员列表
                        _currentSpace.value?.id?.let { 
                            println("[DEBUG SpaceViewModel] Refreshing pending members for spaceId=$it")
                            loadPendingMembers(it)
                        }
                    }
                    HttpStatusCode.NotFound -> {
                        // 成员不存在，从本地数据库删除该记录
                        println("[DEBUG SpaceViewModel] Member not found, deleting from local DB")
                        spaceDao.rejectMember(memberId)
                        
                        // 刷新待审核成员列表
                        _currentSpace.value?.id?.let { 
                            loadPendingMembers(it)
                        }
                        _error.value = "该成员已被处理或不存在"
                    }
                    else -> {
                        println("[ERROR SpaceViewModel] rejectMember failed with status: ${response.status}")
                        _error.value = "拒绝成员失败: $responseBody"
                    }
                }
            } catch (e: Exception) {
                println("[ERROR SpaceViewModel] rejectMember exception: ${e.message}")
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
