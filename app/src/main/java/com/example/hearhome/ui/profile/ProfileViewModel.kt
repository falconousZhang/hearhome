package com.example.hearhome.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.User
import com.example.hearhome.data.local.UserDao
import com.example.hearhome.data.remote.ApiService
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. 定义 UI 状态
data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

// 2. 创建 ViewModel
class ProfileViewModel(private val userDao: UserDao) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    fun loadUser(userId: Int) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState(isLoading = true)
            try {
                val response = ApiService.getProfile(userId)
                if (response.status == HttpStatusCode.OK) {
                    val user = response.body<User>()
                    _uiState.value = ProfileUiState(user = user, isLoading = false)
                } else {
                    _uiState.value = ProfileUiState(error = "Failed to load user profile", isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = ProfileUiState(error = e.message, isLoading = false)
            }
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