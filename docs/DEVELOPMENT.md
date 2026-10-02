# 开发与维护

本项目运行于 Minecraft 1.20.1、Forge 47.4.0 和 JDK 17。Gradle Wrapper 固定为 8.8，无需安装全局 Gradle。源码入口为 `src/main/java/dev/mestorage/controller/`。

## 目录

| 路径 | 用途 |
|---|---|
| `src/main/java/.../block` | 控制器方块与网络节点 |
| `src/main/java/.../menu`、`network` | 服务端菜单、存取校验和同步协议 |
| `src/main/java/.../storage`、`folder` | 存储适配与共享分类数据 |
| `src/main/java/.../client` | 游戏内界面、渲染与个人界面状态 |
| `src/main/java/.../test` | 需要 Forge 启动的开发集成测试，发布 JAR 排除 |
| `src/test/java` | JUnit 单元测试 |
| `src/main/resources` | 本地化、指南、模型、纹理与配置元数据 |
| `docs` | 使用说明、维护说明、各版本验证记录与截图 |
| `scripts` | 通用开发和发布入口 |
| `build`、`run`、`dist`、`compat-mods` | 本机生成文件或依赖，不纳入 Git |

`ClientSmokeTest` 也位于主源码集，因为它在开发客户端内运行；发布任务明确排除该类和开发 GameTest 类。不要把排除规则删掉。测试存档、第三方 JAR 和缓存不随源码分发。

## 构建

在仓库根目录执行：

```powershell
# Windows；已有环境下复用缓存，无需每次填写本机路径。
.\scripts\dev.ps1 -Task Build -Offline
.\scripts\dev.ps1 -Task Test -Offline

# 直接使用 Wrapper（Linux/macOS 使用 ./gradlew）。
.\gradlew.bat build
```

首次下载依赖时去掉 `-Offline`。脚本优先采用 `-JavaHome`／`-GradleHome` 参数，其次采用 `JAVA_HOME`／`GRADLE_USER_HOME`；在原开发工作区内还可复用 `../../work/jdk17` 与 `../../work/gradle-home`。独立克隆只需配置 JDK 17，不依赖该工作区结构。

`dev.ps1` 的路径参数均相对仓库根目录解析，支持绝对路径。`-DryRun` 只检查参数并列出命令，不启动游戏或构建。脚本退出时恢复调用者的工作目录与环境变量。

版本号的唯一构建来源为 `gradle.properties` 的 `mod_version`。`processResources` 自动将版本写入 `META-INF/mods.toml`，JAR 文件名和 Manifest 使用同一版本。修改版本时仍须更新面向用户的说明与发布记录。

## 集成验证

```powershell
.\scripts\dev.ps1 -Task TestServer -Offline
.\scripts\dev.ps1 -Task TestServer -AllCompat -Offline
```

GameTests 默认使用隔离目录 `run/gametest`，也可通过 `-RunDir` 指定。必须核对日志中的 `All ... required tests passed`，不能只看 Gradle 退出码。部分启动失败或客户端自测失败可能仍返回 Gradle 成功。

可选依赖通过 `-Eae`、`-Mek`、`-Omni`、`-Flux` 或 `-AllCompat` 启用。`-CompatModsDir` 指定发行 JAR 所在目录；不指定时脚本先检查原工作区的 `../../work/vendor`，否则使用仓库的 `compat-mods/`。直接调用 Gradle 时使用 `-PeaeTest` 等开关，并通过 `-PcompatModsDir=...` 指定路径（默认 `compat-mods/`）。

| 开关 | 验证过的发行包文件名 |
|---|---|
| Eae | `ExtendedAE-1.20-1.4.21-forge.jar`、`Glodium-1.20-1.5-forge.jar` |
| Mek | `Mekanism-1.20.1-10.4.16.80.jar`、`Applied-Mekanistics-1.4.3.jar` |
| Omni | `ae2omnicells-1.20.1-forge-1.1.6.jar` |
| Flux | `FluxNetworks-1.20.1-7.2.1.15.jar` |

这些包不纳入仓库，也不打进发布 JAR。未安装附属时相关 GameTest 会明确跳过，因此总测试数不能直接理解为所有可选兼容都已验证。

## 客户端自测与演示

两种模式均需要现成存档。先用普通开发客户端创建对应名称的测试世界，退出后再运行脚本；或指定已有的独立测试实例。

```powershell
# 普通客户端：创建名为 SmokeTest 的专用测试世界，保存退出。
.\gradlew.bat runClient -PrunDir=run/smoke
.\scripts\dev.ps1 -Task Smoke -AllCompat -Offline

# 交互演示：指定已布置好控制器的世界，不执行自测重置。
.\scripts\dev.ps1 -Task Demo -RunDir run/demo -DemoWorld ME-Controller-Demo -Offline
```

**Smoke 模式只用于可丢弃的测试世界**：它会重置玩家背包与测试区域，并在验收结束后关闭客户端。截图和结果默认写入 `build/client-smoke`，可用 `-SmokeOutput` 指定；最终必须检查 `client-smoke-result.txt` 的 PASS／FAILED。Demo 模式尝试打开坐标 `8, 100, 8` 的已联网控制器，留下窗口供手动操作；没有准备好方块时会记录 `ME_STORAGE_DEMO_UNAVAILABLE`。

原工作区的交互演示保存在 `../../work/run-v0414-demo`，世界名为 `SmokeTest`：

```powershell
.\scripts\dev.ps1 -Task Demo -RunDir ../../work/run-v0414-demo -DemoWorld SmokeTest -AllCompat -Offline
```

此处使用 Demo 模式保留玩家在演示世界中的修改。不要在同一运行目录同时启动两个 Minecraft 实例。

文件夹真实跨进程验证使用独立服务器目录，依次执行 `runServer -PfolderRestart=seed` 与 `runServer -PfolderRestart=verify`；两次 `-PrunDir` 必须相同。仅用于专用开发世界：seed 会布置固定坐标的验证网络，verify 只加载并核对。成功标记为 `ME_STORAGE_FOLDER_RESTART_PASS phase=...`，两阶段都会正常保存并退出。初次启动服务器需按 Minecraft 提示配置该实例的 EULA。

## 发布打包

```powershell
.\scripts\dev.ps1 -Task Build -Offline
python scripts/package-release.py --check-only
python scripts/package-release.py
```

Python 3.10 或更新版本即可，脚本仅使用标准库；Git 需在 PATH 上，或通过 `--git` 指定。默认输出到仓库的 `dist/`，可用 `--output-dir` 改位置；Python 脚本显式传入的相对路径按调用时的工作目录解析。脚本检查发布 JAR 的元数据、必需类、资源许可及开发类排除，再生成 JAR、源码 ZIP 和 `SHA256SUMS.txt`。

源码包默认只收录 Git 已跟踪文件的当前工作区内容，新增文件应先 `git add`，不要求先提交。开发快照可显式使用 `--include-untracked` 收录未被忽略的新文件。构建目录、运行数据和发布输出不进入源码包。`--help` 列出全部选项。

版本历史与原始验收结果见 [文档索引](README.md)。历史记录中的版本专用命令和本机日志路径用于追溯，不应替代这里的通用入口。
