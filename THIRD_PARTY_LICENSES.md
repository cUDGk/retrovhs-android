# サードパーティライセンス

本アプリは以下のオープンソースソフトウェアを利用しています。各ライセンスの全文は本ファイル末尾に記載。

| ソフトウェア | バージョン / 取得元 | ライセンス |
| --- | --- | --- |
| [ntsc-rs](https://github.com/ntsc-rs/ntsc-rs) | git main (`crates/ntscrs`) | MIT / Apache-2.0 / ISC |
| [jni-rs](https://github.com/jni-rs/jni-rs) | crates.io 0.21 | MIT / Apache-2.0 |
| [image-rs](https://github.com/image-rs/image) | crates.io 0.25 | MIT / Apache-2.0 |
| AndroidX / Jetpack Compose | Maven (multiple) | Apache-2.0 |
| CameraX | Maven 1.3.4 | Apache-2.0 |
| DataStore | Maven 1.1.1 | Apache-2.0 |
| Kotlin Standard Library | Maven 1.9.24 | Apache-2.0 |
| [Coil](https://github.com/coil-kt/coil) | Maven 2.6.0 | Apache-2.0 |
| [VCR OSD Mono](https://www.dafont.com/vcr-osd-mono.font) (フォント) | bundled TTF | 個人/商用利用可（Riciery Leal 作） |

ntsc-rs の各ライセンス（MIT / Apache-2.0 / ISC）は受け手が任意の1つを選択して適用できます。本リポジトリ全体は MIT で公開していますが、依存関係の各ライセンスは下記の通り保持されます。

---

## MIT License

```
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

該当: ntsc-rs, jni-rs, image-rs

---

## Apache License 2.0

```
                              Apache License
                        Version 2.0, January 2004
                     http://www.apache.org/licenses/

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
```

全文: https://www.apache.org/licenses/LICENSE-2.0.txt

該当: AndroidX / Jetpack Compose, CameraX, DataStore, Kotlin, Coil, ntsc-rs, jni-rs, image-rs

---

## ISC License

```
Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

該当: ntsc-rs

---

## VCR OSD Mono Font

`app/src/main/assets/fonts/vcr_osd_mono.ttf` および `app/src/main/res/font/vcr_osd_mono.ttf`

Designed by Riciery Leal (mrmanet@yahoo.com.br). Free for personal and commercial use.
出典: https://www.dafont.com/vcr-osd-mono.font

---

## 著作権表記

各 OSS の Copyright は元プロジェクトの README/LICENSE を参照。アプリ内の「ライセンス / OSS」画面でも帰属を表示しています。
