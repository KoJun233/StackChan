# 陪伴机器人表情渲染调研

- 文档状态：REFERENCE
- 调研日期：2026-08-23
- 关联任务：`MEDIA-002`、`MEDIA-003`
- 目的：为 StackChan 的表情管理、角色情绪和多资源常驻方案提供可复核的产品与技术依据

## 当前问题

StackChan 已有安全可靠的八状态资源包，但当前体验主要受以下结构限制：

- `idle/listening/processing/speaking/success/no_speech/offline/error` 同时承担系统状态和角色表情，无法表达“正在说话但语气开心”这类组合状态。
- 管理页要求在同一长页面逐张上传八张全屏 PNG，资源包创建与目标设备选择混在一起。
- 自定义图片是静态全屏画面，切换时没有眨眼、视线、嘴型或过渡动画；上传更多整图也不会自然获得生命感。
- 内置机械眼被固定为每 100 ms 一帧，即 10 FPS；眨眼阶段短于渲染周期，视线和嘴型按离散时间片跳变，状态切换也没有当前姿态到目标姿态的插值。
- 设备 A/B 槽用于一次资源更新的安全切换，成功后会擦除旧槽，因此角色切换仍需要重新安装资源包。
- 八张 `320×240` 图片会快速消耗 1.5 MiB 槽空间，不适合直接扩展成几十张全屏情绪图片。

## 开源实现对比

### 小智 ESP32

调研基于 `78/xiaozhi-esp32` 提交 [`d6f6b64`](https://github.com/78/xiaozhi-esp32/tree/d6f6b642977940b862f6f3026c3915df75d388b6) 与资源生成器提交 [`55517b4`](https://github.com/78/xiaozhi-assets-generator/tree/55517b40d724014faff00f941ca700cbf9d14b51)。

小智采用“稳定情绪名称 + 板端渲染集合”的结构：

- WebSocket `llm` 消息只下发 `emotion` 名称，显示层通过 `SetEmotion()` 查找对应素材；协议与具体图片解耦。
- 默认集合包含 `neutral/happy/laughing/funny/sad/angry/crying/loving/embarrassed/surprised/shocked/thinking/winking/cool/relaxed/delicious/kissy/confident/sleepy/silly/confused` 21 个名称。
- 板端优先使用自定义 PNG/GIF；找不到图片时回退到 Emoji 字体或 Material Symbols，不会因为缺少一张素材而失去整个显示能力。
- 资源 `index.json` 保存名称到文件的映射。资源生成器提供预设集合、自定义尺寸、本地浏览器打包和基于文件摘要的重复素材去重。
- GIF 播放具有独立控制器，切换情绪时先停止旧动画，再在同一显示锁内替换资源，避免旧帧继续访问已释放数据。
- 字体、表情、背景和唤醒模型共用一个 assets 分区和主题包；它解决了“定制主题”，但没有直接解决 StackChan 所需的多角色包同时常驻与瞬时切换。

可复核源码：

- [情绪协议](https://github.com/78/xiaozhi-esp32/blob/d6f6b642977940b862f6f3026c3915df75d388b6/docs/websocket.md)
- [`LcdDisplay::SetEmotion`](https://github.com/78/xiaozhi-esp32/blob/d6f6b642977940b862f6f3026c3915df75d388b6/main/display/lcd_display.cc)
- [EmojiCollection](https://github.com/78/xiaozhi-esp32/tree/d6f6b642977940b862f6f3026c3915df75d388b6/main/display/lvgl_display)
- [资源索引加载](https://github.com/78/xiaozhi-esp32/blob/d6f6b642977940b862f6f3026c3915df75d388b6/main/assets.cc)
- [浏览器资源生成器](https://github.com/78/xiaozhi-assets-generator/blob/55517b40d724014faff00f941ca700cbf9d14b51/web/src/utils/AssetsBuilder.js)

值得吸收的是语义解耦、素材回退、预设与自定义并存、重复素材去重和本地预处理；不应照搬它的完整 assets 分区，也不应未经性能验证就把 GIF 引入当前 LVGL 渲染链。

### 乐鑫 EAF 与 ESP Emote GFX

EAF 全称为 Emote Animation Format。乐鑫官方 [`esp_lv_eaf_player`](https://github.com/espressif/esp-iot-solution/tree/master/components/display/tools/esp_lv_eaf_player) 0.3.0 支持 RLE、Huffman、JPEG 压缩、RGB565、逐帧控制和循环播放，并提供从 GIF 等源素材生成 EAF 的转换器。该组件面向 LVGL v8/v9，但明确要求 ESP-IDF 5.5 及以上；StackChan 已在 `MEDIA-002D` 迁移到 LVGL 9.4，显示栈兼容，稳定工具链仍需从 5.4.4 迁移后才能正式采用。

[乐鑫 FAQ](https://docs.espressif.com/projects/esp-faq/zh_CN/latest/esp-faq-zh_CN-master.pdf) 对性能边界给出了更直接的建议：EAF 播放慢时优先选择 JPEG 编码；相比 EAF 经 LVGL 播放，直接使用 [`esp_emote_gfx`](https://components.espressif.com/components/espressif2022/esp_emote_gfx) 分段解码刷屏效率更高。`esp_emote_gfx` 使用 Apache-2.0，支持由应用提供 flush callback、RGB565、双缓冲、DMA buffer、任务优先级与 CPU affinity；但它会绕过当前 LVGL 对象树，只有实机基准显著胜出时才值得增加第二套刷新路径。

但 `esp_emote_gfx` 与 [`esp_emote_expression`](https://components.espressif.com/components/espressif2022/esp_emote_expression) 的发布者是组件注册表中的 `espressif2022`，GitHub 所有者也是普通 User，而不是乐鑫官方 `espressif` 组织。它们功能贴近 AI 机器人，并不等于已经具有官方维护承诺。

### 维护成熟度快照

以下数据采集于 2026-08-22，只用于判断维护与采用风险；Star 数本身不等于性能。

| 候选 | 维护与采用信号 | 与 CoreS3 的适配 | 当前判断 |
| --- | --- | --- | --- |
| [M5Unified](https://github.com/m5stack/M5Unified) + [M5GFX](https://github.com/m5stack/M5GFX) | M5Stack 官方；分别约 685/369 Stars；两仓库在 2026-08-20/21 仍有更新 | 原生支持 CoreS3，但 M5Unified 固定依赖 M5GFX，无法在保留整套板级抽象时单独替换显示后端 | 库本身成熟；因本项目显示/音频资源竞争与不可拆分依赖，已在 `MEDIA-002D` 移除 |
| 官方 [`m5stack_core_s3`](https://components.espressif.com/components/espressif/m5stack_core_s3) BSP + [LVGL](https://github.com/lvgl/lvgl) + [`esp_lvgl_port`](https://components.espressif.com/components/espressif/esp_lvgl_port) | 乐鑫组件注册表官方 BSP 4.0.0；LVGL 约 24.4k Stars；官方 port 2.9.0 有 37 个版本、51 个 dependents、4 个 examples | 覆盖 ILI9342C、FT5x06、AW88298、ES7210、BMI270、DMA 刷新和 LVGL 任务 | `MEDIA-002D` 已采用；是当前唯一固件硬件/显示边界 |
| [`esp_lv_eaf_player`](https://components.espressif.com/components/espressif/esp_lv_eaf_player) | 乐鑫官方；当前 0.3.0、4 个版本、0 dependents、0 examples | 可直接播放 EAF，但强依赖 LVGL v8/v9 | 官方但仍年轻，不足以单独驱动整栈迁移 |
| [`esp_emote_gfx`](https://github.com/espressif2022/esp_emote_gfx) | 3.0.5、约 18 Stars/6 Forks；注册表 14 个版本、2 dependents、1 example | flush callback 和分段 buffer 很适合 160×160 小屏接入 | 最值得做性能原型，但社区规模小且非官方组织 |
| [`esp_emote_expression`](https://github.com/espressif2022/esp_emote_expression) | 1.0.2、约 6 Stars/1 Fork；注册表 6 个版本、1 dependent、0 examples | 已有事件、分区/mmap、JSON 布局、任务亲和与双缓冲 | 功能最贴近本项目，但成熟度不足以直接取代现有协议与状态机 |

结论修订为完整迁移到官方 CoreS3 BSP + LVGL 9.4，并从应用依赖中移除 M5Unified/M5GFX。原生 LVGL 参数渲染继续负责眨眼、视线、说话等连续动作；EAF 候选只负责开机、切换、强调情绪等有限时长资源动画。`esp_emote_gfx` 拥有自己的任务、缓冲与 flush 边界，在没有同素材完整显示实测前不作为第二渲染后端。

StackChan 已有 `expression_a` / `expression_b` 两个 1.5 MiB A/B 分区，可以承载版本化 EAF 二进制包；但大量长动画仍可能超出单槽，资源大小、解码耗时和 SPI 传输必须按片段实测。下一资源格式只允许补充当前可回退的原生渲染器，不能替代离线、错误、说话和空闲等持续表现。

### MEDIA-003 可复现原型结果

2026-08-23 使用项目自有生成器制作了 `160×160`、18 帧、4-bit 调色板、RLE 编码的
EAF 球体片段。制品为 53,854 字节，SHA-256 为
`ABD64A59F59CAFF9CEE7A65921781BF6FE28B150AD876ADDF8CF52505690FE81`。
生成器同时校验外层签名、长度、checksum、帧表和每帧 magic，素材不包含第三方代码、轮廓或配色。

LAN HTTP Quad 完整固件的同口径结果如下：

| 方案 | ESP-IDF | 应用大小 | 相对同版 native | 可得出的结论 |
| --- | --- | ---: | ---: | --- |
| 稳定 native | 5.4.4 | 1,581,488 B | — | 与 MEDIA-002 已验收制品同尺寸，默认路径未变化 |
| native 对照 | 5.5.5 | 1,605,088 B | 0 B | 工具链迁移本身增加 23,600 B |
| 官方 EAF，RLE-only | 5.5.5 | 1,669,504 B | +64,416 B | 素材占 53,854 B，播放器与接入净增约 10,562 B |
| `esp_emote_gfx` lifecycle | 5.5.5 | 1,615,776 B | +10,688 B | 只完成 init/deinit，没有动画资源和显示接管，不能作为性能比较 |

因此 `MEDIA-003` 的候选边界是：默认 native 不变；EAF 只在开机约 1.8 秒的生命周期窗口
显示，结束后回到 native；Emote 只保留隔离生命周期 probe。EAF 是否进入正式资源协议仍取决于
CoreS3 实体测试中的 WakeNet、TTS、触摸取消、重连、最低堆和重启稳定性。详细决策见
[ADR 0039](decisions/0039-native-renderer-with-bounded-eaf-lifecycle-clips.md)。

### M5Stack Avatar

[`stack-chan/m5stack-avatar`](https://github.com/stack-chan/m5stack-avatar) 使用程序化脸部组件，支持开心、愤怒、悲伤等表情、颜色主题、移动缩放旋转和唇形同步。渲染运行在独立任务中，业务循环不需要逐帧驱动。

它说明程序化眼睛、嘴和颜色参数很适合小屏幕陪伴机器人：资源体积小，眨眼、视线和说话嘴型可以连续变化。不过该库当前冻结在 v0.10.0，基于 Arduino/M5Unified，StackChan 不直接引入；适合复用的是“脸部部件 + 表情参数 + 独立动画时钟”的设计。

### LVGL Kawaii Face

[`0015/lvgl_kawaii_face`](https://github.com/0015/lvgl_kawaii_face) 展示了 17 种程序化情绪以及眼眉、腮红、眼泪、汗滴、弹跳和粒子等随时间变化的效果。它证明“同一张脸的细小运动”比静态换图更容易产生情绪感。

该项目历史较短且基于 LVGL 9，不能作为 StackChan 的直接依赖；可参考其情绪到动画参数的映射和过渡设计。

### RoboEyes

[`FluxGarage/RoboEyes`](https://github.com/FluxGarage/RoboEyes) 的自动眨眼、闲置视线和眼形情绪是有价值的视觉参考，但许可证为 GPL-3.0。StackChan 继续遵守 ADR 0022：只参考公开可观察的交互概念，不复制其源代码或派生素材。

### Emotion Ball

调研基于 `sam70361/emotion-ball` 提交 [`b4f97df`](https://github.com/sam70361/emotion-ball/tree/b4f97df96c022e08e24afad266dddf5f62cfb5fd)。该项目使用纯 SVG 与浏览器 JavaScript 实现 32 个可见状态、三类球体、48 点眼睛轮廓、弹簧插值、眨眼、视线、呼吸、扫描、抖动和有界粒子，并通过稳定 `emotionId` 把 AI 语义与渲染配置解耦。

它不能作为 CoreS3 固件的直接依赖：实现依赖 DOM、`requestAnimationFrame`、SVG Path 字符串和逐帧节点更新，而当前固件是 ESP-IDF C++ 与 LVGL。移植实质上仍是把视觉语义重写为受限 LVGL 对象与动画参数，不是引入一个浏览器库。

默认 [`LICENSE`](https://github.com/sam70361/emotion-ball/blob/b4f97df96c022e08e24afad266dddf5f62cfb5fd/LICENSE) 仅允许非商业学习、研究和技术交流，商业使用需要另行取得 [`LICENSE-COMMERCIAL.md`](https://github.com/sam70361/emotion-ball/blob/b4f97df96c022e08e24afad266dddf5f62cfb5fd/LICENSE-COMMERCIAL.md) 所述授权。因此 StackChan 只吸收稳定语义、参数化姿态、弹簧过渡和空闲策略等通用思想，不复制源码、轮廓点、配置、配色或粒子参数。

## 商业产品的体验线索

商业产品没有公开渲染实现，以下内容只用于产品行为观察：

- [LivingAI EMO](https://living.ai/emo/) 将面部动画与身体动作组合，并宣称提供 1000 余种表情与动作。重要线索不是绝对数量，而是表情会响应打断、人物识别、节日和环境状态。
- [Energize Lab Eilik](https://store.energizelab.com/products/eilik) 在没有交互时仍会播放自发表情和动作，并按空闲时间进入待机、睡眠和关机；触摸不同位置会触发不同情绪。
- [Eiliko](https://ae.energizelab.com/pages/eiliko-feature) 将五种长期心情与多种短时动画分开，并支持触摸、摇晃和离线命令触发表情。

这些产品共同强调三点：持续的小动作、输入事件触发和“心情”随时间变化。仅增加静态素材数量不会自动得到相同体验。

## 对 StackChan 的结论

### 四层模型

后续渲染应明确分为五层：

1. **系统状态**：错误、离线和更新等确定性状态，本地计算且优先级最高。
2. **物理短时反应**：触摸、靠近和摇晃产生的短时表现，不能覆盖系统状态。
3. **交互阶段**：倾听、思考、说话和完成，由设备与服务端确定。
4. **角色情绪与外观**：模型只能建议受限情绪和三级强度；角色只提供主色与获准主题。
5. **待机动画**：眨眼、视线、呼吸和短暂强调效果，由设备本地时钟驱动，不依赖网络逐帧发送。

渲染优先级为“错误/离线/更新 > 物理短时反应 > 当前交互阶段 > 角色情绪 > 待机动画”。离线和错误必须始终能够使用固件内置渲染器表达。

### 双渲染与格式方向

- v1 八状态 PNG 保持兼容，不改变现有制品或已安装资源包。
- 默认模式演进为固件原生的 160×160 抽象球体，不绘制嘴巴，以眼睛轮廓、球体形变、角色主色和最多八个粒子形成动态表现。
- 动态引擎采用 12 种情绪、8 种系统/交互状态和 6 种生命周期/物理行为的分层组合，不创建 26 张全屏图片。
- 首版只允许选择内置动态主题和角色主色，不接收模型或服务端下发的任意轮廓与动画 JSON。
- GIF、图片序列、动态主题编辑器和多自定义外观常驻必须通过 CoreS3 解码时间、PSRAM 峰值、取消和切换稳定性基准后另行接受。
- 不内置来源或许可不清晰的第三方 Emoji、角色图和生成图片。管理员上传内容继续遵守现有隐私、删除和日志边界。

### 帧率与带宽方向

[`StackChan 官方规格`](https://docs.m5stack.com/zh_CN/StackChan/) 确认设备使用 320×240、65536 色的 ILI9342C。控制器资料给出 60–70 Hz 的面板扫描设置，但 60 张 RGB565 完整画面需要约 73.7 Mbit/s，不能把“面板 60 Hz”解释为“全屏 60 FPS”。当前官方 BSP + LVGL 使用 40 行双 DMA 缓冲和对象失效区域刷新，60 FPS 仍以实机诊断为准。

- 动画计算时钟以 60 Hz 为目标，只刷新球体或眼睛变化区域；页面切换才整屏刷新。
- 管理员可按整数设置 1–60 FPS 固定值或自适应范围；自适应根据真实绘制、传输、锁等待、丢帧和音频 underrun 在范围内调节，音频始终优先。
- 管理端需要显示目标/实际 FPS、帧耗时、降帧原因、活动状态层和堆最低水位；没有这些指标不得凭观感宣称达到 60 FPS。
- 多自定义外观常驻不再是动态表情的前置条件；是否建设资源目录银行在原生引擎真机验收后重新评估。

## 分阶段交付

1. `MEDIA-002A`：重构管理页，分离创建和设备启用，支持文件夹批量导入、8/8 进度、逐项预览与说明；保持 v1 协议不变。
2. `MEDIA-002B/D`：实现独立动画时钟、160×160 局部刷新、连续 1–60 FPS 固定/范围策略与诊断，以及中性、开心、生气、惊讶、倾听、思考、说话、错误八种原型和音频并发降载。
3. `MEDIA-002C`：补齐 12 种情绪、8 种系统/交互状态、6 种生命周期/物理行为、结构化情绪协议、角色主题色和传感器触发。
4. `MEDIA-002D`：将硬件与显示栈完整迁移到官方 `m5stack_core_s3` BSP 4.0.0 + LVGL 9.4，统一显示、触摸、Codec 音频和 BMI270 边界；保留现有协议、OTA 与 PNG A/B 兼容。
5. `MEDIA-003` 候选：在现有 LVGL 栈上用项目自有素材比较原生参数渲染、官方 `esp_lv_eaf_player` 与 `esp_emote_gfx`；只有资源包大小、空闲帧率、切换延迟、WakeNet 和 TTS 并发均通过后，才新增动态资源格式。
