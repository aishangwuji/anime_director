package com.mannequin.registry;

import com.mannequin.MannequinMod;
import com.mannequin.item.MannequinSpawnItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the mod's items.
 */
public final class ModItems {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MannequinMod.MOD_ID);

    public static final DeferredItem<Item> MANNEQUIN_SPAWN =
            ITEMS.register("mannequin_spawn", () -> new MannequinSpawnItem(new Item.Properties()));

    /**
     * 漫剧导演人偶回收/清场魔杖道具。
     */
    public static final DeferredItem<Item> MANNEQUIN_REMOVER =
            ITEMS.register("mannequin_remover", () -> new com.mannequin.item.MannequinRemoverItem(new Item.Properties().stacksTo(1)));

    /**
     * 漫剧导演制作实训手册道具。
     */
    public static final DeferredItem<Item> DIRECTOR_GUIDEBOOK =
            ITEMS.register("director_guidebook", () -> new com.mannequin.item.DirectorGuidebookItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS || event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(MANNEQUIN_SPAWN);
            event.accept(MANNEQUIN_REMOVER);
            event.accept(DIRECTOR_GUIDEBOOK);
        }
    }
}
