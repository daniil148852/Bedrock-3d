package com.bedrock3d.export

import com.bedrock3d.model.Model3D
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject

class EntityGenerator {
    
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    
    fun generateEntityFile(model: Model3D, namespace: String = "custom"): String {
        val entity = JsonObject()
        entity.addProperty("format_version", "1.10.0")
        
        val minecraftEntity = JsonObject()
        
        val description = JsonObject()
        description.addProperty("identifier", "$namespace:${model.name.lowercase().replace(" ", "_")}")
        
        description.addProperty("runtime_identifier", "minecraft:pig")
        
        val scripts = JsonObject()
        scripts.addProperty("scale", "1.0")
        description.add("scripts", scripts)
        
        val animations = JsonObject()
        description.add("animations", animations)
        
        val geometry = JsonObject()
        geometry.addProperty("default", "geometry.${model.name.lowercase().replace(" ", "_")}")
        description.add("geometry", geometry)
        
        val renderController = JsonObject()
        renderController.addProperty("default", "controller.render.default")
        description.add("render_controllers", renderController)
        
        val spawnEgg = JsonObject()
        spawnEgg.addProperty("base_color", "#4287f5")
        spawnEgg.addProperty("overlay_color", "#42f5b0")
        description.add("spawn_egg", spawnEgg)
        
        minecraftEntity.add("description", description)
        
        val components = JsonObject()
        
        val typeFamily = JsonObject()
        val familyList = com.google.gson.JsonArray()
        familyList.add("mob")
        familyList.add("${model.name.lowercase().replace(" ", "_")}")
        typeFamily.add("list", familyList)
        components.add("minecraft:type_family", typeFamily)
        
        components.add("minecraft:physics", JsonObject())
        
        val movement = JsonObject()
        movement.addProperty("value", 0.0)
        components.add("minecraft:movement", movement)
        
        val navigation = JsonObject()
        navigation.addProperty("can_path_over_water", false)
        navigation.addProperty("avoid_water", true)
        navigation.addProperty("avoid_damage_blocks", true)
        components.add("minecraft:navigation.walk", navigation)
        
        val collisionBox = JsonObject()
        collisionBox.addProperty("width", 1.0)
        collisionBox.addProperty("height", 1.0)
        components.add("minecraft:collision_box", collisionBox)
        
        val pushable = JsonObject()
        pushable.addProperty("is_pushable", false)
        pushable.addProperty("is_pushable_by_piston", false)
        components.add("minecraft:pushable", pushable)
        
        val pushThrough = JsonObject()
        pushThrough.addProperty("value", 0)
        components.add("minecraft:push_through", pushThrough)
        
        components.add("minecraft:scale", createScaleComponent(1.0))
        
        components.add("minecraft:tick_world", createTickWorld())
        
        minecraftEntity.add("components", components)
        
        entity.add("minecraft:entity", minecraftEntity)
        
        return gson.toJson(entity)
    }
    
    fun generateClientEntityFile(model: Model3D, namespace: String = "custom", textureName: String = "texture"): String {
        val clientEntity = JsonObject()
        clientEntity.addProperty("format_version", "1.10.0")
        
        val minecraftClientEntity = JsonObject()
        
        val description = JsonObject()
        description.addProperty("identifier", "$namespace:${model.name.lowercase().replace(" ", "_")}")
        
        val materials = JsonObject()
        materials.addProperty("default", "entity_alphatest")
        description.add("materials", materials)
        
        val textures = JsonObject()
        textures.addProperty("default", "textures/entity/$textureName")
        description.add("textures", textures)
        
        val geometry = JsonObject()
        geometry.addProperty("default", "geometry.${model.name.lowercase().replace(" ", "_")}")
        description.add("geometry", geometry)
        
        val renderControllers = com.google.gson.JsonArray()
        renderControllers.add("controller.render.default")
        description.add("render_controllers", renderControllers)
        
        val spawnEgg = JsonObject()
        spawnEgg.addProperty("base_color", "#4287f5")
        spawnEgg.addProperty("overlay_color", "#42f5b0")
        description.add("spawn_egg", spawnEgg)
        
        minecraftClientEntity.add("description", description)
        
        clientEntity.add("minecraft:client_entity", minecraftClientEntity)
        
        return gson.toJson(clientEntity)
    }
    
    fun generateRenderController(model: Model3D, namespace: String = "custom"): String {
        val rc = JsonObject()
        rc.addProperty("format_version", "1.8.0")
        
        val renderControllers = JsonObject()
        
        val controller = JsonObject()
        
        val arrays = JsonObject()
        
        val textures = JsonObject()
        val textureArray = com.google.gson.JsonArray()
        textureArray.add("Texture.default")
        textures.add("textures", textureArray)
        arrays.add("textures", textures)
        
        controller.add("arrays", arrays)
        
        val geometry = com.google.gson.JsonArray()
        geometry.add("Geometry.default")
        controller.add("geometry", geometry)
        
        val materials = com.google.gson.JsonArray()
        materials.add("Material.default")
        controller.add("materials", materials)
        
        val texturesController = com.google.gson.JsonArray()
        texturesController.add("Array.textures[0]")
        controller.add("textures", texturesController)
        
        renderControllers.add("controller.render.default", controller)
        rc.add("render_controllers", renderControllers)
        
        return gson.toJson(rc)
    }
    
    fun generateBehaviorEntityFile(model: Model3D, namespace: String = "custom"): String {
        return generateEntityFile(model, namespace)
    }
    
    private fun createScaleComponent(scale: Double): JsonObject {
        val json = JsonObject()
        json.addProperty("value", scale)
        return json
    }
    
    private fun createTickWorld(): JsonObject {
        val json = JsonObject()
        json.addProperty("never_despawn", true)
        json.addProperty("radius", 2)
        return json
    }
}
