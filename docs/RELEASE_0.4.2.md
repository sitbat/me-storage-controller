# 0.4.2 物品存取与悬停显示修复 / Item transfers and tooltip fix

悬停存储元件时，旧界面同时绘制原生物品提示与自定义元件提示，造成文字重叠。本版统一为一次提示框绘制，保留 AE2 物品说明、元件位置和操作提示，并将目录“整个网络”改为“ME网络”。

Previously, hovering a storage cell drew both the native item tooltip and a separate custom tooltip at the same position. This release combines them into one tooltip and renames the tree root to “ME网络” / “ME Network”.

右侧物品网格开放存取：左键取一组或放入全部，右键取半组或放入一个，空手 Shift 左键可取到背包。空格可接收鼠标持物，持物时 Shift 左键仍存入全部。操作绑定当前网络、设备或元件；设备或元件拒绝操作时不会回退到全网。流体及非物品类型保持查看功能。完整操作说明见 [使用说明](USER_GUIDE.md)。

The content grid now supports item transfers: left-click a stack/all, right-click half/one, and Shift-left-click with an empty cursor into the backpack. Empty tiles accept carried items; Shift-left-click with a carried item inserts that stack. Actions are bound to the selected network, device or cell; a refused scoped transfer never falls back to the full network. Fluids and other non-item keys remain inspectable. See the [user guide](USER_GUIDE.md).

服务端使用 AE2 原生存储路由、存储包装层、玩家操作来源和能量处理，不直接改写存储元件 NBT 或容器库存。请求校验菜单、选择版本与选择身份，持有物及数量由服务端决定；元件替换后旧选择不能继续操作。存取后的真实背包和鼠标持物立即同步，目录和容量扫描仍节流。

The server uses AE2 storage routing, mounted wrappers, player action sources and energy handling rather than editing cell NBT or container inventories directly. Requests validate the menu, selection revision and scope identity; the server owns carried stacks and quantities. Replacing a selected cell invalidates its old transfer target. Physical slots and carried stacks synchronize immediately while metadata scans remain throttled.

网络协议为 **6**。客户端和服务器须一起升级到 **0.4.2**，不能与 0.4.1 或更早版本混用。Minecraft、Forge 和依赖基线不变：1.20.1、Forge 47.4.0、AE2 15.4.10、GuideME 20.1.7。附属测试使用 ExtendedAE 1.20-1.4.21-forge 与 Glodium 1.20-1.5-forge。

Protocol **6** requires upgrading clients and servers together to **0.4.2**. Earlier versions are incompatible. The runtime baseline and addon versions are unchanged.

## 验证 / Validation

2026-09-30 最终回归通过，测试仅使用 `work/run-v042` 中的隔离存档。服务端于 19:40 完成，客户端最终测试于 19:51 完成；日志分别为 `work/v042-server-tests.log` 和 `work/v042-client-final.log`。

Final regression passed on September 30, 2026: server tests at 19:40 and client tests at 19:51, using only isolated test worlds. Logs are listed above.

| 检查 / Check | 结果 / Result |
|---|---|
| 单元测试 / JUnit | 5 项全部通过，无失败或跳过 / All five passed, no failures or skips |
| 游戏测试 / GameTests | 19 项全部通过；新增 4 项覆盖存取守恒、范围、保护、总线过滤与耗能 / All 19 passed, including four new live-transfer tests |
| 网络、设备、元件内容存取 / Content transfers in all scopes | 实际左右键、Shift、空格存入、24 次无等待连续点击通过；铁锭始终合计 12,345 / Real mouse actions and 24 rapid clicks, preserving 12,345 iron |
| 元件范围隔离 / Selected-cell isolation | 32 钻石仅进入指定元件，不改变另一个元件内的 64 钻石，取回后数量恢复 / 32 diamonds enter only the selected cell and can be recovered |
| ExtendedAE 第 20 个元件 / EAE cell twenty | 实际取出 64 铜锭再存回，服务端核对总数仍为 98,765 / Real extraction and reinsertion preserves 98,765 copper |
| 原有元件快速取放 / Whole-cell regression | 96 次真实左键及普通／Shift 取放通过，元件唯一性和内部数量保持一致 / 96 rapid clicks and normal/Shift cell transfers passed |
| 普通／流体元件提示框 / Item/fluid cell tooltips | 每次仅一个组合提示框，保留原生内容预览，实机截图无重叠 / One combined tooltip with native previews; captures visually checked |
| ME网络 文案 / Root label | 界面实际控件名称验证通过 / Actual UI label verified |
| Auto 与外观 / Auto and appearance | 1280×720 倍率 2、1920×1080 倍率 3，关闭恢复原倍率；槽位、边界、方块模型回归通过 / Auto scaling, bounds, slots and block models passed |

测试还确认存储总线只读／只写和白名单限制、目标容器保护拒绝、背包已满、能量耗尽、旧选择与元件替换拒绝操作，以及设备范围修改后下一 tick 网络缓存更新。测试初始化会先移除一次性存档中旧设备的掉落物并重建指定背包；任何交互开始后不清理测试物品。

Additional checks cover storage-bus access modes and whitelists, target protection, full backpacks, exhausted energy, stale selections and replaced cells, plus network-cache updates after scoped transfers. Disposable fixtures are initialized before interaction; no cleanup masks item loss or duplication during tests.

## 实机截图 / Captures

- [流体元件提示框 / Fluid cell tooltip](screenshots/v0.4.2/smoke-fluid-cell-tooltip.png)
- [物品元件提示框 / Item cell tooltip](screenshots/v0.4.2/smoke-cell-tooltip.png)
- [ME网络 总览 / ME Network overview](screenshots/v0.4.2/smoke-auto1920-network.png)

## 复现 / Reproduction

```powershell
.\gradlew.bat test runGameTestServer '-PrunDir=../../work/run-v042' -PeaeTest
.\gradlew.bat runClient '-PrunDir=../../work/run-v042' -PsmokeTest -PeaeTest
.\gradlew.bat build -PeaeTest
```

附属 JAR 默认从 `../../work/vendor` 读取，可用 `-PcompatModsDir` 指定其他目录。客户端烟测要求已存在可重置的 `saves/SmokeTest` 存档和简体中文选项；测试会修改该存档的库存并自动退出。

Addon JARs default to `../../work/vendor`; override with `-PcompatModsDir`. Client smoke tests require an existing disposable `saves/SmokeTest` world and Simplified Chinese selected, modify its test inventories and exit automatically.

领地保护模组、大型整合包及多人并发仍需独立验证；AE2 15.x 没有旧版安全终端 API，本版不声称接入不存在的接口。

Claim-protection integrations, large modpacks and concurrent multiplayer still require separate validation. AE2 15.x does not expose the older security-terminal API.
