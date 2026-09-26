# 漫剧导演系统 (Mannequin Mod) Git 规范化迭代工作流

为了保证本项目在后续迭代开发中的高质量、可维护性与可追溯性，制定本规范。

---

## 一、分支管理策略 (Branching Strategy)

- **`main`**：稳定发布主分支。任何推入 `main` 的代码必须经过完整单元测试构建通过（`./gradlew test build`），确保随时可打包生成正式可用 Jar。
- **特性分支（Feature Branches）**：针对新的制片功能，推荐从 `main` 分支拉取临时特性分支：
  - 新功能：`feat/feature-name` (例如 `feat/camera-orbit`, `feat/voice-acting`)
  - 缺陷修复：`fix/bug-name` (例如 `fix/shadow-flicker`)
  - 性能优化：`perf/pipeline-name` (例如 `perf/ffmpeg-pipe`)

---

## 二、提交信息规范 (Conventional Commits)

每次提交遵循模块化、原子化（Atomic Commit）原则，单次提交仅聚焦一个明确目标：

```
<type>(<scope>): <清晰简洁的简述>

[可选的详细正文描述]
```

### 1. 常用 Type 类型
- **`feat`**：新增业务功能或指令特性（如新机位算法、新型人偶等）；
- **`fix`**：修复已发现的缺陷或异常；
- **`perf`**：针对主线程掉帧、显存拷贝、编解码效率等专项性能优化；
- **`refactor`**：代码重构（既不修复 bug 也不添加新功能的代码结构优化）；
- **`test`**：新增或修改单元测试与集成测试；
- **`docs`**：实训手册、架构全景图、技术规约等文档更新；
- **`chore`**：Gradle 构建脚本、依赖库版本、CI/CD 或项目配置维护。

### 2. 常用 Scope 模块范围
- `(entity)`：人偶实体模型、姿态、语义染色与渲染层；
- `(camera)`：自由相机、防抖航速、多机位分镜、样条运镜算法；
- `(timeline)`：主时钟调度引擎、多轨叠录动捕、幽灵轨迹渲染；
- `(recorder)`：Native 显存直通流式 MP4 硬件/软件视频录制管线；
- `(ui)`：操作快捷中心、监视大厅、时间轴 HUD、画幅遮罩；
- `(wand)`：导演工杖道具交互逻辑与生命周期联动；
- `(build)`：NeoForge 配置、Gradle 依赖与构建输出。

---

## 三、标准迭代交付流程

每次迭代开发请严格遵循以下四步闭环：

```mermaid
flowchart LR
    A["1. 需求分析与方案制定"] --> B["2. 模块化编码实现"]
    B --> C["3. 自动化测试与构建验证<br/>./gradlew test build"]
    C --> D["4. 规范化提交与打标归档<br/>git commit / git tag"]
```

1. **编码与回归**：完成需求编码，保持无无关代码侵入；
2. **测试验证**：在本地运行 `./gradlew test --no-daemon` 确保全量测试 100% 通过；
3. **打包交付**：运行 `./gradlew build -x test --no-daemon` 生成 jar 并同步部署到游戏测试目录；
4. **模块化提交**：根据修改影响范围分批次 `git add` 并按规范 `git commit`。

---

## 四、版本发布打标 (Semantic Versioning)

版本号遵循语义化版本规范：`vMAJOR.MINOR.PATCH`（例如 `v0.1.0`）：
- **MAJOR**：破坏性重大架构调整（如底层文件协议升级）；
- **MINOR**：向后兼容的新增功能特性（如新增主视角录制、巨物缩放）；
- **PATCH**：向后兼容的缺陷修复与细节微调。

创建发布标签命令示例：
```bash
git tag -a v0.1.0 -m "Release v0.1.0: 漫剧导演核心系统与零卡顿MP4直出"
```
