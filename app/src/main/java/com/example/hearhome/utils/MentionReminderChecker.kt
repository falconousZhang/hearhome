package com.example.hearhome.utils

import android.content.Context
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.data.local.PostMention
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 动态提醒检查工具
 * 用于检查用户是否有超时未查看的提醒
 */
object MentionReminderChecker {
    
    /**
     * 检查用户的超时提醒
     * @param context 上下文
     * @param userId 当前用户ID
     * @return 超时且未查看的提醒列表
     */
    suspend fun checkExpiredMentions(context: Context, userId: Int): List<MentionWithPost> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val currentTime = System.currentTimeMillis()
            
            // 获取超时的提醒
            val expiredMentions = db.postMentionDao().getExpiredMentions(userId, currentTime)
            
            // 获取每个提醒对应的动态信息
            expiredMentions.mapNotNull { mention ->
                val post = db.spacePostDao().getPostById(mention.postId)
                val mentioner = db.userDao().getUserById(mention.mentionerUserId)
                
                if (post != null && mentioner != null) {
                    MentionWithPost(
                        mention = mention,
                        postContent = post.content,
                        postTimestamp = post.timestamp,
                        mentionerName = mentioner.nickname,
                        mentionerAvatar = mentioner.avatarColor
                    )
                } else null
            }
        }
    }
    
    /**
     * 标记提醒为已通知
     */
    suspend fun markAsNotified(context: Context, mentionId: Int) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            db.postMentionDao().updateLastNotifiedTime(mentionId, System.currentTimeMillis())
        }
    }
    
    /**
     * 标记提醒为已过期
     * 在倒计时结束后调用，使用户无法再进行"已读"操作
     */
    suspend fun markAsExpired(context: Context, mentionId: Int) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            db.postMentionDao().markAsExpired(mentionId)
        }
    }
}

/**
 * 提醒信息（包含动态内容）
 */
data class MentionWithPost(
    val mention: PostMention,
    val postContent: String,
    val postTimestamp: Long,
    val mentionerName: String,
    val mentionerAvatar: String
)
