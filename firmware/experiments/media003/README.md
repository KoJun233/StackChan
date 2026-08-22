# MEDIA-003 渲染后端评估

本目录只承载隔离实验，不改变默认固件。未显式设置
`STACKCHAN_MEDIA003_BACKEND` 时仍编译 `native`，继续使用已经通过实机验收的
原生 LVGL 动态球体。

## 候选边界

| profile | 用途 | 是否可替代默认渲染器 |
| --- | --- | --- |
| `native` | 当前原生 LVGL 连续表情基线 | 是，当前默认 |
| `eaf` | 用项目自有 18 帧 RLE EAF 验证开机等有限时长动画 | 否，只是候选生命周期片段 |
| `emote` | 验证 `esp_emote_gfx` 3.0.5 可初始化并完整释放 | 否，未接管显示，也不是完整渲染基准 |

EAF 与 Emote profile 使用独立依赖锁，不会污染根目录的稳定依赖锁；同口径 native
5.5 对照锁保存在 `dependencies.native-idf55.lock`。两个候选当前都要求 ESP-IDF 5.5；
默认固件继续使用已验证的 ESP-IDF 5.4.4。

## 自有 EAF 基准素材

`generate-eaf-benchmark.mjs` 以 clean-room 方式生成一段 `160×160`、18 帧、4-bit
调色板、RLE 编码的抽象球体动画，不包含或转换任何第三方源码、轮廓、配色或素材。

```powershell
node firmware/experiments/media003/generate-eaf-benchmark.mjs
node --test firmware/experiments/media003/generate-eaf-benchmark.test.mjs
```

当前确定性制品：

- 大小：53,854 字节
- SHA-256：`ABD64A59F59CAFF9CEE7A65921781BF6FE28B150AD876ADDF8CF52505690FE81`
- 约束：160×160、18 帧、每帧 10 个 16 行块、只使用 RLE

## 同口径完整固件结果

LAN HTTP Quad profile 均从干净 sdkconfig 构建，EAF/Emote/native 5.5.5 使用同一工具链：

| 方案 | ESP-IDF | 应用大小 | 相对同版 native | 说明 |
| --- | --- | ---: | ---: | --- |
| 稳定 native | 5.4.4 | 1,581,488 B | — | 与 MEDIA-002 最终完整固件完全同尺寸 |
| native 对照 | 5.5.5 | 1,605,088 B | 0 B | 仅用于候选同口径比较 |
| EAF RLE-only | 5.5.5 | 1,669,504 B | +64,416 B | 其中 EAF 自有素材 53,854 B |
| Emote lifecycle | 5.5.5 | 1,615,776 B | +10,688 B | 只测 init/deinit，未链接完整动画与刷新路径 |

这些数据只证明编译、依赖隔离和静态容量，不证明真机 FPS、WakeNet/TTS 并发或取消安全。
`esp_emote_gfx` 的数字尤其不能与 EAF 的完整播放路径直接比较。

## 实体结果

提交绑定候选 `71868da` 通过 LAN HTTP 应用 OTA 安装，保留 NVS、未主动演练回退。用户确认
开机 EAF 片段结束后恢复 native、连续三次唤醒对话与回答声音正常、播放中触摸取消和下一回合
正常，且无黑屏、卡住或自动重启。稳定心跳约为 55/60 FPS、场景更新 1097 μs、LVGL 刷新
22627 μs、锁等待 10 μs、音频 underrun 0、最低空闲堆 7,735,712 字节。

该结果接受 EAF 作为有限生命周期片段，不接受其替代持续 native 渲染，也不改变 Emote
lifecycle profile 的“不可刷写、不可作为性能结论”边界。

## 构建选择

先按项目开发文档激活对应 ESP-IDF，再使用独立 build 目录和 sdkconfig。EAF 的
`SDKCONFIG_DEFAULTS` 还应追加 `experiments/media003/sdkconfig.eaf.defaults`，避免为
纯 RLE 基准保留未使用的软件 JPEG 解码器。

```powershell
$env:SDKCONFIG_DEFAULTS = 'sdkconfig.defaults;sdkconfig.lan-http.defaults;experiments/media003/sdkconfig.eaf.defaults'
idf.py -B build-media003-eaf-rle-full -DSDKCONFIG=build-media003-eaf-rle-full/sdkconfig -DSTACKCHAN_MEDIA003_BACKEND=eaf build
```

实体测试必须按 [`MEDIA-003 EAF smoke test`](../../../docs/runbooks/media003-eaf-smoke-test.md)
执行。未经逐次授权，不刷写候选固件。
