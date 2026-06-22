package com.photoeditor.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class MarkupView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Tool { PEN, ERASER }

    private data class Stroke(val path: Path, val paint: Paint)

    private val strokes = mutableListOf<Stroke>()
    private val currentPath = Path()
    var currentTool = Tool.PEN
    var strokeColor: Int = Color.RED
    var strokeWidth: Float = 8f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun makePaint(): Paint = Paint().apply {
        color = if (currentTool == Tool.ERASER) Color.TRANSPARENT else strokeColor
        this.strokeWidth = if (currentTool == Tool.ERASER) strokeWidth * 3 else strokeWidth
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        if (currentTool == Tool.ERASER) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var activePaint = makePaint()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { canvas.drawPath(it.path, it.paint) }
        canvas.drawPath(currentPath, activePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activePaint = makePaint()
                currentPath.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                strokes.add(Stroke(Path(currentPath), Paint(activePaint)))
                currentPath.reset()
                invalidate()
            }
        }
        return true
    }

    fun undo() {
        if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); invalidate() }
    }

    fun clear() {
        strokes.clear(); currentPath.reset(); invalidate()
    }

    fun flattenOnto(base: Bitmap): Bitmap {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val scaleX = base.width.toFloat() / width
        val scaleY = base.height.toFloat() / height
        canvas.scale(scaleX, scaleY)
        strokes.forEach { canvas.drawPath(it.path, it.paint) }
        return result
    }
}
