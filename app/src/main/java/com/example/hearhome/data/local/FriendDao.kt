package com.example.hearhome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * 好友关系 DAO
 * 定义好友申请与查询操作
 */
@Dao
interface FriendDao {

    // 发起好友请求
    @Insert
    suspend fun insertRequest(friend: Friend): Long

    // 更新好友关系实体（用于修改备注等）
    @Update
    suspend fun updateFriend(friend: Friend)

    // 接收方：获取待处理请求列表
    @Query("SELECT * FROM friends WHERE receiverId = :userId AND status = 'pending'")
    suspend fun getPendingRequests(userId: Int): List<Friend>

    // 查询所有已成为好友的记录
    @Query("SELECT * FROM friends WHERE (senderId = :userId OR receiverId = :userId) AND status = 'accepted'")
    suspend fun getAcceptedFriends(userId: Int): List<Friend>

    // 同意好友请求
    @Query("UPDATE friends SET status = 'accepted' WHERE id = :requestId")
    suspend fun acceptRequest(requestId: Int): Int

    // 拒绝好友请求
    @Query("UPDATE friends SET status = 'rejected' WHERE id = :requestId")
    suspend fun rejectRequest(requestId: Int): Int

    // 检查两人之间是否已有关系记录
    @Query("SELECT * FROM friends WHERE (senderId = :userA AND receiverId = :userB) OR (senderId = :userB AND receiverId = :userA) LIMIT 1")
    suspend fun getRelationship(userA: Int, userB: Int): Friend?

    @Query("SELECT * FROM friends WHERE (senderId = :userId OR receiverId = :userId) AND status = 'accepted'")
    fun getFriendsOf(userId: Int): List<Friend>

    // 获取当前用户收到的待处理好友请求数量
    @Query("SELECT COUNT(*) FROM friends WHERE receiverId = :userId AND status = 'pending'")
    suspend fun getPendingRequestsCount(userId: Int): Int

    // 删除好友
    @Query("DELETE FROM friends WHERE (senderId = :userId1 AND receiverId = :userId2) OR (senderId = :userId2 AND receiverId = :userId1) AND status = 'accepted'")
    suspend fun deleteFriend(userId1: Int, userId2: Int)

    // 获取好友及用户信息
    @Transaction
    @Query("SELECT * FROM friends WHERE (senderId = :userId OR receiverId = :userId) AND status = 'accepted'")
    suspend fun getAcceptedFriendsWithUsers(userId: Int): List<FriendWithUser>
}