package com.photoeditor.app

import android.content.ContentValues
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream

class EditActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var originalBitmap: Bitmap
    private lateinit var currentBitmap: Bitmap

    private var currentFilter: String = "none"
    private var brightnessValue: Int = 100
    private var contrastValue: Int = 100

    data class Filter(val name: String, val key: String)

    private val filters = listOf(
        Filter("Original", "none"),
        Filter("Grayscale", "grayscale"),
        Filter("Sepia", "sepia"),
        Filter("Cool", "cool"),
        Filter("Warm", "warm"),
        Filter("Invert", "invert")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        imageView = findViewById(R.id.imageView)

        val uriString = intent.getStringExtra("imageUri") ?: return
        val uri = Uri.parse(uriString)

        originalBitmap = loadBitmap(uri)
        currentBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        imageView.setImageBitmap(currentBitmap)

        setupFilters()
        setupSliders()

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveImage() }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        val inputStream = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(inputStream)
    }

    private fun setupFilters() {
        val container = findViewById<LinearLayout>(R.id.filtersContainer)
        filters.forEach { filter ->
            val btn = Button(this).apply {
                text = filter.name
                textSize = 12f
                setPadding(24, 16, 24, 16)
                setBackgroundColor(Color.parseColor("#2a2a2a"))
                setTextColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(8, 8, 8, 8)
                layoutParams = params
                setOnClickListener {
                    currentFilter = filter.key
                    applyEdits()
                }
            }
            container.addView(btn)
        }
    }

    private fun setupSliders() {
        findViewById<SeekBar>(R.id.seekBrightness).apply {
            progress = brightnessValue
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    brightnessValue = progress
                    applyEdits()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        findViewById<SeekBar>(R.id.seekContrast).apply {
            progress = contrastValue
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    contrastValue = progress
                    applyEdits()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
    }

    private fun applyEdits() {
        var bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        bitmap = applyFilter(bitmap, currentFilter)
        bitmap = applyBrightnessContrast(bitmap, brightnessValue, contrastValue)
        currentBitmap = bitmap
        imageView.setImageBitmap(currentBitmap)
    }

    private fun applyFilter(src: Bitmap, filter: String): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = when (filter) {
            "grayscale" -> ColorMatrix().apply { setSaturation(0f) }
            "sepia" -> ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f,     0f,     0f,     1f, 0f
            ))
            "cool" -> ColorMatrix(floatArrayOf(
                0.8f, 0f,   0f,   0f, 0f,
                0f,   0.9f, 0f,   0f, 0f,
                0f,   0f,   1.2f, 0f, 0f,
                0f,   0f,   0f,   1f, 0f
            ))
            "warm" -> ColorMatrix(floatArrayOf(
                1.2f, 0f,   0f,   0f, 0f,
                0f,   1.0f, 0f,   0f, 0f,
                0f,   0f,   0.8f, 0f, 0f,
                0f,   0f,   0f,   1f, 0f
            ))
            "invert" -> ColorMatrix(floatArrayOf(
                -1f, 0f,  0f,  0f, 255f,
                0f,  -1f, 0f,  0f, 255f,
                0f,  0f,  -1f, 0f, 255f,
                0f,  0f,  0f,  1f, 0f
            ))
            else -> ColorMatrix()
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun applyBrightnessContrast(src: Bitmap, brightness: Int, contrast: Int): Bitmap {
        val b = (brightness - 100).toFloat()
        val c = contrast / 100f

        val colorMatrix = ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, b,
            0f, c, 0f, 0f, b,
            0f, 0f, c, 0f, b,
            0f, 0f, 0f, 1f, 0f
        ))

        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun saveImage() {
        val filename = "PhotoEditor_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            val outputStream: OutputStream? = contentResolver.openOutputStream(it)
            outputStream?.use { stream ->
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            Toast.makeText(this, "Photo saved to Gallery!", Toast.LENGTH_SHORT).show()
        }
    }
}
