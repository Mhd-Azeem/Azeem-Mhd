package com.photoeditor.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File

class EditActivity : AppCompatActivity() {

    // ── State ──────────────────────────────────────────────────────────────────
    private lateinit var originalBitmap: Bitmap
    private lateinit var previewBitmap: Bitmap
    private val adj = ImageProcessor.Adjustments()

    private enum class ActiveTool { NONE, ADJUSTMENTS, FILTERS }
    private enum class AdjCategory { LIGHT, COLOR, BW, DETAIL }

    private var activeTool = ActiveTool.NONE
    private var adjCategory = AdjCategory.LIGHT

    private var processingJob: Job? = null
    private var filterIntensity = 1f

    // ── Views ──────────────────────────────────────────────────────────────────
    private lateinit var imageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var panelAdjustments: LinearLayout
    private lateinit var panelFilters: LinearLayout
    private lateinit var slidersContainer: LinearLayout
    private lateinit var filtersRow: LinearLayout
    private lateinit var tabIndicator: View

    private val tabViews = mutableMapOf<AdjCategory, TextView>()

    // ── Slider definitions ─────────────────────────────────────────────────────
    private data class SliderDef(
        val label: String,
        val symmetric: Boolean,   // true → -1..1 (seekBar 0..200 center 100)
        val get: () -> Float,
        val set: (Float) -> Unit
    )

    private val lightSliders by lazy { listOf(
        SliderDef("Exposure",    true,  { adj.exposure })    { adj.exposure    = it },
        SliderDef("Brilliance",  true,  { adj.brilliance })  { adj.brilliance  = it },
        SliderDef("Highlights",  true,  { adj.highlights })  { adj.highlights  = it },
        SliderDef("Shadows",     true,  { adj.shadows })     { adj.shadows     = it },
        SliderDef("Contrast",    true,  { adj.contrast })    { adj.contrast    = it },
        SliderDef("Brightness",  true,  { adj.brightness })  { adj.brightness  = it },
        SliderDef("Black Point", false, { adj.blackPoint })  { adj.blackPoint  = it }
    )}

    private val colorSliders by lazy { listOf(
        SliderDef("Saturation", true, { adj.saturation }) { adj.saturation = it },
        SliderDef("Vibrance",   true, { adj.vibrance })   { adj.vibrance   = it },
        SliderDef("Warmth",     true, { adj.warmth })     { adj.warmth     = it },
        SliderDef("Tint",       true, { adj.tint })       { adj.tint       = it }
    )}

    private val bwSliders by lazy { listOf(
        SliderDef("Intensity", false, { adj.bwIntensity }) { adj.bwIntensity = it },
        SliderDef("Neutrals",  true,  { adj.bwNeutrals })  { adj.bwNeutrals  = it },
        SliderDef("Tone",      true,  { adj.bwTone })      { adj.bwTone      = it },
        SliderDef("Grain",     false, { adj.bwGrain })     { adj.bwGrain     = it }
    )}

    private val detailSliders by lazy { listOf(
        SliderDef("Sharpness",       false, { adj.sharpness })       { adj.sharpness       = it },
        SliderDef("Definition",      false, { adj.definition })      { adj.definition      = it },
        SliderDef("Noise Reduction", false, { adj.noiseReduction })  { adj.noiseReduction  = it }
    )}

    // ── Filter list ────────────────────────────────────────────────────────────
    private data class FilterDef(val label: String, val key: String)
    private val filters = listOf(
        FilterDef("Original",      "none"),
        FilterDef("Vivid",         "vivid"),
        FilterDef("Vivid Cool",    "vivid_cool"),
        FilterDef("Vivid Warm",    "vivid_warm"),
        FilterDef("Dramatic",      "dramatic"),
        FilterDef("Dramatic Cool", "dramatic_cool"),
        FilterDef("Dramatic Warm", "dramatic_warm"),
        FilterDef("Mono",          "mono"),
        FilterDef("Silvertone",    "silvertone"),
        FilterDef("Noir",          "noir"),
        FilterDef("Fade",          "fade"),
        FilterDef("Chrome",        "chrome"),
        FilterDef("Warm",          "warm"),
        FilterDef("Cool",          "cool"),
        FilterDef("Sepia",         "sepia")
    )

    // ── Activity result codes ──────────────────────────────────────────────────
    private val REQ_CROP   = 101
    private val REQ_MARKUP = 102

    // ──────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        imageView        = findViewById(R.id.imageView)
        progressBar      = findViewById(R.id.progressBar)
        panelAdjustments = findViewById(R.id.panelAdjustments)
        panelFilters     = findViewById(R.id.panelFilters)
        slidersContainer = findViewById(R.id.slidersContainer)
        filtersRow       = findViewById(R.id.filtersRow)
        tabIndicator     = findViewById(R.id.tabIndicator)

        tabViews[AdjCategory.LIGHT]  = findViewById(R.id.tabLight)
        tabViews[AdjCategory.COLOR]  = findViewById(R.id.tabColor)
        tabViews[AdjCategory.BW]     = findViewById(R.id.tabBW)
        tabViews[AdjCategory.DETAIL] = findViewById(R.id.tabDetail)

        loadImage()

        // Top bar
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnReset).setOnClickListener  { resetAll() }
        findViewById<Button>(R.id.btnSave).setOnClickListener   { saveImage() }

        // Bottom tools
        findViewById<LinearLayout>(R.id.toolAdjust).setOnClickListener   { toggleAdjustments() }
        findViewById<LinearLayout>(R.id.toolFilters).setOnClickListener  { toggleFilters() }
        findViewById<LinearLayout>(R.id.toolCrop).setOnClickListener     { launchCrop() }
        findViewById<LinearLayout>(R.id.toolAuto).setOnClickListener     { applyAuto() }
        findViewById<LinearLayout>(R.id.toolPortrait).setOnClickListener { portraitPlaceholder() }
        findViewById<LinearLayout>(R.id.toolMarkup).setOnClickListener   { launchMarkup() }

        // Category tabs
        tabViews[AdjCategory.LIGHT]!!.setOnClickListener  { selectCategory(AdjCategory.LIGHT) }
        tabViews[AdjCategory.COLOR]!!.setOnClickListener  { selectCategory(AdjCategory.COLOR) }
        tabViews[AdjCategory.BW]!!.setOnClickListener     { selectCategory(AdjCategory.BW) }
        tabViews[AdjCategory.DETAIL]!!.setOnClickListener { selectCategory(AdjCategory.DETAIL) }

        // Filter intensity slider
        findViewById<SeekBar>(R.id.seekFilterIntensity).setOnSeekBarChangeListener(seekListener { p ->
            filterIntensity = p / 100f
            adj.filterIntensity = filterIntensity
            schedulePreviewUpdate()
        })
    }

    // ── Image loading ──────────────────────────────────────────────────────────
    private fun loadImage() {
        val uriStr = intent.getStringExtra("imageUri") ?: return
        val uri = Uri.parse(uriStr)
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } ?: return@launch
            originalBitmap = bmp
            previewBitmap  = ImageProcessor.scaleBitmapForPreview(bmp, 900)
            imageView.setImageBitmap(previewBitmap)
        }
    }

    // ── Preview rendering ──────────────────────────────────────────────────────
    private fun schedulePreviewUpdate(debounceMs: Long = 80L) {
        processingJob?.cancel()
        processingJob = lifecycleScope.launch {
            delay(debounceMs)
            progressBar.visibility = View.VISIBLE
            val result = withContext(Dispatchers.IO) {
                ImageProcessor.applyAll(previewBitmap, adj)
            }
            imageView.setImageBitmap(result)
            progressBar.visibility = View.GONE
        }
    }

    // ── Tool panel toggling ────────────────────────────────────────────────────
    private fun toggleAdjustments() {
        if (activeTool == ActiveTool.ADJUSTMENTS) {
            closePanels(); activeTool = ActiveTool.NONE
        } else {
            closePanels()
            activeTool = ActiveTool.ADJUSTMENTS
            panelAdjustments.visibility = View.VISIBLE
            selectCategory(adjCategory)
            highlightTool(R.id.iconAdjust, R.id.labelAdjust, true)
        }
    }

    private fun toggleFilters() {
        if (activeTool == ActiveTool.FILTERS) {
            closePanels(); activeTool = ActiveTool.NONE
        } else {
            closePanels()
            activeTool = ActiveTool.FILTERS
            panelFilters.visibility = View.VISIBLE
            populateFilterThumbnails()
            highlightTool(R.id.iconFilters, R.id.labelFilters, true)
        }
    }

    private fun closePanels() {
        panelAdjustments.visibility = View.GONE
        panelFilters.visibility = View.GONE
        highlightTool(R.id.iconAdjust,  R.id.labelAdjust,  false)
        highlightTool(R.id.iconFilters, R.id.labelFilters, false)
    }

    private fun highlightTool(iconId: Int, labelId: Int, active: Boolean) {
        val color = if (active) getColor(R.color.accent) else getColor(R.color.text_secondary)
        findViewById<ImageView>(iconId).setColorFilter(color)
        findViewById<TextView>(labelId).setTextColor(color)
    }

    // ── Category tabs ──────────────────────────────────────────────────────────
    private fun selectCategory(cat: AdjCategory) {
        adjCategory = cat
        tabViews.forEach { (k, tv) ->
            tv.setTextColor(if (k == cat) getColor(R.color.tab_active) else getColor(R.color.tab_inactive))
            tv.setTypeface(null, if (k == cat) Typeface.BOLD else Typeface.NORMAL)
        }
        moveTabIndicator(cat)
        populateSliders(cat)
    }

    private fun moveTabIndicator(cat: AdjCategory) {
        val order = listOf(AdjCategory.LIGHT, AdjCategory.COLOR, AdjCategory.BW, AdjCategory.DETAIL)
        val idx = order.indexOf(cat)
        tabIndicator.post {
            val tabW = tabViews[AdjCategory.LIGHT]!!.width
            tabIndicator.layoutParams = (tabIndicator.layoutParams as LinearLayout.LayoutParams).also {
                it.width = tabW
                it.marginStart = idx * tabW
            }
        }
    }

    // ── Slider population ──────────────────────────────────────────────────────
    private fun populateSliders(cat: AdjCategory) {
        slidersContainer.removeAllViews()
        val sliders = when (cat) {
            AdjCategory.LIGHT  -> lightSliders
            AdjCategory.COLOR  -> colorSliders
            AdjCategory.BW     -> bwSliders
            AdjCategory.DETAIL -> detailSliders
        }
        sliders.forEach { def -> slidersContainer.addView(buildSliderRow(def)) }
    }

    private fun buildSliderRow(def: SliderDef): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (6 * dp).toInt(), (16 * dp).toInt(), (6 * dp).toInt())
        }

        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val label = TextView(this).apply {
            text = def.label
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val max   = if (def.symmetric) 200 else 100
        val start = if (def.symmetric) 100 else 0
        val curVal = def.get()
        val initProg = if (def.symmetric) (100 + curVal * 100).toInt() else (curVal * 100).toInt()

        val valueLabel = TextView(this).apply {
            text = valText(curVal, def.symmetric)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = (8 * dp).toInt() }
        }

        headerRow.addView(label)
        headerRow.addView(valueLabel)

        val seek = SeekBar(this).apply {
            this.max = max
            progress = initProg.coerceIn(0, max)
            progressDrawable?.setTint(getColor(R.color.accent))
            thumb?.setTint(getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(seekListener { p ->
                val v = if (def.symmetric) (p - 100) / 100f else p / 100f
                def.set(v)
                valueLabel.text = valText(v, def.symmetric)
                schedulePreviewUpdate()
            })
        }

        row.addView(headerRow)
        row.addView(seek)
        return row
    }

    private fun valText(v: Float, symmetric: Boolean): String {
        val i = if (symmetric) (v * 100).toInt() else (v * 100).toInt()
        return if (i > 0 && symmetric) "+$i" else "$i"
    }

    // ── Filter panel ───────────────────────────────────────────────────────────
    private var filterThumbsBuilt = false

    private fun populateFilterThumbnails() {
        if (filterThumbsBuilt) return
        filterThumbsBuilt = true

        filtersRow.removeAllViews()
        val dp = resources.displayMetrics.density
        val thumbSize = (72 * dp).toInt()

        lifecycleScope.launch {
            val tiny = withContext(Dispatchers.IO) {
                ImageProcessor.scaleBitmapForPreview(previewBitmap, 100)
            }

            filters.forEach { f ->
                val thumb = withContext(Dispatchers.IO) {
                    if (f.key == "none") tiny.copy(Bitmap.Config.ARGB_8888, false)
                    else ImageProcessor.applyFilterPreset(tiny, f.key, 1f)
                }

                val cell = LinearLayout(this@EditActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding((6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    setOnClickListener {
                        adj.filterKey = f.key
                        adj.filterIntensity = filterIntensity
                        // Update border for selected
                        for (i in 0 until filtersRow.childCount) {
                            (filtersRow.getChildAt(i) as? LinearLayout)?.background = null
                        }
                        background = android.graphics.drawable.GradientDrawable().also { d ->
                            d.setStroke((2 * dp).toInt(), getColor(R.color.accent))
                            d.cornerRadius = 6 * dp
                        }
                        schedulePreviewUpdate()
                    }
                }

                val iv = ImageView(this@EditActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(thumbSize, thumbSize)
                    setImageBitmap(thumb)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                val tv = TextView(this@EditActivity).apply {
                    text = f.label
                    textSize = 10f
                    setTextColor(getColor(R.color.text_secondary))
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        thumbSize,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = (4 * dp).toInt() }
                }

                cell.addView(iv)
                cell.addView(tv)
                filtersRow.addView(cell)

                if (f.key == adj.filterKey) {
                    cell.background = android.graphics.drawable.GradientDrawable().also { d ->
                        d.setStroke((2 * dp).toInt(), getColor(R.color.accent))
                        d.cornerRadius = 6 * dp
                    }
                }
            }
        }
    }

    // ── Auto-enhance ───────────────────────────────────────────────────────────
    private fun applyAuto() {
        val suggestion = ImageProcessor.autoEnhance(previewBitmap)
        adj.brightness  = suggestion.brightness
        adj.contrast    = suggestion.contrast
        adj.saturation  = suggestion.saturation
        schedulePreviewUpdate(0)
        Toast.makeText(this, "Auto-enhance applied", Toast.LENGTH_SHORT).show()
        // Refresh sliders if panel is open
        if (activeTool == ActiveTool.ADJUSTMENTS) populateSliders(adjCategory)
    }

    // ── Portrait placeholder ───────────────────────────────────────────────────
    private fun portraitPlaceholder() {
        Toast.makeText(this, "Portrait controls require a Portrait-mode photo", Toast.LENGTH_LONG).show()
    }

    // ── Crop / Markup ──────────────────────────────────────────────────────────
    private fun launchCrop() {
        saveTempFile(previewBitmap, "temp_edit.png")
        closePanels(); activeTool = ActiveTool.NONE
        startActivityForResult(Intent(this, CropRotateActivity::class.java), REQ_CROP)
    }

    private fun launchMarkup() {
        saveTempFile(previewBitmap, "temp_edit.png")
        closePanels(); activeTool = ActiveTool.NONE
        startActivityForResult(Intent(this, MarkupActivity::class.java), REQ_MARKUP)
    }

    @Deprecated("Using for result compat")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode in listOf(REQ_CROP, REQ_MARKUP)) {
            val f = File(cacheDir, "temp_result.png")
            if (f.exists()) {
                val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return
                previewBitmap = bmp
                imageView.setImageBitmap(bmp)
            }
        }
    }

    // ── Reset ──────────────────────────────────────────────────────────────────
    private fun resetAll() {
        adj.apply {
            exposure = 0f; brilliance = 0f; highlights = 0f; shadows = 0f
            contrast = 0f; brightness = 0f; blackPoint = 0f
            saturation = 0f; vibrance = 0f; warmth = 0f; tint = 0f
            bwIntensity = 0f; bwNeutrals = 0f; bwTone = 0f; bwGrain = 0f
            sharpness = 0f; definition = 0f; noiseReduction = 0f
            filterKey = "none"; filterIntensity = 1f
        }
        previewBitmap = ImageProcessor.scaleBitmapForPreview(originalBitmap, 900)
        imageView.setImageBitmap(previewBitmap)
        if (activeTool == ActiveTool.ADJUSTMENTS) populateSliders(adjCategory)
    }

    // ── Save ───────────────────────────────────────────────────────────────────
    private fun saveImage() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val result = withContext(Dispatchers.IO) {
                ImageProcessor.applyAll(originalBitmap, adj)
            }
            val name = "PhotoEditor_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { u ->
                contentResolver.openOutputStream(u)?.use {
                    result.compress(Bitmap.CompressFormat.JPEG, 95, it)
                }
            }
            progressBar.visibility = View.GONE
            Toast.makeText(this@EditActivity, "Photo saved to gallery", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private fun saveTempFile(bmp: Bitmap, name: String) {
        File(cacheDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }
}
