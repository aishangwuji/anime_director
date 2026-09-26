package com.mannequin.client.camera;

import com.mannequin.client.gui.TimelineHudOverlay;
import com.mannequin.client.input.ModKeyMappings;
import com.mannequin.client.timeline.MasterClockEngine;
import com.mannequin.client.timeline.PuppeteerController;
import com.mannequin.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 漫剧导演相机总控制器（Director Camera Controller）。
 *
 * <p>本类统一调度相机的生命周期、事件注入与用户交互输入：
 * <ul>
 *   <li><b>相机实体锚定</b>：使用隐形锚点实体（{@link CameraAnchorEntity}）脱离玩家本体，实现自由漫游与穿越机飞行；</li>
 *   <li><b>渲染帧插值驱动</b>：在 {@link RenderFrameEvent.Pre} 驱动时间轴子帧平滑插值与飞控动力学提前结算，彻底消除 1 帧贴脸延迟；</li>
 *   <li><b>事件总线接入</b>：通过 {@link ViewportEvent.ComputeCameraAngles} 和 {@link ViewportEvent.ComputeFov} 注入横滚角（Roll）与无级焦距 FOV；</li>
 *   <li><b>按键响应中心</b>：处理附身、动捕、排演、倒带、画幅遮罩、变焦与教程手册唤起。</li>
 * </ul>
 */
public final class DirectorCameraController {

    public static final DirectorCameraController INSTANCE = new DirectorCameraController();

    private CameraAnchorEntity anchor = null;
    private long lastFrameTimeNanos = System.nanoTime();
    private final java.util.List<CameraKeyframe> dollyKeyframes = new java.util.ArrayList<>();
    private CatmullRomSpline dollySpline = null;
    private boolean hidePlayerModel = true; // 上帝视角下默认彻底隐去导演玩家本体模型

    private DirectorCameraController() {
    }

    public boolean isHidePlayerModel() {
        return hidePlayerModel;
    }

    public void setHidePlayerModel(boolean hidePlayerModel) {
        this.hidePlayerModel = hidePlayerModel;
    }

    public void toggleHidePlayerModel() {
        this.hidePlayerModel = !this.hidePlayerModel;
    }

    public CameraAnchorEntity getAnchor() {
        return anchor;
    }

    public boolean isCameraActive() {
        return FpvFlightController.INSTANCE.isActive();
    }

    /**
     * 开启或关闭导演自由飞控模式。
     */
    public void toggleCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (isCameraActive()) {
            // 退出导演相机，将视角归还玩家本体
            FpvFlightController.INSTANCE.setActive(false);
            mc.setCameraEntity(mc.player);
            if (anchor != null) {
                anchor.discard();
                anchor = null;
            }
            MultiCameraManager.INSTANCE.clearActivePreview();
            mc.player.displayClientMessage(Component.literal("§7[导演系统] 已退出上帝视角，归还角色控制权"), true);
        } else {
            // 模式互斥保护：附身实体期间禁止直接切入自由相机导致视角冲突打架
            if (PuppeteerController.INSTANCE.isPossessing()) {
                mc.player.displayClientMessage(Component.literal("§c[导演系统] 当前处于附身操纵模式，请先按 [G] 退出附身再开启导演相机！"), true);
                return;
            }

            // 开启导演相机，生成客户端专用隐形锚点（使用公开安全的 addFreshEntity）
            Vec3 eyePos = mc.player.getEyePosition();
            anchor = new CameraAnchorEntity(ModEntityTypes.CAMERA_ANCHOR.get(), mc.level);
            anchor.setPos(eyePos.x, eyePos.y, eyePos.z);
            anchor.xo = eyePos.x;
            anchor.yo = eyePos.y;
            anchor.zo = eyePos.z;
            anchor.setYRot(mc.player.getYRot());
            anchor.setXRot(mc.player.getXRot());
            anchor.yRotO = mc.player.getYRot();
            anchor.xRotO = mc.player.getXRot();
            mc.level.addFreshEntity(anchor);
            mc.setCameraEntity(anchor);

            // 冻结玩家本体速度与位移
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.xxa = 0.0F;
            mc.player.zza = 0.0F;

            FpvFlightController.INSTANCE.setActive(true);
            lastFrameTimeNanos = System.nanoTime();
            mc.player.displayClientMessage(Component.literal("§a[导演系统] 已开启上帝视角自由运镜 (鼠标自由环视, WASD/Ctrl飞行, [F8]显隐本体)"), true);
        }
    }

    /**
     * 玩家登出/切换世界时清理相机锚点与滑轨，彻底防止跨世界内存泄漏。
     */
    public void onLogout() {
        if (isCameraActive()) {
            FpvFlightController.INSTANCE.setActive(false);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.setCameraEntity(mc.player);
            }
        }
        if (anchor != null) {
            anchor.discard();
            anchor = null;
        }
        dollyKeyframes.clear();
        dollySpline = null;
        MultiCameraManager.INSTANCE.clearActivePreview();
    }

    /**
     * 客户端逻辑 Tick 更新（处理提线木偶与主时钟步进）。
     *
     * @param event NeoForge 客户端逻辑 Tick 事件
     */
    public void onClientTick(ClientTickEvent.Post event) {
        MasterClockEngine.INSTANCE.onClientTick();
        PuppeteerController.INSTANCE.onClientTick();
        MultiCameraBatchRunner.INSTANCE.onClientTick();
    }

    /**
     * 渲染帧事件：驱动时间轴在渲染帧上的子帧插值与飞控动力学提前结算。
     * <p>必须在每一渲染帧开始时（早于 GameRenderer.renderLevel 及 Camera.setup）调用，
     * 才能将锚点实体精确同步至当帧目标坐标，杜绝 1 帧贴脸延迟。
     *
     * @param event NeoForge 渲染帧事件
     */
    public void onRenderTick(RenderFrameEvent.Pre event) {
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MasterClockEngine.INSTANCE.onRenderTick(partialTick);
        PuppeteerController.INSTANCE.onRenderTick(partialTick);

        // 若处于机械滑轨回放状态，优先由 Catmull-Rom 弧长样条驱动相机绝对匀速滑行（支持子弹时间与慢动作精准对齐）
        if (dollySpline != null && MasterClockEngine.INSTANCE.getState() == MasterClockEngine.State.PLAYING) {
            int totalTicks = Math.max(1, MasterClockEngine.INSTANCE.getTotalDurationTicks());
            double smoothTime = MasterClockEngine.INSTANCE.getSmoothPlaybackTime(partialTick);
            double u = Math.max(0.0, Math.min(1.0, smoothTime / (double) totalTicks));
            CameraKeyframe sample = dollySpline.evaluate(u);

            if (anchor != null) {
                anchor.setPos(sample.position().x, sample.position().y, sample.position().z);
                anchor.xo = sample.position().x;
                anchor.yo = sample.position().y;
                anchor.zo = sample.position().z;
                anchor.setYRot(sample.yaw());
                anchor.setXRot(sample.pitch());
                anchor.yRotO = sample.yaw();
                anchor.xRotO = sample.pitch();
            }
            FpvFlightController.INSTANCE.setPosition(sample.position());
            FpvFlightController.INSTANCE.setYaw(sample.yaw());
            FpvFlightController.INSTANCE.setPitch(sample.pitch());
            FpvFlightController.INSTANCE.setRoll(sample.roll());
            FpvFlightController.INSTANCE.setFov(sample.fov());
        } else if (isCameraActive()) {
            long now = System.nanoTime();
            double dt = Math.max(0.001, Math.min(0.1, (now - lastFrameTimeNanos) / 1_000_000_000.0));
            lastFrameTimeNanos = now;

            // 变焦长按/连发与急推镜头（Crash Zoom）实时驱动：
            // 支持键盘按键（PageUp/PageDown）以及鼠标按键（鼠标键 4/5/中键等任意映射），按住即可丝滑持续变焦
            Minecraft mc = Minecraft.getInstance();
            double zoomSpeed = mc.options.keySprint.isDown() ? 100.0 : 40.0; // 按住 Ctrl 疾跑可获 2.5 倍急推变焦
            if (ModKeyMappings.ZOOM_IN.isDown()) {
                FpvFlightController.INSTANCE.adjustFov((float) (-zoomSpeed * dt));
            }
            if (ModKeyMappings.ZOOM_OUT.isDown()) {
                FpvFlightController.INSTANCE.adjustFov((float) (zoomSpeed * dt));
            }

            // 镜头横滚长按支持 (按住 Z / X 平滑旋转)
            if (ModKeyMappings.ROLL_LEFT.isDown()) {
                FpvFlightController.INSTANCE.addRoll((float) (-45.0 * dt));
            }
            if (ModKeyMappings.ROLL_RIGHT.isDown()) {
                FpvFlightController.INSTANCE.addRoll((float) (45.0 * dt));
            }

            FpvFlightController.INSTANCE.update(dt);

            if (anchor != null) {
                Vec3 pos = FpvFlightController.INSTANCE.getPosition();
                anchor.setPos(pos.x, pos.y, pos.z);
                anchor.xo = pos.x;
                anchor.yo = pos.y;
                anchor.zo = pos.z;
                anchor.setYRot(FpvFlightController.INSTANCE.getYaw());
                anchor.setXRot(FpvFlightController.INSTANCE.getPitch());
                anchor.yRotO = FpvFlightController.INSTANCE.getYaw();
                anchor.xRotO = FpvFlightController.INSTANCE.getPitch();
            }
        }
    }

    /**
     * 视口旋转角与横滚角计算事件（注入 Yaw, Pitch, Roll）。
     *
     * @param event NeoForge 相机旋转角计算事件
     */
    public void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (isCameraActive()) {
            event.setYaw(FpvFlightController.INSTANCE.getYaw());
            event.setPitch(FpvFlightController.INSTANCE.getPitch());
            event.setRoll(FpvFlightController.INSTANCE.getRoll());
        } else if (PuppeteerController.INSTANCE.isPossessing()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                event.setYaw(mc.player.getYRot());
                event.setPitch(mc.player.getXRot());
                event.setRoll(0.0F);
            }
        }
    }

    /**
     * 视口 FOV 视场角计算事件（支持昆虫微观视角超广角或无级电影焦距变焦）。
     *
     * @param event NeoForge FOV 计算事件
     */
    public void onComputeFov(ViewportEvent.ComputeFov event) {
        if (isCameraActive()) {
            if (FpvFlightController.INSTANCE.isMicroMode()) {
                // 昆虫复眼大广角透视
                event.setFOV(110.0);
            } else {
                // 真实动态变焦焦距
                event.setFOV(FpvFlightController.INSTANCE.getFov());
            }
        }
    }

    /**
     * 在当前上帝自由相机位置记录一个运镜滑轨关键帧。
     */
    public void addDollyKeyframe() {
        if (!isCameraActive() || anchor == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = FpvFlightController.INSTANCE.getPosition();
        CameraKeyframe kf = new CameraKeyframe(
                pos,
                FpvFlightController.INSTANCE.getPitch(),
                FpvFlightController.INSTANCE.getYaw(),
                FpvFlightController.INSTANCE.getRoll(),
                FpvFlightController.INSTANCE.getFov()
        );
        dollyKeyframes.add(kf);
        if (dollyKeyframes.size() >= 2) {
            dollySpline = new CatmullRomSpline(dollyKeyframes);
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§a[导演系统] 已打下第 " + dollyKeyframes.size() + " 个运镜机位（Catmull-Rom 机械滑轨已就绪）"), true);
            }
        } else {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§a[导演系统] 已打下第 1 个运镜机位（至少需 2 个关键点生成滑轨）"), true);
            }
        }
        com.mannequin.client.persistence.StudioPersistenceManager.INSTANCE.saveStudioScene(true);
    }

    public java.util.List<CameraKeyframe> getDollyKeyframes() {
        return java.util.Collections.unmodifiableList(dollyKeyframes);
    }

    public void clearDollyKeyframes() {
        dollyKeyframes.clear();
        dollySpline = null;
    }

    public void setDollyKeyframes(java.util.List<CameraKeyframe> keyframes) {
        dollyKeyframes.clear();
        if (keyframes != null) {
            dollyKeyframes.addAll(keyframes);
        }
        if (dollyKeyframes.size() >= 2) {
            dollySpline = new CatmullRomSpline(dollyKeyframes);
        } else {
            dollySpline = null;
        }
    }

    public void dollyKeyframesToNbt(CompoundTag root) {
        ListTag list = new ListTag();
        for (CameraKeyframe kf : dollyKeyframes) {
            list.add(kf.toNbt());
        }
        root.put("DollyKeyframes", list);
    }

    public void loadDollyKeyframesFromNbt(CompoundTag root) {
        dollyKeyframes.clear();
        if (root.contains("DollyKeyframes", Tag.TAG_LIST)) {
            ListTag list = root.getList("DollyKeyframes", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                dollyKeyframes.add(CameraKeyframe.fromNbt(list.getCompound(i)));
            }
        }
        if (dollyKeyframes.size() >= 2) {
            dollySpline = new CatmullRomSpline(dollyKeyframes);
        } else {
            dollySpline = null;
        }
    }

    /**
     * 键盘输入事件监听。
     *
     * @param event 客户端按键事件
     */
    public void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }

        // 切换导演相机自由飞控
        if (ModKeyMappings.TOGGLE_CAMERA.consumeClick()) {
            toggleCamera();
        }

        // 切换附身受控人偶/载具 (G 键)
        if (ModKeyMappings.TOGGLE_POSSESSION.consumeClick()) {
            PuppeteerController.INSTANCE.togglePossession();
        }

        // 镜头倾斜角一键回正复位 (N 键)
        if (ModKeyMappings.RESET_ROLL.consumeClick()) {
            FpvFlightController.INSTANCE.resetRoll();
        }

        // 切换 9:16 / 16:9 / 21:9 画幅遮罩 (V 键)
        if (ModKeyMappings.TOGGLE_ASPECT_RATIO.consumeClick()) {
            TimelineHudOverlay.INSTANCE.toggleAspectRatio();
        }

        // 打开/关闭游戏内新手实训教程手册界面 (H 键)
        if (ModKeyMappings.TOGGLE_GUIDE.consumeClick()) {
            if (mc.screen instanceof com.mannequin.client.gui.tutorial.DirectorTutorialScreen) {
                mc.setScreen(null);
            } else if (mc.screen == null) {
                mc.setScreen(new com.mannequin.client.gui.tutorial.DirectorTutorialScreen());
            }
        }

        // 开启/关闭屏幕操作指引与浮窗提示 (F7 键)
        if (ModKeyMappings.TOGGLE_HUD_TIPS.consumeClick()) {
            TimelineHudOverlay.INSTANCE.toggleTips();
            if (mc.player != null) {
                boolean visible = TimelineHudOverlay.INSTANCE.areTipsVisible();
                mc.player.displayClientMessage(Component.literal(visible ? "§a[导演系统] 已开启屏幕操作指引浮窗" : "§7[导演系统] 已关闭屏幕操作指引（已记住设置，不会再自动弹出）"), true);
            }
        }

        // 显隐导演玩家本体模型 (F8 键)
        if (ModKeyMappings.TOGGLE_PLAYER_VISIBILITY.consumeClick()) {
            toggleHidePlayerModel();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(hidePlayerModel ? "§a[导演系统] 上帝视角：已隐去导演玩家本体模型" : "§e[导演系统] 上帝视角：已显示导演玩家本体模型"), true);
            }
        }

        // 切换导演运镜风格：防抖平稳视角 vs 穿越机航模视角 (F9 键)
        if (ModKeyMappings.TOGGLE_CAMERA_STYLE.consumeClick()) {
            FpvFlightController.INSTANCE.toggleFlightStyle();
        }

        // 开启/停止主视角实时运镜录制并直出 MP4 (F10 键)
        if (ModKeyMappings.RECORD_LIVE_POV.consumeClick()) {
            if (MultiCameraBatchRunner.INSTANCE.isLivePovRecording()) {
                MultiCameraBatchRunner.INSTANCE.stopLivePovRecording();
            } else {
                MultiCameraBatchRunner.INSTANCE.startLivePovRecording();
            }
        }

        // 循环切换自由相机多档位航速预设 (J 键)
        if (ModKeyMappings.CYCLE_SPEED_GEAR.consumeClick()) {
            FpvFlightController.INSTANCE.cycleSpeedGear();
        }

        // 呼出导演全功能快捷操作中心 / 动作面板 (默认 C 键，可自定义)
        if (ModKeyMappings.QUICK_MENU.consumeClick()) {
            if (mc.screen instanceof com.mannequin.client.gui.DirectorQuickMenuScreen) {
                mc.setScreen(null);
            } else if (mc.screen == null) {
                mc.setScreen(new com.mannequin.client.gui.DirectorQuickMenuScreen());
            }
        }

        // 启动/暂停排演回放 (P 键，避免与原版 Enter 聊天冲突)
        if (ModKeyMappings.START_DOLLY.consumeClick()) {
            if (MasterClockEngine.INSTANCE.getState() == MasterClockEngine.State.PLAYING) {
                MasterClockEngine.INSTANCE.pause();
            } else {
                MasterClockEngine.INSTANCE.play();
            }
        }

        // 一键倒带复位至第 0 秒 (R 键)
        if (ModKeyMappings.RESET_DOLLY.consumeClick()) {
            MasterClockEngine.INSTANCE.rewindToStart();
        }

        // 开始/停止对附身实体录制动捕 (K 键)，或打下机械滑轨关键机位
        if (ModKeyMappings.ADD_KEYFRAME.consumeClick()) {
            if (PuppeteerController.INSTANCE.isPossessing()) {
                if (PuppeteerController.INSTANCE.isRecording()) {
                    PuppeteerController.INSTANCE.stopRecordingMoCap();
                } else {
                    PuppeteerController.INSTANCE.startRecordingMoCap();
                }
            } else if (isCameraActive() && anchor != null) {
                addDollyKeyframe();
            }
        }

        // 清空当前动捕轨道与机械滑轨机位 (Delete 键)
        if (ModKeyMappings.CLEAR_TRACK.consumeClick()) {
            if (PuppeteerController.INSTANCE.isPossessing()) {
                net.minecraft.world.entity.Entity possessed = PuppeteerController.INSTANCE.getPossessedEntity();
                com.mannequin.client.timeline.TimelineTrack track = MasterClockEngine.INSTANCE.getTracks().get(possessed.getUUID().toString());
                if (track != null) {
                    track.clear();
                    mc.player.displayClientMessage(Component.literal("§e[导演系统] 已清空当前附身实体的动捕数据"), true);
                }
            } else {
                dollyKeyframes.clear();
                dollySpline = null;
                MasterClockEngine.INSTANCE.clearAllTracks();
                mc.player.displayClientMessage(Component.literal("§e[导演系统] 已清空全场所有动捕轨道与机械滑轨样条机位"), true);
            }
        }

        // 时间轴总长度调节 ([ / ])
        if (ModKeyMappings.INCREASE_DURATION.consumeClick()) {
            MasterClockEngine.INSTANCE.setTotalDurationTicks(MasterClockEngine.INSTANCE.getTotalDurationTicks() + 20); // +1s
        }
        if (ModKeyMappings.DECREASE_DURATION.consumeClick()) {
            MasterClockEngine.INSTANCE.setTotalDurationTicks(MasterClockEngine.INSTANCE.getTotalDurationTicks() - 20); // -1s
        }

        // 添加/覆盖分镜固定拍摄机位 (B 键添加新机位 / Shift+B 覆盖更新当前机位)
        if (ModKeyMappings.ADD_CAMERA_STATION.consumeClick()) {
            CameraStation previewStation = MultiCameraManager.INSTANCE.getActivePreviewStation();
            if (previewStation != null && mc.options.keyShift.isDown()) {
                MultiCameraManager.INSTANCE.updateStationToCurrent(previewStation.id());
            } else {
                MultiCameraManager.INSTANCE.addStationAtCurrent(null);
            }
        }
    }

    /**
     * 鼠标视角转动输入监听。
     *
     * @param deltaYaw   鼠标水平偏移
     * @param deltaPitch 鼠标垂直偏移
     */
    public void onMouseTurn(double deltaYaw, double deltaPitch) {
        if (isCameraActive()) {
            FpvFlightController.INSTANCE.onMouseTurn(deltaYaw, deltaPitch);
        }
    }

    /**
     * 鼠标视角转动拦截与计算：接管上帝视角自由相机的旋转，彻底压制并锁死玩家本体转头。
     *
     * @param event NeoForge 视角计算事件
     */
    public void onCalculatePlayerTurn(CalculatePlayerTurnEvent event) {
        if (!isCameraActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler.isMouseGrabbed() && mc.isWindowActive()) {
            double dx = mc.mouseHandler.getXVelocity();
            double dy = mc.mouseHandler.getYVelocity();

            double sensitivity = mc.options.sensitivity().get();
            double d2 = sensitivity * 0.6F + 0.2F;
            double d3 = d2 * d2 * d2;
            double d4 = d3 * 8.0;
            int invertY = mc.options.invertYMouse().get() ? -1 : 1;

            double deltaYaw = dx * d4;
            double deltaPitch = dy * d4 * invertY;

            onMouseTurn(deltaYaw, deltaPitch);
        }

        // 彻底切断对玩家本体模型的旋转影响：
        // 将计算灵敏度设为抵消值 (-0.2 / 0.6F)，使 d2 = 0，从而使 vanilla 的 player.turn(d0, d1) 接收到的位移恒为 0
        event.setMouseSensitivity(-0.2 / 0.6F);
    }

    /**
     * 玩家移动输入拦截：在上帝视角下清空玩家本体 WASD/跳跃/潜行输入，让玩家本体绝对定身，不再原地走动。
     *
     * @param event NeoForge 玩家按键输入事件
     */
    public void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (isCameraActive()) {
            net.minecraft.client.player.Input input = event.getInput();
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }

    /**
     * 玩家实体渲染前置拦截：当处于上帝自由视角且开启导演隐身时，彻底隐藏本地玩家模型，保持画面绝对纯净。
     *
     * @param event NeoForge 玩家渲染前置事件
     */
    public void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (isCameraActive() && hidePlayerModel && event.getEntity() == mc.player) {
            event.setCanceled(true);
        }
    }

    /**
     * 鼠标滚轮变焦监听（上帝视角/穿越机视角下滚动滚轮平滑微调 FOV 焦距）。
     *
     * @param event NeoForge 鼠标滚轮事件
     */
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (isCameraActive()) {
            double deltaY = event.getScrollDeltaY();
            if (deltaY != 0) {
                // 向上滚拉近镜头(缩小FOV)，向下滚推远镜头(增大FOV)
                FpvFlightController.INSTANCE.adjustFov((float) (-deltaY * 3.0F));
                event.setCanceled(true);
            }
        }
    }
}
