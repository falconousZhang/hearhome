package com.example.hearhome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * 情侣关系 DAO
 * 管理情侣申请、确认与解除逻辑
 */
@Dao
interface CoupleDao {

    // 发起情侣申请
    @Insert
    suspend fun insertRequest(couple: Couple): Long

    // 查询某用户收到的情侣请求
    @Query("SELECT * FROM couples WHERE partnerId = :userId AND status = 'pending'")
    suspend fun getPendingRequests(userId: Int): List<Couple>

    // 查询某用户发起的情侣请求
    @Query("SELECT * FROM couples WHERE requesterId = :userId AND status = 'pending'")
    suspend fun getSentRequests(userId: Int): List<Couple>

    // 接受情侣申请
    @Query("UPDATE couples SET status = 'accepted' WHERE id = :requestId")
    suspend fun acceptRequest(requestId: Int): Int

    // 拒绝情侣申请
    @Query("UPDATE couples SET status = 'rejected' WHERE id = :requestId")
    suspend fun rejectRequest(requestId: Int): Int

    // 解除情侣关系（双方任一方可执行）
    @Query("DELETE FROM couples WHERE (requesterId = :userId OR partnerId = :userId) AND status = 'accepted'")
    suspend fun deleteRelationship(userId: Int): Int

    // 查询当前情侣对象
    @Query("SELECT * FROM couples WHERE (requesterId = :userId OR partnerId = :userId) AND status = 'accepted' LIMIT 1")
    suspend fun getCurrentCouple(userId: Int): Couple?

    // 查询两人之间是否存在任何情侣关系（无论状态）
    @Query("SELECT * FROM couples WHERE (requesterId = :userA AND partnerId = :userB) OR (requesterId = :userB AND partnerId = :userA) LIMIT 1")
    suspend fun getCoupleRelationship(userA: Int, userB: Int): Couple?

    @Query("""
        DELETE FROM couples 
        WHERE (requesterId = :userA AND partnerId = :userB) 
           OR (requesterId = :userB AND partnerId = :userA)
    """)
    suspend fun deleteRelationshipBetween(userA: Int, userB: Int)
}
