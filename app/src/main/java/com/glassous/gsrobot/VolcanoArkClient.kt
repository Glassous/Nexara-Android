package com.glassous.gsrobot

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import android.content.Context

class VolcanoArkClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val context: Context
) {
    companion object {
        private const val TAG = "VolcanoArkClient"
    }

    /**
     * 发送聊天请求到火山方舟 API
     * @param model 模型名称 (例如: doubao-pro-4k, doubao-lite-4k)
     * @param messages 消息列表
     * @param stream 是否启用流式输出
     * @param enableWebSearch 是否启用联网搜索
     * @return 如果stream=true返回Flow<String>，否则返回完整响应
     */
    suspend fun sendChatRequest(
        model: String,
        messages: List<ChatMessage>,
        stream: Boolean = false,
        enableWebSearch: Boolean = false
    ): Flow<String> = flow {
        try {
            val url = URL("$baseUrl/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            
            // 设置请求头 - 火山方舟使用Bearer Token认证
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            
            // 构建请求体
            val requestBody = buildRequestBody(model, messages, stream, enableWebSearch)
            Log.d(TAG, "Request body: $requestBody")
            
            // 发送请求
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(requestBody)
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                
                if (stream) {
                    // 处理流式响应
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { processStreamLine(it) }?.let { content ->
                            if (content.isNotEmpty()) {
                                emit(content)
                            }
                        }
                    }
                } else {
                    // 处理非流式响应
                    val response = reader.readText()
                    Log.d(TAG, "Response: $response")
                    val content = parseNonStreamResponse(response)
                    emit(content)
                }
                
                reader.close()
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
                val errorResponse = errorReader.readText()
                Log.e(TAG, "Error response: $errorResponse")
                errorReader.close()
                emit("火山方舟API请求失败: $errorResponse")
            }
            
            connection.disconnect()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending request", e)
            emit("火山方舟网络请求失败: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 构建火山方舟API请求体
     */
    private fun buildRequestBody(model: String, messages: List<ChatMessage>, stream: Boolean, enableWebSearch: Boolean = false): String {
        val jsonObject = JSONObject()
        jsonObject.put("model", model)
        jsonObject.put("stream", stream)
        
        val messagesArray = JSONArray()
        messages.forEach { message ->
            val messageObj = JSONObject()
            messageObj.put("role", if (message.isFromUser) "user" else "assistant")
            
            // 如果消息包含图片，使用content数组格式
            if (message.imageUri != null && message.isFromUser) {
                val contentArray = JSONArray()
                
                // 添加文本内容
                if (message.content.isNotEmpty()) {
                    val textContent = JSONObject()
                    textContent.put("type", "text")
                    textContent.put("text", message.content)
                    contentArray.put(textContent)
                }
                
                // 添加图片内容 - 火山方舟支持图片输入
                val imageContent = JSONObject()
                imageContent.put("type", "image_url")
                val imageUrl = JSONObject()
                imageUrl.put("url", message.imageUri)
                imageContent.put("image_url", imageUrl)
                contentArray.put(imageContent)
                
                messageObj.put("content", contentArray)
            } else {
                // 普通文本消息
                messageObj.put("content", message.content)
            }
            
            messagesArray.put(messageObj)
        }
        jsonObject.put("messages", messagesArray)
        
        // 添加联网搜索工具配置
        if (enableWebSearch) {
            val toolsArray = JSONArray()
            val webSearchTool = JSONObject()
            webSearchTool.put("type", "function")
            
            val functionObj = JSONObject()
            functionObj.put("name", "web_search")
            functionObj.put("description", "搜索互联网获取最新信息")
            
            val parametersObj = JSONObject()
            parametersObj.put("type", "object")
            
            val propertiesObj = JSONObject()
            val queryProperty = JSONObject()
            queryProperty.put("type", "string")
            queryProperty.put("description", "搜索查询关键词")
            propertiesObj.put("query", queryProperty)
            
            parametersObj.put("properties", propertiesObj)
            val requiredArray = JSONArray()
            requiredArray.put("query")
            parametersObj.put("required", requiredArray)
            
            functionObj.put("parameters", parametersObj)
            webSearchTool.put("function", functionObj)
            
            toolsArray.put(webSearchTool)
            jsonObject.put("tools", toolsArray)
            Log.d(TAG, "Web search function tool enabled for Volcano Ark API")
        }
        
        // 添加其他参数 - 使用火山方舟配置的全局设置
        val maxTokens = getVolcanoArkMaxTokens()
        val temperature = getVolcanoArkTemperature()
        
        if (maxTokens > 0) {
            jsonObject.put("max_tokens", maxTokens)
        }
        if (temperature >= 0) {
            jsonObject.put("temperature", temperature)
        }
        
        Log.d(TAG, "Using Volcano Ark parameters - Temperature: $temperature, Max Tokens: $maxTokens")
        
        return jsonObject.toString()
    }

    /**
     * 获取火山方舟配置的最大令牌数
     */
    private fun getVolcanoArkMaxTokens(): Int {
        val sharedPreferences = context.getSharedPreferences("volcano_ark_model_config", Context.MODE_PRIVATE)
        val maxTokensEnabled = sharedPreferences.getBoolean("max_tokens_enabled", false)
        return if (maxTokensEnabled) {
            val percentage = sharedPreferences.getFloat("max_tokens_value", 100f)
            // 将百分比转换为实际token数：100% = 4096 tokens
            (percentage * 4096 / 100).toInt()
        } else {
            0 // 不设置max_tokens参数
        }
    }

    /**
     * 获取火山方舟配置的温度值
     */
    private fun getVolcanoArkTemperature(): Float {
        val sharedPreferences = context.getSharedPreferences("volcano_ark_model_config", Context.MODE_PRIVATE)
        val temperatureEnabled = sharedPreferences.getBoolean("temperature_enabled", false)
        return if (temperatureEnabled) {
            val percentage = sharedPreferences.getFloat("temperature_value", 50f)
            // 将百分比转换为实际温度值：0-100% 对应 0.0-1.0
            percentage / 100f
        } else {
            -1f // 不设置temperature参数
        }
    }

    /**
     * 处理流式响应的单行数据
     */
    private fun processStreamLine(line: String): String? {
        try {
            if (line.startsWith("data: ")) {
                val data = line.substring(6).trim()
                
                if (data == "[DONE]") {
                    return null
                }
                
                val jsonObject = JSONObject(data)
                val choices = jsonObject.optJSONArray("choices")
                
                if (choices != null && choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    val delta = choice.optJSONObject("delta")
                    val finishReason = choice.optString("finish_reason", "")
                    
                    if (delta != null) {
                        // 处理普通内容
                        val content = delta.optString("content", "")
                        if (content.isNotEmpty()) {
                            return content
                        }
                        
                        // 处理tool_calls（联网搜索等function calling）
                        val toolCalls = delta.optJSONArray("tool_calls")
                        if (toolCalls != null && toolCalls.length() > 0) {
                            val toolCall = toolCalls.getJSONObject(0)
                            val function = toolCall.optJSONObject("function")
                            if (function != null) {
                                val functionName = function.optString("name", "")
                                if (functionName == "web_search") {
                                    // 当检测到联网搜索工具调用时，返回提示信息
                                    return "🔍 正在联网搜索..."
                                }
                            }
                        }
                    }
                    
                    // 处理finish_reason为tool_calls的情况
                    if (finishReason == "tool_calls") {
                        return "\n\n📝 正在整理搜索结果..."
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing stream line: $line", e)
        }
        
        return null
    }

    /**
     * 解析非流式响应
     */
    private fun parseNonStreamResponse(response: String): String {
        try {
            val jsonObject = JSONObject(response)
            val choices = jsonObject.optJSONArray("choices")
            
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val message = choice.optJSONObject("message")
                
                if (message != null) {
                    return message.optString("content", "")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
        }
        
        return "解析火山方舟响应失败"
    }
}