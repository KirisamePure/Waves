# 验证记录

## 0.4.2 海水连通前沿与逐帧接缝

- `python3 tools/gradle_cloud.py build --no-daemon` 通过，76 项核心检查通过。
- 最终发布 JAR 的 `python3 tools/render_test_cloud.py --production` 和 `python3 tools/render_test_cloud.py --production --no-iris` 均通过。原生、Iris 光影和完全无 Iris 的 GPU 波高误差均小于 0.000005 格；每条动画含 12 个相位，湿润范围随浪头推进和回落。动画导出到 `build/reports/waves-0.4.2/native-shore.gif` 与 `iris-shore.gif`，夜雨截图为同目录 `night-rain.png`。

- 修复此前岸面按游戏刻采样、再插值而落后于 GPU 海面的时序问题。所有边界顶点逐帧计算，匹配原海面三角形边缘；计入 Sodium 流体顶面的 0.001 格下移。添加半格以内、海侧透明度渐隐的衔接带，保留外海材质。
- 岸内不再独立采样正弦海浪。四邻接地形图只接受海水边界传来的水头，障碍未被漫过时不向后传播；前沿按有限速度推进，不在整个岸面同时出现。内部退水保留可配置的衰减，边界始终固定在当前海面。对实际可见的湿润前沿再次检查连通性，尚未填满或退干的连接带不能留下岸内独立水片。
- 新增 GPU 顶点反馈测试：实际执行发布包中的波浪 GLSL 并读回 12 个顶点高度，与 CPU 边界顶点比较，容差 0.005 格。该测试在独立测试 Mod 中，不进入发布 JAR。
- 新增两处海湾凹口的锯齿岸线动画：原生与 Iris 分别截取 12 个连续相位，检查湿润范围涨落、存在连通推进前沿、边界误差小于 0.002 格；另保存夜间降雨截图。原有岸台、水花、海浪循环声、天气风声及六页配置测试保留。
- 核心检查新增高岸遮挡低洼地、越岸连通、水头损失、回落清除、覆盖距离、固定海水边界、粗网格边缘插值、前沿有限速度与跨帧抵达时间。仍是局部视觉近似，不改变实际水方块、地形或服务器状态；第三方光影的材质差异和复杂碰撞形状仍需额外适配。

## 0.4.1 曲线岸面与旧水墙遮罩

- `python3 tools/gradle_cloud.py build --no-daemon`：66 项核心检查通过。新增三角形与湿润半空间的交线插值、干面剔除、片段着色器接入和重复补丁检查。
- 岸面细分为四分之一格，按连续浪高与实际碰撞列高度裁剪三角形；不再把每个方块上的水片抬到地面。沿途障碍仍限制上岸范围，退水与透明度设置保留。初次发现岸面时同步插值两端，避免短暂从静止水位跳变。
- 保留 Sodium 或当前 Iris 光影的原海面顶面与材质。128×128 GPU 遮罩配合未位移位置的导数，只隐藏近岸原水网格的竖直侧壁；岸面边界匹配外海的粗网格高度，减少新旧面几何接缝。没有替换外海着色，也没有修改实际水方块。
- `python3 tools/render_test_cloud.py --production` 与 `python3 tools/render_test_cloud.py --production --no-iris` 均通过；实际发布 JAR 覆盖 Iris 光影启用、光影关闭和完全不安装 Iris 三种情况。
- 客户端用自然深海、低岸台、抬高的 L 形台阶及礁石测试；增加贴近水面的低视角截图、运行时遮罩纹理尺寸及着色器 uniform 检查。截图：`0013_waves-stepped-shore-low-view.png` 和 `0023_waves-iris-stepped-shore-low-view.png`。
- 仍是视觉近似。方块地形保持原状；原海面仍受其顶点密度限制；岸面水层与任意第三方光影的水材质、反射折射可能有差异。几何/曲面细分光影保守禁用岸面及侧壁遮罩，避免破坏着色器接口。没有验证实体显卡或所有第三方光影。

## 0.4.0 岸上溢水与天气风声

- `python3 tools/gradle_cloud.py build --no-daemon` 通过，61 项核心检查通过。新增海水连通路径、沿途障碍高度、孤立岸面、覆盖距离、雨天/雷暴风强度、参数边界和世界边界波形采样一致性检查。
- `python3 tools/render_test_cloud.py --production`、`python3 tools/render_test_cloud.py --production --no-iris` 均通过：实际发布 JAR、原生路径、完全不安装 Iris 与可选 Iris。自然深海中放置 7×4 低矮岸台，确认只有浪头超过地面后才绘制岸上水层；开/关截图有可见差异，关闭后额外绘制面数为零。岸台仍是砂岩，岸上仍为空气，没有实际水方块。
- Iris 场景记录 28 个候选岸面、28 个湿润岸面；实际绘制通过 Fabric 世界渲染事件与不可变帧快照完成。已人工查看原生开/关对照及 Iris 下的薄水层画面。
- 天气声验证：晴天不播放；原版服务器雨天后渐入；雷暴音量超过雨天的两倍；独立开关后淡出/恢复；放晴后淡出。测试关闭阵风与遮蔽以比较强度，正常使用默认开启两项。
- 新风声使用 felix.blume 的 CC0 自然风录音，来源、固定镜像提交和 SHA-256 记录在 `licenses/wind-source.json`，说明随发布 JAR 打包。ffprobe 确认为 20 秒、44.1 kHz、单声道 Vorbis。云环境为空音频输出，验证启动播放和音量变化，未进行实体扬声器听感评估。
- 测试覆盖六页中文配置。截图：`build/run/productionClientGameTest/screenshots/0011_waves-native-runup-on.png`、`0012_waves-native-runup-off.png`、`0021_waves-iris-runup-on.png`。
- 本实现仅阅读用户所给 Puddles & Floods 的 README 了解目标，没有复制其源码或资源。岸上薄水层是独立实现的局部视觉近似，不等同真实流体、完整洪水模拟或任意第三方光影的材质适配。

## 0.3.2 原版水花、连续岸边音效与近岸动态

- `python3 tools/gradle_cloud.py build --no-daemon` 通过，50 项核心检查通过。近岸衰减检查改为边界保留 72% 波高，不再以固定水面为预期行为。
- `python3 tools/render_test_cloud.py --production`、`python3 tools/render_test_cloud.py --production --no-iris` 均通过，覆盖可选 Iris 与完全未安装 Iris 的发布版客户端。
- 水花直接创建原版 `ParticleTypes.SPLASH`，保留配置尺寸、发射高度和密度，遵循原版粒子设置。移除自定义粒子注册、椭圆纹理和相关生成脚本。已查看近景中的原版像素水花。
- 原生和 Iris 共用的水面库将近岸波高保留在 72%–100%，缩短过渡带，并让近岸白沫随浪头聚散。GPU 距离场及礁石放置/移除检查仍通过。
- 声音使用一个重复播放的环境声实例，不再等待撞岸事件。测试关闭水花并冻结波浪后等待 450 游戏刻，确认仍在播放且没有重复启动；关闭音效后淡出；升高到离岸范围外后停止；返回后恢复。
- 最终录音为同一 CC0 源处理得到的 20 秒、44.1 kHz 单声道循环，接头使用 2 秒等功率交叉淡化。解码检查时循环边界跳变约 0.0115，小于邻近样本最大跳变约 0.0275，没有额外零音量空隙。云环境为空音频输出，未进行实体设备听感评估。
- 日志仍位于 `build/reports/render-production/client-test.log` 和 `build/reports/render-production-no-iris/client-test.log`，截图位于生产测试目录。

## 0.3.1 更大的拍岸水花与录音音效

- `python3 tools/gradle_cloud.py build --no-daemon` 通过，50 项核心检查通过，包含新增水滴尺寸的边界处理和配置持久化。
- `python3 tools/render_test_cloud.py --production` 通过：覆盖无光影的原生渲染与可选 Iris 测试光影，检查喷溅、落回或碰撞、独立开关和音频引擎实际启动播放。新增断言确认三段录音音效均被 SoundManager 加载；实际记录 3 次声音播放。已查看放大后的水花和新增尺寸滑块截图。
- 默认水滴尺寸约为 0.3.0 的两倍，扩大横向散开范围、提高发射速度，并增加第四页尺寸滑块；保持每刻最多 96 个新水滴的预算。
- 合成声音替换为 SecureSubset 的 CC0 录音《Crashing Waves - Pacific Ocean》的三个片段。固定镜像提交和 SHA-256，保存作者、来源、授权证据与剪辑偏移，详见 `THIRD_PARTY_NOTICES.md` 和 `licenses/surf-source.json`。
- ffprobe 检查三个 Ogg 均为可解码的单声道 44.1 kHz、4.8 秒；播放间隔改为 5.5–6.45 秒，音高限定为 0.97–1.03，避免片段重叠和明显变调。
- 检查发布 JAR 内含三段录音、第三方素材说明和来源元数据。云环境使用 OpenAL 空输出，未进行实体音频设备听感评估。

## 0.3.0 独立 Mod 渲染、水滴和音效

- `python3 tools/gradle_cloud.py build --no-daemon` 通过，50 项核心检查通过，新增水花高度、音量边界处理和设置持久化检查。
- `python3 tools/render_test_cloud.py --production --no-iris` 通过：生产 Loader 与实际重映射发布 JAR，只安装 Fabric API 和 Sodium，完全没有 Iris。原生水面动画、晴天/关闭对照、雷暴增强、礁石距离场和四页中文设置均正常。
- `python3 tools/render_test_cloud.py --production` 通过：先验证 Iris 已安装但光影关闭的原生路径，再加载没有内置水面位移的独立测试光影，检查海浪位移接口、GPU 振幅、水滴与声音。测试光影移入 `src/gametest/shaderpack`，仅进入独立测试 Mod。
- 发布 JAR 内没有光影 ZIP，不再自动安装或切换光影。检查了两段单声道 Ogg 和水滴粒子纹理已打包；Ogg 可正常解码。
- 测试仍使用种子 1、自然深海 X=1856、Z=-2048，放置/移除实体礁石，并读回 GPU 上 128×128 距离场，礁石距离为 0。
- 在晴天另拍近景，确认水滴向上飞起并落回或碰撞，而不是水面贴图或烟雾。Iris 测试累计发射 559 个水滴，截图时已有 373 个落回或碰撞；加强测试参数（波高 1.1、水花高度 1.4×）下最大上升约 3.75 格。
- 拍岸声实际提交音频引擎，`PlayResult` 非 `NOT_STARTED` 才计数；Iris 测试记录 3 次成功播放。分别关闭音效和水花后，不再产生对应新效果。
- 测试使用 OpenAL 空输出设备，验证资源加载与播放启动，没有进行实体扬声器听感评估。视觉使用 Mesa llvmpipe，已查看无雨水花近景及 Iris 下水花截图，未覆盖实体显卡或任意第三方光影。

日志：`build/reports/render-production-no-iris/client-test.log`、`build/reports/render-production/client-test.log`。近景：`build/run/productionClientGameTest/screenshots/0009_waves-native-spray-closeup.png`、`0010_waves-native-spray-falling.png`；Iris 近景：`0016_waves-iris-spray-closeup.png`。

## 0.2.0 不规则波浪与礁石反馈

- `python3 tools/gradle_cloud.py build --no-daemon`：48 项核心检查通过，覆盖八组波的相位连续性、不规则度、距离场和近岸衰减。
- `python3 tools/render_test_cloud.py --production`：真实重映射发布 JAR 在 Minecraft 1.21.10、Iris 1.9.7 和 Sodium 0.7.3 下通过客户端测试。
- 自然深海 X=1856、Z=-2048，验证波浪动画、关闭效果、雷暴增强、参考光影启用和中文三页设置。
- 放置礁石后，直接读取水面程序的 GPU 距离场，确认采样器已绑定、纹理为 128×128、礁石距离为 0；移除礁石后距离恢复。
- 检测到 7 次拍岸水花事件，已查看礁石白沫、水花及关闭泡沫的对照截图。晴天振幅为 0.32 格，雷暴渐变中约为 1.457 格。
- 日志：`build/reports/render-production/client-test.log`；截图：`build/run/productionClientGameTest/screenshots/0007_waves-reef-foam.png`。
- 使用 Mesa llvmpipe 软件 OpenGL；本轮视觉验证覆盖附带参考光影，未验证实体显卡或第三方光影包。近岸反馈是客户端视觉近似，不改变水流、碰撞或船只动力。

## 0.1.1 回归

- `python3 tools/render_test_cloud.py` 通过：不再预先启用光影，改为检查未启用状态提示并点击游戏内按钮启用参考光影。
- `python3 tools/render_test_cloud.py --production` 通过：运行真正的重映射发布 JAR 与生产 Fabric Loader，使用 Iris 包内嵌套依赖。
- 测试使用原版地形、种子 1，在自然深海 **X=1856, Z=-2048** 验证，不放置测试水池。
- 检查动画相位随游戏刻推进、晴天位移、关闭后平面、原版雷暴天气增强、中文设置和保存。
- 生产场景晴天振幅 0.32 格；雷暴渐变中达到约 1.454 格。已查看实际生产截图。
- 生产日志：`build/reports/render-production/client-test.log`；截图：`build/run/productionClientGameTest/screenshots/`。
- 本次改进解决的是缺少光影激活引导、状态不明确与测试仅覆盖水池的问题。用户截图本身不能确认光影、配置或材质适配状态，不能据此断言其唯一原因。

## 0.1.0 初始验证

目标：Minecraft 1.21.10、Fabric Loader 0.17.3、Fabric API 0.138.4+1.21.10、Iris 1.9.7、Sodium 0.7.3、JDK 21。

- `bash tools/setup_cloud.sh`：重现构建、核心检查及 Minecraft 资源准备。
- `python3 tools/render_test_cloud.py`：真实 Minecraft 客户端，Xorg dummy + Mesa llvmpipe OpenGL 4.5 软件渲染。
- 35 项核心检查通过：天气倍率、200 步平滑、远坐标相位连续性、零速度、零振幅、着色器改写及配置文件处理等。
- 客户端集成场景通过：参考光影编译、水材质 ID 10001、晴天起伏、关闭后平面、原版雷暴天气增强、非空白画面、中文两页设置、翻页和保存关闭。
- 集成场景实测晴天振幅 0.32 格；雷暴渐变过程中达到约 1.468 格（超过晴天的 4.5 倍），默认最终目标为 1.6 格。
- 已人工查看晴天/关闭对照、雷暴和中文设置截图。

日志：`build/reports/render/client-test.log`。截图：`build/run/clientGameTest/screenshots/`。

测试使用离线开发身份及空音频设备。账号服务/更新检查的网络警告和虚拟 X11 光标警告不影响此次水面与界面验证。未验证实体显卡、第三方光影包或独立多人服务器；客户端 Mod 不注册服务器端入口或自定义网络协议。

此记录验证当前实例和附带的 Waves Reference 光影，不代表所有 Iris 光影包均兼容，也不代表云环境配置已经发布。
