package com.example.hearhome.ui.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.Friend
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
data class FriendRequestWithSender(
    val request: Friend,
    val sender: User
)

@Serializable
data class FriendWithRelation(
    val user: User,
    val relation: Friend
)

data class FriendScreenState(
    val friends: List<FriendWithRelation> = emptyList(),
    val searchedUser: User? = null,
    val searchCompleted: Boolean = false,
    val friendRequests: List<FriendRequestWithSender> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class FriendViewModel(private val apiService: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendScreenState())
    val uiState: StateFlow<FriendScreenState> = _uiState

    fun searchUserById(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, searchedUser = null, searchCompleted = false, error = null)
            try {
                val response = apiService.searchUserById(userId)
                if (response.status == HttpStatusCode.OK) {
                    val user = response.body<User>()
                    _uiState.value = _uiState.value.copy(searchedUser = user, isLoading = false, searchCompleted = true)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "User not found", searchCompleted = true)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message, searchCompleted = true)
            }
        }
    }
    
    fun clearSearch() {
        _uiState.value = _uiState.value.copy(searchedUser = null, searchCompleted = false, error = null)
    }

    fun getFriends(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val relationsResponse = apiService.getFriends(userId)
                if (relationsResponse.status != HttpStatusCode.OK) {
                    throw IllegalStateException("Failed to fetch friend relations.")
                }
                val relations = relationsResponse.body<List<Friend>>()

                val friendDetails = relations.map { relation ->
                    val friendId = if (relation.senderId == userId) relation.receiverId else relation.senderId
                    async {
                        try {
                            val profileResponse = apiService.getProfile(friendId)
                            if (profileResponse.status == HttpStatusCode.OK) {
                                FriendWithRelation(profileResponse.body<User>(), relation)
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()

                _uiState.value = _uiState.value.copy(friends = friendDetails, isLoading = false)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun getFriendRequests(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val requestsResponse = apiService.getFriendRequests(userId)
                if (requestsResponse.status != HttpStatusCode.OK) {
                    throw IllegalStateException("Failed to fetch friend requests from server.")
                }

                val basicRequests = requestsResponse.body<List<Friend>>()
                
                val detailedRequests = basicRequests.map { request ->
                    async {
                        val profileResponse = apiService.getProfile(request.senderId)
                        if (profileResponse.status == HttpStatusCode.OK) {
                            FriendRequestWithSender(request, profileResponse.body<User>()) 
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()

                _uiState.value = _uiState.value.copy(friendRequests = detailedRequests, isLoading = false)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun acceptFriendRequest(requestId: Int, currentUserId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.acceptFriendRequest(requestId)
                if(response.status == HttpStatusCode.OK) {
                    getFriendRequests(currentUserId)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun rejectFriendRequest(requestId: Int, currentUserId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.rejectFriendRequest(requestId)
                if(response.status == HttpStatusCode.OK) {
                    getFriendRequests(currentUserId)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    
    fun sendFriendRequest(senderId: Int, receiverId: Int) {
        viewModelScope.launch {
            try {
                apiService.sendFriendRequest(senderId, receiverId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteFriend(friendshipId: Int, currentUserId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteFriend(friendshipId)
                 if(response.status == HttpStatusCode.OK) {
                    getFriends(currentUserId) // Refresh the list
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}

class FriendViewModelFactory(private val apiService: ApiService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FriendViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FriendViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}