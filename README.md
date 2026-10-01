# ME Storage Controller / ME存储控制器

**0.4.15**：文件夹菜单改为“移出文件夹”，新建／重命名输入框及文字取消阴影。详见 [更新说明](docs/RELEASE_0.4.15.md)。

**0.4.15** removes the folder name field's border shading and text shadow, and renames the action to “Move out of folder”.

**0.4.14 · 网络共享文件夹与个人折叠记忆 / Shared network folders and personal tree memory**

新增全ME网络共享的嵌套文件夹：同一网络的玩家与控制器看到同一分类。Ctrl／Shift选择目录项后右键，可归组、重命名、移动分类或删除文件夹；这些操作**只改分类元数据，不搬动物品或元件**。单元件成员跟随设备槽位，新换入的元件自动归组。选择文件夹可汇总浏览并仅在成员范围内存取。目录折叠状态另存于个人客户端，不与其他玩家共享。

Nested folders are shared by players and controllers on the same ME network. Use Ctrl/Shift selection and the tree's context menu to group, rename, reorganize or delete folders. These actions **change classification metadata only; they never move items or cells**. Cell membership follows its device slot, including replacement cells. Selecting a folder aggregates its members and restricts transfers to them. Tree folding preferences are saved locally for each player.

0.4.14已通过49项服务端GameTest、13项JUnit，以及两个独立服务端进程的真实重启保存／恢复验证（修订4→8）；不安装可选附属也可正常启动与关服。完整客户端操作验收及截图检查也已通过。详见 [0.4.14测试记录](docs/RELEASE_0.4.14.md)，历史记录按原版本保留。

Version 0.4.14 passed 49 server GameTests, 13 JUnit tests and persistence verification across two separate server processes (revision 4→8). Startup and shutdown also succeeded without optional addons. The complete client interaction test and screenshot review also passed. See the [0.4.14 test report](docs/RELEASE_0.4.14.md); historical records retain their original versions.

Minecraft **1.20.1 Forge** 的 AE2 附属模组。通过一个联网方块，逐级查看全网、存储设备和存储元件的占用及实际内容，直接存取右侧物品，并在设备详情中管理整个存储元件。

An AE2 addon for **Minecraft 1.20.1 Forge**. Browse network, device and cell storage details, inspect exact contents, transfer items and move complete storage cells through a connected controller.

支持 AE2 Omni Cells **1.20.1-forge 1.1.6** 容量适配，覆盖 Omni、Complex Omni、Quantum 三系 1k～256M 及创造 Long／BigInteger 元件。已知无限上限显示 **∞**，与未知容量区分；内容仍读取 AE2 实时存储接口。混合资源存取及元件搬移已通过实际模组测试，详细范围见0.4.12历史记录。既有侧栏、存储树与原生 GUI 比例保持不变。

Capacity support includes AE2 Omni Cells **1.20.1-forge 1.1.6**: Omni, Complex Omni and Quantum tiers from 1k to 256M, plus creative Long/BigInteger cells. Known unlimited bounds display **∞**, separately from unknown values; contents still come from live AE2 storage. Mixed-resource transfers and cell movement passed actual-mod integration tests; the historical 0.4.12 record lists the tested scope. The sidebar, storage tree and native GUI scale are unchanged.

0.4.13取消存储树的设备／元件数量截断，大型目录分批完整同步。新增默认关闭的服务端设置“绕过AE能量转换限制”，管理员可使用 `/mestorage energyBypass true` 开启、`false` 关闭，并保存到存档的 `serverconfig/me-storage-controller-server.toml`。能源元件保持原有缓存功能；Flux Networks无线点通过真实供电需求适配。详见使用说明和当前测试记录。

Version 0.4.13 removes directory count truncation and streams complete large trees. The server setting for bypassing AE buffer-capacity limits defaults to off. Administrators can use `/mestorage energyBypass true` or `false`; changes persist in the world serverconfig. Existing energy cells retain their buffering role, with a demand-driven adapter for Flux Networks points. See the guide and validation record.

容量自动切换 B／KB／MB／GB／TB／PB／EB（1024 进位），并在栏宽不足时缩放文字，避免省略容量；悬停仍显示精确字节。容器存取保持 0.4.4 行为。

Capacity summaries now use adaptive B/KB/MB/GB/TB/PB/EB labels (powers of 1024) and fit the available width. Hover for exact bytes. Container transfers retain 0.4.4 behavior.

本版通过 AE2 `ContainerItemStrategies` 支持流体及附属模组注册的化学品容器存取，保留简洁提示框，不新增操作教学文字。界面继续使用官方 AE2 1.21.1 的 13 张原始 PNG 与确认的终端布局：9 列、最多 5 行的内容网格、侧边图标栏、文件树，以及元件槽与玩家背包。完整双栏布局为 340×240 逻辑像素，按当前界面比例适配可用空间，运行版本仍为 Minecraft 1.20.1 Forge。历史验收状态见 [0.4.13 记录](docs/RELEASE_0.4.13.md)；[0.4.11](docs/RELEASE_0.4.11.md) 及更早记录单独保留。

This update supports fluid and addon-registered chemical containers through AE2's `ContainerItemStrategies`, retaining concise tooltips without new instructions. The approved terminal layout retains thirteen original AE2 1.21.1 PNGs, a nine-column content grid with up to five rows, icon toolbar, storage tree, cell slots and player inventory. The full two-column layout uses 340×240 logical pixels and adapts to the space available at the current GUI scale. The runtime remains Minecraft 1.20.1 Forge. See the historical [0.4.13 record](docs/RELEASE_0.4.13.md); [0.4.11](docs/RELEASE_0.4.11.md) and earlier records remain separate.

## 安装 / Installation

本项目的开发基线为 **Java 17、Forge 47.4.0、AE2 15.4.10、GuideME 20.1.7**。GuideME 是此 AE2 版本的依赖。不要使用 Fabric 或 NeoForge 版本的依赖。

The development baseline is **Java 17, Forge 47.4.0, AE2 15.4.10 and GuideME 20.1.7**. GuideME is required by this AE2 release. Use the Forge releases of all dependencies.

1. 在客户端和服务器的 `mods` 文件夹安装相同版本的本模组及依赖。单人游戏只需安装到对应游戏实例。
2. 放置 **ME存储控制器**，通过 ME 线缆接入网络。它需要供电和 **1 个频道**，不是用来替代 AE2 原版 ME 控制器的频道供应设备。
3. 右键打开界面。在存储目录中展开维度与设备，选择驱动器、ME 箱子、外部存储来源或单个元件。

Install matching versions of this mod and its dependencies on **both client and server**. Place the controller and connect it using ME cable; it needs power and **one channel**. It does not provide channels or replace AE2's ME Controller. Right-click it to browse devices and cells.

0.4.14 使用网络协议 **9**，增加共享文件夹同步。**客户端和服务器必须同步更新**，不能与0.4.13及更早版本混用。

Version 0.4.14 uses protocol **9** for shared folder synchronization. **Update client and server together**; 0.4.13 and earlier releases cannot connect.

## 功能与边界 / Features and scope

- 文件夹递归汇总子文件夹，重复的设备／槽位成员不会重复计数。成员离线或离开当前网络时，该文件夹暂不可存取；可能指向同一物理库存或重叠ME网络的范围会被拒绝，不会回退为全网操作。 / Folder totals include nested members without counting duplicate device/slot references twice. An offline or disconnected member makes that folder temporarily unavailable for transfers. Potentially overlapping physical inventories or ME-network ranges are rejected, without falling back to the whole network.
- 文件树式设备与元件管理，支持多个设备展开、滚动和层级路径；提供原生风格图标控件、悬停说明、深浅主题、搜索、排序及中英文。 / Tree-based device/cell management with multiple expanded branches, scrolling and hierarchical navigation, plus native-style icon controls, tooltips, themes, search, sorting and both languages.
- 显示可获取的字节占用、类型槽位和进度条，内容清单展示精确数量。流体与其他存储类型按其实际单位显示。 / Available byte/type capacity information, progress bars, and exact content quantities with type-appropriate units.
- 右侧普通物品支持左键取一组／放入全部、右键取半组／放入一个、空手 Shift 左键取到背包。操作按当前 ME网络、文件夹、设备或元件范围执行，左侧槽位管理**整个存储元件**。 / Transfer ordinary items within the selected network, folder, device or cell scope: left-click a stack/all, right-click half/one, or Shift-left-click with an empty cursor into the backpack. Left-side slots move **complete cells**.
- 持对应容器左键点击流体／化学品条目以填充，普通右键倒入当前范围（空格也可）；Shift 左键填充后放入背包，单容器结果放不下时保留鼠标持物。支持取决于 AE2 或附属注册的容器策略，不保证所有自定义资源均可取出。 / Left-click a fluid/chemical entry with a matching container to fill it; ordinary right-click empties it into the selected scope, including on empty tiles. Shift-left fills into the backpack, retaining a single-container result on the cursor when space is unavailable. Support requires an AE2 or addon-registered container strategy; arbitrary custom resources are not guaranteed.
- 名称、维度、坐标与临时定位高亮。高亮仅适用于同维度、客户端已加载且不超过 256 格的设备，持续 15 秒。 / Names, dimensions, coordinates and a 15-second highlight for loaded devices within 256 blocks in the same dimension.
- “ME网络”是控制器所在 AE2 网络及其可见存储，不递归扫描独立子网络，也不强制加载区块。 / The overview covers the controller's current AE2 grid and its visible storage; it does not recursively scan independent subnets or force-load chunks.
- 优先使用 AE2 的存储接口。第三方存储元件能提供内容并不代表能提供容量；缺失的容量显示“未知”。创造模式、无限容量、虚空元件及自定义存储类型尤其需要逐项验证。 / Uses AE2 storage interfaces where possible. Third-party cells may expose contents without usable capacity information; missing capacity is shown as unknown. Creative, infinite, void and custom storage types require individual verification.
- 外部存储受存储总线的可见性与过滤规则影响；不会把字节、槽位和流体体积合成同一个占用百分比。 / External storage follows what the storage bus exposes; byte, slot and fluid capacities are not mixed into one occupancy percentage.
- 服务端验证远程元件操作。AE2 15.x 本身未提供旧版安全终端接口；不能声称本模组接入旧版安全终端或所有领地保护系统。使用领地保护模组时，请执行手动权限测试。 / Remote cell operations are server-validated. AE2 15.x does not provide the older security-terminal API; integration with older AE2 security terminals or every claim-protection mod is not claimed. Test your protection mod explicitly.

这是通用接口优先的测试版本，**不保证兼容所有 AE2 附属模组**。0.4.13 的历史验收状态见 [历史测试记录](docs/RELEASE_0.4.13.md)。大型整合包、双客户端并发和保护模组仍需实测。

See the [historical report](docs/RELEASE_0.4.13.md) for prior validation results. Broader modpacks, concurrent clients and protection mods remain unverified.

远程元件操作仅限同维度；跨维度设备仍可浏览。第三方设备只有在能可靠识别物理元件槽时才允许取放，否则只读。注册为全局存储提供者且没有节点／位置的来源，包含在全网内容中，但可能无法逐设备定位。总览的字节容量仅汇总能识别的存储元件，不代表外部容器的总容量。

Cell movement is limited to devices in the player's dimension; other dimensions remain inspectable. Unknown physical slot layouts are read-only. Global storage providers without a discoverable node/location remain in the network contents but may not have a device row. Byte totals cover identifiable cells, not external container capacities.

OmniCells 的 BigInteger 元件可能存储超过 AE2公开计数范围的数量。单个条目达到 `Long.MAX_VALUE` 时可能已被截断；此时已用字节显示未知，已知无限上限仍显示∞，不承诺超出64位范围的精确数量。

OmniCells BigInteger storage can exceed AE2's public counter range. A per-key amount at `Long.MAX_VALUE` may be saturated; used bytes then remain unknown while the known unlimited bound remains ∞. Exact quantities beyond the signed 64-bit range are not claimed.

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

可选化学品兼容测试使用 **Mekanism 10.4.16.80 + Applied Mekanistics 1.4.3**；将对应 Forge JAR 放入 `../../work/vendor`（或指定 `-PcompatModsDir`），使用 `-PmekTest` 加载，例如 `./gradlew runClient -PmekTest`。这些附属不是本模组的必装依赖。

Optional chemical compatibility tests use **Mekanism 10.4.16.80 + Applied Mekanistics 1.4.3**. Place their Forge JARs in `../../work/vendor` (or set `-PcompatModsDir`) and enable `-PmekTest`, for example `./gradlew runClient -PmekTest`. These addons are optional dependencies.

OmniCells兼容测试使用 `ae2omnicells-1.20.1-forge-1.1.6.jar`，放入同一测试依赖目录后加 `-PomniTest`；与 `-PmekTest` 同时启用可验证混合元件中的化学品及容器存取。OmniCells不是必装依赖。

For OmniCells integration tests, place `ae2omnicells-1.20.1-forge-1.1.6.jar` in the same dependency directory and enable `-PomniTest`. Combine it with `-PmekTest` to test chemicals and containers in mixed cells. OmniCells remains optional.

## 项目文件 / Project files

- [使用说明 / User guide](docs/USER_GUIDE.md)
- [模型与纹理来源 / Visual asset provenance](docs/ASSETS.md)
- [手动测试计划 / Manual test plan](docs/MANUAL_TEST_PLAN.md)
- [0.4.14 测试记录 / Current test report](docs/RELEASE_0.4.14.md)
- [0.4.13 历史测试记录 / Historical test report](docs/RELEASE_0.4.13.md)
- [0.4.8 历史测试记录 / Historical test report](docs/RELEASE_0.4.8.md)
- [0.4.7 历史测试记录 / Historical test report](docs/RELEASE_0.4.7.md)
- [0.4.6 历史测试记录 / Historical test report](docs/RELEASE_0.4.6.md)
- [0.4.5 历史测试记录 / Historical test report](docs/RELEASE_0.4.5.md)
- [0.4.4 历史测试记录 / Historical test report](docs/RELEASE_0.4.4.md)
- [0.4.3 历史测试记录 / Historical test report](docs/RELEASE_0.4.3.md)
- [0.4.2 历史测试记录 / Historical test report](docs/RELEASE_0.4.2.md)
- [0.4.1 历史测试记录 / Historical test report](docs/RELEASE_0.4.1.md)
- [0.4.0 历史测试记录 / Historical test report](docs/RELEASE_0.4.0.md)
- [0.3.0 历史测试记录 / Historical test report](docs/RELEASE_0.3.0.md)
- [0.2.0 历史测试记录 / Historical test report](docs/RELEASE_0.2.0.md)
- [0.1.0 历史记录与截图 / Historical test report and screenshots](docs/TEST_REPORT.md)
- [许可证 / License](LICENSE)

原创 Java 代码采用 MIT 许可证；收录的 AE2 美术及其衍生模型／视觉改编采用 CC BY-NC-SA 3.0，须保留署名和许可。参见 [视觉资源与许可说明](docs/ASSETS.md)。本项目不是 AE2 官方项目。

Original Java code is MIT licensed. Included AE2 artwork and derived models/visual adaptations use CC BY-NC-SA 3.0; retain attribution and license notices. See [asset provenance and licensing](docs/ASSETS.md). This is an independent addon, not an official AE2 project.
