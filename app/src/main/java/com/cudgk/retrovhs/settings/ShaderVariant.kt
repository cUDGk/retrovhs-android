package com.cudgk.retrovhs.settings

enum class ShaderVariant(
    val displayName: String,
    val chromaAb: Float,
    val scanline: Float,
    val grain: Float,
    val vignette: Float,
    val jitter: Float,
    val chromaBlur: Float,
) {
    OFF("オフ", 0f, 0f, 0f, 0f, 0f, 0f),
    SOFT("ソフト", 0.4f, 0.5f, 0.3f, 0.4f, 0.0f, 0.7f),
    STANDARD("標準", 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f),
    NOISY("ノイジー", 1.5f, 1.3f, 1.8f, 1.3f, 1.5f, 1.4f),
    CINEMA("シネマ", 0.7f, 0.3f, 0.15f, 0.55f, 0.0f, 0.8f);
}
