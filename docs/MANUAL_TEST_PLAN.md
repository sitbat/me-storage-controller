# 手动验收计划 / Manual acceptance plan

以下项目是**待执行的验收步骤**，不是已通过的测试记录。执行人应记录日期、环境、实际结果和日志。静态检查、编译和资源校验无法代替游戏内验证。

本次实际完成的自动化与客户端检查另见 [TEST_REPORT.md](TEST_REPORT.md)；这里保留完整的后续人工验收清单。

The following are **acceptance procedures to execute**, not passed test results. Record the date, environment, observed results and logs. Static checks, compilation and resource validation do not replace gameplay testing.

## 环境 / Environments

| 环境 / Environment | 基线 / Baseline | 结果 / Result |
|---|---|---|
| 单人开发环境 / Development single-player | Java 17, Forge 47.4.0, AE2 15.4.10, GuideME 20.1.7 | 待测 / Pending |
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

## 元件一致性 / Cell integrity

1. 在驱动器和 ME 箱子中分别放入空元件和含有已知物品／流体的元件；从控制器取出并重新插入，检查 NBT、数量与 AE2 终端内容。
2. 在玩家物品栏满、鼠标持有物品、Shift 点击、快速连续点击等情况下操作，确认元件总数不变，未出现无效堆叠。
3. 尝试放入石头等非元件物品，应拒绝；退出界面和重新连接服务器后再次核对库存。
4. 两名玩家同时对同一个槽位取出同一个元件，预期只有一份元件，服务端与两个客户端一致。
5. 打开详情后，让另一名玩家移除设备、断开线缆或替换目标方块；旧界面不能操作失效设备或其他网络。
6. 保护领地禁止操作、受限玩家、跨维度设备与卸载区块分别测试。记录保护模组是否确实拦截远程操作，不能仅凭其已安装就判定权限安全。

Repeat these scenarios for both drives and ME Chests: empty/full cells, full player inventory, carried cursor stack, Shift-click and rapid clicks, invalid items, reconnecting, two players racing for one cell, device removal/replacement/disconnection, claim protection and unloaded targets. Verify exact total cell counts and NBT before/after. A protection mod's presence alone is not proof that remote operations are blocked.

## 容量与外部存储 / Capacity and external storage

- 1k、64k 和 256k 物品元件，以及流体元件；对照 AE2 原生工具提示核对已用字节、总字节、类型数。
- 部分装满的箱子和流体储罐，经存储总线连接；配置白名单、黑名单、只插入与只提取模式，核对实际可见内容。
- 同一外部容器接两条存储总线路径，检查全网数据与 AE2 终端一致；设备视图应理解为访问路径而不是唯一物理库存。
- 用存储总线连接子网络接口，确保没有递归枚举子网络设备或强制加载区块。
- 至少一种第三方大容量元件、未知容量元件、创造模式元件、虚空元件。不能读取容量时应显示未知，不溢出为负数或伪造精确百分比。
- 自定义存储类型存在时，验证显示、数量单位与搜索；记录未支持的细节，不将单个附属通过推广为所有附属兼容。

Compare standard item/fluid-cell capacity with AE2 tooltips. Test storage-bus filters and access modes, duplicate paths to a container, a subnet exposed through an interface, and third-party high-capacity/unknown/creative/void cells. Verify no recursive device enumeration, no forced chunk loading and no numeric overflow. Record each addon and custom storage type separately.

## 客户端与定位 / Client and highlighting

- 分别使用简体中文和英文、GUI 缩放 1–4、常见窗口尺寸。检查长设备名、物品名、维度名和大数量不遮挡关键控件。
- 点击同维度 256 格内已加载设备的定位，关闭界面查看高亮；确认约 15 秒后消失。
- 测试超过 256 格、未加载、其他维度、目标已经破坏和已断开连接的设备；显示位置提示且不强制加载。
- 内容多于一页、很多设备、包含大 NBT 物品的网络中打开界面并持续更改库存；观察服务端 tick、客户端帧率、内存和网络日志。
- 关闭界面后确认扫描或数据同步不会无止境继续；退出并重进世界后无失效高亮或旧网络数据。

Test both languages, GUI scales 1–4, long names and large quantities. Verify loaded-nearby highlighting and its 15-second timeout, unavailable target messaging, and no force-loading. Use large inventories to inspect tick time, frame rate, memory and packet behavior, then close/reopen the screen and reconnect.

## 发布记录 / Release record

实际发布说明应区分：已编译、已静态校验、单人已测、专用服务器已测、特定附属已测。未执行的项目保留“待测”，不要填写为通过。

Release notes should distinguish compilation, static checks, single-player testing, dedicated-server testing and named addon testing. Leave unexecuted cases pending.
