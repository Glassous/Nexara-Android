package com.glassous.gsrobot

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import android.content.Context

class OpenAIClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val context: Context
) {
    companion object {
        private const val TAG = "OpenAIClient"
    }

    /**
     * 发送聊天请求到OpenAI API
     * @param model 模型名称 (例如: gpt-4, gpt-3.5-turbo)
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
            
            // 设置请求头
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
                emit("API请求失败: $errorResponse")
            }
            
            connection.disconnect()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending request", e)
            emit("网络请求失败: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 构建OpenAI API请求体
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
                
                // 添加图片内容
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
        
        // 添加联网搜索工具
        if (enableWebSearch) {
            val toolsArray = JSONArray()
            val webSearchTool = JSONObject()
            webSearchTool.put("type", "web_search")
            toolsArray.put(webSearchTool)
            jsonObject.put("tools", toolsArray)
            Log.d(TAG, "Web search tool enabled")
        }
        
        // 添加其他参数 - 使用全局设置
        val maxTokens = GlobalParametersManager.getMaxTokens(context)
        val temperature = GlobalParametersManager.getTemperature(context)
        
        jsonObject.put("max_tokens", maxTokens)
        jsonObject.put("temperature", temperature)
        
        Log.d(TAG, "Using global parameters - Temperature: $temperature, Max Tokens: $maxTokens, Web Search: $enableWebSearch")
        
        return jsonObject.toString()
    }

    /**
     * 处理流式响应的单行数据
     */
    private fun processStreamLine(line: String): String? {
        if (line.startsWith("data: ")) {
            val data = line.substring(6).trim()
            if (data == "[DONE]") {
                return null
            }
            
            try {
                val jsonObject = JSONObject(data)
                val choices = jsonObject.getJSONArray("choices")
                if (choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    val delta = choice.getJSONObject("delta")
                    if (delta.has("content")) {
                        return delta.getString("content")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing stream line: $line", e)
            }
        }
        return null
    }

    /**
     * 解析非流式响应
     */
    private fun parseNonStreamResponse(response: String): String {
        try {
            val jsonObject = JSONObject(response)
            val choices = jsonObject.getJSONArray("choices")
            if (choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val message = choice.getJSONObject("message")
                return message.getString("content")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
        }
        return "解析响应失败"
    }
}