# 🎬 Anime Director (漫剧导演)

<p align="center">
  <img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License" />
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg" alt="Minecraft 1.21.1" />
  <img src="https://img.shields.io/badge/NeoForge-21.1.251+-orange.svg" alt="NeoForge" />
  <img src="https://img.shields.io/badge/Java-21-red.svg" alt="Java 21" />
</p>

<p align="center">
  <b>把 Minecraft 变成 AIGC 虚拟影视影棚：手操动捕、多轨叠录、纯色替身、FPV 穿越机运镜！</b><br>
  <i>Turn Minecraft into a lightweight virtual production stage for AIGC anime and cinematic video generation.</i>
</p>

---

## 📖 简介 / Introduction

**Anime Director（漫剧导演）** 专为 **AIGC 漫剧创作者、动画短片创作者与影视分镜导演** 设计。

你不再需要经历 Blender / Maya 复杂的骨骼蒙皮与关键帧调线流程。在 Minecraft 方块世界中，你可以像在真实片场一样：
> **“自己化身演员，把角色一个一个演出来；再化身摄影师，飞着穿越机把它拍下来！”**

录制出的纯色高饱和度替身视频，可无缝送入 **可灵 (Kling)、Wan2.1、Runway Gen-3、ComfyUI** 进行重绘，彻底解决多人物画面串色、肢体混乱与运镜呆板的行业痛点。

---

## 🎬 核心痛点与优势对比

| 传统 3D 动画制作流程 | Anime Director + AIGC 虚拟制片流程 |
| :--- | :--- |
| ❌ 复杂建模、贴图、骨骼蒙皮与权重调试 | ✅ **MC 方块极速搭景**，放置 7 头身无贴图极简人偶 |
| ❌ 在时间线上苦调曲线、手动对齐多角色位移 | ✅ **提线木偶动捕**：准星对准人偶按 `G` 附身，键盘实操跑位逐轨录制 |
| ❌ 多角色交互动作容易穿模脱节 | ✅ **幽灵伴跑回放**：录制角色 B 时，角色 A 在旁边自动按刚才路线奔跑配合！ |
| ❌ AI 生视频时多角色衣服发色混乱（串色） | ✅ **纯色角色代理**：纯红=主角A，纯蓝=对手B，大模型语义分割与 ComfyUI 遮罩秒提取 |
| ❌ 镜头移动机械呆板、手搓鼠标容易晃动拉丝 | ✅ **高动态 FPV 穿越机**：转弯自动侧倾 15°~35°，贴地飞行、9:16 竖屏漫剧画幅遮罩 |

---

## ✨ 核心特性 / Features

* 🎭 **附身手操动捕 (Puppetry Motion Capture)**：准星瞄准人偶按 `G` 一键附身，按 `K` 开启实时逐帧录制。
* 👥 **幽灵伴跑与多轨叠录 (Ghost Replaying)**：排练或录制下一轨时，已录制轨道自动在旁伴跑重放，实现精准双人/多人对手戏。
* 🎨 **纯色代理替身 (Chroma Proxy Mannequins)**：手持染料直接为人偶着色（纯红/纯蓝/纯绿等），支持无光照纯平自发光模式（Unlit），配合 ComfyUI 吸管秒提 Alpha 蒙版。
* 🎥 **FPV 穿越机航模镜头 (Cinematic FPV Camera)**：一键脱离本体进入自由机位，转弯自动侧倾（Dutch angle）、贴地俯冲平滑跟拍。
* 📐 **多画幅构图遮罩 (Aspect Ratio Masks)**：一键循环切换 `全屏` $\to$ `9:16 竖屏漫剧` $\to$ `16:9 影视` $\to$ `21:9 宽银幕`，带三分构图黄金参考线。
* ⏱️ **一键倒带复位 (One-Key Rewind)**：按 `R` 键，全场所有演员与机位瞬间瞬移回第 0 秒起点，零成本无限次重拍排演。

---

## 🚀 5 分钟上手实操演练

假设我们要拍摄一个镜头：**“红衣主角在街巷狂奔，蓝色对手开车载具呼啸而过，穿越机贴地俯冲跟拍”**（时长 6 秒）。

```
┌─────────────────────────────────────────────────────────────┐
│ 步骤 1: 布景与染色 ──► 放置人偶，拿红/蓝染料右键变成纯色替身 │
│ 步骤 2: 附身动捕 ────► 瞄准红人按 [G] 附身，按 [K] 录制跑动 │
│ 步骤 3: 倒带复位 ────► 按 [R] 键，红人瞬间瞬移回起点第 0 秒 │
│ 步骤 4: 录制第 2 轨 ──► 附身对手，红人在旁边幽灵自动伴跑！ │
│ 步骤 5: 穿越机实拍 ──► 按 [F6] 飞起，[V]开竖屏，[F1]纯净录制│
└─────────────────────────────────────────────────────────────┘
```

---

## 🎮 导演快捷键速查表 (Controls Quickstart)

> 所有快捷键均已收录在原版 `选项 -> 控制 -> 按键绑定 -> 漫剧导演与虚拟制片` 中，可自由自定义改键。

| 快捷键 | 默认按键 | 功能用途 |
| :---: | :---: | :--- |
| **开启/退出导演相机** | **`F6`** | 切入脱离本体的 FPV 穿越机自由飞控视角 |
| **附身/脱离实体** | **`G`** | 准星瞄准人偶一键附身操控跑位，或退出附身 |
| **动捕录制开关** | **`K`** | 附身后开始/停止逐帧记录角色移动路线 |
| **排演播放/暂停** | **`P`** | 全场所有角色轨道同步起跑，排练整体时机 |
| **一键倒带复位** | **`R`** | 全场角色与相机瞬间瞬移回第 0 秒初始站桩机位 |
| **切换画幅遮罩** | **`V`** | 循环切换：全屏 $\to$ 9:16竖屏 $\to$ 16:9宽屏 $\to$ 21:9宽银幕 |
| **镜头手动滚转** | **`Z`** / **`X`** | 镜头向左/向右倾斜（Dutch Angle 极富动漫张力构图） |
| **镜头焦距变焦** | **`PageUp`** / **`PageDown`** | 特写拉近 (FOV -) / 广角拉远 (FOV +) |
| **场景时长加减** | **`]`** / **`[`** | 场景总录制时间增加/减少 1 秒（默认 6 秒） |
| **运镜风格切换** | **`F9`** | 防抖平稳三轴防抖视角 vs FPV 航模穿越机视角 |
| **纯净录制模式** | **`F1`** | 原版隐藏 HUD，自动隐藏所有辅助线，输出纯净原片 |

---

## 🤖 AIGC 下游工作流衔接 (Kling / Wan2.1 / ComfyUI)

### 1. 提示词（Prompt）绑定逻辑
多模态视频大模型的 Cross-Attention 对单色对比极度敏感：
```text
A cinematic anime scene, high dynamic camera movement, rainy cyberpunk street.
The pure red figure represents a young male detective with messy silver hair sprinting and dodging.
The pure blue vehicle represents a futuristic neon hover-bike speeding past him.
8k, master lighting, photorealistic anime style.
```

### 2. ComfyUI 局部重绘与遮罩提取（Color Mask / Chroma Key）
1. 在 ComfyUI 中接入 **`Mask by Color`** 或 **`Color Key`** 节点；
2. 吸管直接吸取人偶的纯红色（`#FF0000`），即可秒级提纯出完美的单人 Alpha 蒙版；
3. 将蒙版连接至 ControlNet / Inpaint 局部重绘单元，分别对角色 A 和角色 B 输入独立的提示词与 LoRA，**彻底根治多人物串色、脸部衣服互融的世界级难题**！

---

## 🛠️ 构建与开发 (Build & Development)

### 环境要求
- **Java**: 21
- **Minecraft**: 1.21.1
- **Mod Loader**: NeoForge 21.1.251+

### 本地编译命令
```bash
# 克隆仓库
git clone https://github.com/aishangwuji/anime_director.git
cd anime_director

# 执行测试
./gradlew test

# 构建生产 Jar 包 (产物位于 build/libs/anime_director-0.1.0.jar)
./gradlew build
```

---

## 📄 开源许可证 (License)

本项目基于 [Apache License 2.0](LICENSE) 开源协议分发与维护。欢迎在短视频、动漫制作、电影短片制作及整合包中免费自由使用！
