package com.example.hearhome.ui.chat

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.location.Location
import android.location.LocationManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class AudioUploadResponse(val audioUrl: String)

class ChatViewModel(
    private val apiService: ApiService,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        // TODO: 替换为你的高德 Web 静态图 key
        private const val AMAP_WEB_KEY = "36aa15ed72982f81e76b1081d2e670a0"
    }

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

    fun sendLocation(senderId: Int, receiverId: Int) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val hasFine = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                _uiState.value = _uiState.value.copy(error = "需要定位权限才能发送位置")
                return@launch
            }

            val locationManager = ctx.getSystemService(Application.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            val location: Location? = providers.firstNotNullOfOrNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }

            if (location == null) {
                _uiState.value = _uiState.value.copy(error = "无法获取当前位置，请稍后重试")
                return@launch
            }

            val lat = location.latitude
            val lon = location.longitude
            val locationName = "我的位置"
            val content = "LOCATION:$lat,$lon|$locationName"

            val mapUrl = if (AMAP_WEB_KEY != "REPLACE_WITH_YOUR_AMAP_KEY") {
                "https://restapi.amap.com/v3/staticmap?location=$lon,$lat&zoom=16&size=400*240&markers=mid,,A:$lon,$lat&key=$AMAP_WEB_KEY"
            } else null

            // 复用已有发送逻辑，作为带静态图的特殊消息
            sendMessage(
                senderId = senderId,
                receiverId = receiverId,
                content = content,
                imageUri = null,
                audioPath = null,
                audioDuration = null,
                overrideImageUrl = mapUrl
            )
        }
    }

    fun sendMessage(senderId: Int, receiverId: Int, content: String?, imageUri: Uri?, audioPath: String? = null, audioDuration: Long? = null) {
        sendMessage(senderId, receiverId, content, imageUri, audioPath, audioDuration, overrideImageUrl = null)
    }

    private fun sendMessage(senderId: Int, receiverId: Int, content: String?, imageUri: Uri?, audioPath: String? = null, audioDuration: Long? = null, overrideImageUrl: String? = null) {
        viewModelScope.launch {

            // ① 文本、图片、语音都为空就不发
            if (content.isNullOrBlank() && imageUri == null && audioPath == null && overrideImageUrl == null) return@launch

            var uploadedImageUrl: String? = null
            var uploadedAudioUrl: String? = null

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

            // 如果有语音，上传语音
            if (audioPath != null) {
                try {
                    val audioFile = java.io.File(audioPath)
                    val audioBytes = audioFile.readBytes()
                    val response = apiService.uploadAudio(audioBytes, audioFile.name)
                    if (response.status == HttpStatusCode.OK) {
                        val responseBody = response.body<AudioUploadResponse>()
                        uploadedAudioUrl = responseBody.audioUrl
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = "Audio upload failed: ${e.message}")
                }
            }

            // ③ 组织消息内容
            val apiMessage = ApiMessage(
                senderId = senderId,
                receiverId = receiverId,
                content = if (uploadedImageUrl == null && uploadedAudioUrl == null && overrideImageUrl == null) content else content,
                imageUrl = overrideImageUrl ?: uploadedImageUrl,
                audioUrl = uploadedAudioUrl,
                audioDuration = audioDuration,
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