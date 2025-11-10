package com.example.hearhome.data.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 轻量版 VM：页面进入会主动加载；新建提供 onCreated 回调返回新 ID。
 */
class AnniversaryLiteViewModel(
    private val dao: AnniversaryDao,
    private val spaceId: Int,
    private val currentUserId: Int
) : ViewModel() {

    data class UiState(val items: List<Anniversary> = emptyList(), val loading: Boolean = false)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    /** 首次进入或操作后刷新 */
    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val list = dao.listBySpace(spaceId)
            _uiState.value = UiState(items = list, loading = false)
        }
    }

    /** 创建后把真实 id 通过回调给 UI，再排提醒；并刷新列表 */
    fun create(name: String, dateTimeMillis: Long, style: String, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val id = dao.insert(
                Anniversary(
                    spaceId = spaceId,
                    name = name,
                    dateMillis = dateTimeMillis,
                    style = style,
                    creatorUserId = currentUserId,
                    status = "pending"
                )
            ).toInt()
            onCreated(id)
            load()
        }
    }

    fun confirm(item: Anniversary) {
        viewModelScope.launch {
            dao.confirm(item.id)
            load()
        }
    }

    fun delete(item: Anniversary) {
        viewModelScope.launch {
            dao.delete(item)
            load()
        }
    }
}

class AnniversaryLiteViewModelFactory(
    private val dao: AnniversaryDao,
    private val spaceId: Int,
    private val currentUserId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AnniversaryLiteViewModel(dao, spaceId, currentUserId) as T
    }
}
