# 0.4.1 AE 终端与文件树 / AE terminal and storage tree

本版实际收录官方 AE2 1.21.1 固定提交中的 **13 张原始 PNG**，参考其终端布局与调色板，替换上一版生成纹理。游戏运行版本仍为 **Minecraft 1.20.1 Forge**。具体来源、逐文件哈希、改编范围与美术许可见 [ASSETS](ASSETS.md)；[0.4.0 历史记录](RELEASE_0.4.0.md) 保留原样。

This version includes **thirteen original PNGs** from a pinned official AE2 1.21.1 commit and references its terminal layout and palette, replacing the previous generated artwork. The runtime remains **Minecraft 1.20.1 Forge**. See [ASSETS](ASSETS.md) for provenance, hashes, adaptations and artwork licensing. The [0.4.0 historical record](RELEASE_0.4.0.md) is unchanged.

终端按确认稿采用约 **340×240 逻辑像素**布局：左侧文件树、精确数量和 5 列 × 2 行元件槽；右侧原生 AE 终端风格的 9 列 × 5 行内容网格及玩家背包。内容、元件与背包同屏显示。默认浅色，已有偏好不覆盖。Auto 缩放只在本界面临时限幅，不更改全局选项；关闭恢复游戏计算的倍率，重新打开再次适配。

The approximately **340×240 logical-pixel** terminal follows the approved design: a left storage tree, exact quantity details and five-column, two-row cell controls beside a native-style AE 9-by-5 content grid and player inventory. Contents, cells and inventory remain visible together. Light is the default, preserving saved preferences. Auto scaling is temporarily limited only while this screen is open; the option remains unchanged, closing restores vanilla's calculated scale, and reopening adapts again.

内容页从 6 条增加到 45 条，网络协议升为 **5**。客户端与服务器须一起升级到 0.4.1，不能与 0.4.0 混用。方块模型的 9 个贴图引用显式注册到 blocks atlas，在线叠加层修正镜像，保持与静态面板一致。

Content pages expand from six to 45 entries using protocol **5**. Upgrade clients and servers together to 0.4.1; 0.4.0 is incompatible. Nine block-model textures are explicitly registered in the blocks atlas, and the online overlay's mirrored UV is corrected to align with the static panel.

## 验收状态 / Validation status

批准稿实现后的最终回归已通过：2026-09-30 19:00 的服务端结果及 19:10 的客户端最终结果分别记录在 `work/v041-approved-server-tests.log` 和 `work/v041-approved-client-final.log`。JUnit XML 报告确认 5 项测试均通过，无跳过或失败。测试使用 ExtendedAE 1.20-1.4.21-forge 与 Glodium 1.20-1.5-forge。

Final regression checks for the approved implementation passed on September 30, 2026: server checks at 19:00 and the final client run at 19:10, recorded in the log files above. The JUnit XML report confirms five passing tests with no skips or failures. Addon checks use ExtendedAE 1.20-1.4.21-forge and Glodium 1.20-1.5-forge.

| 检查 / Check | 状态 / Status |
|---|---|
| JUnit 与启用附属的 GameTests / Unit and addon-enabled GameTests | **5 / 5 JUnit、15 / 15 GameTests 通过 / Passed** |
| 固定 scale 2：目录、元件取放、搜索与主题 / Fixed scale 2: tree, transfers, search and themes | **通过 / Passed**；真实展开、折叠、滚动、逐级导航及中文“铜锭”搜索 / Real tree gestures, navigation and localized search |
| 1280×720 Auto：内容、元件与背包同屏、真实鼠标槽位操作 / Auto: simultaneous contents/cells/inventory and actual slot clicks | **通过 / Passed**；实际倍率 2，EAE 第 20 个元件映射正确 / Scale 2, correct EAE cell-twenty mapping |
| 1920×1080 Auto：布局、槽位及服务端数量守恒 / Auto: layout, slots and authoritative conservation | **通过 / Passed**；实际倍率 3，客户端／服务端一致 / Scale 3, client/server agreement |
| Auto 关闭恢复倍率、重开限幅，选项始终为 0 / Restore on close, reapply on open, option remains zero | **通过 / Passed**；1280 下关闭恢复 3、重开限幅 2 / At 1280, close restores 3 and reopen applies 2 |
| 界面边界、官方纹理与方块在线／离线截图 / Bounds, official textures and block online/offline captures | **通过 / Passed**；4 朝向 × 2 状态及物品模型共 315 个面，9 个贴图，无 missingno / 315 quads, nine sprites, no missing texture |
| 45 格分页、搜索排序、协议往返 / 45-entry pagination, search/sort and protocol round-trip | **通过 / Passed**；52 种以上内容跨页无重复或遗漏，真实前后翻页和名称／数量排序 / 52+ keys, complete unique coverage and real paging/sort controls |
| 连续快速左键与持有元件点击工具栏 / Rapid left clicks and toolbar clicks while carrying a cell | **通过 / Passed**；96 次真实左键点击，普通／Shift 取放，元件唯一性与内容数量守恒 / 96 real left clicks, normal/Shift transfers and cell/content conservation |

导航确认现按“原版真实槽位更新 → 自定义快照确认”的顺序发送，客户端收到最新确认前保持操作锁定。最终烟测在快照接收回调内检查槽位，验证反复切换设备／元件页时，解除锁定之前已收到正确物理槽位；快速 A→B→A 导航也通过。普通元件中的 12,345 个铁锭、EAE 第 20 个元件中的 98,765 个铜锭均在取放后保持精确数量。

Navigation now sends vanilla physical-slot updates before the custom snapshot acknowledgement. The client stays locked until the latest acknowledgement. Final tests inspect slots inside the snapshot receiver before unlocking, covering repeated device/page changes and rapid A→B→A navigation. Transfers preserve exactly 12,345 iron ingots in the ordinary cell and 98,765 copper ingots in EAE cell twenty.

## 最终截图 / Final captures

- [1920×1080 Auto：全网 / Network overview](screenshots/v0.4.1/smoke-auto1920-network.png)
- [1280×720 Auto：EAE 第 20 个元件 / EAE cell twenty](screenshots/v0.4.1/smoke-auto1280-cell20.png)
- [深色主题：驱动器与同屏槽位 / Dark drive and simultaneous slots](screenshots/v0.4.1/smoke-dark-drive.png)
- [在线方块 / Online block](screenshots/v0.4.1/smoke-block-online.png)
- [离线方块 / Offline block](screenshots/v0.4.1/smoke-block-offline.png)

客户端自动验收仅使用 `work/run-v041/saves/SmokeTest` 一次性存档，输出到 `work/client-smoke-v041`，不修改正在使用的主运行目录存档。测试通过真实 `mouseClicked`／`mouseReleased` 操作槽位，核对客户端和服务端元件唯一性及精确内容；截图仅作为外观证据。

Client automation uses only the disposable `work/run-v041/saves/SmokeTest`, writing to `work/client-smoke-v041`; it does not alter the player's main run-directory worlds. Tests invoke real screen mouse press/release paths and check client/server cell uniqueness and exact contents. Screenshots provide visual evidence only.

## 复现 / Reproduction

使用 JDK 17，并准备官方 `ExtendedAE-1.20-1.4.21-forge.jar` 与 `Glodium-1.20-1.5-forge.jar`。从项目根目录执行：

Use JDK 17 and the official addon JAR filenames above. From the project root:

```powershell
.\gradlew.bat test runGameTestServer '-PrunDir=../../work/run-v041' -PeaeTest '-PcompatModsDir=C:/path/to/compat-jars'
.\gradlew.bat runClient '-PrunDir=../../work/run-v041' -PsmokeTest -PeaeTest '-PcompatModsDir=C:/path/to/compat-jars'
```

第二条命令要求隔离目录已有 `saves/SmokeTest`，游戏语言为简体中文；会重建测试库存并自动退出。交互演示的 `-Pdemo` 用法见 [使用说明](USER_GUIDE.md)。多人并发、保护模组和大型整合包仍需独立验证。

The second command requires an existing disposable `saves/SmokeTest` world and Simplified Chinese selected; it resets test inventories and exits automatically. See the [user guide](USER_GUIDE.md) for interactive `-Pdemo` startup. Concurrent multiplayer, claim protection and large modpacks still require separate validation.
