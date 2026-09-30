# 0.4.13 完整目录与按需供电 / Complete directory and demand-driven energy

存储树取消256设备、每设备256元件、总计4096元件的显示截断。新增默认关闭的服务端选项“绕过AE能量转换限制”，保留现有能源元件的实际缓存容量。界面布局、纹理、图标和Minecraft GUI比例沿用此前版本。

The storage tree no longer truncates at 256 devices, 256 cells per device or 4,096 cells overall. The optional server-side AE energy bypass defaults to off and retains existing energy-cell capacity. Layout, artwork, icons and Minecraft GUI scale remain unchanged.

## 配置与指令 / Configuration and commands

- 查询：`/mestorage energyBypass`
- 开启：`/mestorage energyBypass true`
- 关闭：`/mestorage energyBypass false`
- 权限等级：2；命令立即生效并保存到当前存档。
- 文件：`<world>/serverconfig/me-storage-controller-server.toml`，`[energy]` 下的 `bypassAeEnergyLimit`，默认 `false`。

文件修改支持热重载；本配置的重载与指令在服务器线程上串行执行，避免快速切换时磁盘保存值被旧重载覆盖。

Commands require permission level 2 and persist immediately to the active world's server configuration. File edits support hot reload on the server thread, serialized with commands so an earlier reload cannot overwrite a newly saved setting. A dedicated server owns its configuration.

## 行为 / Behavior

大型目录以每帧最多48KiB、每tick最多两帧同步。完整一代接收后替换旧目录，刷新期间保留原树和选择，旧代不会覆盖新代。长显示名称单独限制大小，不丢弃设备或元件；只绘制当前视口内的行。

Large directories stream in frames of at most 48 KiB, with at most two frames per tick. Complete generations replace the old tree atomically, preserving selection and preventing stale generations from reappearing. Oversized display text is bounded without omitting entries; rendering visits the visible rows.

能源旁路先使用现有AE缓存，再通过当前联网能源接收器补足真实外部供电。普通Forge Energy接口按其提取能力工作。Flux Networks **1.20.1-7.2.1.15** 无线点使用其已由原生调度送达的实际缓冲；需求窗口请求上游补给，不直接扣取远端储能，也不扩充AE能源元件容量。首次突发可能需等待数个Flux供电周期。原生优先级、源输出限制和无线点限额保持有效。禁用后立即停止旁路提取，保留已存储的能源。

Existing AE caches supply power first. Current acceptors can then obtain real external supply. Ordinary Forge Energy sources must support extraction. The Flux adapter consumes only energy already allocated to the adjacent point by native Flux scheduling, advertising recent real demand for replenishment. It does not directly debit remote stores or enlarge AE cells. A sudden burst can require several Flux cycles. Native priority and source/point limits remain effective. Disabling stops bypass extraction immediately without deleting stored energy.

Flux桥使用long数量，避免单次提取被人为截断到32位；AE自身仍使用双精度数值，不能将数学意义上的无穷或超出数值精度的数量解释为精确能源。其他仅推送而不支持提取的供电系统尚未专门适配。模拟不扣电，整FE转换的余量进入原有AE缓存；满缓存下极小的分数FE需求可能保守少报不足1FE。

Flux requests use long quantities rather than an artificial 32-bit cap. AE still uses double-precision amounts; mathematical infinity or exact values beyond numeric precision are not promised. Other push-only energy systems have no dedicated adapter. Simulation does not debit energy, and integral-FE rounding surplus uses existing AE storage. With a full cache, simulation of a fractional FE shortfall can conservatively under-report less than one FE.

网络协议升级为 **8**；客户端和服务端必须一起更新。Flux是可选兼容项，不是必装依赖。

Protocol is now **8**; update both client and server. Flux remains optional.

## 验证 / Validation

验收环境为 Java17、Forge47.4.0、AE2 15.4.10、GuideME20.1.7，并加载 ExtendedAE、Mekanism／Applied Mekanistics、OmniCells 与 Flux Networks 的实际发行包。

The validation environment uses Java 17, Forge 47.4.0, AE2 15.4.10 and GuideME 20.1.7, with actual ExtendedAE, Mekanism/Applied Mekanistics, OmniCells and Flux Networks releases.

| 检查 / Check | 结果 / Result |
|---|---|
| 最终服务端集成 / Final server integration | 43/43 passed |
| JUnit单元测试 / Unit tests | 9/9 passed |
| 构建 / Build | `BUILD SUCCESSFUL`; development tests excluded from release JAR |
| 不安装Flux / Without Flux | 39实际执行通过，4项Flux测试明确跳过；正常启动与关服 / 39 executed passes, 4 explicit Flux skips; clean startup/shutdown |

新增验收覆盖：

- 完整目录：真实AE网格节点搭配附属宿主测试夹具，共260设备、4,656元件，单设备512槽；能选择第512槽并读到实际内容。帧大小、超长UTF-8名称、旧代／乱序／重复帧都有断言。这不是260台真实ExtendedAE驱动器的性能基准。
- 能源：保留原AE缓存容量、断电、禁用、模拟不扣电、能量守恒、共享电源去重、接收器移除及来源限额。
- Flux：真实Plug／Point和有限FE电源，连续8tick各提供200,000FE，Point限额200,128FE包含网络闲置耗电余量；不是跳过原生调度的模拟。单次3,000,000,000FE提取也通过。普通Point不因开启选项改变请求；近期AE需求停止后会自然恢复原生请求。
- 配置：权限0／1拒绝、权限2查询与修改；32次快速交替命令，每次核对实际TOML；真实文件双向热重载、服务器线程Reloading事件、非法值恢复默认及正常停服。

New coverage includes 260 real AE-managed test hosts with 4,656 cells and a 512-slot host, bounded directory framing, energy conservation and cache retention, real Flux scheduling for eight consecutive 200,000-FE ticks, a 3-billion-FE extraction, unaffected ordinary Points, expiring demand, command permissions, 32 rapid persistent changes, actual file reloads, invalid-value correction and clean shutdown.

客户端界面冒烟也已通过，包括存储树、快速滚动、96次快速元件点击、容器操作、OmniCells内容、指南，以及多种窗口／自动比例。截图见 [展开的存储树](screenshots/v0.4.13/smoke-tree-expanded.png) 和 [1280自动比例](screenshots/v0.4.13/smoke-auto1280-cell20.png)。界面测试后仅能源与配置实现继续修正，最终修正在上面的服务端回归中验证。

The client UI smoke test passed tree navigation, scrolling, 96 rapid cell clicks, container transfers, OmniCells, the guide and multiple window/GUI-scale settings. Only energy/configuration implementation changed afterward; the final changes passed the server regression above.

本地日志：`work/v0413-release-tested.log`、`work/v0413-no-flux-validated.log`、`work/v0413-client-build.log`。Flux发行包SHA-256：`8d24f39b6e1b2d83426a28a62ae0b65294b7e6e925e39a26b62f8ae077f54a89`。未修改用户实际整合包或存档。

Local logs are listed above. The user's actual modpack and saves were not modified.
