package com.bedrock3d.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    
    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "").lowercase()
    }
    
    fun getFileNameWithoutExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0) fileName.substring(0, lastDot) else fileName
    }
    
    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
    }
    
    fun copyUriToFile(context: Context, uri: Uri, outputFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
    }
    
    fun readFileBytes(context: Context, uri: Uri): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        }
    }
    
    fun readFileText(context: Context, uri: Uri): String? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().readText()
        }
    }
}

object TextureUtils {
    
    fun loadTexture(context: Context, uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }
    
    fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        var width = bitmap.width
        var height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        val ratio = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        width = (width * ratio).toInt()
        height = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
    
    fun bitmapToPngBytes(bitmap: Bitmap, quality: Int = 100): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, stream)
        return stream.toByteArray()
    }
    
    fun makePowerOfTwo(width: Int, height: Int): Pair<Int, Int> {
        fun nextPowerOfTwo(n: Int): Int {
            var p = 1
            while (p < n) p *= 2
            return p
        }
        
        return Pair(nextPowerOfTwo(width), nextPowerOfTwo(height))
    }
}
