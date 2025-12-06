package com.example.hearhome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 动态收藏 DAO
 * 管理动态的收藏功能
 */
@Dao
interface PostFavoriteDao {
    
    /**
     * 收藏动态
     */
    @Insert
    suspend fun addFavorite(favorite: PostFavorite): Long
    
    /**
     * 取消收藏
     */
    @Query("DELETE FROM post_favorites WHERE userId = :userId AND postId = :postId")
    suspend fun removeFavorite(userId: Int, postId: Int)
    
    /**
     * 检查是否已收藏
     */
    @Query("SELECT COUNT(*) FROM post_favorites WHERE userId = :userId AND postId = :postId")
    suspend fun isFavorited(userId: Int, postId: Int): Int
    
    /**
     * 获取用户的所有收藏
     */
    @Query("""
        SELECT * FROM post_favorites 
        WHERE userId = :userId 
        ORDER BY timestamp DESC
    """)
    fun getUserFavorites(userId: Int): Flow<List<PostFavorite>>
    
    /**
     * 获取某条动态的收藏数
     */
    @Query("SELECT COUNT(*) FROM post_favorites WHERE postId = :postId")
    suspend fun getFavoriteCount(postId: Int): Int
    
    /**
     * 删除某条动态的所有收藏记录（当动态被删除时）
     */
    @Query("DELETE FROM post_favorites WHERE postId = :postId")
    suspend fun deleteByPostId(postId: Int)
    
    /**
     * 获取用户收藏的动态ID列表
     */
    @Query("SELECT postId FROM post_favorites WHERE userId = :userId ORDER BY timestamp DESC")
    fun getUserFavoritePostIds(userId: Int): Flow<List<Int>>

    /**
     * 响应式监听用户的收藏状态（用于触发UI更新）
     * 返回用户收藏的所有动态ID的Set
     */
    @Query("SELECT postId FROM post_favorites WHERE userId = :userId")
    fun observeUserFavoritedPostIds(userId: Int): Flow<List<Int>>
}
