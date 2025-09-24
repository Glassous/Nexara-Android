package com.glassous.gsrobot

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.glassous.gsrobot.data.ChatSession

class ChatSessionAdapter(
    private val context: Context,
    private val sessions: MutableList<ChatSession>,
    private val onSessionClick: (ChatSession) -> Unit,
    private val onSessionEdit: (ChatSession, String) -> Unit,
    private val onSessionDelete: (ChatSession) -> Unit
) : RecyclerView.Adapter<ChatSessionAdapter.SessionViewHolder>() {

    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textChatTitle: TextView = itemView.findViewById(R.id.textChatTitle)
        val buttonEditChat: ImageButton = itemView.findViewById(R.id.buttonEditChat)
        val buttonDeleteChat: ImageButton = itemView.findViewById(R.id.buttonDeleteChat)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        
        holder.textChatTitle.text = session.getDisplayTitle()
        
        // 点击对话项加载对话
        holder.itemView.setOnClickListener {
            onSessionClick(session)
        }
        
        // 编辑按钮点击事件
        holder.buttonEditChat.setOnClickListener {
            showEditDialog(session)
        }
        
        // 删除按钮点击事件
        holder.buttonDeleteChat.setOnClickListener {
            showDeleteDialog(session)
        }
    }

    override fun getItemCount(): Int = sessions.size

    private fun showEditDialog(session: ChatSession) {
        val editText = EditText(context).apply {
            setText(session.title)
            selectAll()
        }
        
        AlertDialog.Builder(context)
            .setTitle("编辑对话标题")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    onSessionEdit(session, newTitle)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteDialog(session: ChatSession) {
        AlertDialog.Builder(context)
            .setTitle("删除对话")
            .setMessage("确定要删除这个对话吗？此操作无法撤销。")
            .setPositiveButton("删除") { _, _ ->
                onSessionDelete(session)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun updateSessions(newSessions: List<ChatSession>) {
        sessions.clear()
        sessions.addAll(newSessions)
        notifyDataSetChanged()
    }

    fun removeSession(session: ChatSession) {
        val position = sessions.indexOf(session)
        if (position != -1) {
            sessions.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun updateSession(session: ChatSession) {
        val position = sessions.indexOfFirst { it.id == session.id }
        if (position != -1) {
            sessions[position] = session
            notifyItemChanged(position)
        }
    }
}