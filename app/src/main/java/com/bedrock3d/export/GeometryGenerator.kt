package com.bedrock3d.export

import com.bedrock3d.model.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.math.abs
import kotlin.math.floor

class GeometryGenerator {
    
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    
    fun generate(model: Model3D, scale: Float = 1f, textureWidth: Int = 128, textureHeight: Int = 128): String {
        val scaledModel = model.applyScale(scale).centerModel()
        
        val geometry = JsonObject()
        geometry.addProperty("format_version", "1.12.0")
        
        val minecraftGeometry = JsonObject()
        
        val description = JsonObject()
        description.addProperty("identifier", "geometry.${model.name.lowercase().replace(" ", "_")}")
        description.addProperty("texture_width", textureWidth)
        description.addProperty("texture_height", textureHeight)
        minecraftGeometry.add("description", description)
        
        val bones = JsonArray()
        
        val mainBone = JsonObject()
        mainBone.addProperty("name", "root")
        
        val pivot = JsonArray()
        pivot.add(0.0)
        pivot.add(0.0)
        pivot.add(0.0)
        mainBone.add("pivot", pivot)
        
        val cubes = generateCubesFromMeshes(scaledModel.meshes, textureWidth, textureHeight)
        if (cubes.size() > 0) {
            mainBone.add("cubes", cubes)
        }
        
        bones.add(mainBone)
        
        if (model.bones.isNotEmpty()) {
            model.bones.forEach { bone ->
                bones.add(generateBone(bone, textureWidth, textureHeight))
            }
        }
        
        minecraftGeometry.add("bones", bones)
        
        val geometryArray = JsonArray()
        geometryArray.add(minecraftGeometry)
        
        val wrapper = JsonObject()
        wrapper.add("minecraft:geometry", geometryArray)
        
        return gson.toJson(wrapper)
    }
    
    private fun generateCubesFromMeshes(meshes: List<Mesh>, texWidth: Int, texHeight: Int): JsonArray {
        val cubes = JsonArray()
        
        meshes.forEach { mesh ->
            val cubeData = convertMeshToCubes(mesh)
            cubeData.forEach { cube ->
                cubes.add(createCubeJson(cube, texWidth, texHeight))
            }
        }
        
        return cubes
    }
    
    private fun convertMeshToCubes(mesh: Mesh): List<MCCube> {
        val cubes = mutableListOf<MCCube>()
        
        val vertices = mesh.vertices
        if (vertices.isEmpty()) return cubes
        
        val positions = vertices.map { it.position }
        
        val voxelSize = calculateVoxelSize(positions)
        
        val voxelMap = mutableMapOf<Triple<Int, Int, Int>, Boolean>()
        
        positions.forEach { pos ->
            val vx = floor(pos.x / voxelSize).toInt()
            val vy = floor(pos.y / voxelSize).toInt()
            val vz = floor(pos.z / voxelSize).toInt()
            voxelMap[Triple(vx, vy, vz)] = true
        }
        
        val visited = mutableSetOf<Triple<Int, Int, Int>>()
        
        voxelMap.keys.forEach { voxel ->
            if (!visited.contains(voxel)) {
                val cube = findLargestCube(voxel, voxelMap, visited)
                if (cube != null) {
                    cubes.add(cube)
                }
            }
        }
        
        return cubes
    }
    
    private fun calculateVoxelSize(positions: List<Vector3>): Float {
        if (positions.isEmpty()) return 1f
        
        val bb = calculateBoundingBox(positions)
        val maxDim = maxOf(bb.size.x, bb.size.y, bb.size.z)
        
        val targetVoxels = 32f
        
        return maxOf(maxDim / targetVoxels, 0.0625f)
    }
    
    private fun calculateBoundingBox(positions: List<Vector3>): BoundingBox {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var maxZ = Float.MIN_VALUE
        
        positions.forEach { pos ->
            minX = minOf(minX, pos.x)
            minY = minOf(minY, pos.y)
            minZ = minOf(minZ, pos.z)
            maxX = maxOf(maxX, pos.x)
            maxY = maxOf(maxY, pos.y)
            maxZ = maxOf(maxZ, pos.z)
        }
        
        return BoundingBox(
            min = Vector3(minX, minY, minZ),
            max = Vector3(maxX, maxY, maxZ)
        )
    }
    
    private fun findLargestCube(
        start: Triple<Int, Int, Int>,
        voxelMap: Map<Triple<Int, Int, Int>, Boolean>,
        visited: MutableSet<Triple<Int, Int, Int>>
    ): MCCube? {
        var size = 1
        
        while (true) {
            val canExpand = canExpandCube(start, size + 1, voxelMap)
            if (canExpand) {
                size++
            } else {
                break
            }
        }
        
        for (dx in 0 until size) {
            for (dy in 0 until size) {
                for (dz in 0 until size) {
                    visited.add(Triple(start.first + dx, start.second + dy, start.third + dz))
                }
            }
        }
        
        return MCCube(
            origin = Vector3(
                start.first.toFloat(),
                start.second.toFloat(),
                start.third.toFloat()
            ),
            size = Vector3(size.toFloat(), size.toFloat(), size.toFloat())
        )
    }
    
    private fun canExpandCube(
        start: Triple<Int, Int, Int>,
        size: Int,
        voxelMap: Map<Triple<Int, Int, Int>, Boolean>
    ): Boolean {
        for (dx in 0 until size) {
            for (dy in 0 until size) {
                for (dz in 0 until size) {
                    val voxel = Triple(start.first + dx, start.second + dy, start.third + dz)
                    if (!voxelMap.containsKey(voxel)) {
                        return false
                    }
                }
            }
        }
        return true
    }
    
    private fun createCubeJson(cube: MCCube, texWidth: Int, texHeight: Int): JsonObject {
        val json = JsonObject()
        
        val origin = JsonArray()
        origin.add(roundToMinecraft(cube.origin.x))
        origin.add(roundToMinecraft(cube.origin.y))
        origin.add(roundToMinecraft(cube.origin.z))
        json.add("origin", origin)
        
        val size = JsonArray()
        size.add(roundToMinecraft(cube.size.x))
        size.add(roundToMinecraft(cube.size.y))
        size.add(roundToMinecraft(cube.size.z))
        json.add("size", size)
        
        val uv = JsonObject()
        val uvNorth = JsonArray()
        uvNorth.add(0.0)
        uvNorth.add(0.0)
        uv.add("north", uvNorth)
        
        val uvSouth = JsonArray()
        uvSouth.add(0.0)
        uvSouth.add(0.0)
        uv.add("south", uvSouth)
        
        val uvEast = JsonArray()
        uvEast.add(0.0)
        uvEast.add(0.0)
        uv.add("east", uvEast)
        
        val uvWest = JsonArray()
        uvWest.add(0.0)
        uvWest.add(0.0)
        uv.add("west", uvWest)
        
        val uvUp = JsonArray()
        uvUp.add(0.0)
        uvUp.add(0.0)
        uv.add("up", uvUp)
        
        val uvDown = JsonArray()
        uvDown.add(0.0)
        uvDown.add(0.0)
        uv.add("down", uvDown)
        
        json.add("uv", uv)
        
        return json
    }
    
    private fun generateBone(bone: Bone, texWidth: Int, texHeight: Int): JsonObject {
        val json = JsonObject()
        json.addProperty("name", bone.name)
        
        if (bone.parent != null) {
            json.addProperty("parent", bone.parent)
        }
        
        val pivot = JsonArray()
        pivot.add(roundToMinecraft(bone.pivot.x))
        pivot.add(roundToMinecraft(bone.pivot.y))
        pivot.add(roundToMinecraft(bone.pivot.z))
        json.add("pivot", pivot)
        
        if (bone.cubes.isNotEmpty()) {
            val cubes = JsonArray()
            bone.cubes.forEach { cube ->
                cubes.add(createCubeJson(
                    MCCube(cube.origin, cube.size),
                    texWidth,
                    texHeight
                ))
            }
            json.add("cubes", cubes)
        }
        
        return json
    }
    
    private fun roundToMinecraft(value: Float): Double {
        return (value * 16.0).roundTo(4) / 16.0
    }
    
    private fun Double.roundTo(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
    
    private data class MCCube(
        val origin: Vector3,
        val size: Vector3
    )
}
