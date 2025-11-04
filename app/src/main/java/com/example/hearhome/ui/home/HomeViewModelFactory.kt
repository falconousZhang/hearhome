package com.example.hearhome.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hearhome.data.local.FriendDao

class HomeViewModelFactory(private val friendDao: FriendDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(friendDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}