package com.example.hearhome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * 动态提醒 DAO
 * 管理动态的@提醒功能
 */
@Dao
interface PostMentionDao {
    
    /**
     * 创建提醒记录
     */
    @Insert
    suspend fun insertMention(mention: PostMention): Long
    
    /**
     * 批量创建提醒记录
     */
    @Insert
    suspend fun insertMentions(mentions: List<PostMention>)
    
    /**
     * 标记为已查看
     * @param postId 动态ID
     * @param userId 查看的用户ID
     * @param viewedTime 查看时间
     */
    @Query("""
        UPDATE post_mentions 
        SET viewedAt = :viewedTime, status = 'viewed'
        WHERE postId = :postId AND mentionedUserId = :userId AND status = 'pending'
    """)
    suspend fun markAsViewed(postId: Int, userId: Int, viewedTime: Long)
    
    /**
     * 更新最后通知时间
     */
    @Query("""
        UPDATE post_mentions 
        SET lastNotifiedAt = :notifiedTime
        WHERE id = :mentionId
    """)
    suspend fun updateLastNotifiedTime(mentionId: Int, notifiedTime: Long)
    
    /**
     * 获取某条动态的所有提醒记录（带用户信息）
     */
    @Transaction
    @Query("""
        SELECT 
            pm.id, 
            pm.postId, 
            pm.mentionedUserId, 
            pm.mentionerUserId, 
            pm.timeoutSeconds, 
            pm.createdAt, 
            pm.viewedAt, 
            pm.lastNotifiedAt, 
            pm.status,
            u.nickname, 
            u.avatarColor
        FROM post_mentions pm
        LEFT JOIN users u ON pm.mentionedUserId = u.uid
        WHERE pm.postId = :postId
        ORDER BY pm.createdAt DESC
    """)
    fun getMentionsWithUserInfo(postId: Int): Flow<List<PostMentionWithUser>>
    
    /**
     * 获取用户的待查看提醒（用于弹窗）
     */
    @Query("""
        SELECT * FROM post_mentions
        WHERE mentionedUserId = :userId 
          AND status = 'pending'
          AND viewedAt IS NULL
        ORDER BY createdAt ASC
    """)
    suspend fun getPendingMentions(userId: Int): List<PostMention>
    
    /**
     * 获取用户的超时未查看提醒
     * @param currentTime 当前时间戳
     */
    @Query("""
        SELECT * FROM post_mentions
        WHERE mentionedUserId = :userId 
          AND status = 'pending'
          AND viewedAt IS NULL
          AND (createdAt + timeoutSeconds * 1000) <= :currentTime
    """)
    suspend fun getExpiredMentions(userId: Int, currentTime: Long): List<PostMention>
    
    /**
     * 标记提醒为已过期
     */
    @Query("""
        UPDATE post_mentions 
        SET status = 'expired'
        WHERE id = :mentionId
    """)
    suspend fun markAsExpired(mentionId: Int)
    
    /**
     * 检查用户是否有某条动态的待查看提醒
     */
    @Query("""
        SELECT COUNT(*) FROM post_mentions
        WHERE postId = :postId 
          AND mentionedUserId = :userId 
          AND status = 'pending'
          AND viewedAt IS NULL
    """)
    suspend fun hasPendingMention(postId: Int, userId: Int): Int
    
    /**
     * 获取动态的提醒统计
     */
    @Query("""
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN viewedAt IS NOT NULL THEN 1 ELSE 0 END) as viewed,
            SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) as pending
        FROM post_mentions
        WHERE postId = :postId
    """)
    suspend fun getMentionStats(postId: Int): MentionStats
    
    /**
     * 删除动态时，删除相关的所有提醒
     */
    @Query("DELETE FROM post_mentions WHERE postId = :postId")
    suspend fun deleteMentionsByPost(postId: Int)
}

/**
 * 提醒记录（带用户信息）
 */
data class PostMentionWithUser(
    val id: Int,
    val postId: Int,
    val mentionedUserId: Int,
    val mentionerUserId: Int,
    val timeoutSeconds: Long,
    val createdAt: Long,
    val viewedAt: Long?,
    val lastNotifiedAt: Long?,
    val status: String,
    val nickname: String?,
    val avatarColor: String?
)

/**
 * 提醒统计数据
 */
data class MentionStats(
    val total: Int,     // 总提醒数
    val viewed: Int,    // 已查看数
    val pending: Int    // 待查看数
)
