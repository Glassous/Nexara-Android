package com.glassous.gsrobot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.slider.Slider

class ChatDetailActivity : AppCompatActivity() {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var textViewContent: TextView
    private lateinit var fontSizeSlider: Slider
    
    companion object {
        const val EXTRA_MESSAGE_CONTENT = "message_content"
        const val EXTRA_IS_FROM_USER = "is_from_user"
        const val EXTRA_TIMESTAMP = "timestamp"
        
        fun createIntent(context: Context, message: ChatMessage): Intent {
            return Intent(context, ChatDetailActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE_CONTENT, message.content)
                putExtra(EXTRA_IS_FROM_USER, message.isFromUser)
                putExtra(EXTRA_TIMESTAMP, message.timestamp)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置透明状态栏和导航栏
        setupTransparentSystemBars()
        
        setContentView(R.layout.activity_chat_detail)
        
        initViews()
        setupToolbar()
        loadMessageContent()
        setupFontSizeSlider()
    }
    
    private fun setupTransparentSystemBars() {
        // 强制启用边到边显示，禁用系统窗口适配
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 设置状态栏和导航栏完全透明
        window.apply {
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            
            // 强制禁用系统栏对比度增强和状态栏对比度增强
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
                isStatusBarContrastEnforced = false
            }
            
            // 为新版本Android设置更强制的透明模式
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // 再次确保不适配系统窗口
                setDecorFitsSystemWindows(false)
                // 设置系统栏行为，确保透明
                insetsController?.let { controller ->
                    controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                // 为旧版本Android设置更全面的系统UI标志
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
            }
        }
        
        // 强制设置系统栏外观为透明
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false
        
        // 额外的透明度强制设置
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
            }
        }
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        textViewContent = findViewById(R.id.textViewContent)
        fontSizeSlider = findViewById(R.id.fontSizeSlider)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            // 不设置标题，保持简洁
            title = ""
        }
    }
    
    private fun loadMessageContent() {
        val content = intent.getStringExtra(EXTRA_MESSAGE_CONTENT) ?: ""
        
        // 设置内容
        textViewContent.text = content
    }
    
    private fun setupFontSizeSlider() {
        // 设置滑动条范围：12sp到24sp
        fontSizeSlider.valueFrom = 12f
        fontSizeSlider.valueTo = 24f
        fontSizeSlider.value = 16f // 默认字号
        fontSizeSlider.stepSize = 1f
        
        // 监听滑动条变化
        fontSizeSlider.addOnChangeListener { _, value, _ ->
            textViewContent.textSize = value
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}