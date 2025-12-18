package com.example.hearhome.ui.chat

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.Message
import com.example.hearhome.data.local.User
import com.example.hearhome.data.remote.ApiMessage
import com.example.hearhome.data.remote.ApiService
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

data class ChatScreenState(
    val messages: List<Message> = emptyList(),
    val friendUser: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ImageUploadResponse(val imageUrl: String)

class ChatViewModel(
    private val apiService: ApiService,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatScreenState())
    val uiState: StateFlow<ChatScreenState> = _uiState

    private var messagePollingJob: Job? = null

    fun startPolling(userId1: Int, userId2: Int) {
        messagePollingJob?.cancel()
        messagePollingJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val initialResponse = apiService.getMessages(userId1, userId2)
                if (initialResponse.status == HttpStatusCode.OK) {
                    val messages = initialResponse.body<List<Message>>()
                    _uiState.value = _uiState.value.copy(messages = messages, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(error = "Failed to load messages", isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }

            while (isActive) {
                delay(3000)
                try {
                    val pollResponse = apiService.getMessages(userId1, userId2)
                    if (pollResponse.status == HttpStatusCode.OK) {
                        val messages = pollResponse.body<List<Message>>()
                        if (_uiState.value.messages != messages) {
                            _uiState.value = _uiState.value.copy(messages = messages)
                        }
                    }
                } catch (_: Exception) {
                    // Do not show an error for every failed poll
                }
            }
        }
    }

    fun loadFriendDetails(friendId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.getProfile(friendId)
                if (response.status == HttpStatusCode.OK) {
                    val user = response.body<User>()
                    _uiState.value = _uiState.value.copy(friendUser = user)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Could not load friend's name: ${e.message}")
            }
        }
    }

    fun sendMessage(senderId: Int, receiverId: Int, content: String?, imageUri: Uri?) {
        viewModelScope.launch {

            // ① 文本和图片都为空就不发
            if (content.isNullOrBlank() && imageUri == null) return@launch

            var uploadedImageUrl: String? = null

            // ② 如果有图片 URI，先上传图片
            if (imageUri != null) {
                try {
                    // 将 Uri 转换为 ByteArray
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(imageUri)
                    val imageBytes = inputStream?.readBytes()

                    if (imageBytes != null) {
                        val response = apiService.uploadImage(imageBytes, "image.jpg")
                        if(response.status == HttpStatusCode.OK) {
                            val responseBody = response.body<ImageUploadResponse>()
                            uploadedImageUrl = responseBody.imageUrl
                        }
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = "Image upload failed: ${e.message}")
                }
            }

            // ③ 组织消息内容
            val apiMessage = ApiMessage(
                senderId = senderId,
                receiverId = receiverId,
                content = if (uploadedImageUrl == null) content else null,  // 图片消息不发文字
                imageUrl = uploadedImageUrl,
                timestamp = System.currentTimeMillis()
            )

            // ④ 调用发送消息接口
            try {
                val response = apiService.sendMessage(apiMessage)
                if (response.status == HttpStatusCode.Created) {
                    val refreshResponse = apiService.getMessages(senderId, receiverId)
                    if (refreshResponse.status == HttpStatusCode.OK) {
                        _uiState.value = _uiState.value.copy(messages = refreshResponse.body())
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

}

class ChatViewModelFactory(
    private val apiService: ApiService,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(apiService, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}