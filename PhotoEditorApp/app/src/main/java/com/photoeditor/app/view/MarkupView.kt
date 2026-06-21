package com.photoeditor.app.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class MarkupView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Tool { PEN, HIGHLIGHTER, ERASER }

    var currentTool = Tool.PEN
    var currentColor = Color.RED
    var currentStrokeWidth = 6f

    private data class Stroke(
        val path: Path,
        val color: Int,
        val strokeWidth: Float,
        val isHighlighter: Boolean,
        val isEraser: Boolean
    )

    private val strokes = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()
    private var activePath: Path? = null
    private var activeStroke: Stroke? = null

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        for (s in strokes) {
            strokePaint.strokeWidth = s.strokeWidth
            strokePaint.xfermode = if (s.isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
            if (s.isHighlighter) {
                strokePaint.color = (s.color and 0x00FFFFFF) or 0x66000000
            } else {
                strokePaint.color = s.color
            }
            canvas.drawPath(s.path, strokePaint)
        }
        strokePaint.xfermode = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                redoStack.clear()
                val path = Path().also { it.moveTo(event.x, event.y) }
                val stroke = Stroke(
                    path = path,
                    color = currentColor,
                    strokeWidth = currentStrokeWidth,
                    isHighlighter = currentTool == Tool.HIGHLIGHTER,
                    isEraser = currentTool == Tool.ERASER
                )
                strokes.add(stroke)
                activePath = path
                activeStroke = stroke
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                activePath?.quadTo(event.x, event.y, event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                activePath = null; activeStroke = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun undo() {
        if (strokes.isNotEmpty()) { redoStack.add(strokes.removeLast()); invalidate() }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) { strokes.add(redoStack.removeLast()); invalidate() }
    }

    fun clear() { strokes.clear(); redoStack.clear(); invalidate() }

    fun compositeOnto(background: Bitmap): Bitmap {
        val result = background.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val scaleX = background.width.toFloat() / width
        val scaleY = background.height.toFloat() / height

        for (s in strokes) {
            val scaledPath = Path()
            val m = Matrix().also { it.setScale(scaleX, scaleY) }
            s.path.transform(m, scaledPath)

            strokePaint.strokeWidth = s.strokeWidth * ((scaleX + scaleY) / 2f)
            strokePaint.xfermode = if (s.isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
            strokePaint.color = if (s.isHighlighter) (s.color and 0x00FFFFFF) or 0x66000000 else s.color
            canvas.drawPath(scaledPath, strokePaint)
        }
        strokePaint.xfermode = null
        return result
    }
}
