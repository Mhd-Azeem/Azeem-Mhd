package com.photoeditor.app

import android.graphics.*
import kotlin.random.Random

object AdjustmentEngine {

    fun applyAdjustments(original: Bitmap, state: AdjustmentState): Bitmap {
        var bitmap = applyTransform(original, state)
        bitmap = applyPixelLevel(bitmap, state)
        bitmap = applyColorMatrix(bitmap, state)
        return bitmap
    }

    private fun applyTransform(src: Bitmap, state: AdjustmentState): Bitmap {
        val matrix = Matrix()
        val cx = src.width / 2f
        val cy = src.height / 2f
        val totalRotation = state.rotationDegrees + state.straighten
        if (totalRotation != 0f) matrix.postRotate(totalRotation, cx, cy)
        if (state.flipHorizontal) matrix.postScale(-1f, 1f, cx, cy)
        if (state.flipVertical) matrix.postScale(1f, -1f, cx, cy)
        return if (totalRotation != 0f || state.flipHorizontal || state.flipVertical) {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } else src
    }

    private fun applyPixelLevel(src: Bitmap, state: AdjustmentState): Bitmap {
        val hasPixelOps = state.highlights != 0f || state.shadows != 0f ||
                state.blackPoint != 0f || state.bwGrain > 0f || state.noiseReduction > 0f
        if (!hasPixelOps) return src

        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val rand = Random(12345)

        for (i in pixels.indices) {
            var r = Color.red(pixels[i]).toFloat()
            var g = Color.green(pixels[i]).toFloat()
            var b = Color.blue(pixels[i]).toFloat()
            val a = Color.alpha(pixels[i])
            val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

            if (state.highlights != 0f && lum > 0.5f) {
                val factor = (lum - 0.5f) * 2f * (state.highlights / 100f) * -60f
                r = (r + factor).coerceIn(0f, 255f)
                g = (g + factor).coerceIn(0f, 255f)
                b = (b + factor).coerceIn(0f, 255f)
            }
            if (state.shadows != 0f && lum < 0.5f) {
                val factor = (0.5f - lum) * 2f * (state.shadows / 100f) * 60f
                r = (r + factor).coerceIn(0f, 255f)
                g = (g + factor).coerceIn(0f, 255f)
                b = (b + factor).coerceIn(0f, 255f)
            }
            if (state.blackPoint != 0f) {
                val bp = state.blackPoint / 100f * 35f
                r = (r - bp).coerceIn(0f, 255f)
                g = (g - bp).coerceIn(0f, 255f)
                b = (b - bp).coerceIn(0f, 255f)
            }
            if (state.bwGrain > 0f) {
                val amount = (state.bwGrain / 100f * 35f).toInt()
                val noise = rand.nextInt(-amount, amount + 1).toFloat()
                r = (r + noise).coerceIn(0f, 255f)
                g = (g + noise).coerceIn(0f, 255f)
                b = (b + noise).coerceIn(0f, 255f)
            }
            pixels[i] = Color.argb(a, r.toInt(), g.toInt(), b.toInt())
        }

        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, width, 0, 0, width, height)

        return if (state.noiseReduction > 0f) {
            val radius = (state.noiseReduction / 100f * 2f).toInt().coerceAtLeast(1)
            boxBlur(result, radius)
        } else result
    }

    private fun applyColorMatrix(src: Bitmap, state: AdjustmentState): Bitmap {
        val matrix = ColorMatrix()

        // Exposure
        if (state.exposure != 0f) {
            val f = (1f + state.exposure / 100f * 1.5f).coerceIn(0f, 4f)
            matrix.postConcat(scaleMatrix(f, f, f))
        }
        // Brightness
        if (state.brightness != 0f) {
            val b = state.brightness * 2.55f
            matrix.postConcat(offsetMatrix(b))
        }
        // Contrast
        if (state.contrast != 0f) {
            val c = 1f + state.contrast / 100f
            matrix.postConcat(contrastMatrix(c))
        }
        // Brilliance (saturation + slight exposure)
        if (state.brilliance != 0f) {
            val sat = ColorMatrix()
            sat.setSaturation(1f + state.brilliance / 200f)
            matrix.postConcat(sat)
            matrix.postConcat(scaleMatrix(1f + state.brilliance / 500f, 1f + state.brilliance / 500f, 1f + state.brilliance / 500f))
        }
        // Saturation
        val effectiveSat = if (state.bwIntensity > 0f)
            (1f - state.bwIntensity / 100f) * (1f + state.saturation / 100f)
        else 1f + state.saturation / 100f
        if (effectiveSat != 1f) {
            val sat = ColorMatrix(); sat.setSaturation(effectiveSat.coerceIn(0f, 3f))
            matrix.postConcat(sat)
        }
        // Vibrance (gentler saturation boost)
        if (state.vibrance != 0f) {
            val sat = ColorMatrix(); sat.setSaturation(1f + state.vibrance / 250f)
            matrix.postConcat(sat)
        }
        // Warmth
        if (state.warmth != 0f) {
            val w = state.warmth / 100f
            matrix.postConcat(ColorMatrix(floatArrayOf(
                1f + w * 0.3f, 0f, 0f, 0f, 0f,
                0f, 1f + w * 0.05f, 0f, 0f, 0f,
                0f, 0f, 1f - w * 0.35f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        // Tint (green/magenta)
        if (state.tint != 0f) {
            val t = state.tint / 100f
            matrix.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f + t * 0.25f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        // B&W Tone (warm/cool tone on grayscale)
        if (state.bwTone != 0f && state.bwIntensity > 0f) {
            val t = state.bwTone / 100f
            matrix.postConcat(ColorMatrix(floatArrayOf(
                1f + t * 0.15f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f - t * 0.15f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        // B&W Neutrals (midtone brightness)
        if (state.bwNeutrals != 0f && state.bwIntensity > 0f) {
            matrix.postConcat(offsetMatrix(state.bwNeutrals * 1.5f))
        }
        // Definition (local contrast approximation)
        if (state.definition != 0f) {
            val d = 1f + state.definition / 200f
            matrix.postConcat(contrastMatrix(d))
        }

        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return result
    }

    private fun scaleMatrix(r: Float, g: Float, b: Float) = ColorMatrix(floatArrayOf(
        r, 0f, 0f, 0f, 0f,
        0f, g, 0f, 0f, 0f,
        0f, 0f, b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))

    private fun offsetMatrix(v: Float) = ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f, v,
        0f, 1f, 0f, 0f, v,
        0f, 0f, 1f, 0f, v,
        0f, 0f, 0f, 1f, 0f
    ))

    private fun contrastMatrix(c: Float): ColorMatrix {
        val offset = 128f * (1f - c)
        return ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, offset,
            0f, c, 0f, 0f, offset,
            0f, 0f, c, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        val out = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var n = 0
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val p = pixels[ny * w + nx]
                        r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
                    }
                }
                out[y * w + x] = Color.argb(255, r / n, g / n, b / n)
            }
        }
        val res = src.copy(Bitmap.Config.ARGB_8888, true)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    fun autoEnhance(state: AdjustmentState) {
        state.exposure = 8f
        state.contrast = 12f
        state.brightness = 5f
        state.saturation = 20f
        state.highlights = -25f
        state.shadows = 25f
        state.definition = 15f
        state.sharpness = 20f
        state.brilliance = 10f
    }

    fun scaledPreview(src: Bitmap, maxDim: Int = 800): Bitmap {
        val scale = maxDim.toFloat() / maxOf(src.width, src.height)
        if (scale >= 1f) return src
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
    }
}
