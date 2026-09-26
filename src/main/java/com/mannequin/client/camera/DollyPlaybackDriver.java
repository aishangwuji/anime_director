package com.mannequin.client.camera;

import com.mannequin.client.persistence.StudioPersistenceManager;
import com.mannequin.client.timeline.MasterClockEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 机械滑轨驱动器（DollyPlaybackDriver）。
 *
 * <p>负责运镜机位关键帧管理、Catmull-Rom 弧长样条计算与时间轴回放步进驱动。
 */
public final class DollyPlaybackDriver {

    public static final DollyPlaybackDriver INSTANCE = new DollyPlaybackDriver();

    private final List<CameraKeyframe> dollyKeyframes = new ArrayList<>();
    private CatmullRomSpline dollySpline = null;

    private DollyPlaybackDriver() {
    }

    public List<CameraKeyframe> getDollyKeyframes() {
        return Collections.unmodifiableList(dollyKeyframes);
    }

    public CatmullRomSpline getDollySpline() {
        return dollySpline;
    }

    public boolean hasSpline() {
        return dollySpline != null;
    }

    public void clearDollyKeyframes() {
        dollyKeyframes.clear();
        dollySpline = null;
    }

    public void setDollyKeyframes(List<CameraKeyframe> keyframes) {
        dollyKeyframes.clear();
        if (keyframes != null) {
            dollyKeyframes.addAll(keyframes);
        }
        rebuildSpline();
    }

    public void addDollyKeyframe(CameraAnchorEntity anchor) {
        if (!FpvFlightController.INSTANCE.isActive() || anchor == null) {
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
        rebuildSpline();

        if (dollyKeyframes.size() >= 2) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§a[导演系统] 已打下第 " + dollyKeyframes.size() + " 个运镜机位（Catmull-Rom 机械滑轨已就绪）"), true);
            }
        } else {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§a[导演系统] 已打下第 1 个运镜机位（至少需 2 个关键点生成滑轨）"), true);
            }
        }
        StudioPersistenceManager.INSTANCE.saveStudioScene(true);
    }

    private void rebuildSpline() {
        if (dollyKeyframes.size() >= 2) {
            dollySpline = new CatmullRomSpline(dollyKeyframes);
        } else {
            dollySpline = null;
        }
    }

    /**
     * 驱动样条回放并更新锚点实体与飞控参数。
     *
     * @param partialTick 渲染子帧时间
     * @param anchor      当前绑定的相机锚点实体
     * @return 是否成功由滑轨样条接管驱动
     */
    public boolean samplePlayback(float partialTick, CameraAnchorEntity anchor) {
        if (dollySpline == null || MasterClockEngine.INSTANCE.getState() != MasterClockEngine.State.PLAYING) {
            return false;
        }

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
        return true;
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
        rebuildSpline();
    }
}
