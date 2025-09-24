package com.glassous.gsrobot.data

import android.content.Context
import com.glassous.gsrobot.ChatMessage

class ChatSessionManager(context: Context) {
    private val database = ChatDatabase(context)
    private var currentSessionId: Long? = null
    
    // 开始新的对话会话
    fun startNewSession(title: String = "新对话"): Long {
        currentSessionId = database.saveSession(title)
        return currentSessionId!!
    }
    
    // 设置当前会话
    fun setCurrentSession(sessionId: Long) {
        currentSessionId = sessionId
    }
    
    // 获取当前会话ID
    fun getCurrentSessionId(): Long? = currentSessionId
    
    // 保存消息到当前会话
    fun saveMessage(message: ChatMessage) {
        currentSessionId?.let { sessionId ->
            database.saveMessage(sessionId, message)
        }
    }
    
    // 获取所有会话
    fun getAllSessions(): List<ChatSession> {
        return database.getAllSessions()
    }
    
    // 获取指定会话的消息
    fun getMessagesForSession(sessionId: Long): List<ChatMessage> {
        return database.getMessagesForSession(sessionId)
    }
    
    // 删除会话
    fun deleteSession(sessionId: Long) {
        database.deleteSession(sessionId)
        if (currentSessionId == sessionId) {
            currentSessionId = null
        }
    }
    
    // 加载会话到当前对话
    fun loadSession(sessionId: Long): List<ChatMessage> {
        currentSessionId = sessionId
        return database.getMessagesForSession(sessionId)
    }
    
    // 检查是否有当前会话
    fun hasCurrentSession(): Boolean = currentSessionId != null
    
    // 清除当前会话
    fun clearCurrentSession() {
        currentSessionId = null
    }
    
    // 清理空会话
    fun cleanupEmptySessions() {
        database.cleanupEmptySessions()
    }
    
    // 更新会话标题
    fun updateSessionTitle(sessionId: Long, newTitle: String) {
        database.updateSessionTitle(sessionId, newTitle)
    }
}