# 0.4.0 文件树管理界面 / Storage tree interface

本轮围绕真正的文件树式存储管理重构界面：设备可展开为存储元件，多个设备可以同时展开，当前选择与展开状态分开处理。整体采用现代简洁布局，并保留 AE2 终端的物品槽位和存储信息习惯；运行版本仍为 **Minecraft 1.20.1 Forge**。

This iteration rebuilds navigation as a storage tree: devices expand into cells, multiple devices can remain expanded, and selection is separate from expansion state. A modern, minimal layout retains familiar AE2 item slots and storage information. The runtime remains **Minecraft 1.20.1 Forge**.

历史记录：[0.3.0](RELEASE_0.3.0.md)、[0.2.0](RELEASE_0.2.0.md)。历史版本的通过记录不自动适用于此次重构。

Historical results for [0.3.0](RELEASE_0.3.0.md) and [0.2.0](RELEASE_0.2.0.md) do not automatically validate this rewrite.

## 本轮范围 / Scope

- 文件树：展开／折叠多个设备，定位单个元件，滚动查看大型驱动器，保留选择状态。
- 层级导航：全网、设备与元件的路径信息及返回操作。
- 搜索：目录匹配维度、设备和元件名称；内容使用独立的只读清单搜索框。
- 外观：清晰的文字标签、深浅主题、紧凑尺寸适配，以及展开、选中和主题切换的视觉过渡。
- 方块外观：独立制作的凹入式面板模型与原创图集，底部灯条反映真实网络状态；来源见 [视觉资源说明](ASSETS.md)。
- 存储安全：继续只取放整个元件；保留快速连续左键、持物点击控件和服务端权限校验。

The scope includes independent device expansion and cell selection, scrolling through large drives, hierarchical navigation, device/content search, labeled controls, both themes, compact layouts and visual transitions. Whole-cell transfers retain the rapid-click, held-control and server-access protections.

方块模型与纹理也包含在本轮外观更新中。网络协议更新为 **4**，客户端和服务器需同时升级到 0.4.0。

The controller block model and textures are also part of this visual update. Network protocol **4 requires upgrading both client and server to 0.4.0**.

## 验收结果 / Validation results

2026-09-30（Asia/Shanghai），安装实际 ExtendedAE 1.20-1.4.21-forge 与 Glodium 1.20-1.5-forge 的开发环境已完成服务端运行。`work/v040-eae-tests.log` 于 17:27 记录 **BUILD SUCCESSFUL、14 项真实 GameTests 全部通过且没有附属跳过**；5 项 JUnit 通过。无附属基线于 17:32 成功完成：11 项实际 GameTests 通过，另外 3 项明确跳过可选 EAE 检查，5 项 JUnit 通过。最终客户端于 17:35 通过，包含最终界面调整、紧凑背包标签和独立方块展台，共保存 22 张实际截图。

On 2026-09-30 (Asia/Shanghai), the environment with real ExtendedAE 1.20-1.4.21-forge and Glodium 1.20-1.5-forge completed successfully: **all fourteen real GameTests passed without addon skips, and five JUnit tests passed**. The addon-absent baseline succeeded at 17:32 with eleven real GameTests, three explicit optional EAE skips, and five passing JUnit tests. The final client run passed at 17:35, including final UI polish, compact inventory tabs and the separate block gallery, producing twenty-two real captures.

| 项目 / Check | 方法 / Method | 状态 / Status |
|---|---|---|
| 多设备文件树 / Multiple device branches | 使用实际鼠标展开和折叠两个设备，确认互不替代展开状态 / Real mouse expansion and collapse of independent branches | 通过 / Passed |
| 第 20 个元件 / Twentieth cell | 展开 ExtendedAE 驱动器，滚动到第 20 槽并选择，核对精确数量 / Scroll to and select cell twenty; verify quantities | 通过 / Passed |
| 滚动与选择 / Scrolling and selection | 滚离当前行再返回，核对选择、详情与真实元件槽位 / Scroll away and return without changing selected details or slots | 通过 / Passed |
| 路径与搜索 / Path and search | 点击返回／全网路径，执行中文搜索并清除 / Real breadcrumb/back interactions and localized search | 通过 / Passed |
| 快速左键 / Rapid left clicks | 两种驱动器合计 24 次真实鼠标左键，确认元件唯一、数量不变 / Twenty-four real left clicks with authoritative integrity checks | 通过 / Passed |
| 持物控件 / Controls while carrying | 持元件操作主题等控件，界面外释放后不掉落，再放回 / Activate controls while carrying, release outside and reinsert without drops | 通过 / Passed |
| 实际界面 / Actual interface | 全网、设备、元件、外部容器及紧凑尺寸的深浅主题截图 / Light/dark and compact captures of each main view | 通过 / Passed |
| 方块外观 / Block appearance | 退出 GUI 后在实际世界查看控制器正面与侧面 / Real-world front/side capture after closing the GUI | 通过 / Passed |
| 安装附属的服务端回归 / Addon-enabled server regression | 5 项 JUnit、14 项真实 GameTests，包括存储目录和方块状态 / Five JUnit tests and fourteen real GameTests, including directory and block-state checks | 通过 / Passed |
| 无附属基线 / Addon-absent baseline | 5 项 JUnit、11 项实际 GameTests 通过，3 项可选 EAE 检查明确跳过 / Five JUnit tests and eleven real GameTests passed; three optional EAE checks explicitly skipped | 通过 / Passed |

GUI 测试通过界面提供的控件位置取得真实鼠标坐标，避免依赖上一版侧栏的固定布局。元件数量和内容仍由实际服务端库存核对，不以截图代替数据验证。两种驱动器各完成 6 对快速左键（合计 24 次点击），普通元件保留 12,345 个铁锭，第 20 元件保留 98,765 个铜锭；持物操作控件并在界面外释放后，服务端确认没有新增掉落物。

GUI tests obtain real mouse coordinates from the current control layout rather than assuming previous sidebar geometry. Cell counts and contents are checked against actual server inventories. Screenshots are not substitutes for data validation. Six rapid pairs on each drive (twenty-four clicks in total) preserved 12,345 iron in the ordinary cell and 98,765 copper in cell twenty. Server checks found no new dropped cells after held-control clicks and outside releases.

## 实际截图 / Actual captures

截图来自真实客户端，包含 16 张主界面视图、文件树初始／展开／过渡、中文搜索以及方块在线／离线外观，共 **22 张**。紧凑背包深色页已复核没有重叠且第 20 元件映射正确；浅色第 20 元件的 98,765 铜锭、容量微条与层级路径清楚；方块在线图的正面、侧面和顶部均无缺失纹理。生产构建已成功。

The **twenty-two** real client captures include sixteen main views, initial/expanded/transition tree states, localized search, and online/offline block appearance. Review confirmed the compact dark inventory layout and cell-twenty mapping, readable 98,765 copper and tree details in the light cell view, and complete front/side/top textures in the block view. The production build succeeded.

- [客户端结果 / Client result](screenshots/v0.4.0/client-smoke-result.txt) · [交互检查 / Interaction checks](screenshots/v0.4.0/client-smoke-checks.txt)
- [浅色第 20 元件 / Light cell twenty](screenshots/v0.4.0/smoke-light-expanded-cell20.png) · [深色第 20 元件 / Dark cell twenty](screenshots/v0.4.0/smoke-dark-expanded-cell20.png)
- [浅色紧凑背包 / Compact light inventory](screenshots/v0.4.0/smoke-compact-inventory-light.png) · [深色紧凑背包 / Compact dark inventory](screenshots/v0.4.0/smoke-compact-inventory-dark.png)
- [浅色全网 / Light network](screenshots/v0.4.0/smoke-light-network.png) · [深色全网 / Dark network](screenshots/v0.4.0/smoke-dark-network.png) · [文件树展开 / Expanded tree](screenshots/v0.4.0/smoke-tree-expanded.png) · [展开过渡 / Expansion transition](screenshots/v0.4.0/smoke-tree-transition.png)
- [浅色流体 / Light fluid](screenshots/v0.4.0/smoke-light-fluid.png) · [深色外部容器 / Dark external container](screenshots/v0.4.0/smoke-dark-external.png) · [中文搜索 / Localized search](screenshots/v0.4.0/smoke-localized-search.png)
- [方块在线 / Block online](screenshots/v0.4.0/smoke-block-online.png) · [方块离线 / Block offline](screenshots/v0.4.0/smoke-block-offline.png)

## 测试隔离 / Test isolation

开发运行目录为 `work/run-v040`，截图输出为 `work/client-smoke-v040`。自动交互只针对一次性 `SmokeTest` 存档，不使用玩家正在体验的 `ME-Controller-Demo` 存档。测试参数中的路径值在 PowerShell 中应给完整参数加引号。

Development runs use `work/run-v040` and captures use `work/client-smoke-v040`. Automated interactions target only the disposable `SmokeTest` world, never the player's active `ME-Controller-Demo`. Quote complete path-valued Gradle arguments in PowerShell.

使用 JDK 17。兼容测试目录应包含官方 [ExtendedAE 1.20-1.4.21-forge](https://github.com/GlodBlock/ExtendedAE/releases/tag/1.20-1.4.21-forge) 的 `ExtendedAE-1.20-1.4.21-forge.jar` 和 [Glodium 1.20-1.5-forge](https://modrinth.com/mod/glodium/version/eoUaDkZf) 的 `Glodium-1.20-1.5-forge.jar`。从项目根目录执行：

Use JDK 17 and the official addon files listed above in a compatibility dependency directory. Run from the project root:

```powershell
.\gradlew.bat test runGameTestServer '-PrunDir=../../work/run-v040' -PeaeTest '-PcompatModsDir=C:/path/to/compat-jars'
```

客户端验收需要在隔离运行目录内准备一次性 `saves/SmokeTest` 存档，并选择简体中文。以下命令会重建测试库存、执行自动鼠标操作并退出：

Client validation requires a disposable `saves/SmokeTest` world in the isolated run directory and Simplified Chinese selected. This command rebuilds test inventories, performs automatic mouse interactions, and exits:

```powershell
.\gradlew.bat runClient '-PrunDir=../../work/run-v040' -PsmokeTest -PeaeTest '-PcompatModsDir=C:/path/to/compat-jars'
```

无附属基线使用不含可选附属的单独运行目录：

For the addon-absent baseline, use a separate run directory without optional addons:

```powershell
.\gradlew.bat build runGameTestServer '-PrunDir=../../work/run-v040-baseline'
```

多人并发、领地保护、未列出的第三方存储类型和大型整合包性能仍需独立验证。

Concurrent multiplayer, claim protection, unlisted third-party storage types and large-modpack performance require separate validation.

## 桌面演示 / Desktop demonstration

17:42 已在用户桌面实际打开独立 `ME-Controller-Demo-0.4.0` 存档并确认新模型与新版界面显示正常。原演示存档里的预设控制器不存在或离线，因此保留原存档，使用已准备的独立副本。开发辅助启动在预设控制器不可用时会保留游戏供玩家操作，不再自动退出。

After the final client suite, the All action received a small follow-up: it explicitly reveals the root even when the root is already selected. The production build includes this change; the complete automated suite predates that one-line follow-up. The real desktop demo was opened at 17:42. A targeted native mouse check was stopped when active user input was detected, leaving the running instance with the player. The existing demo save was preserved; a prepared standalone copy is used for this session.
