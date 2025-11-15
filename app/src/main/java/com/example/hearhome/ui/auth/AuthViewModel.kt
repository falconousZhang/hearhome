package com.example.hearhome.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.User
import com.example.hearhome.data.local.UserDao
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.data.remote.LoginRequest
import com.example.hearhome.util.Crypto
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class AuthViewModel(private val userDao: UserDao) : ViewModel() {

    sealed class AuthState {
        object Idle : AuthState()
        object AwaitingInput : AuthState()
        object Loading : AuthState()
        data class Success(val user: User) : AuthState()
        data class RegisterSuccess(val user: User) : AuthState()
        object PasswordResetReady : AuthState()
        object PasswordResetSuccess : AuthState()
        object UpdateSuccess : AuthState()
        object PasswordUpdateSuccess : AuthState()
        data class Error(val message: String) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private var currentEmailForReset: String? = null
    private var currentSecQuestion: String? = null
    private var currentSecAnswerHash: String? = null

    fun login(email: String, password: String) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                _authState.value = AuthState.Error("邮箱和密码都不能为空")
                return@launch
            }
            _authState.value = AuthState.Loading
            try {
                val response = ApiService.login(LoginRequest(email, password))
                if (response.status == HttpStatusCode.OK) {
                    val user = response.body<User>()
                    _authState.value = AuthState.Success(user)
                } else {
                    _authState.value = AuthState.Error("邮箱或密码错误")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("登录失败: ${e.message}")
            }
        }
    }

    fun register(
        email: String,
        password: String,
        secQuestion: String,
        secAnswer: String,
        nickname: String,
        gender: String
    ) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank() || secQuestion.isBlank() || secAnswer.isBlank() || nickname.isBlank()) {
                _authState.value = AuthState.Error("所有字段均不能为空")
                return@launch
            }
            if (password.length < 6) {
                _authState.value = AuthState.Error("密码至少 6 位")
                return@launch
            }

            _authState.value = AuthState.Loading

            val rnd = Random
            val color = android.graphics.Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            val hexColor = String.format("#%06X", 0xFFFFFF and color)

            val user = User(
                email = email.trim(),
                password = password,
                secQuestion = secQuestion.trim(),
                secAnswerHash = Crypto.sha256(secAnswer.trim()),
                nickname = nickname.trim(),
                gender = gender,
                avatarColor = hexColor
            )

            try {
                val response = ApiService.register(user)
                if (response.status == HttpStatusCode.Created) {
                    val newUser = response.body<User>()
                    _authState.value = AuthState.RegisterSuccess(newUser)
                } else {
                    _authState.value = AuthState.Error("注册失败")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("注册失败: ${e.message}")
            }
        }
    }

    // ✅ 恢复所有被误删的函数

    fun updatePassword(email: String, oldPassword: String, securityAnswer: String, newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            if (oldPassword.isBlank() || securityAnswer.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
                _authState.value = AuthState.Error("所有字段均不能为空")
                return@launch
            }
            if (newPassword.length < 6) { _authState.value = AuthState.Error("新密码至少 6 位"); return@launch }
            if (newPassword != confirmPassword) { _authState.value = AuthState.Error("两次输入的新密码不一致"); return@launch }

            _authState.value = AuthState.Loading
            val user = userDao.findByEmail(email)
            if (user == null) { _authState.value = AuthState.Error("用户不存在"); return@launch }
            if (user.password != oldPassword) { _authState.value = AuthState.Error("旧密码不正确"); return@launch }
            if (user.secAnswerHash.isBlank() || Crypto.sha256(securityAnswer.trim()) != user.secAnswerHash) { _authState.value = AuthState.Error("安全问题回答错误"); return@launch }

            val updated = userDao.updatePasswordByEmail(email, newPassword)
            if (updated > 0) { _authState.value = AuthState.PasswordUpdateSuccess } else { _authState.value = AuthState.Error("密码更新失败，请稍后再试") }
        }
    }

    fun updateSecurityQuestion(email: String, password: String, question: String, answer: String) {
        viewModelScope.launch {
            if (password.isBlank() || question.isBlank() || answer.isBlank()) { _authState.value = AuthState.Error("密码、问题和答案均不能为空"); return@launch }
            _authState.value = AuthState.Loading
            val user = userDao.findByEmail(email)
            if (user == null || user.password != password) { _authState.value = AuthState.Error("当前密码不正确"); return@launch }
            val answerHash = Crypto.sha256(answer.trim())
            val updated = userDao.updateSecurityQAByEmail(email, question.trim(), answerHash)
            if (updated > 0) { _authState.value = AuthState.UpdateSuccess } else { _authState.value = AuthState.Error("私密问题更新失败") }
        }
    }

    fun onProfileEventConsumed() {
        _authState.value = AuthState.AwaitingInput
    }

    fun resetAuthResult() {
        _authState.value = AuthState.Idle
    }

    fun logout() {
        _authState.value = AuthState.Idle
    }

    fun startResetByQuestion(email: String){
        viewModelScope.launch {
            if(email.isBlank()){ _authState.value=AuthState.Error("请填写邮箱");return@launch }
            _authState.value=AuthState.Loading
            val qa=userDao.getSecurityQA(email.trim())
            if(qa==null){ _authState.value=AuthState.Error("该邮箱未注册");return@launch }
            if(qa.question.isBlank()||qa.answerHash.isBlank()){ _authState.value=AuthState.Error("该账户未设置安全问题");return@launch }
            currentEmailForReset=email.trim()
            currentSecQuestion=qa.question
            currentSecAnswerHash=qa.answerHash
            _authState.value=AuthState.PasswordResetReady
        }
    }

    fun getCurrentResetQuestion():String? = currentSecQuestion

    fun verifyAnswer(answer:String):Boolean{
        val expect=currentSecAnswerHash?:return false
        return Crypto.sha256(answer.trim())==expect
    }

    fun setNewPassword(newPassword:String){
        viewModelScope.launch {
            val email=currentEmailForReset
            if(email.isNullOrBlank()){ _authState.value=AuthState.Error("流程异常");return@launch }
            if(newPassword.length<6){ _authState.value=AuthState.Error("新密码至少 6 位");return@launch }
            _authState.value=AuthState.Loading
            val updated=userDao.updatePasswordByEmail(email,newPassword)
            if(updated>0){
                resetPasswordState()
                _authState.value=AuthState.PasswordResetSuccess
            }else{
                _authState.value=AuthState.Error("重置失败")
            }
        }
    }

    private fun resetPasswordState(){
        currentEmailForReset=null
        currentSecQuestion=null
        currentSecAnswerHash=null
    }
}