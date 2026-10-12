# ME存储控制器

[![版本 0.4.17](https://img.shields.io/badge/version-0.4.17-8b6caf?style=flat-square)](CHANGELOG.md)
![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-5c9b45?style=flat-square)
![Forge](https://img.shields.io/badge/loader-Forge-476f95?style=flat-square)
![AE2 15.4.10](https://img.shields.io/badge/AE2-15.4.10-8b6caf?style=flat-square)
[![代码许可 MIT](https://img.shields.io/badge/code-MIT-3b8c78?style=flat-square)](LICENSE)
[![美术许可 CC BY-NC-SA 3.0](https://img.shields.io/badge/assets-CC_BY--NC--SA_3.0-b88444?style=flat-square)](docs/ASSETS.md)

[下载 0.4.17](https://github.com/sitbat/me-storage-controller/releases/tag/v0.4.17) · [使用指南](docs/USER_GUIDE.md) · [更新记录](CHANGELOG.md) · [开发指南](docs/DEVELOPMENT.md) · [English](README.en.md)

ME存储控制器是适用于 **Minecraft 1.20.1 Forge** 的 Applied Energistics 2 附属模组，用于查看ME网络中各设备和存储元件的容量、内容及资源数量，并在选定范围内存取资源。

## 功能

- **存储浏览**：以文件树展示网络、设备和存储元件，支持搜索、排序和逐级查看。
- **容量与数量**：容量按1024进位显示为B、KB、MB、GB、TB等单位；内容显示资源数量，区分未知容量、无限容量与无限供应。
- **共享文件夹**：支持多选归组、嵌套、重命名和汇总。同一网络的玩家共享分类；归组不会移动实际物品、方块或元件，单元件归属跟随槽位。
- **资源存取**：在网络、文件夹、设备或单元件范围内存取物品，使用兼容容器取放流体与化学品，通过实际元件槽取放完整存储元件。
- **界面与定位**：支持深浅主题、个人折叠状态记忆和设备高亮定位，使用Minecraft当前的界面比例。

## 安装与使用

开发与验证使用以下环境：

| 依赖 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.0 |
| Java | 17 |
| Applied Energistics 2 | 15.4.10 |
| GuideME（该AE2版本的依赖） | 20.1.7 |

1. 下载 [0.4.17 安装 JAR](https://github.com/sitbat/me-storage-controller/releases/download/v0.4.17/me-storage-controller-forge-1.20.1-0.4.17.jar)，与依赖的Forge版本一起放入实例的 `mods` 文件夹。多人游戏中，客户端和服务器安装相同版本的本模组。
2. 放置**ME存储控制器**，通过ME线缆连接已供电的网络，并留出**1个频道**。
3. 右键打开。物品上长按 **G**，或点击侧栏指南按钮，即可查看游戏内说明。

控制器不提供频道，也不替代AE2的ME控制器。存取遵循AE2原有的供电、过滤与权限规则。

[Release 页面](https://github.com/sitbat/me-storage-controller/releases/tag/v0.4.17)中的 **Source code (zip / tar.gz)** 由 GitHub 自动提供，不要放入 `mods`。JAR 的 SHA-256 可查看附件内置校验值。当前协议为10，与0.4.15及更早版本不兼容。

## 兼容性

| 附属 | 支持内容 |
| --- | --- |
| ExtendedAE | 扩展驱动器，以及无限熔岩、无限物质球等自定义无限元件 |
| AE2 Omni Cells | Omni、Complex Omni与Quantum元件的容量、类型和已知无限上限 |
| Applied Mekanistics + Mekanism | 化学品内容，以及AE2已注册策略支持的容器操作 |

上述附属均为可选。存储总线连接的容器可显示内容及接口提供的容量，存取保留总线的过滤与权限设置。具体范围与限制见[使用指南](docs/USER_GUIDE.md)，已完成的检查见[验证记录](docs/VALIDATION.md)。

## 构建

使用 **JDK 17**，在仓库根目录运行：

```powershell
.\gradlew.bat build
```

Linux或macOS使用 `./gradlew build`。安装包位于 `build/libs/`，选择不带 `-sources` 的JAR。

[文档目录](docs/README.md) · [开发与测试](docs/DEVELOPMENT.md) · [手动验收清单](docs/MANUAL_TEST_PLAN.md)

## 许可

原创代码、脚本和文档采用 [MIT](LICENSE)。收录的AE2美术、衍生模型及视觉改编采用 **CC BY-NC-SA 3.0**，详见[资源来源与署名](docs/ASSETS.md)。两者分开授权。

本项目为独立附属模组，不属于AE2官方项目。
