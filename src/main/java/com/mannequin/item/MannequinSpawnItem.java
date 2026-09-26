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
 * 漫剧导演专用全功能工杖（Director's Wand / Mannequin Multi-Tool）。
 *
 * <p>业务规则（全面融合生成、体型缩放与回收）：
 * <ul>
 *   <li><b>普通右键地面</b>：放置 100% 标准基准人偶；</li>
 *   <li><b>普通右键人偶</b>：无级/档位轮转缩放人偶体型（25% ~ 1000% 巨物场景）；</li>
 *   <li><b>Shift + 右键人偶</b>：精准回收指定人偶，并联动抹除其运动轨迹；</li>
 *   <li><b>Shift + 右键方块/空气</b>：以目标为中心 16 格范围批量清场，并彻底清理所有被移除人偶的轨迹。</li>
 * </ul>
 */
public final class MannequinSpawnItem extends Item {

    public MannequinSpawnItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();

        // 1. Shift + 右键方块：范围清场与轨迹联动清理
        if (player != null && player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                ClientActionFacade.cleanupRangeTracks(clickedPos, 16.0);
                return InteractionResult.SUCCESS;
            }

            ServerLevel serverLevel = (ServerLevel) level;
            AABB area = new AABB(clickedPos).inflate(16.0);
            List<MannequinEntity> list = level.getEntitiesOfClass(MannequinEntity.class, area);
            int count = list.size();
            for (MannequinEntity mannequin : list) {
                serverLevel.sendParticles(ParticleTypes.POOF, mannequin.getX(), mannequin.getY() + 1.0, mannequin.getZ(), 8, 0.2, 0.5, 0.2, 0.05);
                mannequin.discard();
            }
            if (count > 0) {
                serverLevel.playSound(null, clickedPos, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
                player.displayClientMessage(Component.literal("§e[导演工杖] 范围清场完成！已批量回收周围 §6" + count + " §e个人偶实体及轨迹"), true);
            } else {
                player.displayClientMessage(Component.literal("§7[导演工杖] 周围 16 格内未发现任何人偶实体"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // 2. 普通右键方块：生成 100% 标准基准人偶
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        EntityType<MannequinEntity> type = ModEntityTypes.MANNEQUIN.get();
        MannequinEntity mannequin = type.create(level);
        if (mannequin == null) {
            return InteractionResult.FAIL;
        }

        float yaw = player != null ? player.getYRot() : 0.0F;
        mannequin.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, yaw, 0.0F);
        level.addFreshEntity(mannequin);
        level.playSound(null, spawnPos, SoundEvents.ARMOR_STAND_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player != null) {
            player.displayClientMessage(Component.literal("§a[导演工杖] 已放置标准人偶 (100%)！右键人偶可缩放体型，Shift+右键可回收。"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Shift + 右键空气：以玩家本体为中心 16 格范围清场
        if (player.isShiftKeyDown()) {
            BlockPos playerPos = player.blockPosition();
            if (level.isClientSide()) {
                ClientActionFacade.cleanupRangeTracks(playerPos, 16.0);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            ServerLevel serverLevel = (ServerLevel) level;
            AABB area = new AABB(playerPos).inflate(16.0);
            List<MannequinEntity> list = level.getEntitiesOfClass(MannequinEntity.class, area);
            int count = list.size();
            for (MannequinEntity mannequin : list) {
                serverLevel.sendParticles(ParticleTypes.POOF, mannequin.getX(), mannequin.getY() + 1.0, mannequin.getZ(), 8, 0.2, 0.5, 0.2, 0.05);
                mannequin.discard();
            }
            if (count > 0) {
                serverLevel.playSound(null, playerPos, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
                player.displayClientMessage(Component.literal("§e[导演工杖] 范围清场完成！已批量回收周围 §6" + count + " §e个人偶实体及轨迹"), true);
            } else {
                player.displayClientMessage(Component.literal("§7[导演工杖] 周围 16 格内未发现任何人偶实体"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("§6【漫剧导演工杖】三合一创作工具:"));
        tooltipComponents.add(Component.literal(" §e• 右键地面: §f放置 100% 标准基准人偶"));
        tooltipComponents.add(Component.literal(" §e• 右键人偶: §f循环缩放体型 (25%微缩 ~ 1000%摩天巨物)"));
        tooltipComponents.add(Component.literal(" §c• Shift+右键人偶: §f单体精准回收人偶并清除轨迹"));
        tooltipComponents.add(Component.literal(" §c• Shift+右键地面/空气: §f16 格范围一键清场并清除轨迹"));

        if (FMLEnvironment.dist.isClient() && ClientActionFacade.hasShiftDown()) {
            tooltipComponents.add(Component.empty());
            tooltipComponents.add(Component.literal("§6【导演制片 3 步工作流】:").withStyle(ChatFormatting.BOLD));
            tooltipComponents.add(Component.literal(" §e① 角色染色: §f手持原版染料右键人偶（纯红=主角A，纯蓝=主角B）"));
            tooltipComponents.add(Component.literal(" §e② 附身动捕: §f准星对准人偶按 §b[G]§f 附身，按 §b[K]§f 开启录制，WASD操纵走位"));
            tooltipComponents.add(Component.literal(" §e③ 倒带实拍: §f按 §b[R]§f 倒带回0秒；按 §b[F10]§f 或 [F6] 启动实拍录制 MP4！"));
        } else {
            tooltipComponents.add(Component.literal("§8按住 §e[Shift] §8查看新手制片指南").withStyle(ChatFormatting.ITALIC));
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    private static final class ClientActionFacade {
        private static boolean hasShiftDown() {
            return net.minecraft.client.gui.screens.Screen.hasShiftDown();
        }

        private static void cleanupRangeTracks(BlockPos pos, double radius) {
            com.mannequin.client.timeline.ClientTrackCleanupHelper.cleanupRangeTracks(pos, radius);
        }
    }
}
