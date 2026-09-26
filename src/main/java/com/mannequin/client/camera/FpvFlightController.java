package com.mannequin.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 电影级高动态穿越机（FPV）飞控动力学与微观昆虫视角控制器。
 *
 * <p>本类为导演镜头注入真实的空气动力学物理质感与极端微距视角能力：
 * <ul>
 *   <li><b>转弯侧倾联动（Banked Turn Roll）</b>：拐弯时相机自动向内侧倾斜，模拟四轴旋翼机的升力倾角；</li>
 *   <li><b>手动滚转持久保持（Manual Roll）</b>：Z/X 键调节持久横滚角，不被急转侧倾自动抹平；</li>
 *   <li><b>无级焦距 FOV 调节（Dynamic FOV Zoom）</b>：支持 PageUp/PageDown 无级变焦，真实反映电影等效焦距；</li>
 *   <li><b>油门推力与漂移惯性（Throttle & Momentum）</b>：推杆加速，松杆滑翔，杜绝原版急停急走的机械生硬感；</li>
 *   <li><b>甲壳虫/昆虫微观视角（Micro Bug's-Eye Mode）</b>：超低贴地高度、极微距穿行、超广角鱼眼透视；</li>
 *   <li><b>地面与防穿透保护（Ground & Void Collision Protection）</b>：强制锁定地表之上，绝对杜绝钻地看见虚空；</li>
 *   <li><b>注视目标锁定（Look-at Tracking）</b>：镜头中心死死咬住指定的人偶或车辆，做复杂机动时永远不丢目标。</li>
 * </ul>
 */
public final class FpvFlightController {

    public static final FpvFlightController INSTANCE = new FpvFlightController();

    // 空间运动向量与惯性
    private Vec3 position = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;

    // 旋转与滚转角（拆分持久手动滚转与瞬时气动侧倾）
    private float pitch = 0.0F;
    private float yaw = 0.0F;
    private float roll = 0.0F;         // 最终合成渲染角
    private float manualRoll = 0.0F;   // 玩家 Z/X 键控制的持久横滚角
    private float bankRoll = 0.0F;     // 鼠标急转弯产生的瞬时动力学侧倾角

    private static final java.nio.file.Path PREFS_PATH =
            net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("mannequin/client_prefs.nbt");

    // 动态焦距 FOV
    private float fov = 70.0F;

    // 状态配置
    private boolean active = false;
    private CameraFlightStyle flightStyle = CameraFlightStyle.STABILIZED; // 默认防抖平稳视角
    private boolean microMode = false; // 昆虫微观视角模式
    private boolean groundCollision = true; // 地面防钻地与防虚空碰撞保护（默认开启）
    private SpeedGear speedGear = SpeedGear.NORMAL; // 自由相机航速档位 (默认标准 6.0m/s)
    private double maxSpeed = 6.0;      // 基础航速 (格/秒)
    private double altCreepMultiplier = 0.25; // 按住 Alt 键的微移减速倍率 (0.25x)
    private Entity lookAtTarget = null; // 注视咬死的目标实体

    private FpvFlightController() {
        loadPreferences();
    }

    public void loadPreferences() {
        try {
            if (java.nio.file.Files.exists(PREFS_PATH)) {
                net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(PREFS_PATH, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                if (tag.contains("cameraFlightStyle")) {
                    String styleName = tag.getString("cameraFlightStyle");
                    try {
                        this.flightStyle = CameraFlightStyle.valueOf(styleName);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (tag.contains("speedGear")) {
                    try {
                        this.speedGear = SpeedGear.valueOf(tag.getString("speedGear"));
                        this.maxSpeed = this.speedGear.getSpeed();
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (tag.contains("maxSpeed")) {
                    this.maxSpeed = tag.getDouble("maxSpeed");
                }
                if (tag.contains("altCreepMultiplier")) {
                    this.altCreepMultiplier = tag.getDouble("altCreepMultiplier");
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void savePreferences() {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                if (PREFS_PATH.getParent() != null) {
                    java.nio.file.Files.createDirectories(PREFS_PATH.getParent());
                }
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                if (java.nio.file.Files.exists(PREFS_PATH)) {
                    try {
                        tag = net.minecraft.nbt.NbtIo.readCompressed(PREFS_PATH, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    } catch (Exception ignored) {
                        tag = new net.minecraft.nbt.CompoundTag();
                    }
                }
                tag.putString("cameraFlightStyle", flightStyle.name());
                tag.putString("speedGear", speedGear.name());
                tag.putDouble("maxSpeed", maxSpeed);
                tag.putDouble("altCreepMultiplier", altCreepMultiplier);
                net.minecraft.nbt.NbtIo.writeCompressed(tag, PREFS_PATH);
            } catch (Exception ignored) {
            }
        });
    }

    public CameraFlightStyle getFlightStyle() {
        return flightStyle;
    }

    public void setFlightStyle(CameraFlightStyle style) {
        this.flightStyle = (style != null) ? style : CameraFlightStyle.STABILIZED;
        if (this.flightStyle == CameraFlightStyle.STABILIZED) {
            this.bankRoll = 0.0F;
        }
        savePreferences();
    }

    public void toggleFlightStyle() {
        setFlightStyle(this.flightStyle.next());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (this.flightStyle == CameraFlightStyle.STABILIZED) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a[导演系统] 运镜风格已切换为：【" + flightStyle.getDisplayName() + "】(三轴水平防抖 | 零侧倾晃荡 | 即走即停)"),
                        true
                );
            } else {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§6[导演系统] 运镜风格已切换为：【" + flightStyle.getDisplayName() + "】(气动转弯侧倾 | 惯性滑翔漂移)"),
                        true
                );
            }
        }
    }

    public SpeedGear getSpeedGear() {
        return speedGear;
    }

    public void setSpeedGear(SpeedGear gear) {
        this.speedGear = (gear != null) ? gear : SpeedGear.NORMAL;
        this.maxSpeed = this.speedGear.getSpeed();
        savePreferences();
    }

    public void cycleSpeedGear() {
        setSpeedGear(this.speedGear.next());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§b[航速档位] 已切换为：【" + speedGear.getDisplayName() + "】(按住 Alt 可极致超微移)"),
                    true
            );
        }
    }

    public double getAltCreepMultiplier() {
        return altCreepMultiplier;
    }

    public void setAltCreepMultiplier(double altCreepMultiplier) {
        this.altCreepMultiplier = Math.max(0.05, Math.min(1.0, altCreepMultiplier));
        savePreferences();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                this.position = mc.player.getEyePosition();
                this.pitch = mc.player.getXRot();
                this.yaw = mc.player.getYRot();
                this.roll = 0.0F;
                this.manualRoll = 0.0F;
                this.bankRoll = 0.0F;
                this.fov = (float) mc.options.fov().get().doubleValue();
                this.velocity = Vec3.ZERO;
            }
        }
    }

    public boolean isMicroMode() {
        return microMode;
    }

    /**
     * 切换昆虫/甲壳虫微观贴地视角。
     */
    public void toggleMicroMode() {
        this.microMode = !this.microMode;
    }

    public boolean isGroundCollision() {
        return groundCollision;
    }

    public void setGroundCollision(boolean groundCollision) {
        this.groundCollision = groundCollision;
    }

    public void toggleGroundCollision() {
        this.groundCollision = !this.groundCollision;
    }

    public Entity getLookAtTarget() {
        return lookAtTarget;
    }

    public void setLookAtTarget(Entity target) {
        this.lookAtTarget = target;
    }

    public Vec3 getPosition() {
        return position;
    }

    public void setPosition(Vec3 position) {
        this.position = position;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getRoll() {
        return roll;
    }

    public float getManualRoll() {
        return manualRoll;
    }

    public void setRoll(float roll) {
        this.manualRoll = Math.max(-85.0F, Math.min(85.0F, roll));
        this.roll = this.manualRoll; // 必须同步直接写 roll，让机械滑轨关键帧回放立即吃到倾斜角度
    }

    public void addRoll(float delta) {
        this.manualRoll = Math.max(-85.0F, Math.min(85.0F, this.manualRoll + delta));
    }

    public void resetRoll() {
        this.manualRoll = 0.0F;
        this.bankRoll = 0.0F;
        this.roll = 0.0F;
    }

    public float getFov() {
        return fov;
    }

    public void setFov(float fov) {
        this.fov = Math.max(10.0F, Math.min(150.0F, fov));
    }

    public void adjustFov(float delta) {
        setFov(this.fov + delta);
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public void adjustSpeed(double delta) {
        this.maxSpeed = Math.max(0.2, Math.min(30.0, this.maxSpeed + delta));
    }

    /**
     * 鼠标视角输入更新（包含转弯侧倾角动力学计算）。
     *
     * @param deltaYaw   水平偏航旋转量
     * @param deltaPitch 垂直俯仰旋转量
     */
    public void onMouseTurn(double deltaYaw, double deltaPitch) {
        if (!active) {
            return;
        }

        // 若开启了目标注视锁定，则朝向由目标位置决定，屏蔽手动偏航
        if (lookAtTarget != null && lookAtTarget.isAlive()) {
            updateLookAtRotation();
            return;
        }

        this.yaw = net.minecraft.util.Mth.wrapDegrees((float) (this.yaw + deltaYaw * 0.15));
        this.pitch = (float) Math.max(-89.9, Math.min(89.9, this.pitch + deltaPitch * 0.15));

        if (flightStyle == CameraFlightStyle.FPV_DRONE) {
            // 穿越机航模视角：转弯微幅动力学侧倾联动，提供真机倾侧飞行感
            this.bankRoll = Math.max(-30.0F, Math.min(30.0F, (float) (-deltaYaw * 0.6)));
        } else {
            // 防抖平稳视角：彻底禁用任何转向侧倾晃动，保持地平线绝对水平稳固！
            this.bankRoll = 0.0F;
        }
    }

    /**
     * 动力学帧更新（包含油门推力、空气阻尼滑翔、以及侧倾平滑衰减）。
     *
     * @param dt 帧间隔时间（秒）
     */
    public void update(double dt) {
        if (!active || dt <= 0.0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // 1. 采集推力输入
        double thrustX = 0.0;
        double thrustY = 0.0;
        double thrustZ = 0.0;

        if (mc.options.keyUp.isDown()) thrustZ += 1.0;
        if (mc.options.keyDown.isDown()) thrustZ -= 1.0;
        if (mc.options.keyLeft.isDown()) thrustX -= 1.0;
        if (mc.options.keyRight.isDown()) thrustX += 1.0;
        if (mc.options.keyJump.isDown()) thrustY += 1.0;
        if (mc.options.keyShift.isDown()) thrustY -= 1.0;

        // 速度修饰键：
        // 1. 按住 Ctrl / 疾跑键: 2.5 倍冲刺极速，方便片场大范围调度
        // 2. 按住 Alt 键: 0.25x 极端微移爬行，轻触 A/D 仅微移毫米/厘米，专为构图死抠细节设计
        long windowHandle = mc.getWindow().getWindow();
        boolean altDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);

        double speedMultiplier = 1.0;
        if (mc.options.keySprint.isDown()) {
            speedMultiplier = 2.5;
        } else if (altDown) {
            speedMultiplier = altCreepMultiplier;
        }

        // 微观昆虫视角下降低推力，便于微距精细穿梭
        double currentMaxSpeed = microMode ? (maxSpeed * 0.25) : (maxSpeed * speedMultiplier);

        // 将相机局部坐标系推力转换到世界坐标系
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
        Vec3 up = new Vec3(0.0, 1.0, 0.0);

        Vec3 thrustVector = forward.scale(thrustZ)
                .add(right.scale(thrustX))
                .add(up.scale(thrustY));

        // 2. 模拟速度推进与阻尼（根据当前运镜风格区分：三轴防抖云台 vs 穿越机气动惯性）
        if (flightStyle == CameraFlightStyle.STABILIZED) {
            // 防抖平稳视角（三轴云台/轨道推车稳态物理）：
            // 平滑线性逼近目标航速：低速时起步极为柔和细腻，轻点 A/D 键仅微移极小距离，绝不突兀跳跃；
            // 松开按键时指数急速刹停，彻底消除冰面滑行漂移感
            if (thrustVector.lengthSqr() > 1e-4) {
                Vec3 targetVel = thrustVector.normalize().scale(currentMaxSpeed);
                double accelRate = Math.min(10.0, Math.max(3.5, currentMaxSpeed * 1.5));
                double accelFactor = 1.0 - Math.exp(-accelRate * dt);
                velocity = velocity.lerp(targetVel, accelFactor);
            } else {
                double brakeFactor = 1.0 - Math.exp(-16.0 * dt);
                velocity = velocity.lerp(Vec3.ZERO, brakeFactor);
                if (velocity.lengthSqr() < 1e-4) {
                    velocity = Vec3.ZERO;
                }
            }
        } else {
            // 穿越机航模视角（气动推力与滑翔漂移惯性）：
            // 推力加速度积分与空气阻尼滑翔
            if (thrustVector.lengthSqr() > 1e-4) {
                thrustVector = thrustVector.normalize().scale(currentMaxSpeed * 3.5); // 加速度
            }
            velocity = velocity.add(thrustVector.scale(dt));
            double drag = Math.exp(-3.5 * dt); // 空气阻尼
            velocity = velocity.scale(drag);

            if (thrustVector.lengthSqr() < 1e-4 && velocity.lengthSqr() < 1e-4) {
                velocity = Vec3.ZERO;
            }
        }

        // 3. 积分更新空间位置与地面防穿透/防虚空碰撞解算
        Vec3 move = velocity.scale(dt);
        if (groundCollision && mc.level != null) {
            position = resolveMovementCollision(mc.level, position, move);
        } else {
            position = position.add(move);
        }

        // 4. 侧倾姿态结算
        if (flightStyle == CameraFlightStyle.STABILIZED) {
            // 防抖模式下：bankRoll 锁定 0，roll 平滑阻尼锁定至 manualRoll（默认 0° 绝对水平）
            bankRoll = 0.0F;
            roll = (float) DampedValue.update(roll, manualRoll, 14.0, dt);
        } else {
            // 穿越机模式下：bankRoll 瞬时气动侧倾衰减归零，与 manualRoll 叠加
            bankRoll = (float) DampedValue.update(bankRoll, 0.0, 5.0, dt);
            float effectiveTargetRoll = manualRoll + bankRoll;
            roll = (float) DampedValue.update(roll, effectiveTargetRoll, 10.0, dt);
        }

        // 5. 若有注视目标，实时跟踪瞄准
        if (lookAtTarget != null && lookAtTarget.isAlive()) {
            updateLookAtRotation();
        }
    }

    /**
     * 计算并更新注视锁定朝向（对准目标的视觉中心）。
     */
    private void updateLookAtRotation() {
        if (lookAtTarget == null) {
            return;
        }
        Vec3 targetEye = lookAtTarget.getEyePosition();
        Vec3 dir = targetEye.subtract(position);
        double dist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);

        this.yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        this.pitch = (float) Math.toDegrees(Math.atan2(-dir.y, dist));
    }

    /**
     * 解决相机移动中的地面碰撞、实体方块阻挡与防穿地防虚空穿透。
     * <p>确保相机只在向下穿透地表时受到地面托举阻断，杜绝将水平障碍物误判为地表而导致镜头被动自动往上飞。
     */
    private Vec3 resolveMovementCollision(Level level, Vec3 current, Vec3 move) {
        double minWorldY = level.getMinBuildHeight() + 1.0;

        double newX = current.x + move.x;
        double newY = current.y + move.y;
        double newZ = current.z + move.z;

        // 1. 水平轴防穿墙 (分轴推进并支持贴墙滑动，撞墙直接阻停水平移动，绝不抬升相机)
        if (move.x != 0) {
            BlockPos posCheckX = BlockPos.containing(newX, current.y, current.z);
            if (!isPassable(level, posCheckX)) {
                newX = current.x;
                velocity = new Vec3(0, velocity.y, velocity.z);
            }
        }
        if (move.z != 0) {
            BlockPos posCheckZ = BlockPos.containing(newX, current.y, newZ);
            if (!isPassable(level, posCheckZ)) {
                newZ = current.z;
                velocity = new Vec3(velocity.x, velocity.y, 0);
            }
        }

        // 2. 核心地面保护：严格只向下探测相机脚下的地表表面
        // 起始扫描点严格设为当前脚下高度 (current.y - 0.01)，绝不向上扫描视线或头部障碍物
        int startScanY = (int) Math.floor(current.y - 0.01);
        int endScanY = (int) Math.floor(newY - 1.0);
        double highestFloorY = minWorldY;

        for (int y = startScanY; y >= Math.max(level.getMinBuildHeight(), endScanY); y--) {
            BlockPos floorPos = new BlockPos((int) Math.floor(newX), y, (int) Math.floor(newZ));
            BlockState state = level.getBlockState(floorPos);
            VoxelShape shape = state.getCollisionShape(level, floorPos);
            if (!shape.isEmpty()) {
                double blockTop = floorPos.getY() + shape.max(Direction.Axis.Y);
                highestFloorY = blockTop;
                break;
            }
        }

        // 贴地安全间隙（仅在相机试图下沉穿入该地面下方时才触发阻断托举）
        double clearance = microMode ? 0.05 : 0.20;
        double minSafeY = highestFloorY + clearance;

        if (newY < minSafeY) {
            newY = minSafeY;
            if (velocity.y < 0) {
                velocity = new Vec3(velocity.x, 0.0, velocity.z);
            }
        }

        // 3. 绝对虚空底线保护（绝不允许掉进世界虚空底部）
        if (newY < minWorldY) {
            newY = minWorldY;
            velocity = new Vec3(velocity.x, Math.max(0.0, velocity.y), velocity.z);
        }

        return new Vec3(newX, newY, newZ);
    }

    private boolean isPassable(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return true;
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty();
    }
}
