# 0.4.11 侧栏图标修正 / Sidebar icon correction

用户要求七个侧栏图标与提供的参考图一致。本次只替换**第3、第5、第7个图标**，使用对应的原生符号；其余四个已一致，保持原样。按钮功能保留原位置。

The seven sidebar icons should match the supplied reference. This release replaces only **icons 3, 5 and 7** with the corresponding native symbols. The other four already match and remain unchanged. Every button retains its existing function and position.

排序按钮固定使用参考图中的图标。点击仍会在名称和数量排序之间切换，悬停提示继续显示当前排序状态；图标不再随排序模式更换。

The sort button uses a fixed reference icon. Clicking still toggles name and quantity sorting, and the tooltip still reports the current mode; the icon no longer changes with that mode.

存储树、按钮背景与边框、间距、游戏内指南、存取操作和原生GUI比例均不变。[0.4.10记录](RELEASE_0.4.10.md)及此前版本记录保留。运行版本仍为 **Minecraft 1.20.1 Forge**，网络协议仍为 **6**。

The storage tree, button backgrounds and borders, spacing, guide, transfers and native GUI scale are unchanged. The [0.4.10 record](RELEASE_0.4.10.md) and earlier release records are preserved. The runtime remains **Minecraft 1.20.1 Forge**, with protocol **6**.

## 验证状态 / Validation status

2026-09-30，隔离目录 `work/run-v0411` 下的完整客户端回归和发行构建通过；`work/v0411-client-build.log` 包含 `ME_STORAGE_SMOKE_RESULT PASS` 与 `BUILD SUCCESSFUL`。启用用户资源包、ExtendedAE、Mekanism 和 Applied Mekanistics，七项单元测试通过。

The complete client regression, release build and seven unit tests passed on September 30, 2026, in the isolated directory with the supplied pack and existing addon dependencies. The log contains explicit success markers.

| 项目 / Check | 状态 / Status |
|---|---|
| 全部七个图标与参考图像素对照 / All seven icons versus the reference | 通过，比例2、3、4 / Passed at scales 2, 3 and 4 |
| 排序实际结果与固定图标 / Sorting result and fixed icon | 通过；提示文本路径另作源码核对 / Passed; tooltip paths reviewed in source |
| 既有面板、间距与原生GUI比例 / Preserved panels, spacing and native GUI scale | 通过 / Passed |
| 完整客户端回归与发行构建 / Full client regression and release build | 通过 / Passed |

参考图中的七个符号依次对应 `states.png` 的 `(176,0)`、`(0,64)`、`(32,16)`、`(160,16)`、`(0,48)`、`(32,64)`、`(48,208)`。三块替换区域在用户资源包与内置美术中逐像素相同，无新增PNG。

The seven reference glyphs use the atlas coordinates listed above. All three replacement regions match between the supplied resource pack and bundled artwork; no PNG was added.

像素核对使用三个实际游戏截图，逐一比对七个完整按钮的16×16逻辑像素，均与参考图相等。截图选中元件，使返回按钮处于可用状态；全网视图中该按钮仍按原行为禁用淡化。名称排序实拍中第三个图标仍保持参考符号。

Three actual gameplay captures verify all seven complete 16×16 logical-pixel buttons against the supplied reference. A cell is selected so Back is active; the network root retains its normal disabled appearance. The name-sort capture confirms that the third icon stays fixed.

- [比例2 / Scale 2](screenshots/v0.4.11/smoke-light-expanded-cell20.png)
- [1280自动比例3 / 1280 Auto scale 3](screenshots/v0.4.11/smoke-auto1280-cell20.png)
- [1920自动比例4 / 1920 Auto scale 4](screenshots/v0.4.11/smoke-auto1920-inventory.png)

```powershell
.\gradlew.bat runClient build '-PrunDir=../../work/run-v0411' -PsmokeTest -PeaeTest -PmekTest
```

烟测仅使用可丢弃的 `saves/SmokeTest` 存档副本。

Use only a disposable `saves/SmokeTest` copy for the smoke run.
