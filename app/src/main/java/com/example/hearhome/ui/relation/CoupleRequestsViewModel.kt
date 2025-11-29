package com.example.hearhome.ui.relation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.User // Use the local User model which has all the fields
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.data.remote.GenericResponse
import com.google.gson.Gson
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// This DTO matches the JSON from the /couples/requests/{userId} endpoint.
@Serializable
data class CoupleRequestFromApi(val id: Int, val requesterId: Int, val partnerId: Int, val status: String, val createdAt: Long)

// This class holds the processed data for the UI
data class CoupleRequestInfo(
    val requestId: Int,
    val requester: User // Using the rich local User model
)

// MODIFIED: Add successMessage to state
data class CoupleRequestsUiState(
    val requests: List<CoupleRequestInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null
)

class CoupleRequestsViewModel(
    private val apiService: ApiService,
    private val currentUserId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoupleRequestsUiState())
    val uiState: StateFlow<CoupleRequestsUiState> = _uiState.asStateFlow()

    init {
        loadRequests()
    }

    fun loadRequests() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = apiService.getCoupleRequests(currentUserId)
                if (response.status == HttpStatusCode.OK) {
                    val remoteRequests = response.body<List<CoupleRequestFromApi>>()
                    val requestInfos = mutableListOf<CoupleRequestInfo>()

                    for (req in remoteRequests) {
                        try {
                            val userResponse = apiService.getProfile(req.requesterId)
                            if (userResponse.status == HttpStatusCode.OK) {
                                val user = userResponse.body<User>()
                                requestInfos.add(CoupleRequestInfo(requestId = req.id, requester = user))
                            }
                        } catch(e: Exception) {
                            e.printStackTrace() // Log error or skip this request
                        }
                    }
                    _uiState.update { it.copy(requests = requestInfos, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load requests") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // MODIFIED: Simplified signature, removed callback, updates state instead
    fun acceptRequest(requestId: Int) {
        viewModelScope.launch {
            try {
                val acceptResponse = apiService.acceptCoupleRequest(requestId)
                if (acceptResponse.status == HttpStatusCode.OK) {
                    _uiState.update { it.copy(successMessage = "申请已同意") }
                    loadRequests() // Refresh the list
                } else {
                    val errorMsg = try { acceptResponse.body<GenericResponse>().message } catch (e: Exception) { "Failed to accept request." }
                    throw Exception(errorMsg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(error = e.message ?: "同意申请失败") }
            }
        }
    }

    // MODIFIED: Simplified signature, removed callback, updates state instead
    fun rejectRequest(requestId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.rejectCoupleRequest(requestId)
                if (response.status == HttpStatusCode.OK) {
                    _uiState.update { it.copy(successMessage = "申请已拒绝") }
                    loadRequests() // Refresh the list
                } else {
                    val errorBody = response.bodyAsText()
                    val error = try { Gson().fromJson(errorBody, GenericResponse::class.java).message } catch(e: Exception) { "Failed to reject request" }
                    throw Exception(error)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                 _uiState.update { it.copy(error = e.message ?: "拒绝申请失败") }
            }
        }
    }

    // NEW: Added method to clear one-time messages
    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, error = null) }
    }
}

class CoupleRequestsViewModelFactory(
    private val apiService: ApiService,
    private val currentUserId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CoupleRequestsViewModel::class.java)) {
            return CoupleRequestsViewModel(apiService, currentUserId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
