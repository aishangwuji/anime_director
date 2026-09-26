package com.mannequin.client.timeline;

import com.mannequin.client.camera.DirectorCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 提线木偶动捕控制器（Puppeteer Controller）。
 *
 * <p>本类负责实现导演对人偶实体、汽车载具的“附身操纵与动捕录制”：
 * <ul>
 *   <li><b>沉浸附身（Possession）</b>：准星对准实体按快捷键，直接切入该实体的视角，使用熟悉的 WASD 键盘原生物理跑位；</li>
 *   <li><b>动捕捕获（MoCap Capture）</b>：在时间轴走字时，逐 Tick 捕获实体的位移与朝向，存入对应轨道；</li>
 *   <li><b>安全脱离</b>：录制完成或中途取消时，视角无缝归还玩家本体，零副作用。</li>
 * </ul>
 */
public final class PuppeteerController {

    public static final PuppeteerController INSTANCE = new PuppeteerController();

    private Entity possessedEntity = null;
    private boolean isRecording = false;

    private PuppeteerController() {
    }

    public boolean isPossessing() {
        return possessedEntity != null && possessedEntity.isAlive();
    }

    public Entity getPossessedEntity() {
        return possessedEntity;
    }

    public boolean isRecording() {
        return isRecording;
    }

    /**
     * 尝试对准星所指的人偶或载具进行附身绑定。
     *
     * @return 成功附身返回 true，无目标或脱离返回 false
     */
    public boolean togglePossession() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }

        if (isPossessing()) {
            // 已在附身状态，执行脱离
            releasePossession();
            return false;
        }

        // 模式互斥检查：自由相机运镜中禁止附身实体导致冲突
        if (DirectorCameraController.INSTANCE.isCameraActive()) {
            mc.player.displayClientMessage(Component.literal("§c[导演系统] 当前处于自由相机模式，请先按 [F6] 退出自由相机再附身实体！"), true);
            return false;
        }

        // 探测准星前方 10 格内的实体
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (target != mc.player) {
                possess(target);
                return true;
            }
        }
        return false;
    }

    /**
     * 附身到目标实体上。
     *
     * @param target 目标实体
     */
    public void possess(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        this.possessedEntity = target;
        if (mc.player != null) {
            target.setYRot(mc.player.getYRot());
            target.setXRot(mc.player.getXRot());
            target.yRotO = mc.player.getYRot();
            target.xRotO = mc.player.getXRot();
            if (target instanceof LivingEntity living) {
                living.setYHeadRot(mc.player.getYRot());
                living.setYBodyRot(mc.player.getYRot());
                living.yHeadRotO = mc.player.getYRot();
                living.yBodyRotO = mc.player.getYRot();
            }
        }
        mc.setCameraEntity(target);
    }

    /**
     * 渲染帧实时同步与视口防抖（消除 20 TPS 阶梯感与视角闪烁回弹）。
     *
     * @param partialTick 渲染帧时间偏移
     */
    public void onRenderTick(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!isPossessing() || mc.player == null) {
            return;
        }

        // 强锁定相机实体，防止原版按键（如潜行Shift/旁观退出）或网络包意外重置 cameraEntity 导致视角闪回玩家
        if (mc.getCameraEntity() != possessedEntity) {
            mc.setCameraEntity(possessedEntity);
        }

        // 每一渲染帧与玩家鼠标平滑旋转实时同步，彻底消除插值回弹闪烁
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        possessedEntity.setYRot(yaw);
        possessedEntity.setXRot(pitch);
        possessedEntity.yRotO = yaw;
        possessedEntity.xRotO = pitch;

        if (possessedEntity instanceof LivingEntity living) {
            living.setYHeadRot(yaw);
            living.setYBodyRot(yaw);
            living.yHeadRotO = yaw;
            living.yBodyRotO = yaw;
        }
    }

    /**
     * 退出附身模式，将相机归还给玩家本体。
     */
    public void releasePossession() {
        Minecraft mc = Minecraft.getInstance();
        if (isRecording) {
            stopRecordingMoCap();
        }
        this.possessedEntity = null;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
    }

    /**
     * 开启对当前附身实体的动捕录制。
     */
    public void startRecordingMoCap() {
        if (!isPossessing()) {
            return;
        }

        String trackId = possessedEntity.getUUID().toString();
        String trackName = possessedEntity.getName().getString() + " 动捕轨";
        MasterClockEngine.INSTANCE.getOrCreateTrack(trackId, trackName);

        this.isRecording = true;
        MasterClockEngine.INSTANCE.startRecording(trackId);
    }

    /**
     * 停止当前实体的动捕录制，并存盘。
     */
    public void stopRecordingMoCap() {
        if (isRecording) {
            this.isRecording = false;
            MasterClockEngine.INSTANCE.stopRecording();
        }
    }

    /**
     * 客户端逻辑 Tick 更新：处理操纵位移与帧捕获。
     */
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (!isPossessing() || mc.player == null) {
            return;
        }

        // 实体销毁保护：若被附身人偶被移除/回收/销毁，立即安全退出附身并交还相机
        if (possessedEntity == null || possessedEntity.isRemoved()) {
            releasePossession();
            return;
        }

        // 附身期间冻结玩家本体物理运动，避免 WASD 同时操纵真人角色位移
        mc.player.xxa = 0.0F;
        mc.player.zza = 0.0F;
        mc.player.setDeltaMovement(Vec3.ZERO);

        // 读取玩家键盘输入，操纵被附身实体
        double forward = 0.0;
        double strafe = 0.0;

        if (mc.options.keyUp.isDown()) forward += 1.0;
        if (mc.options.keyDown.isDown()) forward -= 1.0;
        if (mc.options.keyLeft.isDown()) strafe += 1.0;
        if (mc.options.keyRight.isDown()) strafe -= 1.0;

        boolean jump = mc.options.keyJump.isDown();
        boolean isSprinting = mc.options.keySprint.isDown();

        // 根据被控实体的朝向计算三维移动向量
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        possessedEntity.setYRot(yaw);
        possessedEntity.setXRot(pitch);
        if (possessedEntity instanceof LivingEntity living) {
            living.setYHeadRot(yaw);
            living.setYBodyRot(yaw);
        }

        // 真实物理行进动力学：区分疾跑与常速，并根据人偶体型比例进行动态步长适配
        double baseSpeed = 0.215;
        if (possessedEntity instanceof LivingEntity living) {
            baseSpeed = living.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        } else if (possessedEntity instanceof com.mannequin.entity.MannequinEntity mannequin) {
            float s = mannequin.getScale();
            if (s > 1.0F) {
                baseSpeed *= Math.min(3.5, 1.0 + (s - 1.0) * 0.35);
            } else if (s < 1.0F) {
                baseSpeed *= Math.max(0.35, s);
            }
        }
        double speed = isSprinting ? baseSpeed * 1.3 : baseSpeed;

        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        double dx = (strafe * cos - forward * sin) * speed;
        double dz = (forward * cos + strafe * sin) * speed;

        // 真实重力与跳跃物理计算（解决穿墙、斜飞升天等非物理 Bug）
        Vec3 prevDelta = possessedEntity.getDeltaMovement();
        double dy = prevDelta.y;

        if (possessedEntity.onGround()) {
            if (jump) {
                dy = 0.42; // 原版跳跃垂直初速度
            } else {
                dy = 0.0;
            }
        } else {
            // 空中受自然重力加速度影响下落
            dy = Math.max(-1.5, prevDelta.y - 0.08);
        }

        Vec3 motion = new Vec3(dx, dy, dz);
        possessedEntity.setDeltaMovement(motion);
        // 使用 Minecraft 原生碰撞位移处理，具备方块阻挡、台阶跨越与落地检测，严禁穿墙瞬移
        possessedEntity.move(MoverType.SELF, motion);
        possessedEntity.xo = possessedEntity.getX();
        possessedEntity.yo = possessedEntity.getY();
        possessedEntity.zo = possessedEntity.getZ();

        // 向服务端同步人偶受控位移，解决联机/局域网环境下的服务端物理校验拉回（Rubberbanding）
        if (mc.getConnection() != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.mannequin.network.SyncMannequinPosPayload(
                    possessedEntity.getId(),
                    possessedEntity.getX(), possessedEntity.getY(), possessedEntity.getZ(),
                    possessedEntity.getYRot(), possessedEntity.getXRot(),
                    motion.x, motion.y, motion.z
            ));
        }

        // 若处于录制模式，记录当前 Tick 的空间变换
        if (isRecording && MasterClockEngine.INSTANCE.getState() == MasterClockEngine.State.RECORDING) {
            int currentTick = MasterClockEngine.INSTANCE.getCurrentTick();
            TimelineTrack track = MasterClockEngine.INSTANCE.getTracks().get(possessedEntity.getUUID().toString());
            if (track != null) {
                boolean isMoving = (forward != 0.0 || strafe != 0.0);
                track.recordFrame(new MotionFrame(
                        currentTick,
                        possessedEntity.position(),
                        possessedEntity.getXRot(),
                        possessedEntity.getYRot(),
                        0.0F,
                        70.0F,
                        isMoving
                ));
            }
        }
    }
}
