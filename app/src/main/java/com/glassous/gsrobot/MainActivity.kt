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
import com.glassous.gsrobot.utils.ImageDownloadManager
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
    private lateinit var imageDownloadManager: ImageDownloadManager
    private lateinit var textViewWelcome: TextView
    private lateinit var recyclerViewChatSessions: RecyclerView
    private lateinit var chatSessionAdapter: ChatSessionAdapter
    private lateinit var textEmptyState: TextView
    private lateinit var layoutNewChat: View
    private lateinit var modelConfigPrefs: SharedPreferences

    // 联网搜索Chip
    private lateinit var chipWebSearch: com.google.android.material.chip.Chip
    private var isWebSearchEnabled: Boolean = false
    
    // 图片生成Chip
    private lateinit var chipImageGenerate: com.google.android.material.chip.Chip
    private var isImageGenerateEnabled: Boolean = false

    // 文件上传相关
    private lateinit var textInputLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var layoutImagePreview: android.widget.LinearLayout
    private lateinit var imagePreview: android.widget.ImageView
    private lateinit var btnRemoveImage: com.google.android.material.button.MaterialButton
    private var selectedImageUri: android.net.Uri? = null

    // 输入建议按钮
    private lateinit var layoutInputSuggestions: android.widget.LinearLayout
    private lateinit var btnSuggestion1: com.google.android.material.button.MaterialButton
    private lateinit var btnSuggestion2: com.google.android.material.button.MaterialButton
    private lateinit var btnSuggestion3: com.google.android.material.button.MaterialButton
    private lateinit var btnRefreshSuggestions: com.google.android.material.button.MaterialButton

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
    private var volcanoArkClient: VolcanoArkClient? = null
    private var alibabaCloudBailianClient: AlibabaCloudBailianClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    // 输入建议数据
    private val inputSuggestions = listOf(
        Pair("写作", "请帮我写一篇关于以下主题的文章："),
        Pair("翻译", "请将以下内容翻译成中文："),
        Pair("编程", "请用 Python 编写一个函数来实现以下功能："),
        Pair("代码解释", "请解释以下代码的作用："),
        Pair("学习", "请详细解释以下概念："),
        Pair("创意", "请给我一些关于以下主题的创意点子："),
        Pair("总结", "请总结以下内容的要点："),
        Pair("润色", "请优化以下文本，使其更流畅自然："),
        Pair("分析", "请深入分析以下内容："),
        Pair("计划", "请为我制定一个详细的计划："),
        Pair("邮件", "请帮我写一封专业的邮件，主题是："),
        Pair("故事", "请讲一个关于以下主题的故事："),
        Pair("诗歌", "请写一首关于以下主题的诗歌："),
        Pair("演讲", "请为我准备一篇演讲稿，主题是："),
        Pair("简历", "请帮我优化简历中的以下部分："),
        Pair("营销", "请为我们的产品制定一个营销策略："),
        Pair("报告", "请帮我撰写一份关于以下主题的报告："),
        Pair("调研", "请帮我进行以下主题的调研并总结："),
        Pair("面试", "请帮我准备面试中可能会遇到的问题："),
        Pair("对比", "请对以下两个事物进行比较并分析优缺点：")
    )

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
        
        // 初始化图片下载管理器
        imageDownloadManager = ImageDownloadManager(this)

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
        
        // 初始化图片生成Chip
        chipImageGenerate = findViewById(R.id.chipImageGenerate)

        // 初始化输入框布局
        textInputLayout = findViewById(R.id.textInputLayout)

        // 初始化图片预览相关视图
        layoutImagePreview = findViewById(R.id.layoutImagePreview)
        imagePreview = findViewById(R.id.imagePreview)
        btnRemoveImage = findViewById(R.id.btnRemoveImage)

        // 初始化输入建议按钮
        layoutInputSuggestions = findViewById(R.id.layoutInputSuggestions)
        btnSuggestion1 = findViewById(R.id.btnSuggestion1)
        btnSuggestion2 = findViewById(R.id.btnSuggestion2)
        btnSuggestion3 = findViewById(R.id.btnSuggestion3)
        btnRefreshSuggestions = findViewById(R.id.btnRefreshSuggestions)

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

        // 初始化输入建议按钮
        updateInputSuggestions()
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

        // 加载火山方舟模型配置
        val volcanoArkConfigPrefs = getSharedPreferences("volcano_ark_model_config", Context.MODE_PRIVATE)
        val volcanoArkGroupsConfigJson = volcanoArkConfigPrefs.getString("groups_config", "") ?: ""
        if (volcanoArkGroupsConfigJson.isNotEmpty()) {
            allGroups.addAll(ConfigManager.jsonToGroupConfigList(volcanoArkGroupsConfigJson))
            Log.d("MainActivity", "Loaded Volcano Ark groups from config system")
        }

        // 加载阿里云百炼模型配置
        val alibabaCloudBailianConfigPrefs = getSharedPreferences("alibaba_cloud_bailian_model_config", Context.MODE_PRIVATE)
        val alibabaCloudBailianGroupsConfigJson = alibabaCloudBailianConfigPrefs.getString("groups_config", "") ?: ""
        if (alibabaCloudBailianGroupsConfigJson.isNotEmpty()) {
            allGroups.addAll(ConfigManager.jsonToGroupConfigList(alibabaCloudBailianGroupsConfigJson))
            Log.d("MainActivity", "Loaded Alibaba Cloud Bailian groups from config system")
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
        volcanoArkClient = null
        alibabaCloudBailianClient = null

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
                // 判断是否为火山方舟模型
                groupName.contains("火山方舟") || groupName.contains("volcano") || groupName.contains("ark") -> {
                    // 火山方舟模型
                    if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                        volcanoArkClient = VolcanoArkClient(baseUrl, apiKey, this)
                        Log.d("MainActivity", "Volcano Ark client initialized with group: ${selectedGroup!!.name}, base URL: $baseUrl")
                    } else {
                        Log.d("MainActivity", "Volcano Ark client not initialized - missing configuration")
                    }
                }
                // 判断是否为阿里云百炼模型
                groupName.contains("阿里云百炼") || groupName.contains("alibaba") || groupName.contains("bailian") -> {
                    // 阿里云百炼模型
                    if (apiKey.isNotEmpty()) {
                        alibabaCloudBailianClient = AlibabaCloudBailianClient(this)
                        Log.d("MainActivity", "Alibaba Cloud Bailian client initialized with group: ${selectedGroup!!.name}")
                    } else {
                        Log.d("MainActivity", "Alibaba Cloud Bailian client not initialized - missing API key")
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
        
        // 图片生成Chip点击监听
        chipImageGenerate.setOnCheckedChangeListener { _, isChecked ->
            isImageGenerateEnabled = isChecked
        }

        // 文件上传图标点击监听
        textInputLayout.setStartIconOnClickListener {
            openImagePicker()
        }

        // 移除图片按钮点击监听
        btnRemoveImage.setOnClickListener {
            clearSelectedImage()
        }

        // 建议按钮点击监听
        btnSuggestion1.setOnClickListener {
            val buttonText = btnSuggestion1.text.toString()
            val suggestion = inputSuggestions.find { it.first == buttonText }
            suggestion?.let {
                editTextMessage.setText(it.second)
                editTextMessage.setSelection(editTextMessage.text?.length ?: 0)
            }
        }

        btnSuggestion2.setOnClickListener {
            val buttonText = btnSuggestion2.text.toString()
            val suggestion = inputSuggestions.find { it.first == buttonText }
            suggestion?.let {
                editTextMessage.setText(it.second)
                editTextMessage.setSelection(editTextMessage.text?.length ?: 0)
            }
        }

        btnSuggestion3.setOnClickListener {
            val buttonText = btnSuggestion3.text.toString()
            val suggestion = inputSuggestions.find { it.first == buttonText }
            suggestion?.let {
                editTextMessage.setText(it.second)
                editTextMessage.setSelection(editTextMessage.text?.length ?: 0)
            }
        }

        // 换一批按钮点击监听
        btnRefreshSuggestions.setOnClickListener {
            updateInputSuggestions()
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
            content = "",
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

        // 如果启用了图片生成功能，处理图片生成请求
        if (isImageGenerateEnabled) {
            if (messageText.isNullOrEmpty()) {
                Toast.makeText(this, "请输入图片描述", Toast.LENGTH_SHORT).show()
                return
            }
            handleImageGeneration(messageText)
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

        // 如果第一条消息，更新会话标题为完整的消息内容
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

    private fun handleImageGeneration(prompt: String) {
        // 确保有当前会话
        val isFirstMessage = !sessionManager.hasCurrentSession()
        if (isFirstMessage) {
            sessionManager.startNewSession("图片生成")
            updateNavigationMenu()
        }

        // 添加用户的图片生成请求消息
        val userMessage = ChatMessage(
            content = prompt,
            isFromUser = true
        )
        chatAdapter.addMessage(userMessage)
        sessionManager.saveMessage(userMessage)
        scrollToBottom()
        updateWelcomeVisibility()

        // 添加加载中的消息
        val loadingMessage = ChatMessage(
            content = "正在生成图片，请稍候...",
            isFromUser = false
        )
        chatAdapter.addMessage(loadingMessage)
        scrollToBottom()

        // 根据当前选择的模型类型调用相应的图片生成API
        when {
            // 检查是否选择了阿里云百炼模型
            alibabaCloudBailianClient != null && selectedGroup != null && 
            (selectedGroup!!.name.contains("阿里云百炼", ignoreCase = true) || 
             selectedGroup!!.name.contains("alibaba", ignoreCase = true) || 
             selectedGroup!!.name.contains("bailian", ignoreCase = true)) -> {
                // 使用阿里云百炼图片生成
                val apiKey = selectedGroup?.apiKey ?: ""
                Log.d("GSRobot", "Using Alibaba Cloud Bailian for image generation with model: qwen-image-plus")
                
                coroutineScope.launch {
                    try {
                        alibabaCloudBailianClient!!.generateImage(prompt, apiKey).collect { imageResult ->
                            // 检查结果是否为URL（成功）还是错误消息
                            if (imageResult.startsWith("http", ignoreCase = true)) {
                                // 成功生成图片，下载并保存到本地
                                coroutineScope.launch {
                                    val localImagePath = imageDownloadManager.downloadAndSaveImage(imageResult)
                                    val imageMessage = ChatMessage(
                                        content = "已为您生成图片",
                                        isFromUser = false,
                                        imageUri = imageResult,
                                        localImagePath = localImagePath
                                    )
                                    chatAdapter.updateLastMessage(imageMessage)
                                    sessionManager.saveMessage(imageMessage)
                                    Log.d("GSRobot", "Alibaba Cloud Bailian image generation successful, saved to: $localImagePath")
                                }
                            } else if (imageResult.startsWith("Error:", ignoreCase = true)) {
                                // 生成失败，显示错误消息
                                val errorMessage = ChatMessage(
                                    content = "图片生成失败：${imageResult.substring(6)}",
                                    isFromUser = false
                                )
                                chatAdapter.updateLastMessage(errorMessage)
                                sessionManager.saveMessage(errorMessage)
                                Log.e("GSRobot", "Alibaba Cloud Bailian image generation failed: $imageResult")
                            } else if (imageResult.contains("正在处理中") || imageResult.contains("任务已提交")) {
                                // 保持原有的"正在生成图片，请稍候..."消息，不更新
                                Log.d("GSRobot", "Alibaba Cloud Bailian image generation status: $imageResult")
                            } else {
                                // 其他状态更新消息 - 只更新内容，不保存到数据库
                                val statusMessage = ChatMessage(
                                    content = imageResult,
                                    isFromUser = false
                                )
                                chatAdapter.updateLastMessage(statusMessage)
                                Log.d("GSRobot", "Alibaba Cloud Bailian image generation status: $imageResult")
                            }
                            scrollToBottom()
                        }
                    } catch (e: Exception) {
                        Log.e("GSRobot", "Alibaba Cloud Bailian image generation failed", e)
                        val errorMessage = ChatMessage(
                            content = "图片生成失败：${e.message}",
                            isFromUser = false
                        )
                        chatAdapter.updateLastMessage(errorMessage)
                        sessionManager.saveMessage(errorMessage)
                        scrollToBottom()
                    }
                }
            }
            // 检查是否选择了Google AI模型
            googleAIClient != null && selectedGroup != null && selectedGroup!!.baseUrl.isEmpty() -> {
                // 使用Google AI图片生成
                val apiKey = selectedGroup?.apiKey ?: ""
                Log.d("GSRobot", "Using Google AI for image generation with model: gemini-2.5-flash-image-preview")
                
                coroutineScope.launch {
                    try {
                        googleAIClient!!.generateImage(prompt, apiKey).collect { imageResult ->
                            // 检查结果是否为data URL（成功）还是错误消息
                            if (imageResult.startsWith("data:", ignoreCase = true)) {
                                // 成功生成图片，保存Base64图片到本地
                                coroutineScope.launch {
                                    val localImagePath = imageDownloadManager.saveBase64Image(imageResult)
                                    val imageMessage = ChatMessage(
                                        content = "已为您生成图片：",
                                        isFromUser = false,
                                        imageUri = imageResult,
                                        localImagePath = localImagePath
                                    )
                                    chatAdapter.updateLastMessage(imageMessage)
                                    sessionManager.saveMessage(imageMessage)
                                    Log.d("GSRobot", "Google AI image generation successful, saved to: $localImagePath")
                                }
                            } else {
                                // 生成失败，显示错误消息
                                val errorMessage = ChatMessage(
                                    content = "图片生成失败：$imageResult",
                                    isFromUser = false
                                )
                                chatAdapter.updateLastMessage(errorMessage)
                                sessionManager.saveMessage(errorMessage)
                                Log.e("GSRobot", "Google AI image generation failed: $imageResult")
                            }
                            scrollToBottom()
                        }
                    } catch (e: Exception) {
                        Log.e("GSRobot", "Google AI image generation failed", e)
                        val errorMessage = ChatMessage(
                            content = "图片生成失败：${e.message}",
                            isFromUser = false
                        )
                        chatAdapter.updateLastMessage(errorMessage)
                        sessionManager.saveMessage(errorMessage)
                        scrollToBottom()
                    }
                }
            }
            // 使用OpenAI图片生成（原有逻辑）
            openAIClient != null -> {
                Log.d("GSRobot", "Using OpenAI for image generation with model: dall-e-3")
                
                coroutineScope.launch {
                    try {
                        openAIClient!!.generateImage(
                            prompt = prompt,
                            model = "dall-e-3",
                            size = "1024x1024",
                            quality = "standard",
                            n = 1
                        ).collect { imageResult ->
                            // 检查结果是否为URL（成功）还是错误消息
                            if (imageResult.startsWith("http")) {
                                // 成功生成图片，下载并保存到本地
                                coroutineScope.launch {
                                    val localImagePath = imageDownloadManager.downloadAndSaveImage(imageResult)
                                    val imageMessage = ChatMessage(
                                        content = "已为您生成图片：",
                                        isFromUser = false,
                                        imageUri = imageResult,
                                        localImagePath = localImagePath
                                    )
                                    chatAdapter.updateLastMessage(imageMessage)
                                    sessionManager.saveMessage(imageMessage)
                                    Log.d("GSRobot", "OpenAI image generation successful, saved to: $localImagePath")
                                }
                            } else {
                                // 生成失败，显示错误消息
                                val errorMessage = ChatMessage(
                                    content = "图片生成失败：$imageResult",
                                    isFromUser = false
                                )
                                chatAdapter.updateLastMessage(errorMessage)
                                sessionManager.saveMessage(errorMessage)
                                Log.e("GSRobot", "OpenAI image generation failed: $imageResult")
                            }
                            scrollToBottom()
                        }
                    } catch (e: Exception) {
                        Log.e("GSRobot", "OpenAI image generation failed", e)
                        val errorMessage = ChatMessage(
                            content = "图片生成失败：${e.message}",
                            isFromUser = false
                        )
                        chatAdapter.updateLastMessage(errorMessage)
                        sessionManager.saveMessage(errorMessage)
                        scrollToBottom()
                    }
                }
            }
            else -> {
                // 没有可用的图片生成客户端
                val errorMessage = ChatMessage(
                    content = "图片生成功能不可用，请检查AI模型配置",
                    isFromUser = false
                )
                chatAdapter.updateLastMessage(errorMessage)
                sessionManager.saveMessage(errorMessage)
                scrollToBottom()
                Log.e("GSRobot", "No image generation client available")
            }
        }

        // 清空输入框
        editTextMessage.text?.clear()
    }

    private fun simulateAiResponse() {
        // 检查是否有可用的客户端
        if (openAIClient == null && googleAIClient == null && anthropicAIClient == null && volcanoArkClient == null && alibabaCloudBailianClient == null) {
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
                } else if (volcanoArkClient != null) {
                    // 使用火山方舟客户端
                    val volcanoArkConfigPrefs = getSharedPreferences("volcano_ark_model_config", Context.MODE_PRIVATE)
                    val streaming = volcanoArkConfigPrefs.getBoolean("streaming", true)

                    volcanoArkClient!!.sendChatRequest(modelName, filteredMessages, streaming, isWebSearchEnabled)
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
                } else if (alibabaCloudBailianClient != null) {
                    // 使用阿里云百炼客户端
                    val apiKey = selectedGroup?.apiKey ?: ""

                    alibabaCloudBailianClient!!.sendChatRequest(modelName, filteredMessages, apiKey, isWebSearchEnabled)
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
        if (messages.isEmpty()) {
            textViewWelcome.visibility = View.VISIBLE
            textViewWelcome.text = getTimeBasedGreeting()
            layoutInputSuggestions.visibility = View.VISIBLE
            btnRefreshSuggestions.visibility = View.VISIBLE
        } else {
            textViewWelcome.visibility = View.GONE
            layoutInputSuggestions.visibility = View.GONE
            btnRefreshSuggestions.visibility = View.GONE
        }
    }

    private fun getTimeBasedGreeting(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> "早上好！"
            in 12..13 -> "中午好！"
            in 14..17 -> "下午好！"
            in 18..22 -> "晚上好！"
            else -> "夜深了！"
        }
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
            "Nexara"
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

    private fun updateInputSuggestions() {
        // 随机选择3个不同的建议
        val shuffledSuggestions = inputSuggestions.shuffled().take(3)

        btnSuggestion1.text = shuffledSuggestions[0].first
        btnSuggestion2.text = shuffledSuggestions[1].first
        btnSuggestion3.text = shuffledSuggestions[2].first
    }
}