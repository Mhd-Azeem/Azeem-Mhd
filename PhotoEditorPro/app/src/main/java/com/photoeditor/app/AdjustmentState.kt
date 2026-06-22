package com.photoeditor.app

data class AdjustmentState(
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
    // Crop/Transform
    var rotationDegrees: Float = 0f,
    var flipHorizontal: Boolean = false,
    var flipVertical: Boolean = false,
    var straighten: Float = 0f,
    // Filter preset name (empty = custom)
    var filterName: String = "Original"
)
