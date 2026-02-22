package com.bedrock3d.model

import kotlin.math.*

data class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = Vector3(x / scalar, y / scalar, z / scalar)
    
    fun length() = sqrt(x * x + y * y + z * z)
    
    fun normalize(): Vector3 {
        val len = length()
        return if (len > 0) this / len else Vector3()
    }
    
    fun toMinecraftCoords(): Vector3 {
        return Vector3(x, z, y)
    }
}

data class Vector2(
    val x: Float = 0f,
    val y: Float = 0f
)

data class Vertex(
    val position: Vector3,
    val normal: Vector3 = Vector3(),
    val texCoords: Vector2 = Vector2()
)

data class Mesh(
    val vertices: List<Vertex>,
    val indices: List<Int>,
    val name: String = "mesh"
)

data class Material(
    val name: String = "default",
    val diffuseTexture: String? = null,
    val normalTexture: String? = null,
    val diffuseColor: Vector3 = Vector3(1f, 1f, 1f)
)

data class Bone(
    val name: String,
    val parent: String? = null,
    val pivot: Vector3 = Vector3(),
    val rotation: Vector3 = Vector3(),
    val cubes: List<Cube> = emptyList(),
    val children: MutableList<Bone> = mutableListOf()
)

data class Cube(
    val origin: Vector3,
    val size: Vector3,
    val uv: UVMapping? = null,
    val inflate: Float = 0f
)

data class UVMapping(
    val north: UVBox = UVBox(),
    val south: UVBox = UVBox(),
    val east: UVBox = UVBox(),
    val west: UVBox = UVBox(),
    val up: UVBox = UVBox(),
    val down: UVBox = UVBox()
)

data class UVBox(
    val uv: Vector2 = Vector2(),
    val uvSize: Vector2 = Vector2()
)

data class Model3D(
    val name: String,
    val meshes: List<Mesh>,
    val materials: List<Material> = emptyList(),
    val bones: List<Bone> = emptyList(),
    var scale: Float = 1f,
    var pivot: Vector3 = Vector3()
) {
    val boundingBox: BoundingBox
        get() {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            var maxZ = Float.MIN_VALUE
            
            for (mesh in meshes) {
                for (vertex in mesh.vertices) {
                    minX = minOf(minX, vertex.position.x)
                    minY = minOf(minY, vertex.position.y)
                    minZ = minOf(minZ, vertex.position.z)
                    maxX = maxOf(maxX, vertex.position.x)
                    maxY = maxOf(maxY, vertex.position.y)
                    maxZ = maxOf(maxZ, vertex.position.z)
                }
            }
            
            return BoundingBox(
                min = Vector3(minX, minY, minZ),
                max = Vector3(maxX, maxY, maxZ)
            )
        }
    
    fun centerModel(): Model3D {
        val bb = boundingBox
        val center = Vector3(
            (bb.min.x + bb.max.x) / 2,
            bb.min.y,
            (bb.min.z + bb.max.z) / 2
        )
        
        return copy(
            meshes = meshes.map { mesh ->
                mesh.copy(
                    vertices = mesh.vertices.map { vertex ->
                        vertex.copy(
                            position = vertex.position - center
                        )
                    }
                )
            },
            pivot = center
        )
    }
    
    fun applyScale(newScale: Float): Model3D {
        val scaleFactor = newScale / scale
        return copy(
            meshes = meshes.map { mesh ->
                mesh.copy(
                    vertices = mesh.vertices.map { vertex ->
                        vertex.copy(
                            position = vertex.position * scaleFactor
                        )
                    }
                )
            },
            bones = bones.map { bone ->
                bone.copy(
                    pivot = bone.pivot * scaleFactor,
                    cubes = bone.cubes.map { cube ->
                        cube.copy(
                            origin = cube.origin * scaleFactor,
                            size = cube.size * scaleFactor
                        )
                    }
                )
            },
            scale = newScale
        )
    }
}

data class BoundingBox(
    val min: Vector3,
    val max: Vector3
) {
    val size: Vector3
        get() = max - min
    
    val center: Vector3
        get() = (min + max) * 0.5f
}
