package com.bedrock3d.model

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

class OBJLoader : ModelLoader {
    
    override fun canLoad(extension: String): Boolean {
        return extension == "obj"
    }
    
    override suspend fun load(context: Context, uri: Uri): Result<Model3D> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))
            
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            val positions = mutableListOf<Vector3>()
            val normals = mutableListOf<Vector3>()
            val texCoords = mutableListOf<Vector2>()
            val vertices = mutableListOf<Vertex>()
            val indices = mutableListOf<Int>()
            val vertexMap = mutableMapOf<String, Int>()
            
            var line: String?
            var meshName = "default"
            val meshes = mutableListOf<Mesh>()
            val materials = mutableListOf<Material>()
            
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.trim().split(Regex("\\s+"))
                if (parts.isEmpty()) continue
                
                when (parts[0]) {
                    "v" -> {
                        if (parts.size >= 4) {
                            positions.add(Vector3(
                                parts[1].toFloat(),
                                parts[2].toFloat(),
                                parts[3].toFloat()
                            ))
                        }
                    }
                    "vn" -> {
                        if (parts.size >= 4) {
                            normals.add(Vector3(
                                parts[1].toFloat(),
                                parts[2].toFloat(),
                                parts[3].toFloat()
                            ))
                        }
                    }
                    "vt" -> {
                        if (parts.size >= 3) {
                            texCoords.add(Vector2(
                                parts[1].toFloat(),
                                parts[2].toFloat()
                            ))
                        }
                    }
                    "f" -> {
                        if (parts.size >= 4) {
                            val faceIndices = mutableListOf<Int>()
                            for (i in 1 until parts.size) {
                                val key = parts[i]
                                if (vertexMap.containsKey(key)) {
                                    faceIndices.add(vertexMap[key]!!)
                                } else {
                                    val indices_data = key.split("/")
                                    val posIdx = indices_data[0].toInt() - 1
                                    val texIdx = if (indices_data.size > 1 && indices_data[1].isNotEmpty()) 
                                        indices_data[1].toInt() - 1 else -1
                                    val normIdx = if (indices_data.size > 2 && indices_data[2].isNotEmpty()) 
                                        indices_data[2].toInt() - 1 else -1
                                    
                                    val vertex = Vertex(
                                        position = positions.getOrElse(posIdx) { Vector3() },
                                        normal = if (normIdx >= 0) normals.getOrElse(normIdx) { Vector3() } else Vector3(),
                                        texCoords = if (texIdx >= 0) texCoords.getOrElse(texIdx) { Vector2() } else Vector2()
                                    )
                                    
                                    val idx = vertices.size
                                    vertices.add(vertex)
                                    vertexMap[key] = idx
                                    faceIndices.add(idx)
                                }
                            }
                            
                            if (faceIndices.size == 3) {
                                indices.addAll(faceIndices)
                            } else if (faceIndices.size == 4) {
                                indices.add(faceIndices[0])
                                indices.add(faceIndices[1])
                                indices.add(faceIndices[2])
                                indices.add(faceIndices[0])
                                indices.add(faceIndices[2])
                                indices.add(faceIndices[3])
                            } else if (faceIndices.size > 4) {
                                for (i in 1 until faceIndices.size - 1) {
                                    indices.add(faceIndices[0])
                                    indices.add(faceIndices[i])
                                    indices.add(faceIndices[i + 1])
                                }
                            }
                        }
                    }
                    "o", "g" -> {
                        if (vertices.isNotEmpty()) {
                            meshes.add(Mesh(vertices.toList(), indices.toList(), meshName))
                            vertices.clear()
                            indices.clear()
                            vertexMap.clear()
                        }
                        meshName = if (parts.size > 1) parts[1] else "mesh_${meshes.size}"
                    }
                    "usemtl" -> {
                        if (parts.size > 1) {
                            materials.add(Material(name = parts[1]))
                        }
                    }
                }
            }
            
            if (vertices.isNotEmpty()) {
                meshes.add(Mesh(vertices.toList(), indices.toList(), meshName))
            }
            
            reader.close()
            
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "model"
            val modelName = fileName.substringBeforeLast('.')
            
            Result.success(Model3D(
                name = modelName,
                meshes = meshes,
                materials = materials
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
