# 0.4.8 侧栏与指南 / Sidebar and guide

侧栏按用户提供的 AE 终端截图采样调整：按钮为 **16×16** 逻辑像素，顶部间距 **20** 像素（按钮间留 4 像素），贴合左边框；从上到下依次为指南、目录搜索、排序、ME网络、返回、主题、目录收放。图标保留原生 16×16，不压缩成 14×14。

The sidebar follows the supplied AE terminal screenshot: **16×16** logical-pixel buttons on a **20-pixel** pitch, aligned to the panel edge. The order is Guide, Directory Search, Sort, ME Network, Back, Theme and Toggle Tree. Glyphs retain their native 16×16 size.

存储树的尺寸、位置及操作不变，背景增加轻微凹槽层次，面板补充右下阴影和明暗边线。保留 0.4.7 的材质包支持、原生 GUI 比例、滚轮和定位修复。

The storage tree keeps its size, position and interaction, with an inset background and subtle bottom-right shadow and bevels. Resource-pack support, native GUI scale, scrolling and locator fixes remain.

新增简体中文与英文游戏内指南，作为 AE2 既有指南的页面载入。控制器物品进入 GuideME 原生物品索引，悬停后长按指南键（默认 **G**）可打开；侧栏顶部的 **?** 按钮直接打开同一页。没有新增或覆盖全局 G 键监听。关闭指南返回先前界面，重建时清除临时按钮／拖动状态，避免遗漏松键后影响下一次操作。

New Simplified Chinese and English pages extend AE2's existing guide. The controller item enters GuideME's native item index for its hold-to-open action, and the top **?** button opens the same page. No global G-key listener is added or replaced. Closing returns to the previous screen; transient button/drag states are cleared when rebuilding.

指南介绍联网、目录与范围、容量单位、物品左右键／Shift 存取、流体及化学品容器、整个元件管理、滚轮搜索主题和定位。协议仍为 **6**，运行版本仍为 **Minecraft 1.20.1 Forge**。

The guide covers networking, scope, capacity units, ordinary-item gestures, fluid/chemical containers, whole-cell management, browsing and locating devices. Protocol remains **6** on **Minecraft 1.20.1 Forge**.

## 验证 / Validation

2026-09-30，7 项单元测试、完整客户端烟测和发行构建全部通过。日志 `work/v048-client-build.log` 包含 `ME_STORAGE_SMOKE_RESULT PASS` 与 `BUILD SUCCESSFUL`。测试使用隔离目录 `work/run-v048`，启用 AETexturesBackport1.20-1.1，加载 AE2 15.4.10、GuideME 20.1.7、ExtendedAE、Mekanism 和 Applied Mekanistics。

All seven unit tests, the complete client smoke suite and the release build passed on September 30, 2026. The log contains the explicit smoke-pass and successful-build markers. The isolated run uses the supplied resource pack and the addons listed above.

- 各布局下七个按钮均为 16×16、顶部间距 20 像素，全部位于界面边界内。浅／深主题和 960×720、1280×720、1920×1080 实机截图已目检。 / Every layout passes seven-control size, pitch and bounds assertions; light/dark and all three viewport sizes were visually checked.
- GuideME 原生物品索引映射到本指南，简中页面成功解析和编译，控制器物品显示原生 `Hold [G] to open guide` 提示。未注入物理键盘 G，真实长按及改键仍列在手动验收中。 / Native item indexing, Chinese page compilation and the native hold-key tooltip pass. Physical G presses and rebinding remain manual checks.
- 使用真实界面点击拿起 32 颗钻石，点击“?”打开指南，滚动正文并按 Esc 返回，再将钻石放回原槽。原屏幕、菜单、选择范围、手持数量和背包总量均通过断言。 / Real screen events pick up 32 diamonds, open the guide, scroll it, return with Escape and replace the stack. Screen, menu, scope and exact inventory/cursor counts are preserved.
- 滚轮、滑轨、搜索、窗口重建、原生 Auto 比例、24 次快速内容点击、96 次快速元件点击、水桶存取、EAE 第 20 槽及定位绘制回归通过。本次未修改存储逻辑，未重跑服务端 GameTest。 / Paging, scrolling, search, resize state, native Auto scale, rapid content/cell clicks, bucket transfers, EAE slot twenty and locator rendering pass. Server GameTests were not rerun because storage logic is unchanged.

此次游戏实测启用了用户材质包；未启用材质包的侧栏回退背景经过静态颜色与纹理坐标核对。

Gameplay verification had the supplied pack enabled; the no-pack sidebar fallback received static palette and atlas-coordinate review.

- [浅色界面 / Light UI](screenshots/v0.4.8/smoke-auto1280-network.png)
- [1080p 自动比例 / 1080p Auto scale](screenshots/v0.4.8/smoke-auto1920-network.png)
- [深色界面 / Dark UI](screenshots/v0.4.8/smoke-dark-network.png)
- [窄窗口搜索 / Narrow-window search](screenshots/v0.4.8/smoke-auto960-search-rebuild.png)
- [游戏内指南 / In-game guide](screenshots/v0.4.8/smoke-guide-zh_cn.png)
- [指南操作表格 / Guide interaction table](screenshots/v0.4.8/smoke-guide-zh_cn-scrolled.png)

## 复现 / Reproduction

```powershell
.\gradlew.bat test runClient build '-PrunDir=../../work/run-v048' -PsmokeTest -PeaeTest -PmekTest
```

烟测会重置测试物品，只能使用可丢弃的 `saves/SmokeTest` 存档副本。

The smoke fixture resets test items; use only a disposable `saves/SmokeTest` copy.
