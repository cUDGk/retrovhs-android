# RetroVHS

Android向け VHS / NTSC エフェクトアプリ。
カメラのライブプレビューに OpenGL ES シェーダで VHS エフェクトを適用、
写真・動画として保存。ギャラリーから既存の画像 / 動画を読み込んで加工も可能。
画像加工は [ntsc-rs](https://github.com/ntsc-rs/ntsc-rs) (Rust) を JNI 経由で呼ぶ高画質モードも選べる。

## 機能

- **カメラ**: リアルタイムシェーダ加工 + 写真撮影 + 動画録画 (H.264 + AAC + MP4)
- **ギャラリー**: 画像 / 動画を選択して VHS 加工して保存
- **エンジン切替** (画像のみ):
  - シェーダ (高速・全機種)
  - NTSC-rs (オフライン高画質、Rust ネイティブ)
- **プリセット**: クリーン / 弱VHS / VHS 90s / 壊れたテープ / 高画質(NTSC-rs)
- **設定永続化**: 強度、FPS、レンズ、エンジン、最終プリセット (DataStore)
- **撮影コントロール**: 強度スライダ、FPS (15/24/30/60)、前後カメラ切替

## 必要環境

- Android Studio (AGP 8.5+)
- JDK 17+
- Android SDK 34, NDK 27+
- Rust (`rustup`) + cargo-ndk + Android targets:
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
  cargo install cargo-ndk
  ```

## ビルド

通常の `./gradlew assembleDebug` で OK。
preBuild に `cargoNdkBuild` タスクが繋がっているため、Rust 部分は自動でクロスコンパイルされて
`app/src/main/jniLibs/<abi>/libntscrs_jni.so` に配置される。

手動で Rust だけビルドする場合：
```bash
./rust/build-android.sh
# または
cd rust/ntscrs-jni && cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -t x86 -o ../../app/src/main/jniLibs build --release
```

最小: Android 8.0 (API 26) / Target: Android 14 (API 34)

## ライセンス

本リポジトリは MIT。
組み込んでいる OSS の帰属表示はアプリ内「ライセンス / OSS」画面に表示。

- ntsc-rs: MIT / Apache-2.0 / ISC
- AndroidX / Jetpack Compose: Apache-2.0
- Kotlin: Apache-2.0
- Coil: Apache-2.0
