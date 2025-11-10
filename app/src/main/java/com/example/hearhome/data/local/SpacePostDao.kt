package com.example.hearhome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * 空间动态 DAO
 * 管理空间内的动态发布、点赞、评论等
 */
@Dao
interface SpacePostDao {

    // ==================== 新增：系统/普通动态通用插入 ====================

    /**
     * 通用插入（支持系统提醒贴 status="system"）。
     * ReminderReceiver 会调用这个方法把纪念日提醒写入空间。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: SpacePost): Long

    // ==================== 动态管理 ====================

    /**
     * 发布新动态（你原有的业务入口，保留）
     */
    @Insert
    suspend fun createPost(post: SpacePost): Long

    /**
     * 查询空间的动态
     * ✅ 修改：包含普通帖 + 系统提醒帖；过滤掉已删除
     */
    @Query(
        """
        SELECT * FROM space_posts
        WHERE spaceId = :spaceId AND status IN ('normal','system')
        ORDER BY timestamp DESC
        """
    )
    fun getSpacePosts(spaceId: Int): Flow<List<SpacePost>>

    /**
     * 查询某条动态详情
     */
    @Query("SELECT * FROM space_posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: Int): SpacePost?

    /**
     * 逻辑删除（原有）
     */
    @Query("UPDATE space_posts SET status = 'deleted' WHERE id = :postId")
    suspend fun deletePost(postId: Int)

    /**
     * 可选：物理删除（如果你在某些场景想直接移除）
     */
    @Query("DELETE FROM space_posts WHERE id = :postId")
    suspend fun deleteById(postId: Int)

    /**
     * 更新动态点赞数
     */
    @Query("UPDATE space_posts SET likeCount = likeCount + :delta WHERE id = :postId")
    suspend fun updateLikeCount(postId: Int, delta: Int)

    /**
     * 更新动态评论数
     */
    @Query("UPDATE space_posts SET commentCount = commentCount + :delta WHERE id = :postId")
    suspend fun updateCommentCount(postId: Int, delta: Int)

    // ==================== 点赞管理 ====================

    /**
     * 点赞动态
     */
    @Insert
    suspend fun likePost(like: PostLike): Long

    /**
     * 取消点赞
     */
    @Query("DELETE FROM post_likes WHERE postId = :postId AND userId = :userId")
    suspend fun unlikePost(postId: Int, userId: Int)

    /**
     * 检查用户是否已点赞
     */
    @Query("SELECT COUNT(*) FROM post_likes WHERE postId = :postId AND userId = :userId")
    suspend fun hasLiked(postId: Int, userId: Int): Int

    /**
     * 查询某动态的所有点赞用户
     */
    @Query("SELECT * FROM post_likes WHERE postId = :postId ORDER BY timestamp DESC")
    suspend fun getPostLikes(postId: Int): List<PostLike>

    // ==================== 评论管理 ====================

    /**
     * 发布评论
     */
    @Insert
    suspend fun createComment(comment: PostComment): Long

    /**
     * 查询某动态的所有评论（流式）
     */
    @Query(
        """
        SELECT * FROM post_comments
        WHERE postId = :postId AND status = 'normal'
        ORDER BY timestamp ASC
        """
    )
    fun getPostComments(postId: Int): Flow<List<PostComment>>

    /**
     * 一次性查询评论列表（用于清理）
     */
    @Query(
        """
        SELECT * FROM post_comments
        WHERE postId = :postId AND status = 'normal'
        ORDER BY timestamp ASC
        """
    )
    suspend fun getPostCommentsOnce(postId: Int): List<PostComment>

    /**
     * 删除评论（逻辑删除）
     */
    @Query("UPDATE post_comments SET status = 'deleted' WHERE id = :commentId")
    suspend fun deleteComment(commentId: Int)

    /**
     * 点赞切换（事务）
     */
    @Transaction
    suspend fun toggleLike(postId: Int, userId: Int) {
        val liked = hasLiked(postId, userId) > 0
        if (liked) {
            unlikePost(postId, userId)
            updateLikeCount(postId, -1)
        } else {
            likePost(PostLike(postId = postId, userId = userId))
            updateLikeCount(postId, 1)
        }
    }

    /**
     * 发布评论并更新计数（事务）
     */
    @Transaction
    suspend fun addCommentWithCount(comment: PostComment): Long {
        val commentId = createComment(comment)
        updateCommentCount(comment.postId, 1)
        return commentId
    }
}
