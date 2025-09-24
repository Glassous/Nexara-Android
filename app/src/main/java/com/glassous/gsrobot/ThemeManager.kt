package com.glassous.gsrobot

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * 主题管理器类，负责处理应用的主题切换逻辑
 */
class ThemeManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "theme_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        
        // 主题模式常量
        const val THEME_AUTO = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        
        @Volatile
        private var INSTANCE: ThemeManager? = null
        
        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 获取当前保存的主题模式
     */
    fun getCurrentThemeMode(): Int {
        return sharedPreferences.getInt(KEY_THEME_MODE, THEME_AUTO)
    }
    
    /**
     * 设置主题模式
     */
    fun setThemeMode(themeMode: Int) {
        sharedPreferences.edit().putInt(KEY_THEME_MODE, themeMode).apply()
        applyTheme(themeMode)
    }
    
    /**
     * 应用主题设置
     */
    fun applyTheme(themeMode: Int = getCurrentThemeMode()) {
        when (themeMode) {
            THEME_AUTO -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
    
    /**
     * 获取主题模式的显示名称
     */
    fun getThemeName(themeMode: Int): String {
        return when (themeMode) {
            THEME_AUTO -> "自动"
            THEME_LIGHT -> "浅色"
            THEME_DARK -> "深色"
            else -> "自动"
        }
    }
    
    /**
     * 初始化主题设置（在Application启动时调用）
     */
    fun initializeTheme() {
        applyTheme()
    }
}