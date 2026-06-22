package com.photoeditor.app

import android.graphics.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

object ImageProcessor {

    data class Adjustments(
        // Light
        var exposure: Float = 0f,
        var brilliance: Float = 0f,
        var highlights: Float = 0f,
        var shadows: Float = 0f,
        var contrast: Float = 0f,
        var brightness: Float = 0f,
        var blackPoint: Float = 0f,
        // Color
        var saturation: Float = 0f,
        var vibrance: Float = 0f,
        var warmth: Float = 0f,
        var tint: Float = 0f,
        // B&W
        var bwIntensity: Float = 0f,
        var bwNeutrals: Float = 0f,
        var bwTone: Float = 0f,
        var bwGrain: Float = 0f,
        // Detail
        var sharpness: Float = 0f,
        var definition: Float = 0f,
        var noiseReduction: Float = 0f,
        // Filter
        var filterKey: String = "none",
        var filterIntensity: Float = 1f
    )

    fun applyAll(src: Bitmap, adj: Adjustments): Bitmap {
        var bmp = src.copy(Bitmap.Config.ARGB_8888, true)

        if (adj.filterKey != "none") {
            bmp = applyFilterPreset(bmp, adj.filterKey, adj.filterIntensity)
        }

        val matrix = buildColorMatrix(adj)
        bmp = applyMatrix(bmp, matrix)

        if (adj.vibrance != 0f) {
            bmp = applyVibrance(bmp, adj.vibrance)
        }

        if (adj.bwIntensity > 0f) {
            bmp = applyBW(bmp, adj)
        }

        if (adj.noiseReduction > 0f) {
            bmp = boxBlur(bmp, (adj.noiseReduction * 2).toInt().coerceAtLeast(1))
        }

        if (adj.sharpness > 0f) {
            bmp = convolve(bmp, sharpenKernel(adj.sharpness))
        }

        if (adj.definition > 0f) {
            bmp = convolve(bmp, definitionKernel(adj.definition))
        }

        if (adj.bwGrain > 0f) {
            bmp = applyGrain(bmp, adj.bwGrain)
        }

        return bmp
    }

    private fun buildColorMatrix(adj: Adjustments): ColorMatrix {
        val out = ColorMatrix()

        // Exposure: multiplicative, 2^stops
        val expFactor = 2f.pow(adj.exposure * 2f)
        out.postConcat(scaleMatrix(expFactor, expFactor, expFactor))

        // Brilliance: contrast boost with slight shadow lift
        if (adj.brilliance != 0f) {
            val c = 1f + adj.brilliance * 0.35f
            val offset = -adj.brilliance * 12f
            out.postConcat(contrastMatrix(c, offset))
        }

        // Highlights: compress bright end
        if (adj.highlights != 0f) {
            val h = adj.highlights
            val scale = 1f - h * 0.35f
            val lift = if (h < 0f) -h * 80f else 0f
            out.postConcat(ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, lift,
                0f, scale, 0f, 0f, lift,
                0f, 0f, scale, 0f, lift,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // Shadows: lift or crush dark areas
        if (adj.shadows != 0f) {
            val offset = adj.shadows * 45f
            out.postConcat(offsetMatrix(offset))
        }

        // Contrast
        if (adj.contrast != 0f) {
            val c = 1f + adj.contrast * 0.8f
            out.postConcat(contrastMatrix(c, 127f * (1f - c)))
        }

        // Brightness
        if (adj.brightness != 0f) {
            out.postConcat(offsetMatrix(adj.brightness * 100f))
        }

        // Black Point: raise the floor
        if (adj.blackPoint != 0f) {
            out.postConcat(offsetMatrix(adj.blackPoint * 50f))
        }

        // Warmth: red up / blue down  (or reverse for cool)
        if (adj.warmth != 0f) {
            val rScale = 1f + adj.warmth * 0.25f
            val bScale = 1f - adj.warmth * 0.25f
            out.postConcat(ColorMatrix(floatArrayOf(
                rScale, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, bScale, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // Tint: green up / magenta (red+blue) balance
        if (adj.tint != 0f) {
            val gOffset = adj.tint * 25f
            val rbOffset = -adj.tint * 12f
            out.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, rbOffset,
                0f, 1f, 0f, 0f, gOffset,
                0f, 0f, 1f, 0f, rbOffset,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // Saturation
        if (adj.saturation != 0f) {
            val sat = ColorMatrix()
            sat.setSaturation((1f + adj.saturation).coerceIn(0f, 3f))
            out.postConcat(sat)
        }

        return out
    }

    private fun applyVibrance(src: Bitmap, strength: Float): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            Color.RGBToHSV(r, g, b, hsv)
            val existingSat = hsv[1]
            // Boost low-saturation colors more
            val boost = strength * (1f - existingSat) * existingSat.coerceAtLeast(0.1f)
            hsv[1] = (existingSat + boost).coerceIn(0f, 1f)
            pixels[i] = Color.HSVToColor(Color.alpha(pixels[i]), hsv)
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun applyBW(src: Bitmap, adj: Adjustments): Bitmap {
        val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
        // B&W Neutrals: mid-tone contrast
        if (adj.bwNeutrals != 0f) {
            val c = 1f + adj.bwNeutrals * 0.4f
            grayMatrix.postConcat(contrastMatrix(c, 127f * (1f - c)))
        }
        // B&W Tone: overall brightness shift
        if (adj.bwTone != 0f) {
            grayMatrix.postConcat(offsetMatrix(adj.bwTone * 30f))
        }

        val bwBmp = applyMatrix(src, grayMatrix)
        if (adj.bwIntensity >= 1f) return bwBmp

        // Blend color original with B&W by intensity
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)
        val paint = Paint().apply { alpha = (adj.bwIntensity * 255).toInt() }
        canvas.drawBitmap(bwBmp, 0f, 0f, paint)
        return result
    }

    private fun applyGrain(src: Bitmap, strength: Float): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        val noiseAmt = (strength * 40).toInt()
        val rng = Random(12345L)
        for (i in pixels.indices) {
            val n = rng.nextInt(-noiseAmt, noiseAmt + 1)
            val r = (Color.red(pixels[i]) + n).coerceIn(0, 255)
            val g = (Color.green(pixels[i]) + n).coerceIn(0, 255)
            val b = (Color.blue(pixels[i]) + n).coerceIn(0, 255)
            pixels[i] = Color.argb(Color.alpha(pixels[i]), r, g, b)
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun sharpenKernel(strength: Float): FloatArray {
        val s = strength * 2.5f
        return floatArrayOf(
            0f, -s, 0f,
            -s, 1f + 4f * s, -s,
            0f, -s, 0f
        )
    }

    private fun definitionKernel(strength: Float): FloatArray {
        val s = strength * 1.8f
        val edge = -s / 8f
        return floatArrayOf(
            edge, edge, edge,
            edge, 1f + s, edge,
            edge, edge, edge
        )
    }

    private fun convolve(src: Bitmap, kernel: FloatArray): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        val out = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0f; var g = 0f; var b = 0f
                for (ky in 0..2) for (kx in 0..2) {
                    val px = (x + kx - 1).coerceIn(0, w - 1)
                    val py = (y + ky - 1).coerceIn(0, h - 1)
                    val p = pixels[py * w + px]
                    val k = kernel[ky * 3 + kx]
                    r += Color.red(p) * k
                    g += Color.green(p) * k
                    b += Color.blue(p) * k
                }
                out[y * w + x] = Color.argb(
                    Color.alpha(pixels[y * w + x]),
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        val out = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val size = (2 * radius + 1) * (2 * radius + 1)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0
                for (dy in -radius..radius) for (dx in -radius..radius) {
                    val p = pixels[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
                    r += Color.red(p); g += Color.green(p); b += Color.blue(p)
                }
                out[y * w + x] = Color.argb(Color.alpha(pixels[y * w + x]), r / size, g / size, b / size)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    fun applyFilterPreset(src: Bitmap, key: String, intensity: Float): Bitmap {
        val matrix = filterMatrix(key) ?: return src
        val filtered = applyMatrix(src, matrix)
        if (intensity >= 1f) return filtered
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)
        canvas.drawBitmap(filtered, 0f, 0f, Paint().apply { alpha = (intensity * 255).toInt() })
        return result
    }

    fun filterMatrix(key: String): ColorMatrix? = when (key) {
        "vivid" -> ColorMatrix().also {
            it.postConcat(contrastMatrix(1.2f, 127f * -0.2f))
            it.postConcat(ColorMatrix().apply { setSaturation(1.6f) })
        }
        "vivid_cool" -> ColorMatrix().also {
            it.postConcat(contrastMatrix(1.2f, 127f * -0.2f))
            it.postConcat(ColorMatrix().apply { setSaturation(1.5f) })
            it.postConcat(ColorMatrix(floatArrayOf(0.85f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1.2f,0f,0f, 0f,0f,0f,1f,0f)))
        }
        "vivid_warm" -> ColorMatrix().also {
            it.postConcat(contrastMatrix(1.2f, 127f * -0.2f))
            it.postConcat(ColorMatrix().apply { setSaturation(1.5f) })
            it.postConcat(ColorMatrix(floatArrayOf(1.2f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,0.85f,0f,0f, 0f,0f,0f,1f,0f)))
        }
        "dramatic" -> ColorMatrix().also {
            it.postConcat(contrastMatrix(1.5f, 127f * -0.5f))
            it.postConcat(ColorMatrix().apply { setSaturation(1.3f) })
        }
        "dramatic_cool" -> ColorMatrix().also {
            it.postConcat(contrastMatrix(1.5f, 127f * -0.5f))
            it.postConcat(ColorMatrix(floatArrayOf(0.85f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1.2f,0f,0f, 0f,0f,0f,1f,0f)))
        }
        "dramatic_warm" -> ColorMatrix().also {
            it.postConcat(contrastMatrix(1.5f, 127f * -0.5f))
            it.postConcat(ColorMatrix(floatArrayOf(1.2f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,0.85f,0f,0f, 0f,0f,0f,1f,0f)))
        }
        "mono" -> ColorMatrix().apply { setSaturation(0f) }
        "silvertone" -> ColorMatrix().also {
            it.setSaturation(0f)
            it.postConcat(offsetMatrix(15f))
            it.postConcat(contrastMatrix(1.1f, 127f * -0.1f))
        }
        "noir" -> ColorMatrix().also {
            it.setSaturation(0f)
            it.postConcat(contrastMatrix(1.8f, 127f * -0.8f))
        }
        "fade" -> ColorMatrix(floatArrayOf(
            0.75f,0f,0f,0f,35f, 0f,0.75f,0f,0f,35f, 0f,0f,0.75f,0f,35f, 0f,0f,0f,1f,0f))
        "chrome" -> ColorMatrix(floatArrayOf(
            1.3f,0f,0f,0f,-10f, 0f,1.0f,0f,0f,0f, 0f,0f,0.7f,0f,0f, 0f,0f,0f,1f,0f))
        "warm" -> ColorMatrix(floatArrayOf(
            1.2f,0f,0f,0f,0f, 0f,1.0f,0f,0f,0f, 0f,0f,0.8f,0f,0f, 0f,0f,0f,1f,0f))
        "cool" -> ColorMatrix(floatArrayOf(
            0.85f,0f,0f,0f,0f, 0f,1.0f,0f,0f,0f, 0f,0f,1.2f,0f,0f, 0f,0f,0f,1f,0f))
        "sepia" -> ColorMatrix(floatArrayOf(
            0.393f,0.769f,0.189f,0f,0f,
            0.349f,0.686f,0.168f,0f,0f,
            0.272f,0.534f,0.131f,0f,0f,
            0f,0f,0f,1f,0f))
        else -> null
    }

    fun autoEnhance(src: Bitmap): Adjustments {
        val small = Bitmap.createScaledBitmap(src, 80, 80, true)
        val pixels = IntArray(80 * 80)
        small.getPixels(pixels, 0, 80, 0, 0, 80, 80)
        var avgLum = 0f; var avgSat = 0f
        val hsv = FloatArray(3)
        for (p in pixels) {
            Color.RGBToHSV(Color.red(p), Color.green(p), Color.blue(p), hsv)
            avgLum += hsv[2]; avgSat += hsv[1]
        }
        avgLum /= pixels.size; avgSat /= pixels.size
        val adj = Adjustments()
        adj.brightness = (0.5f - avgLum).coerceIn(-0.4f, 0.4f)
        adj.contrast = 0.1f
        adj.saturation = if (avgSat < 0.25f) 0.25f else 0f
        return adj
    }

    private fun applyMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return result
    }

    private fun scaleMatrix(r: Float, g: Float, b: Float) = ColorMatrix(floatArrayOf(
        r,0f,0f,0f,0f, 0f,g,0f,0f,0f, 0f,0f,b,0f,0f, 0f,0f,0f,1f,0f))

    private fun offsetMatrix(v: Float) = ColorMatrix(floatArrayOf(
        1f,0f,0f,0f,v, 0f,1f,0f,0f,v, 0f,0f,1f,0f,v, 0f,0f,0f,1f,0f))

    private fun contrastMatrix(c: Float, offset: Float) = ColorMatrix(floatArrayOf(
        c,0f,0f,0f,offset, 0f,c,0f,0f,offset, 0f,0f,c,0f,offset, 0f,0f,0f,1f,0f))

    fun scaleBitmapForPreview(src: Bitmap, maxPx: Int = 800): Bitmap {
        val ratio = min(maxPx.toFloat() / max(src.width, src.height), 1f)
        if (ratio >= 1f) return src
        return Bitmap.createScaledBitmap(src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
    }
}
