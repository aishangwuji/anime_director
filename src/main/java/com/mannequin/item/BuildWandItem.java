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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 漫剧片场建筑蓝图仪（Build Wand Item）。
 *
 * <p>专为纯建筑结构的快速打包、便携导出与一键落地部署设计：
 * <ul>
 *   <li><b>选区模式</b>：
 *     <ul>
 *       <li>左键方块：设定三维选区角点 A（Shift+左键：以自身当前位置为 A 点，空中/虚空选点利器）</li>
 *       <li>右键方块：设定三维选区角点 B（Shift+右键：以自身当前位置为 B 点，空中/虚空选点利器）</li>
 *       <li>空中左/右键：视线投射定点 / 呼出建筑蓝图库与导出工作台</li>
 *       <li>Delete 键：一键清空当前选区</li>
 *     </ul>
 *   </li>
 *   <li><b>全息放置模式</b>：
 *     <ul>
 *       <li>左键 / 空中右键：90° 旋转全息建筑预览虚影</li>
 *       <li>右键地面方块：一键确认落地生成建筑</li>
 *       <li>Shift + 右键 / Delete 键：立即退出放置模式</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class BuildWandItem extends Item {

    public BuildWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        // 彻底免疫在创造模式或生存模式下破坏方块
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            BuildingSelectionManager bsm = BuildingSelectionManager.INSTANCE;

            // 1. 若处于全息放置模式：
            if (bsm.isPlacing()) {
                if (player.isShiftKeyDown()) {
                    bsm.clearPlacement();
                    player.displayClientMessage(Component.literal("§e[建筑蓝图仪] 已退出放置模式"), true);
                    level.playSound(player, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 0.8F);
                } else {
                    bsm.rotatePlacement();
                    level.playSound(player, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
                    player.displayClientMessage(
                            Component.literal(String.format("§a[建筑蓝图仪] 已旋转全息建筑：%d°", bsm.getPlacementRotation())),
                            true
                    );
                }
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            // 2. 选区模式：右键空气永远是设定对角点 B（Shift = 自身当前位置，普通 = 视线前方16格）
            BlockPos targetPos;
            if (player.isShiftKeyDown()) {
                targetPos = player.blockPosition();
            } else {
                Vec3 look = player.getLookAngle();
                Vec3 targetEye = player.getEyePosition().add(look.scale(16.0D));
                targetPos = BlockPos.containing(targetEye);
            }
            bsm.setPosB(targetPos);
            level.playSound(player, targetPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.6F);

            if (bsm.getPosA() != null) {
                Vec3i size = bsm.getSelectionSize();
                long volume = (long) size.getX() * size.getY() * size.getZ();
                player.displayClientMessage(
                        Component.literal(String.format("§a[建筑蓝图] 已设定角点 B: (%d, %d, %d)！选区尺寸: §e%d × %d × %d §7(共 %,d 方块) | 按【Enter】保存",
                                targetPos.getX(), targetPos.getY(), targetPos.getZ(), size.getX(), size.getY(), size.getZ(), volume)),
                        true
                );
            } else {
                player.displayClientMessage(
                        Component.literal(String.format("§a[建筑蓝图] 已设定角点 B: (%d, %d, %d)，请左键设定角点 A",
                                targetPos.getX(), targetPos.getY(), targetPos.getZ())),
                        true
                );
            }
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

            // 1. 放置模式：落地部署或退出
            if (bsm.isPlacing()) {
                if (player.isShiftKeyDown()) {
                    bsm.clearPlacement();
                    player.displayClientMessage(Component.literal("§e[建筑蓝图仪] 已退出放置模式"), true);
                    level.playSound(player, clickedPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 0.8F);
                    return InteractionResult.SUCCESS;
                }

                BuildingBlueprintHelper.BlueprintInfo bp = bsm.getSelectedBlueprint();
                BlockPos targetOrigin = clickedPos.relative(context.getClickedFace()).above(bsm.getPlacementOffsetY());
                int rot = bsm.getPlacementRotation();

                PacketDistributor.sendToServer(new PlaceBuildingPayload(targetOrigin, bp.fileName(), rot));

                level.playSound(player, targetOrigin, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
                player.displayClientMessage(Component.literal(String.format("§a[建筑蓝图仪] 正在部署建筑「%s」 (旋转: %d°, 高度: %+d)...", bp.name(), rot, bsm.getPlacementOffsetY())), true);
                return InteractionResult.SUCCESS;
            }

            // 2. 选区模式：右键方块永远是设定对角点 B（Shift = 自身当前位置，普通 = 点击的方块）
            BlockPos targetPos = player.isShiftKeyDown() ? player.blockPosition() : clickedPos;
            bsm.setPosB(targetPos);
            level.playSound(player, targetPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.6F);

            if (bsm.getPosA() != null) {
                Vec3i size = bsm.getSelectionSize();
                long volume = (long) size.getX() * size.getY() * size.getZ();
                player.displayClientMessage(
                        Component.literal(String.format("§a[建筑蓝图] 已设定角点 B: (%d, %d, %d)！选区尺寸: §e%d × %d × %d §7(共 %,d 方块) | 按【Enter】保存",
                                targetPos.getX(), targetPos.getY(), targetPos.getZ(), size.getX(), size.getY(), size.getZ(), volume)),
                        true
                );
            } else {
                player.displayClientMessage(
                        Component.literal(String.format("§a[建筑蓝图] 已设定角点 B: (%d, %d, %d)，请左键设定角点 A",
                                targetPos.getX(), targetPos.getY(), targetPos.getZ())),
                        true
                );
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("§6★ 片场建筑蓝图仪 (Studio Builder Wand)"));
        tooltipComponents.add(Component.literal("§7• §b左键方块/空气§7：设定角点 A (Shift+左键: 自身坐标)"));
        tooltipComponents.add(Component.literal("§7• §e右键方块/空气§7：设定角点 B (Shift+右键: 自身坐标)"));
        tooltipComponents.add(Component.literal("§7• §aEnter 键§7：保存打包选区为 .nbt 蓝图"));
        tooltipComponents.add(Component.literal("§7• §cDelete 键§7：一键清空当前选区"));
        tooltipComponents.add(Component.literal("§7• §d放置模式§7：滚轮旋转 | Shift+滚轮高度微调 | 右键地面部署"));
    }
}
