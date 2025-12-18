package com.example.hearhome.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.User
import com.example.hearhome.data.local.UserDao
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.data.remote.LoginRequest
import com.example.hearhome.data.remote.GenericResponse
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AuthViewModel(
    private val userDao: UserDao
) : ViewModel() {

    /** 支持多种风控校验的业务目标。可扩展为手机号验证、双因素认证等。 */
    enum class VerificationPurpose { UPDATE_PASSWORD, UPDATE_SECURITY_QUESTION, RESET_PASSWORD;
        fun asServerValue(): String = name
    }

    private data class PendingPasswordUpdate(
        val email: String,
        val oldPassword: String,
        val newPassword: String
    )

    private data class PendingSecurityQuestionUpdate(
        val email: String,
        val password: String,
        val question: String,
        val answer: String
    )

    sealed class AuthState {
        object Idle : AuthState()
        object LoggedOut : AuthState() // ADDED: State for logged out
        object AwaitingInput : AuthState()
        object Loading : AuthState()
        data class Success(val user: User) : AuthState()
        data class RegisterSuccess(val user: User) : AuthState()
        object PasswordResetReady : AuthState()
        object PasswordResetSuccess : AuthState()
        object UpdateSuccess : AuthState()
        object PasswordUpdateSuccess : AuthState()
        data class EmailCodeRequired(val email: String, val purpose: VerificationPurpose, val reason: String? = null) : AuthState()
        data class EmailCodeSent(val email: String, val purpose: VerificationPurpose) : AuthState()
        data class EmailCodeVerified(val email: String, val purpose: VerificationPurpose) : AuthState()
        data class SecurityQuestionRequired(val email: String, val question: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private var passwordUpdateJob: Job? = null
    private var securityQuestionJob: Job? = null

    private var currentEmailForReset: String? = null
    private var currentSecQuestion: String? = null
    private var resetAnswerPlain: String? = null
    private var pendingResetEmail: String? = null
    private var pendingResetCode: String? = null

    private var pendingPasswordUpdate: PendingPasswordUpdate? = null
    private var pendingSecurityQuestionUpdate: PendingSecurityQuestionUpdate? = null

    companion object {
        private const val MAX_EMAIL_LENGTH = 25
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z0-9.-]+$")
        private val ENGLISH_LETTER_REGEX = Regex("[A-Za-z]")
        private val CHINESE_CHAR_REGEX = Regex("[\\p{IsHan}]")
    }

    private fun validateEmailInput(emailRaw: String): String? {
        val email = emailRaw.trim()
        if (email.isEmpty()) return "邮箱不能为空"
        if (email.length > MAX_EMAIL_LENGTH) return "邮箱长度不能超过 25 个字符"
        if (!EMAIL_REGEX.matches(email)) return "邮箱格式不正确"
        return null
    }

    private fun validatePasswordInput(passwordRaw: String, fieldLabel: String = "密码"): String? {
        val password = passwordRaw.trim()
        if (password.length < 6) return "${fieldLabel}至少需要 6 位"
        if (!ENGLISH_LETTER_REGEX.containsMatchIn(password)) return "${fieldLabel}必须包含英文字母"
        if (CHINESE_CHAR_REGEX.containsMatchIn(password)) return "${fieldLabel}不能包含中文字符"
        return null
    }

    /** ===== 给 MainActivity / LoginScreen 清一次性状态 ===== */
    fun resetAuthResult() {
        _authState.value = AuthState.Idle
        resetAnswerPlain = null
        pendingResetCode = null
        pendingPasswordUpdate = null
        pendingSecurityQuestionUpdate = null
        pendingResetEmail = null
    }

    /** =====（如果 UI 用到）ProfileScreen 消费完事件后回到可交互状态 ===== */
    fun onProfileEventConsumed() { _authState.value = AuthState.AwaitingInput }

    /** ===== 登出 ===== */
    fun logout() {
        viewModelScope.launch {
            // In a real app, you would clear tokens from SharedPreferences/DataStore here
            _authState.value = AuthState.LoggedOut
        }
    }

    /** ===== 登录（走后端） ===== */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                _authState.value = AuthState.Error("邮箱和密码不能为空")
                return@launch
            }
            _authState.value = AuthState.Loading
            try {
                val resp = ApiService.login(LoginRequest(email.trim(), password))
                when (resp.status) {
                    HttpStatusCode.OK -> _authState.value = AuthState.Success(resp.body<User>())
                    HttpStatusCode.BadRequest, HttpStatusCode.NotFound, HttpStatusCode.Unauthorized -> {
                        val gr = resp.body<com.example.hearhome.data.remote.GenericResponse>()
                        _authState.value = AuthState.Error(gr.message)
                    }
                    else -> _authState.value = AuthState.Error("登录失败：${resp.status.value}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("登录失败：${e.message}")
            }
        }
    }

    /** ===== 注册（走后端） ===== */
    fun register(
        email: String,
        password: String,
        secQuestion: String,
        secAnswer: String,
        nickname: String,
        gender: String
    ) {
        viewModelScope.launch {
             // 1. 输入验证
            if (nickname.isBlank() || email.isBlank() || password.isBlank()) {
                _authState.value = AuthState.Error("昵称、邮箱和密码不能为空")
                return@launch
            }
            if (secQuestion.isBlank() || secAnswer.isBlank()) {
                _authState.value = AuthState.Error("请设置密保问题和答案，用于找回密码")
                return@launch
            }
            val emailError = validateEmailInput(email)
            if (emailError != null) {
                _authState.value = AuthState.Error(emailError)
                return@launch
            }
            val passwordError = validatePasswordInput(password)
            if (passwordError != null) {
                _authState.value = AuthState.Error(passwordError)
                return@launch
            }

            val sanitizedEmail = email.trim()
            val sanitizedPassword = password.trim()
            val sanitizedNickname = nickname.trim()
            val sanitizedQuestion = secQuestion.trim()
            val sanitizedAnswer = secAnswer.trim()

            // 2. 设置加载状态
            _authState.value = AuthState.Loading

            try {
                val user = User(
                    // uid is not needed for registration, it will be generated by the backend
                    email = sanitizedEmail,
                    password = sanitizedPassword, // Password hashing stays server-side; value trimmed locally
                    nickname = sanitizedNickname,
                    secQuestion = sanitizedQuestion,
                    secAnswerHash = sanitizedAnswer, // MODIFIED: Corrected parameter name
                    gender = gender
                )

                // 3. 调用 API
                val response = ApiService.register(user)

                // 4. 处理响应
                if (response.status == HttpStatusCode.Created) {
                    val registeredUser = response.body<User>()
                    _authState.value = AuthState.RegisterSuccess(registeredUser)
                } else {
                    val errorBody = response.body<com.example.hearhome.data.remote.GenericResponse>()
                    _authState.value = AuthState.Error(errorBody.message)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("注册失败：${e.message}")
            }
        }
    }

    /** ===== 个人中心 → 修改密码（/users/update-password） ===== */
    fun updatePassword(
        email: String,
        oldPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        if (passwordUpdateJob?.isActive == true) {
            return
        }
        passwordUpdateJob = viewModelScope.launch {
            try {
                if (oldPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
                    _authState.value = AuthState.Error("请填写全部密码输入框")
                    return@launch
                }

                val sanitizedEmail = email.trim()
                val sanitizedNewPassword = newPassword.trim()
                val sanitizedConfirmPassword = confirmPassword.trim()

                val passwordError = validatePasswordInput(sanitizedNewPassword, "新密码")
                if (passwordError != null) {
                    _authState.value = AuthState.Error(passwordError)
                    return@launch
                }
                if (sanitizedNewPassword != sanitizedConfirmPassword) {
                    _authState.value = AuthState.Error("新密码与确认密码不一致")
                    return@launch
                }

                _authState.value = AuthState.Loading
                try {
                    val resp = ApiService.updatePassword(sanitizedEmail, oldPassword, sanitizedNewPassword)
                    when (resp.status) {
                        HttpStatusCode.OK -> {
                            pendingPasswordUpdate = null
                            _authState.value = AuthState.PasswordUpdateSuccess
                        }
                        else -> handleRiskResponse(
                            resp,
                            onNeedEmail = { reason ->
                                pendingPasswordUpdate = PendingPasswordUpdate(sanitizedEmail, oldPassword, sanitizedNewPassword)
                                requestEmailCode(sanitizedEmail, VerificationPurpose.UPDATE_PASSWORD, reason)
                            }
                        )
                    }
                } catch (e: Exception) {
                    _authState.value = AuthState.Error("修改失败：${e.message}")
                }
            } finally {
                passwordUpdateJob = null
            }
        }
    }

    /** ===== 个人中心 → 设置/修改密保（/users/update-security-question） ===== */
    fun updateSecurityQuestion(email: String, password: String, question: String, answer: String) {
        if (securityQuestionJob?.isActive == true) {
            return
        }
        securityQuestionJob = viewModelScope.launch {
            try {
                if (password.isBlank() || question.isBlank() || answer.isBlank()) {
                    _authState.value = AuthState.Error("密码、问题和答案不能为空"); return@launch
                }

                _authState.value = AuthState.Loading
                try {
                    val resp = ApiService.updateSecurityQuestion(email.trim(), password, question.trim(), answer.trim())
                    when (resp.status) {
                        HttpStatusCode.OK -> {
                            pendingSecurityQuestionUpdate = null
                            _authState.value = AuthState.UpdateSuccess
                        }
                        else -> handleRiskResponse(
                            resp,
                            onNeedEmail = { reason ->
                                pendingSecurityQuestionUpdate = PendingSecurityQuestionUpdate(email.trim(), password, question.trim(), answer.trim())
                                requestEmailCode(email.trim(), VerificationPurpose.UPDATE_SECURITY_QUESTION, reason)
                            }
                        )
                    }
                } catch (e: Exception) {
                    _authState.value = AuthState.Error("更新失败：${e.message}")
                }
            } finally {
                securityQuestionJob = null
            }
        }
    }


    /** ===== 忘记密码 Step 0：优先尝试邮箱验证码 ===== */
    fun startResetByEmail(email: String) {
        viewModelScope.launch {
            val emailError = validateEmailInput(email)
            if (emailError != null) { _authState.value = AuthState.Error(emailError); return@launch }

            val sanitizedEmail = email.trim()
            pendingResetEmail = sanitizedEmail
            pendingResetCode = null
            resetAnswerPlain = null
            currentSecQuestion = null
            _authState.value = AuthState.Loading
            try {
                val resp = ApiService.sendResetPasswordCode(sanitizedEmail)
                when (resp.status) {
                    HttpStatusCode.OK, HttpStatusCode.Accepted -> _authState.value = AuthState.EmailCodeSent(sanitizedEmail, VerificationPurpose.RESET_PASSWORD)
                    else -> handleRiskResponse(
                        resp,
                        onNeedEmail = { reason -> _authState.value = AuthState.Error(reason ?: "发送验证码失败：${resp.status.value}") },
                        onNeedSecurityQuestion = { startResetByQuestion(sanitizedEmail) }
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("发送验证码失败：${e.message}")
            }
        }
    }

    /** ===== 忘记密码 Step 1：获取密保问题（/users/reset-question） ===== */
    fun startResetByQuestion(email: String) {
        viewModelScope.launch {
            val emailError = validateEmailInput(email)
            if (emailError != null) { _authState.value = AuthState.Error(emailError); return@launch }

            val sanitizedEmail = email.trim()
            _authState.value = AuthState.Loading
            try {
                val resp = ApiService.fetchResetQuestion(sanitizedEmail)
                when (resp.status) {
                    HttpStatusCode.OK -> {
                        val q = resp.body<com.example.hearhome.data.remote.SecurityQuestionResponse>()
                        if (q.question.isBlank()) {
                            _authState.value = AuthState.Error("该账号未设置密保问题")
                        } else {
                            currentEmailForReset = sanitizedEmail
                            pendingResetEmail = sanitizedEmail
                            currentSecQuestion = q.question
                            _authState.value = AuthState.SecurityQuestionRequired(sanitizedEmail, q.question)
                        }
                    }
                    HttpStatusCode.NotFound, HttpStatusCode.BadRequest -> {
                        val gr = resp.body<com.example.hearhome.data.remote.GenericResponse>()
                        _authState.value = AuthState.Error(gr.message)
                    }
                    else -> _authState.value = AuthState.Error("获取密保问题失败：${resp.status.value}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("获取密保问题失败：${e.message}")
            }
        }
    }

    /** 提供给 UI 展示当前密保问题 */
    fun getCurrentResetQuestion(): String? = currentSecQuestion

    /** ===== 忘记密码 Step 2：不本地校验，只暂存答案 ===== */
    fun verifyAnswer(answer: String): Boolean {
        resetAnswerPlain = answer
        return true
    }

    /** 密保答案提前远端校验：仅验证答案，不修改密码。 */
    fun verifySecurityAnswerForReset(answer: String, onResult: (Boolean, String?) -> Unit) {
        val email = pendingResetEmail ?: currentEmailForReset
        if (email.isNullOrBlank()) {
            onResult(false, "请先输入邮箱"); return
        }
        viewModelScope.launch {
            try {
                val resp = ApiService.resetPasswordByAnswer(email, answer.trim(), newPassword = "", confirmPassword = "", newEmail = null)
                when (resp.status) {
                    HttpStatusCode.OK -> onResult(true, null)
                    HttpStatusCode.BadRequest, HttpStatusCode.NotFound, HttpStatusCode.Conflict -> {
                        val gr = resp.tryBodyOrNull<GenericResponse>()
                        onResult(false, gr?.message ?: "密保答案不正确")
                    }
                    else -> onResult(false, "校验失败：${resp.status.value}")
                }
            } catch (e: Exception) {
                onResult(false, "校验失败：${e.message}")
            }
        }
    }

    /** ===== 忘记密码 Step 2（邮箱验证码路径）：暂存验证码 */
    fun cacheResetEmailCode(code: String) { pendingResetCode = code }

    /** ===== 忘记密码 Step 3：提交到后端校验并重置（邮箱验证码路径） ===== */
    fun resetPasswordWithEmailCode(newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            val email = pendingResetEmail
            val code = pendingResetCode
            if (email.isNullOrBlank()) { _authState.value = AuthState.Error("找不到邮箱信息，请重新开始找回流程"); return@launch }
            if (code.isNullOrBlank()) { _authState.value = AuthState.Error("请填写邮箱验证码"); return@launch }

            val sanitizedCode = code.trim()
            val sanitizedNewPassword = newPassword.trim()
            val sanitizedConfirmPassword = confirmPassword.trim()

            val passwordError = validatePasswordInput(sanitizedNewPassword, "新密码")
            if (passwordError != null) { _authState.value = AuthState.Error(passwordError); return@launch }
            if (sanitizedNewPassword != sanitizedConfirmPassword) { _authState.value = AuthState.Error("新密码与确认密码不一致"); return@launch }

            _authState.value = AuthState.Loading
            try {
                val resp = ApiService.resetPasswordByEmailCode(email, sanitizedCode, sanitizedNewPassword, sanitizedConfirmPassword)
                when (resp.status) {
                    HttpStatusCode.OK -> clearResetStateAfterSuccess()
                    HttpStatusCode.BadRequest, HttpStatusCode.NotFound -> {
                        val gr = resp.body<com.example.hearhome.data.remote.GenericResponse>()
                        _authState.value = AuthState.Error(gr.message)
                    }
                    else -> _authState.value = AuthState.Error("重置失败：${resp.status.value}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("重置失败：${e.message}")
            }
        }
    }

    /** ===== 忘记密码 Step 3：提交到后端校验并重置（密保 + 新邮箱） ===== */
    fun resetPasswordWithSecurityQuestion(answer: String, newPassword: String, confirmPassword: String, newEmail: String) {
        viewModelScope.launch {
            val email = pendingResetEmail ?: currentEmailForReset
            if (email.isNullOrBlank()) { _authState.value = AuthState.Error("找不到邮箱信息，请重新开始找回流程"); return@launch }

            val sanitizedNewEmail = newEmail.trim()
            val sanitizedAnswer = answer.trim()
            val sanitizedNewPassword = newPassword.trim()
            val sanitizedConfirmPassword = confirmPassword.trim()

            if (sanitizedNewEmail.isEmpty()) { _authState.value = AuthState.Error("请填写新的安全邮箱"); return@launch }
            if (sanitizedAnswer.isEmpty()) { _authState.value = AuthState.Error("请填写密保答案"); return@launch }

            val emailError = validateEmailInput(sanitizedNewEmail)
            if (emailError != null) { _authState.value = AuthState.Error(emailError); return@launch }

            val passwordError = validatePasswordInput(sanitizedNewPassword, "新密码")
            if (passwordError != null) { _authState.value = AuthState.Error(passwordError); return@launch }
            if (sanitizedNewPassword != sanitizedConfirmPassword) { _authState.value = AuthState.Error("新密码与确认密码不一致"); return@launch }

            _authState.value = AuthState.Loading
            try {
                val resp = ApiService.resetPasswordByAnswer(email, sanitizedAnswer, sanitizedNewPassword, sanitizedConfirmPassword, sanitizedNewEmail)
                when (resp.status) {
                    HttpStatusCode.OK -> clearResetStateAfterSuccess()
                    HttpStatusCode.BadRequest, HttpStatusCode.NotFound -> {
                        val gr = resp.body<com.example.hearhome.data.remote.GenericResponse>()
                        _authState.value = AuthState.Error(gr.message)
                    }
                    else -> _authState.value = AuthState.Error("重置失败：${resp.status.value}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("重置失败：${e.message}")
            }
        }
    }

    private fun clearResetStateAfterSuccess() {
        currentEmailForReset = null
        currentSecQuestion = null
        resetAnswerPlain = null
        pendingResetEmail = null
        pendingResetCode = null
        _authState.value = AuthState.PasswordResetSuccess
    }

    /** 发送或重发验证码（UI 复用） */
    fun resendEmailCode(purpose: VerificationPurpose) {
        val email = when (purpose) {
            VerificationPurpose.UPDATE_PASSWORD -> pendingPasswordUpdate?.email
            VerificationPurpose.UPDATE_SECURITY_QUESTION -> pendingSecurityQuestionUpdate?.email
            VerificationPurpose.RESET_PASSWORD -> pendingResetEmail
        }
        if (email.isNullOrBlank()) {
            _authState.value = AuthState.Error("暂无可发送验证码的邮箱"); return
        }
        requestEmailCode(email, purpose, null)
    }

    /** 用户提交验证码后，根据当前 pending 操作继续执行。 */
    fun verifyEmailCode(code: String) {
        val pendingPurpose = when {
            pendingPasswordUpdate != null -> VerificationPurpose.UPDATE_PASSWORD
            pendingSecurityQuestionUpdate != null -> VerificationPurpose.UPDATE_SECURITY_QUESTION
            pendingResetEmail != null -> VerificationPurpose.RESET_PASSWORD
            else -> { _authState.value = AuthState.Error("当前没有需要验证码的操作"); return }
        }
        val email = when (pendingPurpose) {
            VerificationPurpose.UPDATE_PASSWORD -> pendingPasswordUpdate?.email
            VerificationPurpose.UPDATE_SECURITY_QUESTION -> pendingSecurityQuestionUpdate?.email
            VerificationPurpose.RESET_PASSWORD -> pendingResetEmail
        }
        if (email.isNullOrBlank()) { _authState.value = AuthState.Error("缺少邮箱信息"); return }

        // 对已登录的敏感操作，直接携带验证码完成后续请求，避免二次校验导致 "需要先获取验证码"。
        if (pendingPurpose == VerificationPurpose.UPDATE_PASSWORD || pendingPurpose == VerificationPurpose.UPDATE_SECURITY_QUESTION) {
            _authState.value = AuthState.Loading
            viewModelScope.launch {
                when (pendingPurpose) {
                    VerificationPurpose.UPDATE_PASSWORD -> completePendingPasswordUpdate(code)
                    VerificationPurpose.UPDATE_SECURITY_QUESTION -> completePendingSecurityQuestionUpdate(code)
                    else -> {}
                }
            }
            return
        }

        // 忘记密码路径仍使用后端校验以复用通用接口
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val resp = ApiService.verifyEmailCode(email, pendingPurpose.asServerValue(), code)
                if (resp.status == HttpStatusCode.OK) {
                    pendingResetCode = code
                    _authState.value = AuthState.EmailCodeVerified(email, pendingPurpose)
                } else {
                    val gr = resp.tryBodyOrNull<GenericResponse>()
                    _authState.value = AuthState.Error(gr?.message ?: "验证码校验失败：${resp.status.value}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("验证码校验失败：${e.message}")
            }
        }
    }

    private suspend fun completePendingPasswordUpdate(emailCode: String) {
        val payload = pendingPasswordUpdate ?: run {
            _authState.value = AuthState.Error("暂无待继续的修改密码请求")
            return
        }
        try {
            val resp = ApiService.updatePassword(payload.email, payload.oldPassword, payload.newPassword, emailCode = emailCode)
            when (resp.status) {
                HttpStatusCode.OK -> {
                    pendingPasswordUpdate = null
                    _authState.value = AuthState.PasswordUpdateSuccess
                }
                else -> {
                    val gr = resp.tryBodyOrNull<GenericResponse>()
                    _authState.value = AuthState.Error(gr?.message ?: "修改失败：${resp.status.value}")
                }
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Error("修改失败：${e.message}")
        }
    }

    private suspend fun completePendingSecurityQuestionUpdate(emailCode: String) {
        val payload = pendingSecurityQuestionUpdate ?: run {
            _authState.value = AuthState.Error("暂无待继续的密保更新请求")
            return
        }
        try {
            val resp = ApiService.updateSecurityQuestion(payload.email, payload.password, payload.question, payload.answer, emailCode)
            when (resp.status) {
                HttpStatusCode.OK -> {
                    pendingSecurityQuestionUpdate = null
                    _authState.value = AuthState.UpdateSuccess
                }
                else -> {
                    val gr = resp.tryBodyOrNull<GenericResponse>()
                    _authState.value = AuthState.Error(gr?.message ?: "更新失败：${resp.status.value}")
                }
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Error("更新失败：${e.message}")
        }
    }

    private fun requestEmailCode(email: String, purpose: VerificationPurpose, reason: String?) {
        _authState.value = AuthState.EmailCodeRequired(email, purpose, reason)
        viewModelScope.launch {
            try {
                val resp = ApiService.requestEmailVerification(email, purpose.asServerValue())
                if (resp.status == HttpStatusCode.OK || resp.status == HttpStatusCode.Accepted) {
                    _authState.value = AuthState.EmailCodeSent(email, purpose)
                } else {
                    val gr = resp.tryBodyOrNull<GenericResponse>()
                    _authState.value = AuthState.Error(gr?.message ?: "发送验证码失败：${resp.status.value}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("发送验证码失败：${e.message}")
            }
        }
    }

    private suspend fun handleRiskResponse(
        resp: HttpResponse,
        onNeedEmail: (String?) -> Unit,
        onNeedSecurityQuestion: ((String?) -> Unit)? = null
    ) {
        val gr = resp.tryBodyOrNull<GenericResponse>()
        val message = gr?.message
        when {
            resp.status == HttpStatusCode.Forbidden -> onNeedEmail(message ?: "需要邮箱验证码验证")
            requiresEmailVerification(message) -> onNeedEmail(message)
            requiresSecurityQuestion(message) -> onNeedSecurityQuestion?.invoke(message)
            else -> _authState.value = AuthState.Error(message ?: "请求失败：${resp.status.value}")
        }
    }

    private fun requiresEmailVerification(message: String?): Boolean =
        message?.contains("NEED_EMAIL_VERIFICATION", ignoreCase = true) == true

    private fun requiresSecurityQuestion(message: String?): Boolean =
        message?.contains("USE_SECURITY_QUESTION", ignoreCase = true) == true

    private suspend inline fun <reified T> HttpResponse.tryBodyOrNull(): T? =
        try { body<T>() } catch (_: Exception) { null }
}
