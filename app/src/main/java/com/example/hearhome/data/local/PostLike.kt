package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 动态点赞实体类
 * 记录用户对空间动态的点赞
 */
@Entity(tableName = "post_likes")
data class PostLike(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // 动态ID
    val postId: Int,
    
    // 点赞用户ID
    val userId: Int,
    
    // 点赞时间
    val timestamp: Long = System.currentTimeMillis()
)
