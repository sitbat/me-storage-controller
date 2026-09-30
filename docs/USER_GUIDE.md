# 使用说明 / User guide

## 连接与打开 / Connect and open

1. 安装 Forge 1.20.1 对应的 AE2、本模组及其依赖；多人游戏客户端和服务器都要安装。
2. 合成并放置 ME 存储控制器，用 ME 线缆连接到已经供电的网络，并为其留出一个频道。
3. 右键打开。若显示“网络离线”，检查能源、线缆、频道及网络是否分裂。

Install this addon and its Forge 1.20.1 dependencies on both sides. Connect the block to a powered AE2 grid with one free channel, then right-click. An offline message means power, channel availability or connectivity needs checking.

## 逐级浏览 / Drill down

打开后先查看全网概况。左侧设备列表用于选择设备，可以按名称搜索。设备详情显示维度和坐标；驱动器与 ME 箱子可以进一步查看其元件。使用返回按钮逐级回到设备或全网概况。

The first view shows the grid overview. Choose a device from the searchable list on the left. Device details include its dimension and coordinates. Drives and ME Chests provide individual cell details. Use Back to move up a level.

字节占用描述存储元件的内部容量，不等于物品个数：AE2 的类型开销和每字节存储数量会影响实际可存数量。类型占用描述可存储的不同条目数量。附属模组不公开这些数值时显示“未知”，不应将未知当成零或无限。

Byte usage measures a cell's internal capacity, not the number of items. AE2's per-type overhead and items-per-byte rules affect usable capacity. Type usage measures distinct entries. When an addon does not expose these values, the UI shows unknown; this does not mean zero or unlimited.

外部容器如果公开对应接口，可展示已占用／总槽位以及流体体积／总容量。槽位占用表示非空槽位的数量，不是剩余可插入物品的数量；半组物品也会占用一个槽位。容器的物理容量不等于经过存储总线过滤后可访问的容量。

External containers exposing the relevant capabilities can show occupied/total slots and fluid volume/capacity. Slot occupancy counts nonempty slots, not remaining item capacity; a partial stack still occupies one slot. Physical container capacity is distinct from the contents accessible through storage-bus filters.

内容清单支持搜索、按名称或数量排序和翻页。物品条目显示实际数量，流体按流体单位显示。名称相同但 NBT 不同的物品可以是不同存储类型，不应仅根据显示名称判断它们相同。

Search, sort by name or quantity, and page through contents. Item entries show actual quantities and fluid entries use fluid units. Items with the same display name but different NBT may be separate stored types.

## 管理整个元件 / Move complete cells

在设备详情中操作设备的元件槽位，可将整个元件移入或移出玩家物品栏。元件详情按钮用于查看内容。不要把内容清单当成 ME 终端：点击清单不会提取元件内的物品或流体。

Use the device's cell slots to move complete cells to or from your inventory. Cell detail controls inspect the contents. The content list is not an ME Terminal: clicking a listed resource does not extract items or fluids.

点击槽位下方数字进入该元件详情；普通点击、Shift 点击用于移动整个元件。超过 10 个槽位时，通过物品栏右侧的上下箭头切换元件页。只读附属设备仍可使用数字按钮查看详情，但不开放实际取放槽位。跨维度元件只读；同维度取放会验证玩家操作权限和目标设备的 Forge 交互保护事件。

Use the numbered buttons below the cell row to inspect cells. Click or Shift-click actual slots to move whole cells. For more than ten cells, use the arrow buttons beside the player inventory. Generic devices may offer numbered detail buttons without writable physical slots. Cross-dimensional cell operations are read-only; same-dimensional actions validate player access and post the target's Forge interaction protection event.

搜索支持当前客户端语言下的物品／流体注册名称、自定义名称及注册 ID；中文环境可搜索“铁锭”，也可输入 `minecraft:iron_ingot`。单次本地化匹配最多 512 个注册条目；过于宽泛的词可用更具体名称或 ID 缩小范围。

Search accepts client-localized item/fluid registry names, custom names and registry IDs. Localized matching is bounded to 512 registry entries per query; narrow very broad queries or use an exact ID.

元件移动直接作用于对应设备。取出元件后，其中内容通常会从该网络的可用库存消失，直到重新插入。存储总线连接的普通箱子、储罐不提供元件槽位。

Cell moves affect the actual device. Removing a cell normally removes its contents from the grid's available storage until it is reinserted. Ordinary chests and tanks connected by storage buses do not offer cell slots.

## 定位 / Locate

在设备详情点击“定位”。同维度、已加载且距离不超过 256 格的目标会临时高亮 15 秒。其他情况显示维度与坐标。本模组不会为了高亮而传送玩家或强制加载目标区块。

Click Locate in device details. A loaded target within 256 blocks in the same dimension is highlighted for 15 seconds. Otherwise, read its dimension and coordinates. Locating does not teleport the player or force-load target chunks.

## 兼容性解释 / Understanding compatibility

本模组读取当前网络能公开的存储信息。标准 AE2 驱动器和 ME 箱子拥有可管理的元件槽位；通过通用接口识别的其他设备可能只支持内容读取。存储总线过滤、提取模式或附属模组的实现会影响所见内容。

The addon reads storage information exposed by the current grid. Standard AE2 drives and ME Chests provide manageable cell slots. Other devices found through general interfaces may support content inspection only. Storage-bus filters, access modes and addon implementations can affect visible contents.

不同网络或同一容器的多个访问路径可能具有不同的可见内容。全网内容应以 AE2 网络视角理解；不要手工累加设备列表中所有外部容器的内容来推算唯一物理库存。

Different grids or multiple access paths to one container can expose different views. Interpret overview contents from AE2's grid perspective; do not sum every external device listing to infer unique physical inventory.

本版本应先在测试存档中验证第三方元件、领地保护和多人并发操作。请在报告问题时附上模组版本、设备种类、元件名称、是否使用过滤与涉及的日志；不要公开包含令牌或私人服务器信息的日志。

Validate third-party cells, claim protection and concurrent multiplayer actions in a test world. When reporting an issue, include mod versions, device and cell types, filtering settings and relevant logs, with private server information removed.
