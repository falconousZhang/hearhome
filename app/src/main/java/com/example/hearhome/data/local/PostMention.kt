package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 动态提醒实体类
 * 记录用户对动态的提醒和查看状态
 * 
 * 业务逻辑：
 * 1. 发布动态时，发布者可以选择提醒特定用户查看
 * 2. 被提醒用户可以选择"已读"（接受打卡）或"忽略"（拒绝打卡）
 * 3. 如果超过设定时间未响应，系统弹窗提醒
 * 
 * 状态说明：
 * - pending: 待响应，用户尚未操作
 * - viewed: 已读，用户选择了"已读"表示接受打卡
 * - ignored: 忽略，用户选择了"忽略"表示拒绝打卡
 * - expired: 已超时，用户在规定时间内未响应
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
    
    // 响应时间（null表示未响应）
    val viewedAt: Long? = null,
    
    // 最后一次弹窗提醒的时间（null表示未提醒过）
    val lastNotifiedAt: Long? = null,
    
    // 提醒状态：pending(待响应), viewed(已读/接受), ignored(忽略/拒绝), expired(已超时未响应)
    val status: String = "pending"
)
