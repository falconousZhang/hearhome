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
    val newPassword: String,
    val securityAnswer: String? = null,
    val emailCode: String? = null,
    val verificationToken: String? = null
)

/** 个人中心：设置/修改密保 */
@Serializable
data class UpdateSecurityQuestionRequest(
    val email: String,
    val password: String,
    val question: String,
    val answer: String,
    val emailCode: String? = null,
    val verificationToken: String? = null
)

/** 忘记密码：取密保问题 */
@Serializable
data class ResetQuestionRequest(
    val email: String
)

/** 忘记密码：提交答案 + 新密码（安全问题路径） */
@Serializable
data class ResetPasswordRequest(
    val email: String,
    val answer: String,
    val newPassword: String,
    val confirmPassword: String,
    val newEmail: String? = null,
    val method: String
)

/** 忘记密码：邮箱验证码 + 新密码 */
@Serializable
data class ResetPasswordByEmailRequest(
    val email: String,
    val emailCode: String,
    val newPassword: String,
    val confirmPassword: String
)

/** 忘记密码：发送验证码 */
@Serializable
data class ResetPasswordSendCodeRequest(
    val email: String
)

/** 请求发送邮箱验证码 */
@Serializable
data class EmailVerificationRequest(
    val email: String,
    val purpose: String
)

/** 提交邮箱验证码 */
@Serializable
data class EmailVerificationConfirmRequest(
    val email: String,
    val purpose: String,
    val code: String
)

/** 统一验证码/风控响应，供前端判定下一步动作 */
@Serializable
data class VerificationStatusResponse(
    val code: String? = null,
    val status: String? = null,
    val message: String? = null,
    val nextStep: String? = null
)

/** 忘记密码：后端返回的密保问题 */
@Serializable
data class SecurityQuestionResponse(
    val question: String
)

/* ---------- Anniversary (纪念日) Models ---------- */

@Serializable
data class ApiAnniversary(
    val id: Int = 0,
    val spaceId: Int,
    val name: String,
    val dateMillis: Long,
    val style: String = "simple",
    val status: String = "pending", // pending, confirmed, active
    val creatorUserId: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class CreateAnniversaryRequest(
    val spaceId: Int,
    val name: String,
    val dateMillis: Long,
    val style: String,
    val creatorUserId: Int
)
