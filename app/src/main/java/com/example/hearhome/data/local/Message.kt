package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val senderId: Int,
    val receiverId: Int,
    val content: String? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val audioDuration: Long? = null,
    val timestamp: Long,
    val isRead: Boolean = false
)