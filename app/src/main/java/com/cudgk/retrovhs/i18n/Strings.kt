package com.cudgk.retrovhs.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

enum class Language(val displayName: String) {
    JA("日本語"),
    EN("English"),
}

private typealias Bundle = Map<Language, String>

private fun b(ja: String, en: String): Bundle = mapOf(Language.JA to ja, Language.EN to en)

object Strings {
    val DICT: Map<String, Bundle> = mapOf(
        // Home
        "app_subtitle" to b("▌ VHS / NTSC エフェクト ▌", "▌ VHS / NTSC EFFECT ▌"),
        "home_camera_title" to b("カメラ", "CAMERA"),
        "home_camera_sub" to b("カメラ起動", "Open Camera"),
        "home_gallery_title" to b("ギャラリー", "GALLERY"),
        "home_gallery_sub" to b("ギャラリーから加工", "Process Gallery"),
        "home_settings_title" to b("設定", "SETTINGS"),
        "home_settings_sub" to b("設定 / プリセット", "Settings / Presets"),
        "home_license_title" to b("ライセンス", "LICENSE"),
        "home_license_sub" to b("OSS / 帰属", "OSS / Credits"),
        "lang_label" to b("言語", "Language"),

        // Camera screen
        "cam_back" to b("戻る", "Back"),
        "cam_close" to b("閉じる", "Close"),
        "cam_settings_title" to b("◆ 設定", "◆ SETTINGS"),
        "cam_mode" to b("モード", "MODE"),
        "cam_mode_photo" to b("写真", "Photo"),
        "cam_mode_video" to b("動画", "Video"),
        "cam_preset" to b("プリセット", "PRESET"),
        "cam_preset_custom" to b("カスタム", "Custom"),
        "cam_shader" to b("シェーダ", "SHADER"),
        "cam_intensity" to b("強度", "INTENSITY"),
        "cam_tint" to b("色フィルタ", "TINT"),
        "cam_res" to b("画質", "RES"),
        "cam_aspect" to b("比率", "ASPECT"),
        "cam_fps" to b("FPS", "FPS"),
        "cam_date" to b("日時", "DATE"),
        "cam_pos" to b("位置", "POS"),
        "cam_rot" to b("向き", "ROT"),
        "cam_on" to b("ON", "ON"),
        "cam_off" to b("OFF", "OFF"),
        "cam_on_burn" to b("ON 焼き込み", "ON burn-in"),
        "cam_perm_required" to b("カメラ権限が必要です", "Camera permission required"),
        "cam_perm_request" to b("権限をリクエスト", "Request permission"),
        "cam_capture_failed" to b("撮影失敗", "Capture failed"),
        "cam_save_failed" to b("保存失敗", "Save failed"),
        "cam_shader_failed" to b("シェーダ処理失敗", "Shader processing failed"),
        "cam_video_saved" to b("動画を保存しました", "Video saved"),
        "cam_record_start_failed" to b("録画開始失敗", "Record start failed"),
        "cam_capture_btn" to b("撮影", "Capture"),
        "cam_video_stop" to b("停止", "Stop"),
        "cam_video_start" to b("録画開始", "Start recording"),
        "cam_lens_flip" to b("前後切替", "Flip lens"),
        "cam_settings_btn" to b("設定", "Settings"),
        "cam_camera_error" to b("カメラエラー", "Camera error"),
        "cam_saved_with" to b("保存", "Saved"),

        // Gallery
        "gal_title" to b("ギャラリー", "Gallery"),
        "gal_pick" to b("画像/動画を選択", "Pick image/video"),
        "gal_intensity" to b("強度", "Intensity"),
        "gal_processing" to b("処理中…", "Processing…"),
        "gal_process" to b("VHS加工して保存", "Process & save"),
        "gal_video_only_shader" to b("動画はシェーダ処理（NTSC-rsはCPU重すぎるため）", "Video uses shader (NTSC-rs is too heavy on CPU)"),
        "gal_rust_unavailable" to b("⚠ NTSC-rsライブラリ読み込み失敗 — シェーダにフォールバック", "⚠ NTSC-rs library failed to load — falling back to shader"),
        "gal_rust" to b("画像処理: NTSC-rs（高画質）", "Image: NTSC-rs (high quality)"),
        "gal_pinch_hint" to b("ピンチで拡大", "pinch to zoom"),
        "gal_saved" to b("保存しました", "Saved"),
        "gal_failed" to b("処理に失敗", "Processing failed"),
        "gal_back" to b("戻る", "Back"),
        "gal_preset" to b("プリセット", "Preset"),

        // Settings screen
        "set_title" to b("設定 / プリセット", "Settings / Presets"),
        "set_current" to b("現在", "Current"),
        "set_preset" to b("プリセット: ", "Preset: "),
        "set_no_preset" to b("未選択 (カスタム)", "None (custom)"),
        "set_strength" to b("強度: ", "Strength: "),
        "set_engine" to b("エンジン: ", "Engine: "),
        "set_choose_preset" to b("プリセットを選択", "Choose preset"),
        "set_reset" to b("デフォルトに戻す", "Reset to defaults"),
        "set_back" to b("戻る", "Back"),
        "set_engine_shader" to b("シェーダ (高速)", "Shader (fast)"),
        "set_engine_rust" to b("NTSC-rs (高画質)", "NTSC-rs (high quality)"),
        "set_intensity_label" to b("強度", "Strength"),

        // License
        "lic_title" to b("ライセンス / OSS", "License / OSS"),
        "lic_intro" to b(
            "本アプリは以下のオープンソースソフトウェアを利用しています。",
            "This app uses the following open-source software.",
        ),
        "lic_license_label" to b("ライセンス: ", "License: "),
        "lic_back" to b("戻る", "Back"),
    )

    fun get(key: String, lang: Language): String =
        DICT[key]?.get(lang) ?: DICT[key]?.get(Language.JA) ?: key
}

val LocalLanguage = compositionLocalOf { Language.JA }

@Composable
fun s(key: String): String = Strings.get(key, LocalLanguage.current)
