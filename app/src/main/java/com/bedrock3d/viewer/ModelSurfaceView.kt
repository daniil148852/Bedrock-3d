package com.bedrock3d.viewer

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.bedrock3d.model.Model3D

class ModelSurfaceView(context: Context) : GLSurfaceView(context) {
    
    private val renderer: ModelRenderer
    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var mode: TouchMode = TouchMode.ROTATE
    
    enum class TouchMode {
        ROTATE, PAN, SCALE
    }
    
    init {
        setEGLContextClientVersion(2)
        renderer = ModelRenderer()
        setRenderer(renderer)
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }
    
    fun setModel(model: Model3D) {
        queueEvent { renderer.setModel(model) }
    }
    
    fun setTouchMode(mode: TouchMode) {
        this.mode = mode
    }
    
    fun resetView() {
        queueEvent { renderer.resetView() }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - previousX
                val dy = event.y - previousY
                
                when (mode) {
                    TouchMode.ROTATE -> {
                        queueEvent { renderer.rotate(dx, dy) }
                    }
                    TouchMode.PAN -> {
                        queueEvent { renderer.pan(dx, dy) }
                    }
                    TouchMode.SCALE -> {
                        queueEvent { renderer.scale(dy) }
                    }
                }
                
                previousX = event.x
                previousY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    mode = TouchMode.SCALE
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount < 2) {
                    mode = TouchMode.ROTATE
                }
            }
        }
        return true
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
