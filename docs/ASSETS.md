# Visual assets / 视觉资源 — 0.4.1

0.4.1 directly includes original PNG files from the official **Applied Energistics 2, Minecraft 1.21.1** source tree at commit [`db17504a86128fdf3dae31f5fb7a112a646e0b93`](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/db17504a86128fdf3dae31f5fb7a112a646e0b93). These are actual upstream resources, not a generated approximation or a substitute taken from the installed AE2 15.x runtime. The addon itself still runs on **Minecraft 1.20.1 Forge**.

0.4.1 直接采用上述固定提交中官方 **AE2 Minecraft 1.21.1** 的原始 PNG 文件，不再以生成图片近似官方风格，也没有用运行环境中的 AE2 15.x 资源替代。模组运行版本仍为 **Minecraft 1.20.1 Forge**。

## Source files / 来源文件

The thirteen included files live under `src/main/resources/assets/me_storage_controller/textures/ae2_1_21/`: five generic block surfaces, the monitor front, three terminal masks, and four GUI textures. Their relative paths match those under upstream `src/main/resources/assets/ae2/textures/`. The exact file list and SHA-256 digests are recorded in [AE2-ASSET-NOTICE.txt](../src/main/resources/AE2-ASSET-NOTICE.txt), which is also shipped inside the release JAR. Each included PNG is verified byte-for-byte against the pinned upstream source.

发行包现在实际包含十三张第三方 PNG：五张通用机器表面、一张显示器面板、三张终端遮罩和四张 GUI 纹理。资源统一存放于 `textures/ae2_1_21/`，保留官方 `assets/ae2/textures/` 中的相对路径。具体文件清单、原作者和 SHA-256 见上面的署名文件，该文件同时打包进 JAR。所有收录 PNG 均与固定提交的原文件逐一进行哈希校验。

Reference materials include `screens/common/palette.json`, `screens/terminals/base_terminal.json`, the terminal GUI textures, generic block surfaces and monitor textures from that same commit. GUI drawing and input handling are independently implemented; the project does not copy AE2's LGPL Java implementation.

界面实现实际参考同一提交的调色板、终端布局 JSON 和 GUI 纹理，方块参考通用机器表面与显示器资源。GUI 绘制与交互代码为独立实现，没有复制 AE2 的 LGPL Java 实现。

## Attribution and license / 署名与许可

Upstream's [README license section](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db17504a86128fdf3dae31f5fb7a112a646e0b93/README.md#license) identifies **Textures and Models** separately from the software:

- **© 2020, Ridanisaurus Rid** — <https://github.com/Ridanisaurus/>
- **© 2013–2020, AlgorithmX2 et al** — Applied Energistics 2 contributors.
- License: **Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported (CC BY-NC-SA 3.0)** — <https://creativecommons.org/licenses/by-nc-sa/3.0/>.

The full, unmodified legal text downloaded from Creative Commons is included at [licenses/CC-BY-NC-SA-3.0.txt](../src/main/resources/licenses/CC-BY-NC-SA-3.0.txt) and inside the JAR. Retain the asset notice and license when redistributing. The included AE2 artwork, derived JSON models and this addon's visual adaptations of that artwork are subject to attribution, noncommercial use and share-alike requirements; they are **not MIT-licensed**. Original addon Java code remains MIT-licensed. These separate licenses are identified in the release metadata.

官方 README 将纹理、模型与软件代码分别授权。上述 AE2 美术资源、衍生 JSON 模型及本模组对其作出的视觉改编采用 **CC BY-NC-SA 3.0**：须署名、遵守非商业限制，并按相同许可要求分享改编资源，不能将其标注为 MIT。原创 Java 代码仍采用 MIT。完整许可文本与署名随 JAR 提供，转发时应一并保留。

## Changes and version adaptation / 改动与版本适配

The copied **PNG files are unmodified**. Cropping, texture-coordinate selection, tinting, layout and recombination happen in the addon's drawing code and JSON models. The controller combines a custom box/frame with the official monitor surface and three terminal masks. The masks use AE2's Fluix dark, medium and bright colors (`#5a479e`, `#915dcd`, `#e2a3e3`); a Forge 1.20.1 renderer supplies full-bright online layers and a slow 100-tick pulse. Offline presentation dims the colors to 40%; the block's actual AE node state still controls its `LIT` property.

复制的 **PNG 像素和文件均未修改**。裁切、UV 选择、配色、布局及重组在本模组的绘制代码和 JSON 模型中完成。控制器以自定义箱体和像素边框组合官方显示器面板及三层终端遮罩；采用 AE2 的福鲁伊克斯深、中、亮配色。Forge 1.20.1 渲染器实现在线全亮与 100 tick 缓慢脉动，离线显示降低到 40% 色亮，真实 AE 节点状态仍控制方块的 `LIT` 属性。

The nine textures used by the baked block and item models are explicitly registered as single sprites in `assets/minecraft/atlases/blocks.json`. Their original resource paths and PNG bytes remain unchanged. This registration is required because the files are stored under `textures/ae2_1_21/`, outside the automatically scanned `textures/block/` and `textures/item/` directories. Online overlay UVs match the baked north face before applying the same horizontal facing rotation.

静态方块与物品模型使用的九张贴图在 `assets/minecraft/atlases/blocks.json` 中逐项注册为独立精灵，原始资源路径和 PNG 内容不变。资源位于 `textures/ae2_1_21/`，不属于自动扫描的 `textures/block/` 或 `textures/item/` 目录，因此需要显式注册。在线叠加层的 UV 与静态模型北面一致，再按方块水平朝向一起旋转。

This is an adaptation by **ME Storage Controller contributors**, not an official AE2 block or screen. Models and lighting are adapted for Forge 1.20.1 rather than assuming that upstream NeoForge 1.21.1 model extensions behave identically. There is no claim of official endorsement. The previous cyan strip and generated high-resolution casing are no longer used.

这是 **ME Storage Controller contributors** 制作的自定义改编，不是 AE2 官方方块或官方界面。模型和发光效果针对 Forge 1.20.1 适配，不能直接假设 NeoForge 1.21.1 的模型扩展字段可原样生效；本项目不宣称获得官方背书。旧版青色灯条及生成的高分辨率机箱不再使用。

## Historical assets / 历史资源

Version 0.4.0 used a project-generated 512×512 controller atlas. That atlas has been removed from the 0.4.1 resources. Historical 0.4.0 screenshots and release notes remain as records of that version and do not describe the current resource provenance.

0.4.0 曾使用本项目生成的 512×512 控制器图集，现已从 0.4.1 资源中删除。旧版截图与发布记录仍保留，用于记录当时版本，不能据此理解当前资源来源或许可。
