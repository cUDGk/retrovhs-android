package com.cudgk.retrovhs.settings

enum class Quality(
    val displayName: String,
    val width: Int,
    val height: Int,
    val bitRate: Int,
) {
    SD480("480p", 854, 480, 3_000_000),
    HD720("720p", 1280, 720, 6_000_000),
    FHD1080("1080p", 1920, 1080, 10_000_000),
    QHD1440("1440p", 2560, 1440, 16_000_000);

    val displayLabel: String get() = displayName
}
