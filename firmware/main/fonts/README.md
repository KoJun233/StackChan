# StackChan UI CJK 14

由本地 OFL 字体离线生成的衍生点阵字库，名称不使用保留字体名称。支持 ASCII/Latin-1、常用标点、CJK 扩展 A/基本区和全角字符；其他字符使用 LVGL 缺字回退。

- 字符数：28073
- 二进制字节：979760
- 原字体 SHA-256：`1ee89e1669362dee13851129c0a8a791a87521eb4148e5efbf5d26596738e25b`
- 制品 SHA-256：`10e97be9660e74651a7798a6d0a7ea4f21b981c9c3eed3564fb0cc91cb1c550b`
- 复现：`python scripts/generate-device-ui-font.py`（Pillow/FreeType 渲染版本会影响像素；交付以制品摘要为准）
- 许可：[OFL](OFL.txt)，保留 Adobe 原版权声明。

固定控制使用同一许可字体的灰阶子集，任意伙伴名/卡片内容仍回退到上述完整中文字库，不删除中文覆盖：

- `device_menu18.bin`：255 字符，18px/2bpp，18,293 字节；SHA-256 `20d6efa1ba380cc189486ae6690709202c8a55572b745fe00d4a771dfa1fefd8`。
- `device_numbers32.bin`：14 字符，32px/2bpp，1,541 字节；SHA-256 `274dd5cba1a57787fe45e6f889fe012e568001996bab95c45d7861edd337f02d`。
- 复现：`python scripts/generate-device-menu-font.py`；输入原字体摘要同上。Pillow/FreeType版本影响像素，交付以固定制品摘要为准。
