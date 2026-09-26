package com.mannequin.client.camera;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 客户端专用的导演相机锚点虚拟实体（Camera Anchor Entity）。
 *
 * <p>设计原理：
 * <ul>
 *   <li>在 Minecraft 中，相机的空间坐标、视锥体剔除（Frustum Culling）与区块加载天然挂载在 {@link Entity} 上；</li>
 *   <li>当开启自由导演飞控或穿越机视角时，生成此隐形无体积无碰撞的锚点实体，并将客户端相机实体设为此实体
 *       （{@code mc.setCameraEntity(anchor)}）；</li>
 *   <li>摄像机的移动只需平滑改变此实体的坐标与朝向，玩家本体仍安全停留在原地；</li>
 *   <li>关闭导演相机时，视角无缝归还玩家本体，锚点实体自动销毁，零副作用、零崩溃风险。</li>
 * </ul>
 */
public class CameraAnchorEntity extends Entity {

    public CameraAnchorEntity(EntityType<? extends CameraAnchorEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    public void tick() {
        // 空实现：彻底禁用原版实体物理步进，由导演相机控制器直接接管位置映射
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
