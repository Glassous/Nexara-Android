package com.glassous.gsrobot.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatSession(
    val id: Long,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
) : Parcelable {
    
    fun getDisplayTitle(): String {
        return if (title.isBlank()) "新对话" else title
    }
}