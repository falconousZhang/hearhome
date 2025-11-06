package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 动态评论实体类
 * 记录用户对空间动态的评论
 */
@Entity(tableName = "post_comments")
data class PostComment(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // 所属动态ID
    val postId: Int,
    
    // 评论者用户ID
    val authorId: Int,
    
    // 评论内容
    val content: String,
    
    // 语音文件路径（可选）
    val audioPath: String? = null,
    
    // 语音时长（毫秒）
    val audioDuration: Long? = null,
    
    // 回复目标用户ID(如果是回复某人的评论)
    val replyToUserId: Int? = null,
    
    // 评论时间
    val timestamp: Long = System.currentTimeMillis(),
    
    // 评论状态: normal / deleted
    val status: String = "normal"
)
