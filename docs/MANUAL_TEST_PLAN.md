# 手动验收计划 / Manual acceptance plan

以下项目是**待执行的验收步骤**，不是已通过的测试记录。执行人应记录日期、环境、实际结果和日志。静态检查、编译和资源校验无法代替游戏内验证。

0.4.11 验收状态另见 [当前测试记录](RELEASE_0.4.11.md)；这里保留完整的后续人工验收清单。

The following are **acceptance procedures to execute**, not passed test results. Record the date, environment, observed results and logs. Static checks, compilation and resource validation do not replace gameplay testing.

## 环境 / Environments

| 环境 / Environment | 基线 / Baseline | 结果 / Result |
|---|---|---|
| 单人开发环境 / Development single-player | Java 17, Forge 47.4.0, AE2 15.4.10, GuideME 20.1.7 | 待测 / Pending |
| 化学品附属 / Chemical addons | Mekanism 10.4.16.80 + Applied Mekanistics 1.4.3, `-PmekTest` | 待测 / Pending |
| 专用服务器及两个客户端 / Dedicated server and two clients | 同上 / Same baseline | 待测 / Pending |
| 整合包 / Modpack | 记录所有 AE2 附属和保护模组版本 / Record addon and protection versions | 待测 / Pending |

## 核心流程 / Core flow

| # | 操作 / Action | 预期 / Expected |
|---|---|---|
| 1 | 加载新世界、搜索物品、放置方块、切换四个方向 / Load world, find item, place in four orientations | 无加载错误；模型、物品及名称正常 / No loading errors; correct block/item model and names |
| 2 | 按 README 配方合成，分别用手、石镐、铁镐破坏 / Craft; break by hand, stone pickaxe, iron pickaxe | 配方产出一个；只有合适工具正常掉落 / Recipe makes one; proper tool required for drop |
| 3 | 接入有能源和空余频道的网络，然后断电并恢复 / Connect powered grid with free channel; remove/restore power | 正常打开；离线状态清晰；恢复后可刷新 / Works online; offline state clear; recovers |
| 4 | 用尽频道后添加本方块，再腾出频道 / Exhaust channels, add block, then free a channel | 遵循频道规则，不在无频道时继续操作 / Follows channel availability |
| 5 | 网络接两个驱动器、一个 ME 箱子和一个存储总线箱子 / Connect two drives, ME Chest and storage-bus chest | 设备可区分，名称、维度、坐标准确 / Devices distinguished with correct locations |
| 6 | 全网 → 驱动器 → 元件 → 返回 / Grid → drive → cell → Back | 数据对应层级，返回路径正确 / Correct details at each level and back navigation |
| 7 | 填入已知数量的两种物品、不同 NBT 的同名物品、流体 / Insert known counts, same-name NBT variants and fluids | 数量与 AE2 对照一致，类型不按名称错误合并 / Exact counts; NBT types remain distinct |
| 8 | 搜索无结果、切换排序、多页翻页、切换设备 / Empty search, sort, page, switch devices | 无残留错误页码、内容或槽位 / No stale pages, contents or slots |
| 9 | 使用铁砧命名设备（设备支持时）后搜索 / Rename device if supported and search | 显示与搜索当前自定义名称 / Custom name displayed/searchable |

## 滚动与界面比例 / Scrolling and GUI scale

- 在含多页内容的网络中快速连续滚动、立即反向、使用大幅度和小数滚轮增量；目标应连续累加，较早的服务端响应不能把滚动位置拉回。
- 鼠标分别停在物品网格、滑轨和上下箭头上滚动，再拖动滑块到底部和顶部；均应能导航内容。搜索框和玩家背包上的滚轮不应滚动内容网格。
- 使用固定比例和“自动”比例打开、关闭界面及调整窗口大小；界面比例选项与实际渲染倍率均保持原版值。检查 1920×1080、1280×720、960×720 的内容、目录、元件槽和背包边界；窄屏切换目录后仍可操作元件。
- 输入搜索词、选中设备／元件并展开目录后调整窗口大小；查询、选择、目录状态和仍可见的搜索框焦点应保留。

Test rapid/reversed, large and fractional wheel input across the grid, track and arrows, plus dragging to both limits. Keep inventory/search wheel input separate. Open, close and resize at fixed and Auto GUI scales without changing either the option or native window scale. Check the three resolutions above, narrow tree/cell access, and preservation of search, selection, tree state and visible search focus.

## 元件一致性 / Cell integrity

1. 在驱动器和 ME 箱子中分别放入空元件和含有已知物品／流体的元件；从控制器取出并重新插入，检查 NBT、数量与 AE2 终端内容。
2. 在玩家物品栏满、鼠标持有物品、Shift 点击、快速连续点击等情况下操作，确认元件总数不变，未出现无效堆叠。
3. 尝试放入石头等非元件物品，应拒绝；退出界面和重新连接服务器后再次核对库存。
4. 两名玩家同时对同一个槽位取出同一个元件，预期只有一份元件，服务端与两个客户端一致。
5. 打开详情后，让另一名玩家移除设备、断开线缆或替换目标方块；旧界面不能操作失效设备或其他网络。
6. 保护领地禁止操作、受限玩家、跨维度设备与卸载区块分别测试。记录保护模组是否确实拦截远程操作，不能仅凭其已安装就判定权限安全。

Repeat these scenarios for both drives and ME Chests: empty/full cells, full player inventory, carried cursor stack, Shift-click and rapid clicks, invalid items, reconnecting, two players racing for one cell, device removal/replacement/disconnection, claim protection and unloaded targets. Verify exact total cell counts and NBT before/after. A protection mod's presence alone is not proof that remote operations are blocked.

## 内容存取 / Content transfers

- 在 ME网络、驱动器、单个元件与存储总线范围分别测试左键、右键、Shift、空白格存入及背包 Shift 存入，核对服务器、鼠标与背包的总数。
- 保留同名但不同 NBT 的物品，确认不会混为一类；取放满堆叠数为 1、16、64 的物品。
- 两名玩家同时提取最后一组物品，另一名玩家同时换芯或切断设备；确认没有复制、遗失或跨范围存取。
- 背包只剩部分堆叠空间、元件已满、断电、存储总线只读／只写／过滤拒绝时，确认剩余物品留在正确位置。
- 悬停物品和流体元件、普通背包物品及工具栏，确认只出现一个清晰提示框；拿着物品时不遮挡操作位置。

Test every scope and gesture with authoritative item totals, NBT-distinct and nonstandard stack-size items, concurrent players, replacement/disconnection, full inventories/cells, power loss and bus restrictions. Check single, readable tooltips for item/fluid cells, backpack items and controls.

## 容器交互 / Container interactions

- 在 ME网络、单设备和单元件范围，持空桶／部分填充容器左键流体条目；普通右键向资源格及空白格倒入。核对存储量、容器内容、容器数量、背包和鼠标，确认总量守恒。
- Shift 左键填充单个容器，分别测试背包空、仅剩部分堆叠空间和完全满；放不下的单容器结果应保留鼠标。另测堆叠容器，由原生策略处理的额外产物可能掉落，应将掉落实体计入总量，不能复制或丢失。
- 空手点击水或熔岩，测试当前范围有／无空桶；选择流体元件且桶仅在另一个物品元件时，不得跨范围借桶。借桶后填充失败，应退还原范围或保留鼠标。
- 倒空时使用不满一桶的存储剩余空间，填充时资源少于容器所需量；测试不同流体混装拒绝、无电、满元件、只读／只写总线与过滤拒绝，核对容器及资源不丢失，也不改存整个容器。
- 在可选 `-PmekTest` 环境中，用 Applied Mekanistics 注册的化学品及兼容容器，执行左键填充、普通右键倒空、Shift 填充和满背包测试。逐项记录气体等实际测试的类型、容器及精确单位，不由一个类型通过推断所有类型兼容。
- 每次转移量应对照容器原生速率，而非总容量；Mekanism 基础化学品储罐的预期为每次 1000 单位，不是一次 16000。逐项验证 gas、infuse、pigment 和 slurry，不将尚未执行的类型记为通过。 / Compare each transfer with native rate, not total capacity: expect 1000 units per operation for a Mekanism basic chemical tank, not 16000. Validate gas, infuse, pigment and slurry separately; leave unexecuted types pending.
- 使用没有注册 `ContainerItemStrategies` 的自定义资源，确认仍可浏览且拒绝不支持的容器交互；快速连续操作、另一玩家取走资源或替换元件时不得复制、丢失或跨范围转移。
- 悬停流体、化学品与元件时保持一份简洁提示框，不重新出现操作教学文字。升级两端至 0.4.6 后验证完整行为；协议6可连接旧版本不等于旧客户端具备新点击行为。

Repeat fill/empty operations on resource and empty tiles in network, device and cell scopes, tracking stored resources, container contents and every physical item. Test single-container Shift-fill with empty, partially available and full inventories; a result that cannot fit must remain on the cursor. Separately test stacked containers: additional strategy-produced items may drop, so include world entities in conservation checks. Test borrowed water/lava buckets with and without a bucket in the selected scope, including failed filling and a bucket available only elsewhere. Check partial capacity, insufficient resources, incompatible contents, power loss, filters and access modes; refusal must not store the entire container instead. With `-PmekTest`, record each actual chemical type, container and unit tested through Applied Mekanistics. Check unregistered resource types, rapid clicks, concurrent players and replaced cells. Confirm concise single tooltips and upgrade both sides to 0.4.6 for complete behavior.

## 容量与外部存储 / Capacity and external storage

- 1k、64k 和 256k 物品元件，以及流体元件；对照 AE2 原生工具提示核对已用字节、总字节、类型数。
- 部分装满的箱子和流体储罐，经存储总线连接；配置白名单、黑名单、只插入与只提取模式，核对实际可见内容。
- 同一外部容器接两条存储总线路径，检查全网数据与 AE2 终端一致；设备视图应理解为访问路径而不是唯一物理库存。
- 用存储总线连接子网络接口，确保没有递归枚举子网络设备或强制加载区块。
- 至少一种第三方大容量元件、未知容量元件、创造模式元件、虚空元件。不能读取容量时应显示未知，不溢出为负数或伪造精确百分比。
- 自定义存储类型存在时，验证显示、数量单位与搜索；记录未支持的细节，不将单个附属通过推广为所有附属兼容。

Compare standard item/fluid-cell capacity with AE2 tooltips. Test storage-bus filters and access modes, duplicate paths to a container, a subnet exposed through an interface, and third-party high-capacity/unknown/creative/void cells. Verify no recursive device enumeration, no forced chunk loading and no numeric overflow. Record each addon and custom storage type separately.

## 客户端与定位 / Client and highlighting

- 0.4.11 对照用户参考图，逐个比较全部7个图标；第3、5、7个使用对应原生符号，其余4个应与0.4.10一致。按钮背景、边框、间距、树面板与原生 GUI 比例保持原样。点击排序按钮两次，确认名称／数量排序实际切换、悬停提示反映当前状态，图标本身保持固定。 / Compare all seven icons with the supplied reference: only icons 3, 5 and 7 change. Preserve backgrounds, borders, spacing, tree panel and native GUI scale. Toggle sorting twice and verify actual name/quantity order and current-state tooltips while the icon stays fixed.
- 既有0.4.10按钮底板要求继续适用：按钮为16×16、20像素步距，相对整体面板的位置为x=3、y=9+20n（n=0…6）。使用原生背景，不能出现连续黑色底板、额外矩形投影或蓝色悬停填充；焦点显示1像素白色外框。检查浅深主题、原生自动比例与窄屏下边界和点击位置。
- 与已确认的0.4.9截图对照，存储树外框、右侧终端和其余主面板应保持一致；逐一验证七个按钮的原功能，尤其携带物品打开指南并返回时的物品守恒。

For 0.4.10, check only the seven-button sidebar: 16×16 buttons at 20-pixel pitch, x=3 and y=9+20n relative to the whole panel. Confirm native backgrounds, a one-pixel white focus outline, and removal of the continuous black backing, added rectangular shadows and blue hover fill. Check both themes, Auto scale, narrow windows and click bounds. Compare the tree border and other panels with the approved 0.4.9 captures; they should remain unchanged. Retest every button, including opening and closing the guide while carrying items.
- 悬停控制器物品并长按当前指南键（默认G），确认进入对应简中／英文页面；再改绑指南键检查使用新绑定。点击侧栏?应打开同一页。
- 手持物品点击?、浏览后按Esc返回，核对原菜单、范围和持物；下一次槽位点击仍正常，物品不能掉落或复制。

- 分别使用简体中文和英文、GUI 缩放 1–4、常见窗口尺寸。检查长设备名、物品名、维度名和大数量不遮挡关键控件。
- 点击同维度 256 格内已加载设备的定位，关闭界面查看高亮；确认约 15 秒后消失。
- 在正面、斜侧面和第三人称下移动／旋转镜头，确认线框始终贴合同一目标方块；分别检查驱动器与存储总线对应容器。定位无效新目标时，旧高亮应清除。
- 启用 AETexturesBackport1.20-1.1 后查看浅／深色主题、搜索框聚焦、侧边栏按钮和滚动条；关闭材质包并重新打开界面后，应保留内置现代美术。确认中文方块名和标题使用“ME存储控制器”“ME存储”。
- 测试超过 256 格、未加载、其他维度、目标已经破坏和已断开连接的设备；显示位置提示且不强制加载。
- 内容多于一页、很多设备、包含大 NBT 物品的网络中打开界面并持续更改库存；观察服务端 tick、客户端帧率、内存和网络日志。
- 关闭界面后确认扫描或数据同步不会无止境继续；退出并重进世界后无失效高亮或旧网络数据。

Test both languages, GUI scales 1–4, long names and large quantities. Verify loaded-nearby highlighting and its 15-second timeout, unavailable target messaging, and no force-loading. Use large inventories to inspect tick time, frame rate, memory and packet behavior, then close/reopen the screen and reconnect.

## 发布记录 / Release record

实际发布说明应区分：已编译、已静态校验、单人已测、专用服务器已测、特定附属已测。未执行的项目保留“待测”，不要填写为通过。

Release notes should distinguish compilation, static checks, single-player testing, dedicated-server testing and named addon testing. Leave unexecuted cases pending.
