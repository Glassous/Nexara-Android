package com.glassous.gsrobot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ChatDetailActivity : AppCompatActivity() {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var textViewContent: TextView
    private lateinit var fontSizeSlider: Slider
    private lateinit var imageViewPreview: ImageView
    private lateinit var buttonDownloadImage: MaterialButton
    
    companion object {
        const val EXTRA_MESSAGE_CONTENT = "message_content"
        const val EXTRA_IS_FROM_USER = "is_from_user"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_LOCAL_IMAGE_PATH = "local_image_path"
        
        fun createIntent(context: Context, message: ChatMessage): Intent {
            return Intent(context, ChatDetailActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE_CONTENT, message.content)
                putExtra(EXTRA_IS_FROM_USER, message.isFromUser)
                putExtra(EXTRA_TIMESTAMP, message.timestamp)
                putExtra(EXTRA_IMAGE_URI, message.imageUri)
                putExtra(EXTRA_LOCAL_IMAGE_PATH, message.localImagePath)
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
        setupImagePreview()
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
        imageViewPreview = findViewById(R.id.imageViewPreview)
        buttonDownloadImage = findViewById(R.id.buttonDownloadImage)
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
    
    private fun setupImagePreview() {
        val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)
        val localImagePath = intent.getStringExtra(EXTRA_LOCAL_IMAGE_PATH)
        
        if (!imageUri.isNullOrEmpty()) {
            try {
                // 优先使用本地图片路径
                if (!localImagePath.isNullOrEmpty() && File(localImagePath).exists()) {
                    // 使用本地图片文件
                    com.bumptech.glide.Glide.with(this)
                        .load(File(localImagePath))
                        .into(imageViewPreview)
                    imageViewPreview.visibility = View.VISIBLE
                    buttonDownloadImage.visibility = View.VISIBLE
                    
                    // 设置下载按钮点击事件
                    buttonDownloadImage.setOnClickListener {
                        saveImageToGallery(localImagePath)
                    }
                } else if (imageUri.startsWith("data:", ignoreCase = true)) {
                    // Base64编码的图片
                    val bitmap = decodeBase64ToBitmap(imageUri)
                    if (bitmap != null) {
                        imageViewPreview.setImageBitmap(bitmap)
                        imageViewPreview.visibility = View.VISIBLE
                        buttonDownloadImage.visibility = View.VISIBLE
                        
                        // 设置下载按钮点击事件
                        buttonDownloadImage.setOnClickListener {
                            saveBase64ImageToGallery(imageUri)
                        }
                    }
                } else if (imageUri.startsWith("http", ignoreCase = true)) {
                    // 网络图片URL - 使用Glide加载
                    com.bumptech.glide.Glide.with(this)
                        .load(imageUri)
                        .into(imageViewPreview)
                    imageViewPreview.visibility = View.VISIBLE
                    buttonDownloadImage.visibility = View.VISIBLE
                    
                    // 设置下载按钮点击事件
                    buttonDownloadImage.setOnClickListener {
                        downloadAndSaveNetworkImage(imageUri)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatDetailActivity", "Error loading image: ${e.message}")
                imageViewPreview.visibility = View.GONE
                buttonDownloadImage.visibility = View.GONE
            }
        } else {
            imageViewPreview.visibility = View.GONE
            buttonDownloadImage.visibility = View.GONE
        }
    }
    
    private fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        return try {
            // 移除data URL前缀
            val base64Data = if (base64String.contains(",")) {
                base64String.substring(base64String.indexOf(",") + 1)
            } else {
                base64String
            }
            
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e("ChatDetailActivity", "Error decoding base64 image: ${e.message}")
            null
        }
    }
    
    private fun saveImageToGallery(localImagePath: String) {
        try {
            val sourceFile = File(localImagePath)
            if (!sourceFile.exists()) {
                Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            
            val fileName = "GSRobot_${System.currentTimeMillis()}.jpg"
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GSRobot")
            }
            
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let { imageUri ->
                contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "保存图片失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("ChatDetailActivity", "Error saving image to gallery: ${e.message}")
            Toast.makeText(this, "保存图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveBase64ImageToGallery(base64String: String) {
        try {
            val bitmap = decodeBase64ToBitmap(base64String)
            if (bitmap == null) {
                Toast.makeText(this, "图片解码失败", Toast.LENGTH_SHORT).show()
                return
            }
            
            val fileName = "GSRobot_${System.currentTimeMillis()}.jpg"
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GSRobot")
            }
            
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let { imageUri ->
                contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }
                Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "保存图片失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("ChatDetailActivity", "Error saving base64 image to gallery: ${e.message}")
            Toast.makeText(this, "保存图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun downloadAndSaveNetworkImage(imageUrl: String) {
        // 使用Glide下载网络图片并保存
        com.bumptech.glide.Glide.with(this)
            .asBitmap()
            .load(imageUrl)
            .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?) {
                    try {
                        val fileName = "GSRobot_${System.currentTimeMillis()}.jpg"
                        val contentValues = android.content.ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GSRobot")
                        }
                        
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        uri?.let { imageUri ->
                            contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                                resource.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                            }
                            Toast.makeText(this@ChatDetailActivity, "图片已保存到相册", Toast.LENGTH_SHORT).show()
                        } ?: run {
                            Toast.makeText(this@ChatDetailActivity, "保存图片失败", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("ChatDetailActivity", "Error saving network image: ${e.message}")
                        Toast.makeText(this@ChatDetailActivity, "保存图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    // 清理资源
                }
                
                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    Toast.makeText(this@ChatDetailActivity, "下载图片失败", Toast.LENGTH_SHORT).show()
                }
            })
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