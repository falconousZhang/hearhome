package com.example.hearhome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * 空间 DAO
 * 管理空间的创建、查询与管理
 */
@Dao
interface SpaceDao {
    
    // ==================== 空间管理 ====================
    
    /**
     * 创建新空间
     * @return 新创建空间的ID
     */
    @Insert
    suspend fun createSpace(space: Space): Long
    
    /**
     * 根据ID查询空间
     */
    @Query("SELECT * FROM spaces WHERE id = :spaceId LIMIT 1")
    suspend fun getSpaceById(spaceId: Int): Space?
    
    /**
     * 根据邀请码查询空间
     */
    @Query("SELECT * FROM spaces WHERE inviteCode = :inviteCode AND status = 'active' LIMIT 1")
    suspend fun getSpaceByInviteCode(inviteCode: String): Space?

    /**
     * 查询两位用户间已存在的活跃情侣空间
     */
    @Transaction
    @Query("""
        SELECT s.* FROM spaces s
        INNER JOIN space_members sm1 ON s.id = sm1.spaceId
        INNER JOIN space_members sm2 ON s.id = sm2.spaceId
        WHERE s.type = 'couple'
          AND s.status = 'active'
          AND sm1.userId = :userId1 AND sm1.status = 'active'
          AND sm2.userId = :userId2 AND sm2.status = 'active'
        LIMIT 1
    """)
    suspend fun findActiveCoupleSpace(userId1: Int, userId2: Int): Space?
    
    /**
     * 查询用户创建的所有空间
     */
    @Query("SELECT * FROM spaces WHERE creatorId = :userId AND status = 'active' ORDER BY createdAt DESC")
    fun getSpacesCreatedByUser(userId: Int): Flow<List<Space>>
    
    /**
     * 查询用户加入的所有空间(通过成员表关联)
     */
    @Transaction
    @Query("""
        SELECT s.* FROM spaces s
        INNER JOIN space_members sm ON s.id = sm.spaceId
        WHERE sm.userId = :userId AND sm.status = 'active' AND s.status = 'active'
        ORDER BY sm.joinedAt DESC
    """)
    fun getSpacesJoinedByUser(userId: Int): Flow<List<Space>>
    
    /**
     * 查询用户是否已有活跃的情侣空间
     */
    @Query("""
        SELECT s.* FROM spaces s
        INNER JOIN space_members sm ON s.id = sm.spaceId
        WHERE sm.userId = :userId
          AND sm.status = 'active'
          AND s.status = 'active'
          AND s.type = 'couple'
        LIMIT 1
    """)
    suspend fun findActiveCoupleSpaceForUser(userId: Int): Space?
    
    /**
     * 更新空间信息
     */
    @Query("UPDATE spaces SET name = :name, description = :description WHERE id = :spaceId")
    suspend fun updateSpace(spaceId: Int, name: String, description: String?)
    
    /**
     * 归档空间(软删除)
     */
    @Query("UPDATE spaces SET status = 'archived' WHERE id = :spaceId")
    suspend fun archiveSpace(spaceId: Int)
    
    /**
     * 检查邀请码是否已存在
     */
    @Query("SELECT COUNT(*) FROM spaces WHERE inviteCode = :inviteCode")
    suspend fun isInviteCodeExists(inviteCode: String): Int
    
    // ==================== 空间成员管理 ====================
    
    /**
     * 添加空间成员
     */
    @Insert
    suspend fun addSpaceMember(member: SpaceMember): Long
    
    /**
     * 查询空间的所有成员
     */
    @Query("""
        SELECT * FROM space_members 
        WHERE spaceId = :spaceId AND status = 'active'
        ORDER BY joinedAt ASC
    """)
    suspend fun getSpaceMembers(spaceId: Int): List<SpaceMember>

    /**
     * 根据成员ID查询成员信息
     */
    @Query("SELECT * FROM space_members WHERE id = :memberId LIMIT 1")
    suspend fun getMemberById(memberId: Int): SpaceMember?
    
    /**
     * 查询待审核的加入申请
     */
    @Query("""
        SELECT * FROM space_members 
        WHERE spaceId = :spaceId AND status = 'pending'
        ORDER BY joinedAt DESC
    """)
    suspend fun getPendingMembers(spaceId: Int): List<SpaceMember>
    
    /**
     * 查询用户在某空间的成员信息
     */
    @Query("""
        SELECT * FROM space_members 
        WHERE spaceId = :spaceId AND userId = :userId 
        LIMIT 1
    """)
    suspend fun getSpaceMember(spaceId: Int, userId: Int): SpaceMember?
    
    /**
     * 审核通过加入申请
     */
    @Query("UPDATE space_members SET status = 'active' WHERE id = :memberId")
    suspend fun approveMember(memberId: Int)
    
    /**
     * 拒绝加入申请
     */
    @Query("DELETE FROM space_members WHERE id = :memberId")
    suspend fun rejectMember(memberId: Int)
    
    /**
     * 移除成员(管理员操作)
     */
    @Query("UPDATE space_members SET status = 'left' WHERE id = :memberId")
    suspend fun removeMember(memberId: Int)

    /**
     * 更新成员状态
     */
    @Query("UPDATE space_members SET status = :status WHERE id = :memberId")
    suspend fun updateMemberStatus(memberId: Int, status: String)
    
    /**
     * 用户主动退出空间
     */
    @Query("""
        UPDATE space_members SET status = 'left' 
        WHERE spaceId = :spaceId AND userId = :userId
    """)
    suspend fun leaveSpace(spaceId: Int, userId: Int)
    
    /**
     * 转让管理员权限
     */
    @Transaction
    suspend fun transferAdmin(spaceId: Int, fromUserId: Int, toUserId: Int) {
        // 将原管理员降为普通成员
        downgradeToMember(spaceId, fromUserId)
        // 将新用户升为管理员
        upgradeToAdmin(spaceId, toUserId)
    }
    
    @Query("""
        UPDATE space_members SET role = 'member' 
        WHERE spaceId = :spaceId AND userId = :userId
    """)
    suspend fun downgradeToMember(spaceId: Int, userId: Int)
    
    @Query("""
        UPDATE space_members SET role = 'admin' 
        WHERE spaceId = :spaceId AND userId = :userId
    """)
    suspend fun upgradeToAdmin(spaceId: Int, userId: Int)
    
    /**
     * 更新成员角色
     */
    @Query("""
        UPDATE space_members SET role = :role
        WHERE spaceId = :spaceId AND userId = :userId
    """)
    suspend fun updateMemberRole(spaceId: Int, userId: Int, role: String)
    
    /**
     * 统计空间成员数
     */
    @Query("SELECT COUNT(*) FROM space_members WHERE spaceId = :spaceId AND status = 'active'")
    suspend fun getSpaceMemberCount(spaceId: Int): Int

    /**
     * 批量更新指定空间成员状态
     */
    @Query("UPDATE space_members SET status = :status WHERE spaceId = :spaceId")
    suspend fun updateMembersStatusBySpace(spaceId: Int, status: String)
    
    // ==================== 打卡设置 ====================
    
    /**
     * 更新空间的打卡间隔时间（单位：秒）
     * @param spaceId 空间ID
     * @param intervalSeconds 打卡间隔时间，0表示关闭打卡功能
     */
    @Query("UPDATE spaces SET checkInIntervalSeconds = :intervalSeconds WHERE id = :spaceId")
    suspend fun updateCheckInInterval(spaceId: Int, intervalSeconds: Long)
    
    /**
     * 获取指定用户在指定空间的最后一条动态发布时间
     * @return 最后发布时间的时间戳（毫秒），如果没有动态则返回null
     */
    @Query("""
        SELECT MAX(timestamp) FROM space_posts 
        WHERE spaceId = :spaceId AND authorId = :userId AND status = 'normal'
    """)
    suspend fun getLastPostTimeByUser(spaceId: Int, userId: Int): Long?
}
