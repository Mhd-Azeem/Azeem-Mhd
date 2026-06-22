package com.photoeditor.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.photoeditor.app.view.MarkupView
import java.io.File

class MarkupActivity : AppCompatActivity() {

    private lateinit var markupView: MarkupView
    private lateinit var imageView: ImageView
    private var background: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_markup)

        markupView = findViewById(R.id.markupView)
        imageView  = findViewById(R.id.markupImageView)

        background = loadFromCache("temp_edit.png")
        background?.let { imageView.setImageBitmap(it) }

        // Tool selection
        val btnPen    = findViewById<ImageButton>(R.id.btnPen)
        val btnHl     = findViewById<Button>(R.id.btnHighlighter)
        val btnEraser = findViewById<Button>(R.id.btnEraser)

        fun selectTool(t: MarkupView.Tool) {
            markupView.currentTool = t
            val accent = getColor(R.color.accent)
            val neutral = Color.parseColor("#444444")
            btnPen.backgroundTintList    = android.content.res.ColorStateList.valueOf(if (t == MarkupView.Tool.PEN) accent else neutral)
            btnHl.backgroundTintList     = android.content.res.ColorStateList.valueOf(if (t == MarkupView.Tool.HIGHLIGHTER) accent else neutral)
            btnEraser.backgroundTintList = android.content.res.ColorStateList.valueOf(if (t == MarkupView.Tool.ERASER) accent else neutral)
        }

        btnPen.setOnClickListener    { selectTool(MarkupView.Tool.PEN) }
        btnHl.setOnClickListener     { selectTool(MarkupView.Tool.HIGHLIGHTER) }
        btnEraser.setOnClickListener { selectTool(MarkupView.Tool.ERASER) }
        selectTool(MarkupView.Tool.PEN)

        // Color palette
        val palette = listOf(
            Color.WHITE, Color.BLACK, Color.RED,
            Color.parseColor("#FF9500"), Color.YELLOW,
            Color.GREEN, Color.CYAN, Color.BLUE,
            Color.MAGENTA, Color.parseColor("#FF69B4")
        )
        val container = findViewById<LinearLayout>(R.id.colorPalette)
        val dp = resources.displayMetrics.density
        for (c in palette) {
            val dot = View(this).apply {
                val size = (36 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                }
                setBackgroundColor(c)
                background = android.graphics.drawable.GradientDrawable().also { d ->
                    d.shape = android.graphics.drawable.GradientDrawable.OVAL
                    d.setColor(c)
                }
                setOnClickListener {
                    markupView.currentColor = c
                    container.children.forEach { v -> v.scaleX = 1f; v.scaleY = 1f }
                    scaleX = 1.3f; scaleY = 1.3f
                }
            }
            container.addView(dot)
        }

        // Stroke size
        findViewById<SeekBar>(R.id.seekMarkupSize).apply {
            progress = 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, f: Boolean) {
                    markupView.currentStrokeWidth = (p + 1).toFloat()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        // Undo
        findViewById<Button>(R.id.btnMarkupUndo).setOnClickListener { markupView.undo() }

        // Cancel / Done
        findViewById<Button>(R.id.btnMarkupCancel).setOnClickListener {
            setResult(RESULT_CANCELED); finish()
        }
        findViewById<Button>(R.id.btnMarkupDone).setOnClickListener {
            background?.let { bg ->
                val result = markupView.compositeOnto(bg)
                saveToCache(result, "temp_result.png")
                setResult(RESULT_OK)
                finish()
            } ?: run { setResult(RESULT_CANCELED); finish() }
        }
    }

    private val LinearLayout.children: Sequence<View>
        get() = sequence { for (i in 0 until childCount) yield(getChildAt(i)) }

    private fun loadFromCache(name: String): Bitmap? {
        val f = File(cacheDir, name)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    private fun saveToCache(bmp: Bitmap, name: String) {
        File(cacheDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
