package com.example.hearhome.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

/**
 * 图片工具类
 * 处理图片的保存、删除、压缩等操作
 */
object ImageUtils {
    
    // 默认图片压缩配置
    private const val MAX_IMAGE_WIDTH = 1920
    private const val MAX_IMAGE_HEIGHT = 1920
    private const val JPEG_QUALITY = 85
    
    /**
     * 将URI对应的图片复制到应用内部存储（带压缩）
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
            
            // 读取、压缩并保存图片
            context.contentResolver.openInputStream(uri)?.use { input ->
                // 获取图片方向信息
                val exif = try {
                    ExifInterface(input)
                } catch (e: Exception) {
                    null
                }
                val orientation = exif?.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                ) ?: ExifInterface.ORIENTATION_UNDEFINED
                
                // 重新打开输入流读取图片
                context.contentResolver.openInputStream(uri)?.use { imageInput ->
                    val bitmap = BitmapFactory.decodeStream(imageInput)
                    if (bitmap != null) {
                        // 压缩并保存
                        val compressedBitmap = compressBitmap(bitmap, orientation)
                        FileOutputStream(destFile).use { output ->
                            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                        }
                        
                        // 释放资源
                        if (compressedBitmap != bitmap) {
                            compressedBitmap.recycle()
                        }
                        bitmap.recycle()
                    } else {
                        // 如果解码失败，直接复制原文件
                        context.contentResolver.openInputStream(uri)?.use { fallbackInput ->
                            FileOutputStream(destFile).use { output ->
                                fallbackInput.copyTo(output)
                            }
                        }
                    }
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
     * 压缩图片
     * @param bitmap 原始图片
     * @param orientation EXIF方向信息
     * @return 压缩后的图片
     */
    private fun compressBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // 计算缩放比例
        val scale = min(
            MAX_IMAGE_WIDTH.toFloat() / width,
            MAX_IMAGE_HEIGHT.toFloat() / height
        )
        
        // 如果图片已经足够小，只处理旋转
        val scaledBitmap = if (scale >= 1.0f) {
            bitmap
        } else {
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
        
        // 根据 EXIF 信息旋转图片
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(scaledBitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(scaledBitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(scaledBitmap, 270f)
            else -> scaledBitmap
        }
    }
    
    /**
     * 旋转图片
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
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
