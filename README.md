# ME Storage Controller / ME 存储控制器

**0.2.0 · Compatibility and dashboard update / 兼容性与仪表盘更新**

Minecraft **1.20.1 Forge** 的 AE2 附属模组。通过一个联网方块，逐级查看全网、存储设备和存储元件的占用及实际内容，并在设备详情中管理整个存储元件。

An AE2 addon for **Minecraft 1.20.1 Forge**. Browse network, device and cell storage details, inspect exact contents, and move complete storage cells through a connected controller.

本轮更新增加现代仪表盘与深色／浅色外观，扩展 ExtendedAE 驱动器适配及真实存储总线容器测试。新版进展与实际验收范围见 [0.2.0 记录](docs/RELEASE_0.2.0.md)；[0.1.0 测试记录](docs/TEST_REPORT.md) 单独保留。

This update adds a modern light/dark dashboard, ExtendedAE drive support and broader real storage-bus testing. See the [0.2.0 record](docs/RELEASE_0.2.0.md) for current validation status; the [0.1.0 record](docs/TEST_REPORT.md) remains separate.

## 安装 / Installation

本项目的开发基线为 **Java 17、Forge 47.4.0、AE2 15.4.10、GuideME 20.1.7**。GuideME 是此 AE2 版本的依赖。不要使用 Fabric 或 NeoForge 版本的依赖。

The development baseline is **Java 17, Forge 47.4.0, AE2 15.4.10 and GuideME 20.1.7**. GuideME is required by this AE2 release. Use the Forge releases of all dependencies.

1. 在客户端和服务器的 `mods` 文件夹安装相同版本的本模组及依赖。单人游戏只需安装到对应游戏实例。
2. 放置 **ME 存储控制器**，通过 ME 线缆接入网络。它需要供电和 **1 个频道**，不是用来替代 AE2 原版 ME 控制器的频道供应设备。
3. 右键打开界面。从设备列表选择驱动器、ME 箱子或外部存储来源，再查看单个元件。

Install matching versions of this mod and its dependencies on **both client and server**. Place the controller and connect it using ME cable; it needs power and **one channel**. It does not provide channels or replace AE2's ME Controller. Right-click it to browse devices and cells.

## 功能与边界 / Features and scope

- 现代仪表盘宽窗口，支持深色／浅色外观；设备与内容搜索、内容排序、分页，支持简体中文和英文。 / Modern wide dashboard with light/dark appearance, search, sorting, pagination, Simplified Chinese and English.
- 显示可获取的字节占用、类型槽位和进度条，内容清单展示精确数量。流体与其他存储类型按其实际单位显示。 / Available byte/type capacity information, progress bars, and exact content quantities with type-appropriate units.
- 设备详情允许存入、取出**整个存储元件**；内容清单只读，不能直接取出里面的物品或流体。 / Device details allow moving **complete storage cells**; content lists are read-only.
- 名称、维度、坐标与临时定位高亮。高亮仅适用于同维度、客户端已加载且不超过 256 格的设备，持续 15 秒。 / Names, dimensions, coordinates and a 15-second highlight for loaded devices within 256 blocks in the same dimension.
- “全网”是控制器所在 AE2 网络及其可见存储，不递归扫描独立子网络，也不强制加载区块。 / The overview covers the controller's current AE2 grid and its visible storage; it does not recursively scan independent subnets or force-load chunks.
- 优先使用 AE2 的存储接口。第三方存储元件能提供内容并不代表能提供容量；缺失的容量显示“未知”。创造模式、无限容量、虚空元件及自定义存储类型尤其需要逐项验证。 / Uses AE2 storage interfaces where possible. Third-party cells may expose contents without usable capacity information; missing capacity is shown as unknown. Creative, infinite, void and custom storage types require individual verification.
- 外部存储受存储总线的可见性与过滤规则影响；不会把字节、槽位和流体体积合成同一个占用百分比。 / External storage follows what the storage bus exposes; byte, slot and fluid capacities are not mixed into one occupancy percentage.
- 服务端验证远程元件操作。AE2 15.x 本身未提供旧版安全终端接口；不能声称本模组接入旧版安全终端或所有领地保护系统。使用领地保护模组时，请执行手动权限测试。 / Remote cell operations are server-validated. AE2 15.x does not provide the older security-terminal API; integration with older AE2 security terminals or every claim-protection mod is not claimed. Test your protection mod explicitly.

这是通用接口优先的测试版本，**不保证兼容所有 AE2 附属模组**。0.2.0 已通过 **5 项单元测试、安装 ExtendedAE 时的 11 项真实 Forge 游戏测试，以及无附属时的 8 项实际基线测试**。实际中文客户端完成 10 张深浅主题和紧凑布局截图，验证第 20 个元件取放、精确数量、中文搜索及快速设备切换。详见 [0.2.0 测试记录与截图](docs/RELEASE_0.2.0.md)。大型整合包、双客户端并发和保护模组仍需实测。

Version 0.2.0 passed **five unit tests, eleven real Forge GameTests with ExtendedAE, and eight executed baseline cases without it**. The Chinese-language client produced ten real dark/light/compact captures and verified twentieth-cell transfers, exact quantities, localized search and rapid device switching. See the [0.2.0 report and screenshots](docs/RELEASE_0.2.0.md). Broader modpacks, concurrent clients and protection mods remain unverified.

远程元件操作仅限同维度；跨维度设备仍可浏览。第三方设备只有在能可靠识别物理元件槽时才允许取放，否则只读。注册为全局存储提供者且没有节点／位置的来源，包含在全网内容中，但可能无法逐设备定位。总览的字节容量仅汇总能识别的存储元件，不代表外部容器的总容量。

Cell movement is limited to devices in the player's dimension; other dimensions remain inspectable. Unknown physical slot layouts are read-only. Global storage providers without a discoverable node/location remain in the network contents but may not have a device row. Byte totals cover identifiable cells, not external container capacities.

## 合成 / Crafting

```text
Q E Q
T C T
P E P
```

| 符号 / Key | 材料 / Ingredient | 注册名 / Registry ID |
|---|---|---|
| Q | 石英玻璃 / Quartz Glass | `ae2:quartz_glass` |
| E | 工程处理器 / Engineering Processor | `ae2:engineering_processor` |
| T | ME 终端 / ME Terminal | `ae2:terminal` |
| C | ME 控制器 / ME Controller | `ae2:controller` |
| P | 福鲁伊克斯珍珠 / Fluix Pearl | `ae2:fluix_pearl` |

产出 1 个 `me_storage_controller:controller`。配方可通过数据包覆盖。需要铁镐或更高级的镐采集。

Produces one `me_storage_controller:controller`. Override the recipe through a datapack. Mine with an iron-tier pickaxe or better.

## 构建 / Build

安装 **JDK 17**，将 `JAVA_HOME` 指向该 JDK，并允许 Gradle 下载依赖。在项目根目录执行：

Install **JDK 17**, point `JAVA_HOME` to it, and allow Gradle to download dependencies. Run from the project root:

```powershell
# Windows PowerShell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient
```

```sh
# Linux / macOS
./gradlew build
./gradlew runClient
```

成品位于 `build/libs/`；游戏安装使用不带 `-sources` 后缀的 JAR。开发专用服务器可使用 `gradlew runServer`，首次启动需要自行阅读并接受 Minecraft EULA。

Build artifacts are in `build/libs/`. Install the JAR without the `-sources` suffix. Use `gradlew runServer` for a development dedicated server; read and accept the Minecraft EULA yourself if you choose to run it.

## 项目文件 / Project files

- [使用说明 / User guide](docs/USER_GUIDE.md)
- [手动测试计划 / Manual test plan](docs/MANUAL_TEST_PLAN.md)
- [0.2.0 测试记录 / Current test report](docs/RELEASE_0.2.0.md)
- [0.1.0 历史记录与截图 / Historical test report and screenshots](docs/TEST_REPORT.md)
- [许可证 / License](LICENSE)

本模组原创代码和 JSON 模型采用 MIT 许可证。方块模型通过资源路径引用 AE2 与 Minecraft 已安装的纹理，不在本模组中复制分发这些纹理。本项目不是 AE2 官方项目。

Original mod code and JSON models are MIT licensed. Block models reference textures from installed AE2 and Minecraft resources without bundling copies. This is an independent addon, not an official AE2 project.
