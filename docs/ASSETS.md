# 视觉资源与许可

[返回文档目录](README.md)

本模组的界面与方块采用AE2的视觉资源，在Forge 1.20.1中重新组合与适配。原创代码和第三方美术分开授权。

## 来源

收录的13张PNG来自Applied Energistics 2的Minecraft 1.21.1源码，固定于提交 [`db17504a`](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/db17504a86128fdf3dae31f5fb7a112a646e0b93)。文件包括通用机器表面、显示器面板、终端遮罩和GUI纹理。

文件保存在 [`textures/ae2_1_21/`](../src/main/resources/assets/me_storage_controller/textures/ae2_1_21/)，PNG原始字节未改动。完整清单及SHA-256记录在[随包署名文件](../src/main/resources/AE2-ASSET-NOTICE.txt)中；该文件与许可全文一同包含在JAR中。

界面布局也参考同一提交的调色板和终端布局。**AETexturesBackport1.20-1.1**资源包用于后续配色对照，其作者署名为 **Gelicvecz**；项目未从该包额外复制或分发PNG。

## 作者与许可

AE2上游的[资源许可声明](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db17504a86128fdf3dae31f5fb7a112a646e0b93/README.md#license)列明：

- © 2020 **Ridanisaurus Rid** — [作者主页](https://github.com/Ridanisaurus/)
- © 2013–2020 **AlgorithmX2 et al** — Applied Energistics 2 contributors
- 纹理与模型采用 **Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported（CC BY-NC-SA 3.0）**。

AE2美术、衍生JSON模型和本模组对这些资源的视觉改编保留该许可，涉及署名、非商业使用与相同方式共享。分发时须保留[署名文件](../src/main/resources/AE2-ASSET-NOTICE.txt)和[许可全文](../src/main/resources/licenses/CC-BY-NC-SA-3.0.txt)。这些资源不属于MIT授权范围。

原创Java代码、脚本和文档采用[MIT](../LICENSE)。项目独立实现界面绘制与交互，不包含AE2的LGPL Java实现。

## 适配方式

**ME Storage Controller contributors**在绘制代码与JSON模型中完成裁切、UV选择、配色、布局和重组，组合官方显示器表面与终端遮罩制作控制器外观；渲染与发光效果适配Forge 1.20.1。

用于方块和物品模型的9张纹理在[`blocks.json`](../src/main/resources/assets/minecraft/atlases/blocks.json)中显式注册，因为它们不在自动扫描的 `textures/block/` 或 `textures/item/` 目录下。

本项目是独立附属，界面和方块为本项目的适配作品，不代表AE2官方背书。
