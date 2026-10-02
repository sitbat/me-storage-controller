# 0.4.14 使用说明 / User guide

## 连接与打开 / Connect and open

1. 安装 Forge 1.20.1 对应的 AE2、本模组及其依赖；多人游戏客户端和服务器都要安装。
2. 合成并放置 ME存储控制器，用 ME 线缆连接到已经供电的网络，并为其留出一个频道。
3. 右键打开。若显示“网络离线”，检查能源、线缆、频道及网络是否分裂。

Install this addon and its Forge 1.20.1 dependencies on both sides. Connect the block to a powered AE2 grid with one free channel, then right-click. An offline message means power, channel availability or connectivity needs checking.

0.4.14新增网络共享嵌套文件夹与个人本地折叠记忆，保留完整目录、默认关闭的能量旁路、OmniCells和既有存取功能。网络协议为 **9**，客户端与服务器须同步更新，不能与0.4.13及更早版本混用。游戏版本仍为 **Minecraft 1.20.1 Forge**。已通过49项服务端GameTest、13项JUnit及两个独立服务端进程的真实保存／重启恢复验证（修订4→8）；无可选附属环境也正常启动与关服。完整客户端操作验收及截图检查也已通过，详见 [0.4.14测试记录](RELEASE_0.4.14.md)。

Version 0.4.14 adds shared nested folders and personal local tree memory, preserving complete directories, the optional energy bypass, OmniCells and existing transfers. Protocol is **9**: update both sides together; 0.4.13 and earlier releases cannot connect. The runtime remains **Minecraft 1.20.1 Forge**. 49 server GameTests, 13 JUnit tests and persistence verification across two independent server processes passed (revision 4→8). Startup and shutdown also succeeded without optional addons. The complete client interaction test and screenshot review also passed; see the [0.4.14 report](RELEASE_0.4.14.md).

## 游戏内指南 / In-game guide

将鼠标悬停在ME存储控制器物品上，长按 AE2／GuideME 的指南快捷键（默认 **G**），即可打开对应页面。控制器侧栏顶部的 **?** 按钮也会打开同一页。关闭指南后返回原界面。若改过指南按键，使用游戏按键设置中的实际绑定。

Hover the ME Storage Controller item and hold the existing AE2/GuideME guide key (**G** by default). The top **?** sidebar button opens the same page; closing the guide returns to the previous screen. Use your configured guide binding if you changed it.

指南提供简体中文和英文内容，介绍连接、存储范围、容量与实际数量、物品和容器存取、元件管理、搜索滚动、外观和定位。

The guide is available in Simplified Chinese and English, covering connections, scopes, capacity and amounts, item/container transfers, whole-cell management, browsing, appearance and locating devices.

## 本地演示启动 / Local demo

使用 JDK 17，并确保已有演示存档 `run/saves/ME-Controller-Demo`。在项目根目录执行以下命令；ExtendedAE 与 Glodium 的测试依赖默认放在 `compat-mods/`，其他位置可追加 `'-PcompatModsDir=C:/path/to/compat-jars'`。原开发工作区可指定 `'-PcompatModsDir=../../work/vendor'`，或使用[开发说明](DEVELOPMENT.md)中的 `dev.ps1` 自动发现依赖。

```powershell
.\gradlew.bat runClient -Pdemo -PeaeTest
# 也可选择另一个现成存档，例如 run/saves/ME-Controller-Demo-0.4.3
.\gradlew.bat runClient -Pdemo -PeaeTest '-PdemoWorld=ME-Controller-Demo-0.4.3'
```

`demoWorld` 指定运行目录 `saves` 下的现成存档文件夹名，省略时默认为 `ME-Controller-Demo`。演示模式会进入该存档并尝试打开控制器界面，不执行自动测试或自动退出。如果控制器不存在或离线，会记录 `ME_STORAGE_DEMO_UNAVAILABLE` 并把操作权留给玩家。按 Esc 关闭界面后，可右键坐标 **8, 100, 8** 的控制器重新打开。此处说明启动方法，不代表当前实例已成功启动。

Use JDK 17 and an existing world under the run directory's `saves` folder. `demoWorld` selects its folder name and defaults to `ME-Controller-Demo`. Run either command above from the project root; add `'-PcompatModsDir=C:/path/to/compat-jars'` if the addon JARs are outside `compat-mods/`. The original workspace can use `../../work/vendor`; see [development instructions](DEVELOPMENT.md) for the helper script. Demo mode attempts to open the controller without running automated tests or exiting automatically. A missing or offline controller logs `ME_STORAGE_DEMO_UNAVAILABLE` and leaves the game available for manual play. After pressing Esc, right-click the controller at **8, 100, 8** to reopen it. These are launch instructions, not confirmation that an instance has started.

## 文件树与界面 / Storage tree and interface

左侧目录按 **ME网络 → 维度 → 设备 → 元件** 分层。点击节点前的箭头展开或折叠；多个设备可同时展开。点击设备名称查看设备汇总，点击元件行查看该元件的容量与内容。展开、折叠和滚动目录不会取放元件，也不会自动清除其他已展开的设备。

The left tree follows **ME Network → Dimension → Device → Cell**. Click arrows to expand or collapse branches; multiple devices can remain open. Click a device name for its summary or a cell row for that cell's details. Expanding, collapsing and scrolling do not move cells or automatically close other devices.

鼠标位于目录上时滚轮用于浏览树。滚离当前元件后，其详情仍保留。“返回”从元件回到设备，再回到全网；“全网”直接返回网络概况。图标按钮提供悬停说明，标题与节点提示显示完整详情。

Use the mouse wheel over the tree to scroll. Scrolling the selected cell out of view leaves its details selected. Back moves from cell to device, then to the network; All returns directly to the overview. Hover the title or path for full details.

界面采用最大 340×240 逻辑像素的 AE 终端布局：左侧文件树与元件区，右侧 9 列、最多 5 行的内容网格和玩家背包。元件槽为 5 列 × 2 行，选择具有可操作元件的设备后，可直接在同屏元件槽与背包之间取放。图标栏提供返回、全网、排序、主题等控制。默认浅色，已有主题偏好仍保留，设置保存在当前客户端的 `config/me-storage-controller-client.properties`。内容网格支持物品存取及已注册的流体／化学品容器交互，选中条目后左侧显示精确数量。提示框保持简洁，不额外显示操作教程。

The terminal uses up to 340×240 logical pixels, with the tree and cells on the left and a nine-column content grid with up to five rows plus player inventory on the right. The five-column, two-row cell controls and player inventory remain visible together, allowing direct transfers when the selected device exposes editable cells. The icon toolbar provides Back, All, Sort and Theme controls. Light is the default; saved preferences remain in `config/me-storage-controller-client.properties`. The grid supports item transfers and registered fluid/chemical containers, with exact selected quantities on the left. Tooltips remain concise, without additional instructions.

控制器保持 Minecraft 当前实际 GUI 比例，打开、关闭、调整大小均不会修改比例选项或窗口渲染倍率。界面根据当前逻辑空间自适应：空间足够时显示双栏，较矮时减少内容行数，窄屏通过目录图标切换文件树与内容区。窗口变化保留搜索、选择和折叠状态。

The controller keeps Minecraft's actual GUI scale. Opening, closing and resizing never change the scale option or the window rendering scale. It adapts to the logical viewport: both columns when space permits, fewer content rows in shorter viewports, and a tree/content toggle on narrow screens. Resizing preserves search, selection and folding.

鼠标在右侧内容区、滑轨或上下箭头上时，滚轮均可滚动内容。快速滚动会累加目标位置，滚轮步长和触控板的小数增量不会被丢掉。背包和搜索框不用于内容滚动。

Wheel input works over the content grid, scrollbar and arrow buttons. Rapid input accumulates the target position and retains both wheel magnitude and fractional trackpad input. Inventory and search fields do not scroll the content list.

主容量条与树内元件条显示可获取的占用信息：低于 80% 使用当前主题强调色，80% 至 95% 使用琥珀色，95% 起使用红色。实际元件槽下方的细条始终使用主题强调色。未知容量不会伪造占用百分比。颜色用于辅助浏览，精确数值以文本及悬停提示为准。

The main capacity bar and tree cell bars use the theme accent below 80%, amber from 80% to below 95%, and red from 95%. Thin bars beneath physical cell slots always use the theme accent. Unknown capacity does not receive an invented percentage. Read text and tooltips for exact values.

## 共享文件夹 / Shared folders

文件夹保存在存档中，由**同一ME网络的所有玩家与控制器共享**，支持多层嵌套。成员可以是整个设备，也可以是设备中的一个元件槽。点击文件夹会汇总它及子文件夹的成员，容量和内容不会重复计算同一设备／槽位。

Folders persist in the world and are **shared by all players and controllers on the same ME network**. They can contain nested folders, whole devices or individual cell slots. Selecting a folder aggregates its descendants without double-counting the same device/slot.

在目录中使用 **Ctrl左键**增减多选、**Shift左键**选择连续范围，再右键打开操作菜单。可将选中项归入新文件夹、创建子文件夹、重命名、移动到其他文件夹或移出分类。右键菜单本身不会切换当前内容操作范围；左键选择文件夹后才以该文件夹为存取范围。

Use **Ctrl-left-click** to toggle multiple selections and **Shift-left-click** for a range, then right-click for actions. Group selections into a new folder, create a child folder, rename, move classifications or remove assignments. Opening a context menu does not switch the current content scope; left-click a folder to use it for transfers.

**新建、归组、重命名、移动分类与删除只修改分类元数据，不搬动物品、流体或实际存储元件。**删除文件夹会将直接成员与子文件夹提升到其父级；删除顶层文件夹则回到根目录。单元件归属**跟随槽位**：取出元件后槽位归属仍在，换入新元件会自动加入该分类；旧元件移到其他槽后，不会凭原身份继续属于此分类。

**Creating, grouping, renaming, reorganizing and deleting folders only changes classification metadata; it never moves items, fluids or physical cells.** Deleting a folder promotes its direct members and child folders to its parent, or to the root. Individual cell membership **follows the slot**: an empty slot stays assigned and a replacement cell joins automatically. A removed cell does not retain membership after moving elsewhere.

只有主动在右侧内容区存取、使用容器或Shift存入背包物品时，才改变存储内容。文件夹操作只使用其成员，遵循原存储过滤、模式、供电和权限；不足时不会借用非成员空间或资源。任一成员离线、移除或离开当前网络时，该文件夹暂不可存取，归属记录仍保留。可能重叠的外部库存或ME网络范围会被拒绝，而不是冒险重复统计或跨范围存取。

Storage contents change only when you explicitly transfer resources, use containers or Shift-insert inventory items. Folder transfers use only members and retain filtering, access, energy and permission rules, without borrowing from nonmembers. Any offline, removed or disconnected member temporarily makes that folder unavailable for transfers while retaining its assignment. Potentially overlapping external inventories or ME-network ranges are rejected instead of risking duplicate counts or transfers outside the scope.

网络分裂后各分支保留分类；不可访问的成员不会被当作另一个网络的可用存储。重连时合并分类变更。其他玩家更新分类后，界面会同步；过期操作被拒绝时，请等待刷新再重试。

Split networks retain their classifications without gaining access to members on another grid. Reconnection merges classification changes. Other players' edits synchronize to your screen; if an outdated action is rejected, wait for the refresh and try again.

目录展开／折叠和面板收起状态属于**个人客户端偏好**，关闭再打开时恢复；按世界或服务器、玩家、维度及控制器位置分别记忆，不会改变其他玩家的目录。调整窗口和从指南返回也保留当前状态。

Tree expansion and panel collapse are **personal client preferences**, restored when reopening and separated by world/server, player, dimension and controller location. They do not change another player's tree. Resizing or returning from the guide also preserves the current state.

## 逐级浏览 / Drill down

打开后先查看全网概况。目录搜索支持维度、设备名称与元件名称；搜索时显示匹配的分支，清空后恢复原来的展开偏好。设备详情显示维度和坐标；驱动器与 ME 箱子可以进一步查看其元件。使用返回按钮逐级回到设备或全网概况。

The first view shows the grid overview. Tree search matches dimensions, device names and cell names, exposing matching branches while searching and restoring expansion preferences when cleared. Device details include dimension and coordinates. Drives and ME Chests offer cell details; Back moves up a level.

点击左侧工具栏的 **A–Z 目录搜索图标**，可显示或收起目录搜索框。目录搜索与右侧内容搜索独立；要取消目录过滤，请清空目录搜索文字，收起输入框本身不会清除搜索条件。

Click the **A–Z tree-search icon** on the left toolbar to show or hide the tree search field. Tree and content searches are independent. Clear the tree query to remove its filter; hiding the field retains the query.

容量摘要按 1024 进位自动选择 B／KB／MB／GB／TB／PB／EB，最多保留一位小数；悬停显示完整字节数。

Capacity summaries use powers of 1024 with B/KB/MB/GB/TB/PB/EB labels and at most one decimal; hover for the exact byte counts.

字节占用描述存储元件的内部容量，不等于物品个数：AE2 的类型开销和每字节存储数量会影响实际可存数量。类型占用描述可存储的不同条目数量。附属模组不公开这些数值时显示“未知”，不应将未知当成零或无限。

Byte usage measures a cell's internal capacity, not the number of items. AE2's per-type overhead and items-per-byte rules affect usable capacity. Type usage measures distinct entries. When an addon does not expose these values, the UI shows unknown; this does not mean zero or unlimited.

外部容器如果公开对应接口，可展示已占用／总槽位以及流体体积／总容量。槽位占用表示非空槽位的数量，不是剩余可插入物品的数量；半组物品也会占用一个槽位。容器的物理容量不等于经过存储总线过滤后可访问的容量。

External containers exposing the relevant capabilities can show occupied/total slots and fluid volume/capacity. Slot occupancy counts nonempty slots, not remaining item capacity; a partial stack still occupies one slot. Physical container capacity is distinct from the contents accessible through storage-bus filters.

内容清单支持搜索、按名称或数量排序和翻页。物品条目显示实际数量，流体按流体单位显示。名称相同但 NBT 不同的物品可以是不同存储类型，不应仅根据显示名称判断它们相同。

Search, sort by name or quantity, and page through contents. Item entries show actual quantities and fluid entries use fluid units. Items with the same display name but different NBT may be separate stored types.

## 存取物品 / Item transfers

右侧物品区按当前选择的范围操作：选择 **ME网络** 时使用网络的正常存储路由；选择文件夹时只使用其递归成员；选择设备或元件时只操作该设备或元件，失败时不会转存到其他设备。空白格同样可以接收鼠标上拿着的物品。

The content grid operates on the selected scope. **ME Network** uses normal network routing; a folder uses only its recursive members; a device or cell selection only accesses that device or cell and never falls back to another destination. Empty tiles also accept the carried item.

以下表格描述普通物品取放；可填充或倒空的容器按下一节规则处理。

The table describes ordinary item transfers; fillable or drainable containers follow the next section.

| 操作 / Action | 鼠标未持物 / Empty cursor | 鼠标持物 / Carrying an item |
|---|---|---|
| 左键 / Left-click | 取出最多一组 / Extract up to one stack | 存入持有的全部物品 / Insert the carried stack |
| 右键 / Right-click | 取出可取一组的一半，向上取整 / Extract half of an available stack, rounded up | 存入一个 / Insert one |
| Shift 左键 / Shift-left-click | 取一组到背包，受可用空间限制 / Extract a stack into available backpack space | 存入持有的全部物品 / Insert the carried stack |

Shift 点击背包中的普通物品可将其存入当前范围。若当前设备开放元件槽，Shift 点击背包中的存储元件仍优先将整个元件装入设备；普通物品不会误入元件槽。

Shift-click a regular backpack item to insert it into the selected scope. When editable cell slots are available, Shift-clicking a storage cell still installs the complete cell in the selected device.

存取由服务端执行，使用 AE2 原生存储接口、玩家操作来源和能量消耗；存储总线过滤、存取模式及元件限制仍然生效。设备或元件范围操作还会检查设备及外部容器的交互保护，同维度且目标可访问时才允许操作。无电、无空间、目标移除或拒绝存取时不会凭空生成物品。

Transfers run on the server through AE2 storage interfaces with the player action source and energy costs. Storage-bus filters, access modes and cell restrictions remain effective. Scoped transfers also check device and external-container interaction protection and require an accessible target in the same dimension. Power, capacity and access failures cannot create items.

## 流体与化学品容器 / Fluid and chemical containers

本版使用 AE2 `ContainerItemStrategies` 注册的容器能力。原版流体由 AE2 提供策略；化学品等附属类型必须由相应附属注册策略，并提供能处理该资源的容器。显示内容或容量不等于一定支持取出；未注册的自定义资源仍可查看，不保证可装入任意容器。

Container interactions use AE2's registered `ContainerItemStrategies`. AE2 supplies fluid support; chemical and other addon types need an addon-registered strategy and a compatible container. Visible contents or capacity do not guarantee extraction support. Unregistered custom resources remain inspectable and cannot be assumed to fit arbitrary containers.

单次转移量遵循容器的原生能力及速率，并非一次填满其全部容量。例如 Mekanism 基础化学品储罐每次可转移 1000 单位，而不是一次填满 64000 单位；AE2 标准终端同样遵循此限制。

Each transfer follows the container's native capability and rate rather than filling its total capacity in one click. For example, a Mekanism basic chemical tank transfers 1000 units per operation, rather than filling all 64000 units at once; AE2's standard terminal follows the same limit.

| 操作 / Action | 行为 / Behavior |
|---|---|
| 持对应容器左键点击资源条目 / Left-click a resource with a matching container | 从当前选择范围填充容器 / Fill from the selected scope |
| 持含资源容器普通右键（不按 Shift） / Right-click a filled container without Shift | 向当前范围倒入内容；点击空白格也可 / Empty its contents into the selected scope, including on an empty tile |
| Shift 左键点击资源条目 / Shift-left-click a resource | 填充后尝试放入玩家背包；单容器结果放不下时保留鼠标 / Fill, then try to move the result into the backpack; retain a single-container result on the cursor if it cannot fit |

单个容器填充后的背包转移失败时，结果保留在鼠标上。持有一叠容器时，处理一个容器产生的额外物品由 AE2／附属容器策略安排，背包满时可能掉落；不要将单容器保证理解为所有堆叠容器都不会掉落。

If moving a filled single container into the backpack fails, it remains on the cursor. When holding stacked containers, additional items produced while processing one container are handled by the AE2/addon strategy and may drop if the inventory is full. The single-container guarantee does not cover every stacked-container overflow.

空手点击原版可装桶流体时，会尝试从**当前选择范围**取一个空桶并填充；Shift 左键可将结果放入背包。当前范围没有空桶时不会向其他设备或全网借桶。填充失败时尝试将借出的空桶还回原范围，未能还回的桶保留在鼠标上，不丢弃。其他资源没有通用的“自动找容器”保证，通常需自行持有对应容器。

An empty-cursor click on a bucketable vanilla fluid tries to borrow one empty bucket from the **selected scope** and fill it; Shift-left can move the result into the backpack. No bucket is borrowed from another device or the full network when absent from that scope. Failed filling attempts return the borrowed bucket to its original scope, retaining it on the cursor if it cannot be returned. Other resources have no generic automatic-container lookup guarantee; normally hold the appropriate container yourself.

容器操作继续遵循供电、容量、存取模式、过滤、玩家操作来源与范围权限。拒绝填充或倒空不会自动改为存入整个容器，也不会回退到其他存储范围。可选化学品验收环境为 **Mekanism 10.4.16.80 + Applied Mekanistics 1.4.3**，使用 `-PmekTest` 加载；这不代表所有化学品附属均已兼容。0.4.14使用协议 **9**，必须同步更新客户端与服务器。

Container actions retain power, capacity, access-mode, filtering, player-source and scope checks. A rejected fill or empty action does not instead store the whole container or fall back to a different scope. Optional chemical testing uses **Mekanism 10.4.16.80 + Applied Mekanistics 1.4.3**, enabled with `-PmekTest`; this does not imply compatibility with every chemical addon. Version 0.4.14 uses protocol **9** and requires updating client and server together.

## 管理整个元件 / Move complete cells

在设备详情中操作设备的元件槽位，可将整个元件移入或移出玩家物品栏。元件详情按钮用于查看内容。右侧内容网格用于移动存储中的物品；左侧元件槽用于移动整个元件。

Use the device's cell slots to move complete cells to or from your inventory. Cell detail controls inspect the contents. The content grid moves stored items, while the left-side cell slots move complete storage cells.

点击树中的元件行进入元件详情；普通点击、Shift 点击实际槽位用于移动整个元件。超过 10 个槽位时，选中树中后面的元件会自动映射到对应操作页，也可使用元件区域的分页箭头。只读附属设备可查看详情但不开放取放槽位。跨维度元件只读；同维度取放会验证玩家操作权限和目标设备的 Forge 交互保护事件。

Select a cell in the tree to inspect it. Click or Shift-click actual slots to move whole cells. Selecting later tree cells maps the controls to the appropriate page; cell-area arrows also change pages. Read-only addon devices can expose details without writable slots. Cross-dimensional operations are read-only; same-dimensional actions validate player access and the target's Forge interaction protection event.

ExtendedAE 扩展 ME 驱动器在目录中展示实际元件槽位。选择第 20 个元件时，操作区显示第 11–20 个真实槽位，其中最后一格就是所选元件。元件本身的兼容性仍取决于其存储接口，适配驱动器不等于保证所有第三方元件都能显示字节容量。

ExtendedAE expanded drives list their actual cells in the tree. Selecting cell twenty shows physical slots eleven through twenty in the operation area; its last slot is the selected cell. Compatibility still depends on each cell's storage interfaces, so drive support does not guarantee capacity information for every addon cell.

搜索支持当前客户端语言下的物品／流体注册名称、自定义名称及注册 ID；中文环境可搜索“铁锭”，也可输入 `minecraft:iron_ingot`。单次本地化匹配最多 512 个注册条目；过于宽泛的词可用更具体名称或 ID 缩小范围。

Search accepts client-localized item/fluid registry names, custom names and registry IDs. Localized matching is bounded to 512 registry entries per query; narrow very broad queries or use an exact ID.

元件移动直接作用于对应设备。取出元件后，其中内容通常会从该网络的可用库存消失，直到重新插入。存储总线连接的普通箱子、储罐不提供元件槽位。

Cell moves affect the actual device. Removing a cell normally removes its contents from the grid's available storage until it is reinserted. Ordinary chests and tanks connected by storage buses do not offer cell slots.

## 绕过AE能量转换限制 / AE energy conversion bypass

默认关闭，配置作用于整个服务端存档。拥有2级权限的管理员使用以下命令查询或修改；修改立即生效并保存到当前存档。

Off by default and scoped to the server world. Administrators with permission level 2 can query or change it. Changes apply immediately and persist in the active world.

```text
/mestorage energyBypass
/mestorage energyBypass true
/mestorage energyBypass false
```

配置文件位于 `<存档>/serverconfig/me-storage-controller-server.toml`。客户端单独修改文件不能覆盖专用服务器的设置。

The configuration is `<world>/serverconfig/me-storage-controller-server.toml`. A client's local setting does not override a dedicated server.

```toml
[energy]
    bypassAeEnergyLimit = false
```

开启后，AE的用电需求可由贴着能源接收器的真实外部电源补足，不再仅受ME网络中AE缓存容量限制。能源元件保持原有充放电和储能容量；已有缓存先参与供电。支持可主动提取的Forge Energy电源，以及 **Flux Networks 1.20.1-7.2.1.15** 无线点。Flux无线点按真实请求向其原网络申请供电，首次突发需求可能要等待数个原生供电周期。Flux优先级、传输限额、上游供能和剩余电量继续有效；其他仅主动推送、无法提取的供电接口尚未做专门适配。

When enabled, real sources attached to an AE energy acceptor can meet demand beyond the grid's AE buffer capacity. Existing caches supply energy first, and cells retain normal capacity and charging. Supports extractable Forge Energy sources and **Flux Networks 1.20.1-7.2.1.15** points. Flux receives real demand through its normal network scheduling; a new burst can require several supply cycles. Flux priorities, limits and available upstream energy remain effective. Other push-only interfaces have no dedicated adapter yet.

## 方块外观 / Block appearance

控制器方块使用官方 AE2 1.21.1 机器表面、显示器面板及终端遮罩，组合为适配 Forge 1.20.1 的自定义模型。福鲁伊克斯色显示层反映真实联网状态：在线时全亮并缓慢脉动，离线时降低亮度。实时容量仍需在界面中查看。美术来源、改编方式及 CC BY-NC-SA 3.0 许可见 [视觉资源说明](ASSETS.md)。

The block combines official AE2 1.21.1 machine surfaces, monitor artwork and terminal masks in a custom model adapted for Forge 1.20.1. Fluix-colored display layers follow the actual node state: full-bright with a slow pulse online, dimmed offline. Read live capacity in the GUI. See [asset provenance](ASSETS.md) for sources, adaptations and CC BY-NC-SA 3.0 licensing.

## AE2 Omni Cells

适配版本为 **1.20.1-forge 1.1.6**，涵盖 Omni、Complex Omni、Quantum 三系 1k～256M 和创造 Long／BigInteger 元件。按原有目录选择设备或元件即可查看；内容仍来自当前联网元件的实时存储接口，混合物品、流体及已注册化学品的取放继续遵循当前范围与容器规则。具体实测项目见 [0.4.12记录](RELEASE_0.4.12.md)，未完成的项目不视为已验证。

The target is **1.20.1-forge 1.1.6**, covering Omni, Complex Omni and Quantum tiers from 1k to 256M plus creative Long/BigInteger cells. Select a device or cell through the existing tree. Contents come from the mounted cell's live storage, with the same scope and container rules for mixed items, fluids and registered chemicals. See the [0.4.12 record](RELEASE_0.4.12.md) for actual validation; pending cases are not verified.

OmniCells 不收取额外类型字节开销。Quantum 的类型上限无限，但普通 Quantum 元件的字节容量仍有限。**∞** 表示已知无限上限；**未知**表示无法可靠取得数值，两者不等同。

OmniCells has no extra per-type byte overhead. Quantum cells have unlimited types, while ordinary Quantum byte capacity remains finite. **∞** means a known unlimited bound; **unknown** means no reliable value is available.

BigInteger 元件中，单条资源达到 AE2公开计数上限 `Long.MAX_VALUE`（9,223,372,036,854,775,807）时可能已被截断。此时不能据此算出精确占用，已用字节显示未知，无限上限仍显示∞；界面不保证显示超出这个范围的精确数量。

A BigInteger cell's per-resource amount may be saturated at AE2's public `Long.MAX_VALUE` counter (9,223,372,036,854,775,807). Exact usage cannot then be derived: used bytes remain unknown and the unlimited bound stays ∞. Exact amounts beyond that range are not guaranteed.

## 定位 / Locate

在设备详情点击“定位”。同维度、已加载且距离不超过 256 格的目标会临时高亮 15 秒。其他情况显示维度与坐标。本模组不会为了高亮而传送玩家或强制加载目标区块。

Click Locate in device details. A loaded target within 256 blocks in the same dimension is highlighted for 15 seconds. Otherwise, read its dimension and coordinates. Locating does not teleport the player or force-load target chunks.

## 兼容性解释 / Understanding compatibility

目录不再按设备数、单设备元件数或总元件数截断。大型目录分批同步，接收完整后更新，刷新期间保留原有树、选择和滚动位置。展开分支后可滚动到底部查看全部可枚举条目。

The directory is no longer truncated by device count, cells per device or total cell count. Large directories synchronize in bounded batches and replace the previous tree once complete, retaining selection and scroll. Expand and scroll through all enumerable entries.

本模组读取当前网络能公开的存储信息。标准 AE2 驱动器和 ME 箱子拥有可管理的元件槽位；通过通用接口识别的其他设备可能只支持内容读取。存储总线过滤、提取模式或附属模组的实现会影响所见内容。

The addon reads storage information exposed by the current grid. Standard AE2 drives and ME Chests provide manageable cell slots. Other devices found through general interfaces may support content inspection only. Storage-bus filters, access modes and addon implementations can affect visible contents.

外部存储来源显示目标容器及其位置，悬停可查看连接它的存储总线和容器连接面。对于熔炉这类方向受限的容器，槽位数是连接面实际公开的槽位，而不是方块 GUI 中所有槽位。双箱应按总线实际连接的合并库存处理；存储总线过滤后的可读内容仍可能少于物理库存。

External sources identify the target container and its location; hover to inspect the connecting storage bus and container face. For sided containers such as furnaces, the slot count follows the connected face, not every slot in the block's own GUI. Double chests use the combined inventory accessible to the bus; filtering may still expose fewer contents than physically stored.

不同网络或同一容器的多个访问路径可能具有不同的可见内容。全网内容应以 AE2 网络视角理解；不要手工累加设备列表中所有外部容器的内容来推算唯一物理库存。

Different grids or multiple access paths to one container can expose different views. Interpret overview contents from AE2's grid perspective; do not sum every external device listing to infer unique physical inventory.

本版本应先在测试存档中验证第三方元件、领地保护和多人并发操作。请在报告问题时附上模组版本、设备种类、元件名称、是否使用过滤与涉及的日志；不要公开包含令牌或私人服务器信息的日志。

Validate third-party cells, claim protection and concurrent multiplayer actions in a test world. When reporting an issue, include mod versions, device and cell types, filtering settings and relevant logs, with private server information removed.
