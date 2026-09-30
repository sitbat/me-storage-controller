# 0.4.0 使用说明 / User guide

## 连接与打开 / Connect and open

1. 安装 Forge 1.20.1 对应的 AE2、本模组及其依赖；多人游戏客户端和服务器都要安装。
2. 合成并放置 ME 存储控制器，用 ME 线缆连接到已经供电的网络，并为其留出一个频道。
3. 右键打开。若显示“网络离线”，检查能源、线缆、频道及网络是否分裂。

Install this addon and its Forge 1.20.1 dependencies on both sides. Connect the block to a powered AE2 grid with one free channel, then right-click. An offline message means power, channel availability or connectivity needs checking.

本版网络协议为 4，升级时请同时替换客户端和服务器上的旧版 JAR。界面与方块外观更新，但游戏版本仍为 **Minecraft 1.20.1 Forge**。

This release uses network protocol 4; replace the old JAR on both client and server. The interface and block appearance are updated, while the game version remains **Minecraft 1.20.1 Forge**.

## 本地演示启动 / Local demo

使用 JDK 17，并确保已有演示存档 `run/saves/ME-Controller-Demo`。在项目根目录执行以下命令；ExtendedAE 与 Glodium 的测试依赖使用项目配置的默认目录，其他位置可追加 `'-PcompatModsDir=C:/path/to/compat-jars'`。

```powershell
.\gradlew.bat runClient -Pdemo -PeaeTest
# 也可选择另一个现成存档，例如 run/saves/ME-Controller-Demo-0.4.0
.\gradlew.bat runClient -Pdemo -PeaeTest '-PdemoWorld=ME-Controller-Demo-0.4.0'
```

`demoWorld` 指定运行目录 `saves` 下的现成存档文件夹名，省略时默认为 `ME-Controller-Demo`。演示模式会进入该存档并尝试打开控制器界面，不执行自动测试或自动退出。如果控制器不存在或离线，会记录 `ME_STORAGE_DEMO_UNAVAILABLE` 并把操作权留给玩家。按 Esc 关闭界面后，可右键坐标 **8, 100, 8** 的控制器重新打开。此处说明启动方法，不代表当前实例已成功启动。

Use JDK 17 and an existing world under the run directory's `saves` folder. `demoWorld` selects its folder name and defaults to `ME-Controller-Demo`. Run either command above from the project root; add `'-PcompatModsDir=C:/path/to/compat-jars'` if the addon JARs are outside the default directory. Demo mode attempts to open the controller without running automated tests or exiting automatically. A missing or offline controller logs `ME_STORAGE_DEMO_UNAVAILABLE` and leaves the game available for manual play. After pressing Esc, right-click the controller at **8, 100, 8** to reopen it. These are launch instructions, not confirmation that an instance has started.

## 文件树与界面 / Storage tree and interface

左侧目录按 **整个网络 → 维度 → 设备 → 元件** 分层。点击节点前的箭头展开或折叠；多个设备可同时展开。点击设备名称查看设备汇总，点击元件行查看该元件的容量与内容。展开、折叠和滚动目录不会取放元件，也不会自动清除其他已展开的设备。

The left tree follows **Entire network → Dimension → Device → Cell**. Click arrows to expand or collapse branches; multiple devices can remain open. Click a device name for its summary or a cell row for that cell's details. Expanding, collapsing and scrolling do not move cells or automatically close other devices.

鼠标位于目录上时滚轮用于浏览树。滚离当前元件后，其详情仍保留。顶部“返回”从元件回到设备，再回到全网；“全网”直接返回网络概况。宽窗口在右侧显示层级路径和设备位置。

Use the mouse wheel over the tree to scroll. Scrolling the selected cell out of view leaves its details selected. Back moves from cell to device, then to the network; All returns directly to the overview. Wide layouts display the path and device location on the right.

顶部有定位、返回、全网和主题按钮。默认浅色，已有主题偏好仍保留，设置保存在当前客户端的 `config/me-storage-controller-client.properties`。宽窗口同时显示内容清单、元件操作区和玩家背包；紧凑窗口通过“存储内容 · 只读”和“元件与背包”标签切换。列表的物品图标是只读信息，只有元件操作区和玩家背包中的实际槽位可取放。

Header controls provide Locate, Back, All and Theme. Light is the default; saved preferences remain in `config/me-storage-controller-client.properties`. Wide windows show contents, cell controls and player inventory together. Compact windows use Contents · Read only and Cells & inventory tabs. Listed resource icons are read-only; actual cell-control and player-inventory slots handle transfers.

主容量条与元件下方的小容量条显示可获取的占用信息：低于 80% 使用蓝灰色，80% 至 95% 使用琥珀色，95% 起使用红色。未知容量不会伪造占用百分比。颜色用于辅助浏览，精确数值以文本及悬停提示为准。

The main capacity bar and small cell bars show available occupancy information: blue-gray below 80%, amber from 80% to below 95%, and red from 95%. Unknown capacity does not receive an invented percentage. Read the text and tooltips for exact values.

## 逐级浏览 / Drill down

打开后先查看全网概况。目录搜索支持维度、设备名称与元件名称；搜索时显示匹配的分支，清空后恢复原来的展开偏好。设备详情显示维度和坐标；驱动器与 ME 箱子可以进一步查看其元件。使用返回按钮逐级回到设备或全网概况。

The first view shows the grid overview. Tree search matches dimensions, device names and cell names, exposing matching branches while searching and restoring expansion preferences when cleared. Device details include dimension and coordinates. Drives and ME Chests offer cell details; Back moves up a level.

字节占用描述存储元件的内部容量，不等于物品个数：AE2 的类型开销和每字节存储数量会影响实际可存数量。类型占用描述可存储的不同条目数量。附属模组不公开这些数值时显示“未知”，不应将未知当成零或无限。

Byte usage measures a cell's internal capacity, not the number of items. AE2's per-type overhead and items-per-byte rules affect usable capacity. Type usage measures distinct entries. When an addon does not expose these values, the UI shows unknown; this does not mean zero or unlimited.

外部容器如果公开对应接口，可展示已占用／总槽位以及流体体积／总容量。槽位占用表示非空槽位的数量，不是剩余可插入物品的数量；半组物品也会占用一个槽位。容器的物理容量不等于经过存储总线过滤后可访问的容量。

External containers exposing the relevant capabilities can show occupied/total slots and fluid volume/capacity. Slot occupancy counts nonempty slots, not remaining item capacity; a partial stack still occupies one slot. Physical container capacity is distinct from the contents accessible through storage-bus filters.

内容清单支持搜索、按名称或数量排序和翻页。物品条目显示实际数量，流体按流体单位显示。名称相同但 NBT 不同的物品可以是不同存储类型，不应仅根据显示名称判断它们相同。

Search, sort by name or quantity, and page through contents. Item entries show actual quantities and fluid entries use fluid units. Items with the same display name but different NBT may be separate stored types.

## 管理整个元件 / Move complete cells

在设备详情中操作设备的元件槽位，可将整个元件移入或移出玩家物品栏。元件详情按钮用于查看内容。不要把内容清单当成 ME 终端：点击清单不会提取元件内的物品或流体。

Use the device's cell slots to move complete cells to or from your inventory. Cell detail controls inspect the contents. The content list is not an ME Terminal: clicking a listed resource does not extract items or fluids.

点击树中的元件行，或元件操作区槽位下方数字，进入元件详情；普通点击、Shift 点击实际槽位用于移动整个元件。超过 10 个槽位时，选中树中后面的元件会自动映射到对应操作页，也可使用元件区域的分页箭头。只读附属设备可查看详情但不开放取放槽位。跨维度元件只读；同维度取放会验证玩家操作权限和目标设备的 Forge 交互保护事件。

Select a cell in the tree or use its numbered control below the slots. Click or Shift-click actual slots to move whole cells. Selecting later tree cells maps the controls to the appropriate page; cell-area arrows also change pages. Read-only addon devices can expose details without writable slots. Cross-dimensional operations are read-only; same-dimensional actions validate player access and the target's Forge interaction protection event.

ExtendedAE 扩展 ME 驱动器在目录中展示实际元件槽位。选择第 20 个元件时，操作区显示第 11–20 个真实槽位，其中最后一格就是所选元件。元件本身的兼容性仍取决于其存储接口，适配驱动器不等于保证所有第三方元件都能显示字节容量。

ExtendedAE expanded drives list their actual cells in the tree. Selecting cell twenty shows physical slots eleven through twenty in the operation area; its last slot is the selected cell. Compatibility still depends on each cell's storage interfaces, so drive support does not guarantee capacity information for every addon cell.

搜索支持当前客户端语言下的物品／流体注册名称、自定义名称及注册 ID；中文环境可搜索“铁锭”，也可输入 `minecraft:iron_ingot`。单次本地化匹配最多 512 个注册条目；过于宽泛的词可用更具体名称或 ID 缩小范围。

Search accepts client-localized item/fluid registry names, custom names and registry IDs. Localized matching is bounded to 512 registry entries per query; narrow very broad queries or use an exact ID.

元件移动直接作用于对应设备。取出元件后，其中内容通常会从该网络的可用库存消失，直到重新插入。存储总线连接的普通箱子、储罐不提供元件槽位。

Cell moves affect the actual device. Removing a cell normally removes its contents from the grid's available storage until it is reinserted. Ordinary chests and tanks connected by storage buses do not offer cell slots.

## 方块外观 / Block appearance

控制器方块采用下沉前面板与独立底部状态灯条。前面板上的树和容量条纹是设备标识，实际存储数据请在界面查看。底部灯条反映真实联网状态：在线时缓慢呼吸并提供 4 级方块光照，掉电或失去频道后熄灭。模型与图集来源见 [视觉资源说明](ASSETS.md)。

The block has a recessed front panel and an independent lower status strip. Its painted tree/storage symbols identify the device; live storage data is shown in the GUI. The lower strip follows the actual network node: online it gently pulses and emits light level four; losing power or a channel turns it off. See [visual asset provenance](ASSETS.md).

## 定位 / Locate

在设备详情点击“定位”。同维度、已加载且距离不超过 256 格的目标会临时高亮 15 秒。其他情况显示维度与坐标。本模组不会为了高亮而传送玩家或强制加载目标区块。

Click Locate in device details. A loaded target within 256 blocks in the same dimension is highlighted for 15 seconds. Otherwise, read its dimension and coordinates. Locating does not teleport the player or force-load target chunks.

## 兼容性解释 / Understanding compatibility

目录每次最多显示 256 个设备、每个设备最多 256 个元件，总计最多 4,096 个元件条目；达到限制或部分元件无法枚举时，会提示目录只显示部分内容。这个显示限制不代表未显示的存储已经从网络消失。

The directory is bounded to 256 devices, 256 cells per device and 4,096 cell entries overall. A partial-directory notice appears if these limits are reached or some cells cannot be enumerated. This display limit does not mean omitted storage has disappeared from the network.

本模组读取当前网络能公开的存储信息。标准 AE2 驱动器和 ME 箱子拥有可管理的元件槽位；通过通用接口识别的其他设备可能只支持内容读取。存储总线过滤、提取模式或附属模组的实现会影响所见内容。

The addon reads storage information exposed by the current grid. Standard AE2 drives and ME Chests provide manageable cell slots. Other devices found through general interfaces may support content inspection only. Storage-bus filters, access modes and addon implementations can affect visible contents.

外部存储来源显示目标容器及其位置，悬停可查看连接它的存储总线和容器连接面。对于熔炉这类方向受限的容器，槽位数是连接面实际公开的槽位，而不是方块 GUI 中所有槽位。双箱应按总线实际连接的合并库存处理；存储总线过滤后的可读内容仍可能少于物理库存。

External sources identify the target container and its location; hover to inspect the connecting storage bus and container face. For sided containers such as furnaces, the slot count follows the connected face, not every slot in the block's own GUI. Double chests use the combined inventory accessible to the bus; filtering may still expose fewer contents than physically stored.

不同网络或同一容器的多个访问路径可能具有不同的可见内容。全网内容应以 AE2 网络视角理解；不要手工累加设备列表中所有外部容器的内容来推算唯一物理库存。

Different grids or multiple access paths to one container can expose different views. Interpret overview contents from AE2's grid perspective; do not sum every external device listing to infer unique physical inventory.

本版本应先在测试存档中验证第三方元件、领地保护和多人并发操作。请在报告问题时附上模组版本、设备种类、元件名称、是否使用过滤与涉及的日志；不要公开包含令牌或私人服务器信息的日志。

Validate third-party cells, claim protection and concurrent multiplayer actions in a test world. When reporting an issue, include mod versions, device and cell types, filtering settings and relevant logs, with private server information removed.
