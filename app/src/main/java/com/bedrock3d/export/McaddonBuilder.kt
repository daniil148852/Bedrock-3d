package com.bedrock3d.export

import com.bedrock3d.model.Model3D
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.*
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class McaddonBuilder {
    
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val geometryGenerator = GeometryGenerator()
    private val entityGenerator = EntityGenerator()
    
    fun build(
        model: Model3D,
        scale: Float,
        outputPath: String,
        namespace: String = "custom",
        texturePath: String? = null
    ): Result<File> {
        return try {
            val outputFile = File(outputPath)
            val tempDir = createTempDirectory()
            
            try {
                val behaviorPackDir = File(tempDir, "${model.name}_bp")
                val resourcePackDir = File(tempDir, "${model.name}_rp")
                
                behaviorPackDir.mkdirs()
                resourcePackDir.mkdirs()
                
                createManifestFiles(behaviorPackDir, resourcePackDir, model, namespace)
                
                createGeometryFile(resourcePackDir, model, scale)
                
                createEntityFiles(behaviorPackDir, resourcePackDir, model, namespace)
                
                createRenderControllerFile(resourcePackDir, model, namespace)
                
                if (texturePath != null) {
                    copyTextureFile(resourcePackDir, texturePath, model)
                } else {
                    createDefaultTexture(resourcePackDir, model)
                }
                
                val bpZip = File(tempDir, "${model.name}_bp.mcpack")
                val rpZip = File(tempDir, "${model.name}_rp.mcpack")
                
                zipDirectory(behaviorPackDir, bpZip)
                zipDirectory(resourcePackDir, rpZip)
                
                createMcaddon(outputFile, bpZip, rpZip, model)
                
                Result.success(outputFile)
            } finally {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun createManifestFiles(bpDir: File, rpDir: File, model: Model3D, namespace: String) {
        val bpManifest = createBehaviorPackManifest(model, namespace)
        val rpManifest = createResourcePackManifest(model, namespace)
        
        File(bpDir, "manifest.json").writeText(bpManifest)
        File(rpDir, "manifest.json").writeText(rpManifest)
    }
    
    private fun createBehaviorPackManifest(model: Model3D, namespace: String): String {
        val manifest = JsonObject()
        manifest.addProperty("format_version", 2)
        
        val header = JsonObject()
        header.addProperty("name", "${model.name} Behavior Pack")
        header.addProperty("description", "Behavior pack for ${model.name} custom entity")
        header.addProperty("uuid", generateUUID())
        header.add("version", createVersionArray())
        header.add("min_engine_version", createEngineVersionArray())
        manifest.add("header", header)
        
        val modules = com.google.gson.JsonArray()
        val module = JsonObject()
        module.addProperty("type", "data")
        module.addProperty("uuid", generateUUID())
        module.add("version", createVersionArray())
        modules.add(module)
        manifest.add("modules", modules)
        
        val dependencies = com.google.gson.JsonArray()
        val dep = JsonObject()
        dep.addProperty("uuid", getResourcePackUUID(model))
        dep.add("version", createVersionArray())
        dependencies.add(dep)
        manifest.add("dependencies", dependencies)
        
        return gson.toJson(manifest)
    }
    
    private fun createResourcePackManifest(model: Model3D, namespace: String): String {
        val manifest = JsonObject()
        manifest.addProperty("format_version", 2)
        
        val header = JsonObject()
        header.addProperty("name", "${model.name} Resource Pack")
        header.addProperty("description", "Resource pack for ${model.name} custom entity")
        header.addProperty("uuid", getResourcePackUUID(model))
        header.add("version", createVersionArray())
        header.add("min_engine_version", createEngineVersionArray())
        manifest.add("header", header)
        
        val modules = com.google.gson.JsonArray()
        val module = JsonObject()
        module.addProperty("type", "resources")
        module.addProperty("uuid", generateUUID())
        module.add("version", createVersionArray())
        modules.add(module)
        manifest.add("modules", modules)
        
        return gson.toJson(manifest)
    }
    
    private fun createGeometryFile(rpDir: File, model: Model3D, scale: Float) {
        val modelsDir = File(rpDir, "models")
        modelsDir.mkdirs()
        val entityDir = File(modelsDir, "entity")
        entityDir.mkdirs()
        
        val geometryJson = geometryGenerator.generate(model, scale)
        File(entityDir, "${model.name.lowercase().replace(" ", "_")}.geo.json").writeText(geometryJson)
    }
    
    private fun createEntityFiles(bpDir: File, rpDir: File, model: Model3D, namespace: String) {
        val bpEntityDir = File(bpDir, "entities")
        bpEntityDir.mkdirs()
        
        val entityJson = entityGenerator.generateBehaviorEntityFile(model, namespace)
        File(bpEntityDir, "${model.name.lowercase().replace(" ", "_")}.json").writeText(entityJson)
        
        val rpEntityDir = File(rpDir, "entity")
        rpEntityDir.mkdirs()
        
        val clientEntityJson = entityGenerator.generateClientEntityFile(model, namespace, model.name.lowercase().replace(" ", "_"))
        File(rpEntityDir, "${model.name.lowercase().replace(" ", "_")}.entity.json").writeText(clientEntityJson)
    }
    
    private fun createRenderControllerFile(rpDir: File, model: Model3D, namespace: String) {
        val rcDir = File(rpDir, "render_controllers")
        rcDir.mkdirs()
        
        val rcJson = entityGenerator.generateRenderController(model, namespace)
        File(rcDir, "${model.name.lowercase().replace(" ", "_")}.render_controller.json").writeText(rcJson)
    }
    
    private fun copyTextureFile(rpDir: File, texturePath: String, model: Model3D) {
        val texturesDir = File(rpDir, "textures")
        val entityDir = File(texturesDir, "entity")
        entityDir.mkdirs()
        
        val sourceFile = File(texturePath)
        if (sourceFile.exists()) {
            sourceFile.copyTo(File(entityDir, "${model.name.lowercase().replace(" ", "_")}.png"))
        }
    }
    
    private fun createDefaultTexture(rpDir: File, model: Model3D) {
        val texturesDir = File(rpDir, "textures")
        val entityDir = File(texturesDir, "entity")
        entityDir.mkdirs()
        
        val defaultTexture = createDefaultTextureData()
        File(entityDir, "${model.name.lowercase().replace(" ", "_")}.png").writeBytes(defaultTexture)
    }
    
    private fun createDefaultTextureData(): ByteArray {
        val size = 64
        val pixels = ByteArray(size * size * 4)
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = (y * size + x) * 4
                
                val isCheckerboard = ((x / 8) + (y / 8)) % 2 == 0
                
                if (isCheckerboard) {
                    pixels[idx] = 200.toByte()
                    pixels[idx + 1] = 200.toByte()
                    pixels[idx + 2] = 220.toByte()
                } else {
                    pixels[idx] = 180.toByte()
                    pixels[idx + 1] = 180.toByte()
                    pixels[idx + 2] = 200.toByte()
                }
                pixels[idx + 3] = 255.toByte()
            }
        }
        
        return encodePng(pixels, size, size)
    }
    
    private fun encodePng(pixels: ByteArray, width: Int, height: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        baos.write(signature)
        
        val ihdr = createIHDRChunk(width, height)
        baos.write(ihdr)
        
        val idat = createIDATChunk(pixels, width, height)
        baos.write(idat)
        
        val iend = createIENDChunk()
        baos.write(iend)
        
        return baos.toByteArray()
    }
    
    private fun createIHDRChunk(width: Int, height: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        
        baos.write(intToBytes(13))
        baos.write("IHDR".toByteArray())
        
        baos.write(intToBytes(width))
        baos.write(intToBytes(height))
        baos.write(byteArrayOf(8, 6, 0, 0, 0))
        
        val crc = CRC32()
        crc.update("IHDR".toByteArray())
        crc.update(intToBytes(width))
        crc.update(intToBytes(height))
        crc.update(byteArrayOf(8, 6, 0, 0, 0))
        
        baos.write(longToBytes(crc.value))
        
        return baos.toByteArray()
    }
    
    private fun createIDATChunk(pixels: ByteArray, width: Int, height: Int): ByteArray {
        val rawData = ByteArrayOutputStream()
        
        for (y in 0 until height) {
            rawData.write(0)
            for (x in 0 until width) {
                val idx = (y * width + x) * 4
                rawData.write(pixels[idx].toInt() and 0xFF)
                rawData.write(pixels[idx + 1].toInt() and 0xFF)
                rawData.write(pixels[idx + 2].toInt() and 0xFF)
                rawData.write(pixels[idx + 3].toInt() and 0xFF)
            }
        }
        
        val compressed = deflate(rawData.toByteArray())
        
        val baos = ByteArrayOutputStream()
        baos.write(intToBytes(compressed.size))
        baos.write("IDAT".toByteArray())
        baos.write(compressed)
        
        val crc = CRC32()
        crc.update("IDAT".toByteArray())
        crc.update(compressed)
        baos.write(longToBytes(crc.value))
        
        return baos.toByteArray()
    }
    
    private fun createIENDChunk(): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(intToBytes(0))
        baos.write("IEND".toByteArray())
        
        val crc = CRC32()
        crc.update("IEND".toByteArray())
        baos.write(longToBytes(crc.value))
        
        return baos.toByteArray()
    }
    
    private fun deflate(data: ByteArray): ByteArray {
        val deflater = java.util.zip.Deflater()
        deflater.setInput(data)
        deflater.finish()
        val output = ByteArray(data.size * 2)
        val size = deflater.deflate(output)
        deflater.end()
        return output.copyOf(size)
    }
    
    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }
    
    private fun longToBytes(value: Long): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }
    
    private fun zipDirectory(dir: File, outputFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(dir).path.replace("\\", "/")
                    zos.putNextEntry(ZipEntry(relativePath))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
    
    private fun createMcaddon(outputFile: File, bpZip: File, rpZip: File, model: Model3D) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
            zos.putNextEntry(ZipEntry("${model.name}_bp.mcpack"))
            bpZip.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
            
            zos.putNextEntry(ZipEntry("${model.name}_rp.mcpack"))
            rpZip.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }
    
    private fun createTempDirectory(): File {
        val temp = File(System.getProperty("java.io.tmpdir"), "bedrock3d_${System.currentTimeMillis()}")
        temp.mkdirs()
        return temp
    }
    
    private fun generateUUID(): String {
        return java.util.UUID.randomUUID().toString()
    }
    
    private fun getResourcePackUUID(model: Model3D): String {
        val namespace = "bedrock3d"
        val name = model.name.lowercase().replace(" ", "_")
        val hash = (namespace + name).hashCode().toLong()
        return java.util.UUID.nameUUIDFromBytes(hash.toString().toByteArray()).toString()
    }
    
    private fun createVersionArray(): com.google.gson.JsonArray {
        val arr = com.google.gson.JsonArray()
        arr.add(1)
        arr.add(0)
        arr.add(0)
        return arr
    }
    
    private fun createEngineVersionArray(): com.google.gson.JsonArray {
        val arr = com.google.gson.JsonArray()
        arr.add(1)
        arr.add(16)
        arr.add(0)
        return arr
    }
}
