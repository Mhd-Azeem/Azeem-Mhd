package com.photoeditor.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.photoeditor.app.view.CropView
import java.io.File

class CropRotateActivity : AppCompatActivity() {

    private lateinit var cropView: CropView
    private var originalBitmap: Bitmap? = null
    private var flipH = false
    private var flipV = false
    private var extraRotation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        cropView = findViewById(R.id.cropView)

        originalBitmap = loadFromCache("temp_edit.png")
        cropView.sourceBitmap = originalBitmap

        val seekStraighten = findViewById<SeekBar>(R.id.seekStraighten)
        val tvStraighten   = findViewById<TextView>(R.id.tvStraightenVal)

        seekStraighten.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val degrees = (progress - 45).toFloat()
                cropView.rotationDegrees = degrees
                tvStraighten.text = "${degrees.toInt()}°"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Perspective sliders (visual indication only — we store value for future use)
        val tvPerspH = findViewById<TextView>(R.id.tvPerspHVal)
        val tvPerspV = findViewById<TextView>(R.id.tvPerspVVal)
        findViewById<SeekBar>(R.id.seekPerspectiveH).setOnSeekBarChangeListener(simple { p ->
            tvPerspH.text = "${p - 100}"
        })
        findViewById<SeekBar>(R.id.seekPerspectiveV).setOnSeekBarChangeListener(simple { p ->
            tvPerspV.text = "${p - 100}"
        })

        findViewById<Button>(R.id.btnRotate90).setOnClickListener {
            extraRotation = (extraRotation + 90) % 360
            rebuildTransform()
        }
        findViewById<Button>(R.id.btnFlipH).setOnClickListener {
            flipH = !flipH; rebuildTransform()
        }
        findViewById<Button>(R.id.btnFlipV).setOnClickListener {
            flipV = !flipV; rebuildTransform()
        }

        findViewById<Button>(R.id.btnCropCancel).setOnClickListener {
            setResult(RESULT_CANCELED); finish()
        }
        findViewById<Button>(R.id.btnCropDone).setOnClickListener {
            val result = cropView.getCroppedBitmap()
            saveToCache(result, "temp_result.png")
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun rebuildTransform() {
        val bmp = originalBitmap ?: return
        val m = Matrix()
        if (extraRotation != 0) m.postRotate(extraRotation.toFloat(), bmp.width / 2f, bmp.height / 2f)
        if (flipH) m.postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f)
        if (flipV) m.postScale(1f, -1f, bmp.width / 2f, bmp.height / 2f)
        cropView.sourceBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun loadFromCache(name: String): Bitmap? {
        val f = File(cacheDir, name)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    private fun saveToCache(bmp: Bitmap, name: String) {
        File(cacheDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun simple(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, f: Boolean) = onChange(p)
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }
}
