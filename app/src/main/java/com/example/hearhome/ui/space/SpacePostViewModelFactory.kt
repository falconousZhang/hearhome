package com.example.hearhome.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hearhome.data.local.SpacePostDao
import com.example.hearhome.data.local.UserDao

class SpacePostViewModelFactory(
    private val spacePostDao: SpacePostDao,
    private val userDao: UserDao,
    private val spaceId: Int,
    private val currentUserId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpacePostViewModel::class.java)) {
            return SpacePostViewModel(spacePostDao, userDao, spaceId, currentUserId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
