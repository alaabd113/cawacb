package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

@SuppressLint("ViewConstructor")
class CosmosGlSurfaceView(
    context: Context,
    val cosmosRenderer: CosmosGlRenderer
) : GLSurfaceView(context) {

    private var lastX = 0f
    private var lastY = 0f
    private var isScaling = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                queueEvent { cosmosRenderer.onPinchZoom(factor) }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                queueEvent { cosmosRenderer.onDoubleTap() }
                return true
            }
        }
    )

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(cosmosRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isScaling && event.pointerCount == 1) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    queueEvent { cosmosRenderer.onDrag(dx, dy) }
                } else {
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (event.actionIndex == 0) 1 else 0
                if (remaining < event.pointerCount) {
                    lastX = event.getX(remaining)
                    lastY = event.getY(remaining)
                }
            }
        }
        return true
    }
}
