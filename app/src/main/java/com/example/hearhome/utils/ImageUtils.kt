package com.example.hearhome.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 图片工具类
 * 处理图片的保存、删除等操作
 */
object ImageUtils {
    
    /**
     * 将URI对应的图片复制到应用内部存储
     * @param context 上下文
     * @param uri 图片URI
     * @return 保存后的文件路径，失败返回null
     */
    fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            // 创建图片存储目录
            val imageDir = File(context.filesDir, "images")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }
            
            // 生成唯一文件名
            val fileName = "${UUID.randomUUID()}.jpg"
            val destFile = File(imageDir, fileName)
            
            // 读取URI内容并写入文件
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // 返回文件的绝对路径
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 批量保存图片
     * @param context 上下文
     * @param uris 图片URI列表
     * @return 保存后的文件路径列表
     */
    fun saveImagesToInternalStorage(context: Context, uris: List<Uri>): List<String> {
        return uris.mapNotNull { uri ->
            saveImageToInternalStorage(context, uri)
        }
    }
    
    /**
     * 删除图片文件
     * @param filePath 文件路径
     * @return 是否删除成功
     */
    fun deleteImage(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 批量删除图片
     * @param filePaths 文件路径列表（逗号分隔的字符串）
     */
    fun deleteImages(filePaths: String) {
        if (filePaths.isBlank()) return
        
        filePaths.split(",").forEach { path ->
            deleteImage(path.trim())
        }
    }
    
    /**
     * 获取图片文件
     * @param filePath 文件路径
     * @return File对象，不存在返回null
     */
    fun getImageFile(filePath: String): File? {
        val file = File(filePath)
        return if (file.exists()) file else null
    }
    
    /**
     * 清理过期或无效的图片
     * @param context 上下文
     * @param validPaths 有效的图片路径列表
     */
    fun cleanupUnusedImages(context: Context, validPaths: Set<String>) {
        try {
            val imageDir = File(context.filesDir, "images")
            if (imageDir.exists() && imageDir.isDirectory) {
                imageDir.listFiles()?.forEach { file ->
                    if (!validPaths.contains(file.absolutePath)) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
