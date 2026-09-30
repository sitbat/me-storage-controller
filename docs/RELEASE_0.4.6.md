# 0.4.6 滚轮响应与原生界面比例 / Scrolling and native GUI scale

右侧内容滚轮累加本地目标位置，不再反复根据尚未返回的页码请求同一页。滚轮幅度及小数增量保留，作用范围包含内容网格、右侧滑轨和上下箭头；背包和搜索框不会误触内容滚动。纯分页请求下一 tick 应答，直接切分最新已扫描的排序内容，不再等待 5 tick 或因翻页重扫整个网络。滚动不延后存取变更刷新和定时库存刷新。

The content wheel accumulates a local target instead of repeatedly requesting the same page from a delayed snapshot. Wheel magnitude and fractional increments are retained across the grid, scrollbar and arrow controls, excluding inventory and search fields. Pure page requests acknowledge on the next tick using the latest sorted content scan. Paging does not defer mutation or periodic refreshes.

打开、关闭和重建控制器界面均不再修改 `Window` 的 GUI 倍率，也不改游戏的界面比例选项。界面使用 Minecraft 提供的逻辑宽高：空间足够时显示文件树与内容双栏，高度不足时减少内容行数，窄屏可切换目录与内容。窗口变化保留搜索、选择、折叠等浏览状态。

Opening, closing or rebuilding the controller no longer changes the window's GUI scale or the game option. It lays out within Minecraft's logical viewport, showing both columns when space allows, reducing content rows for shorter screens, and allowing tree/content switching in narrow viewports. Resizing preserves browsing state such as search, selection and folding.

协议仍为 **6**，容量单位和容器存取保持 0.4.5 行为。

Protocol remains **6**. Capacity units and container transfers retain 0.4.5 behavior.

## 验证 / Validation

2026-09-30，5 项单元测试和 25 项服务端 GameTests 全部通过，日志为 `work/v046-server-tests.log`。新增分页测试覆盖最新目标应答、立即反向、旧请求拒绝、搜索和范围切换隔离，以及持续翻页期间的存取刷新与定时刷新。

All five unit tests and all 25 server GameTests passed on September 30, 2026. New paging coverage includes latest-target acknowledgement, immediate reversal, rejection of old requests, search/scope isolation and mutation/periodic refreshes during continuous paging. The server log is listed above.

最终客户端烟测与发行构建全部通过，日志为 `work/v046-client-build-final.log`，明确输出 `ME_STORAGE_SMOKE_RESULT PASS`、`ME_STORAGE_SMOKE_NARROW PASS` 和 `BUILD SUCCESSFUL`。实际客户端检查包括：

The final client smoke run and release build passed. The log and explicit success markers are listed above. Actual client coverage includes:

- 在 260 多种内容上连续滚动、快速反向、大幅度／小数滚轮、悬停滑轨滚动、边界反向与旧响应防回跳；固定 2 倍、原生 Auto 3 倍均通过。 / Rapid/reversed, large and fractional wheel input, scrollbar hover, boundary reversal and prevention of old acknowledgements rolling back the target; tested with 260+ keys at fixed scale 2 and native Auto scale 3.
- 1280×720 Auto 使用原生 3 倍（逻辑 427×240，4 行），1920×1080 Auto 使用原生 4 倍（480×270，5 行）；打开、关闭、重新打开都没有修改选项 0 或实际倍率。 / Native Auto is 3 at 1280×720 (427×240 logical, four rows) and 4 at 1920×1080 (480×270, five rows); opening, closing and reopening preserve both option 0 and the actual scale.
- 960×720 Auto 使用原生 3 倍（320×240），目录和内容切换、滑块拖至两端、EAE 第 20 个元件真实取出及放回均通过。 / At 960×720, native Auto 3 (320×240) passes pane switching, dragging to both scrollbar limits and real pickup/reinsertion of ExtendedAE cell twenty.
- 同一个界面重建后，搜索词、光标、焦点和所选元件仍正确；重建恢复光标不会误触发新的搜索。 / Rebuilding the same screen preserves search text, caret, focus and the selected cell; caret restoration does not trigger a new search.
- 原有左右键／Shift 存取、24 次快速内容点击、96 次快速元件点击、流体桶存取、范围隔离与深浅主题通过。 / Existing left/right/Shift transfers, 24 rapid content clicks, 96 rapid cell clicks, bucket transfers, scope isolation and both themes pass.

测试使用隔离存档 `work/run-v046`；基线为 Java 17、Forge 47.4.0、AE2 15.4.10、GuideME 20.1.7，并加载 ExtendedAE 1.4.21、Mekanism 10.4.16.80 与 Applied Mekanistics 1.4.3。未修改正在游玩的存档。多人并发、领地保护和其他整合包组合仍需独立验收。

Tests use the isolated `work/run-v046` worlds with the versions above. The active gameplay world was not modified. Concurrent multiplayer, claim protection and other modpack combinations require separate validation.

- [1280×720 原生 Auto / Native Auto](screenshots/v0.4.6/smoke-auto1280-network.png)
- [1920×1080 原生 Auto / Native Auto](screenshots/v0.4.6/smoke-auto1920-network.png)
- [960×720 窄屏内容 / Narrow contents](screenshots/v0.4.6/smoke-auto960-contents.png)
- [960×720 目录元件 / Narrow tree and cell](screenshots/v0.4.6/smoke-auto960-tree-cell.png)
- [重建后的查询与焦点 / Search and focus after rebuilding](screenshots/v0.4.6/smoke-auto960-search-rebuild.png)

## 复现 / Reproduction

```powershell
.\gradlew.bat test runGameTestServer '-PrunDir=../../work/run-v046' -PeaeTest -PmekTest
.\gradlew.bat runClient build '-PrunDir=../../work/run-v046' -PsmokeTest -PeaeTest -PmekTest
```
