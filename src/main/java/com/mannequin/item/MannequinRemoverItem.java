package com.mannequin.item;

import com.mannequin.entity.MannequinEntity;
import com.mannequin.registry.ModEntityTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * 漫剧导演专用人偶回收与清场工杖（兼具全功能工杖特性）。
 */
public final class MannequinRemoverItem extends Item {

    public MannequinRemoverItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();

        // 1. Shift + 右键方块：范围清场与轨迹联动清理
        if (player != null && player.isShiftKeyDown()) {
            DirectorWandHelper.performRangeCleanup(level, player, clickedPos, 16.0);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // 2. 普通右键方块：若附近 2.5 格内有人偶则回收最近的一个；若无则放置新的人偶
        AABB nearArea = new AABB(clickedPos).inflate(2.5);
        List<MannequinEntity> nearList = level.getEntitiesOfClass(MannequinEntity.class, nearArea);
        if (!nearList.isEmpty()) {
            DirectorWandHelper.recycleMannequin(nearList.get(0), player);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // 3. 附近无人偶时右键方块：放置新的人偶
        BlockPos spawnPos = clickedPos.relative(context.getClickedFace());
        float yaw = player != null ? player.getYRot() : 0.0F;
        boolean ok = DirectorWandHelper.spawnMannequin(level, player, spawnPos, yaw);
        return ok ? InteractionResult.sidedSuccess(level.isClientSide()) : InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            DirectorWandHelper.performRangeCleanup(level, player, player.blockPosition(), 16.0);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("§6【漫剧导演工杖】三合一创作工具:"));
        tooltipComponents.add(Component.literal(" §e• 右键地面: §f放置人偶 / 回收就近人偶"));
        tooltipComponents.add(Component.literal(" §e• 右键人偶: §f循环缩放体型 (25%微缩 ~ 1000%摩天巨物)"));
        tooltipComponents.add(Component.literal(" §c• Shift+右键人偶: §f精准回收人偶并清除运动轨迹"));
        tooltipComponents.add(Component.literal(" §c• Shift+右键地面/空气: §f16 格范围一键清场并清除轨迹"));

        if (FMLEnvironment.dist.isClient() && ClientActionFacade.hasShiftDown()) {
            tooltipComponents.add(Component.empty());
            tooltipComponents.add(Component.literal("§6【导演制片指南】:").withStyle(ChatFormatting.BOLD));
            tooltipComponents.add(Component.literal(" §e• 单体回收: §f直接 Shift+右键人偶，瞬间化为白烟回收并清理轨迹"));
            tooltipComponents.add(Component.literal(" §e• 范围清场: §fShift+右键地面或空气，16格内人偶全清"));
            tooltipComponents.add(Component.literal(" §e• 体型变焦: §f直接右键人偶快速缩放"));
        } else {
            tooltipComponents.add(Component.literal("§8按住 §e[Shift] §8查看更多操作说明").withStyle(ChatFormatting.ITALIC));
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    private static final class ClientActionFacade {
        private static boolean hasShiftDown() {
            return net.minecraft.client.gui.screens.Screen.hasShiftDown();
        }

        private static void cleanupTrack(String uuid) {
            com.mannequin.client.timeline.ClientTrackCleanupHelper.cleanupTrack(uuid);
        }

        private static void cleanupRangeTracks(BlockPos pos, double radius) {
            com.mannequin.client.timeline.ClientTrackCleanupHelper.cleanupRangeTracks(pos, radius);
        }
    }
}
