package com.photoeditor.app

import android.content.ContentValues
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var markupView: MarkupView
    private lateinit var progressBar: ProgressBar

    private lateinit var adjustPanel: LinearLayout
    private lateinit var filterPanel: LinearLayout
    private lateinit var cropPanel: LinearLayout
    private lateinit var markupPanel: LinearLayout
    private lateinit var slidersContainer: LinearLayout

    private lateinit var originalBitmap: Bitmap
    private lateinit var previewBitmap: Bitmap

    private val state = AdjustmentState()
    private var renderJob: Job? = null

    private val lightSliders = listOf("Exposure", "Brilliance", "Highlights", "Shadows", "Contrast", "Brightness", "Black Point")
    private val colorSliders = listOf("Saturation", "Vibrance", "Warmth", "Tint")
    private val bwSliders = listOf("Intensity", "Neutrals", "Tone", "Grain")
    private val detailSliders = listOf("Sharpness", "Definition", "Noise Reduction")

    private val markupColors = listOf(
        0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(),
        0xFF34C759.toInt(), 0xFF007AFF.toInt(), 0xFFAF52DE.toInt(),
        0xFFFFFFFF.toInt(), 0xFF000000.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        imageView = findViewById(R.id.imageView)
        markupView = findViewById(R.id.markupView)
        progressBar = findViewById(R.id.progressBar)
        adjustPanel = findViewById(R.id.adjustPanel)
        filterPanel = findViewById(R.id.filterPanel)
        cropPanel = findViewById(R.id.cropPanel)
        markupPanel = findViewById(R.id.markupPanel)
        slidersContainer = findViewById(R.id.slidersContainer)

        val uri = Uri.parse(intent.getStringExtra("imageUri") ?: return)
        originalBitmap = loadBitmap(uri)
        previewBitmap = AdjustmentEngine.scaledPreview(originalBitmap)
        imageView.setImageBitmap(previewBitmap)

        setupToolbar()
        setupCategoryTabs()
        showSliderCategory(lightSliders, "light")
        setupCropPanel()
        setupMarkupPanel()
        setupMainTools()
        setupFilters()
    }

    // ─── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveImage() }
        findViewById<Button>(R.id.btnAutoEnhance).setOnClickListener {
            AdjustmentEngine.autoEnhance(state)
            rebuildSliders()
            scheduleRender()
            Toast.makeText(this, "Auto-enhanced!", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Main Tool Tabs ─────────────────────────────────────────────────────────

    private fun setupMainTools() {
        val btnAdjust = findViewById<Button>(R.id.btnToolAdjust)
        val btnFilter = findViewById<Button>(R.id.btnToolFilter)
        val btnCrop   = findViewById<Button>(R.id.btnToolCrop)
        val btnMarkup = findViewById<Button>(R.id.btnToolMarkup)

        fun selectTool(active: Button) {
            listOf(btnAdjust, btnFilter, btnCrop, btnMarkup).forEach {
                it.backgroundTintList = getColorStateList(
                    if (it == active) R.color.accent else R.color.tab_unselected
                )
            }
            adjustPanel.visibility = if (active == btnAdjust) View.VISIBLE else View.GONE
            filterPanel.visibility = if (active == btnFilter) View.VISIBLE else View.GONE
            cropPanel.visibility   = if (active == btnCrop)   View.VISIBLE else View.GONE
            markupPanel.visibility = if (active == btnMarkup) View.VISIBLE else View.GONE
            markupView.visibility  = if (active == btnMarkup) View.VISIBLE else View.GONE
        }

        btnAdjust.setOnClickListener { selectTool(btnAdjust) }
        btnFilter.setOnClickListener { selectTool(btnFilter) }
        btnCrop.setOnClickListener   { selectTool(btnCrop) }
        btnMarkup.setOnClickListener { selectTool(btnMarkup) }
    }

    // ─── Category Tabs ──────────────────────────────────────────────────────────

    private fun setupCategoryTabs() {
        val btnLight  = findViewById<Button>(R.id.btnCatLight)
        val btnColor  = findViewById<Button>(R.id.btnCatColor)
        val btnBW     = findViewById<Button>(R.id.btnCatBW)
        val btnDetail = findViewById<Button>(R.id.btnCatDetail)

        fun selectCat(active: Button, sliders: List<String>, key: String) {
            listOf(btnLight, btnColor, btnBW, btnDetail).forEach {
                it.backgroundTintList = getColorStateList(
                    if (it == active) R.color.accent else R.color.tab_unselected
                )
            }
            showSliderCategory(sliders, key)
        }

        btnLight.setOnClickListener  { selectCat(btnLight,  lightSliders,  "light") }
        btnColor.setOnClickListener  { selectCat(btnColor,  colorSliders,  "color") }
        btnBW.setOnClickListener     { selectCat(btnBW,     bwSliders,     "bw") }
        btnDetail.setOnClickListener { selectCat(btnDetail, detailSliders, "detail") }
    }

    // ─── Sliders ────────────────────────────────────────────────────────────────

    private fun showSliderCategory(names: List<String>, category: String) {
        slidersContainer.removeAllViews()
        names.forEach { name -> addSlider(name, category) }
    }

    private fun rebuildSliders() {
        val visibleCat = when {
            slidersContainer.tag == "color"  -> Pair(colorSliders,  "color")
            slidersContainer.tag == "bw"     -> Pair(bwSliders,     "bw")
            slidersContainer.tag == "detail" -> Pair(detailSliders, "detail")
            else                             -> Pair(lightSliders,  "light")
        }
        showSliderCategory(visibleCat.first, visibleCat.second)
    }

    private fun addSlider(name: String, category: String) {
        val isClamped = name in listOf("Black Point", "Intensity", "Grain", "Sharpness", "Noise Reduction")
        val max = 200; val initial = if (isClamped) 0 else 100

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 6, 16, 6)
        }
        val label = TextView(this).apply {
            text = name; textSize = 12f; setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val valueLabel = TextView(this).apply {
            text = "0"; textSize = 11f; setTextColor(0xFFAAAAAA.toInt())
            layoutParams = LinearLayout.LayoutParams(60, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginStart = 8 }
        }
        val seekBar = SeekBar(this).apply {
            this.max = max
            progress = initial
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val value = if (isClamped) p.toFloat() else (p - 100).toFloat()
                    valueLabel.text = value.toInt().toString()
                    applyToState(name, value)
                    scheduleRender()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        // Set initial value from state
        val currentValue = getStateValue(name)
        seekBar.progress = if (isClamped) currentValue.toInt() else (currentValue + 100).toInt()
        valueLabel.text = currentValue.toInt().toString()

        row.addView(label); row.addView(seekBar); row.addView(valueLabel)
        slidersContainer.addView(row)
    }

    private fun getStateValue(name: String): Float = when (name) {
        "Exposure"       -> state.exposure
        "Brilliance"     -> state.brilliance
        "Highlights"     -> state.highlights
        "Shadows"        -> state.shadows
        "Contrast"       -> state.contrast
        "Brightness"     -> state.brightness
        "Black Point"    -> state.blackPoint
        "Saturation"     -> state.saturation
        "Vibrance"       -> state.vibrance
        "Warmth"         -> state.warmth
        "Tint"           -> state.tint
        "Intensity"      -> state.bwIntensity
        "Neutrals"       -> state.bwNeutrals
        "Tone"           -> state.bwTone
        "Grain"          -> state.bwGrain
        "Sharpness"      -> state.sharpness
        "Definition"     -> state.definition
        "Noise Reduction"-> state.noiseReduction
        else             -> 0f
    }

    private fun applyToState(name: String, value: Float) {
        when (name) {
            "Exposure"       -> state.exposure = value
            "Brilliance"     -> state.brilliance = value
            "Highlights"     -> state.highlights = value
            "Shadows"        -> state.shadows = value
            "Contrast"       -> state.contrast = value
            "Brightness"     -> state.brightness = value
            "Black Point"    -> state.blackPoint = value
            "Saturation"     -> state.saturation = value
            "Vibrance"       -> state.vibrance = value
            "Warmth"         -> state.warmth = value
            "Tint"           -> state.tint = value
            "Intensity"      -> state.bwIntensity = value
            "Neutrals"       -> state.bwNeutrals = value
            "Tone"           -> state.bwTone = value
            "Grain"          -> state.bwGrain = value
            "Sharpness"      -> state.sharpness = value
            "Definition"     -> state.definition = value
            "Noise Reduction"-> state.noiseReduction = value
        }
    }

    // ─── Filters ────────────────────────────────────────────────────────────────

    private fun setupFilters() {
        val rv = findViewById<RecyclerView>(R.id.rvFilters)
        val thumb = AdjustmentEngine.scaledPreview(originalBitmap, 150)
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = FilterAdapter(FilterPresets.all, thumb) { preset ->
            applyFilterPreset(preset)
        }
    }

    private fun applyFilterPreset(preset: FilterPreset) {
        val s = preset.state
        state.exposure = s.exposure; state.brilliance = s.brilliance
        state.highlights = s.highlights; state.shadows = s.shadows
        state.contrast = s.contrast; state.brightness = s.brightness
        state.blackPoint = s.blackPoint; state.saturation = s.saturation
        state.vibrance = s.vibrance; state.warmth = s.warmth; state.tint = s.tint
        state.bwIntensity = s.bwIntensity; state.bwNeutrals = s.bwNeutrals
        state.bwTone = s.bwTone; state.bwGrain = s.bwGrain
        state.sharpness = s.sharpness; state.definition = s.definition
        state.noiseReduction = s.noiseReduction
        state.filterName = preset.name
        rebuildSliders()
        scheduleRender()
    }

    // ─── Crop / Transform ───────────────────────────────────────────────────────

    private fun setupCropPanel() {
        findViewById<Button>(R.id.btnRotateCCW).setOnClickListener {
            state.rotationDegrees -= 90f; scheduleRender()
        }
        findViewById<Button>(R.id.btnRotateCW).setOnClickListener {
            state.rotationDegrees += 90f; scheduleRender()
        }
        findViewById<Button>(R.id.btnFlipH).setOnClickListener {
            state.flipHorizontal = !state.flipHorizontal; scheduleRender()
        }
        findViewById<Button>(R.id.btnFlipV).setOnClickListener {
            state.flipVertical = !state.flipVertical; scheduleRender()
        }
        val txtStraighten = findViewById<TextView>(R.id.txtStraighten)
        findViewById<SeekBar>(R.id.seekStraighten).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                state.straighten = (p - 45).toFloat()
                txtStraighten.text = "${state.straighten.toInt()}°"
                scheduleRender()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // ─── Markup ─────────────────────────────────────────────────────────────────

    private fun setupMarkupPanel() {
        val btnPen = findViewById<Button>(R.id.btnPen)
        val btnEraser = findViewById<Button>(R.id.btnEraser)
        val colorContainer = findViewById<LinearLayout>(R.id.colorPicker)

        btnPen.setOnClickListener {
            markupView.currentTool = MarkupView.Tool.PEN
            btnPen.backgroundTintList = getColorStateList(R.color.accent)
            btnEraser.backgroundTintList = getColorStateList(R.color.tab_unselected)
        }
        btnEraser.setOnClickListener {
            markupView.currentTool = MarkupView.Tool.ERASER
            btnEraser.backgroundTintList = getColorStateList(R.color.accent)
            btnPen.backgroundTintList = getColorStateList(R.color.tab_unselected)
        }
        findViewById<Button>(R.id.btnUndo).setOnClickListener { markupView.undo() }
        findViewById<Button>(R.id.btnClearMarkup).setOnClickListener { markupView.clear() }
        findViewById<SeekBar>(R.id.seekBrushSize).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                markupView.strokeWidth = (p + 2).toFloat()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        markupColors.forEach { color ->
            val dot = View(this).apply {
                val size = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 2
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = 8; it.setMargins(4, 0, 4, 0)
                }
                setBackgroundColor(color)
                setOnClickListener {
                    markupView.strokeColor = color
                    markupView.currentTool = MarkupView.Tool.PEN
                    btnPen.backgroundTintList = getColorStateList(R.color.accent)
                    btnEraser.backgroundTintList = getColorStateList(R.color.tab_unselected)
                }
            }
            colorContainer.addView(dot)
        }
        markupView.strokeColor = markupColors[0]
    }

    // ─── Rendering ──────────────────────────────────────────────────────────────

    private fun scheduleRender() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val result = withContext(Dispatchers.Default) {
                AdjustmentEngine.applyAdjustments(previewBitmap, state)
            }
            imageView.setImageBitmap(result)
            progressBar.visibility = View.GONE
        }
    }

    // ─── Save ───────────────────────────────────────────────────────────────────

    private fun saveImage() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val saved = withContext(Dispatchers.Default) {
                var result = AdjustmentEngine.applyAdjustments(originalBitmap, state)
                if (markupView.visibility == View.VISIBLE) {
                    result = markupView.flattenOnto(result)
                }
                result
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "PhotoEditorPro_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { contentResolver.openOutputStream(it)?.use { stream -> saved.compress(Bitmap.CompressFormat.JPEG, 97, stream) } }
            progressBar.visibility = View.GONE
            Toast.makeText(this@EditActivity, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        return contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
    }
}
