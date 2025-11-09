package com.example.hearhome.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 安全配置管理器
 * 使用加密存储敏感信息
 */
object SecureConfigManager {
    
    private const val PREFS_FILE_NAME = "secure_config"
    
    // 配置键
    private const val KEY_SMTP_HOST = "smtp_host"
    private const val KEY_SMTP_PORT = "smtp_port"
    private const val KEY_EMAIL_FROM = "email_from"
    private const val KEY_EMAIL_PASSWORD = "email_password"
    private const val KEY_SMS_API_URL = "sms_api_url"
    private const val KEY_SMS_API_KEY = "sms_api_key"
    private const val KEY_SMS_API_SECRET = "sms_api_secret"
    
    /**
     * 获取加密的SharedPreferences
     */
    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // ==================== 邮件配置 ====================
    
    /**
     * 保存邮件配置
     */
    fun saveEmailConfig(
        context: Context,
        smtpHost: String,
        smtpPort: String,
        emailFrom: String,
        password: String
    ) {
        getEncryptedPrefs(context).edit().apply {
            putString(KEY_SMTP_HOST, smtpHost)
            putString(KEY_SMTP_PORT, smtpPort)
            putString(KEY_EMAIL_FROM, emailFrom)
            putString(KEY_EMAIL_PASSWORD, password)
            apply()
        }
    }
    
    /**
     * 获取SMTP主机
     */
    fun getSmtpHost(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_SMTP_HOST, "") ?: ""
    }
    
    /**
     * 获取SMTP端口
     */
    fun getSmtpPort(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_SMTP_PORT, "587") ?: "587"
    }
    
    /**
     * 获取发件邮箱
     */
    fun getEmailFrom(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_EMAIL_FROM, "") ?: ""
    }
    
    /**
     * 获取邮箱密码
     */
    fun getEmailPassword(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_EMAIL_PASSWORD, "") ?: ""
    }
    
    /**
     * 检查邮件配置是否完整
     */
    fun isEmailConfigured(context: Context): Boolean {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(KEY_SMTP_HOST, "")?.isNotEmpty() == true &&
                prefs.getString(KEY_EMAIL_FROM, "")?.isNotEmpty() == true &&
                prefs.getString(KEY_EMAIL_PASSWORD, "")?.isNotEmpty() == true
    }
    
    // ==================== 短信配置 ====================
    
    /**
     * 保存短信配置
     */
    fun saveSmsConfig(
        context: Context,
        apiUrl: String,
        apiKey: String,
        apiSecret: String
    ) {
        getEncryptedPrefs(context).edit().apply {
            putString(KEY_SMS_API_URL, apiUrl)
            putString(KEY_SMS_API_KEY, apiKey)
            putString(KEY_SMS_API_SECRET, apiSecret)
            apply()
        }
    }
    
    /**
     * 获取短信API URL
     */
    fun getSmsApiUrl(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_SMS_API_URL, "") ?: ""
    }
    
    /**
     * 获取短信API Key
     */
    fun getSmsApiKey(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_SMS_API_KEY, "") ?: ""
    }
    
    /**
     * 获取短信API Secret
     */
    fun getSmsApiSecret(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_SMS_API_SECRET, "") ?: ""
    }
    
    /**
     * 检查短信配置是否完整
     */
    fun isSmsConfigured(context: Context): Boolean {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(KEY_SMS_API_URL, "")?.isNotEmpty() == true &&
                prefs.getString(KEY_SMS_API_KEY, "")?.isNotEmpty() == true &&
                prefs.getString(KEY_SMS_API_SECRET, "")?.isNotEmpty() == true
    }
    
    /**
     * 清除所有配置
     */
    fun clearAllConfig(context: Context) {
        getEncryptedPrefs(context).edit().clear().apply()
    }
    
    /**
     * 清除邮件配置
     */
    fun clearEmailConfig(context: Context) {
        getEncryptedPrefs(context).edit().apply {
            remove(KEY_SMTP_HOST)
            remove(KEY_SMTP_PORT)
            remove(KEY_EMAIL_FROM)
            remove(KEY_EMAIL_PASSWORD)
            apply()
        }
    }
    
    /**
     * 清除短信配置
     */
    fun clearSmsConfig(context: Context) {
        getEncryptedPrefs(context).edit().apply {
            remove(KEY_SMS_API_URL)
            remove(KEY_SMS_API_KEY)
            remove(KEY_SMS_API_SECRET)
            apply()
        }
    }
}
