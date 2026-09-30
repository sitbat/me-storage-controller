# 0.4.12 AE2 Omni Cells兼容 / AE2 Omni Cells support

新增 **AE2 Omni Cells 1.20.1-forge 1.1.6** 的容量适配：Omni、Complex Omni、Quantum 三系 1k～256M，以及创造 Long／BigInteger 元件。内容继续读取已挂载存储的实时 AE2 接口，元件搬移和混合资源容器操作沿用现有入口。本次不改变侧栏图标、存储树、布局或原生GUI比例；[0.4.11及更早记录](RELEASE_0.4.11.md)保留。

This release adds capacity support for **AE2 Omni Cells 1.20.1-forge 1.1.6**: Omni, Complex Omni and Quantum tiers from 1k to 256M, plus creative Long/BigInteger cells. Contents continue to use the mounted storage's live AE2 interface, with existing cell-movement and mixed-resource container controls. Sidebar icons, storage tree, layout and native GUI scale are unchanged; [0.4.11 and earlier records](RELEASE_0.4.11.md) are preserved.

## 容量语义与升级 / Capacity and upgrade

- 字节占用遵循OmniCells自身规则：相同每字节数量的资源合并计数后进位，不额外收取类型开销。
- Quantum类型数量不限，不代表普通Quantum元件字节无限。已知无限上限显示 **∞**，无法确定的值显示**未知**。
- BigInteger单个条目达到 AE2公开计数器的 `Long.MAX_VALUE` 时可能已经截断，不能推断超出64位范围的精确内容或字节占用。此时已用字节保留未知，已知无限上限仍显示∞。
- 网络协议升级为 **7**，无限上限与未知容量使用不同状态。**客户端和服务器必须同步更新**，不与协议6版本混用。

Byte usage follows OmniCells' shared amount-per-byte buckets without per-type overhead. Unlimited Quantum types do not imply unlimited byte capacity. Known unlimited bounds show **∞**, separately from **unknown** values. A BigInteger resource at AE2's public `Long.MAX_VALUE` counter may be saturated; quantities or usage beyond that range are not claimed exact. Used bytes remain unknown while the unlimited bound remains ∞. Protocol is now **7**, requiring both client and server to update; protocol-6 releases cannot connect.

## 版本依据 / Version provenance

主要测试JAR来自用户现有实例的 `ae2omnicells-1.20.1-forge-1.1.6.jar`，SHA-256为 `c3222ad905ddb0a0517bbd76b48722bfe5a2e5770375f1e393b427d4a26fd5e7`。源码依据[上游1.20.1分支固定提交7592756](https://github.com/Frostbite-time/AE2OmniCells/tree/75927568887a1f8d80822a722375751cbadabbe1)，其版本配置为1.1.6。上游同名Forge发布标签指向另一版本分支，故本次未将该标签源码作为1.20.1依据；版本配置与公开API已核对，不声称进行了可复现构建比对。

The primary test JAR is the user's existing 1.20.1 Forge 1.1.6 artifact with the hash above. Source research uses the pinned upstream 1.20.1 branch commit, whose version is 1.1.6. The similarly named Forge release tag points to another version branch and was not used as the 1.20.1 source. Version metadata and public APIs were checked; reproducible binary/source equivalence is not claimed.

## 验证状态 / Validation status

2026-10-01，使用上述实际OmniCells JAR、AE2 15.4.10、ExtendedAE 1.4.21、Mekanism 10.4.16.80及Applied Mekanistics 1.4.3，**30项服务端GameTest全部通过**。其中5项新增OmniCells测试验证分桶计量、类型限制、大数边界、范围隔离和实际容器存取。9项单元测试通过。适配范围不等于每个档位和第三方组合都已逐一实测。

All **30 required server GameTests** passed on October 1, 2026, using the real OmniCells JAR and the versions above. Five new tests cover OmniCells accounting, type limits, large values, scope isolation and actual container transfers. Nine unit tests passed. Every tier and third-party combination has not been individually tested.

| 项目 / Check | 状态 / Status |
|---|---|
| 三系元件容量、类型及高阶容量 / Three series, type limits and high tiers | 通过：1k三系、256M填满、63／12／无限类型 / Passed |
| 创造Long／BigInteger无限与截断边界 / Creative Long/BigInteger unlimited and saturation bounds | 通过：超32位计数及BigInteger单条超Long.MAX_VALUE / Passed |
| 混合物品、流体、化学品及容器守恒 / Mixed resources and container conservation | 通过：四类Mek化学品，真实水桶及基础化学储罐往返 / Passed |
| 元件范围隔离、物理移出重入 / Cell scope isolation and physical removal/reinsertion | 通过：48次快速操作、失效库存拒绝存入、重插后数量守恒 / Passed |
| 客户端真实截图与既有交互回归 / Real client captures and existing interaction regression | 通过：实际协议7数据、四种容量视图；滚轮、容器、指南、96次元件点击、自动比例 / Passed |
| 发行构建 / Release build | 通过 / Passed |
| 未安装OmniCells的回归 / Without OmniCells | 通过：25项既有测试通过，5项Omni专用测试明确跳过 / Passed: 25 existing tests, 5 Omni tests explicitly skipped |

首轮元件重插测试在同一tick内立即发送新选择和取出，未等待服务端确认；修正测试节奏后保留全部数量与隔离断言并通过，生产逻辑未放宽。

The initial reinsertion test sent a new selection and extraction within one tick without waiting for acknowledgement. It now waits for the real server acknowledgement while retaining all quantity and isolation assertions; production checks were not relaxed.

客户端日志同时包含 `ME_STORAGE_SMOKE_OMNI PASS`、`ME_STORAGE_SMOKE_RESULT PASS` 和 `BUILD SUCCESSFUL`。固定比例2、1280×720自动比例3、1920×1080自动比例4以及960×720窄屏检查通过；生产界面没有修改用户GUI比例。下列原始游戏截图经过目视检查，有限容量、无限类型与无限容量均正常显示：

The client log contains all three explicit success markers above. Fixed scale 2, native Auto scales 3 and 4, and the narrow 960×720 layout pass. Production screens do not change the user's GUI-scale option. These original gameplay captures were visually inspected:

- [ME网络容量汇总 / Network summary](screenshots/v0.4.12/smoke-omni-network.png)
- [256M复合元件 / Complex cell](screenshots/v0.4.12/smoke-omni-complex256m.png)
- [量子元件：类型∞、容量1KB / Quantum cell](screenshots/v0.4.12/smoke-omni-quantum.png)
- [BigInteger元件：容量与类型∞ / BigInteger cell](screenshots/v0.4.12/smoke-omni-biginteger.png)

```powershell
.\gradlew.bat runGameTestServer '-PrunDir=../../work/run-v0412-server' -PomniTest -PeaeTest -PmekTest
.\gradlew.bat runClient build '-PrunDir=../../work/run-v0412' -PsmokeTest -PomniTest -PeaeTest -PmekTest
.\gradlew.bat runGameTestServer '-PrunDir=../../work/run-v0412-no-omni' -PeaeTest -PmekTest
```

测试使用隔离目录与可丢弃的SmokeTest存档副本。上游可选配方缺失、离线鉴权以及OmniCells缺少Mixin minVersion的日志信息不影响上述测试完成。

Tests use isolated directories and a disposable SmokeTest save copy. Upstream optional-recipe, offline-authentication and missing Mixin minVersion messages do not prevent test completion.
