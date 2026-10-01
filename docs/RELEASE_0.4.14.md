# 0.4.14 共享文件夹与折叠记忆 / Shared folders and tree memory

本次增加存储树整理功能，沿用既有界面布局、材质和 Minecraft GUI 比例。

## 使用

- 关闭控制器界面后，下次打开恢复存储树的展开／折叠状态。状态按玩家、世界／服务器和控制器位置保存在客户端。
- Ctrl 点击多选，Shift 点击连续选择；右键打开菜单，可将所选设备、存储元件槽位或文件夹归入新文件夹或现有文件夹。
- 支持命名、重命名、多层嵌套、移出和删除。删除文件夹会把成员与下层文件夹提升到上一层。
- 文件夹由同一 ME 网络的玩家共用。建立、移动、改名和删除文件夹只修改分类元数据，不搬动物品、方块或元件，不修改优先级。
- 点击文件夹汇总下层成员的容量与内容；主动存入／取出时仅访问这些成员。整台设备与其中槽位重叠时，汇总只计算一次。
- 单个元件的归属记录在驱动器槽位上；取走元件后保留分组，新换入的元件自动属于该槽位的文件夹。

## 数据与边界

共享数据存于世界的 `data/me_storage_controller_folders.dat`，AE 节点仅保存引用。相同目录版本复用不可变快照，避免每个节点重复保存整份文件夹目录。网络分开后分别编辑；重新合网时合并记录，删除标记防止离线旧记录复活。

成员离线、被拆除或不属于当前网络时，引用保留，该文件夹暂不允许存取。已知共用同一物理容器的总线，以及无法证明范围独立的 ME 转发总线组合，会拒绝聚合操作。用户可继续单独选择设备使用。服务端保留原有设备交互保护、挂载过滤与存取规则。

文件夹编辑采用修订校验；其他玩家改动后，旧范围的操作不能访问已经移出的成员。分帧同步完整目录，不把半份目录应用到界面。创建限制为 256 个文件夹、8,192 个归属引用，最多 32 层；达到限制时明确拒绝编辑，不隐藏现有条目。

客户端与服务端均需更新至 0.4.14（协议 **9**）。

## 验证

验收环境：Java 17、Forge 47.4.0、AE2 15.4.10、GuideME 20.1.7，及实际 ExtendedAE、Mekanism／Applied Mekanistics、OmniCells、Flux Networks 发行包。

- 服务端集成：49/49 通过，包括六项文件夹测试组，以及原有目录、内容、容器、兼容和能源回归。
- 单元测试：13/13 通过，包括本地折叠文件往返、状态隔离、错误文件处理及异步目录恢复。
- 真实重启：两个独立服务端进程先保存并退出，再加载原世界；目录 UUID、中文名称、父子层级、槽位引用、完整元件 NBT 和范围内容全部一致。新网络修订从 4 增至 8。仅安装 AE2／GuideME 也正常启动、保存和关服。
- 客户端：完整实机验收通过。真实点击验证折叠记忆、多选、嵌套、重命名、移动、删除、持物打开菜单、范围内取还及背包 Shift 存回。原有滚轮、搜索、容器、OmniCells、96 次快速元件点击、多种窗口及自动比例也通过。
- 构建通过；开发测试类不包含在发布 JAR 内。

文件夹测试核对实际元件完整 NBT，覆盖共享编辑、嵌套汇总去重、物品与水桶存取、槽位更换、权限、旧修订、实际拆线分网及重连、删除标记、SavedData 去重和分帧协议。积压编辑帧超过处理限额时明确返回错误，并忽略该请求尾帧，避免界面一直等待。

实机截图：[右键归组](screenshots/v0.4.14/smoke-folder-context.png)、[新建文件夹](screenshots/v0.4.14/smoke-folder-create.png)、[嵌套汇总](screenshots/v0.4.14/smoke-folder-nested-aggregate.png)、[恢复折叠状态](screenshots/v0.4.14/smoke-tree-restored-collapse.png)。五张文件夹操作截图已逐张检查，未见文字重叠或控件越界。长树节点沿用省略号及完整路径提示。

本地日志：`work/v0414-release-tested.log`、`work/v0414-client-2.log`、`work/v0414-restart-seed.log`、`work/v0414-restart-verify.log`。客户端验收后仅追加了积压帧错误反馈处理，该路径在最终服务端回归中验证。测试使用隔离存档，未修改用户实际整合包或存档。

## English

Storage folders organize metadata only; folder management never relocates blocks, cells or stored resources. Ctrl/Shift selection and the context menu support shared nested folders, renaming, moving and deletion. Clicking a folder aggregates its descendants and limits deliberate transfers to that scope. Individual cell membership follows a physical slot. Expansion state is personal and stored locally per world/server and controller.

Network records survive split/merge through versioned world SavedData and node references. Revision checks reject stale edits and stale transfer scopes. Offline members retain their references and make the affected folder unavailable for transfers. Known overlapping storage-bus targets and ambiguous ME forwarding combinations are rejected. Client and server must both use protocol 9.

Server integration: 49 passed. Unit tests: 13 passed. The complete client smoke test passed real folder management, scoped transfers, collapse restoration and the existing compatibility/layout checks. A separate two-process dedicated-server probe passed actual world save/reload, preserving folder identity, hierarchy, slot references and complete cell NBT. Only the queued-frame error response changed after client validation; it is covered by the final server regression. Screenshots were inspected for clipping and overlap.
