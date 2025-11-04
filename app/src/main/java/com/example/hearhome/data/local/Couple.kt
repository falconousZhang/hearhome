package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "couples")
data class Couple(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val requesterId: Int,
    val partnerId: Int,

    val status: String = "pending",

    // 申请者对伴侣的备注
    val requesterRemark: String? = null,

    // 伴侣对申请者的备注
    val partnerRemark: String? = null,

    val createdAt: Long = System.currentTimeMillis()
)
