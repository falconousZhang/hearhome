package com.example.hearhome.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 空间管理 ViewModel
 * 处理空间的创建、加入、成员管理等逻辑
 */
class SpaceViewModel(
    private val spaceDao: SpaceDao,
    private val userDao: UserDao,
    private val currentUserId: Int
) : ViewModel() {

    // 用户加入的所有空间
    private val _mySpaces = MutableStateFlow<List<Space>>(emptyList())
    val mySpaces: StateFlow<List<Space>> = _mySpaces.asStateFlow()

    // 当前选中的空间
    private val _currentSpace = MutableStateFlow<Space?>(null)
    val currentSpace: StateFlow<Space?> = _currentSpace.asStateFlow()

    // 当前空间的成员列表
    private val _spaceMembers = MutableStateFlow<List<SpaceMemberInfo>>(emptyList())
    val spaceMembers: StateFlow<List<SpaceMemberInfo>> = _spaceMembers.asStateFlow()

    // 待审核的成员申请
    private val _pendingMembers = MutableStateFlow<List<SpaceMemberInfo>>(emptyList())
    val pendingMembers: StateFlow<List<SpaceMemberInfo>> = _pendingMembers.asStateFlow()

    // 当前用户在当前空间的角色
    private val _currentUserRole = MutableStateFlow<String?>(null)
    val currentUserRole: StateFlow<String?> = _currentUserRole.asStateFlow()

    init {
        loadMySpaces()
    }

    /**
     * 加载用户的所有空间
     */
    fun loadMySpaces() {
        viewModelScope.launch {
            spaceDao.getSpacesJoinedByUser(currentUserId).collect { spaces ->
                _mySpaces.value = spaces
            }
        }
    }

    /**
     * 选择某个空间
     */
    fun selectSpace(spaceId: Int) {
        viewModelScope.launch {
            val space = spaceDao.getSpaceById(spaceId)
            _currentSpace.value = space
            
            if (space != null) {
                // 先加载用户角色，因为其他操作可能依赖角色信息
                loadCurrentUserRole(spaceId)
                // 加载空间成员
                loadSpaceMembers(spaceId)
                // 如果是管理员，加载待审核成员
                val role = _currentUserRole.value
                if (role == "admin" || role == "owner") {
                    loadPendingMembers(spaceId)
                }
            }
        }
    }

    /**
     * 加载空间成员
     */
    private suspend fun loadSpaceMembers(spaceId: Int) {
        val members = spaceDao.getSpaceMembers(spaceId)
        val memberInfoList = members.mapNotNull { member ->
            userDao.getUserById(member.userId)?.let { user ->
                SpaceMemberInfo(member, user)
            }
        }
        _spaceMembers.value = memberInfoList
    }

    /**
     * 加载当前用户角色
     */
    private suspend fun loadCurrentUserRole(spaceId: Int) {
        val member = spaceDao.getSpaceMember(spaceId, currentUserId)
        _currentUserRole.value = member?.role
    }

    /**
     * 加载待审核成员
     */
    private suspend fun loadPendingMembers(spaceId: Int) {
        val pending = spaceDao.getPendingMembers(spaceId)
        val pendingInfoList = pending.mapNotNull { member ->
            userDao.getUserById(member.userId)?.let { user ->
                SpaceMemberInfo(member, user)
            }
        }
        _pendingMembers.value = pendingInfoList
    }

    /**
     * 创建新空间
     * @param name 空间名称
     * @param type 空间类型 (couple/family)
     * @param description 空间描述
     * @return 创建成功返回空间ID,失败返回null
     */
    suspend fun createSpace(
        name: String,
        type: String,
        description: String? = null
    ): Int? {
        return try {
            // 生成唯一邀请码
            val inviteCode = generateUniqueInviteCode()
            
            val space = Space(
                name = name,
                type = type,
                description = description,
                creatorId = currentUserId,
                inviteCode = inviteCode
            )
            
            val spaceId = spaceDao.createSpace(space).toInt()
            
            // 自动将创建者加入空间并设为所有者
            val creatorMember = SpaceMember(
                spaceId = spaceId,
                userId = currentUserId,
                role = "owner",
                status = "active"
            )
            spaceDao.addSpaceMember(creatorMember)
            
            loadMySpaces()
            spaceId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 生成唯一的6位邀请码
     */
    private suspend fun generateUniqueInviteCode(): String {
        var code: String
        do {
            code = Random.nextInt(100000, 999999).toString()
        } while (spaceDao.isInviteCodeExists(code) > 0)
        return code
    }

    /**
     * 通过邀请码加入空间
     */
suspend fun joinSpaceByCode(inviteCode: String): JoinSpaceResult {
        return try {
            val space = spaceDao.getSpaceByInviteCode(inviteCode)
                ?: return JoinSpaceResult.Failure("邀请码无效或空间不存在")

            val existingMember = spaceDao.getSpaceMember(space.id, currentUserId)
            if (existingMember != null) {
                when (existingMember.status) {
                    "active" -> return JoinSpaceResult.Joined(space, "您已经在该空间中了")
                    "pending" -> return JoinSpaceResult.RequestPending(space, "加入申请正在审核中，请耐心等待")
                    "left" -> {
                        // 继续执行后续逻辑，重新加入或发起申请
                    }
                    else -> {
                        // 未知状态，继续执行以便重新设置状态
                    }
                }
            }

            val currentUser = userDao.getUserById(currentUserId)
                ?: return JoinSpaceResult.Failure("用户信息获取失败")

            when (space.type) {
                "couple" -> {
                    val activeCount = spaceDao.getSpaceMemberCount(space.id)
                    if (activeCount >= 2) {
                        return JoinSpaceResult.Failure("该情侣空间成员已满")
                    }
                    val partnerId = currentUser.partnerId
                    if (currentUser.relationshipStatus != "in_relationship" || partnerId == null) {
                        return JoinSpaceResult.Failure("您不是该情侣空间的成员，无法加入")
                    }
                    val partnerMember = spaceDao.getSpaceMember(space.id, partnerId)
                    if (partnerMember == null || partnerMember.status != "active") {
                        return JoinSpaceResult.Failure("您的伴侣尚未加入该情侣空间，无法加入")
                    }

                    if (existingMember != null) {
                        spaceDao.updateMemberStatus(existingMember.id, "active")
                    } else {
                        spaceDao.addSpaceMember(
                            SpaceMember(
                                spaceId = space.id,
                                userId = currentUserId,
                                role = "member",
                                status = "active"
                            )
                        )
                    }
                    loadMySpaces()
                    return JoinSpaceResult.Joined(space, "成功加入情侣空间")
                }

                "family" -> {
                    if (existingMember != null) {
                        spaceDao.updateMemberStatus(existingMember.id, "pending")
                    } else {
                        spaceDao.addSpaceMember(
                            SpaceMember(
                                spaceId = space.id,
                                userId = currentUserId,
                                status = "pending"
                            )
                        )
                    }
                    loadMySpaces()
                    return JoinSpaceResult.RequestPending(space, "加入家族空间请求已发送！")
                }

                else -> {
                    if (existingMember != null) {
                        spaceDao.updateMemberStatus(existingMember.id, "active")
                    } else {
                        spaceDao.addSpaceMember(
                            SpaceMember(
                                spaceId = space.id,
                                userId = currentUserId,
                                status = "active"
                            )
                        )
                    }
                    loadMySpaces()
                    return JoinSpaceResult.Joined(space, "成功加入空间")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JoinSpaceResult.Failure("加入空间失败，请稍后再试")
        }
    }

    /**
     * 审核通过加入申请
     */
    suspend fun approveMember(memberId: Int): Boolean {
        return try {
            val member = spaceDao.getMemberById(memberId) ?: return false
            val spaceId = member.spaceId
            val space = spaceDao.getSpaceById(spaceId) ?: return false

            if (space.type == "couple") {
                val activeCount = spaceDao.getSpaceMemberCount(spaceId)
                if (activeCount >= 2) {
                    return false
                }
                val user = userDao.getUserById(member.userId) ?: return false
                val partnerId = user.partnerId
                if (user.relationshipStatus != "in_relationship" || partnerId == null) {
                    return false
                }
                val partnerMember = spaceDao.getSpaceMember(spaceId, partnerId)
                if (partnerMember == null || partnerMember.status != "active") {
                    return false
                }
            }

            spaceDao.approveMember(memberId)
            loadSpaceMembers(spaceId)
            loadPendingMembers(spaceId)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 拒绝加入申请
     */
    suspend fun rejectMember(memberId: Int): Boolean {
        return try {
            spaceDao.rejectMember(memberId)
            _currentSpace.value?.let { space ->
                loadPendingMembers(space.id)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 移除成员
     */
    suspend fun removeMember(memberId: Int): Boolean {
        return try {
            spaceDao.removeMember(memberId)
            _currentSpace.value?.let { space ->
                loadSpaceMembers(space.id)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 退出空间
     */
    suspend fun leaveSpace(spaceId: Int): Boolean {
        return try {
            spaceDao.leaveSpace(spaceId, currentUserId)
            loadMySpaces()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 更新空间信息
     */
    suspend fun updateSpace(spaceId: Int, name: String, description: String?): Boolean {
        return try {
            spaceDao.updateSpace(spaceId, name, description)
            selectSpace(spaceId)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * 空间成员信息(包含用户详情)
 */
data class SpaceMemberInfo(
    val member: SpaceMember,
    val user: User
)

sealed class JoinSpaceResult {
    data class Joined(val space: Space, val message: String? = null) : JoinSpaceResult()
    data class RequestPending(val space: Space, val message: String) : JoinSpaceResult()
    data class Failure(val message: String) : JoinSpaceResult()
}
