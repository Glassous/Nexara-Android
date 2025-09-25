package com.glassous.gsrobot

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.glassous.gsrobot.data.ConfigManager
import com.glassous.gsrobot.data.GroupConfig
import com.glassous.gsrobot.data.ModelConfig
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.glassous.gsrobot.GoogleAIModelConfigActivity
import com.glassous.gsrobot.VolcanoArkModelConfigActivity

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var themeManager: ThemeManager
    private lateinit var buttonThemeAuto: MaterialButton
    private lateinit var buttonThemeLight: MaterialButton
    private lateinit var buttonThemeDark: MaterialButton
    private lateinit var buttonModelConfig: MaterialButton
    private lateinit var buttonGoogleAIConfig: MaterialButton
    private lateinit var buttonAnthropicConfig: MaterialButton
    private lateinit var buttonXAIConfig: MaterialButton
    private lateinit var buttonDeepSeekConfig: MaterialButton
    private lateinit var buttonMoonshotConfig: MaterialButton
    private lateinit var buttonAliyunConfig: MaterialButton
    private lateinit var buttonVolcanoConfig: MaterialButton
    
    // 模型选择相关
    private lateinit var cardCurrentModel: MaterialCardView
    private lateinit var textCurrentModelName: TextView
    private lateinit var textCurrentModelProvider: TextView
    private lateinit var layoutModelGroups: LinearLayout
    private lateinit var sharedPreferences: SharedPreferences
    
    private var currentGroups: List<GroupConfig> = emptyList()
    private var selectedGroup: GroupConfig? = null
    private var selectedModel: ModelConfig? = null
    private var isModelGroupsExpanded = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        
        initViews()
        setupToolbar()
        setupWindowInsets()
        setupThemeButtons()
        setupModelConfigButton()
        setupGoogleAIConfigButton()
        setupAnthropicConfigButton()
        setupNewAIConfigButtons()
        setupModelSelection()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        buttonThemeAuto = findViewById(R.id.buttonThemeAuto)
        buttonThemeLight = findViewById(R.id.buttonThemeLight)
        buttonThemeDark = findViewById(R.id.buttonThemeDark)
        buttonModelConfig = findViewById(R.id.buttonModelConfig)
        buttonGoogleAIConfig = findViewById(R.id.buttonGoogleAIConfig)
        buttonAnthropicConfig = findViewById(R.id.buttonAnthropicConfig)
        buttonXAIConfig = findViewById(R.id.buttonXAIConfig)
        buttonDeepSeekConfig = findViewById(R.id.buttonDeepSeekConfig)
        buttonMoonshotConfig = findViewById(R.id.buttonMoonshotConfig)
        buttonAliyunConfig = findViewById(R.id.buttonAliyunConfig)
        buttonVolcanoConfig = findViewById(R.id.buttonVolcanoConfig)
        
        // 模型选择相关
        cardCurrentModel = findViewById(R.id.cardCurrentModel)
        textCurrentModelName = findViewById(R.id.textCurrentModelName)
        textCurrentModelProvider = findViewById(R.id.textCurrentModelProvider)
        layoutModelGroups = findViewById(R.id.layoutModelGroups)
        
        themeManager = ThemeManager.getInstance(this)
        sharedPreferences = getSharedPreferences("model_config", Context.MODE_PRIVATE)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "设置"
        }
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
            
            // 为主容器设置侧边边距，不设置顶部和底部边距让内容延伸到导航栏下方实现完全透明
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            
            // 设置状态栏占位区域的高度
            val statusBarSpacer = findViewById<android.view.View>(R.id.statusBarSpacer)
            val layoutParams = statusBarSpacer.layoutParams
            layoutParams.height = systemBars.top
            statusBarSpacer.layoutParams = layoutParams
            
            insets
        }
    }
    
    private fun setupThemeButtons() {
        // 设置当前选中的主题按钮
        updateThemeButtonSelection()
        
        // 设置按钮点击事件
        buttonThemeAuto.setOnClickListener {
            themeManager.setThemeMode(ThemeManager.THEME_AUTO)
            updateThemeButtonSelection()
        }
        
        buttonThemeLight.setOnClickListener {
            themeManager.setThemeMode(ThemeManager.THEME_LIGHT)
            updateThemeButtonSelection()
        }
        
        buttonThemeDark.setOnClickListener {
            themeManager.setThemeMode(ThemeManager.THEME_DARK)
            updateThemeButtonSelection()
        }
    }
    
    private fun updateThemeButtonSelection() {
        val currentTheme = themeManager.getCurrentThemeMode()
        
        // 重置所有按钮样式
        resetButtonStyles()
        
        // 获取主题颜色
        val typedArray = theme.obtainStyledAttributes(intArrayOf(
            android.R.attr.colorPrimary,
            android.R.attr.textColorPrimaryInverse
        ))
        val primaryColor = typedArray.getColor(0, 0)
        val onPrimaryColor = typedArray.getColor(1, 0)
        typedArray.recycle()
        
        // 设置选中按钮的样式
        when (currentTheme) {
            ThemeManager.THEME_AUTO -> {
                buttonThemeAuto.setBackgroundColor(primaryColor)
                buttonThemeAuto.setTextColor(onPrimaryColor)
            }
            ThemeManager.THEME_LIGHT -> {
                buttonThemeLight.setBackgroundColor(primaryColor)
                buttonThemeLight.setTextColor(onPrimaryColor)
            }
            ThemeManager.THEME_DARK -> {
                buttonThemeDark.setBackgroundColor(primaryColor)
                buttonThemeDark.setTextColor(onPrimaryColor)
            }
        }
    }
    
    private fun resetButtonStyles() {
        val buttons = listOf(buttonThemeAuto, buttonThemeLight, buttonThemeDark)
        
        // 获取表面颜色
        val typedArray = theme.obtainStyledAttributes(intArrayOf(
            android.R.attr.textColorPrimary
        ))
        val onSurfaceColor = typedArray.getColor(0, 0)
        typedArray.recycle()
        
        buttons.forEach { button ->
            button.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            button.setTextColor(onSurfaceColor)
        }
    }
    
    private fun setupModelConfigButton() {
        buttonModelConfig.setOnClickListener {
            val intent = Intent(this, OpenAIModelConfigActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupGoogleAIConfigButton() {
        buttonGoogleAIConfig.setOnClickListener {
            val intent = Intent(this, GoogleAIModelConfigActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupAnthropicConfigButton() {
        buttonAnthropicConfig.setOnClickListener {
            startActivity(Intent(this, AnthropicAIModelConfigActivity::class.java))
        }
    }
    
    private fun setupNewAIConfigButtons() {
        buttonXAIConfig.setOnClickListener {
            Toast.makeText(this, "xAI配置功能敬请期待", Toast.LENGTH_SHORT).show()
        }
        
        buttonDeepSeekConfig.setOnClickListener {
            Toast.makeText(this, "DeepSeek配置功能敬请期待", Toast.LENGTH_SHORT).show()
        }
        
        buttonMoonshotConfig.setOnClickListener {
            Toast.makeText(this, "Moonshot配置功能敬请期待", Toast.LENGTH_SHORT).show()
        }
        
        buttonAliyunConfig.setOnClickListener {
            val intent = Intent(this, AlibabaCloudBailianModelConfigActivity::class.java)
            startActivity(intent)
        }
        
        buttonVolcanoConfig.setOnClickListener {
            val intent = Intent(this, VolcanoArkModelConfigActivity::class.java)
            startActivity(intent)
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupModelSelection() {
        loadModelConfiguration()
        updateCurrentModelDisplay()
        
        cardCurrentModel.setOnClickListener {
            toggleModelGroups()
        }
    }
    
    private fun loadModelConfiguration() {
        val allGroups = mutableListOf<GroupConfig>()
        
        // 加载OpenAI模型配置
        val openaiGroupsConfigJson = sharedPreferences.getString("groups_config", "") ?: ""
        if (openaiGroupsConfigJson.isNotEmpty()) {
            allGroups.addAll(ConfigManager.jsonToGroupConfigList(openaiGroupsConfigJson))
        }
        
        // 加载Google AI模型配置
        val googleAIPrefs = getSharedPreferences("google_ai_model_config", Context.MODE_PRIVATE)
        val googleAIGroupsConfigJson = googleAIPrefs.getString("groups_config", "") ?: ""
        if (googleAIGroupsConfigJson.isNotEmpty()) {
            allGroups.addAll(ConfigManager.jsonToGroupConfigList(googleAIGroupsConfigJson))
        }
        
        // 加载Anthropic AI模型配置
        val anthropicAIPrefs = getSharedPreferences("anthropic_ai_model_config", Context.MODE_PRIVATE)
        val anthropicAIGroupsConfigJson = anthropicAIPrefs.getString("groups_config", "") ?: ""
        if (anthropicAIGroupsConfigJson.isNotEmpty()) {
            allGroups.addAll(ConfigManager.jsonToGroupConfigList(anthropicAIGroupsConfigJson))
        }
        
        // 加载火山方舟模型配置
        val volcanoArkPrefs = getSharedPreferences("volcano_ark_model_config", Context.MODE_PRIVATE)
        val volcanoArkGroupsConfigJson = volcanoArkPrefs.getString("groups_config", "") ?: ""
        if (volcanoArkGroupsConfigJson.isNotEmpty()) {
            allGroups.addAll(ConfigManager.jsonToGroupConfigList(volcanoArkGroupsConfigJson))
        }
        
        // 加载阿里云百炼模型配置
        val alibabaCloudBailianPrefs = getSharedPreferences("alibaba_cloud_bailian_model_config", Context.MODE_PRIVATE)
        val alibabaCloudBailianGroupsConfigJson = alibabaCloudBailianPrefs.getString("groups_config", "") ?: ""
        if (alibabaCloudBailianGroupsConfigJson.isNotEmpty()) {
            allGroups.addAll(ConfigManager.jsonToGroupConfigList(alibabaCloudBailianGroupsConfigJson))
        }
        
        currentGroups = allGroups
        
        if (currentGroups.isNotEmpty()) {
            // 尝试从SharedPreferences加载当前选中的模型
            loadSelectedModel()
        }
    }
    
    private fun loadSelectedModel() {
        val selectedGroupId = sharedPreferences.getString("selected_group_id", "")
        val selectedModelId = sharedPreferences.getString("selected_model_id", "")
        
        if (!selectedGroupId.isNullOrEmpty() && !selectedModelId.isNullOrEmpty()) {
            currentGroups.forEach { group ->
                if (group.id == selectedGroupId) {
                    group.models.forEach { model ->
                        if (model.id == selectedModelId) {
                            selectedGroup = group
                            selectedModel = model
                            return
                        }
                    }
                }
            }
        }
        
        // 如果没有找到保存的选择，自动选择第一个可用的
        if (selectedGroup == null && currentGroups.isNotEmpty()) {
            val firstGroup = currentGroups.first()
            if (firstGroup.models.isNotEmpty()) {
                selectedGroup = firstGroup
                selectedModel = firstGroup.models.first()
                saveSelectedModel()
            }
        }
    }
    
    private fun saveSelectedModel() {
        val editor = sharedPreferences.edit()
        if (selectedGroup != null && selectedModel != null) {
            editor.putString("selected_group_id", selectedGroup!!.id)
            editor.putString("selected_model_id", selectedModel!!.id)
        } else {
            editor.remove("selected_group_id")
            editor.remove("selected_model_id")
        }
        editor.apply()
    }
    
    private fun updateCurrentModelDisplay() {
        if (selectedGroup != null && selectedModel != null) {
            textCurrentModelName.text = selectedModel!!.name
            textCurrentModelProvider.text = selectedGroup!!.name
        } else {
            textCurrentModelName.text = "未选择模型"
            textCurrentModelProvider.text = "请选择AI模型"
        }
    }
    
    private fun toggleModelGroups() {
        if (currentGroups.isEmpty()) {
            Toast.makeText(this, "没有可用的AI模型配置", Toast.LENGTH_SHORT).show()
            return
        }
        
        isModelGroupsExpanded = !isModelGroupsExpanded
        
        if (isModelGroupsExpanded) {
            showModelGroups()
        } else {
            hideModelGroups()
        }
    }
    
    private fun showModelGroups() {
        layoutModelGroups.removeAllViews()
        layoutModelGroups.visibility = View.VISIBLE
        
        currentGroups.forEach { group ->
            if (group.models.isNotEmpty()) {
                addGroupToLayout(group)
            }
        }
    }
    
    private fun hideModelGroups() {
        layoutModelGroups.visibility = View.GONE
        layoutModelGroups.removeAllViews()
    }
    
    private fun addGroupToLayout(group: GroupConfig) {
        val groupView = LayoutInflater.from(this).inflate(R.layout.item_model_group, layoutModelGroups, false)
        
        val textGroupName = groupView.findViewById<TextView>(R.id.textGroupName)
        val layoutModels = groupView.findViewById<LinearLayout>(R.id.layoutModels)
        
        textGroupName.text = group.name
        
        // 添加该分组下的所有模型
        group.models.forEach { model ->
            addModelToGroup(model, group, layoutModels)
        }
        
        layoutModelGroups.addView(groupView)
    }
    
    private fun addModelToGroup(model: ModelConfig, group: GroupConfig, container: LinearLayout) {
        val modelView = LayoutInflater.from(this).inflate(R.layout.item_model_selection, container, false)
        
        val textModelName = modelView.findViewById<TextView>(R.id.textModelName)
        val textModelDescription = modelView.findViewById<TextView>(R.id.textModelDescription)
        
        textModelName.text = model.name
        textModelDescription.text = if (model.description.isNotEmpty()) model.description else "AI模型"
        
        // 设置选中状态
        val isSelected = selectedGroup?.id == group.id && selectedModel?.id == model.id
        modelView.isSelected = isSelected
        
        // 设置点击事件
        modelView.setOnClickListener {
            selectedGroup = group
            selectedModel = model
            saveSelectedModel()
            updateCurrentModelDisplay()
            hideModelGroups()
            isModelGroupsExpanded = false
            
            Toast.makeText(this, "已选择: ${group.name} - ${model.name}", Toast.LENGTH_SHORT).show()
        }
        
        container.addView(modelView)
    }
    
    override fun onResume() {
        super.onResume()
        // 重新加载配置，以防从OpenAI模型配置页面返回后配置发生变化
        loadModelConfiguration()
        updateCurrentModelDisplay()
    }
}