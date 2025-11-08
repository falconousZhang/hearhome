package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 通用媒体附件实体，用于空间动态、评论以及私聊消息
 */
@Entity(
    tableName = "media_attachments",
    indices = [Index(value = ["ownerType", "ownerId"])]
)
data class MediaAttachment(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ownerType: String,
    val ownerId: Int,
    val type: String,
    val uri: String,
    val duration: Long? = null,
    val extra: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 附件所属对象类型常量
 */
object AttachmentOwnerType {
    const val SPACE_POST = "space_post"
    const val POST_COMMENT = "post_comment"
    const val CHAT_MESSAGE = "chat_message"
}
