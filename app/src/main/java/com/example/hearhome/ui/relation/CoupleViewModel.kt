package com.example.hearhome.ui.relation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.Couple
import com.example.hearhome.data.local.User
import com.example.hearhome.data.remote.ApiService
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class CoupleRequestWithRequester(
    val request: Couple,
    val requester: User
)

@Serializable
data class CoupleWithPartner(
    val couple: Couple,
    val partner: User
)

data class CoupleScreenState(
    val myCouple: CoupleWithPartner? = null,
    val coupleRequests: List<CoupleRequestWithRequester> = emptyList(),
    val requestCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

/**
 * ViewModel for managing couple relationship operations
 */
class CoupleViewModel(private val apiService: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow(CoupleScreenState())
    val uiState: StateFlow<CoupleScreenState> = _uiState

    /**
     * 获取当前用户的情侣关系
     */
    fun getMyCouple(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getCouple(userId)
                if (response.status == HttpStatusCode.OK) {
                    val couple = response.body<Couple>()
                    // 获取伴侣信息
                    val partnerId = if (couple.requesterId == userId) couple.partnerId else couple.requesterId
                    val partnerResponse = apiService.getProfile(partnerId)
                    if (partnerResponse.status == HttpStatusCode.OK) {
                        val partner = partnerResponse.body<User>()
                        _uiState.value = _uiState.value.copy(
                            myCouple = CoupleWithPartner(couple, partner),
                            isLoading = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                } else if (response.status == HttpStatusCode.NotFound) {
                    // 没有情侣关系
                    _uiState.value = _uiState.value.copy(myCouple = null, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to fetch couple relationship",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    /**
     * 获取待处理的情侣请求
     */
    fun getCoupleRequests(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val requestsResponse = apiService.getCoupleRequests(userId)
                if (requestsResponse.status == HttpStatusCode.OK) {
                    val basicRequests = requestsResponse.body<List<Couple>>()
                    
                    // 获取每个请求者的详细信息
                    val detailedRequests = basicRequests.map { request ->
                        async {
                            try {
                                val profileResponse = apiService.getProfile(request.requesterId)
                                if (profileResponse.status == HttpStatusCode.OK) {
                                    CoupleRequestWithRequester(request, profileResponse.body<User>())
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()

                    _uiState.value = _uiState.value.copy(
                        coupleRequests = detailedRequests,
                        requestCount = detailedRequests.size,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to fetch couple requests",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    /**
     * 发送情侣关系请求
     */
    fun sendCoupleRequest(requesterId: Int, partnerId: Int, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val response = apiService.sendCoupleRequest(requesterId, partnerId)
                if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "情侣请求已发送",
                        isLoading = false
                    )
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "发送请求失败",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "发送请求出错: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    /**
     * 接受情侣关系请求
     */
    fun acceptCoupleRequest(requestId: Int, currentUserId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.acceptCoupleRequest(requestId)
                if (response.status == HttpStatusCode.OK) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "已接受情侣请求",
                        isLoading = false
                    )
                    // 刷新数据
                    getCoupleRequests(currentUserId)
                    getMyCouple(currentUserId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "接受请求失败",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    /**
     * 拒绝情侣关系请求
     */
    fun rejectCoupleRequest(requestId: Int, currentUserId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.rejectCoupleRequest(requestId)
                if (response.status == HttpStatusCode.OK) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "已拒绝情侣请求",
                        isLoading = false
                    )
                    // 刷新请求列表
                    getCoupleRequests(currentUserId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "拒绝请求失败",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    /**
     * 解除情侣关系
     */
    fun breakupCouple(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.breakupCouple(userId)
                if (response.status == HttpStatusCode.OK) {
                    _uiState.value = _uiState.value.copy(
                        myCouple = null,
                        successMessage = "已解除情侣关系",
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "解除关系失败",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    /**
     * 清除消息
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}

class CoupleViewModelFactory(private val apiService: ApiService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CoupleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CoupleViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

