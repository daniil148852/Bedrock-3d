package com.bedrock3d.model

import android.content.Context
import android.net.Uri

class FBXLoader : ModelLoader {
    
    override fun canLoad(extension: String): Boolean {
        return extension == "fbx"
    }
    
    override suspend fun load(context: Context, uri: Uri): Result<Model3D> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))
            
            val data = inputStream.readAllBytes()
            inputStream.close()
            
            val parser = FBXParser(data)
            val result = parser.parse()
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class FBXParser(private val data: ByteArray) {
    
    private var position = 0
    
    fun parse(): Model3D {
        val header = parseHeader()
        
        return if (header.isBinary) {
            parseBinaryFBX(header.version)
        } else {
            parseAsciiFBX()
        }
    }
    
    private fun parseHeader(): FBXHeader {
        val magic = String(data.copyOfRange(0, 21))
        val isBinary = magic.startsWith("Kaydara FBX Binary")
        
        val version = if (isBinary) {
            (data[23].toInt() and 0xFF) or
            ((data[24].toInt() and 0xFF) shl 8) or
            ((data[25].toInt() and 0xFF) shl 16) or
            ((data[26].toInt() and 0xFF) shl 24)
        } else {
            0
        }
        
        return FBXHeader(isBinary, version)
    }
    
    private fun parseBinaryFBX(version: Int): Model3D {
        val meshes = mutableListOf<Mesh>()
        val materials = mutableListOf<Material>()
        val bones = mutableListOf<Bone>()
        
        position = 27
        
        while (position < data.size - 13) {
            val record = parseRecord(version)
            if (record != null) {
                when (record.name) {
                    "Geometry" -> {
                        val mesh = parseGeometry(record)
                        if (mesh != null) meshes.add(mesh)
                    }
                    "Material" -> {
                        val material = parseMaterial(record)
                        if (material != null) materials.add(material)
                    }
                    "Model" -> {
                        val bone = parseModel(record)
                        if (bone != null) bones.add(bone)
                    }
                }
            }
        }
        
        return Model3D(
            name = "fbx_model",
            meshes = meshes,
            materials = materials,
            bones = bones
        )
    }
    
    private fun parseAsciiFBX(): Model3D {
        val content = String(data)
        
        val meshes = mutableListOf<Mesh>()
        val materials = mutableListOf<Material>()
        
        val verticesRegex = Regex("Vertices:\\s*\\*([0-9]+)\\s*\\{([^}]+)\\}")
        val verticesMatch = verticesRegex.find(content)
        
        if (verticesMatch != null) {
            val verticesData = verticesMatch.groupValues[2]
                .split(",")
                .mapNotNull { it.trim().toFloatOrNull() }
            
            val vertices = mutableListOf<Vertex>()
            for (i in verticesData.indices step 3) {
                if (i + 2 < verticesData.size) {
                    vertices.add(Vertex(
                        position = Vector3(
                            verticesData[i],
                            verticesData[i + 1],
                            verticesData[i + 2]
                        )
                    ))
                }
            }
            
            if (vertices.isNotEmpty()) {
                meshes.add(Mesh(vertices, emptyList(), "mesh"))
            }
        }
        
        return Model3D(
            name = "fbx_model",
            meshes = meshes,
            materials = materials
        )
    }
    
    private fun parseRecord(version: Int): FBXRecord? {
        if (position >= data.size) return null
        
        val endOffset = readUInt32()
        val numProperties = readUInt32()
        val propertyListLen = readUInt32()
        val nameLen = data[position++].toInt() and 0xFF
        
        if (endOffset == 0L) return null
        
        val name = String(data, position, nameLen, Charsets.UTF_8)
        position += nameLen
        
        position += propertyListLen.toInt()
        
        val children = mutableListOf<FBXRecord>()
        
        while (position < endOffset && position < data.size) {
            val child = parseRecord(version)
            if (child != null && child.name.isNotEmpty()) {
                children.add(child)
            }
        }
        
        position = endOffset.toInt()
        
        return FBXRecord(name, children)
    }
    
    private fun parseGeometry(record: FBXRecord): Mesh? {
        var vertices = listOf<Vector3>()
        var indices = listOf<Int>()
        var name = "mesh"
        
        for (child in record.children) {
            when (child.name) {
                "Vertices" -> {
                    vertices = parseVertices(child)
                }
                "PolygonVertexIndex" -> {
                    indices = parsePolygonIndices(child)
                }
                "Name" -> {
                    name = child.children.firstOrNull()?.name ?: "mesh"
                }
            }
        }
        
        if (vertices.isEmpty()) return null
        
        val vertexList = vertices.map { Vertex(position = it) }
        
        return Mesh(vertexList, indices, name)
    }
    
    private fun parseVertices(record: FBXRecord): List<Vector3> {
        val vertices = mutableListOf<Vector3>()
        
        return vertices
    }
    
    private fun parsePolygonIndices(record: FBXRecord): List<Int> {
        return emptyList()
    }
    
    private fun parseMaterial(record: FBXRecord): Material? {
        var name = "material"
        
        for (child in record.children) {
            if (child.name == "Name") {
                name = child.children.firstOrNull()?.name ?: "material"
            }
        }
        
        return Material(name = name)
    }
    
    private fun parseModel(record: FBXRecord): Bone? {
        var name = "bone"
        var pivot = Vector3()
        
        for (child in record.children) {
            when (child.name) {
                "Name" -> {
                    name = child.children.firstOrNull()?.name ?: "bone"
                }
                "Properties70" -> {
                    for (prop in child.children) {
                        if (prop.name == "P") {
                        }
                    }
                }
            }
        }
        
        return Bone(name = name, pivot = pivot)
    }
    
    private fun readUInt32(): Long {
        if (position + 4 > data.size) return 0
        val value = (data[position].toLong() and 0xFF) or
                   ((data[position + 1].toLong() and 0xFF) shl 8) or
                   ((data[position + 2].toLong() and 0xFF) shl 16) or
                   ((data[position + 3].toLong() and 0xFF) shl 24)
        position += 4
        return value
    }
    
    private data class FBXHeader(
        val isBinary: Boolean,
        val version: Int
    )
    
    private data class FBXRecord(
        val name: String,
        val children: List<FBXRecord>
    )
}
