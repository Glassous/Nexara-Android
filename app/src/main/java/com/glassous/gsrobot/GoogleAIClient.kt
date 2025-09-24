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
            val requestBody = buildRequestBody(messages, false, enableWebSearch)
            val url = "$BASE_URL/$model:streamGenerateContent?key=$apiKey"
            
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
                val responseString = responseBody.string()
                Log.d(TAG, "Response: $responseString")
                
                try {
                    // 首先尝试解析为JSONArray（流式响应格式）
                    if (responseString.trim().startsWith("[")) {
                        val jsonArray = JSONArray(responseString)
                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
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
                        }
                    } else {
                        // 尝试解析为单个JSONObject（非流式响应格式）
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
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing response: ${e.message}")
                    Log.e(TAG, "Response content: $responseString")
                    emit("Error: Response parsing failed - ${e.message}")
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
                inlineData.put("mimeType", "image/jpeg")
                inlineData.put("data", message.imageUri)
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
            googleSearchTool.put("googleSearchRetrieval", JSONObject())
            toolsArray.put(googleSearchTool)
            requestJson.put("tools", toolsArray)
        }
        
        return requestJson.toString()
    }
}