package com.example.hearhome.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.Message
import com.example.hearhome.data.local.MessageDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val messageDao: MessageDao,
    private val currentUserId: Int,
    private val friendUserId: Int
) : ViewModel() {

    val messages: StateFlow<List<Message>> = 
        messageDao.getMessagesBetweenUsers(currentUserId, friendUserId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun sendMessage(content: String) {
        viewModelScope.launch {
            val message = Message(
                senderId = currentUserId,
                receiverId = friendUserId,
                content = content,
                timestamp = System.currentTimeMillis(),
                isRead = false
            )
            messageDao.insertMessage(message)
        }
    }
}