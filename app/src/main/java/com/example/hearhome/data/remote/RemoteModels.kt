package com.example.hearhome.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class GenericResponse(
    val success: Boolean,
    val message: String
)

/** 个人中心：修改密码 */
@Serializable
data class UpdatePasswordRequest(
    val email: String,
    val oldPassword: String,
    val securityAnswer: String,
    val newPassword: String
)

/** 个人中心：设置/修改密保 */
@Serializable
data class UpdateSecurityQuestionRequest(
    val email: String,
    val password: String,
    val question: String,
    val answer: String
)

/** 忘记密码：取密保问题 */
@Serializable
data class ResetQuestionRequest(
    val email: String
)

/** 忘记密码：提交答案 + 新密码 */
@Serializable
data class ResetPasswordRequest(
    val email: String,
    val answer: String,
    val newPassword: String
)

/** 忘记密码：后端返回的密保问题 */
@Serializable
data class SecurityQuestionResponse(
    val question: String
)
