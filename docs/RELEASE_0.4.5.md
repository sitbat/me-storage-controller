# 0.4.5 自适应容量单位 / Adaptive capacity units

容量摘要按 1024 进位，自动选择 **B、KB、MB、GB、TB、PB、EB**，最多保留一位小数。沿用存储元件的 1024 换算，以用户要求的 KB/MB 等简写显示。接近下一单位时，舍入得到 1024 会自动晋级。未知容量仍显示未知，悬停提示仍显示精确字节数。

Capacity summaries automatically choose B, KB, MB, GB, TB, PB or EB using powers of 1024, with at most one decimal place. The requested labels use KB/MB rather than KiB/MiB; the underlying conversion is unchanged. Rounded values at 1024 promote to the next unit. Unknown capacities and exact-byte tooltips remain unchanged.

截图中的 `452231510 / 1618608128` 字节现在显示为 **431.3 MB / 1.5 GB**。容量与类型栏分别留出空间，必要时缩放文字，不再将容量尾部折叠成省略号。类型栏最多占 76 个逻辑像素，避免大类型数量挤出容量栏。

The reported `452231510 / 1618608128` bytes now display as **431.3 MB / 1.5 GB**. Capacity and type text use separate bounds and shrink to fit instead of truncating the capacity suffix. Type text uses at most 76 logical pixels, preserving room for capacity even with large type counts.

本次只改客户端文字显示；存储数据、容量计算、容器存取与协议 6 保持不变。此前存取检查见 [0.4.4](RELEASE_0.4.4.md)。

This only changes client text presentation. Storage data, capacity calculations, container transfers and protocol 6 are unchanged. See [0.4.4](RELEASE_0.4.4.md) for prior transfer validation.

## 验证 / Validation

离线 `gradlew build` 成功，现有 5 项单元测试通过，日志为 `work/v045-build.log`。未为这次文字格式修复新增测试或重跑完整游戏交互套件。

The offline `gradlew build` passed, including the existing five unit tests; see `work/v045-build.log`. No new tests or full in-game interaction rerun are added for this text-formatting change.
