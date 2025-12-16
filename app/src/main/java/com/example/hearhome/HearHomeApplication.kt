package com.example.hearhome

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.hearhome.worker.PetTickWorker

/**
 * 应用程序类
 * 用于全局配置和初始化
 */
class HearHomeApplication : Application(), ImageLoaderFactory {
    
    override fun onCreate() {
        super.onCreate()
        
        // 启动宠物定时衰减任务
        PetTickWorker.schedule(this)
    }
    
    /**
     * 配置 Coil 图片加载器
     * 确保能够正确加载应用内部存储的图片文件
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)  // 使用25%内存作为缓存
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)  // 使用2%磁盘空间作为缓存
                    .build()
            }
            // 允许从网络、磁盘和内存缓存读取
            .respectCacheHeaders(false)
            .build()
    }
}

