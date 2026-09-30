# Visual assets / 视觉资源

The 0.4.0 controller uses an original generated texture atlas at `assets/me_storage_controller/textures/block/controller_atlas.png`. It was created with the built-in imagegen tool for this project, then resized with nearest-neighbor sampling to a 512×512 power-of-two atlas for Minecraft. Its quadrants are front, side, top and rear. No AE2 texture files are copied into the release.

0.4.0 控制器使用本项目通过 imagegen 制作的原创图集，按最近邻采样整理为适合 Minecraft 的 512×512 图集。四个区域分别对应正面、侧面、顶部和背面；发行包未复制 AE2 的贴图文件。

The JSON model is authored for the project: the front is recessed one model unit behind four structural rails. The painted tree/storage symbols identify the block; live capacity values appear in the interactive screen. A separate narrow strip along the bottom edge reflects the actual AE2 node state. It pulses gently only while online, and the block emits light level 4. Losing power or its channel disables the strip and emitted light.

模型采用独立制作的 JSON 几何：四条框架包围下沉一格模型单位的前面板。树与存储条纹是设备标识，实时容量在交互界面中显示。底部独立窄灯条反映 AE2 节点真实状态，在线时缓慢呼吸并提供 4 级方块光照；掉电或失去频道时停止并熄灭。

The screen uses original drawing code. AE2 1.21.1's terminal conventions are a reference for item slots, resource information and restrained cool accents, while tree navigation, panels and animation are this addon's design.

界面由原创绘制代码实现。物品槽位、存储信息与克制的冷色点缀参考 AE2 1.21.1 的终端习惯，文件树、布局与过渡动画为本模组设计。

Reference: [AE2 1.21.1 terminals](https://guide.appliedenergistics.org/1.21.1/items-blocks-machines/terminals).
