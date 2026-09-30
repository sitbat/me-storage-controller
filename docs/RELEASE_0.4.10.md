# 0.4.10 侧栏按钮修正 / Sidebar button correction

本次仅修改用户标出的左侧七按钮栏。用户已确认的 **0.4.9 存储树外框保持原样**，右侧终端及其他主面板不变；[0.4.9 发布与验收记录](RELEASE_0.4.9.md) 单独保留。

This release changes only the seven-button left sidebar identified by the user. The **approved 0.4.9 storage-tree border remains intact**, together with the right terminal and other panels. The [0.4.9 release and validation record](RELEASE_0.4.9.md) is preserved separately.

## 改动 / Changes

- 去掉手绘连续黑色底板、每个按钮的黑色矩形投影和蓝色悬停填充。
- 按钮采用用户材质包中的 AE2 v15 原生背景：`states.png` 的 `(240,240)` 处，尺寸 `16×16`。
- 七按钮保持 `20` 像素步距，位置为整体面板的 `x+3, y+9+20n`（`n=0…6`）。相对存储树面板左上角，首个按钮位于 `x−15, y+3`。
- 焦点使用 `1` 像素白色外框；按钮功能、游戏内指南、滚轮、搜索、存取与原生 GUI 比例行为保持不变。

The hand-drawn continuous black backing, per-button rectangular shadows and blue hover fill are removed. Buttons use the resource pack's native AE2 v15 `16×16` background at `(240,240)` in `states.png`. They retain a 20-pixel pitch at panel-relative `x+3, y+9+20n`, placing the first button 15 pixels left and 3 pixels below the storage-tree panel's top-left corner. Focus uses a one-pixel white outline. Button functions, the guide, scrolling, search, transfers and native GUI scale are unchanged.

运行版本仍为 **Minecraft 1.20.1 Forge**，网络协议仍为 **6**。

The runtime remains **Minecraft 1.20.1 Forge**, with network protocol **6**.

## 验证状态 / Validation status

2026-09-30，在隔离目录 `work/run-v0410` 中完成验收，启用用户提供的 `AETexturesBackport1.20-1.1` 资源包及 ExtendedAE、Mekanism、Applied Mekanistics 等既有验收依赖。`work/v0410-client-build.log` 输出 `ME_STORAGE_SMOKE_RESULT PASS` 与 `BUILD SUCCESSFUL`，七项单元测试全部通过。

Validation completed on September 30, 2026 in the isolated directory with the supplied pack and existing addon dependencies. The log contains explicit smoke and build success markers; all seven unit tests pass.

| 项目 / Check | 状态 / Status |
|---|---|
| 完整客户端交互回归 / Full client interaction regression | 通过 / Passed |
| 七按钮浅深主题、悬停与贴边位置实拍 / Button appearance, hover and placement captures | 通过 / Passed |
| 与0.4.9对照存储树外框及其余面板 / Compare preserved panels with 0.4.9 | 通过 / Passed |
| 发行构建 / Release build | 通过 / Passed |

对普通、悬停、1280自动比例3和1920自动比例4四张实机截图作像素核对：“?”、A–Z、ME网络、齿轮四个共用图标的完整16×16按钮，与用户截图中的对应按钮逐像素相等。七个按钮未被图标覆盖的底板像素，也均与材质包原图相等。透明间隙另经目检确认；不同位置的游戏天空本身存在渐变，不作为相等像素的参照。

Pixel checks cover normal, hovered and two Auto-scale captures. Four shared icons (Guide, A–Z, ME Network and gear), including their full button backgrounds, exactly match the supplied screenshot. Uncovered background pixels on all seven controls match the resource texture. Transparent gaps were visually reviewed; spatially varying world pixels are not treated as identical references.

原有浅／深主题、1280与1920自动比例的面板边框像素检查再次通过。指南返回、24次快速内容点击、96次快速元件点击、滚轮、搜索与EAE第20槽通过。焦点白框及未启用材质包的三色回退经过源码核对，本次实机启用了用户材质包。

Existing panel-border checks pass in light/dark and both Auto scales. Guide return, rapid content/cell clicks, scrolling, search and EAE slot twenty pass. The focus outline and no-pack color fallback were reviewed in source; gameplay captures use the supplied pack.

- [浅色自动比例 / Light Auto scale](screenshots/v0.4.10/smoke-auto1280-network.png)
- [1920自动比例 / 1920 Auto scale](screenshots/v0.4.10/smoke-auto1920-network.png)
- [深色 / Dark](screenshots/v0.4.10/smoke-dark-network.png)
- [悬停指南按钮 / Guide-button hover](screenshots/v0.4.10/smoke-sidebar-hover.png)

```powershell
.\gradlew.bat runClient build '-PrunDir=../../work/run-v0410' -PsmokeTest -PeaeTest -PmekTest
```

烟测只使用可丢弃的 `saves/SmokeTest` 副本；本次未修改用户的既有演示存档。

The smoke run uses only a disposable `saves/SmokeTest` copy; the user's existing demo world is not modified by this run.
