package com.example.hearhome.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.User
import com.example.hearhome.data.local.UserDao
import com.example.hearhome.util.Crypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val userDao: UserDao) : ViewModel() {

    // --- [合并后] 的 AuthState ---
    sealed class AuthState {
        object Idle : AuthState() // 替换旧的 Initial
        object Loading : AuthState()
        data class Success(val user: User) : AuthState() // 登录成功
        data class RegisterSuccess(val user: User) : AuthState() // 注册成功
        object PasswordResetReady : AuthState()
        object PasswordResetSuccess : AuthState()
        data class Error(val message: String) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    // 忘记密码临时状态
    private var currentEmailForReset: String? = null
    private var currentSecQuestion: String? = null
    private var currentSecAnswerHash: String? = null

    // ===== 登录 (合并版) =====
    fun login(email: String, password: String) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                _authState.value = AuthState.Error("邮箱和密码都不能为空")
                return@launch
            }
            _authState.value = AuthState.Loading
            val user = userDao.findByEmail(email.trim())
            if (user == null || user.password != password) {
                _authState.value = AuthState.Error("邮箱或密码错误")
                return@launch
            }
            // [修改] 发射 Success(user) 以便导航获取 userId
            _authState.value = AuthState.Success(user)
        }
    }

    // ===== 注册 (新版) =====
    fun register(email: String, password: String, secQuestion: String, secAnswer: String) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank() || secQuestion.isBlank() || secAnswer.isBlank()) {
                _authState.value = AuthState.Error("邮箱、密码、问题、答案均不能为空")
                return@launch
            }
            if (password.length < 6) {
                _authState.value = AuthState.Error("密码至少 6 位")
                return@launch
            }
            // (你可以添加更多答案验证)

            _authState.value = AuthState.Loading
            val exist = userDao.findByEmail(email.trim())
            if (exist != null) {
                _authState.value = AuthState.Error("该邮箱已注册")
                return@launch
            }

            val user = User(
                email = email.trim(),
                password = password,
                secQuestion = secQuestion.trim(),
                secAnswerHash = Crypto.sha256(secAnswer.trim()) // 使用加密
            )
            // 插入后，我们需要获取新生成的 user (带 uid)
            val newUserId = userDao.insert(user)
            val newUser = user.copy(uid = newUserId.toInt()) // 假设 insert 返回 Long 类型的 ID

            // [修改] 发射 RegisterSuccess(user) 以便导航
            _authState.value = AuthState.RegisterSuccess(newUser)
        }
    }

    // ===== 忘记密码：步骤1 (新版) =====
    fun startResetByQuestion(email: String) {
        viewModelScope.launch {
            if (email.isBlank()) {
                _authState.value = AuthState.Error("请填写邮箱")
                return@launch
            }
            _authState.value = AuthState.Loading
            val qa = userDao.getSecurityQA(email.trim()) // [修改] 使用 getSecurityQA

            if (qa == null) {
                _authState.value = AuthState.Error("该邮箱未注册")
                return@launch
            }
            if (qa.question.isBlank() || qa.answerHash.isBlank()) {
                _authState.value = AuthState.Error("该账户未设置安全问题，无法找回")
                return@launch
            }
            currentEmailForReset = email.trim()
            currentSecQuestion = qa.question
            currentSecAnswerHash = qa.answerHash
            _authState.value = AuthState.PasswordResetReady
        }
    }

    fun getCurrentResetQuestion(): String? = currentSecQuestion

    // ===== 忘记密码：步骤2 (新版) =====
    fun verifyAnswer(answer: String): Boolean {
        val expect = currentSecAnswerHash ?: return false
        return Crypto.sha256(answer.trim()) == expect
    }

    // ===== 忘记密码：步骤3 (新版) =====
    fun setNewPassword(newPassword: String) {
        viewModelScope.launch {
            val email = currentEmailForReset
            if (email.isNullOrBlank()) {
                _authState.value = AuthState.Error("流程异常，缺少邮箱")
                return@launch
            }
            if (newPassword.length < 6) {
                _authState.value = AuthState.Error("新密码至少 6 位")
                return@launch
            }
            _authState.value = AuthState.Loading
            val updated = userDao.updatePasswordByEmail(email, newPassword)
            if (updated > 0) {
                resetPasswordState()
                _authState.value = AuthState.PasswordResetSuccess
            } else {
                _authState.value = AuthState.Error("重置失败，请稍后重试")
            }
        }
    }

    private fun resetPasswordState() {
        currentEmailForReset = null
        currentSecQuestion = null
        currentSecAnswerHash = null
    }

    // ===== 退出登录 (来自我们旧版) =====
    fun logout() {
        _authState.value = AuthState.Idle
    }

    // ===== 重置状态 (合并版) =====
    fun resetAuthState() {
        // 仅在非加载和非成功状态下重置
        if (_authState.value !is AuthState.Loading &&
            _authState.value !is AuthState.Success &&
            _authState.value !is AuthState.RegisterSuccess) {

            _authState.value = AuthState.Idle
        }
        resetPasswordState()
    }
}


// [重要] 你的 AuthViewModelFactory.kt 文件不需要改动
// (它应该在 app/src/main/java/com/example/hearhome/ui/auth/AuthViewModelFactory.kt)