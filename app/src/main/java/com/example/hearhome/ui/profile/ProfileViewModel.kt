package com.example.hearhome.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.User
import com.example.hearhome.data.local.UserDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 1. 定义 UI 状态
data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false
)

// 2. 创建 ViewModel
class ProfileViewModel(private val userDao: UserDao) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    fun loadUser(userId: Int) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState(isLoading = true)
            // 从 DAO 获取用户数据 (使用 .first() 来从 Flow 中获取一次性数据)
            val user = userDao.findById(userId).first()
            _uiState.value = ProfileUiState(user = user, isLoading = false)
        }
    }
}

// 3. 创建 ViewModel Factory
// 这是为了能把 UserDao 传入 ViewModel
class ProfileViewModelFactory(private val userDao: UserDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(userDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}