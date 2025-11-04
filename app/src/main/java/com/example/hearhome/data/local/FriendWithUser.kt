package com.example.hearhome.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class FriendWithUser(
    @Embedded
    val friend: Friend,

    @Relation(
        parentColumn = "senderId",
        entityColumn = "uid"
    )
    val sender: User,

    @Relation(
        parentColumn = "receiverId",
        entityColumn = "uid"
    )
    val receiver: User
)