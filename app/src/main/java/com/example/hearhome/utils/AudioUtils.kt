package com.example.hearhome.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 语音工具类
 * 处理语音录制、播放、保存等操作
 */
object AudioUtils {
    
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null
    
    /**
     * 开始录音
     * @param context 上下文
     * @return 录音文件路径，失败返回null
     */
    fun startRecording(context: Context): String? {
        return try {
            // 创建语音存储目录
            val audioDir = File(context.filesDir, "audios")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            // 生成唯一文件名
            val fileName = "${UUID.randomUUID()}.m4a"
            currentRecordingFile = File(audioDir, fileName)
            
            // 初始化MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentRecordingFile?.absolutePath)
                
                try {
                    prepare()
                    start()
                } catch (e: IOException) {
                    e.printStackTrace()
                    return null
                }
            }
            
            currentRecordingFile?.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 停止录音
     * @return 录音文件路径，失败返回null
     */
    fun stopRecording(): String? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            currentRecordingFile?.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 取消录音（删除录音文件）
     */
    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            currentRecordingFile?.delete()
            currentRecordingFile = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 播放语音
     * @param filePath 语音文件路径
     * @param onCompletion 播放完成回调
     * @return 是否成功开始播放
     */
    fun playAudio(filePath: String, onCompletion: (() -> Unit)? = null): Boolean {
        return try {
            // 释放之前的播放器
            stopAudio()
            
            val file = File(filePath)
            if (!file.exists()) {
                return false
            }
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener {
                    onCompletion?.invoke()
                    release()
                    mediaPlayer = null
                }
                prepare()
                start()
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 停止播放
     */
    fun stopAudio() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 暂停播放
     */
    fun pauseAudio() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 恢复播放
     */
    fun resumeAudio() {
        try {
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取音频时长（毫秒）
     * @param filePath 音频文件路径
     * @return 时长（毫秒），失败返回0
     */
    fun getAudioDuration(filePath: String): Long {
        return try {
            val mp = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
            }
            val duration = mp.duration.toLong()
            mp.release()
            duration
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }
    
    /**
     * 删除语音文件
     * @param filePath 文件路径
     * @return 是否删除成功
     */
    fun deleteAudio(filePath: String): Boolean {
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
     * 格式化音频时长
     * @param duration 时长（毫秒）
     * @return 格式化字符串，如 "00:15"
     */
    fun formatDuration(duration: Long): String {
        val seconds = (duration / 1000) % 60
        val minutes = (duration / 1000) / 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    /**
     * 清理过期或无效的语音文件
     * @param context 上下文
     * @param validPaths 有效的语音路径列表
     */
    fun cleanupUnusedAudios(context: Context, validPaths: Set<String>) {
        try {
            val audioDir = File(context.filesDir, "audios")
            if (audioDir.exists() && audioDir.isDirectory) {
                audioDir.listFiles()?.forEach { file ->
                    if (!validPaths.contains(file.absolutePath)) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 释放所有资源
     */
    fun release() {
        try {
            mediaRecorder?.release()
            mediaRecorder = null
            
            mediaPlayer?.release()
            mediaPlayer = null
            
            currentRecordingFile = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
