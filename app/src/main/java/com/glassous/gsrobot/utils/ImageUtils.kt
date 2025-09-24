package com.glassous.gsrobot.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val MAX_IMAGE_SIZE = 1024 // 最大图片尺寸
    private const val JPEG_QUALITY = 85 // JPEG压缩质量

    /**
     * 将Uri转换为Base64编码的数据URL
     * @param context 上下文
     * @param uri 图片Uri
     * @return Base64编码的数据URL，格式为 "data:image/jpeg;base64,..."
     */
    fun uriToBase64DataUrl(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                // 读取原始图片
                val originalBitmap = BitmapFactory.decodeStream(stream)
                
                // 压缩图片
                val compressedBitmap = compressBitmap(originalBitmap)
                
                // 转换为Base64
                val base64String = bitmapToBase64(compressedBitmap)
                
                // 返回数据URL格式
                "data:image/jpeg;base64,$base64String"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting URI to Base64", e)
            null
        }
    }

    /**
     * 压缩Bitmap以减少文件大小
     */
    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // 如果图片尺寸超过最大限制，进行缩放
        if (width > MAX_IMAGE_SIZE || height > MAX_IMAGE_SIZE) {
            val ratio = minOf(
                MAX_IMAGE_SIZE.toFloat() / width,
                MAX_IMAGE_SIZE.toFloat() / height
            )
            
            val newWidth = (width * ratio).toInt()
            val newHeight = (height * ratio).toInt()
            
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
        
        return bitmap
    }

    /**
     * 将Bitmap转换为Base64字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}