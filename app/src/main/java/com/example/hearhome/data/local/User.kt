package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    val email: String,
    val password: String // Reminder: This should be a hash in a real app
)
