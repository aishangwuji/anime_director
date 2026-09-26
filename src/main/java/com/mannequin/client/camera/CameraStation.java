package com.mannequin.client.camera;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * 导演分镜固定拍摄机位数据模型（Camera Station）。
 *
 * <p>记录机位序号、自命名、三维绝对坐标、航向俯仰横滚角以及特定焦距 FOV。
 *
 * @param id       机位序号 (1, 2, 3...)
 * @param name     机位备注名称 (如 "机位 1 (全景主视角)", "特写机位" 等)
 * @param position 空间位置坐标
 * @param yaw      水平航向角
 * @param pitch    垂直俯仰角
 * @param roll     画面横滚角
 * @param fov      无级镜头焦距 FOV
 */
public record CameraStation(int id, String name, Vec3 position, float yaw, float pitch, float roll, float fov) {

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Name", name);
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.y);
        tag.putDouble("Z", position.z);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putFloat("Roll", roll);
        tag.putFloat("Fov", fov);
        return tag;
    }

    public static CameraStation fromNbt(CompoundTag tag) {
        int id = tag.getInt("Id");
        String name = tag.getString("Name");
        Vec3 pos = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        float yaw = tag.getFloat("Yaw");
        float pitch = tag.getFloat("Pitch");
        float roll = tag.getFloat("Roll");
        float fov = tag.getFloat("Fov");
        return new CameraStation(id, name, pos, yaw, pitch, roll, fov);
    }
}
