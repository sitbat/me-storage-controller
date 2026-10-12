# 开发指南

本项目面向 Minecraft 1.20.1 Forge，使用 JDK 17。仓库自带 Gradle 8.8 Wrapper，无需另行安装 Gradle。构建依赖固定为 Forge 47.4.0、AE2 15.4.10 和 GuideME 20.1.7。

## 从源码构建

克隆仓库后，配置 `JAVA_HOME` 指向 JDK 17，并在仓库根目录执行：

```powershell
.\gradlew.bat build
```

Linux 和 macOS 使用 `./gradlew build`。首次构建需要联网下载依赖；只有本机已有完整缓存时才使用 `--offline`。

可安装的模组位于 `build/libs/me-storage-controller-forge-1.20.1-<版本>.jar`。将它放入游戏实例的 `mods` 目录，并安装匹配的 Forge、AE2 及其依赖；多人游戏的服务端和客户端都需安装同版控制器模组。文件名带 `-sources.jar` 的是源码归档，不能作为游戏模组安装。AE2、GuideME 和可选附属不会被打包进控制器 JAR。

版本号在 `gradle.properties` 的 `mod_version` 中维护，构建时自动写入模组元数据、Manifest 和 JAR 文件名。

## 开发脚本与测试

Windows 可使用 `scripts/dev.ps1` 统一运行构建和测试：

```powershell
.\scripts\dev.ps1 -Task Build
.\scripts\dev.ps1 -Task Test
.\scripts\dev.ps1 -Task TestServer
```

`Build` 执行 Gradle `build`，`Test` 运行 JUnit，`TestServer` 启动 Forge GameTests，默认使用独立目录 `run/gametest`。JUnit 报告位于 `build/reports/tests/test/index.html`。GameTests 应确认日志中的 `All ... required tests passed`；不能只凭 Gradle 退出码判断游戏内测试通过。

常用参数：

| 参数 | 用途 |
|---|---|
| `-JavaHome` | 指定 JDK 17 目录 |
| `-GradleHome` | 指定 Gradle 缓存目录 |
| `-RunDir` | 指定独立的游戏运行目录 |
| `-Offline` | 使用已有依赖缓存，禁止联网解析依赖 |
| `-DryRun` | 检查参数并显示命令，不启动构建或游戏 |

显式参数优先于环境变量和本地自动探测。`dev.ps1` 的相对路径以仓库根目录为基准；脚本结束后恢复调用者的工作目录与环境变量。独立克隆无需原作者的开发环境。

## 可选附属兼容测试

普通构建不需要附属模组。测试兼容功能时，将相应发行 JAR 放入本地 `compat-mods/`，通过以下开关启用：

| 脚本开关 | Gradle 属性 | 所需文件 |
|---|---|---|
| `-Eae` | `-PeaeTest` | `ExtendedAE-1.20-1.4.21-forge.jar`、`Glodium-1.20-1.5-forge.jar` |
| `-Mek` | `-PmekTest` | `Mekanism-1.20.1-10.4.16.80.jar`、`Applied-Mekanistics-1.4.3.jar` |
| `-Omni` | `-PomniTest` | `ae2omnicells-1.20.1-forge-1.1.6.jar` |

`-AllCompat` 同时启用上述三组。目录也可通过 `-CompatModsDir` 指定；直接使用 Gradle 时，对应参数为 `-PcompatModsDir=...`。

```powershell
.\scripts\dev.ps1 -Task TestServer -AllCompat -CompatModsDir compat-mods
```

ExtendedAE 默认使用 1.4.21。测试 1.4.18 时，准备对应的 `ExtendedAE-1.20-1.4.18-forge.jar`，再传入 `-Eae -EaeVersion 1.20-1.4.18-forge`；Gradle 对应属性为 `-PeaeVersion=1.20-1.4.18-forge`。

附属 JAR 不纳入 Git。未加载附属时，相关 GameTest 会跳过；测试总数不能单独证明可选兼容功能已验证。创建世界和后续测试应使用相同的附属配置。

## 客户端自测与演示

### Smoke：自动客户端验收

Smoke 需要一个已经创建的、名为 `SmokeTest` 的可丢弃世界。先启动普通开发客户端，创建世界并保存退出，再运行自测：

```powershell
.\gradlew.bat runClient -PrunDir=run/smoke
.\scripts\dev.ps1 -Task Smoke
```

需要测试附属时，两次启动均应带上对应开关；例如首次 Gradle 启动添加 `-PeaeTest -PmekTest -PomniTest`，随后脚本添加 `-AllCompat -CompatModsDir compat-mods`。

**Smoke 会重置玩家背包和测试区域，并在验收结束后自动关闭客户端。不要对正式游玩存档运行此模式。** 截图和结果默认写入 `build/client-smoke`，可通过 `-SmokeOutput` 指定输出目录。最终检查 `client-smoke-result.txt` 中的 PASS 或 FAILED，而不只看 Gradle 是否成功。

### Demo：保留现场的交互演示

Demo 不布置或重置测试区域，也不清空背包。先用普通开发客户端创建名为 `ME-Controller-Demo` 的世界，在 `(8, 100, 8)` 放置控制器，并接入有供电、可用频道的 ME 网络，然后保存退出：

```powershell
.\gradlew.bat runClient -PrunDir=run/demo
.\scripts\dev.ps1 -Task Demo -RunDir run/demo -DemoWorld ME-Controller-Demo
```

Demo 会将玩家切换为创造模式、开启飞行并传送到控制器附近，自动打开该控制器，随后保留客户端供手动操作。控制器缺失或离线时会记录 `ME_STORAGE_DEMO_UNAVAILABLE`。`-DemoWorld` 应填写运行目录下 `saves` 中的实际世界文件夹名。不要在同一个运行目录同时启动两个游戏实例。

### 文件夹跨重启验证

共享文件夹另有两进程验证入口，仅用于专用服务器测试世界：

```powershell
.\gradlew.bat runServer -PrunDir=run/folder-restart -PfolderRestart=seed
.\gradlew.bat runServer -PrunDir=run/folder-restart -PfolderRestart=verify
```

首次启动需按 Minecraft 提示处理该实例的 EULA。`seed` 会布置验证网络并保存退出；`verify` 从同一世界加载并检查，不重建网络。两阶段均应出现 `ME_STORAGE_FOLDER_RESTART_PASS phase=...`，运行目录必须相同。

## 本地打包

仓库目前没有 GitHub Release。以下脚本只生成本地分发文件，不创建 Release，也不上传任何文件：

```powershell
.\scripts\dev.ps1 -Task Build
python scripts/package-release.py --check-only
python scripts/package-release.py
```

需要 Python 3.10 或更新版本，以及 PATH 中的 Git；也可通过 `--git` 指定 Git 可执行文件。脚本使用标准库，先检查现有 JAR 的元数据、必需资源、许可和开发类排除，再将安装 JAR、源码 ZIP、`SHA256SUMS.txt` 写入 `dist/`。`--check-only` 只校验 JAR；`--output-dir` 可指定输出目录。Python 脚本显式传入的相对路径以调用时的工作目录为基准。

源码包默认收录 Git 已跟踪文件的当前工作区内容，包括尚未提交的修改。新增文件需先 `git add`；开发快照可使用 `--include-untracked` 收录未被忽略的新文件。打包前应确认源码与刚构建的 JAR 对应。分发时保留 JAR 内的许可与署名，详见[资源来源](ASSETS.md)和[许可索引](../licenses/README.md)。

## 源码导航

| 目录 | 内容 |
|---|---|
| `src/main/java/dev/mestorage/controller/block` | 控制器方块、方块实体和 AE 网络节点 |
| `src/main/java/dev/mestorage/controller/menu`、`network` | 服务端菜单、权限校验和同步协议 |
| `src/main/java/dev/mestorage/controller/storage`、`folder` | 存储适配、存取操作和共享文件夹 |
| `src/main/java/dev/mestorage/controller/client` | 界面、渲染和客户端交互 |
| `src/main/java/dev/mestorage/controller/test` | Forge GameTests 与专用验证入口 |
| `src/test/java` | JUnit 测试 |
| `src/main/resources` | 本地化、指南、模型、纹理和模组元数据 |

Forge GameTests 和 `ClientSmokeTest` 位于主源码集以便在开发游戏中运行，但安装 JAR 会排除它们及测试结构。`build/`、`run/`、`dist/`、`compat-mods/` 是本地生成目录，不纳入 Git。
