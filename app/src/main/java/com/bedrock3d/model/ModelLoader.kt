package com.bedrock3d.model

import android.content.Context
import android.net.Uri
import java.io.InputStream

interface ModelLoader {
    fun canLoad(extension: String): Boolean
    suspend fun load(context: Context, uri: Uri): Result<Model3D>
}

class ModelLoaderManager {
    private val loaders = mutableListOf<ModelLoader>()
    
    init {
        loaders.add(OBJLoader())
        loaders.add(GLTFLoader())
        loaders.add(GLBLoader())
        loaders.add(FBXLoader())
    }
    
    fun getLoader(fileName: String): ModelLoader? {
        val extension = fileName.substringAfterLast('.').lowercase()
        return loaders.find { it.canLoad(extension) }
    }
    
    suspend fun loadModel(context: Context, uri: Uri, fileName: String): Result<Model3D> {
        val loader = getLoader(fileName)
            ?: return Result.failure(UnsupportedFormatException("Unsupported format: $fileName"))
        
        return loader.load(context, uri)
    }
}

class UnsupportedFormatException(message: String) : Exception(message)

fun InputStream.readAllBytes(): ByteArray {
    val buffer = mutableListOf<Byte>()
    val data = ByteArray(4096)
    var bytesRead: Int
    while (read(data).also { bytesRead = it } != -1) {
        for (i in 0 until bytesRead) {
            buffer.add(data[i])
        }
    }
    return buffer.toByteArray()
}
