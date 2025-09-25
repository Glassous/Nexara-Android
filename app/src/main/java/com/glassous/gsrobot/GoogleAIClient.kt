package com.glassous.gsrobot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GoogleAIClient(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GoogleAIClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val PREFS_NAME = "google_ai_model_config"
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
            config.put("maxOutputTokens", maxTokens)
        }
        
        return config
    }

    fun sendChatRequest(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String,
        streaming: Boolean = true,
        enableWebSearch: Boolean = false
    ): Flow<String> = flow {
        if (apiKey.isNullOrEmpty()) {
            emit("Error: Google AI API key not configured")
            return@flow
        }

        try {
            // 检测消息中是否包含图片
            val hasImages = messages.any { it.imageUri != null }
            val requestBody = buildRequestBody(messages, hasImages, enableWebSearch)
            
            // 根据streaming参数选择不同的端点和URL格式
            val url = if (streaming) {
                "$BASE_URL/$model:streamGenerateContent?alt=sse&key=$apiKey"
            } else {
                "$BASE_URL/$model:generateContent?key=$apiKey"
            }
            
            Log.d(TAG, "Sending request to: $url")

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "API request failed: ${response.code} - $errorBody")
                emit("Error: API request failed (${response.code}): $errorBody")
                return@flow
            }

            response.body?.let { responseBody ->
                if (streaming) {
                    // 处理SSE流式响应
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
                                        val candidates = jsonObject.optJSONArray("candidates")
                                        
                                        if (candidates != null && candidates.length() > 0) {
                                            val candidate = candidates.getJSONObject(0)
                                            val content = candidate.optJSONObject("content")
                                            val parts = content?.optJSONArray("parts")
                                            
                                            if (parts != null && parts.length() > 0) {
                                                val part = parts.getJSONObject(0)
                                                val text = part.optString("text", "")
                                                if (text.isNotEmpty()) {
                                                    emit(text)
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
                } else {
                    // 处理非流式响应
                    val responseString = responseBody.string()
                    Log.d(TAG, "Response: $responseString")
                    
                    try {
                        val jsonObject = JSONObject(responseString)
                        val candidates = jsonObject.optJSONArray("candidates")
                        
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            
                            if (parts != null && parts.length() > 0) {
                                val part = parts.getJSONObject(0)
                                val text = part.optString("text", "")
                                if (text.isNotEmpty()) {
                                    emit(text)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing response: ${e.message}")
                        Log.e(TAG, "Response content: $responseString")
                        emit("Error: Response parsing failed - ${e.message}")
                    }
                }
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}")
            emit("Error: Network connection failed - ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}")
            emit("Error: Request processing failed - ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        enableImageUpload: Boolean,
        enableWebSearch: Boolean
    ): String {
        val requestJson = JSONObject()
        val globalConfig = getGlobalConfig()
        
        if (globalConfig.length() > 0) {
            val generationConfig = JSONObject()
            globalConfig.keys().forEach { key ->
                generationConfig.put(key, globalConfig.get(key))
            }
            requestJson.put("generationConfig", generationConfig)
        }
        
        val contentsArray = JSONArray()
        
        messages.forEach { message ->
            val contentObject = JSONObject()
            val role = if (message.isFromUser) "user" else "model"
            contentObject.put("role", role)
            
            val partsArray = JSONArray()
            
            if (message.content.isNotEmpty()) {
                val textPart = JSONObject()
                textPart.put("text", message.content)
                partsArray.put(textPart)
            }
            
            if (enableImageUpload && message.imageUri != null) {
                val imagePart = JSONObject()
                val inlineData = JSONObject()
                
                // 从data URL中提取base64数据和MIME类型
                val dataUrl = message.imageUri
                val mimeType = if (dataUrl.contains("data:image/")) {
                    dataUrl.substring(5, dataUrl.indexOf(";"))
                } else {
                    "image/jpeg"
                }
                
                val base64Data = if (dataUrl.contains("base64,")) {
                    dataUrl.substring(dataUrl.indexOf("base64,") + 7)
                } else {
                    dataUrl
                }
                
                inlineData.put("mimeType", mimeType)
                inlineData.put("data", base64Data)
                imagePart.put("inlineData", inlineData)
                partsArray.put(imagePart)
            }
            
            contentObject.put("parts", partsArray)
            contentsArray.put(contentObject)
        }
        
        requestJson.put("contents", contentsArray)
        
        if (enableWebSearch) {
            val toolsArray = JSONArray()
            val googleSearchTool = JSONObject()
            googleSearchTool.put("google_search", JSONObject())
            toolsArray.put(googleSearchTool)
            requestJson.put("tools", toolsArray)
        }
        
        return requestJson.toString()
    }
}