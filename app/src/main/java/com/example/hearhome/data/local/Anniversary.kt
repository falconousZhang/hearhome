package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 纪念日实体
 * status: pending(待对方确认)、active(已生效)
 * style: simple / ring / card / flip / capsule
 */
@Entity(tableName = "anniversaries")
data class Anniversary(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val spaceId: Int,
    val name: String,
    val dateMillis: Long,        // 目标时间（含时分）
    val style: String = "simple",
    val creatorUserId: Int,
    val status: String = "pending",
    val createdAt: Long = System.currentTimeMillis()
)
