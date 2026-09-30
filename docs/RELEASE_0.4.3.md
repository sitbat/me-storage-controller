# 0.4.3 精简提示框与图标对齐 / Concise tooltips and icon alignment

移除物品、流体和存储元件提示框中的额外操作说明，也不再对空白格、标题或详情区域重复提示鼠标操作。保留 AE2 原生物品说明、内容预览、精确数量和元件槽位编号。操作方式与 0.4.2 相同。

Instructional text is removed from item, fluid and cell tooltips, including redundant guidance on empty tiles, titles and detail panels. Native AE2 information, content previews, exact amounts and cell-slot numbers remain. Transfers behave as in 0.4.2.

对照官方 AE2 1.21.1 `IconButton`：左侧按钮背景为 18×20，图标为 16×16，原绘制没有横向压缩。原版图标相对背景偏移为 `(1,1)`，本模组之前居中计算为 `(1,2)`；本版将主栏图标上移 1 像素，保持原始宽高比例与像素尺寸。

Official AE2 1.21.1 uses an 18×20 button background and a 16×16 icon. The old rendering did not compress icon width; its centering placed the glyph at `(1,2)` instead of AE's `(1,1)`. Main sidebar glyphs now move up one pixel without stretching.

网络协议仍为 **6**，与 0.4.2 协议兼容。本次只改客户端表现，未更改存储、权限、网络包或元件存取逻辑。旧版本详细存取验收见 [0.4.2 记录](RELEASE_0.4.2.md)。

Protocol remains **6**, compatible with 0.4.2. This is a client presentation change; storage, access checks, packets and transfers are unchanged. See the [0.4.2 record](RELEASE_0.4.2.md) for detailed transfer validation.

## 验证 / Validation

最终 `runClient build -PsmokeTest -PeaeTest` 离线构建成功，5 项单元测试通过。现有客户端烟测在隔离存档 `work/run-v043/saves/SmokeTest` 中通过，包括物品存取、24 次快速内容点击、96 次快速元件点击、ExtendedAE 第 20 槽，以及 1280×720 / 1920×1080 自动界面缩放。正在运行的演示存档未修改。

The final offline client run and build passed, including all five unit tests. Existing client checks passed for item transfers, 24 rapid content clicks, 96 rapid cell clicks, ExtendedAE slot twenty and Auto GUI scaling at 1280×720 / 1920×1080. The running demo world was not modified. Server GameTests were not rerun for this presentation-only update; their previous results are recorded in 0.4.2.

已人工查看游戏内物品与流体元件提示框截图，确认仅显示一份原生提示框、槽位信息，操作说明已移除，左侧图标保持原始像素比例。

In-game item-cell and fluid-cell captures were visually checked: each shows a single native tooltip with slot information, without the removed instructions. Sidebar icons retain their original pixel proportions.

- [物品元件提示框 / Item-cell tooltip](screenshots/v0.4.3/smoke-cell-tooltip.png)
- [流体元件提示框 / Fluid-cell tooltip](screenshots/v0.4.3/smoke-fluid-cell-tooltip.png)
- [1920×1080 自动缩放 / Auto GUI scaling](screenshots/v0.4.3/smoke-auto1920-network.png)
