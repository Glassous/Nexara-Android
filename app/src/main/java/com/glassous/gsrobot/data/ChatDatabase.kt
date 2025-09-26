package com.glassous.gsrobot.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.glassous.gsrobot.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    
    companion object {
        private const val DATABASE_NAME = "chat_database.db"
        private const val DATABASE_VERSION = 3
        
        // 对话会话表
        private const val TABLE_SESSIONS = "chat_sessions"
        private const val COLUMN_SESSION_ID = "id"
        private const val COLUMN_SESSION_TITLE = "title"
        private const val COLUMN_SESSION_CREATED_AT = "created_at"
        private const val COLUMN_SESSION_UPDATED_AT = "updated_at"
        
        // 消息表
        private const val TABLE_MESSAGES = "chat_messages"
        private const val COLUMN_MESSAGE_ID = "id"
        private const val COLUMN_MESSAGE_SESSION_ID = "session_id"
        private const val COLUMN_MESSAGE_CONTENT = "content"
        private const val COLUMN_MESSAGE_IS_FROM_USER = "is_from_user"
        private const val COLUMN_MESSAGE_TIMESTAMP = "timestamp"
        private const val COLUMN_MESSAGE_IMAGE_URI = "image_uri"
        private const val COLUMN_MESSAGE_LOCAL_IMAGE_PATH = "local_image_path"
        
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
    
    override fun onCreate(db: SQLiteDatabase) {
        // 创建会话表
        val createSessionsTable = """
            CREATE TABLE $TABLE_SESSIONS (
                $COLUMN_SESSION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SESSION_TITLE TEXT NOT NULL,
                $COLUMN_SESSION_CREATED_AT TEXT NOT NULL,
                $COLUMN_SESSION_UPDATED_AT TEXT NOT NULL
            )
        """.trimIndent()
        
        // 创建消息表
        val createMessagesTable = """
            CREATE TABLE $TABLE_MESSAGES (
                $COLUMN_MESSAGE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_MESSAGE_SESSION_ID INTEGER NOT NULL,
                $COLUMN_MESSAGE_CONTENT TEXT NOT NULL,
                $COLUMN_MESSAGE_IS_FROM_USER INTEGER NOT NULL,
                $COLUMN_MESSAGE_TIMESTAMP TEXT NOT NULL,
                $COLUMN_MESSAGE_IMAGE_URI TEXT,
                $COLUMN_MESSAGE_LOCAL_IMAGE_PATH TEXT,
                FOREIGN KEY($COLUMN_MESSAGE_SESSION_ID) REFERENCES $TABLE_SESSIONS($COLUMN_SESSION_ID) ON DELETE CASCADE
            )
        """.trimIndent()
        
        db.execSQL(createSessionsTable)
        db.execSQL(createMessagesTable)
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // 添加image_uri字段到现有的消息表
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COLUMN_MESSAGE_IMAGE_URI TEXT")
        }
        if (oldVersion < 3) {
            // 添加local_image_path字段到现有的消息表
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COLUMN_MESSAGE_LOCAL_IMAGE_PATH TEXT")
        }
    }
    
    // 保存新的对话会话
    fun saveSession(title: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SESSION_TITLE, title)
            put(COLUMN_SESSION_CREATED_AT, dateFormat.format(Date()))
            put(COLUMN_SESSION_UPDATED_AT, dateFormat.format(Date()))
        }
        return db.insert(TABLE_SESSIONS, null, values)
    }
    
    // 更新会话的更新时间
    fun updateSessionTimestamp(sessionId: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SESSION_UPDATED_AT, dateFormat.format(Date()))
        }
        db.update(TABLE_SESSIONS, values, "$COLUMN_SESSION_ID = ?", arrayOf(sessionId.toString()))
    }
    
    // 保存消息
    fun saveMessage(sessionId: Long, message: ChatMessage) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_MESSAGE_SESSION_ID, sessionId)
            put(COLUMN_MESSAGE_CONTENT, message.content)
            put(COLUMN_MESSAGE_IS_FROM_USER, if (message.isFromUser) 1 else 0)
            put(COLUMN_MESSAGE_TIMESTAMP, dateFormat.format(Date()))
            put(COLUMN_MESSAGE_IMAGE_URI, message.imageUri)
            put(COLUMN_MESSAGE_LOCAL_IMAGE_PATH, message.localImagePath)
        }
        db.insert(TABLE_MESSAGES, null, values)
        
        // 更新会话时间戳
        updateSessionTimestamp(sessionId)
    }
    
    // 获取所有会话（按更新时间倒序）
    fun getAllSessions(): List<ChatSession> {
        val sessions = mutableListOf<ChatSession>()
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_SESSION_UPDATED_AT DESC"
        )
        
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(COLUMN_SESSION_ID))
                val title = it.getString(it.getColumnIndexOrThrow(COLUMN_SESSION_TITLE))
                val createdAtDate = dateFormat.parse(it.getString(it.getColumnIndexOrThrow(COLUMN_SESSION_CREATED_AT))) ?: Date()
                val updatedAtDate = dateFormat.parse(it.getString(it.getColumnIndexOrThrow(COLUMN_SESSION_UPDATED_AT))) ?: Date()
                
                val messages = getMessagesForSession(id)
                sessions.add(ChatSession(id, title, createdAtDate.time, updatedAtDate.time, messages.size))
            }
        }
        
        return sessions
    }
    
    // 获取指定会话的所有消息
    fun getMessagesForSession(sessionId: Long): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COLUMN_MESSAGE_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            "$COLUMN_MESSAGE_TIMESTAMP ASC"
        )
        
        cursor.use {
            while (it.moveToNext()) {
                val content = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE_CONTENT))
                val isFromUser = it.getInt(it.getColumnIndexOrThrow(COLUMN_MESSAGE_IS_FROM_USER)) == 1
                val imageUri = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE_IMAGE_URI))
                val localImagePath = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE_LOCAL_IMAGE_PATH))
                messages.add(ChatMessage(content, isFromUser, imageUri = imageUri, localImagePath = localImagePath))
            }
        }
        
        return messages
    }
    
    // 删除会话及其所有消息
    fun deleteSession(sessionId: Long) {
        val db = writableDatabase
        db.delete(TABLE_SESSIONS, "$COLUMN_SESSION_ID = ?", arrayOf(sessionId.toString()))
        // 消息会因为外键约束自动删除
    }
    
    // 清理空会话（没有消息的会话）
    fun cleanupEmptySessions() {
        val db = writableDatabase
        
        // 查找没有消息的会话
        val query = """
            SELECT s.$COLUMN_SESSION_ID 
            FROM $TABLE_SESSIONS s 
            LEFT JOIN $TABLE_MESSAGES m ON s.$COLUMN_SESSION_ID = m.$COLUMN_MESSAGE_SESSION_ID 
            WHERE m.$COLUMN_MESSAGE_ID IS NULL
        """.trimIndent()
        
        val cursor = db.rawQuery(query, null)
        val emptySessionIds = mutableListOf<Long>()
        
        cursor.use {
            while (it.moveToNext()) {
                emptySessionIds.add(it.getLong(0))
            }
        }
        
        // 删除空会话
        emptySessionIds.forEach { sessionId ->
            deleteSession(sessionId)
        }
    }
    
    // 更新会话标题
    fun updateSessionTitle(sessionId: Long, newTitle: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SESSION_TITLE, newTitle)
            put(COLUMN_SESSION_UPDATED_AT, dateFormat.format(Date()))
        }
        db.update(TABLE_SESSIONS, values, "$COLUMN_SESSION_ID = ?", arrayOf(sessionId.toString()))
    }
}