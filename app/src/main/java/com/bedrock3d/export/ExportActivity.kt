package com.bedrock3d.export

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bedrock3d.R
import com.bedrock3d.databinding.ActivityExportBinding
import com.bedrock3d.model.Model3D
import com.bedrock3d.viewer.ModelViewerActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ExportActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityExportBinding
    private lateinit var model: Model3D
    private lateinit var exportManager: ExportManager
    
    private var selectedScale: Float = 1f
    private var namespace: String = "custom"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())
        
        exportManager = ExportManager(this)
        
        @Suppress("DEPRECATION")
        model = intent.getSerializableExtra("model") as? Model3D 
            ?: run {
                finish()
                return
            }
        
        setupViews()
        checkPermissions()
    }
    
    private fun setupViews() {
        binding.modelName.setText(model.name)
        
        binding.namespaceEditText.setText(namespace)
        
        binding.scaleRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedScale = when (checkedId) {
                R.id.scale_025 -> 0.25f
                R.id.scale_05 -> 0.5f
                R.id.scale_1 -> 1f
                R.id.scale_2 -> 2f
                R.id.scale_custom -> {
                    binding.customScaleInput.isEnabled = true
                    binding.customScaleInput.text.toString().toFloatOrNull() ?: 1f
                }
                else -> 1f
            }
            
            if (checkedId != R.id.scale_custom) {
                binding.customScaleInput.isEnabled = false
            }
        }
        
        binding.exportButton.setOnClickListener {
            if (binding.scaleRadioGroup.checkedRadioButtonId == R.id.scale_custom) {
                selectedScale = binding.customScaleInput.text.toString().toFloatOrNull() ?: 1f
            }
            namespace = binding.namespaceEditText.text.toString().ifEmpty { "custom" }
            performExport()
        }
        
        binding.previewButton.setOnClickListener {
            openPreview()
        }
    }
    
    private fun openPreview() {
        val intent = android.content.Intent(this, ModelViewerActivity::class.java)
        intent.putExtra("model", model as java.io.Serializable)
        startActivity(intent)
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
            Toast.makeText(this, "Разрешение на запись необходимо для экспорта", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun performExport() {
        val modelName = binding.modelName.text.toString().ifEmpty { model.name }
        val updatedModel = model.copy(name = modelName)
        
        binding.exportProgress.setVisibility(android.view.View.VISIBLE)
        binding.exportButton.setEnabled(false)
        
        lifecycleScope.launch {
            val result = exportManager.exportModel(
                model = updatedModel,
                scale = selectedScale,
                namespace = namespace
            )
            
            binding.exportProgress.setVisibility(android.view.View.GONE)
            binding.exportButton.setEnabled(true)
            
            result.fold(
                onSuccess = { file ->
                    showExportSuccessDialog(file)
                },
                onFailure = { error ->
                    showExportErrorDialog(error.message ?: "Неизвестная ошибка")
                }
            )
        }
    }
    
    private fun showExportSuccessDialog(file: java.io.File) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Экспорт завершён")
            .setMessage("Файл успешно сохранён:\n${file.absolutePath}\n\nВы можете:\n• Открыть в Minecraft\n• Поделиться файлом")
            .setPositiveButton("Открыть в Minecraft") { _, _ ->
                exportManager.openInMinecraft(file)
            }
            .setNeutralButton("Поделиться") { _, _ ->
                exportManager.shareMcaddon(file)
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }
    
    private fun showExportErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Ошибка экспорта")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
