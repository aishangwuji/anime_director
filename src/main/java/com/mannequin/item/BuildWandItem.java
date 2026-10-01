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

            // 2. 选区模式：
            // Shift + 右键 -> 将玩家当前站立/漂浮位置直接设为角点 B（解决空中/虚空无方块可点的痛点！）
            if (player.isShiftKeyDown()) {
                BlockPos myPos = player.blockPosition();
                bsm.setPosB(myPos);
                level.playSound(player, myPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
                if (bsm.getPosA() != null) {
                    Vec3i size = bsm.getSelectionSize();
                    long volume = (long) size.getX() * size.getY() * size.getZ();
                    player.displayClientMessage(
                            Component.literal(String.format("§a[建筑蓝图仪] 已将你所在位置锁定为角点 B！选区尺寸: §e%d × %d × %d §7(共 %,d 方块) | 空中右键导出",
                                    size.getX(), size.getY(), size.getZ(), volume)),
                            true
                    );
                } else {
                    player.displayClientMessage(
                            Component.literal(String.format("§e[建筑蓝图仪] 已将你所在位置锁定为角点 B: (%d, %d, %d)",
                                    myPos.getX(), myPos.getY(), myPos.getZ())),
                            true
                    );
                }
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            // 3. 选区模式下，若准星没有指向方块（指向空中/虚空）：
            // 若已有角点 A，则根据视线前方 12 格投射设立角点 B！
            HitResult hit = player.pick(20.0D, 0.0F, false);
            if (hit.getType() == HitResult.Type.MISS && bsm.getPosA() != null) {
                Vec3 look = player.getLookAngle();
                Vec3 targetEye = player.getEyePosition().add(look.scale(12.0D));
                BlockPos airPos = BlockPos.containing(targetEye);
                bsm.setPosB(airPos);
                level.playSound(player, airPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.6F);

                Vec3i size = bsm.getSelectionSize();
                long volume = (long) size.getX() * size.getY() * size.getZ();
                player.displayClientMessage(
                        Component.literal(String.format("§a[建筑蓝图仪] 已在视线空中设立角点 B: (%d, %d, %d)！选区尺寸: §e%d × %d × %d §7(共 %,d 方块)",
                                airPos.getX(), airPos.getY(), airPos.getZ(), size.getX(), size.getY(), size.getZ(), volume)),
                        true
                );
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            // 4. 其余情况直接呼出【建筑蓝图库与导出工作台】
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

            // 1. 潜行右键方块：若在放置模式则退出放置模式；选区模式下将自身位置设为角点 B
            if (player.isShiftKeyDown()) {
                if (bsm.isPlacing()) {
                    bsm.clearPlacement();
                    player.displayClientMessage(Component.literal("§e[建筑蓝图仪] 已退出放置模式"), true);
                    level.playSound(player, clickedPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 0.8F);
                    return InteractionResult.SUCCESS;
                } else {
                    BlockPos myPos = player.blockPosition();
                    bsm.setPosB(myPos);
                    level.playSound(player, myPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
                    if (bsm.getPosA() != null) {
                        Vec3i size = bsm.getSelectionSize();
                        long volume = (long) size.getX() * size.getY() * size.getZ();
                        player.displayClientMessage(
                                Component.literal(String.format("§a[建筑蓝图仪] 已将你所在位置锁定为角点 B！选区尺寸: §e%d × %d × %d §7(共 %,d 方块) | 空中右键呼出蓝图库",
                                        size.getX(), size.getY(), size.getZ(), volume)),
                                true
                        );
                    } else {
                        player.displayClientMessage(
                                Component.literal(String.format("§e[建筑蓝图仪] 已将你所在位置锁定为角点 B: (%d, %d, %d)",
                                        myPos.getX(), myPos.getY(), myPos.getZ())),
                                true
                        );
                    }
                    return InteractionResult.SUCCESS;
                }
            }

            // 2. 放置模式：一键在点击方块上方生成落地建筑（附带高度微调）
            if (bsm.isPlacing()) {
                BuildingBlueprintHelper.BlueprintInfo bp = bsm.getSelectedBlueprint();
                BlockPos targetOrigin = clickedPos.relative(context.getClickedFace()).above(bsm.getPlacementOffsetY());
                int rot = bsm.getPlacementRotation();

                // 发送网络包至服务端一键放置
                PacketDistributor.sendToServer(new PlaceBuildingPayload(targetOrigin, bp.fileName(), rot));

                level.playSound(player, targetOrigin, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
                player.displayClientMessage(Component.literal(String.format("§a[建筑蓝图仪] 正在部署建筑「%s」 (旋转: %d°, 高度: %+d)...", bp.name(), rot, bsm.getPlacementOffsetY())), true);
                return InteractionResult.SUCCESS;
            }

            // 3. 选区模式正常右键方块：设定角点 B
            bsm.setPosB(clickedPos);
            level.playSound(player, clickedPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.6F);

            if (bsm.getPosA() != null) {
                Vec3i size = bsm.getSelectionSize();
                long volume = (long) size.getX() * size.getY() * size.getZ();
                player.displayClientMessage(
                        Component.literal(String.format("§a[建筑蓝图仪] 已锁定角点 B！选区尺寸: §e%d × %d × %d §7(共 %,d 方块) | 空中右键呼出蓝图库",
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
        tooltipComponents.add(Component.literal("§e【选区与打包模式】"));
        tooltipComponents.add(Component.literal("§7• §b左键方块§7：设定角点 A (Shift+左键: 以自身当前坐标为 A 点)"));
        tooltipComponents.add(Component.literal("§7• §e右键方块§7：设定角点 B (Shift+右键: 以自身当前坐标为 B 点)"));
        tooltipComponents.add(Component.literal("§7• §a空中左/右键§7：视线空中定点 / 呼出蓝图库"));
        tooltipComponents.add(Component.literal("§7• §cDelete 键§7：一键清空当前选区"));
        tooltipComponents.add(Component.literal("§e【全息放置模式】"));
        tooltipComponents.add(Component.literal("§7• §a左键 / 空中右键§7：90° 旋转全息建筑预览"));
        tooltipComponents.add(Component.literal("§7• §6右键地面方块§7：确认落地部署建筑"));
        tooltipComponents.add(Component.literal("§7• §cShift+右键 / Delete 键§7：退出放置模式"));
        tooltipComponents.add(Component.literal("§8※ 创造模式左键免疫破坏方块，空中/虚空均可自由选点"));
    }
}
