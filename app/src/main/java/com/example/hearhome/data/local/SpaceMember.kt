package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 空间成员实体类
 * 记录用户与空间的关系
 */
@Entity(tableName = "space_members")
data class SpaceMember(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // 空间ID
    val spaceId: Int,
    
    // 成员用户ID
    val userId: Int,
    
    // 角色: owner(所有者) / admin(管理员) / member(普通成员)
    val role: String = "member",
    
    // 成员昵称(在该空间的显示名)
    val nickname: String? = null,
    
    // 加入时间
    val joinedAt: Long = System.currentTimeMillis(),
    
    // 成员状态: active / pending(待审核) / left(已退出)
    val status: String = "active"
)
