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
                    // Shift + 右键 -> 退出放置模式
                    bsm.clearPlacement();
                    player.displayClientMessage(Component.literal("§e[建筑蓝图仪] 已退出放置模式"), true);
                    level.playSound(player, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 0.8F);
                } else {
                    // 普通空中右键 -> 90° 旋转全息建筑
                    bsm.rotatePlacement();
                    level.playSound(player, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
                    player.displayClientMessage(
                            Component.literal(String.format("§a[建筑蓝图仪] 已旋转全息建筑：%d° (右键地面方块落地部署)", bsm.getPlacementRotation())),
                            true
                    );
                }
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            // 2. 若选区已就绪闭合（LOCKED_READY） -> 空中右键直接呼出【选区打包导出界面】！
            if (bsm.getSelectionState() == BuildingSelectionManager.SelectionState.LOCKED_READY) {
                openExportScreen();
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            // 3. 若正在选角点 B（SELECTING_B）：空中右键根据视线或自身位置设定角点 B！
            if (bsm.getSelectionState() == BuildingSelectionManager.SelectionState.SELECTING_B) {
                BlockPos targetPos;
                if (player.isShiftKeyDown()) {
                    targetPos = player.blockPosition();
                } else {
                    Vec3 look = player.getLookAngle();
                    Vec3 targetEye = player.getEyePosition().add(look.scale(16.0D));
                    targetPos = BlockPos.containing(targetEye);
                }
                bsm.lockSelection(targetPos);
                level.playSound(player, targetPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.6F);

                Vec3i size = bsm.getSelectionSize();
                long volume = (long) size.getX() * size.getY() * size.getZ();
                player.displayClientMessage(
                        Component.literal(String.format("§a[建筑蓝图] ✔ 选区已闭合锁定！尺寸: §e%d × %d × %d §7(共 %,d 方块) | 请按【Enter / 空中右键】打包保存",
                                size.getX(), size.getY(), size.getZ(), volume)),
                        true
                );
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            // 4. 若没有任何选区（EMPTY） -> 空中右键直接打开【建筑蓝图库】浏览！
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

                // 发送网络包至服务端一键放置
                PacketDistributor.sendToServer(new PlaceBuildingPayload(targetOrigin, bp.fileName(), rot));

                level.playSound(player, targetOrigin, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
                player.displayClientMessage(Component.literal(String.format("§a[建筑蓝图仪] 正在部署建筑「%s」 (旋转: %d°, 高度: %+d)...", bp.name(), rot, bsm.getPlacementOffsetY())), true);
                return InteractionResult.SUCCESS;
            }

            // 2. 选区模式：
            // A. 若选区已锁定就绪（LOCKED_READY）：防误触保护！不改动任何选区，并提醒如何保存或重选
            if (bsm.getSelectionState() == BuildingSelectionManager.SelectionState.LOCKED_READY) {
                Vec3i size = bsm.getSelectionSize();
                long volume = (long) size.getX() * size.getY() * size.getZ();
                level.playSound(player, clickedPos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
                player.displayClientMessage(
                        Component.literal(String.format("§e[建筑蓝图] 选区已锁定 (%d×%d×%d 共 %,d 方块)！按【Enter / 空中右键】保存，【Ctrl+左键 / Delete】清空重选",
                                size.getX(), size.getY(), size.getZ(), volume)),
                        true
                );
                return InteractionResult.SUCCESS;
            }

            // B. 若正在选角点 B（SELECTING_B）：点击方块锁定为角点 B，选区闭合！
            if (bsm.getSelectionState() == BuildingSelectionManager.SelectionState.SELECTING_B) {
                BlockPos targetPos = player.isShiftKeyDown() ? player.blockPosition() : clickedPos;
                bsm.lockSelection(targetPos);
                level.playSound(player, targetPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.6F);

                Vec3i size = bsm.getSelectionSize();
                long volume = (long) size.getX() * size.getY() * size.getZ();
                player.displayClientMessage(
                        Component.literal(String.format("§a[建筑蓝图] ✔ 选区已闭合锁定！尺寸: §e%d × %d × %d §7(共 %,d 方块) | 请按【Enter / 空中右键】打包保存",
                                size.getX(), size.getY(), size.getZ(), volume)),
                        true
                );
                return InteractionResult.SUCCESS;
            }

            // C. 若尚未选任何点（EMPTY）：
            // 贴心体验：右键点击方块也可以直接作为起始角点 A！
            BlockPos targetPos = player.isShiftKeyDown() ? player.blockPosition() : clickedPos;
            bsm.startNewSelection(targetPos);
            level.playSound(player, targetPos, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
            player.displayClientMessage(
                    Component.literal(String.format("§b[建筑蓝图] 已设定起始角点 A: (%d, %d, %d)！请对角【右键】设定对角点 B",
                            targetPos.getX(), targetPos.getY(), targetPos.getZ())),
                    true
            );
        }

        return InteractionResult.SUCCESS;
    }

    private void openLibraryScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new BuildingLibraryScreen(BuildingLibraryScreen.Tab.LIBRARY));
        }
    }

    private void openExportScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new BuildingLibraryScreen(BuildingLibraryScreen.Tab.EXPORT));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("§6★ 片场建筑蓝图仪 (Studio Builder Wand)"));
        tooltipComponents.add(Component.literal("§e【选区与打包模式】"));
        tooltipComponents.add(Component.literal("§7• §b左键方块/空气§7：设定起始角点 A (Shift+左键: 自身坐标)"));
        tooltipComponents.add(Component.literal("§7• §e右键方块/空气§7：设定对角点 B (Shift+右键: 自身坐标)"));
        tooltipComponents.add(Component.literal("§7• §aEnter / 空中右键 / Ctrl+S§7：选区就绪后立即弹出打包保存窗口"));
        tooltipComponents.add(Component.literal("§7• §cCtrl+左键 / Delete§7：重设起始点 A / 一键清空重选"));
        tooltipComponents.add(Component.literal("§e【全息放置模式】"));
        tooltipComponents.add(Component.literal("§7• §a滚轮滑动§7：90° 旋转建筑全息"));
        tooltipComponents.add(Component.literal("§7• §bShift+滚轮§7：垂直高度微调 (-20~+20 格)"));
        tooltipComponents.add(Component.literal("§7• §6右键地面方块§7：确认落地部署建筑"));
        tooltipComponents.add(Component.literal("§7• §cShift+右键 / Delete§7：退出放置模式"));
        tooltipComponents.add(Component.literal("§8※ 创造模式左键免疫破坏方块，虚空/空中选点自由流畅"));
    }
}
