package com.glassous.gsrobot

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.glassous.gsrobot.data.ConfigManager
import com.glassous.gsrobot.data.GroupConfig
import com.glassous.gsrobot.data.ModelConfig
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

class VolcanoArkModelConfigActivity : AppCompatActivity() {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var layoutGroupsConfig: LinearLayout
    private lateinit var layoutGroupsList: LinearLayout
    private lateinit var buttonAddGroup: MaterialButton
    private lateinit var buttonSave: MaterialButton
    private lateinit var sharedPreferences: SharedPreferences
    
    // 全局参数设置UI组件
    private lateinit var checkboxTemperatureEnabled: MaterialCheckBox
    private lateinit var sliderTemperature: Slider
    private lateinit var textTemperatureValue: TextView
    private lateinit var checkboxMaxTokensEnabled: MaterialCheckBox
    private lateinit var sliderMaxTokens: Slider
    private lateinit var textMaxTokensValue: TextView
    
    private var currentGroups: MutableList<GroupConfig> = mutableListOf()
    private var hasUnsavedChanges = false
    
    companion object {
        const val PREFS_NAME = "volcano_ark_model_config"
        const val KEY_GROUPS_CONFIG = "groups_config"
        const val KEY_STREAMING = "streaming"
        const val KEY_TEMPERATURE_ENABLED = "temperature_enabled"
        const val KEY_TEMPERATURE_VALUE = "temperature_value"
        const val KEY_MAX_TOKENS_ENABLED = "max_tokens_enabled"
        const val KEY_MAX_TOKENS_VALUE = "max_tokens_value"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_volcano_ark_model_config)
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        initViews()
        setupToolbar()
        setupWindowInsets()
        loadConfiguration()
        setupSaveButton()
        setupBackPressedCallback()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        layoutGroupsConfig = findViewById(R.id.layoutGroupsConfig)
        layoutGroupsList = findViewById(R.id.layoutGroupsList)
        buttonAddGroup = findViewById(R.id.buttonAddGroup)
        buttonSave = findViewById(R.id.buttonSave)
        
        // 全局参数设置UI组件
        checkboxTemperatureEnabled = findViewById(R.id.checkboxTemperatureEnabled)
        sliderTemperature = findViewById(R.id.sliderTemperature)
        textTemperatureValue = findViewById(R.id.textTemperatureValue)
        checkboxMaxTokensEnabled = findViewById(R.id.checkboxMaxTokensEnabled)
        sliderMaxTokens = findViewById(R.id.sliderMaxTokens)
        textMaxTokensValue = findViewById(R.id.textMaxTokensValue)
        
        setupAddGroupButton()
        setupGlobalParametersUI()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "火山方舟模型配置"
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
            val statusBarSpacer = findViewById<View>(R.id.statusBarSpacer)
            val layoutParams = statusBarSpacer.layoutParams
            layoutParams.height = systemBars.top
            statusBarSpacer.layoutParams = layoutParams
            
            insets
        }
    }
    
    private fun markAsChanged() {
        hasUnsavedChanges = true
    }
    
    private fun setupGlobalParametersUI() {
        // 温度设置
        checkboxTemperatureEnabled.setOnCheckedChangeListener { _, isChecked ->
            sliderTemperature.isEnabled = isChecked
            markAsChanged()
        }
        
        sliderTemperature.addOnChangeListener { _, value, _ ->
            updateTemperatureValue()
            markAsChanged()
        }
        
        // 最大令牌数设置
        checkboxMaxTokensEnabled.setOnCheckedChangeListener { _, isChecked ->
            sliderMaxTokens.isEnabled = isChecked
            markAsChanged()
        }
        
        sliderMaxTokens.addOnChangeListener { _, value, _ ->
            updateMaxTokensValue()
            markAsChanged()
        }
    }
    
    private fun updateTemperatureValue() {
        val percentage = if (checkboxTemperatureEnabled.isChecked) {
            sliderTemperature.value
        } else {
            50f // 默认50%
        }
        textTemperatureValue.text = "${percentage.toInt()}%"
    }
    
    private fun updateMaxTokensValue() {
        val percentage = if (checkboxMaxTokensEnabled.isChecked) {
            sliderMaxTokens.value
        } else {
            100f // 默认100%
        }
        textMaxTokensValue.text = "${percentage.toInt()}%"
    }
    
    private fun loadConfiguration() {
        // 加载分组配置
        val groupsJson = sharedPreferences.getString(KEY_GROUPS_CONFIG, null)
        if (groupsJson != null) {
            currentGroups = ConfigManager.jsonToGroupConfigList(groupsJson).toMutableList()
        }
        
        loadGlobalParameters()
        refreshGroupsList()
    }
    
    private fun loadGlobalParameters() {
        // 加载全局参数
        val temperatureEnabled = sharedPreferences.getBoolean(KEY_TEMPERATURE_ENABLED, false)
        val temperatureValue = sharedPreferences.getFloat(KEY_TEMPERATURE_VALUE, 50f) // 默认50%
        val maxTokensEnabled = sharedPreferences.getBoolean(KEY_MAX_TOKENS_ENABLED, false)
        val maxTokensValue = sharedPreferences.getFloat(KEY_MAX_TOKENS_VALUE, 100f) // 默认100%
        
        checkboxTemperatureEnabled.isChecked = temperatureEnabled
        sliderTemperature.value = temperatureValue
        sliderTemperature.isEnabled = temperatureEnabled
        updateTemperatureValue()
        
        checkboxMaxTokensEnabled.isChecked = maxTokensEnabled
        sliderMaxTokens.value = maxTokensValue
        sliderMaxTokens.isEnabled = maxTokensEnabled
        updateMaxTokensValue()
    }
    
    private fun setupAddGroupButton() {
        buttonAddGroup.setOnClickListener {
            addNewGroup()
        }
    }
    
    private fun addNewGroup() {
        val newGroup = GroupConfig(
            id = UUID.randomUUID().toString(),
            name = "",
            apiKey = "",
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            models = mutableListOf()
        )
        currentGroups.add(newGroup)
        refreshGroupsList()
        markAsChanged()
    }
    
    private fun setupSaveButton() {
        buttonSave.setOnClickListener {
            saveConfiguration()
        }
    }
    
    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPressed()
            }
        })
    }
    
    private fun handleBackPressed() {
        if (hasUnsavedChanges) {
            showUnsavedChangesDialog()
        } else {
            finish()
        }
    }
    
    private fun refreshGroupsList() {
        layoutGroupsList.removeAllViews()
        for (group in currentGroups) {
            addGroupView(group)
        }
    }
    
    private fun addGroupView(group: GroupConfig) {
        val groupView = LayoutInflater.from(this).inflate(R.layout.item_group_config, layoutGroupsList, false)
        
        val textGroupName = groupView.findViewById<TextView>(R.id.textGroupName)
        val buttonEditGroup = groupView.findViewById<MaterialButton>(R.id.buttonEditGroup)
        val buttonDeleteGroup = groupView.findViewById<MaterialButton>(R.id.buttonDeleteGroup)
        val layoutGroupInfo = groupView.findViewById<LinearLayout>(R.id.layoutGroupInfo)
        val editTextGroupName = groupView.findViewById<TextInputEditText>(R.id.editTextGroupName)
        val editTextBaseUrl = groupView.findViewById<TextInputEditText>(R.id.editTextBaseUrl)
        val editTextApiKey = groupView.findViewById<TextInputEditText>(R.id.editTextApiKey)
        val buttonSaveGroup = groupView.findViewById<MaterialButton>(R.id.buttonSaveGroup)
        val buttonCancelEdit = groupView.findViewById<MaterialButton>(R.id.buttonCancelEdit)
        val buttonAddModel = groupView.findViewById<MaterialButton>(R.id.buttonAddModel)
        val layoutModelsList = groupView.findViewById<LinearLayout>(R.id.layoutModelsList)
        
        // 隐藏 Base URL 字段
        val textInputLayoutBaseUrl = groupView.findViewById<View>(R.id.textInputLayoutBaseUrl)
        textInputLayoutBaseUrl.visibility = View.GONE
        
        // 设置分组信息
        textGroupName.text = group.name
        editTextGroupName.setText(group.name)
        editTextBaseUrl.setText("https://ark.cn-beijing.volces.com/api/v3")
        editTextApiKey.setText(group.apiKey)
        
        // 确保使用固定的 base URL
        group.baseUrl = "https://ark.cn-beijing.volces.com/api/v3"
        
        // 编辑按钮
        buttonEditGroup.setOnClickListener {
            layoutGroupInfo.visibility = View.VISIBLE
            buttonEditGroup.visibility = View.GONE
            buttonDeleteGroup.visibility = View.GONE
        }
        
        // 删除按钮
        buttonDeleteGroup.setOnClickListener {
            currentGroups.remove(group)
            refreshGroupsList()
            markAsChanged()
        }
        
        // 保存分组
        buttonSaveGroup.setOnClickListener {
            val name = editTextGroupName.text.toString().trim()
            val apiKey = editTextApiKey.text.toString().trim()
            
            if (name.isEmpty()) {
                Toast.makeText(this, "请填写分组名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            group.name = name
            group.baseUrl = "https://ark.cn-beijing.volces.com/api/v3"
            group.apiKey = apiKey
            
            layoutGroupInfo.visibility = View.GONE
            buttonEditGroup.visibility = View.VISIBLE
            buttonDeleteGroup.visibility = View.VISIBLE
            textGroupName.text = name
            markAsChanged()
        }
        
        // 取消编辑
        buttonCancelEdit.setOnClickListener {
            // 恢复原始值
            editTextGroupName.setText(group.name)
            editTextBaseUrl.setText(group.baseUrl)
            editTextApiKey.setText(group.apiKey)
            
            layoutGroupInfo.visibility = View.GONE
            buttonEditGroup.visibility = View.VISIBLE
            buttonDeleteGroup.visibility = View.VISIBLE
        }
        
        // 添加模型按钮
        buttonAddModel.setOnClickListener {
            addNewModel(group, layoutModelsList)
        }
        
        // 添加现有模型
        for (model in group.models) {
            addModelView(model, group, layoutModelsList)
        }
        
        layoutGroupsList.addView(groupView)
    }
    
    private fun addNewModel(group: GroupConfig, modelsContainer: LinearLayout) {
        val newModel = ModelConfig(
            id = UUID.randomUUID().toString(),
            name = "",
            description = ""
        )
        group.models.add(newModel)
        refreshModelsList(group, modelsContainer)
        markAsChanged()
    }
    
    private fun refreshModelsList(group: GroupConfig, modelsContainer: LinearLayout) {
        modelsContainer.removeAllViews()
        for (model in group.models) {
            addModelView(model, group, modelsContainer)
        }
    }
    
    private fun addModelView(model: ModelConfig, group: GroupConfig, modelsContainer: LinearLayout) {
        val modelView = LayoutInflater.from(this).inflate(R.layout.item_model_config, modelsContainer, false)
        
        val layoutModelDisplay = modelView.findViewById<LinearLayout>(R.id.layoutModelDisplay)
        val layoutModelEdit = modelView.findViewById<LinearLayout>(R.id.layoutModelEdit)
        val textModelName = modelView.findViewById<TextView>(R.id.textModelName)
        val textModelDescription = modelView.findViewById<TextView>(R.id.textModelDescription)
        val buttonEditModel = modelView.findViewById<MaterialButton>(R.id.buttonEditModel)
        val buttonDeleteModel = modelView.findViewById<MaterialButton>(R.id.buttonDeleteModel)
        val editTextModelName = modelView.findViewById<TextInputEditText>(R.id.editTextModelName)
        val editTextModelDescription = modelView.findViewById<TextInputEditText>(R.id.editTextModelDescription)
        val buttonSaveModel = modelView.findViewById<MaterialButton>(R.id.buttonSaveModel)
        val buttonCancelModelEdit = modelView.findViewById<MaterialButton>(R.id.buttonCancelModelEdit)
        
        // 设置模型信息
        textModelName.text = model.name
        if (model.description.isNotEmpty()) {
            textModelDescription.text = model.description
            textModelDescription.visibility = View.VISIBLE
        }
        editTextModelName.setText(model.name)
        editTextModelDescription.setText(model.description)
        
        // 编辑模型
        buttonEditModel.setOnClickListener {
            layoutModelDisplay.visibility = View.GONE
            layoutModelEdit.visibility = View.VISIBLE
        }
        
        // 删除模型
        buttonDeleteModel.setOnClickListener {
            group.models.remove(model)
            refreshModelsList(group, modelsContainer)
            markAsChanged()
        }
        
        // 保存模型
        buttonSaveModel.setOnClickListener {
            val name = editTextModelName.text.toString().trim()
            val description = editTextModelDescription.text.toString().trim()
            
            if (name.isEmpty()) {
                Toast.makeText(this, "模型名称不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            model.name = name
            model.description = description
            
            layoutModelDisplay.visibility = View.VISIBLE
            layoutModelEdit.visibility = View.GONE
            textModelName.text = name
            if (description.isNotEmpty()) {
                textModelDescription.text = description
                textModelDescription.visibility = View.VISIBLE
            } else {
                textModelDescription.visibility = View.GONE
            }
            markAsChanged()
        }
        
        // 取消编辑模型
        buttonCancelModelEdit.setOnClickListener {
            editTextModelName.setText(model.name)
            editTextModelDescription.setText(model.description)
            
            layoutModelDisplay.visibility = View.VISIBLE
            layoutModelEdit.visibility = View.GONE
        }
        
        modelsContainer.addView(modelView)
    }
    
    private fun saveConfiguration() {
        try {
            // 保存分组配置
            val groupsJson = ConfigManager.groupConfigListToJson(currentGroups)
            sharedPreferences.edit().apply {
                putString(KEY_GROUPS_CONFIG, groupsJson)
                
                // 保存全局参数
                putBoolean(KEY_TEMPERATURE_ENABLED, checkboxTemperatureEnabled.isChecked)
                putFloat(KEY_TEMPERATURE_VALUE, sliderTemperature.value)
                putBoolean(KEY_MAX_TOKENS_ENABLED, checkboxMaxTokensEnabled.isChecked)
                putFloat(KEY_MAX_TOKENS_VALUE, sliderMaxTokens.value)
                
                apply()
            }
            
            hasUnsavedChanges = false
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("未保存的更改")
            .setMessage("您有未保存的更改，是否要保存？")
            .setPositiveButton("保存") { _, _ ->
                saveConfiguration()
                finish()
            }
            .setNegativeButton("不保存") { _, _ ->
                finish()
            }
            .setNeutralButton("取消", null)
            .show()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}