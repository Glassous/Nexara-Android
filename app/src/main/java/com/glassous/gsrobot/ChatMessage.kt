package com.glassous.gsrobot

data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String? = null // 图片的Base64数据URL
)