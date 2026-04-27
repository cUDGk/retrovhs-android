package com.cudgk.retrovhs.settings

data class Preset(
    val name: String,
    val description: String,
    val intensity: Float,
    val engine: Engine,
)

object Presets {
    val ALL = listOf(
        Preset("クリーン", "ほぼ加工なし", 0.15f, Engine.SHADER),
        Preset("弱VHS", "薄くノスタルジック", 0.4f, Engine.SHADER),
        Preset("VHS 90s", "標準的なVHSルック", 0.7f, Engine.SHADER),
        Preset("壊れたテープ", "強烈なノイズと滲み", 1.0f, Engine.SHADER),
        Preset("高画質 (NTSC-rs)", "オフライン高品質", 1.0f, Engine.RUST),
    )

    fun byName(name: String?): Preset? = ALL.firstOrNull { it.name == name }
}
