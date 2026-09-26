---
covered_paths:
  - "src/main/"
last_verified_commit: "UNVERSIONED"
last_verified: "2026-09-26"
---

# Mannequin Director — 业务域全景

面向 AIGC 漫剧生产的 MC Mod。核心不是"做一个好看的模型"，而是产出**可被视频大模型稳定识别的生肉素材**：场景中性、角色用高对比纯色区分、镜头可电影级运镜。

## 核心概念

- **人偶（Mannequin）**：AI 无关（无寻路、无战斗）的布景实体，只承载"体积、姿态、走位、身份"。
- **纯色代理（Color-Coded Stunt Double）**：用纯色人偶代表角色身份，Prompt/色键/语义分割都能据此切割角色。
- **双着色模式**：
  - `SHADED`：接受场景光照的纯色（保留体积明暗），适合 Depth/Lineart 条件控制。
  - `UNLIT`：跳过光照的纯平色（emissive，全亮），适合直接做语义分割遮罩。
- **导演相机（Director Camera）**：带阻尼的自由飞控，替代原版生硬的旁观者模式。
- **Clay World**：一键把场景降饱和为浅灰容器，与人偶拉出最大反差。（未实现，见 techdebt）

## 人偶状态与数据

人偶是无 AI 的 `Entity`，没有自主行为，只有一组正交属性：

- `color`（`MannequinColor`，16 档，与 vanilla `DyeColor` 1:1）：身份标识。染色方式为手持染料右键。
- `shadeMode`（`unlit` 布尔）：渲染外观。

- 生命周期：无自主状态迁移，`放置 → 静止`，未来"走位"将作为独立子系统叠加（见 techdebt）。
- 持久化：颜色/着色模式的**序数**写入 NBT；读取时经 `MannequinColor.byId` 校验，非法值回落 `WHITE`（边界数据强制校验）。

## 参与者与权限边界

- **单人/创造**：全部功能开放。
- **多人/服务器**：染色、姿态、路径编辑等均为"改实体/改世界"操作，**必须**按 op/建造权限鉴权（当前尚未实现，见 techdebt）。

## 关键业务流程（目标态）

1. 放置人偶 → 染料右键染色（角色 A 红 / 角色 B 蓝 / …）→ 选预设姿态 → 标尺放路径节点 → 指定速度与循环。
2. 切导演模式：阻尼运镜、无级 FOV、滚转、锁注视目标。
3. 开构图 HUD（9:16 / 16:9 / 21:9 遮罩 + 三分线）与 Clay World。
4. 录制 → 画面喂给 AIGC（Prompt 用颜色绑定角色）。

当前仅打通第 1 步的"放置 + 染色"与基础纯色渲染。

## 架构分层与约束

- **渲染外观与数据模型彻底解耦**：服务端只认 `color`/`shadeMode` 数据；`shaded/unlit`、Clay World、letterbox 全为纯客户端渲染决策。
- **Loader/版本**：NeoForge 21.1.251 / MC 1.21.1 / Java 21（Gradle toolchain 自动供给 JDK 21）。
- **相机与 HUD 的事件落点**：`ViewportEvent.ComputeCameraAngles`（原生 roll）、`ComputeFov`、`RegisterGuiLayersEvent`。
- **可测试性边界**：纯计算（阻尼、焦距换算、折线采样）与 NBT 往返抽为可单测单元；渲染/相机交互仍需 `runClient` 手测。

## 边界与异常场景

- 两个同色人偶同框 → 身份歧义（需引导用高区分度色板）。
- 光影（Iris/OptiFine）开启时 post-process 类 Clay World 失效（已决议首版不兼容）。
- 性能模组（Sodium/Embeddium）重写方块渲染 → 拦截式刷白会失效，故 Clay World 不采用方块级拦截。
- 路径节点被破坏/越界、姿态 id 失效、客户端伪造 payload → 全部需在边界校验。

## 当前实现范围

已实现：
- **人偶核心**：工程骨架、人偶实体、16 色染料染色、7 头身无贴图模型、`SHADED`/`UNLIT` 双模式渲染、刷怪物品。
- **全局主时钟与分轨叠录（DAW 模式）**：`MasterClockEngine`、`TimelineTrack`、`MotionFrame`。支持多轨逐个录制，其他轨道幽灵伴跑，一键倒带复位回第 0 秒，时间轴洗带（Scrubbing）。
- **提线木偶动捕附身**：`PuppeteerController`。准星对准实体一键附身，用 WASD 原生物理走位并捕获运动帧。
- **高动态 FPV 穿越机飞控**：`FpvFlightController`、`DirectorCameraController`、`CameraAnchorEntity`。包含侧倾联动（Banked Turn Roll）、油门与滑翔惯性、贴地微观昆虫视角（Micro Bug's-Eye Mode）、注视锁定（Look-at Track）。
- **构图与运镜辅助**：`TimelineHudOverlay`（9:16 / 16:9 / 21:9 纯黑遮罩 + 黄金三分线）、35mm 电影等效焦距实时换算（`FovConverter`）、发光 3D 轨迹光带与秒数刻度（`GhostPathRenderer`）、Catmull-Rom 样条滑轨（`CatmullRomSpline`）。
- **注释与工程规范**：所有新增及改造代码均附带全中文 JavaDoc 与行间注释；集成 JUnit 5 单元测试且全数通过。

未实现（待后续版本演进）：
- 姿态系统与轮盘（GTA 式径向菜单选择 30 种高频漫剧姿势）
- 姿态微调 Gizmo（三轴旋转环）
- Clay World（全局场景降饱和）
- 多人/服务器权限鉴权（当前专注于单人导演创作环境）
