package com.mannequin.item;

import com.mannequin.building.BuildingBlueprintHelper;
import com.mannequin.client.building.BuildingSelectionManager;
import com.mannequin.client.gui.BuildingLibraryScreen;
import com.mannequin.network.PlaceBuildingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 漫剧片场建筑蓝图仪（Build Wand Item）。
 *
 * <p>专为纯建筑结构的快速打包、便携导出与一键落地部署设计：
 * <ul>
 *   <li><b>左键方块</b>：设定建筑三维选区角点 A；</li>
 *   <li><b>右键方块</b>：设定建筑三维选区角点 B（若已在放置模式，则直接在此点一键生成建筑）；</li>
 *   <li><b>空中右键</b>：呼出【建筑蓝图库与打包导出工作台】；</li>
 *   <li><b>Shift + 右键方块</b>：退出当前放置模式 / 清空选区。</li>
 * </ul>
 */
public class BuildWandItem extends Item {

    public BuildWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            openLibraryScreen();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();

        if (player == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            BuildingSelectionManager bsm = BuildingSelectionManager.INSTANCE;

            // 1. 潜行右键：若在放置模式则退出放置模式；否则清空当前选区
            if (player.isShiftKeyDown()) {
                if (bsm.isPlacing()) {
                    bsm.clearPlacement();
                    player.displayClientMessage(Component.literal("§e[建筑蓝图仪] 已退出放置模式"), true);
                } else if (bsm.hasSelection()) {
                    bsm.clearSelection();
                    player.displayClientMessage(Component.literal("§e[建筑蓝图仪] 已清空当前框选区域"), true);
                }
                level.playSound(player, clickedPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 0.8F);
                return InteractionResult.SUCCESS;
            }

            // 2. 放置模式：一键在点击方块上方生成落地建筑
            if (bsm.isPlacing()) {
                BuildingBlueprintHelper.BlueprintInfo bp = bsm.getSelectedBlueprint();
                BlockPos targetOrigin = clickedPos.relative(context.getClickedFace());
                int rot = bsm.getPlacementRotation();

                // 发送网络包至服务端一键放置
                PacketDistributor.sendToServer(new PlaceBuildingPayload(targetOrigin, bp.fileName(), rot));

                level.playSound(player, targetOrigin, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
                player.displayClientMessage(Component.literal(String.format("§a[建筑蓝图仪] 正在部署建筑「%s」 (旋转: %d°)...", bp.name(), rot)), true);
                return InteractionResult.SUCCESS;
            }

            // 3. 正常右键：设定角点 B
            bsm.setPosB(clickedPos);
            level.playSound(player, clickedPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.6F);

            if (bsm.getPosA() != null) {
                Vec3i size = bsm.getSelectionSize();
                long volume = (long) size.getX() * size.getY() * size.getZ();
                player.displayClientMessage(
                        Component.literal(String.format("§a[建筑蓝图仪] 已锁定角点 B！选区尺寸: §e%d × %d × %d §7(共 %,d 方块) | 空中右键导出",
                                size.getX(), size.getY(), size.getZ(), volume)),
                        true
                );
            } else {
                player.displayClientMessage(
                        Component.literal(String.format("§e[建筑蓝图仪] 已设定角点 B: (%d, %d, %d)，请左键方块设定角点 A",
                                clickedPos.getX(), clickedPos.getY(), clickedPos.getZ())),
                        true
                );
            }
        }

        return InteractionResult.SUCCESS;
    }

    private void openLibraryScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new BuildingLibraryScreen());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("§6★ 片场纯建筑打包与一键部署工具"));
        tooltipComponents.add(Component.literal("§7• §b左键方块§7：设定三维选区角点 A"));
        tooltipComponents.add(Component.literal("§7• §e右键方块§7：设定三维选区角点 B（或一键部署建筑）"));
        tooltipComponents.add(Component.literal("§7• §a空中右键§7：呼出建筑蓝图库与导出面板"));
        tooltipComponents.add(Component.literal("§7• §cShift + 右键§7：取消放置模式 / 清空选区"));
        tooltipComponents.add(Component.literal("§8※ 严格专注于纯建筑与方块结构迁移，不打包演播层数据"));
    }
}
