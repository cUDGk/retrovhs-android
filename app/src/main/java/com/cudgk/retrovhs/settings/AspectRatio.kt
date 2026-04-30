package com.cudgk.retrovhs.settings

enum class AspectRatio(val displayName: String, val w: Int, val h: Int) {
    RATIO_4_3("4:3", 4, 3),
    RATIO_16_9("16:9", 16, 9),
    RATIO_1_1("1:1", 1, 1);

    /** Returns ratio as portrait-oriented (taller than wide) for display sizing. */
    val portraitRatio: Float get() = when (this) {
        RATIO_4_3 -> 3f / 4f      // portrait: 3 wide / 4 tall
        RATIO_16_9 -> 9f / 16f    // portrait: 9 wide / 16 tall
        RATIO_1_1 -> 1f
    }
}
