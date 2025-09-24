package com.glassous.gsrobot.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 模型配置数据类
 */
data class ModelConfig(
    val id: String,
    var name: String,
    var description: String = ""
)

data class GroupConfig(
    val id: String,
    var name: String,
    var baseUrl: String,
    var apiKey: String,
    val models: MutableList<ModelConfig> = mutableListOf()
)

/**
 * 标准类型配置数据类
 */
data class StandardTypeConfig(
    val id: String,
    val name: String,
    val groups: MutableList<GroupConfig> = mutableListOf()
)

/**
 * 配置管理器
 */
object ConfigManager {
    private val gson = Gson()
    
    fun standardTypeConfigToJson(config: StandardTypeConfig): String {
        return gson.toJson(config)
    }
    
    fun jsonToStandardTypeConfig(json: String): StandardTypeConfig? {
        return try {
            gson.fromJson(json, StandardTypeConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun groupConfigListToJson(groups: List<GroupConfig>): String {
        return gson.toJson(groups)
    }
    
    fun jsonToGroupConfigList(json: String): List<GroupConfig> {
        return try {
            val type = object : TypeToken<List<GroupConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}