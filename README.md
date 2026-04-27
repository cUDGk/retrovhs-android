# RetroVHS

Android向け VHS/NTSC エフェクトアプリ。
[ntsc-rs](https://github.com/ntsc-rs/ntsc-rs) を参考に、リアルタイムカメラ加工とギャラリー動画/画像加工を提供する。

## ステータス
- **Phase 0**: プロジェクト雛形（current）
- Phase 1: GLSLシェーダ + CameraXでリアルタイム加工
- Phase 2: ギャラリー画像/動画の加工と保存
- Phase 3: ntsc-rs (Rust) をJNIで組み込んだ高画質モード
- Phase 4: モード切替UI + プリセット + 設定永続化
- Phase 5: Playストア提出

## ビルド

1. Android Studio で `vhs-android/` を開く
2. Sync Now で依存解決と `gradle-wrapper.jar` 自動生成
3. Run

最小: Android 8.0 (API 26) / Target: Android 14 (API 34)

## ライセンス

本リポジトリは MIT。
組み込んでいるOSSの帰属表示はアプリ内「ライセンス / OSS」画面に表示。
