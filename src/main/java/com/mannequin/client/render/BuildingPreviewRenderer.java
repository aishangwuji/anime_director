package com.mannequin.client.render;

import com.mannequin.client.building.BuildingSelectionManager;
import com.mannequin.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 客户端建筑选区线框与放置全息预览渲染器（Building Preview Renderer）。
 */
public final class BuildingPreviewRenderer {

    private BuildingPreviewRenderer() {
    }

    /**
     * 左键点击方块监听：若手持建筑蓝图仪，则拦截破坏方块，并执行设定角点 A 或旋转放置全息。
     */
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(ModItems.BUILD_WAND.get())) {
            // 彻底拦截并取消方块破坏逻辑（双端拦截）
            event.setCanceled(true);

            if (event.getLevel().isClientSide()) {
                BuildingSelectionManager bsm = BuildingSelectionManager.INSTANCE;

                // 1. 若处于全息放置模式，左键点击直接 90° 旋转全息建筑预览！
                if (bsm.isPlacing()) {
                    bsm.rotatePlacement();
                    event.getLevel().playSound(player, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
                    player.displayClientMessage(
                            Component.literal(String.format("§a[建筑蓝图仪] 已旋转全息建筑：%d°", bsm.getPlacementRotation())),
                            true
                    );
                    return;
                }

                // 2. 选区模式：
                if (player.isShiftKeyDown()) {
                    // Shift + 左键：以自身当前位置为角点 A
                    BlockPos myPos = player.blockPosition();
                    bsm.setPosA(myPos);
                    event.getLevel().playSound(player, myPos, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
                    if (bsm.getPosB() != null) {
                        Vec3i size = bsm.getSelectionSize();
                        long volume = (long) size.getX() * size.getY() * size.getZ();
                        player.displayClientMessage(
                                Component.literal(String.format("§b[建筑蓝图仪] 已将你所在位置锁定为角点 A！选区尺寸: §e%d × %d × %d §7(共 %,d 方块)",
                                        size.getX(), size.getY(), size.getZ(), volume)),
                                true
                        );
                    } else {
                        player.displayClientMessage(
                                Component.literal(String.format("§b[建筑蓝图仪] 已将你所在位置锁定为角点 A: (%d, %d, %d)",
                                        myPos.getX(), myPos.getY(), myPos.getZ())),
                                true
                        );
                    }
                } else {
                    // 普通左键方块：设方块为角点 A
                    BlockPos clickedPos = event.getPos();
                    bsm.setPosA(clickedPos);
                    event.getLevel().playSound(player, clickedPos, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 0.8F, 1.4F);

                    if (bsm.getPosB() != null) {
                        Vec3i size = bsm.getSelectionSize();
                        long volume = (long) size.getX() * size.getY() * size.getZ();
                        player.displayClientMessage(
                                Component.literal(String.format("§b[建筑蓝图仪] 已锁定角点 A！选区尺寸: §e%d × %d × %d §7(共 %,d 方块) | 空中右键呼出蓝图库",
                                        size.getX(), size.getY(), size.getZ(), volume)),
                                true
                        );
                    } else {
                        player.displayClientMessage(
                                Component.literal(String.format("§b[建筑蓝图仪] 已设定角点 A: (%d, %d, %d)，请右键设定角点 B (Shift+右键可将自身坐标设为B点)",
                                        clickedPos.getX(), clickedPos.getY(), clickedPos.getZ())),
                                true
                        );
                    }
                }
            }
        }
    }

    /**
     * 左键点击虚空/空气监听：若手持建筑蓝图仪，支持在空中/虚空选定角点 A 或旋转放置全息。
     */
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        if (player == null || !event.getLevel().isClientSide()) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(ModItems.BUILD_WAND.get())) {
            BuildingSelectionManager bsm = BuildingSelectionManager.INSTANCE;

            // 1. 若处于全息放置模式，左键点击直接 90° 旋转全息建筑！
            if (bsm.isPlacing()) {
                bsm.rotatePlacement();
                event.getLevel().playSound(player, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
                player.displayClientMessage(
                        Component.literal(String.format("§a[建筑蓝图仪] 已旋转全息建筑：%d°", bsm.getPlacementRotation())),
                        true
                );
                return;
            }

            // 2. 选区模式：Shift + 左键空气 -> 自身当前坐标为角点 A
            if (player.isShiftKeyDown()) {
                BlockPos myPos = player.blockPosition();
                bsm.setPosA(myPos);
                event.getLevel().playSound(player, myPos, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
                if (bsm.getPosB() != null) {
                    Vec3i size = bsm.getSelectionSize();
                    long volume = (long) size.getX() * size.getY() * size.getZ();
                    player.displayClientMessage(
                            Component.literal(String.format("§b[建筑蓝图仪] 已将你所在位置锁定为角点 A！选区尺寸: §e%d × %d × %d §7(共 %,d 方块)",
                                    size.getX(), size.getY(), size.getZ(), volume)),
                            true
                    );
                } else {
                    player.displayClientMessage(
                            Component.literal(String.format("§b[建筑蓝图仪] 已将你所在位置锁定为角点 A: (%d, %d, %d)",
                                    myPos.getX(), myPos.getY(), myPos.getZ())),
                            true
                    );
                }
                return;
            }

            // 3. 普通左键空气 -> 视线前方 12 格投射设定角点 A
            Vec3 look = player.getLookAngle();
            Vec3 targetEye = player.getEyePosition().add(look.scale(12.0D));
            BlockPos airPos = BlockPos.containing(targetEye);
            bsm.setPosA(airPos);
            event.getLevel().playSound(player, airPos, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
            if (bsm.getPosB() != null) {
                Vec3i size = bsm.getSelectionSize();
                long volume = (long) size.getX() * size.getY() * size.getZ();
                player.displayClientMessage(
                        Component.literal(String.format("§b[建筑蓝图仪] 已在视线空中设立角点 A: (%d, %d, %d)！选区尺寸: §e%d × %d × %d §7(共 %,d 方块)",
                                airPos.getX(), airPos.getY(), airPos.getZ(), size.getX(), size.getY(), size.getZ())),
                        true
                );
            } else {
                player.displayClientMessage(
                        Component.literal(String.format("§b[建筑蓝图仪] 已在视线空中设立角点 A: (%d, %d, %d)，请右键设定角点 B",
                                airPos.getX(), airPos.getY(), airPos.getZ())),
                        true
                );
            }
        }
    }

    /**
     * 世界渲染阶段：绘制三维框选范围与放置全息线框。
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        // 仅在手持蓝图仪时渲染辅助选框与放置全息
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        boolean holdingWand = mainHand.is(ModItems.BUILD_WAND.get()) || offHand.is(ModItems.BUILD_WAND.get());
        if (!holdingWand) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        BuildingSelectionManager bsm = BuildingSelectionManager.INSTANCE;

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // 1. 渲染当前选区线框 (霓虹青)
        if (bsm.hasSelection()) {
            AABB aabb = bsm.getSelectionBoundingBox();
            if (aabb != null) {
                LevelRenderer.renderLineBox(poseStack, lineConsumer, aabb, 0.0F, 0.9F, 1.0F, 0.9F);
            }
        } else if (bsm.getPosA() != null) {
            BlockPos a = bsm.getPosA();
            AABB aBox = new AABB(a.getX(), a.getY(), a.getZ(), a.getX() + 1, a.getY() + 1, a.getZ() + 1);
            LevelRenderer.renderLineBox(poseStack, lineConsumer, aBox, 0.0F, 0.8F, 1.0F, 0.7F);
        } else if (bsm.getPosB() != null) {
            BlockPos b = bsm.getPosB();
            AABB bBox = new AABB(b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1);
            LevelRenderer.renderLineBox(poseStack, lineConsumer, bBox, 1.0F, 0.8F, 0.0F, 0.7F);
        }

        // 2. 渲染放置模式下的地面全息对齐框 (亮绿)
        if (bsm.isPlacing()) {
            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos targetOrigin = bhr.getBlockPos().relative(bhr.getDirection());
                Vec3i size = bsm.getRotatedPlacementSize();

                AABB ghostBox = new AABB(
                        targetOrigin.getX(),
                        targetOrigin.getY(),
                        targetOrigin.getZ(),
                        targetOrigin.getX() + size.getX(),
                        targetOrigin.getY() + size.getY(),
                        targetOrigin.getZ() + size.getZ()
                );

                LevelRenderer.renderLineBox(poseStack, lineConsumer, ghostBox, 0.2F, 1.0F, 0.3F, 0.95F);
            }
        }

        poseStack.popPose();
    }
}
