# 0.4.17 恢复AE2原有供电逻辑 / Restore native AE2 energy behavior

移除“绕过AE能量转换限制”功能及其配置、命令和专用兼容处理。能源接收、缓存、转换与供电调度恢复使用AE2和供电模组原有逻辑。

同时移除该功能的中英文说明、游戏内指南条目和专用测试支持。开发环境的 `-AllCompat` 仅包含ExtendedAE、Mekanism／Applied Mekanistics及OmniCells。保留既有存储树、共享文件夹、物品与容器存取和无限元件显示。

旧存档中的 `serverconfig/me-storage-controller-server.toml` 不再注册或读取，即使保留旧的开启值也不会启用旁路；无需手动删除文件。能源元件按AE2原有规则继续缓存AE能量。

网络协议仍为 **10**，与0.4.16相同。历史发布记录保留原文，用于追溯当时功能与验证范围。

## 验证 / Validation

- Java 17、Forge 47.4.0、AE2 15.4.10环境下，`clean build`及全部13项JUnit通过。
- 同时加载ExtendedAE 1.4.21、Glodium 1.5、Mekanism 10.4.16.80、Applied Mekanistics 1.4.3和OmniCells 1.1.6，全部42项服务端GameTests通过，可选兼容项没有跳过。删除11项仅验证已移除功能的旧测试。
- 加强原有真实AE能源元件测试：存取消耗AE能量，缓存耗尽后拒绝取出和存入，重新充能后恢复操作；光标、目标元件及其他元件的数量守恒。
- 发布检查禁止旁路实现类、Mixin配置及Manifest注册残留，并保留资源许可、指南、版本和测试类排除检查。
- 本轮没有修改界面绘制，未重跑客户端界面验收。历史界面实测见[0.4.16](RELEASE_0.4.16.md)。

原始日志位于开发工作区 `work/v0417-build.log` 和 `work/v0417-gametests.log`。服务端最终记录为 `All 42 required tests passed`；测试中的空能力异常为既有容错测试主动构造。

## English

Removes the AE energy-limit bypass, its configuration and commands, and dedicated compatibility handling. Energy reception, buffering, conversion and scheduling use the original behavior of AE2 and the supplying mods.

The associated documentation, in-game guide entries and dedicated test support are removed. Development `-AllCompat` now includes only ExtendedAE, Mekanism/Applied Mekanistics and OmniCells. Storage browsing, shared folders, item/container transfers and infinity-cell display are retained. Protocol **10** is unchanged from 0.4.16.

A clean build, all 13 unit tests and all 42 server GameTests passed with the optional addons listed above. The native-power test now also verifies rejected insertion on an empty cache and successful transfers after recharging without item loss. Release validation rejects leftover bypass classes or mixin registration. Client UI checks were not rerun; this change does not alter rendering. Legacy server configuration files are no longer read and cannot reactivate the removed feature.
