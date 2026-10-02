# ME Storage Controller / ME存储控制器

**0.4.15 · Minecraft 1.20.1 Forge · AE2 addon**

通过一个联网控制器，逐级查看ME网络、文件夹、设备和元件的容量及内容，存取资源并管理整个存储元件。

Browse storage capacity and contents by network, folder, device or cell; transfer resources and manage complete cells through one connected controller.

[使用说明 / User guide](docs/USER_GUIDE.md) · [文档索引 / Documentation](docs/README.md) · [最新更新 / Release notes](docs/RELEASE_0.4.15.md)

## 安装 / Installation

开发与验收基线：**Java 17、Forge 47.4.0、AE2 15.4.10、GuideME 20.1.7**。GuideME是此AE2版本的依赖；使用Forge发行包。

Development and validation baseline: **Java 17, Forge 47.4.0, AE2 15.4.10 and GuideME 20.1.7**. GuideME is required by this AE2 version. Use Forge releases.

1. 在客户端与服务器的 `mods` 中安装本模组及依赖；单人游戏只需安装到游戏实例。
2. 放置**ME存储控制器**，用ME线缆接入已供电网络，为它留出**1个频道**。
3. 右键打开。物品悬停后长按 **G**，或点击侧栏指南按钮，查看中英文游戏内说明。

Install matching addon/dependency versions on both sides. Connect the block to a powered network with **one free channel**, then right-click. Hold **G** over its item or use the Guide button for the bilingual in-game guide. This block does not supply channels or replace AE2's ME Controller.

当前协议为 **9**，与0.4.14相同；不能连接使用协议8或更早协议的版本。客户端与服务器建议安装相同版本。

The current protocol is **9**, unchanged from 0.4.14. Protocol 8 and earlier are incompatible; use matching client/server versions.

合成配方 / Crafting: `QEQ / TCT / PEP`，Q=石英玻璃 / Quartz Glass，E=工程处理器 / Engineering Processor，T=ME终端 / ME Terminal，C=ME控制器 / ME Controller，P=福鲁伊克斯珍珠 / Fluix Pearl。产出1个控制器；[配方文件](src/main/resources/data/me_storage_controller/recipes/controller.json)可由数据包覆盖。Produces one controller; datapacks can override the recipe.

## 主要功能 / Features

- **存储树与容量**：逐级浏览，搜索、排序、精确数量、1024进位容量及未知／无限值区分。 / Hierarchical browsing, search, sorting, exact quantities and capacity units, distinguishing unknown from unlimited.
- **共享嵌套文件夹**：同一网络共用分类，Ctrl／Shift多选后右键整理。文件夹操作只改元数据，不搬物品或元件；单元件归属跟随槽位。 / Shared nested folders with Ctrl/Shift selection and context actions. Organization only changes metadata; cell membership follows its slot.
- **范围存取**：按网络、文件夹、设备或元件范围取放物品；使用AE2注册策略填充／倒空流体和化学品容器。实际元件槽用于移动整个元件。 / Scoped item and registered fluid/chemical container transfers, with separate controls for moving complete cells.
- **界面与定位**：深浅主题、个人本地折叠记忆，保持Minecraft原生GUI比例；同维度已加载目标可在256格内高亮15秒。 / Themes, personal tree memory and native GUI scale; highlight loaded targets within 256 blocks in the same dimension for 15 seconds.
- **附属适配**：ExtendedAE扩展驱动器、AE2 Omni Cells容量、Applied Mekanistics化学品；可选模组并非必装依赖。 / Support for ExtendedAE drives, OmniCells capacities and Applied Mekanistics chemicals; these addons are optional.
- **可选供电设置**：服务端能量旁路默认关闭，保留AE缓存；支持可提取Forge Energy来源及Flux Networks真实供电调度。 / Optional server energy bypass, off by default, retaining AE caches and real source/Flux supply limits.

具体按键与兼容边界请查阅[使用说明](docs/USER_GUIDE.md)。当前范围只包含控制器所在网络的可见存储，不递归扫描独立子网络或强制加载区块。文件夹成员离线时暂不可存取，可能重叠的库存范围会被拒绝，不会回退为全网操作。远程元件移动仅限同维度且可识别的物理槽位。

See the [user guide](docs/USER_GUIDE.md) for controls and compatibility details. Only the current grid's visible storage is included; no recursive subnet scan or forced chunk loading. Offline folder members and potentially overlapping inventories block scoped transfers instead of falling back to the entire network. Remote cell movement requires identifiable physical slots in the same dimension.

## 验证范围 / Validation scope

**0.4.15**构建及13项单元测试通过；本次仅调整文件夹菜单文字与输入框绘制，未重跑整套游戏内验收。**0.4.14**已通过49项服务端GameTests、13项JUnit、完整客户端操作与截图检查，以及两个独立服务端进程的保存／重启恢复验证。详见[当前更新](docs/RELEASE_0.4.15.md)与[完整验收记录](docs/RELEASE_0.4.14.md)。

**0.4.15** passed its build and 13 unit tests; the full in-game suite was not rerun for this local text/rendering change. **0.4.14** passed 49 server GameTests, 13 JUnit tests, client interaction/screenshot validation and persistence checks across two server processes. See the linked release records for exact coverage.

不保证所有AE2附属、整合包、真实双客户端并发或领地保护系统均兼容。容量读取依赖来源公开的接口；OmniCells BigInteger内容可能超过AE公开计数范围，不能承诺超64位数量精确。

Compatibility with every addon, modpack, simultaneous real clients or claim-protection system is not established. Capacity depends on exposed interfaces; OmniCells BigInteger quantities beyond AE's public 64-bit counters are not guaranteed exact.

## 构建 / Build

使用 **JDK 17**，在仓库根目录运行： / With **JDK 17**, run from the repository root:

```powershell
.\gradlew.bat build
```

Linux／macOS使用 `./gradlew build`。产物位于 `build/libs/`，游戏安装使用不带 `-sources` 的JAR。测试、隔离运行目录与可选附属环境见[开发与维护说明](docs/DEVELOPMENT.md)。

On Linux/macOS use `./gradlew build`. Install the JAR without `-sources` from `build/libs/`. See [development and maintenance](docs/DEVELOPMENT.md) for tests, isolated runs and optional addon environments.

## 许可证 / License

原创Java代码采用 [MIT](LICENSE)。收录的AE2美术及衍生视觉资源采用 **CC BY-NC-SA 3.0**，须遵守署名、非商业使用与相同方式共享要求；详见[资源来源与许可](docs/ASSETS.md)及[随包署名](src/main/resources/AE2-ASSET-NOTICE.txt)。本项目为独立附属，不是AE2官方项目。

Original Java code uses [MIT](LICENSE). Included AE2 artwork and derived visuals use **CC BY-NC-SA 3.0**, including attribution, noncommercial and share-alike requirements. See [asset provenance](docs/ASSETS.md) and the [bundled notice](src/main/resources/AE2-ASSET-NOTICE.txt). This is an independent addon, not an official AE2 project.
