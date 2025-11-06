package com.example.hearhome.utils

import android.content.Context
import java.io.File

/**
 * 测试工具类
 * 用于开发环境下的功能测试
 */
object TestUtils {
    
    /**
     * 创建一个模拟的语音文件用于测试
     * 实际项目中应该使用真实录音
     * 
     * @return 返回模拟音频文件路径，如果失败返回null
     */
    fun createMockAudioFile(context: Context): String? {
        return try {
            // 创建测试音频目录
            val audioDir = File(context.filesDir, "audios/test")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            // 创建一个空的音频文件模拟
            val audioFile = File(audioDir, "mock_audio_${System.currentTimeMillis()}.m4a")
            audioFile.createNewFile()
            
            // 写入一些模拟数据（实际上这不是真正的音频数据）
            audioFile.writeText("Mock audio data for testing")
            
            audioFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 获取模拟音频的时长（固定返回5秒）
     */
    fun getMockAudioDuration(): Long {
        return 5000L // 5秒
    }
    
    /**
     * 检查是否在模拟器中运行
     */
    fun isEmulator(): Boolean {
        return (android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || "google_sdk" == android.os.Build.PRODUCT)
    }
    
    /**
     * 创建测试用的图片文件
     * 方便在没有相机/相册的环境下测试图片上传功能
     */
    fun createMockImageFile(context: Context): File? {
        return try {
            val imageDir = File(context.filesDir, "images/test")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }
            
            val imageFile = File(imageDir, "mock_image_${System.currentTimeMillis()}.jpg")
            
            // 创建一个简单的彩色图片（纯色方块）
            // 这里使用 Android 的 Bitmap API
            val bitmap = android.graphics.Bitmap.createBitmap(
                400, 400, 
                android.graphics.Bitmap.Config.ARGB_8888
            )
            
            // 填充随机颜色
            val canvas = android.graphics.Canvas(bitmap)
            val colors = listOf(
                android.graphics.Color.RED,
                android.graphics.Color.BLUE,
                android.graphics.Color.GREEN,
                android.graphics.Color.YELLOW,
                android.graphics.Color.MAGENTA,
                android.graphics.Color.CYAN
            )
            val paint = android.graphics.Paint().apply {
                color = colors.random()
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, 400f, 400f, paint)
            
            // 添加文字标识
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 40f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText(
                "测试图片",
                200f,
                180f,
                textPaint
            )
            canvas.drawText(
                System.currentTimeMillis().toString().takeLast(6),
                200f,
                220f,
                textPaint
            )
            
            // 保存为JPEG
            imageFile.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            
            imageFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 获取所有测试文件列表
     */
    fun listTestFiles(context: Context): List<String> {
        val testFiles = mutableListOf<String>()
        
        // 音频测试文件
        val audioTestDir = File(context.filesDir, "audios/test")
        if (audioTestDir.exists()) {
            audioTestDir.listFiles()?.forEach {
                testFiles.add("Audio: ${it.name}")
            }
        }
        
        // 图片测试文件
        val imageTestDir = File(context.filesDir, "images/test")
        if (imageTestDir.exists()) {
            imageTestDir.listFiles()?.forEach {
                testFiles.add("Image: ${it.name}")
            }
        }
        
        return testFiles
    }
    
    /**
     * 清理所有测试文件
     */
    fun cleanTestFiles(context: Context) {
        // 清理音频测试文件
        val audioTestDir = File(context.filesDir, "audios/test")
        if (audioTestDir.exists()) {
            audioTestDir.deleteRecursively()
        }
        
        // 清理图片测试文件
        val imageTestDir = File(context.filesDir, "images/test")
        if (imageTestDir.exists()) {
            imageTestDir.deleteRecursively()
        }
    }
}
