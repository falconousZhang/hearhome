package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 动态提醒实体类
 * 记录用户对动态的提醒和查看状态
 * 
 * 业务逻辑：
 * 1. 发布动态时，发布者可以选择提醒特定用户查看
 * 2. 被提醒用户打开动态详情时，自动记录查看时间
 * 3. 如果超过设定时间未查看，系统弹窗提醒
 */
@Entity(tableName = "post_mentions")
data class PostMention(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // 动态ID
    val postId: Int,
    
    // 被提醒的用户ID
    val mentionedUserId: Int,
    
    // 发起提醒的用户ID（通常是动态作者）
    val mentionerUserId: Int,
    
    // 超时时间（秒），被提醒用户需要在此时间内查看动态
    val timeoutSeconds: Long,
    
    // 提醒创建时间
    val createdAt: Long = System.currentTimeMillis(),
    
    // 查看时间（null表示未查看）
    val viewedAt: Long? = null,
    
    // 最后一次弹窗提醒的时间（null表示未提醒过）
    val lastNotifiedAt: Long? = null,
    
    // 提醒状态：pending(待查看), viewed(已查看), expired(已超时未查看)
    val status: String = "pending"
)
