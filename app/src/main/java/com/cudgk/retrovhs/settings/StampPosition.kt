package com.cudgk.retrovhs.settings

enum class StampPosition(
    val displayName: String,
    val isLeft: Boolean,
    val isTop: Boolean,
) {
    BOTTOM_RIGHT("右下", false, false),
    BOTTOM_LEFT("左下", true, false),
    TOP_RIGHT("右上", false, true),
    TOP_LEFT("左上", true, true);
}
