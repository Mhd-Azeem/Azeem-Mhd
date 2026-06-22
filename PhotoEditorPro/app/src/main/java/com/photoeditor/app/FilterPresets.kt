package com.photoeditor.app

data class FilterPreset(val name: String, val state: AdjustmentState)

object FilterPresets {
    val all = listOf(
        FilterPreset("Original", AdjustmentState()),
        FilterPreset("Vivid",    AdjustmentState(saturation = 35f, contrast = 15f, brightness = 5f, highlights = -10f)),
        FilterPreset("Dramatic", AdjustmentState(contrast = 45f, saturation = -15f, highlights = -30f, shadows = 10f)),
        FilterPreset("Mono",     AdjustmentState(bwIntensity = 100f, contrast = 10f)),
        FilterPreset("Silvertone", AdjustmentState(bwIntensity = 100f, warmth = 25f, contrast = 15f, brightness = 5f)),
        FilterPreset("Fade",     AdjustmentState(contrast = -25f, warmth = 15f, brightness = 15f, saturation = -10f)),
        FilterPreset("Chrome",   AdjustmentState(contrast = 30f, saturation = 25f, highlights = -20f, shadows = 15f)),
        FilterPreset("Process",  AdjustmentState(warmth = -25f, contrast = 15f, saturation = 10f, brightness = -5f)),
        FilterPreset("Transfer", AdjustmentState(warmth = 35f, contrast = -15f, saturation = -15f, brightness = 10f)),
        FilterPreset("Instant",  AdjustmentState(warmth = 25f, contrast = 30f, bwGrain = 25f, saturation = 10f)),
        FilterPreset("Noir",     AdjustmentState(bwIntensity = 100f, contrast = 40f, bwTone = -20f, bwGrain = 15f)),
        FilterPreset("Warm",     AdjustmentState(warmth = 40f, saturation = 15f, brightness = 5f)),
        FilterPreset("Cool",     AdjustmentState(warmth = -35f, saturation = 10f, brightness = 5f))
    )
}
