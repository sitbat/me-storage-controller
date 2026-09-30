# 0.4.9 原生面板边框修正 / Native panel border correction

0.4.8 左侧面板采用额外黑色投影、右侧凹边和目录内框，与右侧 AE 终端的原生边框不一致。本版去掉这些自绘效果，直接使用 `background.png` 原始面板纹理，通过非对称九宫格适配原有存储树尺寸。左、右、上边保留 2 像素，底边保留 4 像素；纹理边线不缩放。

Version 0.4.8 introduced a black drop shadow, recessed right edge and inset tree frame that did not match the adjacent AE terminal. This release removes those effects and uses the original `background.png` panel texture with asymmetric nine-slicing: two-pixel top/side borders and a four-pixel bottom border, all preserved at native thickness.

材质包的背景图与终端图共用同一套像素颜色：底色 `#CBCCD4`，边线 `#413F54`，亮线 `#F2F2F2`。底部由内向外依次为一行亮线、两行 `#878FA5` 灰色阴影、一行暗线。左侧使用与右侧相同的主题着色。启用用户材质包时取该包的背景图，否则取模组内置 AE2 1.21 美术。树的非选中行恢复为平面底色。

The panel and terminal textures share the same background, outline and highlight colors. Their bottom edge is one highlight row, two grey shadow rows and one outline row. Both panels use the same theme tint. File resource packs can override the panel texture; the existing bundled AE2 artwork remains the fallback. Unselected tree rows use the flat panel background.

侧栏排版、指南、滚动、搜索、选中范围、GUI 比例与服务端逻辑未改动。仍为 Minecraft 1.20.1 Forge，网络协议 6。

Sidebar layout, guide behavior, scrolling, search, selection, GUI scale and server logic are unchanged. The runtime remains Minecraft 1.20.1 Forge with protocol 6.

## 验证 / Validation

2026-09-30，完整客户端回归与发行构建通过。`work/v049-client-build.log` 输出 `ME_STORAGE_SMOKE_RESULT PASS` 和 `BUILD SUCCESSFUL`。测试仅使用隔离目录 `work/run-v049`，加载用户提供的 AETexturesBackport1.20-1.1、AE2 15.4.10、GuideME 20.1.7、ExtendedAE、Mekanism 和 Applied Mekanistics。

The complete client smoke suite and release build passed on September 30, 2026, with explicit success markers in the log. The run used an isolated test world and the supplied texture pack with the addons listed above.

对以下四张真实游戏截图逐像素比较了左右面板的顶部两行、底部四行、右侧两列和空白底色：固定比例 2 的浅／深主题、1280×720 自动比例 3、1920×1080 自动比例 4。各组采样 RGB 完全相等；浅色底部依次为 `(242,242,242)`、`(135,143,165)`、`(135,143,165)`、`(65,63,84)`。另经独立目检确认底边对齐，无外投黑影、反向凹边或目录内框。

Pixel comparisons of four actual captures cover the top two rows, bottom four rows, right two columns and flat background: light/dark at fixed scale two, 1280×720 Auto at scale three and 1920×1080 Auto at scale four. Each sample matches the adjacent terminal exactly. Independent visual review confirms aligned bottom edges and removal of the added drop shadow, reverse bevel and inset frame.

指南打开／返回、快速物品及元件存取、滚轮、搜索、窗口重建、原生界面比例与 EAE 第 20 槽回归通过。现有七项单元测试全部通过。服务端逻辑未改动，本次未重跑服务端 GameTest。

Guide navigation, rapid content/cell interactions, scrolling, search, resize state, native GUI scale and EAE slot twenty all pass. All seven existing unit tests pass. Server logic is unchanged; server GameTests were not rerun.

- [浅色 / Light](screenshots/v0.4.9/smoke-light-network.png)
- [深色 / Dark](screenshots/v0.4.9/smoke-dark-network.png)
- [1280 自动比例 / 1280 Auto](screenshots/v0.4.9/smoke-auto1280-network.png)
- [1920 自动比例 / 1920 Auto](screenshots/v0.4.9/smoke-auto1920-network.png)

```powershell
.\gradlew.bat runClient build '-PrunDir=../../work/run-v049' -PsmokeTest -PeaeTest -PmekTest
```

烟测会重置测试物品，只能使用可丢弃的 `saves/SmokeTest` 存档副本。

The smoke fixture resets test items; use only a disposable `saves/SmokeTest` copy.
