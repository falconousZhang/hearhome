package com.example.hearhome.data.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnniversaryUiState(
    val loading: Boolean = false,
    val items: List<Anniversary> = emptyList(),
    val error: String? = null
)

/** 通过构造函数注入 DAO，避免直接依赖数据库类静态调用 */
class AnniversaryViewModel(
    app: Application,
    private val dao: AnniversaryDao
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(AnniversaryUiState())
    val state = _state.asStateFlow()

    fun load(spaceId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                _state.value = AnniversaryUiState(items = dao.listBySpace(spaceId))
            } catch (t: Throwable) {
                _state.value = AnniversaryUiState(error = t.message ?: "加载失败")
            }
        }
    }

    fun create(
        spaceId: Int,
        name: String,
        dateMillis: Long,
        style: String,
        creatorUserId: Int,
        onCreated: (Int) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val id = dao.insert(
                    Anniversary(
                        spaceId = spaceId,
                        name = name,
                        dateMillis = dateMillis,
                        style = style,
                        creatorUserId = creatorUserId,
                        status = "pending"
                    )
                ).toInt()
                load(spaceId)
                onCreated(id)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "创建失败")
            }
        }
    }

    fun confirm(spaceId: Int, anniversaryId: Int) {
        viewModelScope.launch {
            try {
                dao.confirm(anniversaryId)
                load(spaceId)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "确认失败")
            }
        }
    }

    fun delete(spaceId: Int, anniversary: Anniversary) {
        viewModelScope.launch {
            try {
                dao.delete(anniversary)
                load(spaceId)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "删除失败")
            }
        }
    }
}

/** Factory：统一从 AppDatabase 取 DAO 注入 */
class AnniversaryViewModelFactory(
    private val app: Application
) : ViewModelProvider.AndroidViewModelFactory(app) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnniversaryViewModel::class.java)) {
            val dao: AnniversaryDao = AppDatabase.getInstance(app).anniversaryDao()
            return AnniversaryViewModel(app, dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
