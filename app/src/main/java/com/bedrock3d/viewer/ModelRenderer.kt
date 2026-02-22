package com.bedrock3d.viewer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.bedrock3d.model.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class ModelRenderer : GLSurfaceView.Renderer {
    
    private var model: Model3D? = null
    private val meshes = mutableListOf<MeshRenderData>()
    
    private var rotationX = 0f
    private var rotationY = 0f
    private var zoom = 5f
    private var panX = 0f
    private var panY = 0f
    
    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpMatrixHandle = 0
    private var mvMatrixHandle = 0
    private var lightPosHandle = 0
    private var colorHandle = 0
    
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    
    fun setModel(newModel: Model3D) {
        model = newModel
        setupMeshes()
    }
    
    private fun setupMeshes() {
        meshes.clear()
        model?.meshes?.forEach { mesh ->
            meshes.add(MeshRenderData(mesh))
        }
        
        val bb = model?.boundingBox
        if (bb != null) {
            val maxSize = maxOf(bb.size.x, bb.size.y, bb.size.z)
            zoom = maxSize * 3f
        }
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.15f, 0.15f, 0.2f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        
        initShaders()
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        
        val aspect = width.toFloat() / height.toFloat()
        setPerspectiveMatrix(projectionMatrix, 45f, aspect, 0.1f, 1000f)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        if (model == null || meshes.isEmpty()) {
            return
        }
        
        setIdentityMatrix(viewMatrix)
        setIdentityMatrix(modelMatrix)
        
        translateMatrix(viewMatrix, panX, panY, -zoom)
        rotateMatrix(viewMatrix, rotationX, 1f, 0f, 0f)
        rotateMatrix(viewMatrix, rotationY, 0f, 1f, 0f)
        
        GLES20.glUseProgram(program)
        
        multiplyMatrix(mvMatrix, viewMatrix, modelMatrix)
        multiplyMatrix(mvpMatrix, projectionMatrix, mvMatrix)
        
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(mvMatrixHandle, 1, false, mvMatrix, 0)
        GLES20.glUniform3f(lightPosHandle, 5f, 10f, 7f)
        GLES20.glUniform4f(colorHandle, 0.6f, 0.7f, 0.9f, 1.0f)
        
        meshes.forEach { meshData ->
            drawMesh(meshData)
        }
    }
    
    private fun drawMesh(meshData: MeshRenderData) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshData.vertexBuffer)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 24, 0)
        
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 24, 12)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, meshData.vertexCount)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
    
    private fun initShaders() {
        val vertexShader = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            
            uniform mat4 uMVPMatrix;
            uniform mat4 uMVMatrix;
            uniform vec3 uLightPos;
            
            varying vec3 vNormal;
            varying vec3 vLightDir;
            
            void main() {
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
                vNormal = mat3(uMVMatrix) * aNormal;
                vec4 vertexPos = uMVMatrix * vec4(aPosition, 1.0);
                vLightDir = uLightPos - vertexPos.xyz;
            }
        """
        
        val fragmentShader = """
            precision mediump float;
            
            varying vec3 vNormal;
            varying vec3 vLightDir;
            
            uniform vec4 uColor;
            
            void main() {
                vec3 normal = normalize(vNormal);
                vec3 lightDir = normalize(vLightDir);
                
                float ambient = 0.3;
                float diffuse = max(dot(normal, lightDir), 0.0) * 0.7;
                
                gl_FragColor = vec4(uColor.rgb * (ambient + diffuse), uColor.a);
            }
        """
        
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        mvMatrixHandle = GLES20.glGetUniformLocation(program, "uMVMatrix")
        lightPosHandle = GLES20.glGetUniformLocation(program, "uLightPos")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
    }
    
    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }
    
    fun rotate(dx: Float, dy: Float) {
        rotationY += dx * 0.5f
        rotationX += dy * 0.5f
    }
    
    fun scale(delta: Float) {
        zoom = maxOf(0.5f, zoom - delta * 0.01f)
    }
    
    fun pan(dx: Float, dy: Float) {
        panX += dx * 0.005f
        panY -= dy * 0.005f
    }
    
    fun resetView() {
        rotationX = 0f
        rotationY = 0f
        panX = 0f
        panY = 0f
        model?.boundingBox?.let { bb ->
            zoom = maxOf(bb.size.x, bb.size.y, bb.size.z) * 3f
        }
    }
    
    private class MeshRenderData(mesh: Mesh) {
        val vertexBuffer: Int
        val vertexCount: Int
        
        init {
            val buffers = IntArray(1)
            GLES20.glGenBuffers(1, buffers, 0)
            vertexBuffer = buffers[0]
            
            val vertices = FloatArray(mesh.vertices.size * 6)
            mesh.vertices.forEachIndexed { idx, vertex ->
                val i = idx * 6
                vertices[i] = vertex.position.x
                vertices[i + 1] = vertex.position.y
                vertices[i + 2] = vertex.position.z
                vertices[i + 3] = vertex.normal.x
                vertices[i + 4] = vertex.normal.y
                vertices[i + 5] = vertex.normal.z
            }
            
            vertexCount = mesh.vertices.size
            
            val bb = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
            val fb = bb.asFloatBuffer()
            fb.put(vertices)
            fb.position(0)
            
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, fb, GLES20.GL_STATIC_DRAW)
        }
    }
    
    private fun setIdentityMatrix(m: FloatArray) {
        for (i in 0..15) m[i] = 0f
        m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
    }
    
    private fun setPerspectiveMatrix(m: FloatArray, fov: Float, aspect: Float, near: Float, far: Float) {
        val f = 1.0f / tan(fov * PI / 360.0).toFloat()
        for (i in 0..15) m[i] = 0f
        m[0] = f / aspect
        m[5] = f
        m[10] = (far + near) / (near - far)
        m[11] = -1f
        m[14] = 2 * far * near / (near - far)
    }
    
    private fun translateMatrix(m: FloatArray, x: Float, y: Float, z: Float) {
        m[12] += m[0] * x + m[4] * y + m[8] * z
        m[13] += m[1] * x + m[5] * y + m[9] * z
        m[14] += m[2] * x + m[6] * y + m[10] * z
    }
    
    private fun rotateMatrix(m: FloatArray, angle: Float, x: Float, y: Float, z: Float) {
        val rad = angle * PI.toFloat() / 180f
        val c = cos(rad)
        val s = sin(rad)
        
        val len = sqrt(x * x + y * y + z * z)
        val nx = x / len
        val ny = y / len
        val nz = z / len
        
        val r = FloatArray(16)
        r[0] = nx * nx * (1 - c) + c
        r[1] = ny * nx * (1 - c) + nz * s
        r[2] = nz * nx * (1 - c) - ny * s
        r[4] = nx * ny * (1 - c) - nz * s
        r[5] = ny * ny * (1 - c) + c
        r[6] = nz * ny * (1 - c) + nx * s
        r[8] = nx * nz * (1 - c) + ny * s
        r[9] = ny * nz * (1 - c) - nx * s
        r[10] = nz * nz * (1 - c) + c
        r[15] = 1f
        
        val result = FloatArray(16)
        for (i in 0..3) {
            for (j in 0..3) {
                result[i * 4 + j] = 0f
                for (k in 0..3) {
                    result[i * 4 + j] += m[i * 4 + k] * r[k * 4 + j]
                }
            }
        }
        for (i in 0..15) m[i] = result[i]
    }
    
    private fun multiplyMatrix(result: FloatArray, a: FloatArray, b: FloatArray) {
        for (i in 0..3) {
            for (j in 0..3) {
                result[i * 4 + j] = 0f
                for (k in 0..3) {
                    result[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]
                }
            }
        }
    }
}
