# 0.4.16 自定义无限元件与搜索文字

- 保留ExtendedAE无限元件的资源记录，修复无限熔岩、无限物质球等元件在存储树中被当成默认水元件的问题。适用于同一实现的其他自定义资源，不依赖整合包或中文名称。
- 已知无限来源显示`∞`，容量显示`0 / ∞`；单资源元件类型显示`1 / 1`。网络、设备、元件与文件夹的标记只覆盖当前范围；同种资源的普通有限元件不会被误标。
- 同时识别AE2创造元件。OmniCells的无限容量仍与无限供应分开处理，有限的`Long.MAX_VALUE`数量不会变成无限。
- 右侧物品搜索框与左侧树搜索框输入文字去除阴影，与文件夹名称输入框共用绘制方式；保留原版光标、选择、横向滚动及现有AE背景和布局。

无限元件预览只保留有界资源记录与显示名称，不复制完整元件NBT、不重新创建库存、不改变实际存储。存入、取出及容器操作继续经过原有AE2挂载库存、范围校验与耗电流程。

协议更新为 **10**，客户端与服务器须同时更新，不能与0.4.15及更早版本连接；现有存档、文件夹和折叠状态的数据格式不变。

## 验证

- 构建及13项JUnit全部通过，发布JAR中的版本、必需类、许可证、指南和开发测试类排除检查通过。
- ExtendedAE **1.4.21**、用户整合包使用的 **1.4.18** 分别通过全部 **53项服务端GameTests**，同时加载OmniCells、Applied Mekanistics/Mekanism及Flux Networks；三项EAE专用新增测试均实际执行，没有跳过。
- 新增四项集成测试覆盖原生AE创造元件、无限熔岩／物质球、重复同资源无限元件、预览NBT精简、目录分帧与Snapshot传输、有限`Long.MAX_VALUE`不误标、网络／驱动器／单元件／文件夹范围，以及各12次熔岩桶与物质球取还。
- 完整客户端自测通过：使用用户提供的AE材质包，校验真实目录／内容包和中文“熔岩”输入；无限名称、∞、有限64个铁锭、搜索结果、光标与焦点正确。原有文件夹、连续点击、桶操作、滚动及960／1280／1920窗口的原生Auto比例检查通过。已逐张查看下列实机截图。
- 首次客户端运行在原版`NaturalSpawner`的JVM C2编译代码中发生本机访问异常；复测仅给测试进程添加`-XX:TieredStopAtLevel=1`，完整通过。该参数没有写入模组、开发脚本默认设置或用户整合包。原始日志保存在开发工作区`work/v0416-client.log`、`work/v0416-client-retry.log`；服务端日志为`work/v0416-gametests.log`和`work/v0416-eae18.log`。

实机截图：[网络汇总](screenshots/v0.4.16/smoke-infinite-network.png)、[无限熔岩元件](screenshots/v0.4.16/smoke-infinite-cell.png)、[无阴影中文搜索](screenshots/v0.4.16/smoke-infinite-search.png)。

识别依据为已知库存实现与当前真实挂载身份，不对未知模组猜测无限，也不递归检查独立子网络。预览资源记录限16KiB，过大的记录仅保留实际显示名称；实际元件与存储内容不受此显示限制影响。

ExtendedAE custom infinity cells now preserve their resource identity and show scoped infinite supply. Search text renders without a shadow while keeping native editing behavior. Protocol 10 requires matching client/server updates; saved world and folder formats are unchanged.
