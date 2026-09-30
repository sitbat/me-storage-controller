# 0.4.4 流体与化学品容器 / Fluid and chemical containers

右侧内容格现在接入 AE2 的通用 `ContainerItemStrategies` 容器交互。持对应容器左键点击资源可填充，普通右键可将容器内容倒入当前范围，空白格同样接受倒入。Shift 左键填充后尝试放入背包，单容器结果放不下时保留在鼠标上。普通物品存取和整个存储元件的槽位操作继续保留。

The content grid now uses AE2's generic `ContainerItemStrategies`. Left-click a resource with a matching container to fill it; ordinary right-click empties the container into the selected scope, including on an empty tile. Shift-left fills and tries to move the result into the backpack, retaining a single-container result on the cursor if space is unavailable. Ordinary item transfers and complete-cell slot operations remain available.

空手点击可装桶的原版流体时，可从当前选择的网络、设备或元件范围借一个空桶；不会为选中设备或元件跨范围取桶。失败后尝试返还原范围，返还不了的桶留在鼠标上。填充与倒空继续遵循存储范围、过滤、存取模式、玩家来源、供电和保护检查。

An empty-cursor click on a bucketable vanilla fluid can borrow one bucket from the selected network, device or cell scope. Device/cell selections never borrow across scopes. Failed filling attempts return it to the original scope, retaining it on the cursor if return is refused. Filling and emptying retain storage-scope, filtering, access-mode, player-source, power and protection checks.

流体由 AE2 提供容器策略；化学品等附属资源依赖附属向 AE2 注册对应策略，并需要匹配的容器。本版不保证任意自定义资源或容器都可存取。可选兼容环境为 **Mekanism 10.4.16.80 + Applied Mekanistics 1.4.3**，通过 `-PmekTest` 加载。简洁的原生提示框保持不变，不新增操作教程文字。

AE2 supplies the fluid strategy. Chemicals and other addon resources require an addon-registered strategy and a compatible container; arbitrary resources and containers are not guaranteed. Optional compatibility testing uses **Mekanism 10.4.16.80 + Applied Mekanistics 1.4.3**, enabled with `-PmekTest`. Concise native tooltips remain, without new instructional prose.

单次转移量遵循容器原生能力及速率：Mekanism 基础化学品储罐每次转移 1000 单位，并非一次填满其 64000 单位容量，与 AE2 标准终端一致。堆叠容器产生的额外物品由原生策略处理，满背包时可能掉落；单容器的鼠标保留行为不代表所有堆叠溢出都不掉落。

Transfers follow native container capabilities and rates: a Mekanism basic chemical tank transfers 1000 units per operation, not its entire 64000-unit capacity, matching AE2's standard terminal. Additional items produced from stacked containers are handled by the native strategy and may drop when the backpack is full; single-container cursor retention does not guarantee drop-free stacked overflow.

协议字段保持 **6**，可与 0.4.2／0.4.3 协议连接，但应将客户端和服务器都升级到 **0.4.4** 以获得完整行为。运行基线仍为 Java 17、Minecraft 1.20.1、Forge 47.4.0、AE2 15.4.10、GuideME 20.1.7。

Protocol remains **6**, allowing protocol connections with 0.4.2/0.4.3. Upgrade both client and server to **0.4.4** for complete behavior. The runtime baseline remains Java 17, Minecraft 1.20.1, Forge 47.4.0, AE2 15.4.10 and GuideME 20.1.7.

## 验证 / Validation

2026-09-30，5 项单元测试和 24 项服务端 GameTests 全部通过，日志为 `work/v044-server-tests-final.log`。新增 4 项流体容器测试和 1 项真实化学品兼容测试：

All five unit tests and all 24 server GameTests passed on September 30, 2026. The server log is listed above. New coverage includes four fluid-container tests and one real chemical-addon test:

- 水桶左键取出、右键倒入、Shift 转入背包；不足 1000 mB、仅余 500 mB 空间、全满与背包已满；网络借桶失败返还、元件范围隔离。 / Bucket filling, emptying and Shift-to-backpack; insufficient fluid, partial/full capacity, full backpacks, borrowed-bucket return and cell isolation.
- 真实 AE 能量元件只够处理半桶时，两方向均保持流体、容器和电量；充电后两方向各转移 1000 mB 并实际耗能。 / With real AE power sufficient for only half a bucket, both operations leave fluid, containers and energy unchanged; charged transfers move 1000 mB and consume power.
- Mekanism 氢气、红石灌注料、红色颜料和脏铁浆液通过真实基础储罐完成 80 次连续存取，每次按原生上限转移 1000，四类库存各恢复为 16000。同时验证邻接元件不变、不匹配容器拒绝、建造权限限制及 Shift 后化学品数据完整。 / Real basic tanks complete 80 rapid transfers across hydrogen, redstone infusion, red pigment and dirty iron slurry, respecting the 1000-per-operation rate and restoring each stock to 16000. Neighboring cells, unsupported containers, build restrictions and Shift preservation are checked.

最终客户端烟测和发行构建于 21:10 完成，日志为 `work/v044-client-build.log`，明确输出 `ME_STORAGE_SMOKE_BUCKET PASS`、`ME_STORAGE_SMOKE_RESULT PASS` 和 `BUILD SUCCESSFUL`。真实界面左右键及 Shift 水桶操作后，服务端核对水恢复为 23456 mB、熔岩维持 777000 mB、空桶恰好一个且没有掉落。原有物品存取、24 次快速内容点击、96 次快速元件点击、EAE 第 20 槽、深浅主题与 1280×720 / 1920×1080 Auto 缩放检查全部通过。

Final client smoke validation and the release build passed at 21:10. Real left/right and Shift bucket actions restore exactly 23456 mB of water and one empty bucket, leave 777000 mB of lava unchanged, and produce no dropped containers. Existing item transfers, 24 rapid content clicks, 96 rapid cell clicks, ExtendedAE slot twenty, both themes and Auto scaling at 1280×720 / 1920×1080 also passed. The log and explicit success markers are listed above.

测试使用隔离存档 `work/run-v044`，不修改正在游玩的演示存档。多人并发、领地保护和其他附属组合仍需独立验收。

Tests used the isolated `work/run-v044` worlds without modifying the active demo world. Concurrent multiplayer, claim protection and other addon combinations require separate validation.

- [取出一桶后水库存为 22456 mB / Water stock after filling one bucket](screenshots/v0.4.4/smoke-water-bucket-filled.png)
- [1920×1080 自动缩放 / Auto GUI scaling](screenshots/v0.4.4/smoke-auto1920-network.png)

## 复现 / Reproduction

```powershell
.\gradlew.bat test runGameTestServer '-PrunDir=../../work/run-v044' -PeaeTest -PmekTest
.\gradlew.bat runClient '-PrunDir=../../work/run-v044' -PsmokeTest -PeaeTest -PmekTest
.\gradlew.bat build -PeaeTest -PmekTest
```

附属 Forge JAR 默认放在 `../../work/vendor`，也可指定 `-PcompatModsDir`。ExtendedAE 测试使用 `-PeaeTest`；化学品测试使用 `-PmekTest`。客户端烟测需要已存在且可重置的 `saves/SmokeTest` 存档及简体中文设置；测试会修改隔离存档并自动退出。不要对需要保留的游玩存档运行烟测。

Place optional Forge JARs in `../../work/vendor`, or set `-PcompatModsDir`. Use `-PeaeTest` for ExtendedAE and `-PmekTest` for chemical tests. Client smoke tests require an existing disposable `saves/SmokeTest` world and Simplified Chinese settings; they modify the isolated world and exit automatically. Do not run smoke tests against a world you need to preserve.

完整交互说明及后续验收步骤见 [使用说明](USER_GUIDE.md) 和 [手动测试计划](MANUAL_TEST_PLAN.md)。历史结果见 [0.4.3](RELEASE_0.4.3.md) 与 [0.4.2](RELEASE_0.4.2.md)。

See the [user guide](USER_GUIDE.md) and [manual acceptance plan](MANUAL_TEST_PLAN.md) for behavior and follow-up checks. Historical results remain in [0.4.3](RELEASE_0.4.3.md) and [0.4.2](RELEASE_0.4.2.md).
