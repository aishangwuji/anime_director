package com.mannequin.client.camera;

import com.mannequin.client.input.ModKeyMappings;
import com.mannequin.client.timeline.MasterClockEngine;
import com.mannequin.client.timeline.PuppeteerController;
import com.mannequin.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.List;

/**
 * 漫剧导演相机总控制器（DirectorCameraController）。
 *
 * <p>统一调度相机的生命周期、事件注入与外观渲染，具体子功能已解耦至：
 * <ul>
 *   <li>{@link CameraInputHandler}：按键消费、视角微调与本体位移锁定；</li>
 *   <li>{@link DollyPlaybackDriver}：机械滑轨关键帧管理与 Catmull-Rom 样条平滑回放驱动；</li>
 *   <li>{@link MannequinScaleScrollHandler}：鼠标滚轮人偶体型缩放与射线检测。</li>
 * </ul>
 */
public final class DirectorCameraController {

    public static final DirectorCameraController INSTANCE = new DirectorCameraController();

    private CameraAnchorEntity anchor = null;
    private long lastFrameTimeNanos = System.nanoTime();
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
            anchor = new CameraAnchorEntity(com.mannequin.client.registry.ClientEntityTypes.CAMERA_ANCHOR.get(), mc.level);
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

            FpvFlightController.INSTANCE.setPosition(eyePos);
            FpvFlightController.INSTANCE.setYaw(mc.player.getYRot());
            FpvFlightController.INSTANCE.setPitch(mc.player.getXRot());
            FpvFlightController.INSTANCE.resetRoll();
            FpvFlightController.INSTANCE.setActive(true);
            lastFrameTimeNanos = System.nanoTime();

            mc.player.displayClientMessage(Component.literal("§a[导演系统] 已开启上帝视角自由运镜 (鼠标自由环视, WASD/Ctrl飞行, [F8]显隐本体)"), true);
        }
    }

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
        DollyPlaybackDriver.INSTANCE.clearDollyKeyframes();
        MultiCameraManager.INSTANCE.clearActivePreview();
    }

    public void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        MasterClockEngine.INSTANCE.onClientTick();
        PuppeteerController.INSTANCE.onClientTick();
        MultiCameraBatchRunner.INSTANCE.onClientTick();
    }

    /**
     * 渲染帧事件：驱动时间轴在渲染帧上的子帧插值与飞控动力学提前结算。
     */
    public void onRenderTick(RenderFrameEvent.Pre event) {
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MasterClockEngine.INSTANCE.onRenderTick(partialTick);
        PuppeteerController.INSTANCE.onRenderTick(partialTick);

        // 若处于机械滑轨回放状态，优先由 Catmull-Rom 弧长样条驱动相机绝对匀速滑行
        boolean handledByDolly = DollyPlaybackDriver.INSTANCE.samplePlayback(partialTick, anchor);
        if (!handledByDolly && isCameraActive()) {
            long now = System.nanoTime();
            double dt = Math.max(0.001, Math.min(0.1, (now - lastFrameTimeNanos) / 1_000_000_000.0));
            lastFrameTimeNanos = now;

            // 变焦长按/连发与急推镜头驱动
            Minecraft mc = Minecraft.getInstance();
            double zoomSpeed = mc.options.keySprint.isDown() ? 100.0 : 40.0;
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

    public void onComputeFov(ViewportEvent.ComputeFov event) {
        if (isCameraActive()) {
            if (FpvFlightController.INSTANCE.isMicroMode()) {
                event.setFOV(110.0);
            } else {
                event.setFOV(FpvFlightController.INSTANCE.getFov());
            }
        }
    }

    public void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (isCameraActive() && hidePlayerModel && event.getEntity() == mc.player) {
            event.setCanceled(true);
        }
    }

    // ==================== 委托给解耦子模块 ====================

    public void onKeyInput(InputEvent.Key event) {
        CameraInputHandler.INSTANCE.onKeyInput(event);
    }

    public void onCalculatePlayerTurn(CalculatePlayerTurnEvent event) {
        CameraInputHandler.INSTANCE.onCalculatePlayerTurn(event);
    }

    public void onMovementInputUpdate(MovementInputUpdateEvent event) {
        CameraInputHandler.INSTANCE.onMovementInputUpdate(event);
    }

    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        MannequinScaleScrollHandler.INSTANCE.onMouseScroll(event);
    }

    public void addDollyKeyframe() {
        DollyPlaybackDriver.INSTANCE.addDollyKeyframe(anchor);
    }

    public List<CameraKeyframe> getDollyKeyframes() {
        return DollyPlaybackDriver.INSTANCE.getDollyKeyframes();
    }

    public CatmullRomSpline getDollySpline() {
        return DollyPlaybackDriver.INSTANCE.getDollySpline();
    }

    public void clearDollyKeyframes() {
        DollyPlaybackDriver.INSTANCE.clearDollyKeyframes();
    }

    public void setDollyKeyframes(List<CameraKeyframe> keyframes) {
        DollyPlaybackDriver.INSTANCE.setDollyKeyframes(keyframes);
    }

    public void dollyKeyframesToNbt(CompoundTag root) {
        DollyPlaybackDriver.INSTANCE.dollyKeyframesToNbt(root);
    }

    public void loadDollyKeyframesFromNbt(CompoundTag root) {
        DollyPlaybackDriver.INSTANCE.loadDollyKeyframesFromNbt(root);
    }
}
