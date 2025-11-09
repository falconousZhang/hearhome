package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 动态收藏实体类
 * 记录用户收藏的动态
 */
@Entity(tableName = "post_favorites")
data class PostFavorite(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // 用户ID
    val userId: Int,
    
    // 动态ID
    val postId: Int,
    
    // 收藏时间
    val timestamp: Long = System.currentTimeMillis(),
    
    // 备注（可选）
    val note: String? = null
)
