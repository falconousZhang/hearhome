package com.example.hearhome.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.FriendDao
import com.example.hearhome.data.local.FriendWithUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val friendDao: FriendDao) : ViewModel() {

    private val _friendsWithUsers = MutableStateFlow<List<FriendWithUser>>(emptyList())
    val friendsWithUsers: StateFlow<List<FriendWithUser>> = _friendsWithUsers

    fun loadFriends(userId: Int) {
        viewModelScope.launch {
            _friendsWithUsers.value = friendDao.getAcceptedFriendsWithUsers(userId)
        }
    }
}