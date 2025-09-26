package com.glassous.gsrobot

data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String? = null, // 图片的Base64数据URL或网络URL
    val localImagePath: String? = null, // 本地保存的图片文件路径
    val isLoading: Boolean = false // 是否显示Loading indicator
)