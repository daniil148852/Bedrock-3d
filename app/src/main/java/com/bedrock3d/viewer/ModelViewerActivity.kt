package com.bedrock3d.viewer

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.bedrock3d.R
import com.bedrock3d.databinding.ActivityModelViewerBinding
import com.bedrock3d.model.Model3D

class ModelViewerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityModelViewerBinding
    private var model: Model3D? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelViewerBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        @Suppress("DEPRECATION")
        model = intent.getSerializableExtra("model") as? Model3D
        
        model?.let { m ->
            binding.surfaceView.setModel(m)
            supportActionBar?.title = m.name
            updateModelInfo(m)
        }
        
        setupControls()
    }
    
    private fun setupControls() {
        binding.rotateButton.setOnClickListener {
            binding.surfaceView.setTouchMode(com.bedrock3d.viewer.ModelSurfaceView.TouchMode.ROTATE)
            updateModeButtons()
        }
        
        binding.panButton.setOnClickListener {
            binding.surfaceView.setTouchMode(com.bedrock3d.viewer.ModelSurfaceView.TouchMode.PAN)
            updateModeButtons()
        }
        
        binding.zoomButton.setOnClickListener {
            binding.surfaceView.setTouchMode(com.bedrock3d.viewer.ModelSurfaceView.TouchMode.SCALE)
            updateModeButtons()
        }
        
        binding.resetButton.setOnClickListener {
            binding.surfaceView.resetView()
        }
        
        updateModeButtons()
    }
    
    private fun updateModeButtons() {
        binding.rotateButton.setText(R.string.rotate)
        binding.panButton.setText(R.string.pan)
        binding.zoomButton.setText(R.string.zoom)
    }
    
    private fun updateModelInfo(model: Model3D) {
        val bb = model.boundingBox
        val info = """
            Вершин: ${model.meshes.sumOf { it.vertices.size }}
            Мешей: ${model.meshes.size}
            Размер: ${String.format("%.2f", bb.size.x)} x ${String.format("%.2f", bb.size.y)} x ${String.format("%.2f", bb.size.z)}
        """.trimIndent()
        binding.modelInfo.setText(info)
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.viewer_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_reset -> {
                binding.surfaceView.resetView()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onPause() {
        super.onPause()
        binding.surfaceView.onPause()
    }
    
    override fun onResume() {
        super.onResume()
        binding.surfaceView.onResume()
    }
}
