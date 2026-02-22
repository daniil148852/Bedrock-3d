package com.bedrock3d

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bedrock3d.databinding.ActivityMainBinding
import com.bedrock3d.export.ExportActivity
import com.bedrock3d.model.Model3D
import com.bedrock3d.model.ModelLoaderManager
import com.bedrock3d.viewer.ModelViewerActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val modelLoader = ModelLoaderManager()
    private var currentModel: Model3D? = null
    private var currentModelUri: Uri? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())
        
        setSupportActionBar(binding.toolbar)
        
        checkPermissions()
        
        setupViews()
        
        handleIntent(intent)
    }
    
    private fun setupViews() {
        binding.importButton.setOnClickListener {
            openFilePicker()
        }
        
        binding.previewButton.setOnClickListener {
            currentModel?.let { model ->
                val intent = Intent(this, ModelViewerActivity::class.java)
                intent.putExtra("model", model as java.io.Serializable)
                startActivity(intent)
            } ?: run {
                Toast.makeText(this, R.string.no_model_loaded, Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.exportButton.setOnClickListener {
            currentModel?.let { model ->
                val intent = Intent(this, ExportActivity::class.java)
                intent.putExtra("model", model as java.io.Serializable)
                startActivity(intent)
            } ?: run {
                Toast.makeText(this, R.string.no_model_loaded, Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.textureButton.setOnClickListener {
            openTexturePicker()
        }
    }
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadModel(it) }
    }
    
    private val texturePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            binding.texturePath.setText(getFileName(it))
        }
    }
    
    private fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("*/*"))
    }
    
    private fun openTexturePicker() {
        texturePickerLauncher.launch(arrayOf("image/*"))
    }
    
    private fun loadModel(uri: Uri) {
        val fileName = getFileName(uri)
        
        binding.progressIndicator.setVisibility(android.view.View.VISIBLE)
        binding.statusText.setText(R.string.loading_model)
        
        lifecycleScope.launch {
            val result = modelLoader.loadModel(this@MainActivity, uri, fileName)
            
            binding.progressIndicator.setVisibility(android.view.View.GONE)
            
            result.fold(
                onSuccess = { model ->
                    currentModel = model
                    currentModelUri = uri
                    updateModelInfo(model, fileName)
                    binding.statusText.setText(R.string.model_loaded)
                },
                onFailure = { error ->
                    binding.statusText.setText(getString(R.string.error_loading, error.message))
                    showErrorDialog(error.message ?: "Unknown error")
                }
            )
        }
    }
    
    private fun updateModelInfo(model: Model3D, fileName: String) {
        val bb = model.boundingBox
        
        val info = StringBuilder()
        info.appendLine("Файл: $fileName")
        info.appendLine("Название: ${model.name}")
        info.appendLine("Мешей: ${model.meshes.size}")
        info.appendLine("Вершин: ${model.meshes.sumOf { it.vertices.size }}")
        info.appendLine("Размер: ${String.format("%.2f", bb.size.x)} x ${String.format("%.2f", bb.size.y)} x ${String.format("%.2f", bb.size.z)}")
        
        if (model.materials.isNotEmpty()) {
            info.appendLine("Материалов: ${model.materials.size}")
        }
        
        if (model.bones.isNotEmpty()) {
            info.appendLine("Костей: ${model.bones.size}")
        }
        
        binding.modelInfo.setText(info.toString())
        
        binding.previewButton.setEnabled(true)
        binding.exportButton.setEnabled(true)
    }
    
    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                requestPermissionLauncher.launch(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            loadModel(uri)
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            R.id.action_help -> {
                showHelpDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about)
            .setMessage(R.string.about_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help)
            .setMessage(R.string.help_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
