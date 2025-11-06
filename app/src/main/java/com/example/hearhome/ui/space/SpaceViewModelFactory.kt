package com.example.hearhome.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hearhome.data.local.SpaceDao
import com.example.hearhome.data.local.UserDao

class SpaceViewModelFactory(
    private val spaceDao: SpaceDao,
    private val userDao: UserDao,
    private val currentUserId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpaceViewModel::class.java)) {
            return SpaceViewModel(spaceDao, userDao, currentUserId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
