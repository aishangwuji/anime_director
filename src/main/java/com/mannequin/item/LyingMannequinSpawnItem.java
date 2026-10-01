package com.mannequin.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * 漫剧导演专用卧式工杖（Lying Director's Wand / Horizontal Stand-in Tool）。
 *
 * <p>业务规则：
 * <ul>
 *   <li><b>普通右键地面</b>：放置水平平躺圆柱替身人偶；</li>
 *   <li><b>普通右键人偶</b>：精准回收指定人偶并清除对应轨迹；</li>
 *   <li><b>Shift + 右键方块/空气</b>：以目标为中心 16 格范围批量清场，并彻底清理轨迹。</li>
 * </ul>
 */
public final class LyingMannequinSpawnItem extends Item {

    public LyingMannequinSpawnItem(Item.Properties properties) {
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

        // 2. 普通右键方块：生成水平平躺圆柱替身
        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        float yaw = player != null ? player.getYRot() : 0.0F;
        boolean ok = DirectorWandHelper.spawnLyingMannequin(level, player, spawnPos, yaw);
        return ok ? InteractionResult.sidedSuccess(level.isClientSide()) : InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Shift + 右键空气：以玩家本体为中心 16 格范围清场
        if (player.isShiftKeyDown()) {
            DirectorWandHelper.performRangeCleanup(level, player, player.blockPosition(), 16.0);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        return super.use(level, player, hand);
    }

    private static final boolean IS_CLIENT = FMLEnvironment.dist.isClient();

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("§6【卧式导演工杖】漫剧平躺替身专用工具:"));
        tooltipComponents.add(Component.literal(" §e• 右键地面: §f放置水平平躺圆柱替身"));
        tooltipComponents.add(Component.literal(" §c• 右键人偶: §f单体回收人偶并清除对应轨迹"));
        tooltipComponents.add(Component.literal(" §d• 对准人偶+Shift+滚轮: §f无级缩放体型 (5%微缩 ~ 2000%巨像)"));
        tooltipComponents.add(Component.literal(" §c• Shift+右键地面/空气: §f16 格范围一键清场并清除轨迹"));

        if (IS_CLIENT && ClientActionFacade.hasShiftDown()) {
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
    }
}
