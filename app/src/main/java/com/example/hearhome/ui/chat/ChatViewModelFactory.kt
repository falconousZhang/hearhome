package com.example.hearhome.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hearhome.data.local.MessageDao

class ChatViewModelFactory(
    private val messageDao: MessageDao,
    private val currentUserId: Int,
    private val friendUserId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(messageDao, currentUserId, friendUserId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}