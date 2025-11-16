package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 好友关系实体类
 * 表示用户之间的好友请求与状态
 */
@Serializable
@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // 发起方
    val senderId: Int,

    // 接收方
    val receiverId: Int,

    // 状态：pending / accepted / rejected
    val status: String = "pending",

    // 发起方对接收方的备注
    val senderRemark: String? = null,

    // 接收方对发起方的备注
    val receiverRemark: String? = null,

    // 时间戳（可选）
    val createdAt: Long = System.currentTimeMillis()
)
