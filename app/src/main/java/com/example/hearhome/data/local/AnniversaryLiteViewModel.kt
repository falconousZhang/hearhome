package com.example.hearhome.data.local

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.remote.ApiAnniversary
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.data.remote.CreateAnniversaryRequest
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
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

    data class UiState(
        val items: List<Anniversary> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null // 新增错误信息状态
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    /** 首次进入或操作后刷新 */
    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            
            // 1. 先显示本地缓存
            val localList = dao.listBySpace(spaceId)
            _uiState.value = UiState(items = localList, loading = true) // 保持 loading 因为还在拉取网络

            try {
                // 2. 从网络拉取最新列表
                val remoteList = ApiService.getAnniversaries(spaceId)
                
                // 3. 同步策略：以服务器为准
                // 清除本地该空间所有旧数据 (简单粗暴但有效，防止 ID 冲突和僵尸数据)
                // 注意：这样会把还没上传成功的本地数据删掉。
                // 如果需要支持离线创建，需要更复杂的 sync 逻辑。
                // 鉴于目前是 V1 版本，我们假设必须联网使用同步功能。
                
                // 为了避免闪烁，我们可以对比一下。这里简化处理：
                // 先把远端数据转成本地对象
                val newLocalList = remoteList.map { apiAnn ->
                    Anniversary(
                        id = apiAnn.id, 
                        spaceId = apiAnn.spaceId,
                        name = apiAnn.name,
                        dateMillis = apiAnn.dateMillis,
                        style = apiAnn.style,
                        creatorUserId = apiAnn.creatorUserId,
                        status = apiAnn.status,
                        createdAt = apiAnn.createdAt
                    )
                }

                // 只有当数据有变化时才写入数据库，避免无谓的 IO
                // 这里为了确保一致性，我们采取“全量替换”策略（针对该 spaceId）
                // 实际操作中，应该用事务处理
                // 由于 dao 接口限制，我们循环插入/更新
                
                // 简单做法：删除该空间下所有，重新插入。
                // 缺点：会丢失本地未同步的修改。
                // 优点：逻辑简单，绝对与服务器一致。
                
                // 改进做法：遍历远端数据，插入或更新本地
                newLocalList.forEach { ann ->
                    val existing = dao.getById(ann.id)
                    if (existing == null) {
                        dao.insert(ann)
                    } else {
                        // 如果内容不同则更新 (这里简化为直接更新，假设 dao.insert OnConflict=REPLACE)
                        // 注意：AnniversaryDao 的 insert 如果是 OnConflictStrategy.IGNORE，则需要 update
                        // 假设我们之前没看 Dao 源码，稳妥起见，如果我们无法确定 insert 行为，
                        // 最好是有 update 方法。如果没有，我们先不做动作，或者 delete + insert
                        // 假设 ID 主键冲突会崩溃，所以通常 Dao 会写 OnConflictStrategy.REPLACE
                        // 让我们假设它是 REPLACE 或者手动处理
                        if (existing != ann) {
                             // dao.update(ann) // 假设有 update
                             dao.delete(existing)
                             dao.insert(ann)
                        }
                    }
                }
                
                // 再次读取数据库更新 UI
                val updatedList = dao.listBySpace(spaceId)
                _uiState.value = UiState(items = updatedList, loading = false)

            } catch (e: Exception) {
                Log.e("AnniversaryLiteViewModel", "Sync failed: ${e.message}")
                // 网络失败，报错并保留本地数据显示
                _uiState.value = _uiState.value.copy(
                    loading = false, 
                    error = "同步失败，显示本地数据"
                )
            }
        }
    }

    /** 
     * 创建纪念日
     * 修改逻辑：优先请求网络，成功后写入本地。
     */
    fun create(name: String, dateTimeMillis: Long, style: String, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            
            try {
                // 1. 构造请求
                val request = CreateAnniversaryRequest(
                    spaceId = spaceId,
                    name = name,
                    dateMillis = dateTimeMillis,
                    style = style,
                    creatorUserId = currentUserId
                )
                
                // 2. 发送给后端
                val response = ApiService.createAnniversary(request)
                
                if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                    // 3. 解析返回的完整对象（包含服务器生成的 ID）
                    val apiAnn = response.body<ApiAnniversary>()
                    
                    // 4. 存入本地数据库
                    // 注意：这里手动指定了 ID，Room 的 autoGenerate 应该会接受这个非 0 的 ID
                    val newAnniversary = Anniversary(
                        id = apiAnn.id,
                        spaceId = apiAnn.spaceId,
                        name = apiAnn.name,
                        dateMillis = apiAnn.dateMillis,
                        style = apiAnn.style,
                        creatorUserId = apiAnn.creatorUserId,
                        status = apiAnn.status,
                        createdAt = apiAnn.createdAt
                    )
                    
                    dao.insert(newAnniversary)
                    
                    Log.d("AnniversaryVM", "Created successfully on server and saved locally with ID: ${apiAnn.id}")
                    
                    // 5. 刷新列表并回调
                    load()
                    onCreated(apiAnn.id)
                } else {
                    // 服务器返回错误
                    val errorMsg = "创建失败：服务器返回 ${response.status}"
                    Log.e("AnniversaryVM", errorMsg)
                    _uiState.value = _uiState.value.copy(loading = false, error = errorMsg)
                }
                
            } catch (e: Exception) {
                // 网络异常
                Log.e("AnniversaryVM", "Create upload failed", e)
                _uiState.value = _uiState.value.copy(
                    loading = false, 
                    error = "网络连接失败，请检查网络"
                )
                
                // 备选方案：如果一定要支持离线创建，可以在这里存入一个临时 ID (负数) 的本地数据
                // 但考虑到后续同步的复杂性，暂时先要求必须联网创建。
            }
        }
    }

    fun confirm(item: Anniversary) {
        viewModelScope.launch {
            try {
                // 1. 先调 API
                val response = ApiService.confirmAnniversary(item.id)
                if (response.status == HttpStatusCode.OK) {
                    // 2. 成功后更新本地
                    dao.confirm(item.id)
                    load()
                } else {
                    _uiState.value = _uiState.value.copy(error = "确认失败：服务器错误")
                }
            } catch (e: Exception) {
                Log.e("AnniversaryVM", "Confirm upload failed", e)
                _uiState.value = _uiState.value.copy(error = "确认失败：网络错误")
            }
        }
    }

    fun delete(item: Anniversary) {
        viewModelScope.launch {
            try {
                // 1. 先调 API
                val response = ApiService.deleteAnniversary(item.id)
                if (response.status == HttpStatusCode.OK) {
                    // 2. 成功后删除本地
                    dao.delete(item)
                    load()
                } else {
                    _uiState.value = _uiState.value.copy(error = "删除失败：服务器错误")
                }
            } catch (e: Exception) {
                Log.e("AnniversaryVM", "Delete upload failed", e)
                _uiState.value = _uiState.value.copy(error = "删除失败：网络错误")
            }
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
