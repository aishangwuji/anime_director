package com.mannequin.client.building;

import com.mannequin.building.BuildingBlueprintHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;

/**
 * 客户端建筑选区与放置状态管理器（Building Selection & Placement Manager）。
 */
public final class BuildingSelectionManager {

    public static final BuildingSelectionManager INSTANCE = new BuildingSelectionManager();

    private BlockPos posA = null;
    private BlockPos posB = null;

    private BuildingBlueprintHelper.BlueprintInfo selectedBlueprint = null;
    private int placementRotation = 0; // 0, 90, 180, 270

    private BuildingSelectionManager() {
    }

    public BlockPos getPosA() {
        return posA;
    }

    public void setPosA(BlockPos posA) {
        this.posA = posA;
    }

    public BlockPos getPosB() {
        return posB;
    }

    public void setPosB(BlockPos posB) {
        this.posB = posB;
    }

    public boolean hasSelection() {
        return posA != null && posB != null;
    }

    public void clearSelection() {
        this.posA = null;
        this.posB = null;
    }

    public Vec3i getSelectionSize() {
        if (!hasSelection()) {
            return Vec3i.ZERO;
        }
        int sx = Math.abs(posA.getX() - posB.getX()) + 1;
        int sy = Math.abs(posA.getY() - posB.getY()) + 1;
        int sz = Math.abs(posA.getZ() - posB.getZ()) + 1;
        return new Vec3i(sx, sy, sz);
    }

    public AABB getSelectionBoundingBox() {
        if (!hasSelection()) {
            return null;
        }
        int minX = Math.min(posA.getX(), posB.getX());
        int minY = Math.min(posA.getY(), posB.getY());
        int minZ = Math.min(posA.getZ(), posB.getZ());
        int maxX = Math.max(posA.getX(), posB.getX()) + 1;
        int maxY = Math.max(posA.getY(), posB.getY()) + 1;
        int maxZ = Math.max(posA.getZ(), posB.getZ()) + 1;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // ==================== 放置模式管理 ====================

    public boolean isPlacing() {
        return selectedBlueprint != null;
    }

    public BuildingBlueprintHelper.BlueprintInfo getSelectedBlueprint() {
        return selectedBlueprint;
    }

    public void setSelectedBlueprint(BuildingBlueprintHelper.BlueprintInfo selectedBlueprint) {
        this.selectedBlueprint = selectedBlueprint;
        this.placementRotation = 0;
    }

    public void clearPlacement() {
        this.selectedBlueprint = null;
        this.placementRotation = 0;
    }

    public int getPlacementRotation() {
        return placementRotation;
    }

    public void rotatePlacement() {
        this.placementRotation = (this.placementRotation + 90) % 360;
    }

    /**
     * 获取考虑当前旋转角度后的建筑占地尺寸 (X, Y, Z)。
     */
    public Vec3i getRotatedPlacementSize() {
        if (selectedBlueprint == null) {
            return Vec3i.ZERO;
        }
        int x = selectedBlueprint.sizeX();
        int y = selectedBlueprint.sizeY();
        int z = selectedBlueprint.sizeZ();
        if (placementRotation == 90 || placementRotation == 270) {
            return new Vec3i(z, y, x);
        }
        return new Vec3i(x, y, z);
    }
}
