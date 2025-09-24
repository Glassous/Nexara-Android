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

class AnthropicAIClient(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "AnthropicAIClient"
        private const val BASE_URL = "https://api.anthropic.com/v1/messages"
        private const val PREFS_NAME = "anthropic_ai_model_config"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_TEMPERATURE_ENABLED = "temperature_enabled"
        private const val KEY_TEMPERATURE_VALUE = "temperature_value"
        private const val KEY_MAX_TOKENS_ENABLED = "max_tokens_enabled"
        private const val KEY_MAX_TOKENS_VALUE = "max_tokens_value"
    }

    private fun getApiKey(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, null)
    }

    private fun getGlobalConfig(): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val config = JSONObject()
        
        if (prefs.getBoolean(KEY_TEMPERATURE_ENABLED, false)) {
            // Temperature is saved as percentage (0-100), convert to 0.0-1.0 range
            val temperatureValue = prefs.getFloat(KEY_TEMPERATURE_VALUE, 50f)
            config.put("temperature", temperatureValue / 100.0)
        }
        
        if (prefs.getBoolean(KEY_MAX_TOKENS_ENABLED, false)) {
            // Max tokens is saved as percentage (0-100), convert to actual token count
            val maxTokensValue = prefs.getFloat(KEY_MAX_TOKENS_VALUE, 100f)
            val maxTokens = (maxTokensValue / 100.0 * 4096).toInt().coerceAtLeast(1)
            config.put("max_tokens", maxTokens)
        } else {
            // Anthropic API requires max_tokens parameter
            config.put("max_tokens", 4096)
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
            emit("Error: Anthropic API key not configured")
            return@flow
        }

        try {
            val requestBody = buildRequestBody(model, messages, streaming, enableWebSearch)
            
            Log.d(TAG, "Sending request to: $BASE_URL")

            val request = Request.Builder()
                .url(BASE_URL)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
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
                    // Handle Anthropic SSE streaming response
                    val source = responseBody.source()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()
                        if (line != null) {
                            when {
                                line.startsWith("event: ") -> {
                                    // Event type line, we'll process the data line that follows
                                    continue
                                }
                                line.startsWith("data: ") -> {
                                    val data = line.substring(6).trim()
                                    if (data.isEmpty()) continue
                                    
                                    try {
                                        val jsonObject = JSONObject(data)
                                        val eventType = jsonObject.optString("type", "")
                                        
                                        when (eventType) {
                                            "content_block_delta" -> {
                                                val delta = jsonObject.optJSONObject("delta")
                                                if (delta != null) {
                                                    val deltaType = delta.optString("type", "")
                                                    when (deltaType) {
                                                        "text_delta" -> {
                                                            val text = delta.optString("text", "")
                                                            if (text.isNotEmpty()) {
                                                                emit(text)
                                                            }
                                                        }
                                                        "thinking_delta" -> {
                                                            // Handle thinking content if needed
                                                            val thinking = delta.optString("thinking", "")
                                                            if (thinking.isNotEmpty()) {
                                                                // For now, we'll emit thinking content as well
                                                                emit(thinking)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            "message_stop" -> {
                                                // End of stream
                                                break
                                            }
                                            "error" -> {
                                                val error = jsonObject.optJSONObject("error")
                                                val errorMessage = error?.optString("message", "Unknown error") ?: "Unknown error"
                                                Log.e(TAG, "Streaming error: $errorMessage")
                                                emit("Error: $errorMessage")
                                                break
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error parsing streaming response: ${e.message}")
                                        Log.e(TAG, "Problematic data: $data")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Handle non-streaming response
                    val responseString = responseBody.string()
                    Log.d(TAG, "Response: $responseString")
                    
                    try {
                        val jsonObject = JSONObject(responseString)
                        val content = jsonObject.optJSONArray("content")
                        
                        if (content != null && content.length() > 0) {
                            val contentBlock = content.getJSONObject(0)
                            val text = contentBlock.optString("text", "")
                            if (text.isNotEmpty()) {
                                emit(text)
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
        model: String,
        messages: List<ChatMessage>,
        streaming: Boolean,
        enableWebSearch: Boolean
    ): String {
        val requestJson = JSONObject()
        val globalConfig = getGlobalConfig()
        
        requestJson.put("model", model)
        requestJson.put("stream", streaming)
        
        // Add global configuration
        globalConfig.keys().forEach { key ->
            requestJson.put(key, globalConfig.get(key))
        }
        
        val messagesArray = JSONArray()
        
        // Convert messages to Anthropic format
        messages.forEach { message ->
            val messageObject = JSONObject()
            val role = if (message.isFromUser) "user" else "assistant"
            messageObject.put("role", role)
            
            val contentArray = JSONArray()
            
            // Add text content
            if (message.content.isNotEmpty()) {
                val textContent = JSONObject()
                textContent.put("type", "text")
                textContent.put("text", message.content)
                contentArray.put(textContent)
            }
            
            // Add image content if present
            if (message.imageUri != null && message.isFromUser) {
                val imageContent = JSONObject()
                imageContent.put("type", "image")
                val source = JSONObject()
                source.put("type", "base64")
                source.put("media_type", "image/jpeg")
                source.put("data", message.imageUri)
                imageContent.put("source", source)
                contentArray.put(imageContent)
            }
            
            messageObject.put("content", contentArray)
            messagesArray.put(messageObject)
        }
        
        requestJson.put("messages", messagesArray)
        
        // Note: Anthropic doesn't have built-in web search like Google AI
        // Web search functionality would need to be implemented separately
        if (enableWebSearch) {
            Log.d(TAG, "Web search requested but not natively supported by Anthropic API")
        }
        
        return requestJson.toString()
    }
}