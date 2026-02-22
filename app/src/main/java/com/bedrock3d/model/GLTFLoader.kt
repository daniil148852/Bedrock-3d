package com.bedrock3d.model

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GLTFLoader : ModelLoader {
    
    override fun canLoad(extension: String): Boolean {
        return extension == "gltf"
    }
    
    override suspend fun load(context: Context, uri: Uri): Result<Model3D> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))
            
            val reader = InputStreamReader(inputStream)
            val gson = Gson()
            val gltf = JsonParser.parseReader(reader).asJsonObject
            reader.close()
            
            val meshes = mutableListOf<Mesh>()
            val materials = mutableListOf<Material>()
            val bones = mutableListOf<Bone>()
            
            val buffers = loadBuffers(context, uri, gltf)
            val accessors = loadAccessors(gltf, buffers)
            
            gltf.getAsJsonArray("meshes")?.forEach { meshElement ->
                val meshObj = meshElement.asJsonObject
                val meshName = meshObj.get("name")?.asString ?: "mesh_${meshes.size}"
                
                meshObj.getAsJsonArray("primitives")?.forEach { primitiveElement ->
                    val primitive = primitiveElement.asJsonObject
                    
                    val positions = mutableListOf<Vector3>()
                    val normals = mutableListOf<Vector3>()
                    val texCoords = mutableListOf<Vector2>()
                    
                    val attributes = primitive.getAsJsonObject("attributes")
                    
                    attributes?.get("POSITION")?.asInt?.let { idx ->
                        positions.addAll(accessors.getOrNull(idx)?.map { 
                            Vector3(it[0], it[1], it[2]) 
                        } ?: emptyList())
                    }
                    
                    attributes?.get("NORMAL")?.asInt?.let { idx ->
                        normals.addAll(accessors.getOrNull(idx)?.map { 
                            Vector3(it[0], it[1], it[2]) 
                        } ?: emptyList())
                    }
                    
                    attributes?.get("TEXCOORD_0")?.asInt?.let { idx ->
                        texCoords.addAll(accessors.getOrNull(idx)?.map { 
                            Vector2(it[0], it[1]) 
                        } ?: emptyList())
                    }
                    
                    val indices = mutableListOf<Int>()
                    primitive.get("indices")?.asInt?.let { idx ->
                        indices.addAll(accessors.getOrNull(idx)?.map { it[0].toInt() } ?: emptyList())
                    }
                    
                    val vertices = positions.mapIndexed { index, pos ->
                        Vertex(
                            position = pos,
                            normal = normals.getOrElse(index) { Vector3() },
                            texCoords = texCoords.getOrElse(index) { Vector2() }
                        )
                    }
                    
                    if (vertices.isNotEmpty()) {
                        meshes.add(Mesh(vertices, indices, meshName))
                    }
                }
            }
            
            gltf.getAsJsonArray("materials")?.forEach { matElement ->
                val matObj = matElement.asJsonObject
                val name = matObj.get("name")?.asString ?: "material_${materials.size}"
                val pbr = matObj.getAsJsonObject("pbrMetallicRoughness")
                val baseColor = pbr?.getAsJsonObject("baseColorFactor")
                val color = baseColor?.let {
                    Vector3(
                        it.get("r")?.asFloat ?: 1f,
                        it.get("g")?.asFloat ?: 1f,
                        it.get("b")?.asFloat ?: 1f
                    )
                } ?: Vector3(1f, 1f, 1f)
                materials.add(Material(name = name, diffuseColor = color))
            }
            
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "model"
            val modelName = fileName.substringBeforeLast('.')
            
            Result.success(Model3D(
                name = modelName,
                meshes = meshes,
                materials = materials,
                bones = bones
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun loadBuffers(context: Context, uri: Uri, gltf: JsonObject): List<ByteArray> {
        val buffers = mutableListOf<ByteArray>()
        
        gltf.getAsJsonArray("buffers")?.forEach { bufferElement ->
            val bufferObj = bufferElement.asJsonObject
            val byteLength = bufferObj.get("byteLength")?.asInt ?: 0
            val uriStr = bufferObj.get("uri")?.asString
            
            if (uriStr != null) {
                buffers.add(ByteArray(byteLength))
            } else {
                buffers.add(ByteArray(byteLength))
            }
        }
        
        return buffers
    }
    
    private fun loadAccessors(gltf: JsonObject, buffers: List<ByteArray>): List<List<FloatArray>> {
        val accessors = mutableListOf<List<FloatArray>>()
        
        gltf.getAsJsonArray("accessors")?.forEach { accessorElement ->
            val accessor = accessorElement.asJsonObject
            val count = accessor.get("count")?.asInt ?: 0
            val accessorType = accessor.get("type")?.asString ?: "SCALAR"
            val componentType = accessor.get("componentType")?.asInt ?: 5126
            
            val numComponents = when (accessorType) {
                "SCALAR" -> 1
                "VEC2" -> 2
                "VEC3" -> 3
                "VEC4" -> 4
                "MAT4" -> 16
                else -> 1
            }
            
            val data = mutableListOf<FloatArray>()
            for (i in 0 until count) {
                val values = FloatArray(numComponents) { 0f }
                data.add(values)
            }
            accessors.add(data)
        }
        
        return accessors
    }
}

class GLBLoader : ModelLoader {
    
    override fun canLoad(extension: String): Boolean {
        return extension == "glb"
    }
    
    override suspend fun load(context: Context, uri: Uri): Result<Model3D> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))
            
            val baos = ByteArrayOutputStream()
            val tempBuffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(tempBuffer).also { bytesRead = it } != -1) {
                baos.write(tempBuffer, 0, bytesRead)
            }
            inputStream.close()
            val buffer = baos.toByteArray()
            
            val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
            
            val magic = byteBuffer.int
            if (magic != 0x46546C67) {
                return Result.failure(Exception("Invalid GLB file"))
            }
            
            val version = byteBuffer.int
            val totalLength = byteBuffer.int
            
            var jsonChunk: String? = null
            var binaryChunk: ByteArray? = null
            
            while (byteBuffer.remaining() >= 8) {
                val chunkLength = byteBuffer.int
                val chunkType = byteBuffer.int
                
                val chunkData = ByteArray(chunkLength)
                byteBuffer.get(chunkData)
                
                if (chunkType == 0x4E4F534A) {
                    jsonChunk = String(chunkData, Charsets.UTF_8)
                } else if (chunkType == 0x004E4942) {
                    binaryChunk = chunkData
                }
            }
            
            if (jsonChunk == null) {
                return Result.failure(Exception("No JSON chunk found"))
            }
            
            val gltf = JsonParser.parseString(jsonChunk).asJsonObject
            
            val meshes = parseGLTFMeshes(gltf, binaryChunk)
            
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "model"
            val modelName = fileName.substringBeforeLast('.')
            
            Result.success(Model3D(
                name = modelName,
                meshes = meshes.first,
                materials = meshes.second
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun parseGLTFMeshes(gltf: JsonObject, binaryChunk: ByteArray?): Pair<List<Mesh>, List<Material>> {
        val meshes = mutableListOf<Mesh>()
        val materials = mutableListOf<Material>()
        
        val buffers = mutableListOf<ByteArray>()
        gltf.getAsJsonArray("buffers")?.forEach { bufferElement ->
            val bufferObj = bufferElement.asJsonObject
            val byteLength = bufferObj.get("byteLength")?.asInt ?: 0
            if (binaryChunk != null) {
                buffers.add(binaryChunk.copyOf(byteLength))
            } else {
                buffers.add(ByteArray(byteLength))
            }
        }
        
        val bufferViews = mutableListOf<BufferView>()
        gltf.getAsJsonArray("bufferViews")?.forEach { viewElement ->
            val viewObj = viewElement.asJsonObject
            bufferViews.add(BufferView(
                buffer = viewObj.get("buffer")?.asInt ?: 0,
                byteOffset = viewObj.get("byteOffset")?.asInt ?: 0,
                byteLength = viewObj.get("byteLength")?.asInt ?: 0,
                byteStride = viewObj.get("byteStride")?.asInt
            ))
        }
        
        val accessors = mutableListOf<Accessor>()
        gltf.getAsJsonArray("accessors")?.forEach { accessorElement ->
            val accessor = accessorElement.asJsonObject
            val type = accessor.get("type")?.asString ?: "SCALAR"
            val componentType = accessor.get("componentType")?.asInt ?: 5126
            val count = accessor.get("count")?.asInt ?: 0
            val bufferViewIdx = accessor.get("bufferView")?.asInt
            val byteOffset = accessor.get("byteOffset")?.asInt ?: 0
            
            val numComponents = when (type) {
                "SCALAR" -> 1
                "VEC2" -> 2
                "VEC3" -> 3
                "VEC4" -> 4
                else -> 1
            }
            
            accessors.add(Accessor(
                bufferView = bufferViewIdx,
                byteOffset = byteOffset,
                componentType = componentType,
                count = count,
                type = type,
                numComponents = numComponents
            ))
        }
        
        gltf.getAsJsonArray("meshes")?.forEach { meshElement ->
            val meshObj = meshElement.asJsonObject
            val meshName = meshObj.get("name")?.asString ?: "mesh_${meshes.size}"
            
            meshObj.getAsJsonArray("primitives")?.forEach { primitiveElement ->
                val primitive = primitiveElement.asJsonObject
                
                val vertices = mutableListOf<Vertex>()
                val indicesList = mutableListOf<Int>()
                
                val attributes = primitive.getAsJsonObject("attributes")
                
                var positions = listOf<Vector3>()
                var normals = listOf<Vector3>()
                var texCoords = listOf<Vector2>()
                
                attributes?.get("POSITION")?.asInt?.let { idx ->
                    positions = readVec3Data(buffers, bufferViews, accessors, idx)
                }
                
                attributes?.get("NORMAL")?.asInt?.let { idx ->
                    normals = readVec3Data(buffers, bufferViews, accessors, idx)
                }
                
                attributes?.get("TEXCOORD_0")?.asInt?.let { idx ->
                    texCoords = readVec2Data(buffers, bufferViews, accessors, idx)
                }
                
                primitive.get("indices")?.asInt?.let { idx ->
                    indicesList.addAll(readIndicesData(buffers, bufferViews, accessors, idx))
                }
                
                for (i in positions.indices) {
                    vertices.add(Vertex(
                        position = positions[i],
                        normal = normals.getOrElse(i) { Vector3() },
                        texCoords = texCoords.getOrElse(i) { Vector2() }
                    ))
                }
                
                if (vertices.isNotEmpty()) {
                    meshes.add(Mesh(vertices, indicesList, meshName))
                }
            }
        }
        
        gltf.getAsJsonArray("materials")?.forEach { matElement ->
            val matObj = matElement.asJsonObject
            val name = matObj.get("name")?.asString ?: "material_${materials.size}"
            materials.add(Material(name = name))
        }
        
        return Pair(meshes, materials)
    }
    
    private fun readVec3Data(
        buffers: List<ByteArray>,
        bufferViews: List<BufferView>,
        accessors: List<Accessor>,
        accessorIdx: Int
    ): List<Vector3> {
        val result = mutableListOf<Vector3>()
        val accessor = accessors.getOrNull(accessorIdx) ?: return result
        val bufferView = accessor.bufferView?.let { bufferViews.getOrNull(it) } ?: return result
        val buffer = buffers.getOrNull(bufferView.buffer) ?: return result
        
        val byteBuffer = ByteBuffer.wrap(buffer)
            .order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.position(bufferView.byteOffset + accessor.byteOffset)
        val slicedBuffer = byteBuffer.slice()
        
        for (i in 0 until accessor.count) {
            val x = slicedBuffer.getFloat()
            val y = slicedBuffer.getFloat()
            val z = slicedBuffer.getFloat()
            result.add(Vector3(x, y, z))
        }
        
        return result
    }
    
    private fun readVec2Data(
        buffers: List<ByteArray>,
        bufferViews: List<BufferView>,
        accessors: List<Accessor>,
        accessorIdx: Int
    ): List<Vector2> {
        val result = mutableListOf<Vector2>()
        val accessor = accessors.getOrNull(accessorIdx) ?: return result
        val bufferView = accessor.bufferView?.let { bufferViews.getOrNull(it) } ?: return result
        val buffer = buffers.getOrNull(bufferView.buffer) ?: return result
        
        val byteBuffer = ByteBuffer.wrap(buffer)
            .order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.position(bufferView.byteOffset + accessor.byteOffset)
        val slicedBuffer = byteBuffer.slice()
        
        for (i in 0 until accessor.count) {
            val x = slicedBuffer.getFloat()
            val y = slicedBuffer.getFloat()
            result.add(Vector2(x, y))
        }
        
        return result
    }
    
    private fun readIndicesData(
        buffers: List<ByteArray>,
        bufferViews: List<BufferView>,
        accessors: List<Accessor>,
        accessorIdx: Int
    ): List<Int> {
        val result = mutableListOf<Int>()
        val accessor = accessors.getOrNull(accessorIdx) ?: return result
        val bufferView = accessor.bufferView?.let { bufferViews.getOrNull(it) } ?: return result
        val buffer = buffers.getOrNull(bufferView.buffer) ?: return result
        
        val byteBuffer = ByteBuffer.wrap(buffer)
            .order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.position(bufferView.byteOffset + accessor.byteOffset)
        val slicedBuffer = byteBuffer.slice()
        
        for (i in 0 until accessor.count) {
            val value = when (accessor.componentType) {
                5121 -> slicedBuffer.get().toInt() and 0xFF
                5123 -> slicedBuffer.getShort().toInt() and 0xFFFF
                5125 -> slicedBuffer.getInt()
                else -> 0
            }
            result.add(value)
        }
        
        return result
    }
    
    private data class BufferView(
        val buffer: Int,
        val byteOffset: Int,
        val byteLength: Int,
        val byteStride: Int?
    )
    
    private data class Accessor(
        val bufferView: Int?,
        val byteOffset: Int,
        val componentType: Int,
        val count: Int,
        val type: String,
        val numComponents: Int
    )
}
