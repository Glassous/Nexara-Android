package com.glassous.gsrobot

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局参数管理器
 * 用于管理OpenAI API调用的全局温度和最大token数设置
 */
object GlobalParametersManager {
    
    private const val PREFS_NAME = "model_config"
    private const val KEY_TEMPERATURE_ENABLED = "temperature_enabled"
    private const val KEY_TEMPERATURE_VALUE = "temperature_value"
    private const val KEY_MAX_TOKENS_ENABLED = "max_tokens_enabled"
    private const val KEY_MAX_TOKENS_VALUE = "max_tokens_value"
    
    // 默认值 - 温度50%对应1.0，最大token数100%对应4096
    private const val DEFAULT_TEMPERATURE = 1.0  // 50%
    private const val DEFAULT_MAX_TOKENS = 4096   // 100%
    
    /**
     * 获取当前的温度设置
     * @param context 上下文
     * @return 温度值，如果未启用则返回默认值
     */
    fun getTemperature(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_TEMPERATURE_ENABLED, false)
        
        return if (isEnabled) {
            val percentage = prefs.getFloat(KEY_TEMPERATURE_VALUE, 50f)
            // 将百分比转换为0-2范围的温度值
            (percentage / 100.0 * 2.0)
        } else {
            DEFAULT_TEMPERATURE
        }
    }
    
    /**
     * 获取当前的最大token数设置
     * @param context 上下文
     * @return 最大token数，如果未启用则返回默认值
     */
    fun getMaxTokens(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_MAX_TOKENS_ENABLED, false)
        
        return if (isEnabled) {
            val percentage = prefs.getFloat(KEY_MAX_TOKENS_VALUE, 100f)
            // 将百分比转换为1-4096范围的token数
            val minTokens = 1
            val maxTokens = 4096
            (minTokens + (percentage / 100.0 * (maxTokens - minTokens))).toInt()
        } else {
            DEFAULT_MAX_TOKENS
        }
    }
    
    /**
     * 检查温度设置是否启用
     * @param context 上下文
     * @return 是否启用温度设置
     */
    fun isTemperatureEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TEMPERATURE_ENABLED, false)
    }
    
    /**
     * 检查最大token数设置是否启用
     * @param context 上下文
     * @return 是否启用最大token数设置
     */
    fun isMaxTokensEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MAX_TOKENS_ENABLED, false)
    }
    
    /**
     * 获取温度百分比值
     * @param context 上下文
     * @return 温度百分比值 (0-100)
     */
    fun getTemperaturePercentage(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_TEMPERATURE_VALUE, 50f)
    }
    
    /**
     * 获取最大token数百分比值
     * @param context 上下文
     * @return 最大token数百分比值 (0-100)
     */
    fun getMaxTokensPercentage(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_MAX_TOKENS_VALUE, 100f)
    }
}