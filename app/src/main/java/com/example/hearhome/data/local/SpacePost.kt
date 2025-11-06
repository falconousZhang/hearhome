package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 空间动态实体类
 * 记录用户在空间发布的内容(类似朋友圈)
 */
@Entity(tableName = "space_posts")
data class SpacePost(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // 所属空间ID
    val spaceId: Int,
    
    // 发布者用户ID
    val authorId: Int,
    
    // 动态内容(文字)
    val content: String,
    
    // 图片列表(JSON格式存储路径数组)
    val images: String? = null,
    
    // 定位信息
    val location: String? = null,
    
    // 发布时间
    val timestamp: Long = System.currentTimeMillis(),
    
    // 点赞数
    val likeCount: Int = 0,
    
    // 评论数
    val commentCount: Int = 0,
    
    // 动态状态: normal / deleted
    val status: String = "normal"
)
