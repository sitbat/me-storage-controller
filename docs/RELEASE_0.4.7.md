# 0.4.7 材质细节与定位高亮 / Texture details and locator highlighting

参照用户提供的 **AETexturesBackport1.20-1.1**（Gelicvecz）细化现有 UI，保留文件树、内容网格、背包、缩放及交互布局。终端、搜索框和背景 PNG 与既有官方 AE2 1.21.1 资源逐字节相同，因此调整集中在自绘边框、搜索状态、树选中态、按钮和滑块的一致性；不将整个资源包作为模组依赖或打入发行 JAR。

The existing UI is refined using **AETexturesBackport1.20-1.1** by Gelicvecz as its reference. Tree, grid, inventory, scale and interaction layout remain unchanged. Terminal, text-field and background PNGs are byte-identical to the existing official AE2 1.21.1 assets; changes focus on consistent borders and control states. The complete resource pack is neither bundled nor required.

本模组简中文案统一为“ME存储控制器”“ME存储”等，去除 ME 与中文之间的空格，不更改英文词间空格或其他模组的全局翻译。

Addon Chinese labels now use forms such as “ME存储控制器” and “ME存储”, preserving English word spaces and other mods' global translations.

定位高亮改用正确的世界渲染阶段。Forge 1.20.1 的 `AFTER_LEVEL` 事件传入的矩阵已包含透视投影，不适合直接作为世界线框的模型视图矩阵；新的绘制阶段使用真实世界视图，以相机相对坐标绘制目标方块。存储总线条目继续定位其连接的容器。

The locator uses the correct world-render stage. Forge 1.20.1 passes a projection-bearing matrix to `AFTER_LEVEL`, which cannot serve directly as the world outline's model-view matrix. Drawing now uses the real world view and camera-relative coordinates. Storage-bus entries still locate their attached containers.

协议仍为 **6**，运行版本仍为 **Minecraft 1.20.1 Forge**。保留 0.4.6 的滚轮、原生 GUI 比例和窗口状态修复。

Protocol remains **6** on **Minecraft 1.20.1 Forge**, retaining 0.4.6 scrolling, native GUI scale and window-state fixes.

## 验证 / Validation

2026-09-30，7 项单元测试全部通过，包括新增的世界边界附近坐标精度和相机移动一致性检查。客户端完整烟测及发行构建成功，日志为 `work/v047-client-build.log`，明确输出 `ME_STORAGE_SMOKE_RESULT PASS` 和 `BUILD SUCCESSFUL`。隔离验收目录为 `work/run-v047`，运行基线为 Java 17、Forge 47.4.0、AE2 15.4.10、GuideME 20.1.7，附加 ExtendedAE、Mekanism 和 Applied Mekanistics。

All seven unit tests, the full client smoke suite and the release build passed on September 30, 2026. New unit checks cover precision near the world border and camera displacement. The log, success markers and isolated environment are listed above.

- 日志确认终端、图标及文本框来自 `file/AETexturesBackport1.20-1.1.zip`；按钮背景保留现代图集的正确 UV。没有启用用户材质包时使用既有内置美术；此回退路径经过静态来源与 UV 核对，本次游戏实测启用了用户材质包。 / Resource-source assertions confirm the supplied pack supplies terminal, icons and text-field textures, while toolbar backgrounds keep the modern atlas. The no-pack fallback was statically reviewed; this gameplay run had the supplied pack enabled.
- 已目检浅／深主题及 960×720、1280×720、1920×1080 布局。完整滚轮、窗口重建、搜索、96 次快速元件点击、24 次快速内容点击、容器操作及 EAE 第 20 槽回归通过。 / Light/dark and three viewport sizes were visually checked. Scrolling, rebuild state, search, 96 rapid cell clicks, 24 rapid content clicks, container actions and ExtendedAE slot twenty pass.
- 正面与移动后的斜视角均真实绘制并截图，已目检线框准确贴合同一 `(14, 100, 8)` 方块。相机分别为 `(14.5, 100.62, 6.2)` 与 `(16.2, 100.62, 6.5)`。存储总线快照指向实际木桶位置的断言通过。 / Real front and moved oblique renders were captured and visually confirmed to outline the same target block. Storage-bus snapshots also pass the attached-barrel position assertion.
- 简中控制器物品名及界面标题分别为“ME存储控制器”和“ME存储”的断言通过。 / Chinese item-name and terminal-title assertions pass.

此次未对光影模组、第三人称及其他整合包组合进行实机验证；后续手动验收见 [验收计划](MANUAL_TEST_PLAN.md)。

Shader mods, third-person rendering and other modpack combinations were not exercised in this run; see the [manual plan](MANUAL_TEST_PLAN.md).

- [浅色界面 / Light UI](screenshots/v0.4.7/smoke-auto1280-network.png)
- [深色界面 / Dark UI](screenshots/v0.4.7/smoke-dark-network.png)
- [窄屏聚焦搜索 / Narrow focused search](screenshots/v0.4.7/smoke-auto960-search-rebuild.png)
- [正面定位 / Front locator](screenshots/v0.4.7/smoke-highlight-front.png)
- [移动后的定位 / Locator after moving](screenshots/v0.4.7/smoke-highlight-oblique.png)

## 复现 / Reproduction

```powershell
.\gradlew.bat test runClient build '-PrunDir=../../work/run-v047' -PsmokeTest -PeaeTest -PmekTest
```

客户端烟测需要已存在、可重置的 `saves/SmokeTest` 存档；仅使用可丢弃的测试副本。

Client smoke validation requires an existing, disposable `saves/SmokeTest` world.
