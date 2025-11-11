package com.example.hearhome.utils

import com.example.hearhome.data.local.Space
import com.example.hearhome.data.local.SpaceDao

/**
 * 打卡辅助工具类
 * 用于检查用户是否需要发布动态打卡
 */
object CheckInHelper {
    
    /**
     * 检查用户是否需要打卡
     * @param space 空间信息
     * @param userId 用户ID
     * @param spaceDao 空间DAO
     * @return true 表示需要打卡（超过了设定的时间间隔），false 表示不需要
     */
    suspend fun needsCheckIn(
        space: Space,
        userId: Int,
        spaceDao: SpaceDao
    ): Boolean {
        // 如果未设置打卡间隔，返回 false
        if (space.checkInIntervalSeconds <= 0) {
            return false
        }
        
        // 获取用户最后一次发布动态的时间
        val lastPostTime = spaceDao.getLastPostTimeByUser(space.id, userId)
        
        // 如果从未发布过动态，需要打卡
        if (lastPostTime == null) {
            return true
        }
        
        // 计算距离上次发布的时间间隔（秒）
        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - lastPostTime) / 1000
        
        // 如果超过设定的间隔时间，需要打卡
        return elapsedSeconds >= space.checkInIntervalSeconds
    }
    
    /**
     * 获取距离下次打卡的剩余时间（秒）
     * @return 剩余秒数，如果已经超时返回0，如果未设置打卡返回-1
     */
    suspend fun getRemainingTime(
        space: Space,
        userId: Int,
        spaceDao: SpaceDao
    ): Long {
        if (space.checkInIntervalSeconds <= 0) {
            return -1
        }
        
        val lastPostTime = spaceDao.getLastPostTimeByUser(space.id, userId)
        if (lastPostTime == null) {
            return 0
        }
        
        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - lastPostTime) / 1000
        val remaining = space.checkInIntervalSeconds - elapsedSeconds
        
        return if (remaining > 0) remaining else 0
    }
    
    /**
     * 格式化时间间隔为易读的字符串
     * @param seconds 秒数
     * @return 格式化后的字符串，如 "1天2小时30分钟"
     */
    fun formatInterval(seconds: Long): String {
        if (seconds <= 0) return "0秒"
        
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}天")
        if (hours > 0) parts.add("${hours}小时")
        if (minutes > 0) parts.add("${minutes}分钟")
        if (secs > 0 && parts.isEmpty()) parts.add("${secs}秒")
        
        return parts.joinToString("")
    }
    
    /**
     * 获取打卡状态的描述文本
     */
    suspend fun getCheckInStatusText(
        space: Space,
        userId: Int,
        spaceDao: SpaceDao
    ): String {
        if (space.checkInIntervalSeconds <= 0) {
            return "未设置打卡"
        }
        
        val remaining = getRemainingTime(space, userId, spaceDao)
        
        return when {
            remaining < 0 -> "未设置打卡"
            remaining == 0L -> "需要打卡"
            else -> "还剩 ${formatInterval(remaining)}"
        }
    }
}
