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
     * 标记为已读（用户选择"已读"按钮）
     * @param postId 动态ID
     * @param userId 查看的用户ID
     * @param viewedTime 响应时间
     */
    @Query("""
        UPDATE post_mentions 
        SET viewedAt = :viewedTime, status = 'viewed'
        WHERE postId = :postId AND mentionedUserId = :userId AND status = 'pending'
    """)
    suspend fun markAsViewed(postId: Int, userId: Int, viewedTime: Long)
    
    /**
     * 标记为忽略（用户选择"忽略"按钮）
     * @param postId 动态ID
     * @param userId 用户ID
     * @param ignoredTime 忽略时间
     */
    @Query("""
        UPDATE post_mentions 
        SET viewedAt = :ignoredTime, status = 'ignored'
        WHERE postId = :postId AND mentionedUserId = :userId AND status = 'pending'
    """)
    suspend fun markAsIgnored(postId: Int, userId: Int, ignoredTime: Long)
    
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
     * 获取用户待处理提醒数量（用于底部导航栏小红点）
     * 返回 Flow 以实时更新
     */
    @Query("""
        SELECT COUNT(*) FROM post_mentions
        WHERE mentionedUserId = :userId 
          AND status = 'pending'
          AND viewedAt IS NULL
    """)
    fun getPendingMentionCountFlow(userId: Int): Flow<Int>
    
/**
 * 获取用户待处理提醒数量（一次性查询）
 */
@Query("""
    SELECT COUNT(*) FROM post_mentions
    WHERE mentionedUserId = :userId 
      AND status = 'pending'
      AND viewedAt IS NULL
""")
suspend fun getPendingMentionCount(userId: Int): Int

/**
 * 获取用户在指定空间的待处理提醒数量（一次性查询）
 */
@Query("""
    SELECT COUNT(*) FROM post_mentions pm
    INNER JOIN space_posts sp ON pm.postId = sp.id
    WHERE pm.mentionedUserId = :userId 
      AND pm.status = 'pending'
      AND pm.viewedAt IS NULL
      AND sp.spaceId = :spaceId
""")
suspend fun getPendingMentionCountBySpace(userId: Int, spaceId: Int): Int

/**
 * 获取用户在指定空间的待处理提醒数量（Flow实时更新）
 */
@Query("""
    SELECT COUNT(*) FROM post_mentions pm
    INNER JOIN space_posts sp ON pm.postId = sp.id
    WHERE pm.mentionedUserId = :userId 
      AND pm.status = 'pending'
      AND pm.viewedAt IS NULL
      AND sp.spaceId = :spaceId
""")
fun getPendingMentionCountBySpaceFlow(userId: Int, spaceId: Int): Flow<Int>

/**
 * 一键标记某个空间内的所有待处理提醒为已读
 */
@Query("""
    UPDATE post_mentions 
    SET viewedAt = :viewedTime, status = 'viewed'
    WHERE mentionedUserId = :userId
      AND status = 'pending'
      AND viewedAt IS NULL
      AND postId IN (SELECT id FROM space_posts WHERE spaceId = :spaceId)
""")
suspend fun markAllAsViewedBySpace(userId: Int, spaceId: Int, viewedTime: Long)

/**
 * 一键标记用户所有待处理提醒为已读
 */
@Query("""
    UPDATE post_mentions 
    SET viewedAt = :viewedTime, status = 'viewed'
    WHERE mentionedUserId = :userId
      AND status = 'pending'
      AND viewedAt IS NULL
""")
suspend fun markAllAsViewed(userId: Int, viewedTime: Long)    

    /**
     * 获取用户的超时未查看提醒（用于弹窗通知）
     * 注意：这个查询只返回 status='pending' 且已超时的提醒
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
     * 获取用户即将超时的提醒（用于预警）
     * 返回距离超时不超过指定秒数的提醒
     */
    @Query("""
        SELECT * FROM post_mentions
        WHERE mentionedUserId = :userId 
          AND status = 'pending'
          AND viewedAt IS NULL
          AND (createdAt + timeoutSeconds * 1000) > :currentTime
          AND (createdAt + timeoutSeconds * 1000) <= :currentTime + :thresholdMillis
    """)
    suspend fun getNearlyExpiredMentions(userId: Int, currentTime: Long, thresholdMillis: Long): List<PostMention>
    
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
 * 检查用户在某条动态的提醒是否已过期
 * 返回提醒记录（如果存在且是pending状态）
 */
@Query("""
    SELECT * FROM post_mentions
    WHERE postId = :postId 
      AND mentionedUserId = :userId 
    LIMIT 1
""")
suspend fun getMentionForPost(postId: Int, userId: Int): PostMention?    /**
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
    
    // ============== 作者管理@提醒相关方法 ==============
    
    /**
     * 获取某条动态的所有提醒记录（作者用于管理）
     * 按创建时间倒序排列
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
    suspend fun getMentionsByPost(postId: Int): List<PostMentionWithUser>
    
    /**
     * 删除单条@提醒（作者撤回）
     * @param mentionId 提醒ID
     */
    @Query("DELETE FROM post_mentions WHERE id = :mentionId")
    suspend fun deleteMention(mentionId: Int)
    
    /**
     * 更新@提醒的倒计时时间（作者修改）
     * 同时更新 createdAt 为当前时间，使新的倒计时从修改时刻开始计算
     * @param mentionId 提醒ID
     * @param newTimeoutSeconds 新的倒计时秒数（从现在起）
     * @param newCreatedAt 新的起始时间（通常传入当前时间）
     */
    @Query("""
        UPDATE post_mentions 
        SET timeoutSeconds = :newTimeoutSeconds, createdAt = :newCreatedAt
        WHERE id = :mentionId
    """)
    suspend fun updateMentionTimeout(mentionId: Int, newTimeoutSeconds: Long, newCreatedAt: Long)
    
    /**
     * 批量添加新的@提醒到已有动态
     * 当作者编辑动态添加新成员时使用
     */
    @Insert
    suspend fun addMentionsToPost(mentions: List<PostMention>)
    
    /**
     * 检查某条动态是否为指定用户发布
     * 用于验证作者权限
     */
    @Query("""
        SELECT COUNT(*) FROM post_mentions 
        WHERE postId = :postId AND mentionerUserId = :userId
    """)
    suspend fun isPostMentioner(postId: Int, userId: Int): Int
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
