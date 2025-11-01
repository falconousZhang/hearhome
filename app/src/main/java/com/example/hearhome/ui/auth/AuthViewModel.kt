package com.example.hearhome.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.User
import com.example.hearhome.data.local.UserDao
import com.example.hearhome.util.Crypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val userDao: UserDao) : ViewModel() {

    sealed class AuthState {
        object Idle : AuthState()              // 初始状态, 或已登出
        object AwaitingInput : AuthState()     // [新] 在个人中心等页面，等待用户操作
        object Loading : AuthState()
        data class Success(val user: User) : AuthState()      // 登录/注册成功
        data class RegisterSuccess(val user: User) : AuthState()
        object PasswordResetReady : AuthState()
        object PasswordResetSuccess : AuthState()
        object UpdateSuccess : AuthState()         // 用于更新私密问题成功
        object PasswordUpdateSuccess : AuthState() // [新] 专门用于密码更新成功
        data class Error(val message: String) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private var currentEmailForReset: String? = null
    private var currentSecQuestion: String? = null
    private var currentSecAnswerHash: String? = null

    init {
        // 当ViewModel创建时，给ProfileScreen一个初始状态
        // 这可以防止在已经登录的情况下，authState的初始值Idle导致意外退出
        // 但由于ViewModel是全局共享的，更好的方式是在合适的时机调用它
        // _authState.value = AwaitingInput 
    }

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
            _authState.value = AuthState.Success(user)
        }
    }

    fun register(email: String, password: String, secQuestion: String, secAnswer: String) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank() || secQuestion.isBlank() || secAnswer.isBlank()) {
                _authState.value = AuthState.Error("所有字段均不能为空")
                return@launch
            }
            if (password.length < 6) {
                _authState.value = AuthState.Error("密码至少 6 位")
                return@launch
            }
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
                secAnswerHash = Crypto.sha256(secAnswer.trim())
            )
            val newUserId = userDao.insert(user)
            val newUser = user.copy(uid = newUserId.toInt())
            _authState.value = AuthState.RegisterSuccess(newUser)
        }
    }

    fun updatePassword(email: String, oldPassword: String, securityAnswer: String, newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            if (oldPassword.isBlank() || securityAnswer.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
                _authState.value = AuthState.Error("所有字段均不能为空")
                return@launch
            }
            if (newPassword.length < 6) {
                _authState.value = AuthState.Error("新密码至少 6 位")
                return@launch
            }
            if (newPassword != confirmPassword) {
                _authState.value = AuthState.Error("两次输入的新密码不一致")
                return@launch
            }

            _authState.value = AuthState.Loading
            val user = userDao.findByEmail(email)
            if (user == null) { _authState.value = AuthState.Error("用户不存在"); return@launch }

            if (user.password != oldPassword) {
                _authState.value = AuthState.Error("旧密码不正确")
                return@launch
            }
            if (user.secAnswerHash.isBlank() || Crypto.sha256(securityAnswer.trim()) != user.secAnswerHash) {
                _authState.value = AuthState.Error("安全问题回答错误")
                return@launch
            }
            
            val updated = userDao.updatePasswordByEmail(email, newPassword)
            if (updated > 0) {
                _authState.value = AuthState.PasswordUpdateSuccess 
            } else {
                _authState.value = AuthState.Error("密码更新失败，请稍后再试")
            }
        }
    }

    fun updateSecurityQuestion(email: String, password: String, question: String, answer: String) {
        viewModelScope.launch {
            if (password.isBlank() || question.isBlank() || answer.isBlank()) {
                _authState.value = AuthState.Error("密码、问题和答案均不能为空")
                return@launch
            }
            _authState.value = AuthState.Loading
            val user = userDao.findByEmail(email)
            if (user == null || user.password != password) { 
                _authState.value = AuthState.Error("当前密码不正确")
                return@launch
            }
            val answerHash = Crypto.sha256(answer.trim())
            val updated = userDao.updateSecurityQAByEmail(email, question.trim(), answerHash)
            if (updated > 0) {
                _authState.value = AuthState.UpdateSuccess
            } else {
                _authState.value = AuthState.Error("私密问题更新失败")
            }
        }
    }

    // [新] 在个人中心显示完提示后调用，返回“等待输入”状态
    fun onProfileEventConsumed() {
        _authState.value = AuthState.AwaitingInput
    }

    // [新] 在登录/注册页面间导航时调用，清除错误状态
    fun resetAuthResult() {
        _authState.value = AuthState.Idle
    }

    fun logout() {
        _authState.value = AuthState.Idle
    }
    
    // ... (忘记密码函数保持不变) ...
    fun startResetByQuestion(email: String){ viewModelScope.launch { if(email.isBlank()){ _authState.value=AuthState.Error("请填写邮箱");return@launch };_authState.value=AuthState.Loading;val qa=userDao.getSecurityQA(email.trim());if(qa==null){ _authState.value=AuthState.Error("该邮箱未注册");return@launch };if(qa.question.isBlank()||qa.answerHash.isBlank()){ _authState.value=AuthState.Error("该账户未设置安全问题");return@launch };currentEmailForReset=email.trim();currentSecQuestion=qa.question;currentSecAnswerHash=qa.answerHash;_authState.value=AuthState.PasswordResetReady } }
    fun getCurrentResetQuestion():String? = currentSecQuestion
    fun verifyAnswer(answer:String):Boolean{ val expect=currentSecAnswerHash?:return false;return Crypto.sha256(answer.trim())==expect }
    fun setNewPassword(newPassword:String){ viewModelScope.launch { val email=currentEmailForReset;if(email.isNullOrBlank()){ _authState.value=AuthState.Error("流程异常");return@launch };if(newPassword.length<6){ _authState.value=AuthState.Error("新密码至少 6 位");return@launch };_authState.value=AuthState.Loading;val updated=userDao.updatePasswordByEmail(email,newPassword);if(updated>0){ resetPasswordState();_authState.value=AuthState.PasswordResetSuccess }else{ _authState.value=AuthState.Error("重置失败") } } }
    private fun resetPasswordState(){ currentEmailForReset=null;currentSecQuestion=null;currentSecAnswerHash=null }
}
