package com.glassous.gsrobot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AlibabaCloudBailianClient(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "AlibabaCloudBailianClient"
        private const val BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        private const val PREFS_NAME = "alibaba_cloud_bailian_model_config"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_TEMPERATURE_ENABLED = "temperature_enabled"
        private const val KEY_TEMPERATURE_VALUE = "temperature_value"
        private const val KEY_MAX_TOKENS_ENABLED = "max_tokens_enabled"
        private const val KEY_MAX_TOKENS_VALUE = "max_tokens_value"
    }

    private fun getApiKey(): String? {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_API_KEY, null)
    }

    private fun getGlobalConfig(): JSONObject {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val config = JSONObject()
        
        if (sharedPreferences.getBoolean(KEY_TEMPERATURE_ENABLED, false)) {
            val temperatureValue = sharedPreferences.getFloat(KEY_TEMPERATURE_VALUE, 50f)
            config.put("temperature", temperatureValue / 100.0)
        }
        
        if (sharedPreferences.getBoolean(KEY_MAX_TOKENS_ENABLED, false)) {
            val maxTokensValue = sharedPreferences.getFloat(KEY_MAX_TOKENS_VALUE, 100f)
            val maxTokens = (maxTokensValue / 100.0 * 8192).toInt().coerceAtLeast(1)
            config.put("max_tokens", maxTokens)
        }
        
        return config
    }

    fun sendChatRequest(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String,
        enableWebSearch: Boolean = false
    ): Flow<String> = flow {
        if (apiKey.isNullOrEmpty()) {
            emit("Error: 阿里云百炼 API key 未配置")
            return@flow
        }

        try {
            // 构建请求体 - 始终启用流式输出
            val requestBody = buildRequestBody(model, messages, enableWebSearch)
            
            // 阿里云百炼使用专用的API端点
            val url = if (enableWebSearch) {
                "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
            } else {
                "$BASE_URL/chat/completions"
            }
            
            Log.d(TAG, "Sending request to: $url")
            Log.d(TAG, "Request body: $requestBody")

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .apply {
                    // 阿里云百炼流式输出需要设置SSE头
                    if (enableWebSearch) {
                        addHeader("X-DashScope-SSE", "enable")
                    }
                }
                .build()

            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "API request failed: ${response.code} - $errorBody")
                emit("Error: 阿里云百炼 API 请求失败 (${response.code}): $errorBody")
                return@flow
            }

            response.body?.let { responseBody ->
                // 始终使用流式响应处理
                if (enableWebSearch) {
                    // 处理阿里云百炼联网搜索的SSE流式响应
                    handleWebSearchStreamingResponse(responseBody)
                } else {
                    // 处理OpenAI兼容的SSE流式响应
                    handleOpenAICompatibleStreamingResponse(responseBody)
                }
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}")
            emit("Error: 网络连接失败 - ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}")
            emit("Error: 请求处理失败 - ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(
        model: String,
        messages: List<ChatMessage>,
        enableWebSearch: Boolean
    ): String {
        return if (enableWebSearch) {
            // 使用阿里云百炼专用的联网搜索API格式
            buildWebSearchRequestBody(model, messages)
        } else {
            // 使用OpenAI兼容格式
            buildOpenAICompatibleRequestBody(model, messages)
        }
    }

    private fun buildOpenAICompatibleRequestBody(
        model: String,
        messages: List<ChatMessage>
    ): String {
        val requestJson = JSONObject()
        requestJson.put("model", model)
        requestJson.put("stream", true)
        
        // 添加全局配置参数
        val globalConfig = getGlobalConfig()
        globalConfig.keys().forEach { key ->
            requestJson.put(key, globalConfig.get(key))
        }
        
        val messagesArray = JSONArray()
        
        messages.forEach { message ->
            val messageObject = JSONObject()
            val role = if (message.isFromUser) "user" else "assistant"
            messageObject.put("role", role)
            
            // 检查是否包含图片
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
                
                messageObject.put("content", contentArray)
            } else {
                // 普通文本消息
                messageObject.put("content", message.content)
            }
            
            messagesArray.put(messageObject)
        }
        
        requestJson.put("messages", messagesArray)
        
        return requestJson.toString()
    }

    private suspend fun FlowCollector<String>.handleOpenAICompatibleStreamingResponse(responseBody: ResponseBody) {
        // 处理OpenAI兼容的SSE流式响应
        responseBody.source().use { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line()
                if (line != null) {
                    Log.d(TAG, "SSE Line: $line")
                    
                    // 处理SSE格式的数据行
                    if (line.startsWith("data: ")) {
                        val jsonData = line.substring(6) // 移除 "data: " 前缀
                        
                        // 跳过空数据行和结束标记
                        if (jsonData.trim().isEmpty() || jsonData.trim() == "[DONE]") {
                            continue
                        }
                        
                        try {
                            val jsonObject = JSONObject(jsonData)
                            val choices = jsonObject.optJSONArray("choices")
                            
                            if (choices != null && choices.length() > 0) {
                                val choice = choices.getJSONObject(0)
                                val delta = choice.optJSONObject("delta")
                                
                                if (delta != null) {
                                    val content = delta.optString("content", "")
                                    if (content.isNotEmpty()) {
                                        emit(content)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing SSE data: ${e.message}")
                            Log.e(TAG, "SSE data content: $jsonData")
                            // 继续处理下一行，不中断流
                        }
                    }
                }
            }
        }
    }

    private suspend fun FlowCollector<String>.handleWebSearchStreamingResponse(responseBody: ResponseBody) {
        // 处理阿里云百炼联网搜索的SSE流式响应
        responseBody.source().use { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line()
                if (line != null) {
                    Log.d(TAG, "Web Search SSE Line: $line")
                    
                    // 处理SSE格式的数据行
                    if (line.startsWith("data:")) {
                        val jsonData = line.substring(5).trim() // 移除 "data:" 前缀
                        
                        // 跳过空数据行和结束标记
                        if (jsonData.isEmpty() || jsonData == "[DONE]") {
                            continue
                        }
                        
                        try {
                            val jsonObject = JSONObject(jsonData)
                            val output = jsonObject.optJSONObject("output")
                            
                            if (output != null) {
                                val choices = output.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val message = choice.optJSONObject("message")
                                    
                                    if (message != null) {
                                        val content = message.optString("content", "")
                                        if (content.isNotEmpty()) {
                                            emit(content)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing web search SSE data: ${e.message}")
                            Log.e(TAG, "Web search SSE data content: $jsonData")
                            // 继续处理下一行，不中断流
                        }
                    }
                }
            }
        }
    }

    private suspend fun FlowCollector<String>.handleOpenAICompatibleNonStreamingResponse(responseBody: ResponseBody) {
        // 处理OpenAI兼容的非流式响应
        val responseString = responseBody.string()
        Log.d(TAG, "Response: $responseString")
        
        try {
            val jsonObject = JSONObject(responseString)
            val choices = jsonObject.optJSONArray("choices")
            
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val message = choice.optJSONObject("message")
                
                if (message != null) {
                    val content = message.optString("content", "")
                    if (content.isNotEmpty()) {
                        emit(content)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response: ${e.message}")
            Log.e(TAG, "Response content: $responseString")
            emit("Error: 响应解析失败 - ${e.message}")
        }
    }

    private suspend fun FlowCollector<String>.handleWebSearchNonStreamingResponse(responseBody: ResponseBody) {
        // 处理阿里云百炼联网搜索的非流式响应
        val responseString = responseBody.string()
        Log.d(TAG, "Web Search Response: $responseString")
        
        try {
            val jsonObject = JSONObject(responseString)
            val output = jsonObject.optJSONObject("output")
            
            if (output != null) {
                val choices = output.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    val message = choice.optJSONObject("message")
                    
                    if (message != null) {
                        val content = message.optString("content", "")
                        if (content.isNotEmpty()) {
                            // 处理搜索结果和引用
                            val searchInfo = output.optJSONObject("search_info")
                            if (searchInfo != null) {
                                val searchResults = searchInfo.optJSONArray("search_results")
                                if (searchResults != null && searchResults.length() > 0) {
                                    // 在内容后添加搜索来源信息
                                    val sourcesText = buildSearchSourcesText(searchResults)
                                    emit("$content\n\n$sourcesText")
                                } else {
                                    emit(content)
                                }
                            } else {
                                emit(content)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing web search response: ${e.message}")
            Log.e(TAG, "Web search response content: $responseString")
            emit("Error: 联网搜索响应解析失败 - ${e.message}")
        }
    }

    private fun buildSearchSourcesText(searchResults: JSONArray): String {
        val sources = StringBuilder()
        sources.append("**搜索来源：**\n")
        
        for (i in 0 until searchResults.length()) {
            val result = searchResults.getJSONObject(i)
            val title = result.optString("title", "")
            val url = result.optString("url", "")
            val siteName = result.optString("site_name", "")
            val index = result.optInt("index", i + 1)
            
            if (title.isNotEmpty() && url.isNotEmpty()) {
                sources.append("[$index] $title")
                if (siteName.isNotEmpty()) {
                    sources.append(" - $siteName")
                }
                sources.append("\n$url\n\n")
            }
        }
        
        return sources.toString()
    }

    private fun buildWebSearchRequestBody(
        model: String,
        messages: List<ChatMessage>
    ): String {
        val requestJson = JSONObject()
        requestJson.put("model", model)
        
        // 构建input对象
        val inputObject = JSONObject()
        val messagesArray = JSONArray()
        
        messages.forEach { message ->
            val messageObject = JSONObject()
            val role = if (message.isFromUser) "user" else "assistant"
            messageObject.put("role", role)
            
            // 检查是否包含图片
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
                
                messageObject.put("content", contentArray)
            } else {
                // 普通文本消息
                messageObject.put("content", message.content)
            }
            
            messagesArray.put(messageObject)
        }
        
        inputObject.put("messages", messagesArray)
        requestJson.put("input", inputObject)
        
        // 构建parameters对象
        val parametersObject = JSONObject()
        parametersObject.put("enable_search", true)
        
        // 搜索选项
        val searchOptionsObject = JSONObject()
        searchOptionsObject.put("enable_source", true)
        searchOptionsObject.put("enable_citation", true)
        searchOptionsObject.put("citation_format", "[ref_<number>]")
        parametersObject.put("search_options", searchOptionsObject)
        
        parametersObject.put("result_format", "message")
        
        // 添加全局配置参数
        val globalConfig = getGlobalConfig()
        globalConfig.keys().forEach { key ->
            when (key) {
                "temperature" -> parametersObject.put("temperature", globalConfig.get(key))
                "max_tokens" -> parametersObject.put("max_tokens", globalConfig.get(key))
            }
        }
        
        // 始终添加incremental_output参数，因为现在总是使用流式输出
        parametersObject.put("incremental_output", true)
        
        requestJson.put("parameters", parametersObject)
        
        return requestJson.toString()
    }
}