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

    public enum SelectionState {
        EMPTY,        // 尚未设定任何选点
        SELECTING_B,  // 已设定起始角点 A，等待设定对角点 B
        LOCKED_READY  // 选区已闭合锁定（A与B均已就绪，防止误触破坏）
    }

    private BuildingSelectionManager() {
    }

    public SelectionState getSelectionState() {
        if (posA == null && posB == null) {
            return SelectionState.EMPTY;
        }
        if (posA != null && posB == null) {
            return SelectionState.SELECTING_B;
        }
        return SelectionState.LOCKED_READY;
    }

    /**
     * 开启全新选区：设定起始角点 A，并自动清空旧的角点 B，杜绝残留畸形选区。
     */
    public void startNewSelection(BlockPos newA) {
        this.posA = newA;
        this.posB = null;
    }

    /**
     * 锁定选区：设定对角点 B，进入已锁定就绪状态。
     */
    public void lockSelection(BlockPos newB) {
        this.posB = newB;
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

    private int placementOffsetY = 0; // 高度偏移（-20 到 +20 格）

    public boolean isPlacing() {
        return selectedBlueprint != null;
    }

    public BuildingBlueprintHelper.BlueprintInfo getSelectedBlueprint() {
        return selectedBlueprint;
    }

    public void setSelectedBlueprint(BuildingBlueprintHelper.BlueprintInfo selectedBlueprint) {
        this.selectedBlueprint = selectedBlueprint;
        this.placementRotation = 0;
        this.placementOffsetY = 0;
    }

    public void clearPlacement() {
        this.selectedBlueprint = null;
        this.placementRotation = 0;
        this.placementOffsetY = 0;
    }

    public int getPlacementRotation() {
        return placementRotation;
    }

    public void rotatePlacement() {
        rotatePlacement(true);
    }

    public void rotatePlacement(boolean clockwise) {
        int delta = clockwise ? 90 : -90;
        this.placementRotation = (this.placementRotation + delta + 360) % 360;
    }

    public int getPlacementOffsetY() {
        return placementOffsetY;
    }

    public void setPlacementOffsetY(int offset) {
        this.placementOffsetY = Math.max(-20, Math.min(20, offset));
    }

    public void adjustPlacementOffsetY(int delta) {
        setPlacementOffsetY(this.placementOffsetY + delta);
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
