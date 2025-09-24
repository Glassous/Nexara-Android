package com.glassous.gsrobot

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.glassous.gsrobot.data.ChatSession
import com.glassous.gsrobot.data.ChatSessionManager
import com.glassous.gsrobot.data.ConfigManager
import com.glassous.gsrobot.data.GroupConfig
import com.glassous.gsrobot.data.ModelConfig
import com.glassous.gsrobot.utils.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.glassous.gsrobot.GoogleAIClient

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var editTextMessage: TextInputEditText
    private lateinit var fabSend: FloatingActionButton
    private lateinit var toolbar: MaterialToolbar
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationDrawer: View
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var sessionManager: ChatSessionManager
    private lateinit var textViewWelcome: TextView
    private lateinit var recyclerViewChatSessions: RecyclerView
    private lateinit var chatSessionAdapter: ChatSessionAdapter
    private lateinit var textEmptyState: TextView
    private lateinit var layoutNewChat: View
    private lateinit var modelConfigPrefs: SharedPreferences
    
    // 联网搜索相关组件
    private lateinit var chipWebSearch: com.google.android.material.chip.Chip
    private var isWebSearchEnabled: Boolean = false
    
    // 文件上传相关
    private lateinit var textInputLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var layoutImagePreview: android.widget.LinearLayout
    private lateinit var imagePreview: android.widget.ImageView
    private lateinit var btnRemoveImage: com.google.android.material.button.MaterialButton
    private var selectedImageUri: android.net.Uri? = null
    
    // 文件选择器
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showImagePreview(it)
            Toast.makeText(this, "已选择图片", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 新的配置管理
    private var currentGroups: List<GroupConfig> = emptyList()
    private var selectedGroup: GroupConfig? = null
    private var selectedModel: ModelConfig? = null
    
    private val messages = mutableListOf<ChatMessage>()
    private val handler = Handler(Looper.getMainLooper())
    private var openAIClient: OpenAIClient? = null
    private var googleAIClient: GoogleAIClient? = null
    private var anthropicAIClient: AnthropicAIClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    
    // AI回复列表
    private val aiResponses = listOf(
        "你好！很高兴为您服务！",
        "这是一个很有趣的问题，让我想想...",
        "根据我的理解，我认为...",
        "感谢您的提问！这个话题很值得探讨。",
        "我明白您的意思，让我为您详细解释一下。",
        "这确实是一个复杂的问题，需要从多个角度来看。",
        "很好的观点！我完全同意您的看法。",
        "让我为您提供一些相关的信息和建议。"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 应用主题设置
        ThemeManager.getInstance(this).initializeTheme()
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // 初始化会话管理器
        sessionManager = ChatSessionManager(this)
        
        // 初始化模型配置SharedPreferences（保持兼容性）
        modelConfigPrefs = getSharedPreferences("model_config", Context.MODE_PRIVATE)
        
        // 加载新的配置系统
        loadModelConfiguration()
        
        // 清理空会话
        sessionManager.cleanupEmptySessions()
        
        initViews()
        setupRecyclerView()
        setupClickListeners()
        setupNavigationDrawer()
        setupWindowInsets()
        
        // 开始新会话或加载现有会话
        initializeSession()
        
        // 初始化OpenAI客户端
        initializeAIClients()
    }
    
    private fun initViews() {
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        editTextMessage = findViewById(R.id.editTextMessage)
        fabSend = findViewById(R.id.fabSend)
        toolbar = findViewById(R.id.toolbar)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationDrawer = findViewById(R.id.navigationDrawer)
        textViewWelcome = findViewById(R.id.textViewWelcome)
        
        // 初始化联网搜索Chip
        chipWebSearch = findViewById(R.id.chipWebSearch)
        
        // 初始化输入框布局
        textInputLayout = findViewById(R.id.textInputLayout)
        
        // 初始化图片预览相关视图
        layoutImagePreview = findViewById(R.id.layoutImagePreview)
        imagePreview = findViewById(R.id.imagePreview)
        btnRemoveImage = findViewById(R.id.btnRemoveImage)
        
        // 初始化导航抽屉内的组件
        recyclerViewChatSessions = navigationDrawer.findViewById(R.id.recyclerViewChatSessions)
        textEmptyState = navigationDrawer.findViewById(R.id.textEmptyState)
        layoutNewChat = navigationDrawer.findViewById(R.id.layoutNewChat)
        
        // 设置工具栏
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        
        // 更新工具栏标题显示当前模型
        updateToolbarTitle()
        
        // 初始化对话历史适配器
        setupChatSessionsRecyclerView()
    }
    
    private fun loadModelConfiguration() {
        val allGroups = mutableListOf<GroupConfig>()
        
        // 加载OpenAI模型配置
        val openaiConfigPrefs = getSharedPreferences("model_config", Context.MODE_PRIVATE)
        val openaiGroupsConfigJson = openaiConfigPrefs.getString("groups_config", "") ?: ""
        if (openaiGroupsConfigJson.isNotEmpty()) {
            allGroups.addAll(ConfigManager.jsonToGroupConfigList(openaiGroupsConfigJson))
            Log.d("MainActivity", "Loaded OpenAI groups from config system")
        }
        
        // 加载Google AI模型配置
        val googleAIConfigPrefs = getSharedPreferences("google_ai_model_config", Context.MODE_PRIVATE)
        val googleAIGroupsConfigJson = googleAIConfigPrefs.getString("groups_config", "") ?: ""
        if (googleAIGroupsConfigJson.isNotEmpty()) {
            allGroups.addAll(ConfigManager.jsonToGroupConfigList(googleAIGroupsConfigJson))
            Log.d("MainActivity", "Loaded Google AI groups from config system")
        }
        
        // 加载Anthropic AI模型配置
        val anthropicAIConfigPrefs = getSharedPreferences("anthropic_ai_model_config", Context.MODE_PRIVATE)
        val anthropicAIGroupsConfigJson = anthropicAIConfigPrefs.getString("groups_config", "") ?: ""
        if (anthropicAIGroupsConfigJson.isNotEmpty()) {
            allGroups.addAll(ConfigManager.jsonToGroupConfigList(anthropicAIGroupsConfigJson))
            Log.d("MainActivity", "Loaded Anthropic AI groups from config system")
        }
        
        currentGroups = allGroups
        Log.d("MainActivity", "Loaded ${currentGroups.size} total groups")
        
        if (currentGroups.isNotEmpty()) {
            // 自动选择第一个可用的分组和模型
            selectDefaultGroupAndModel()
        }
    }
    
    private fun selectDefaultGroupAndModel() {
        // 首先尝试从SharedPreferences加载用户选择的模型
        val selectedGroupId = modelConfigPrefs.getString("selected_group_id", "")
        val selectedModelId = modelConfigPrefs.getString("selected_model_id", "")
        
        if (!selectedGroupId.isNullOrEmpty() && !selectedModelId.isNullOrEmpty()) {
            // 查找用户选择的分组和模型
            currentGroups.forEach { group ->
                if (group.id == selectedGroupId) {
                    group.models.forEach { model ->
                        if (model.id == selectedModelId) {
                            selectedGroup = group
                            selectedModel = model
                            Log.d("MainActivity", "Loaded user selected group: ${group.name}, model: ${model.name}")
                            return
                        }
                    }
                }
            }
        }
        
        // 如果没有找到用户选择的模型，自动选择第一个可用的
        if (selectedGroup == null && currentGroups.isNotEmpty()) {
            selectedGroup = currentGroups.first()
            selectedModel = selectedGroup?.models?.firstOrNull()
            
            Log.d("MainActivity", "Auto selected group: ${selectedGroup?.name}, model: ${selectedModel?.name}")
        } else if (currentGroups.isEmpty()) {
            selectedGroup = null
            selectedModel = null
            Log.d("MainActivity", "No groups available")
        }
    }

    private fun initializeAIClients() {
        // 重置所有客户端
        openAIClient = null
        googleAIClient = null
        anthropicAIClient = null
        
        Log.d("MainActivity", "initializeAIClients: selectedGroup = ${selectedGroup?.name}, selectedModel = ${selectedModel?.name}")
        
        if (selectedGroup != null) {
            val groupName = selectedGroup!!.name.lowercase()
            val apiKey = selectedGroup!!.apiKey
            val baseUrl = selectedGroup!!.baseUrl
            
            Log.d("MainActivity", "Group details: name='${selectedGroup!!.name}', groupName='$groupName', baseUrl='$baseUrl', apiKey.length=${apiKey.length}")
            
            when {
                // 判断是否为Anthropic AI模型（优先检查）
                groupName.contains("anthropic") || groupName.contains("claude") -> {
                    // Anthropic AI模型
                    if (apiKey.isNotEmpty()) {
                        anthropicAIClient = AnthropicAIClient(this)
                        Log.d("MainActivity", "Anthropic AI client initialized with group: ${selectedGroup!!.name}")
                    } else {
                        Log.d("MainActivity", "Anthropic AI client not initialized - missing API key")
                    }
                }
                // 判断是否为Google AI模型（通过检查baseUrl是否为空来判断）
                selectedGroup!!.baseUrl.isEmpty() -> {
                    // Google AI模型
                    if (apiKey.isNotEmpty()) {
                        googleAIClient = GoogleAIClient(this)
                        Log.d("MainActivity", "Google AI client initialized with group: ${selectedGroup!!.name}")
                    } else {
                        Log.d("MainActivity", "Google AI client not initialized - missing API key")
                    }
                }
                else -> {
                    // OpenAI模型
                    val baseUrl = selectedGroup!!.baseUrl
                    
                    if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                        openAIClient = OpenAIClient(baseUrl, apiKey, this)
                        Log.d("MainActivity", "OpenAI client initialized with group: ${selectedGroup!!.name}, base URL: $baseUrl")
                    } else {
                        Log.d("MainActivity", "OpenAI client not initialized - missing group configuration")
                    }
                }
            }
        } else {
            // 回退到旧的配置系统（仅支持OpenAI）
            val baseUrl = modelConfigPrefs.getString("base_url", "") ?: ""
            val apiKey = modelConfigPrefs.getString("api_key", "") ?: ""
            
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                openAIClient = OpenAIClient(baseUrl, apiKey, this)
                Log.d("MainActivity", "OpenAI client initialized with legacy config, base URL: $baseUrl")
            } else {
                Log.d("MainActivity", "No client initialized - missing configuration")
            }
        }
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages)
        recyclerViewChat.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
        }
    }
    
    private fun setupChatSessionsRecyclerView() {
        chatSessionAdapter = ChatSessionAdapter(
            context = this,
            sessions = mutableListOf(),
            onSessionClick = { session ->
                loadChatSession(session.id)
                drawerLayout.closeDrawer(GravityCompat.START)
            },
            onSessionEdit = { session, newTitle ->
                sessionManager.updateSessionTitle(session.id, newTitle)
                updateNavigationMenu()
                Toast.makeText(this, "标题已更新", Toast.LENGTH_SHORT).show()
            },
            onSessionDelete = { session ->
                sessionManager.deleteSession(session.id)
                updateNavigationMenu()
                
                // 如果删除的是当前会话，清空聊天界面
                if (sessionManager.getCurrentSessionId() == session.id) {
                    messages.clear()
                    chatAdapter.notifyDataSetChanged()
                    updateWelcomeVisibility()
                    sessionManager.clearCurrentSession()
                }
                
                Toast.makeText(this, "对话已删除", Toast.LENGTH_SHORT).show()
            }
        )
        
        recyclerViewChatSessions.apply {
            adapter = chatSessionAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }
    
    private fun setupClickListeners() {
        fabSend.setOnClickListener {
            sendMessage()
        }
        
        editTextMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
        
        // 新建对话按钮点击监听
        layoutNewChat.setOnClickListener {
            startNewChat()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        
        // 联网搜索Chip点击监听
        chipWebSearch.setOnCheckedChangeListener { _, isChecked ->
            isWebSearchEnabled = isChecked
        }
        
        // 文件上传图标点击监听
        textInputLayout.setStartIconOnClickListener {
            openImagePicker()
        }
        
        // 移除图片按钮点击监听
        btnRemoveImage.setOnClickListener {
            clearSelectedImage()
        }
    }
    
    private fun setupNavigationDrawer() {
        // 设置ActionBarDrawerToggle
        drawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
    }
    
    private fun setupWindowInsets() {
        // 强制设置系统UI标志，确保底部导航栏完全透明
        window.apply {
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            
            // 强制底部导航栏透明，禁用系统强制对比度
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
                isStatusBarContrastEnforced = false
            }
            
            // 设置系统UI可见性标志，确保完全透明
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            // 为主容器设置侧边边距，不设置顶部边距让AppBar延伸到状态栏
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            
            // 设置状态栏占位区域的高度
            val statusBarSpacer = findViewById<View>(R.id.statusBarSpacer)
            val layoutParams = statusBarSpacer.layoutParams
            layoutParams.height = systemBars.top
            statusBarSpacer.layoutParams = layoutParams
            
            // 智能安全距离策略：为底部输入区域设置安全距离
            val layoutInputArea = findViewById<View>(R.id.layoutInputArea)
            val bottomPadding = when {
                // 键盘弹起时，使用键盘高度（优先级最高）
                imeInsets.bottom > 0 -> imeInsets.bottom
                // 键盘收起时，使用导航栏高度作为安全距离，确保输入区域不被遮挡
                // 同时保持导航栏区域完全透明
                else -> systemBars.bottom
            }
            
            layoutInputArea.setPadding(
                layoutInputArea.paddingLeft,
                layoutInputArea.paddingTop,
                layoutInputArea.paddingRight,
                bottomPadding
            )
            
            // 为聊天内容区域添加底部安全距离，确保最后一条消息不被导航栏遮挡
            val recyclerViewChat = findViewById<RecyclerView>(R.id.recyclerViewChat)
            recyclerViewChat.setPadding(
                recyclerViewChat.paddingLeft,
                recyclerViewChat.paddingTop,
                recyclerViewChat.paddingRight,
                // 为聊天内容添加最小安全距离，确保内容可见性
                if (imeInsets.bottom > 0) 0 else systemBars.bottom
            )
            
            insets
        }
    }
    
    private fun initializeSession() {
        // 清空当前消息列表
        messages.clear()
        chatAdapter.notifyDataSetChanged()
        
        // 清除当前会话，等待用户发送第一条消息时再创建
        sessionManager.clearCurrentSession()
        
        // 更新导航菜单
        updateNavigationMenu()
        
        // 显示欢迎界面
        updateWelcomeVisibility()
    }
    
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            content = "你好！我是GSRobot，很高兴为您服务！有什么我可以帮助您的吗？",
            isFromUser = false
        )
        chatAdapter.addMessage(welcomeMessage)
        sessionManager.saveMessage(welcomeMessage)
        scrollToBottom()
    }
    
    private fun sendMessage() {
        val messageText = editTextMessage.text?.toString()?.trim()
        
        // 检查是否有文本或图片
        if (messageText.isNullOrEmpty() && selectedImageUri == null) {
            Toast.makeText(this, "请输入消息或选择图片", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 检查是否是新会话的第一条消息
        val isFirstMessage = !sessionManager.hasCurrentSession()
        Log.d("GSRobot", "sendMessage: isFirstMessage = $isFirstMessage, messageText = $messageText")
        
        // 确保有当前会话
        if (isFirstMessage) {
            // 先创建会话，使用临时标题
            sessionManager.startNewSession("新对话")
            Log.d("GSRobot", "Created new session with temporary title")
            // 更新导航菜单以显示新会话
            updateNavigationMenu()
        }
        
        // 处理图片转换为Base64
        var imageDataUrl: String? = null
        selectedImageUri?.let { uri ->
            try {
                imageDataUrl = ImageUtils.uriToBase64DataUrl(this, uri)
                Log.d("GSRobot", "Image converted to Base64 data URL")
            } catch (e: Exception) {
                Log.e("GSRobot", "Failed to convert image to Base64", e)
                Toast.makeText(this, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // 添加用户消息
        val userMessage = ChatMessage(
            content = messageText ?: "",
            isFromUser = true,
            imageUri = imageDataUrl
        )
        chatAdapter.addMessage(userMessage)
        sessionManager.saveMessage(userMessage)
        scrollToBottom()
        updateWelcomeVisibility()
        
        // 如果是第一条消息，更新会话标题为完整的消息内容
        if (isFirstMessage) {
            val titleText = if (messageText.isNullOrEmpty()) "图片消息" else messageText
            sessionManager.getCurrentSessionId()?.let { sessionId ->
                Log.d("GSRobot", "Updating session title: sessionId = $sessionId, newTitle = $titleText")
                sessionManager.updateSessionTitle(sessionId, titleText)
                // 更新导航菜单以显示新的标题
                updateNavigationMenu()
                Log.d("GSRobot", "Session title updated and navigation menu refreshed")
            }
        }
        
        // 清空输入框和选中的图片
        editTextMessage.text?.clear()
        clearSelectedImage()
        
        // AI回复（延迟1-2秒）
        simulateAiResponse()
    }
    
    private fun simulateAiResponse() {
        // 检查是否有可用的客户端
        if (openAIClient == null && googleAIClient == null && anthropicAIClient == null) {
            val errorMessage = ChatMessage(
                content = "请先在设置中配置AI API",
                isFromUser = false
            )
            chatAdapter.addMessage(errorMessage)
            sessionManager.saveMessage(errorMessage)
            scrollToBottom()
            return
        }
        
        // 显示"正在输入"状态
        fabSend.isEnabled = false
        
        // 立即创建并显示空的AI回复气泡
        val tempMessage = ChatMessage(
            content = "正在思考中...",
            isFromUser = false
        )
        chatAdapter.addMessage(tempMessage)
        scrollToBottom()
        
        // 获取配置
        val modelName = selectedModel?.name ?: ""
        if (modelName.isEmpty()) {
            val errorMessage = ChatMessage(
                content = "请先在设置中配置模型",
                isFromUser = false
            )
            chatAdapter.updateLastMessage(errorMessage)
            sessionManager.saveMessage(errorMessage)
            scrollToBottom()
            fabSend.isEnabled = true
            return
        }
        
        // 根据客户端类型发送请求
        coroutineScope.launch {
            try {
                var responseContent = ""
                var isFirstChunk = true
                
                val filteredMessages = messages.filter { it.content != "正在思考中..." }
                
                if (openAIClient != null) {
                    // 使用OpenAI客户端
                    val newConfigPrefs = getSharedPreferences("model_config", Context.MODE_PRIVATE)
                    val streaming = newConfigPrefs.getBoolean("streaming", true)
                    
                    openAIClient!!.sendChatRequest(modelName, filteredMessages, streaming, isWebSearchEnabled)
                        .collect { chunk ->
                            if (isFirstChunk) {
                                responseContent = chunk
                                isFirstChunk = false
                            } else {
                                responseContent += chunk
                            }
                            
                            val aiMessage = ChatMessage(
                                content = responseContent,
                                isFromUser = false
                            )
                            
                            chatAdapter.updateLastMessage(aiMessage)
                            scrollToBottom()
                        }
                } else if (googleAIClient != null) {
                    // 使用Google AI客户端
                    val googleAIConfigPrefs = getSharedPreferences("google_ai_model_config", Context.MODE_PRIVATE)
                    val streaming = googleAIConfigPrefs.getBoolean("streaming", true)
                    val apiKey = selectedGroup?.apiKey ?: ""
                    
                    googleAIClient!!.sendChatRequest(modelName, filteredMessages, apiKey, streaming, isWebSearchEnabled)
                        .collect { chunk ->
                            if (isFirstChunk) {
                                responseContent = chunk
                                isFirstChunk = false
                            } else {
                                responseContent += chunk
                            }
                            
                            val aiMessage = ChatMessage(
                                content = responseContent,
                                isFromUser = false
                            )
                            
                            chatAdapter.updateLastMessage(aiMessage)
                            scrollToBottom()
                        }
                } else if (anthropicAIClient != null) {
                    // 使用Anthropic AI客户端
                    val anthropicAIConfigPrefs = getSharedPreferences("anthropic_ai_model_config", Context.MODE_PRIVATE)
                    val streaming = anthropicAIConfigPrefs.getBoolean("streaming", true)
                    val apiKey = selectedGroup?.apiKey ?: ""
                    
                    anthropicAIClient!!.sendChatRequest(modelName, filteredMessages, apiKey, streaming, isWebSearchEnabled)
                        .collect { chunk ->
                            if (isFirstChunk) {
                                responseContent = chunk
                                isFirstChunk = false
                            } else {
                                responseContent += chunk
                            }
                            
                            val aiMessage = ChatMessage(
                                content = responseContent,
                                isFromUser = false
                            )
                            
                            chatAdapter.updateLastMessage(aiMessage)
                            scrollToBottom()
                        }
                }
                
                // 保存最终消息到当前会话
                val finalMessage = ChatMessage(
                    content = responseContent,
                    isFromUser = false
                )
                sessionManager.saveMessage(finalMessage)
                
                // 更新UI中的最后一条消息
                chatAdapter.updateLastMessage(finalMessage)
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting AI response", e)
                
                val errorMessage = ChatMessage(
                    content = "获取AI回复失败: ${e.message}",
                    isFromUser = false
                )
                chatAdapter.updateLastMessage(errorMessage)
                sessionManager.saveMessage(errorMessage)
                scrollToBottom()
            } finally {
                // 重新启用发送按钮
                fabSend.isEnabled = true
            }
        }
    }
    

    
    private fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            recyclerViewChat.scrollToPosition(messages.size - 1)
        }
    }
    
    private fun updateWelcomeVisibility() {
        textViewWelcome.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
    }
    

    
    private fun startNewChat() {
        // 清空当前对话
        messages.clear()
        chatAdapter.notifyDataSetChanged()
        updateWelcomeVisibility()
        
        // 清除当前会话，等待用户发送第一条消息时再创建
        sessionManager.clearCurrentSession()
        
        // 更新导航抽屉菜单
        updateNavigationMenu()
        
        Toast.makeText(this, "开始新对话", Toast.LENGTH_SHORT).show()
    }
    
    private fun loadChatSession(sessionId: Long) {
        // 加载指定会话的消息
        val sessionMessages = sessionManager.loadSession(sessionId)
        
        // 更新UI
        messages.clear()
        messages.addAll(sessionMessages)
        chatAdapter.notifyDataSetChanged()
        updateWelcomeVisibility()
        
        // 滚动到底部
        if (messages.isNotEmpty()) {
            recyclerViewChat.scrollToPosition(messages.size - 1)
        }
        
        Toast.makeText(this, "已加载历史对话", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateNavigationMenu() {
        // 获取所有会话
        val sessions = sessionManager.getAllSessions()
        
        // 更新对话历史适配器
        chatSessionAdapter.updateSessions(sessions)
        
        // 更新空状态显示
        if (sessions.isEmpty()) {
            recyclerViewChatSessions.visibility = View.GONE
            textEmptyState.visibility = View.VISIBLE
        } else {
            recyclerViewChatSessions.visibility = View.VISIBLE
            textEmptyState.visibility = View.GONE
        }
    }
    
    private fun updateToolbarTitle() {
        val modelName = selectedModel?.name ?: ""
        val title = if (modelName.isNotEmpty()) {
            modelName
        } else {
            "GSRobot"
        }
        supportActionBar?.title = title
        toolbar.title = title
    }


    
    override fun onResume() {
        super.onResume()
        // 重新加载配置，以防从设置页面返回后配置发生变化
        loadModelConfiguration()
        // 更新工具栏标题
        updateToolbarTitle()
        // 重新初始化AI客户端
        initializeAIClients()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_toolbar, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    
    /**
     * 打开图片选择器
     */
    private fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

    /**
     * 显示图片预览
     */
    private fun showImagePreview(uri: Uri) {
        try {
            imagePreview.setImageURI(uri)
            layoutImagePreview.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("MainActivity", "显示图片预览失败", e)
            Toast.makeText(this, "无法显示图片预览", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 清除选中的图片
     */
    private fun clearSelectedImage() {
        selectedImageUri = null
        layoutImagePreview.visibility = View.GONE
        imagePreview.setImageURI(null)
    }
}