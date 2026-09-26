package com.mannequin.client.camera;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 多机位分镜管理调度中心（Multi-Camera Manager）。
 *
 * <p>负责拍摄机位的添加、保存、切换导播以及导出目录管理。
 */
public final class MultiCameraManager {

    public static final MultiCameraManager INSTANCE = new MultiCameraManager();

    private final List<CameraStation> stations = new ArrayList<>();
    private int nextStationId = 1;
    private File exportDirectory = new File(Minecraft.getInstance().gameDirectory, "videos/mannequin");

    private MultiCameraManager() {
    }

    public List<CameraStation> getStations() {
        return Collections.unmodifiableList(stations);
    }

    public File getExportDirectory() {
        if (!exportDirectory.exists()) {
            exportDirectory.mkdirs();
        }
        return exportDirectory;
    }

    public void setExportDirectory(File dir) {
        if (dir != null) {
            this.exportDirectory = dir;
            if (!this.exportDirectory.exists()) {
                this.exportDirectory.mkdirs();
            }
        }
    }

    /**
     * 在当前上帝视角/穿越机视角的位置与焦距处，捕获并添加一个新的分镜拍摄机位。
     *
     * @param customName 自定义机位名称（若为 null 则自动命名为 "机位 N"）
     * @return 创建的机位对象
     */
    public CameraStation addStationAtCurrent(String customName) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos;
        float yaw, pitch, roll, fov;

        if (DirectorCameraController.INSTANCE.isCameraActive()) {
            pos = FpvFlightController.INSTANCE.getPosition();
            yaw = FpvFlightController.INSTANCE.getYaw();
            pitch = FpvFlightController.INSTANCE.getPitch();
            roll = FpvFlightController.INSTANCE.getRoll();
            fov = FpvFlightController.INSTANCE.getFov();
        } else if (mc.player != null) {
            pos = mc.player.getEyePosition();
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
            roll = 0.0F;
            fov = (float) (double) mc.options.fov().get();
        } else {
            return null;
        }

        int id = nextStationId++;
        String name = (customName != null && !customName.isBlank()) ? customName : ("机位 " + id);
        CameraStation station = new CameraStation(id, name, pos, yaw, pitch, roll, fov);
        stations.add(station);

        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§a[机位系统] 成功添加 " + name + " (FOV: " + String.format("%.1f", fov) + "°)"),
                    true
            );
        }
        com.mannequin.client.persistence.StudioPersistenceManager.INSTANCE.saveStudioScene(true);
        return station;
    }

    /**
     * 导播切换视角：瞬间跳转并锁定到指定机位的视角、方向与焦距。
     *
     * @param id 机位序号
     * @return 是否成功切换
     */
    public boolean switchToStation(int id) {
        CameraStation target = stations.stream().filter(s -> s.id() == id).findFirst().orElse(null);
        if (target == null) {
            return false;
        }

        if (!DirectorCameraController.INSTANCE.isCameraActive()) {
            DirectorCameraController.INSTANCE.toggleCamera();
        }

        FpvFlightController.INSTANCE.setPosition(target.position());
        FpvFlightController.INSTANCE.setYaw(target.yaw());
        FpvFlightController.INSTANCE.setPitch(target.pitch());
        FpvFlightController.INSTANCE.setRoll(target.roll());
        FpvFlightController.INSTANCE.setFov(target.fov());

        CameraAnchorEntity anchor = DirectorCameraController.INSTANCE.getAnchor();
        if (anchor != null) {
            Vec3 pos = target.position();
            anchor.setPos(pos.x, pos.y, pos.z);
            anchor.xo = pos.x;
            anchor.yo = pos.y;
            anchor.zo = pos.z;
            anchor.setYRot(target.yaw());
            anchor.setXRot(target.pitch());
            anchor.yRotO = target.yaw();
            anchor.xRotO = target.pitch();
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§6[导播台] 已切至: " + target.name()),
                    true
            );
        }
        return true;
    }

    /**
     * 删除指定机位。
     */
    public void removeStation(int id) {
        stations.removeIf(s -> s.id() == id);
        com.mannequin.client.persistence.StudioPersistenceManager.INSTANCE.saveStudioScene(true);
    }

    /**
     * 清空所有拍摄机位。
     */
    public void clearStations() {
        stations.clear();
        nextStationId = 1;
    }

    private int currentStationIndex = -1;

    /**
     * 导播顺次切换到下一个拍摄机位。
     */
    public void cycleNextStation() {
        if (stations.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§c[导播台] 尚未添加任何分镜机位！请先按 [B] 打下机位。"), true);
            }
            return;
        }
        currentStationIndex = (currentStationIndex + 1) % stations.size();
        CameraStation target = stations.get(currentStationIndex);
        switchToStation(target.id());
    }

    /**
     * 获取当前切入的机位名称，若未切入则返回空字符串。
     */
    public String getCurrentStationName() {
        if (currentStationIndex >= 0 && currentStationIndex < stations.size()) {
            return stations.get(currentStationIndex).name();
        }
        return "";
    }

    private CameraStation activePreviewStation = null;

    public CameraStation getActivePreviewStation() {
        return activePreviewStation;
    }

    public void clearActivePreview() {
        this.activePreviewStation = null;
    }

    /**
     * 进入指定机位的沉浸式实时试看模式。
     */
    public boolean previewStation(int id) {
        CameraStation target = stations.stream().filter(s -> s.id() == id).findFirst().orElse(null);
        if (target == null) {
            return false;
        }
        this.activePreviewStation = target;
        boolean switched = switchToStation(id);
        if (switched) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("§6[监视器] 已切入【" + target.name() + "】！按 [P] 试看动作演播，按 [C] 打开监视大厅"),
                        false
                );
            }
        }
        return switched;
    }

    /**
     * 将指定机位更新覆盖为当前视口的绝对坐标、角度与 FOV。
     */
    public boolean updateStationToCurrent(int id) {
        for (int i = 0; i < stations.size(); i++) {
            CameraStation s = stations.get(i);
            if (s.id() == id) {
                Vec3 pos;
                float yaw, pitch, roll, fov;
                if (DirectorCameraController.INSTANCE.isCameraActive()) {
                    pos = FpvFlightController.INSTANCE.getPosition();
                    yaw = FpvFlightController.INSTANCE.getYaw();
                    pitch = FpvFlightController.INSTANCE.getPitch();
                    roll = FpvFlightController.INSTANCE.getRoll();
                    fov = FpvFlightController.INSTANCE.getFov();
                } else {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) return false;
                    pos = mc.player.getEyePosition();
                    yaw = mc.player.getYRot();
                    pitch = mc.player.getXRot();
                    roll = 0.0F;
                    fov = (float) (double) mc.options.fov().get();
                }
                CameraStation updated = new CameraStation(s.id(), s.name(), pos, yaw, pitch, roll, fov);
                stations.set(i, updated);
                if (activePreviewStation != null && activePreviewStation.id() == id) {
                    activePreviewStation = updated;
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("§a[机位系统] 已将【" + s.name() + "】覆盖更新为当前构图视角 (FOV: " + String.format("%.1f", fov) + "°)"),
                            true
                    );
                }
                com.mannequin.client.persistence.StudioPersistenceManager.INSTANCE.saveStudioScene(true);
                return true;
            }
        }
        return false;
    }

    /**
     * 重命名指定机位。
     */
    public boolean renameStation(int id, String newName) {
        if (newName == null || newName.isBlank()) return false;
        for (int i = 0; i < stations.size(); i++) {
            CameraStation s = stations.get(i);
            if (s.id() == id) {
                CameraStation updated = new CameraStation(s.id(), newName.trim(), s.position(), s.yaw(), s.pitch(), s.roll(), s.fov());
                stations.set(i, updated);
                if (activePreviewStation != null && activePreviewStation.id() == id) {
                    activePreviewStation = updated;
                }
                com.mannequin.client.persistence.StudioPersistenceManager.INSTANCE.saveStudioScene(true);
                return true;
            }
        }
        return false;
    }

    /**
     * 打开视频导出存储目录。
     */
    public void openExportFolder() {
        File dir = getExportDirectory();
        Util.getPlatform().openFile(dir);
    }

    /**
     * 将全场所有机位持久化序列化为 NBT 标签。
     */
    public void toNbt(CompoundTag root) {
        ListTag list = new ListTag();
        for (CameraStation s : stations) {
            list.add(s.toNbt());
        }
        root.put("CameraStations", list);
        root.putInt("NextStationId", nextStationId);
    }

    /**
     * 从 NBT 标签中反序列化恢复机位。
     */
    public void fromNbt(CompoundTag root) {
        stations.clear();
        if (root.contains("CameraStations", 9)) {
            ListTag list = root.getList("CameraStations", 10);
            for (int i = 0; i < list.size(); i++) {
                stations.add(CameraStation.fromNbt(list.getCompound(i)));
            }
        }
        if (root.contains("NextStationId")) {
            this.nextStationId = root.getInt("NextStationId");
        } else {
            this.nextStationId = stations.stream().mapToInt(CameraStation::id).max().orElse(0) + 1;
        }
    }
}
