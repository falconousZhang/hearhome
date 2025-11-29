package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 情侣关系表实体
 * 记录情侣关系的完整信息，包括请求状态
 */
@Serializable
@Entity(tableName = "couples")
data class Couple(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val requesterId: Int,
    val partnerId: Int,
    val status: String = "pending", // pending, accepted, rejected
    val createdAt: Long,
    var requesterRemark: String? = null,
    var partnerRemark: String? = null
)
