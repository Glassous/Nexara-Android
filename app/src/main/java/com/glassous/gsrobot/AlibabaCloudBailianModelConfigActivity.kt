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

class AlibabaCloudBailianModelConfigActivity : AppCompatActivity() {
    
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
        const val PREFS_NAME = "alibaba_cloud_bailian_model_config"
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
        setContentView(R.layout.activity_alibaba_cloud_bailian_model_config)
        
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
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupAddGroupButton()
        setupGlobalParametersUI()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "阿里云百炼模型配置"
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
        // 设置温度控制
        checkboxTemperatureEnabled.setOnCheckedChangeListener { _, isChecked ->
            sliderTemperature.isEnabled = isChecked
            updateTemperatureValue()
            markAsChanged()
        }
        
        sliderTemperature.addOnChangeListener { _, value, _ ->
            updateTemperatureValue()
            markAsChanged()
        }
        
        // 设置最大Token数控制
        checkboxMaxTokensEnabled.setOnCheckedChangeListener { _, isChecked ->
            sliderMaxTokens.isEnabled = isChecked
            updateMaxTokensValue()
            markAsChanged()
        }
        
        sliderMaxTokens.addOnChangeListener { _, value, _ ->
            updateMaxTokensValue()
            markAsChanged()
        }
        
        // 初始化数值显示
        updateTemperatureValue()
        updateMaxTokensValue()
    }
    
    private fun updateTemperatureValue() {
        textTemperatureValue.text = "${sliderTemperature.value.toInt()}%"
    }
    
    private fun updateMaxTokensValue() {
        textMaxTokensValue.text = "${sliderMaxTokens.value.toInt()}%"
    }
    
    private fun loadConfiguration() {
        // 加载分组配置
        val groupsConfigJson = sharedPreferences.getString(KEY_GROUPS_CONFIG, "") ?: ""
        if (groupsConfigJson.isNotEmpty()) {
            currentGroups = ConfigManager.jsonToGroupConfigList(groupsConfigJson).toMutableList()
        }
        
        // 加载全局参数设置
        loadGlobalParameters()
        
        refreshGroupsList()
        
        // 重置未保存状态
        hasUnsavedChanges = false
    }
    
    private fun loadGlobalParameters() {
        // 加载温度设置
        val temperatureEnabled = sharedPreferences.getBoolean(KEY_TEMPERATURE_ENABLED, false)
        val temperatureValue = sharedPreferences.getFloat(KEY_TEMPERATURE_VALUE, 50f)
        
        checkboxTemperatureEnabled.isChecked = temperatureEnabled
        sliderTemperature.value = temperatureValue
        sliderTemperature.isEnabled = temperatureEnabled
        
        // 加载最大Token数设置
        val maxTokensEnabled = sharedPreferences.getBoolean(KEY_MAX_TOKENS_ENABLED, false)
        val maxTokensValue = sharedPreferences.getFloat(KEY_MAX_TOKENS_VALUE, 100f)
        
        checkboxMaxTokensEnabled.isChecked = maxTokensEnabled
        sliderMaxTokens.value = maxTokensValue
        sliderMaxTokens.isEnabled = maxTokensEnabled
        
        // 更新显示值
        updateTemperatureValue()
        updateMaxTokensValue()
    }
    
    private fun setupAddGroupButton() {
        buttonAddGroup.setOnClickListener {
            addNewGroup()
            markAsChanged()
        }
    }
    
    private fun addNewGroup() {
        val newGroup = GroupConfig(
            id = UUID.randomUUID().toString(),
            name = "",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            apiKey = ""
        )
        
        currentGroups.add(newGroup)
        refreshGroupsList()
    }
    
    private fun setupSaveButton() {
        buttonSave.setOnClickListener {
            saveConfiguration()
        }
    }
    
    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges) {
                    showUnsavedChangesDialog()
                } else {
                    finish()
                }
            }
        })
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
    
    private fun refreshGroupsList() {
        layoutGroupsList.removeAllViews()
        
        currentGroups.forEachIndexed { index, group ->
            val groupView = createGroupView(group, index)
            layoutGroupsList.addView(groupView)
        }
    }
    
    private fun createGroupView(group: GroupConfig, index: Int): View {
        val inflater = LayoutInflater.from(this)
        val groupView = inflater.inflate(R.layout.item_group_config, layoutGroupsList, false)
        
        val textGroupName = groupView.findViewById<TextView>(R.id.textGroupName)
        val buttonEditGroup = groupView.findViewById<MaterialButton>(R.id.buttonEditGroup)
        val layoutGroupInfo = groupView.findViewById<LinearLayout>(R.id.layoutGroupInfo)
        val editTextGroupName = groupView.findViewById<TextInputEditText>(R.id.editTextGroupName)
        val editTextBaseUrl = groupView.findViewById<TextInputEditText>(R.id.editTextBaseUrl)
        val editTextApiKey = groupView.findViewById<TextInputEditText>(R.id.editTextApiKey)
        val buttonCancelEdit = groupView.findViewById<MaterialButton>(R.id.buttonCancelEdit)
        val buttonSaveGroup = groupView.findViewById<MaterialButton>(R.id.buttonSaveGroup)
        val buttonDeleteGroup = groupView.findViewById<MaterialButton>(R.id.buttonDeleteGroup)
        val layoutModelsList = groupView.findViewById<LinearLayout>(R.id.layoutModelsList)
        val buttonAddModel = groupView.findViewById<MaterialButton>(R.id.buttonAddModel)
        
        // 设置分组显示名称
        textGroupName.text = if (group.name.isNotEmpty()) group.name else "新分组"
        
        // 设置分组信息
        editTextGroupName.setText(group.name)
        editTextBaseUrl.setText(group.baseUrl)
        editTextApiKey.setText(group.apiKey)
        
        // 编辑按钮点击事件
        buttonEditGroup.setOnClickListener {
            layoutGroupInfo.visibility = View.VISIBLE
            buttonEditGroup.visibility = View.GONE
        }
        
        // 取消编辑按钮
        buttonCancelEdit.setOnClickListener {
            layoutGroupInfo.visibility = View.GONE
            buttonEditGroup.visibility = View.VISIBLE
            // 恢复原始值
            editTextGroupName.setText(group.name)
            editTextBaseUrl.setText(group.baseUrl)
            editTextApiKey.setText(group.apiKey)
        }
        
        // 保存分组按钮
        buttonSaveGroup.setOnClickListener {
            group.name = editTextGroupName.text.toString()
            group.baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1" // 固定Base URL
            group.apiKey = editTextApiKey.text.toString()
            textGroupName.text = if (group.name.isNotEmpty()) group.name else "新分组"
            layoutGroupInfo.visibility = View.GONE
            buttonEditGroup.visibility = View.VISIBLE
            markAsChanged()
        }
        
        // 删除分组按钮
        buttonDeleteGroup.setOnClickListener {
            currentGroups.removeAt(index)
            refreshGroupsList()
            markAsChanged()
        }
        
        // 添加模型按钮
        buttonAddModel.setOnClickListener {
            val newModel = ModelConfig(
                id = UUID.randomUUID().toString(),
                name = "",
                description = ""
            )
            group.models.add(newModel)
            refreshGroupsList()
            markAsChanged()
        }
        
        // 显示模型列表
        group.models.forEachIndexed { modelIndex, model ->
            val modelView = createModelView(model, group, modelIndex)
            layoutModelsList.addView(modelView)
        }
        
        return groupView
    }
    
    private fun createModelView(model: ModelConfig, group: GroupConfig, modelIndex: Int): View {
        val inflater = LayoutInflater.from(this)
        val modelView = inflater.inflate(R.layout.item_model_config, null, false)
        
        val layoutModelDisplay = modelView.findViewById<LinearLayout>(R.id.layoutModelDisplay)
        val layoutModelEdit = modelView.findViewById<LinearLayout>(R.id.layoutModelEdit)
        val textModelName = modelView.findViewById<TextView>(R.id.textModelName)
        val textModelDescription = modelView.findViewById<TextView>(R.id.textModelDescription)
        val buttonEditModel = modelView.findViewById<MaterialButton>(R.id.buttonEditModel)
        val buttonDeleteModel = modelView.findViewById<MaterialButton>(R.id.buttonDeleteModel)
        val editTextModelName = modelView.findViewById<TextInputEditText>(R.id.editTextModelName)
        val editTextModelDescription = modelView.findViewById<TextInputEditText>(R.id.editTextModelDescription)
        val buttonCancelModelEdit = modelView.findViewById<MaterialButton>(R.id.buttonCancelModelEdit)
        val buttonSaveModel = modelView.findViewById<MaterialButton>(R.id.buttonSaveModel)
        
        // 设置模型显示信息
        textModelName.text = if (model.name.isNotEmpty()) model.name else "新模型"
        textModelDescription.text = model.description
        textModelDescription.visibility = if (model.description.isNotEmpty()) View.VISIBLE else View.GONE
        
        // 设置模型编辑信息
        editTextModelName.setText(model.name)
        editTextModelDescription.setText(model.description)
        
        // 编辑按钮点击事件
        buttonEditModel.setOnClickListener {
            layoutModelDisplay.visibility = View.GONE
            layoutModelEdit.visibility = View.VISIBLE
        }
        
        // 取消编辑按钮
        buttonCancelModelEdit.setOnClickListener {
            layoutModelDisplay.visibility = View.VISIBLE
            layoutModelEdit.visibility = View.GONE
            // 恢复原始值
            editTextModelName.setText(model.name)
            editTextModelDescription.setText(model.description)
        }
        
        // 保存模型按钮
        buttonSaveModel.setOnClickListener {
            model.name = editTextModelName.text.toString()
            model.description = editTextModelDescription.text.toString()
            textModelName.text = if (model.name.isNotEmpty()) model.name else "新模型"
            textModelDescription.text = model.description
            textModelDescription.visibility = if (model.description.isNotEmpty()) View.VISIBLE else View.GONE
            layoutModelDisplay.visibility = View.VISIBLE
            layoutModelEdit.visibility = View.GONE
            markAsChanged()
        }
        
        // 删除模型按钮
        buttonDeleteModel.setOnClickListener {
            group.models.removeAt(modelIndex)
            refreshGroupsList()
            markAsChanged()
        }
        
        return modelView
    }
    
    private fun saveConfiguration() {
        // 保存分组配置
        val groupsConfigJson = ConfigManager.groupConfigListToJson(currentGroups)
        sharedPreferences.edit().apply {
            putString(KEY_GROUPS_CONFIG, groupsConfigJson)
            
            // 保存全局参数设置
            putBoolean(KEY_STREAMING, true) // 默认启用流式输出
            putBoolean(KEY_TEMPERATURE_ENABLED, checkboxTemperatureEnabled.isChecked)
            putFloat(KEY_TEMPERATURE_VALUE, sliderTemperature.value)
            putBoolean(KEY_MAX_TOKENS_ENABLED, checkboxMaxTokensEnabled.isChecked)
            putFloat(KEY_MAX_TOKENS_VALUE, sliderMaxTokens.value)
            
            apply()
        }
        
        hasUnsavedChanges = false
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}