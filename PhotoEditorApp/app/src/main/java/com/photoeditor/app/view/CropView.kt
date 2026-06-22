package com.photoeditor.app.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.pow
import kotlin.math.sqrt

class CropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var sourceBitmap: Bitmap? = null
        set(value) {
            field = value
            resetCrop()
            invalidate()
        }

    var rotationDegrees: Float = 0f
        set(value) { field = value; invalidate() }

    private var cropRect = RectF()
    private var imageRect = RectF()
    private val imageMatrix = Matrix()

    private val handlePx = 24f * resources.displayMetrics.density
    private val touchPx  = 40f * resources.displayMetrics.density

    private enum class Handle { NONE, TL, TR, BL, BR, MOVE }
    private var activeHandle = Handle.NONE
    private var lastX = 0f; private var lastY = 0f

    private val dimPaint   = Paint().apply { color = Color.argb(160, 0, 0, 0); style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val gridPaint  = Paint().apply { color = Color.argb(80, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val handlePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeImageRect()
        if (cropRect.isEmpty) cropRect.set(imageRect)
    }

    private fun computeImageRect() {
        val bmp = sourceBitmap ?: return
        val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val sw = bmp.width * scale; val sh = bmp.height * scale
        val left = (width - sw) / 2f; val top = (height - sh) / 2f
        imageRect.set(left, top, left + sw, top + sh)
        imageMatrix.reset()
        imageMatrix.setScale(scale, scale)
        imageMatrix.postTranslate(left, top)
    }

    private fun resetCrop() {
        post {
            computeImageRect()
            cropRect.set(imageRect)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = sourceBitmap ?: return
        val cx = (imageRect.left + imageRect.right) / 2f
        val cy = (imageRect.top + imageRect.bottom) / 2f
        canvas.save()
        canvas.rotate(rotationDegrees, cx, cy)
        canvas.drawBitmap(bmp, imageMatrix, null)
        canvas.restore()

        // Dim outside crop
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)

        // Border + grid
        canvas.drawRect(cropRect, borderPaint)
        val tw = cropRect.width() / 3f; val th = cropRect.height() / 3f
        canvas.drawLine(cropRect.left + tw, cropRect.top, cropRect.left + tw, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left + 2*tw, cropRect.top, cropRect.left + 2*tw, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + th, cropRect.right, cropRect.top + th, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + 2*th, cropRect.right, cropRect.top + 2*th, gridPaint)

        // Corner handles — L-shapes
        val hs = handlePx
        drawCorner(canvas, cropRect.left, cropRect.top, hs, 1f, 1f)
        drawCorner(canvas, cropRect.right, cropRect.top, hs, -1f, 1f)
        drawCorner(canvas, cropRect.left, cropRect.bottom, hs, 1f, -1f)
        drawCorner(canvas, cropRect.right, cropRect.bottom, hs, -1f, -1f)
    }

    private fun drawCorner(canvas: Canvas, x: Float, y: Float, len: Float, dx: Float, dy: Float) {
        val p = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND }
        canvas.drawLine(x, y, x + dx * len, y, p)
        canvas.drawLine(x, y, x, y + dy * len, p)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                activeHandle = nearest(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX; val dy = event.y - lastY
                drag(activeHandle, dx, dy)
                lastX = event.x; lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> { activeHandle = Handle.NONE; return true }
        }
        return super.onTouchEvent(event)
    }

    private fun nearest(x: Float, y: Float): Handle {
        val t = touchPx
        return when {
            dist(x, y, cropRect.left,  cropRect.top)    < t -> Handle.TL
            dist(x, y, cropRect.right, cropRect.top)    < t -> Handle.TR
            dist(x, y, cropRect.left,  cropRect.bottom) < t -> Handle.BL
            dist(x, y, cropRect.right, cropRect.bottom) < t -> Handle.BR
            cropRect.contains(x, y)                         -> Handle.MOVE
            else -> Handle.NONE
        }
    }

    private fun drag(h: Handle, dx: Float, dy: Float) {
        val min = 80f
        when (h) {
            Handle.TL -> {
                cropRect.left = (cropRect.left + dx).coerceIn(imageRect.left, cropRect.right - min)
                cropRect.top  = (cropRect.top  + dy).coerceIn(imageRect.top,  cropRect.bottom - min)
            }
            Handle.TR -> {
                cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + min, imageRect.right)
                cropRect.top   = (cropRect.top  + dy).coerceIn(imageRect.top, cropRect.bottom - min)
            }
            Handle.BL -> {
                cropRect.left   = (cropRect.left  + dx).coerceIn(imageRect.left, cropRect.right - min)
                cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + min, imageRect.bottom)
            }
            Handle.BR -> {
                cropRect.right  = (cropRect.right  + dx).coerceIn(cropRect.left + min, imageRect.right)
                cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + min, imageRect.bottom)
            }
            Handle.MOVE -> {
                val nl = (cropRect.left + dx).coerceIn(imageRect.left, imageRect.right  - cropRect.width())
                val nt = (cropRect.top  + dy).coerceIn(imageRect.top,  imageRect.bottom - cropRect.height())
                cropRect.offsetTo(nl, nt)
            }
            Handle.NONE -> {}
        }
    }

    fun getCroppedBitmap(): Bitmap {
        val bmp = sourceBitmap ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        // Rotate the full bitmap if needed
        val rotated = if (rotationDegrees != 0f) {
            val m = Matrix().also { it.postRotate(rotationDegrees) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } else bmp

        // Map crop rect from view coords to bitmap coords
        val scaleX = rotated.width.toFloat()  / imageRect.width()
        val scaleY = rotated.height.toFloat() / imageRect.height()

        // After rotation the imageRect is still the same display rect; re-calc for rotated dims
        val displayScale = minOf(width.toFloat() / rotated.width, height.toFloat() / rotated.height)
        val dw = rotated.width * displayScale; val dh = rotated.height * displayScale
        val dl = (width - dw) / 2f; val dt = (height - dh) / 2f

        val cl = ((cropRect.left - dl) / displayScale).toInt().coerceIn(0, rotated.width)
        val ct = ((cropRect.top  - dt) / displayScale).toInt().coerceIn(0, rotated.height)
        val cw = (cropRect.width()  / displayScale).toInt().coerceIn(1, rotated.width  - cl)
        val ch = (cropRect.height() / displayScale).toInt().coerceIn(1, rotated.height - ct)

        return Bitmap.createBitmap(rotated, cl, ct, cw, ch)
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
        sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
}
