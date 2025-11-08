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
    private val coupleDao: CoupleDao,
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
        description: String? = null,
        partnerUserId: Int? = null
    ): CreateSpaceResult {
        return try {
            val inviteCode = generateUniqueInviteCode()

            when (type) {
                "couple" -> {
                    val partnerId = partnerUserId
                        ?: return CreateSpaceResult.Failure("请选择要邀请的情侣成员")
                    if (partnerId == currentUserId) {
                        return CreateSpaceResult.Failure("情侣空间必须邀请另一位成员")
                    }

                    val creatorActiveCouple = spaceDao.findActiveCoupleSpaceForUser(currentUserId)
                    if (creatorActiveCouple != null) {
                        return CreateSpaceResult.Failure("您已经拥有情侣空间，无法重复创建")
                    }
                    val partnerActiveCouple = spaceDao.findActiveCoupleSpaceForUser(partnerId)
                    if (partnerActiveCouple != null) {
                        return CreateSpaceResult.Failure("对方已在其他情侣空间中")
                    }

                    val currentUser = userDao.getUserById(currentUserId)
                        ?: return CreateSpaceResult.Failure("用户信息获取失败")
                    val partnerUser = userDao.getUserById(partnerId)
                        ?: return CreateSpaceResult.Failure("邀请成员不存在")

                    val existingSpace = spaceDao.findActiveCoupleSpace(currentUserId, partnerId)
                    if (existingSpace != null) {
                        return CreateSpaceResult.Failure("你们已经拥有情侣空间")
                    }

                    if (currentUser.relationshipStatus == "in_relationship" && currentUser.partnerId != partnerId) {
                        return CreateSpaceResult.Failure("您正在与其他用户处于情侣关系，无法创建新的情侣空间")
                    }
                    if (partnerUser.relationshipStatus == "in_relationship" && partnerUser.partnerId != currentUserId) {
                        return CreateSpaceResult.Failure("对方已经与其他用户建立情侣关系，无法加入")
                    }

                    val existingRelationship = coupleDao.getCoupleRelationship(currentUserId, partnerId)
                    when (existingRelationship?.status) {
                        "pending" -> {
                            return if (existingRelationship.requesterId == currentUserId) {
                                CreateSpaceResult.RequestSent("邀请已发送，对方同意后将自动创建情侣空间")
                            } else {
                                CreateSpaceResult.Failure("对方已向您发出情侣邀请，请在情侣申请列表中处理")
                            }
                        }
                        "accepted" -> {
                            // 已互为情侣关系，直接创建空间
                            val space = Space(
                                name = name,
                                type = "couple",
                                description = description,
                                creatorId = currentUserId,
                                inviteCode = inviteCode
                            )

                            val spaceId = spaceDao.createSpace(space).toInt()

                            spaceDao.addSpaceMember(
                                SpaceMember(
                                    spaceId = spaceId,
                                    userId = currentUserId,
                                    role = "owner",
                                    status = "active"
                                )
                            )
                    spaceDao.addSpaceMember(
                        SpaceMember(
                            spaceId = spaceId,
                            userId = partnerId,
                            role = "owner",
                            status = "active"
                        )
                    )

                            if (currentUser.relationshipStatus != "in_relationship" || currentUser.partnerId != partnerId) {
                                userDao.updateRelationshipStatus(currentUserId, "in_relationship", partnerId)
                            }
                            if (partnerUser.relationshipStatus != "in_relationship" || partnerUser.partnerId != currentUserId) {
                                userDao.updateRelationshipStatus(partnerId, "in_relationship", currentUserId)
                            }

                            loadMySpaces()
                            CreateSpaceResult.Success(spaceId, "情侣空间创建成功")
                        }
                        else -> {
                            // 未建立情侣关系，发起申请
                            val newRequest = Couple(
                                requesterId = currentUserId,
                                partnerId = partnerId,
                                createdAt = System.currentTimeMillis()
                            )
                            coupleDao.insertRequest(newRequest)
                            CreateSpaceResult.RequestSent(
                                "已向 ${partnerUser.displayName()} 发送情侣邀请，对方同意后将自动创建情侣空间"
                            )
                        }
                    }
                }

                else -> {
                    val space = Space(
                        name = name,
                        type = type,
                        description = description,
                        creatorId = currentUserId,
                        inviteCode = inviteCode
                    )

                    val spaceId = spaceDao.createSpace(space).toInt()

                    spaceDao.addSpaceMember(
                        SpaceMember(
                            spaceId = spaceId,
                            userId = currentUserId,
                            role = "owner",
                            status = "active"
                        )
                    )

                    loadMySpaces()
                    CreateSpaceResult.Success(spaceId, "空间创建成功")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CreateSpaceResult.Failure("创建空间失败，请稍后再试")
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
                    spaceDao.updateMemberRole(space.id, partnerId, "owner")

                    if (existingMember != null) {
                        spaceDao.updateMemberStatus(existingMember.id, "active")
                        spaceDao.updateMemberRole(space.id, currentUserId, "owner")
                    } else {
                        spaceDao.addSpaceMember(
                            SpaceMember(
                                spaceId = space.id,
                                userId = currentUserId,
                                role = "owner",
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
     * 解散空间
     * 情侣空间：双方都是所有者，都可以解散
     * 其他空间：仅所有者可以解散
     */
    suspend fun dissolveSpace(spaceId: Int): DissolveSpaceResult {
        return try {
            val space = spaceDao.getSpaceById(spaceId) ?: return DissolveSpaceResult.Failure("空间不存在")
            val myMember = spaceDao.getSpaceMember(spaceId, currentUserId)
                ?: return DissolveSpaceResult.Failure("您不在该空间中")
            
            if (space.type != "couple" && myMember.role != "owner") {
                return DissolveSpaceResult.Failure("只有空间所有者可以解散空间")
            }
            
            if (space.type == "couple" && myMember.role != "owner") {
                return DissolveSpaceResult.Failure("您没有权限解散情侣空间")
            }

            val members = spaceDao.getSpaceMembers(spaceId)
            val memberIds = members.map { it.userId }

            spaceDao.archiveSpace(spaceId)
            spaceDao.updateMembersStatusBySpace(spaceId, "left")

            if (space.type == "couple") {
                if (memberIds.size >= 2) {
                    val userA = memberIds[0]
                    val userB = memberIds[1]
    
                    coupleDao.deleteRelationshipBetween(userA, userB)

                    val userAInfo = userDao.getUserById(userA)
                    if (userAInfo?.partnerId == userB) {
                        userDao.updateRelationshipStatus(userA, "single", null)
                    }
                    val userBInfo = userDao.getUserById(userB)
                    if (userBInfo?.partnerId == userA) {
                        userDao.updateRelationshipStatus(userB, "single", null)
                    }
                } else if (memberIds.isNotEmpty()) {
                    val userId = memberIds.first()
                    val userInfo = userDao.getUserById(userId)
                    val partnerId = userInfo?.partnerId
                    if (partnerId != null) {
                        coupleDao.deleteRelationshipBetween(userId, partnerId)
                        if (userInfo.relationshipStatus == "in_relationship") {
                            userDao.updateRelationshipStatus(userId, "single", null)
                        }
                        val partnerInfo = userDao.getUserById(partnerId)
                        if (partnerInfo?.partnerId == userId) {
                            userDao.updateRelationshipStatus(partnerId, "single", null)
                        }
                    } else {
                        coupleDao.deleteRelationship(userId)
                        if (userInfo?.relationshipStatus == "in_relationship") {
                            userDao.updateRelationshipStatus(userId, "single", null)
                        }
                    }
                }
            }

            if (_currentSpace.value?.id == spaceId) {
                _currentSpace.value = null
                _spaceMembers.value = emptyList()
                _pendingMembers.value = emptyList()
                _currentUserRole.value = null
            }

            loadMySpaces()

            val message = if (space.type == "couple") {
                "情侣空间已解散，情侣关系已解除"
            } else {
                "空间已解散"
            }
            DissolveSpaceResult.Success(message)
        } catch (e: Exception) {
            e.printStackTrace()
            DissolveSpaceResult.Failure("解散空间失败，请稍后再试")
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

private fun User.displayName(): String = nickname.ifBlank { email }

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
