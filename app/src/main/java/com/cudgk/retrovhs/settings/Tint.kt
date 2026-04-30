package com.cudgk.retrovhs.settings

enum class Tint(
    val displayName: String,
    val r: Float,
    val g: Float,
    val b: Float,
    val saturation: Float,
) {
    NONE("なし", 1.0f, 1.0f, 1.0f, 1.0f),
    SEPIA("セピア", 1.0f, 0.85f, 0.6f, 0.0f),
    MONO("モノクロ", 1.0f, 1.0f, 1.0f, 0.0f),
    COOL("ブルー", 0.8f, 0.95f, 1.2f, 0.4f),
    WARM("レッド", 1.2f, 0.85f, 0.7f, 0.4f),
    MATRIX("グリーン", 0.7f, 1.2f, 0.8f, 0.5f),
    NIGHT("ナイトモード", 0.5f, 0.85f, 1.4f, 0.3f),
    FADED("色褪せ", 1.05f, 1.0f, 0.95f, 0.55f);

    val rgbArray: FloatArray get() = floatArrayOf(r, g, b)
}
