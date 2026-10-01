package com.mannequin.client.camera;

import com.mannequin.entity.MannequinEntity;
import com.mannequin.network.SyncMannequinScalePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 鼠标滚轮人偶体型缩放处理器（MannequinScaleScrollHandler）。
 *
 * <p>负责检测准星前方人偶实体、执行无级体型缩放并向服务器同步。
 */
public final class MannequinScaleScrollHandler {

    public static final MannequinScaleScrollHandler INSTANCE = new MannequinScaleScrollHandler();

    private int activeTargetId = -1;
    private long lastScaleTimestamp = 0L;

    private MannequinScaleScrollHandler() {
    }

    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }

        double deltaY = event.getScrollDeltaY();
        if (deltaY == 0) {
            return;
        }

        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        boolean holdingBuildWand = mainHand.is(com.mannequin.registry.ModItems.BUILD_WAND.get()) || offHand.is(com.mannequin.registry.ModItems.BUILD_WAND.get());
        com.mannequin.client.building.BuildingSelectionManager bsm = com.mannequin.client.building.BuildingSelectionManager.INSTANCE;

        // 0. 建筑蓝图全息放置模式：滚轮旋转 90°，Shift + 滚轮微调高度偏移
        if (holdingBuildWand && bsm.isPlacing()) {
            boolean isShift = mc.options.keyShift.isDown() || net.minecraft.client.gui.screens.Screen.hasShiftDown();
            if (isShift) {
                int offsetDelta = deltaY > 0 ? 1 : -1;
                bsm.adjustPlacementOffsetY(offsetDelta);
                int currentOffset = bsm.getPlacementOffsetY();
                String sign = currentOffset > 0 ? "+" : "";
                mc.player.displayClientMessage(
                        Component.literal(String.format("§a[全息建筑] 高度偏移: §e%s%d格", sign, currentOffset)),
                        true
                );
            } else {
                bsm.rotatePlacement(deltaY > 0);
                mc.player.displayClientMessage(
                        Component.literal(String.format("§a[全息建筑] 旋转朝向: §e%d°", bsm.getPlacementRotation())),
                        true
                );
            }
            mc.level.playSound(mc.player, mc.player.blockPosition(), net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.2F);
            event.setCanceled(true);
            return;
        }

        boolean holdingDirectorWand = mainHand.is(com.mannequin.registry.ModItems.MANNEQUIN_SPAWN.get())
                || mainHand.is(com.mannequin.registry.ModItems.MANNEQUIN_LYING_SPAWN.get())
                || offHand.is(com.mannequin.registry.ModItems.MANNEQUIN_SPAWN.get())
                || offHand.is(com.mannequin.registry.ModItems.MANNEQUIN_LYING_SPAWN.get());

        boolean isShift = mc.options.keyShift.isDown() || net.minecraft.client.gui.screens.Screen.hasShiftDown();

        // 1. Shift + 滚轮：无级自由缩放准星所指人偶的体型 (5% ~ 2000%)
        if (isShift) {
            long now = System.currentTimeMillis();
            MannequinEntity targetMannequin = findTargetMannequin(mc, 32.0);

            // 粘性目标吸附与丢帧保护：若准星微移脱靶或模型在缩放过程中边界变动，在 1200ms 内持续锁定上一目标
            if (targetMannequin == null && (now - lastScaleTimestamp < 1200L) && activeTargetId != -1) {
                net.minecraft.world.entity.Entity cached = mc.level.getEntity(activeTargetId);
                if (cached instanceof MannequinEntity m && m.isAlive() && !m.isRemoved()) {
                    double distSq = mc.player.distanceToSqr(m);
                    if (distSq < 48.0 * 48.0) {
                        targetMannequin = m;
                    }
                }
            }

            if (targetMannequin != null) {
                activeTargetId = targetMannequin.getId();
                lastScaleTimestamp = now;

                float currentScale = targetMannequin.getScale();
                float factor = (float) Math.pow(1.06, deltaY);
                float newScale = Math.max(0.05F, Math.min(20.0F, currentScale * factor));
                targetMannequin.setScale(newScale);

                // 立即网络同步至服务端并全网广播
                PacketDistributor.sendToServer(
                        new SyncMannequinScalePayload(targetMannequin.getId(), newScale)
                );

                int percent = Math.round(newScale * 100.0F);
                mc.player.displayClientMessage(
                        Component.literal(String.format("§6[人偶体型] 实时缩放: §e%d%% §7(%.2fx)", percent, newScale)),
                        true
                );
                // 彻底拦截滚轮事件，杜绝跳动/切换快捷栏栏位
                event.setCanceled(true);
                return;
            }

            // 防误触安全网：当手持导演工杖且按住 Shift 滚轮时，严格锁定快捷栏，不发生切槽位现象
            if (holdingDirectorWand) {
                event.setCanceled(true);
                return;
            }
        }

        // 2. 上帝视角/自由相机下的滚轮 FOV 变焦 (拉近/推远焦距)
        if (FpvFlightController.INSTANCE.isActive()) {
            FpvFlightController.INSTANCE.adjustFov((float) (-deltaY * 3.0F));
            event.setCanceled(true);
        }
    }

    /**
     * 准星光线投射：从当前视角相机向视线前方发射射线检测人偶实体（最远支持 32 格大范围精准抓取）。
     */
    public MannequinEntity findTargetMannequin(Minecraft mc, double maxDistance) {
        net.minecraft.world.entity.Entity cameraEntity = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        if (cameraEntity == null || mc.level == null) {
            return null;
        }

        Vec3 eyePos = cameraEntity.getEyePosition(1.0F);
        Vec3 viewVec = cameraEntity.getViewVector(1.0F);
        Vec3 reachVec = eyePos.add(viewVec.scale(maxDistance));
        // 放大查询包围盒至 16 格，确保对 10x/20x 超大体型巨物的边缘光线投射也能稳定检索到实体
        net.minecraft.world.phys.AABB searchBox = cameraEntity.getBoundingBox().expandTowards(viewVec.scale(maxDistance)).inflate(16.0);

        MannequinEntity closest = null;
        double closestDistSq = maxDistance * maxDistance;

        for (net.minecraft.world.entity.Entity entity : mc.level.getEntities(cameraEntity, searchBox, e -> e instanceof MannequinEntity)) {
            MannequinEntity mannequin = (MannequinEntity) entity;
            net.minecraft.world.phys.AABB aabb = mannequin.getBoundingBox().inflate(0.5);

            // 当相机或玩家视点已处于超大包围盒内部（放大巨型人偶时），直接命中
            if (aabb.contains(eyePos)) {
                return mannequin;
            }

            java.util.Optional<Vec3> hit = aabb.clip(eyePos, reachVec);
            if (hit.isPresent()) {
                double distSq = eyePos.distanceToSqr(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = mannequin;
                }
            }
        }
        return closest;
    }
}
