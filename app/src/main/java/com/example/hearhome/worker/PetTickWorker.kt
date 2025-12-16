package com.example.hearhome.worker

import android.content.Context
import androidx.work.*
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.pet.PetEngine
import com.example.hearhome.pet.PetAttributes
import com.example.hearhome.data.remote.ApiPetAttributes
import com.example.hearhome.data.remote.ApiSpacePetRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 宠物属性定时衰减Worker
 * 每30分钟自动执行一次tick操作
 */
class PetTickWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val spaceDao = db.spaceDao()
            val engine = PetEngine()

            // 获取所有活跃空间
            val spaces = spaceDao.getAllSpaces()
            
            spaces.forEach { space ->
                try {
                    // 从服务器获取宠物数据
                    val remotePet = ApiService.getSpacePet(space.id)
                    
                    // 转换为本地属性并应用衰减
                    val currentAttrs = PetAttributes(
                        mood = remotePet.attributes.mood,
                        health = remotePet.attributes.health,
                        energy = remotePet.attributes.energy,
                        hydration = remotePet.attributes.hydration,
                        intimacy = remotePet.attributes.intimacy
                    )
                    
                    val decayedAttrs = engine.tickDecay(currentAttrs)
                    
                    // 同步回服务器
                    val request = ApiSpacePetRequest(
                        name = remotePet.name,
                        type = remotePet.type,
                        attributes = ApiPetAttributes(
                            mood = decayedAttrs.mood,
                            health = decayedAttrs.health,
                            energy = decayedAttrs.energy,
                            hydration = decayedAttrs.hydration,
                            intimacy = decayedAttrs.intimacy
                        )
                    )
                    
                    ApiService.saveSpacePet(space.id, request)
                } catch (e: Exception) {
                    // 某个空间失败不影响其他空间
                    e.printStackTrace()
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "pet_tick_work"

        /**
         * 启动定时任务
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<PetTickWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
        }

        /**
         * 取消定时任务
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }
    }
}
