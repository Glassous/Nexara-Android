package com.glassous.gsrobot.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * 图片下载和存储管理器
 * 负责下载网络图片并保存到本地存储
 */
class ImageDownloadManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageDownloadManager"
        private const val IMAGES_FOLDER = "generated_images"
    }
    
    private val client = OkHttpClient()
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    /**
     * 获取图片存储目录
     */
    private fun getImagesDirectory(): File {
        val imagesDir = File(context.filesDir, IMAGES_FOLDER)
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        return imagesDir
    }
    
    /**
     * 生成唯一的图片文件名
     */
    private fun generateImageFileName(): String {
        val timestamp = dateFormat.format(Date())
        return "img_${timestamp}_${UUID.randomUUID().toString().substring(0, 8)}.jpg"
    }
    
    /**
     * 下载网络图片并保存到本地
     * @param imageUrl 图片URL
     * @return 本地文件路径，失败时返回null
     */
    suspend fun downloadAndSaveImage(imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to download image from: $imageUrl")
            
            val request = Request.Builder()
                .url(imageUrl)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download image: HTTP ${response.code}")
                return@withContext null
            }
            
            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                Log.e(TAG, "Response body is null")
                return@withContext null
            }
            
            // 创建本地文件
            val imagesDir = getImagesDirectory()
            val fileName = generateImageFileName()
            val localFile = File(imagesDir, fileName)
            
            // 保存图片到本地文件
            val outputStream = FileOutputStream(localFile)
            inputStream.copyTo(outputStream)
            outputStream.close()
            inputStream.close()
            
            Log.d(TAG, "Image saved successfully to: ${localFile.absolutePath}")
            return@withContext localFile.absolutePath
            
        } catch (e: IOException) {
            Log.e(TAG, "IO error while downloading image: ${e.message}")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while downloading image: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * 保存Base64编码的图片到本地
     * @param base64Data Base64编码的图片数据
     * @return 本地文件路径，失败时返回null
     */
    suspend fun saveBase64Image(base64Data: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to save Base64 image")
            
            // 解析Base64数据
            val base64String = if (base64Data.startsWith("data:")) {
                // 移除data URL前缀
                val commaIndex = base64Data.indexOf(",")
                if (commaIndex != -1) {
                    base64Data.substring(commaIndex + 1)
                } else {
                    base64Data
                }
            } else {
                base64Data
            }
            
            // 解码Base64
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode Base64 image")
                return@withContext null
            }
            
            // 创建本地文件
            val imagesDir = getImagesDirectory()
            val fileName = generateImageFileName()
            val localFile = File(imagesDir, fileName)
            
            // 保存bitmap到本地文件
            val outputStream = FileOutputStream(localFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
            
            Log.d(TAG, "Base64 image saved successfully to: ${localFile.absolutePath}")
            return@withContext localFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error while saving Base64 image: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * 检查本地图片文件是否存在
     * @param filePath 文件路径
     * @return 文件是否存在
     */
    fun isImageFileExists(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            file.exists() && file.isFile()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 删除本地图片文件
     * @param filePath 文件路径
     * @return 是否删除成功
     */
    fun deleteImageFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting image file: ${e.message}")
            false
        }
    }
    
    /**
     * 获取图片存储目录的大小（字节）
     */
    fun getImageStorageSize(): Long {
        return try {
            val imagesDir = getImagesDirectory()
            var totalSize = 0L
            imagesDir.listFiles()?.forEach { file ->
                if (file.isFile()) {
                    totalSize += file.length()
                }
            }
            totalSize
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * 清理所有存储的图片
     */
    fun clearAllImages(): Boolean {
        return try {
            val imagesDir = getImagesDirectory()
            var success = true
            imagesDir.listFiles()?.forEach { file ->
                if (file.isFile() && !file.delete()) {
                    success = false
                }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all images: ${e.message}")
            false
        }
    }
}