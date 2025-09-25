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

    // 定义一个 “payload” (有效载荷)，用来标识这只是一次内容更新
    companion object {
        private const val PAYLOAD_CONTENT_UPDATE = "PAYLOAD_CONTENT_UPDATE"
    }

    private fun getMarkwon(context: Context): Markwon {
        if (markwon == null) {
            markwon = Markwon.builder(context)
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(JLatexMathPlugin.create(42f) { builder ->
                    builder.inlinesEnabled(true)
                })
                .usePlugin(HtmlPlugin.create())
                .usePlugin(ImagesPlugin.create())
                .build()
        }
        return markwon!!
    }

    private fun preprocessLatex(content: String): String {
        var result = content
        val doubleDollarPlaceholder = "DOUBLE_DOLLAR_PLACEHOLDER"
        val doubleDollarPattern = Regex("""\$\$([^$]*?)\$\$""")
        val doubleDollarMatches = mutableListOf<String>()
        result = doubleDollarPattern.replace(result) { matchResult ->
            doubleDollarMatches.add(matchResult.value)
            "$doubleDollarPlaceholder${doubleDollarMatches.size - 1}"
        }
        val singleDollarPattern = Regex("""(?<!\\)\$(?!\$)([^$\n]*?)(?<!\\)\$(?!\$)""")
        result = singleDollarPattern.replace(result) { matchResult ->
            val mathContent = matchResult.groupValues[1].trim()
            if (mathContent.isNotEmpty()) {
                "$$${mathContent}$$"
            } else {
                matchResult.value
            }
        }
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
        val imageAiMessage: ImageView = itemView.findViewById(R.id.imageAiMessage)
        val buttonCopyMessage: ImageButton = itemView.findViewById(R.id.buttonCopyMessage)
        val buttonCopyUserMessage: ImageButton = itemView.findViewById(R.id.buttonCopyUserMessage)
        val buttonDetailMessage: ImageButton = itemView.findViewById(R.id.buttonDetailMessage)
        val buttonDetailUserMessage: ImageButton = itemView.findViewById(R.id.buttonDetailUserMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]

        if (message.isFromUser) {
            holder.layoutUserMessage.visibility = View.VISIBLE
            holder.layoutAiMessage.visibility = View.GONE
            if (message.content.isNotEmpty()) {
                holder.textUserMessage.text = message.content
                holder.textUserMessage.visibility = View.VISIBLE
            } else {
                holder.textUserMessage.visibility = View.GONE
            }
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
            holder.buttonCopyUserMessage.setOnClickListener {
                copyToClipboard(holder.itemView.context, message.content)
            }
            holder.buttonDetailUserMessage.setOnClickListener {
                val intent = ChatDetailActivity.createIntent(holder.itemView.context, message)
                holder.itemView.context.startActivity(intent)
            }
        } else {
            holder.layoutUserMessage.visibility = View.GONE
            holder.layoutAiMessage.visibility = View.VISIBLE
            bindAiMessageContent(holder, message)
            holder.buttonCopyMessage.setOnClickListener {
                copyToClipboard(holder.itemView.context, message.content)
            }
            holder.buttonDetailMessage.setOnClickListener {
                val intent = ChatDetailActivity.createIntent(holder.itemView.context, message)
                holder.itemView.context.startActivity(intent)
            }
        }
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_CONTENT_UPDATE)) {
            val message = messages[position]
            if (!message.isFromUser) {
                bindAiMessageContent(holder, message)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun updateLastMessage(message: ChatMessage) {
        if (messages.isNotEmpty()) {
            messages[messages.size - 1] = message
            notifyItemChanged(messages.size - 1, PAYLOAD_CONTENT_UPDATE)
        }
    }

    private fun bindAiMessageContent(holder: ChatViewHolder, message: ChatMessage) {
        // 处理文本内容
        if (message.content.isNotEmpty()) {
            val processedContent = preprocessLatex(message.content)
            val markwon = getMarkwon(holder.itemView.context)
            markwon.setMarkdown(holder.textAiMessage, processedContent)
            holder.textAiMessage.visibility = View.VISIBLE
        } else {
            holder.textAiMessage.visibility = View.GONE
        }
        
        // 处理图片内容
        if (!message.imageUri.isNullOrEmpty()) {
            try {
                if (message.imageUri.startsWith("data:", ignoreCase = true)) {
                    // Base64编码的图片
                    val bitmap = decodeBase64ToBitmap(message.imageUri)
                    holder.imageAiMessage.setImageBitmap(bitmap)
                    holder.imageAiMessage.visibility = View.VISIBLE
                } else if (message.imageUri.startsWith("http", ignoreCase = true)) {
                    // 网络图片URL - 使用Glide加载
                    com.bumptech.glide.Glide.with(holder.itemView.context)
                        .load(message.imageUri)
                        .into(holder.imageAiMessage)
                    holder.imageAiMessage.visibility = View.VISIBLE
                } else {
                    holder.imageAiMessage.visibility = View.GONE
                }
            } catch (e: Exception) {
                holder.imageAiMessage.visibility = View.GONE
            }
        } else {
            holder.imageAiMessage.visibility = View.GONE
        }
    }

    private fun decodeBase64ToBitmap(dataUrl: String): Bitmap {
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

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI回复", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}