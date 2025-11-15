package com.example.hearhome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 用户实体类（Room 数据库表）
 * 存储注册用户的基础信息、密保信息以及社交状态
 */
@Serializable
@Entity(tableName = "users")
data class User(
    // 主键，自增
    @PrimaryKey(autoGenerate = true)
    val uid: Int = 0,

    // 用户登录邮箱（唯一标识）
    val email: String,

    // 登录密码（当前版本为明文存储，建议后续改为加密存储）
    val password: String,

    // 密保问题
    val secQuestion: String = "",

    // 密保答案哈希
    val secAnswerHash: String = "",

    // 昵称（展示用，可通过它或邮箱搜索）
    val nickname: String = "",

    // 性别
    val gender: String = "Not specified",

    // 头像颜色，以十六进制字符串存储（例如 \"#FF0000\")
    val avatarColor: String = "#CCCCCC",

    // 当前关系状态：single / in_relationship / hidden 等
    val relationshipStatus: String = "single",

    // 绑定情侣的 uid（没有情侣则为 null）
    val partnerId: Int? = null
)
