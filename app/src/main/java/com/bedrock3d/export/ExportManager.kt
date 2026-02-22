package com.bedrock3d.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.bedrock3d.model.Model3D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ExportManager(private val context: Context) {
    
    private val mcaddonBuilder = McaddonBuilder()
    
    suspend fun exportModel(
        model: Model3D,
        scale: Float,
        namespace: String = "custom",
        textureUri: Uri? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        
        val outputDir = getExportDirectory()
        outputDir.mkdirs()
        
        val outputFile = File(outputDir, "${model.name}.mcaddon")
        
        val texturePath = textureUri?.let { uri ->
            copyTextureToCache(uri, model)
        }
        
        mcaddonBuilder.build(
            model = model,
            scale = scale,
            outputPath = outputFile.absolutePath,
            namespace = namespace,
            texturePath = texturePath
        )
    }
    
    private fun getExportDirectory(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, "Bedrock3D")
    }
    
    private fun copyTextureToCache(uri: Uri, model: Model3D): String {
        val cacheDir = File(context.cacheDir, "textures")
        cacheDir.mkdirs()
        
        val outputFile = File(cacheDir, "${model.name}_texture.png")
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        return outputFile.absolutePath
    }
    
    fun shareMcaddon(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Share .mcaddon file"))
    }
    
    fun openInMinecraft(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            shareMcaddon(file)
        }
    }
    
    companion object {
        val SCALE_OPTIONS = listOf(
            ScaleOption("0.25x", 0.25f, "Очень маленький"),
            ScaleOption("0.5x", 0.5f, "Маленький"),
            ScaleOption("1x (По умолчанию)", 1.0f, "Оригинальный размер"),
            ScaleOption("2x", 2.0f, "Большой"),
            ScaleOption("4x", 4.0f, "Очень большой"),
            ScaleOption("Пользовательский", -1f, "Введите своё значение")
        )
    }
}

data class ScaleOption(
    val label: String,
    val scale: Float,
    val description: String
)
