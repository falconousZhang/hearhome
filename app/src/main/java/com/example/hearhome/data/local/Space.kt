package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 空间实体类
 * 表示情侣空间或家族空间的基本信息
 * 
 * 空间类型:
 * - couple: 情侣空间(双人专属)
 * - family: 家族空间(多人共享)
 */
@Entity(tableName = "spaces")
data class Space(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // 空间名称
    val name: String,
    
    // 空间类型: couple / family
    val type: String,
    
    // 空间描述
    val description: String? = null,
    
    // 创建者(管理员)ID
    val creatorId: Int,
    
    // 空间唯一识别码(6位数字,用于加入空间)
    val inviteCode: String,
    
    // 空间封面颜色
    val coverColor: String = "#FF9800",
    
    // 创建时间
    val createdAt: Long = System.currentTimeMillis(),
    
    // 空间状态: active / archived
    val status: String = "active"
)
