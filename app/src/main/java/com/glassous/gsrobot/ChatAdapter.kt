package com.glassous.gsrobot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

class ChatAdapter(private val messages: MutableList<ChatMessage>) : 
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    
    private var markwon: Markwon? = null
    
    private fun getMarkwon(context: Context): Markwon {
        if (markwon == null) {
            markwon = Markwon.builder(context)
                .usePlugin(MarkwonInlineParserPlugin.create()) // 内联解析器支持
                .usePlugin(TablePlugin.create(context)) // 表格支持
                .usePlugin(JLatexMathPlugin.create(42f) { builder ->
                    builder.inlinesEnabled(true) // 启用单$符号的内联LaTeX
                }) // LaTeX数学公式支持
                .usePlugin(HtmlPlugin.create()) // HTML支持
                .usePlugin(ImagesPlugin.create()) // 图片支持
                .build()
        }
        return markwon!!
    }
    
    /**
     * 预处理LaTeX公式，将单$转换为双$$以确保正确渲染
     * 避免只有一侧成功适配的问题
     */
    private fun preprocessLatex(content: String): String {
        var result = content
        
        // 首先保护已经存在的双$$符号，避免重复处理
        val doubleDollarPlaceholder = "DOUBLE_DOLLAR_PLACEHOLDER"
        val doubleDollarPattern = Regex("""\$\$([^$]*?)\$\$""")
        val doubleDollarMatches = mutableListOf<String>()
        
        // 保存双$$内容并用占位符替换
        result = doubleDollarPattern.replace(result) { matchResult ->
            doubleDollarMatches.add(matchResult.value)
            "$doubleDollarPlaceholder${doubleDollarMatches.size - 1}"
        }
        
        // 处理单$符号，确保成对匹配
        // 这个正则表达式匹配非转义的单$符号对
        val singleDollarPattern = Regex("""(?<!\\)\$(?!\$)([^$\n]*?)(?<!\\)\$(?!\$)""")
        
        result = singleDollarPattern.replace(result) { matchResult ->
            val mathContent = matchResult.groupValues[1].trim()
            // 只有当内容不为空时才转换为双$$
            if (mathContent.isNotEmpty()) {
                "$$${mathContent}$$"
            } else {
                matchResult.value // 保持原样
            }
        }
        
        // 恢复双$$内容
        doubleDollarMatches.forEachIndexed { index, originalMatch ->
            result = result.replace("$doubleDollarPlaceholder$index", originalMatch)
        }
        
        return result
    }

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutUserMessage: LinearLayout = itemView.findViewById(R.id.layoutUserMessage)
        val layoutAiMessage: LinearLayout = itemView.findViewById(R.id.layoutAiMessage)
        val textUserMessage: TextView = itemView.findViewById(R.id.textUserMessage)
        val textAiMessage: TextView = itemView.findViewById(R.id.textAiMessage)
        val imageUserMessage: ImageView = itemView.findViewById(R.id.imageUserMessage)
        val buttonCopyMessage: ImageButton = itemView.findViewById(R.id.buttonCopyMessage)
        val buttonCopyUserMessage: ImageButton = itemView.findViewById(R.id.buttonCopyUserMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        
        if (message.isFromUser) {
            // 显示用户消息
            holder.layoutUserMessage.visibility = View.VISIBLE
            holder.layoutAiMessage.visibility = View.GONE
            
            // 处理文本消息
            if (message.content.isNotEmpty()) {
                holder.textUserMessage.text = message.content
                holder.textUserMessage.visibility = View.VISIBLE
            } else {
                holder.textUserMessage.visibility = View.GONE
            }
            
            // 处理图片消息
            if (!message.imageUri.isNullOrEmpty()) {
                try {
                    val bitmap = decodeBase64ToBitmap(message.imageUri)
                    holder.imageUserMessage.setImageBitmap(bitmap)
                    holder.imageUserMessage.visibility = View.VISIBLE
                } catch (e: Exception) {
                    holder.imageUserMessage.visibility = View.GONE
                }
            } else {
                holder.imageUserMessage.visibility = View.GONE
            }
            
            // 设置用户消息复制按钮点击事件
            holder.buttonCopyUserMessage.setOnClickListener {
                copyToClipboard(holder.itemView.context, message.content)
            }
        } else {
            // 显示AI消息
            holder.layoutUserMessage.visibility = View.GONE
            holder.layoutAiMessage.visibility = View.VISIBLE
            
            // 预处理LaTeX公式，将单$转换为双$$
            val processedContent = preprocessLatex(message.content)
            
            // 使用Markwon渲染Markdown格式的AI消息
            val markwon = getMarkwon(holder.itemView.context)
            markwon.setMarkdown(holder.textAiMessage, processedContent)
            
            // 设置复制按钮点击事件
            holder.buttonCopyMessage.setOnClickListener {
                copyToClipboard(holder.itemView.context, message.content)
            }
        }
    }
    
    private fun decodeBase64ToBitmap(dataUrl: String): Bitmap {
        // 移除data URL前缀 (data:image/jpeg;base64,)
        val base64String = dataUrl.substringAfter("base64,")
        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateMessage(position: Int, newContent: String) {
        if (position >= 0 && position < messages.size) {
            messages[position] = messages[position].copy(content = newContent)
            notifyItemChanged(position)
        }
    }

    fun addMessages(newMessages: List<ChatMessage>) {
        val startPosition = messages.size
        messages.addAll(newMessages)
        notifyItemRangeInserted(startPosition, newMessages.size)
    }
    
    fun updateLastMessage(message: ChatMessage) {
        if (messages.isNotEmpty()) {
            messages[messages.size - 1] = message
            notifyItemChanged(messages.size - 1)
        }
    }
    
    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI回复", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}