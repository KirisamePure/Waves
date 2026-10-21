# 验证记录

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
