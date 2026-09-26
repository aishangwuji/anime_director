package com.mannequin.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * 漫剧导演指南手册道具（Director Guidebook Item）。
 *
 * <p>创作者手持本手册右键使用，即可在游戏内即时呼出《漫剧导演制作实训手册》交互界面。
 * 针对服务端物理隔离进行了安全封装，避免专用服务器加载图形类引发 NoClassDefFoundError。
 */
public final class DirectorGuidebookItem extends Item {

    public DirectorGuidebookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            // 通过环境守卫与单独的客户端门面类隔离客户端图形调用，保证服务端类加载安全
            ClientActionFacade.openTutorial();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * 客户端专属调用门面（延迟类加载保证服务端安全）。
     */
    private static final class ClientActionFacade {
        private static void openTutorial() {
            if (FMLEnvironment.dist.isClient()) {
                net.minecraft.client.Minecraft.getInstance().setScreen(new com.mannequin.client.gui.tutorial.DirectorTutorialScreen());
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("§7右键打开 §6漫剧导演制作实训手册§7。"));
        tooltipComponents.add(Component.literal("§e内含角色染色、提线动捕、穿越机实拍与 AIGC 提示词教程。").withStyle(ChatFormatting.ITALIC));
        tooltipComponents.add(Component.literal("§8也可以在游戏内随时按快捷键 §b[H] §8呼出教程").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
