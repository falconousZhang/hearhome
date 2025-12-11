package com.example.hearhome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SpaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpace(space: Space): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpaces(spaces: List<Space>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSpaceMember(member: SpaceMember)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpaceMembers(members: List<SpaceMember>)

    @Query("DELETE FROM space_members WHERE spaceId = :spaceId")
    suspend fun deleteMembersBySpace(spaceId: Int)

    @Query("DELETE FROM space_members WHERE userId = :userId AND status != 'pending'")
    suspend fun deleteAllSpaceMembersForUserExceptPending(userId: Int)

    /**
     * [REPLACED] This is the new, robust synchronization transaction.
     * It safely replaces all spaces and memberships for a given user.
     * 
     * IMPORTANT: This method now preserves 'pending' membership statuses
     * to prevent wiping out pending join requests.
     */
    @Transaction
    suspend fun syncUserSpacesAndMembers(userId: Int, spaces: List<Space>, members: List<SpaceMember>) {
        println("[DEBUG SpaceDao] syncUserSpacesAndMembers: userId=$userId, spaces=${spaces.size}, members=${members.size}")
        members.forEach { member ->
            println("[DEBUG SpaceDao] syncUserSpacesAndMembers: inserting member spaceId=${member.spaceId}, userId=${member.userId}, status=${member.status}")
        }
        
        // 1. Delete memberships for this user, BUT preserve pending statuses
        // This way, pending join requests won't be wiped out by sync
        deleteAllSpaceMembersForUserExceptPending(userId)

        // 2. Insert the new spaces and new memberships.
        // OnConflictStrategy.REPLACE ensures that if a space already exists, it gets updated.
        if (spaces.isNotEmpty()) {
            insertSpaces(spaces)
        }
        if (members.isNotEmpty()) {
            insertSpaceMembers(members)
        }
    }

    @Transaction
    suspend fun replaceMembersForSpace(spaceId: Int, members: List<SpaceMember>) {
        deleteMembersBySpace(spaceId)
        if (members.isNotEmpty()) {
            insertSpaceMembers(members)
        }
    }
    
    @Query("SELECT * FROM space_members WHERE userId = :userId")
    suspend fun getSpaceMembersByUserId(userId: Int): List<SpaceMember>
    
    @Query("SELECT * FROM spaces WHERE id = :spaceId LIMIT 1")
    suspend fun getSpaceById(spaceId: Int): Space?
    
    /**
     * 获取空间信息（Flow版本，用于实时更新）
     */
    @Query("SELECT * FROM spaces WHERE id = :spaceId LIMIT 1")
    fun getSpaceByIdFlow(spaceId: Int): Flow<Space?>
    
    @Query("SELECT * FROM spaces WHERE inviteCode = :inviteCode AND status = 'active' LIMIT 1")
    suspend fun getSpaceByInviteCode(inviteCode: String): Space?

    @Transaction
    @Query("SELECT s.* FROM spaces s INNER JOIN space_members sm1 ON s.id = sm1.spaceId INNER JOIN space_members sm2 ON s.id = sm2.spaceId WHERE s.type = 'couple' AND s.status = 'active' AND sm1.userId = :userId1 AND sm1.status = 'active' AND sm2.userId = :userId2 AND sm2.status = 'active' LIMIT 1")
    suspend fun findActiveCoupleSpace(userId1: Int, userId2: Int): Space?
    
    @Query("SELECT * FROM spaces WHERE creatorId = :userId AND status = 'active' ORDER BY createdAt DESC")
    fun getSpacesCreatedByUser(userId: Int): Flow<List<Space>>
    
    @Transaction
    @Query("SELECT s.* FROM spaces s INNER JOIN space_members sm ON s.id = sm.spaceId WHERE sm.userId = :userId AND sm.status = 'active' AND s.status = 'active' ORDER BY sm.joinedAt DESC")
    fun getSpacesJoinedByUser(userId: Int): Flow<List<Space>>
    
    @Query("SELECT s.* FROM spaces s INNER JOIN space_members sm ON s.id = sm.spaceId WHERE sm.userId = :userId AND sm.status = 'active' AND s.status = 'active' AND s.type = 'couple' LIMIT 1")
    suspend fun findActiveCoupleSpaceForUser(userId: Int): Space?
    
    @Query("UPDATE spaces SET name = :name, description = :description WHERE id = :spaceId")
    suspend fun updateSpace(spaceId: Int, name: String, description: String?)
    
    @Query("UPDATE spaces SET status = 'archived' WHERE id = :spaceId")
    suspend fun archiveSpace(spaceId: Int)
    
    @Query("SELECT COUNT(*) FROM spaces WHERE inviteCode = :inviteCode")
    suspend fun isInviteCodeExists(inviteCode: String): Int
    
    @Query("DELETE FROM spaces WHERE id IN (SELECT spaceId FROM space_members WHERE userId = :userId)")
    suspend fun deleteAllSpacesForUser(userId: Int)
    
    @Query("SELECT * FROM space_members WHERE spaceId = :spaceId AND status = 'active' ORDER BY joinedAt ASC")
    suspend fun getSpaceMembers(spaceId: Int): List<SpaceMember>

    @Query("SELECT * FROM space_members WHERE id = :memberId LIMIT 1")
    suspend fun getMemberById(memberId: Int): SpaceMember?
    
    @Query("SELECT * FROM space_members WHERE spaceId = :spaceId AND status = 'pending' ORDER BY joinedAt DESC")
    suspend fun getPendingMembers(spaceId: Int): List<SpaceMember>
    
    @Query("SELECT * FROM space_members WHERE spaceId = :spaceId AND userId = :userId LIMIT 1")
    suspend fun getSpaceMember(spaceId: Int, userId: Int): SpaceMember?
    
    @Query("UPDATE space_members SET status = 'active' WHERE id = :memberId")
    suspend fun approveMember(memberId: Int)
    
    @Query("DELETE FROM space_members WHERE id = :memberId")
    suspend fun rejectMember(memberId: Int)
    
    @Query("UPDATE space_members SET status = 'left' WHERE id = :memberId")
    suspend fun removeMember(memberId: Int)

    @Query("UPDATE space_members SET status = :status WHERE id = :memberId")
    suspend fun updateMemberStatus(memberId: Int, status: String)
    
    @Query("UPDATE space_members SET status = 'left' WHERE spaceId = :spaceId AND userId = :userId")
    suspend fun leaveSpace(spaceId: Int, userId: Int)
    
    @Transaction
    suspend fun transferAdmin(spaceId: Int, fromUserId: Int, toUserId: Int) {
        downgradeToMember(spaceId, fromUserId)
        upgradeToAdmin(spaceId, toUserId)
    }
    
    @Query("UPDATE space_members SET role = 'member' WHERE spaceId = :spaceId AND userId = :userId")
    suspend fun downgradeToMember(spaceId: Int, userId: Int)
    
    @Query("UPDATE space_members SET role = 'admin' WHERE spaceId = :spaceId AND userId = :userId")
    suspend fun upgradeToAdmin(spaceId: Int, userId: Int)
    
    @Query("UPDATE space_members SET role = :role WHERE spaceId = :spaceId AND userId = :userId")
    suspend fun updateMemberRole(spaceId: Int, userId: Int, role: String)
    
    @Query("SELECT COUNT(*) FROM space_members WHERE spaceId = :spaceId AND status = 'active'")
    suspend fun getSpaceMemberCount(spaceId: Int): Int

    @Query("UPDATE space_members SET status = :status WHERE spaceId = :spaceId")
    suspend fun updateMembersStatusBySpace(spaceId: Int, status: String)
    
    @Query("UPDATE spaces SET checkInIntervalSeconds = :intervalSeconds WHERE id = :spaceId")
    suspend fun updateCheckInInterval(spaceId: Int, intervalSeconds: Long)
    
    @Query("SELECT MAX(timestamp) FROM space_posts WHERE spaceId = :spaceId AND authorId = :userId AND status = 'normal'")
    suspend fun getLastPostTimeByUser(spaceId: Int, userId: Int): Long?
}
