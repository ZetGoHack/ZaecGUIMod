package com.mahiru.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** The menus we know how to decorate, their display names and (when possible) real preview screens. */
public final class MenuInfo {
    public record Entry(String key, String title) {}

    public static final List<Entry> ENTRIES = List.of(
            new Entry("inventory", "Inventory"),
            new Entry("creative", "Creative: item list"),
            new Entry("creative_inventory", "Creative: inventory tab"),
            new Entry("crafting_table", "Crafting table"),
            new Entry("furnace", "Furnace"),
            new Entry("blast_furnace", "Blast furnace"),
            new Entry("smoker", "Smoker"),
            new Entry("anvil", "Anvil"),
            new Entry("enchanting_table", "Enchanting table"),
            new Entry("brewing_stand", "Brewing stand"),
            new Entry("beacon", "Beacon"),
            new Entry("stonecutter", "Stonecutter"),
            new Entry("loom", "Loom"),
            new Entry("smithing", "Smithing table"),
            new Entry("grindstone", "Grindstone"),
            new Entry("cartography_table", "Cartography table"),
            new Entry("crafter", "Crafter"),
            new Entry("dispenser", "Dispenser / dropper"),
            new Entry("hopper", "Hopper"),
            new Entry("shulker_box", "Shulker box"),
            new Entry("villager", "Villager trading"),
            new Entry("horse", "Horse / donkey / mule / llama"),
            new Entry("generic_54_1", "Chest (1 row)"),
            new Entry("generic_54_2", "Chest (2 rows)"),
            new Entry("generic_54_3", "Chest (3 rows)"),
            new Entry("generic_54_4", "Chest (4 rows)"),
            new Entry("generic_54_5", "Chest (5 rows)"),
            new Entry("generic_54_6", "Large chest (6 rows)"));

    public static String title(String key) {
        for (Entry e : ENTRIES) if (e.key.equals(key)) return T.t(e.title);
        return key;
    }

    /** Mask/menu key for an open container screen, or null when we do not decorate it. */
    public static String keyFor(AbstractContainerScreen<?> s) {
        // only vanilla screens: a foreign class with the same simple name must not be cast blindly
        if (!s.getClass().getName().startsWith("net.minecraft.client.gui.screens.")) return null;
        return switch (s.getClass().getSimpleName()) {
            case "AnvilScreen" -> "anvil";
            case "BeaconScreen" -> "beacon";
            case "BlastFurnaceScreen" -> "blast_furnace";
            case "BrewingStandScreen" -> "brewing_stand";
            case "CartographyTableScreen", "CartographyScreen" -> "cartography_table";
            case "CrafterScreen" -> "crafter";
            case "CraftingScreen" -> "crafting_table";
            case "ContainerScreen" -> s instanceof ContainerScreen cs ? "generic_54_" + Math.max(1, Math.min(6, cs.getMenu().getRowCount())) : null;
            case "DispenserScreen" -> "dispenser";
            case "EnchantmentScreen" -> "enchanting_table";
            case "FurnaceScreen" -> "furnace";
            case "GrindstoneScreen" -> "grindstone";
            case "HopperScreen" -> "hopper";
            case "HorseInventoryScreen" -> "horse";
            case "InventoryScreen" -> "inventory";
            case "CreativeModeInventoryScreen" -> s instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen cm && cm.isInventoryOpen() ? "creative_inventory" : "creative";
            case "LoomScreen" -> "loom";
            case "ShulkerBoxScreen" -> "shulker_box";
            case "SmithingScreen" -> "smithing";
            case "SmokerScreen" -> "smoker";
            case "StonecutterScreen" -> "stonecutter";
            case "MerchantScreen" -> "villager";
            default -> null;
        };
    }

    private static MenuType<?> typeFor(String key) {
        return switch (key) {
            case "crafting_table" -> MenuType.CRAFTING;
            case "furnace" -> MenuType.FURNACE;
            case "blast_furnace" -> MenuType.BLAST_FURNACE;
            case "smoker" -> MenuType.SMOKER;
            case "anvil" -> MenuType.ANVIL;
            case "enchanting_table" -> MenuType.ENCHANTMENT;
            case "brewing_stand" -> MenuType.BREWING_STAND;
            case "beacon" -> MenuType.BEACON;
            case "stonecutter" -> MenuType.STONECUTTER;
            case "loom" -> MenuType.LOOM;
            case "smithing" -> MenuType.SMITHING;
            case "grindstone" -> MenuType.GRINDSTONE;
            case "cartography_table" -> MenuType.CARTOGRAPHY_TABLE;
            case "crafter" -> MenuType.CRAFTER_3x3;
            case "dispenser" -> MenuType.GENERIC_3x3;
            case "hopper" -> MenuType.HOPPER;
            case "shulker_box" -> MenuType.SHULKER_BOX;
            case "villager" -> MenuType.MERCHANT;
            case "generic_54_1" -> MenuType.GENERIC_9x1;
            case "generic_54_2" -> MenuType.GENERIC_9x2;
            case "generic_54_3" -> MenuType.GENERIC_9x3;
            case "generic_54_4" -> MenuType.GENERIC_9x4;
            case "generic_54_5" -> MenuType.GENERIC_9x5;
            case "generic_54_6" -> MenuType.GENERIC_9x6;
            default -> null;
        };
    }

    /** A real, live screen instance of the menu for previewing (needs an in-world player), or null. */
    public static Screen createPreview(String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        try {
            Screen s;
            if (key.equals("inventory")) {
                s = new InventoryScreen(mc.player);
            } else if (key.equals("creative") || key.equals("creative_inventory")) {
                s = createCreative(key);
            } else {
                MenuType<?> t = typeFor(key);
                if (t == null) return null;
                // a throw-away inventory: the sample stacks must never end up in the player's real one
                s = make(t, title(key), new Inventory(mc.player));
            }
            return s;
        } catch (Throwable t) {
            Guard.LOG.error("[ZaecGuiMod] could not build a preview screen for {}", key, t);
            return null;
        }
    }

    /**
     * Initialises a preview screen. InventoryScreen.init() swaps itself for the creative screen when the player
     * is in creative, which would replace the studio: show it a throw-away survival game mode instead.
     */
    public static void initPreview(Screen s, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (s instanceof CreativeModeInventoryScreen) { initCreative(s, w, h); return; }
        var real = mc.gameMode;
        try {
            if (s instanceof InventoryScreen && real != null && mc.getConnection() != null)
                mc.gameMode = new net.minecraft.client.multiplayer.MultiPlayerGameMode(mc, mc.getConnection());
            s.init(mc, w, h);
        } finally {
            mc.gameMode = real;
        }
    }

    // ---- the creative screen as a preview. It is not a plain menu screen: its constructor makes itself the player's
    // open container, it keeps the selected tab in a static field, and init() registers a slot listener. All of that
    // is undone right after each call (and the listener in disposePreview), so the real creative screen is unaffected.

    private static final java.util.Map<Screen, CreativeModeTab> CREATIVE_TAB = new java.util.WeakHashMap<>();
    private static java.lang.reflect.Field tabField;

    private static java.lang.reflect.Field tabField() throws ReflectiveOperationException {
        if (tabField == null) {
            var f = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
            f.setAccessible(true);
            tabField = f;
        }
        return tabField;
    }

    private static Screen createCreative(String key) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || p.connection == null || mc.level == null) return null;
        var oldMenu = p.containerMenu;
        try {
            tabField();
            CreativeModeTab want = key.equals("creative_inventory")
                    ? BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(CreativeModeTabs.INVENTORY)
                    : CreativeModeTabs.getDefaultTab();
            Screen s = new CreativeModeInventoryScreen(p, p.connection.enabledFeatures(), mc.options.operatorItemsTab().get());
            CREATIVE_TAB.put(s, want);
            return s;
        } catch (Throwable t) {
            Guard.LOG.warn("[ZaecGuiMod] no real creative preview, using the schematic one", t);
            return null;
        } finally {
            p.containerMenu = oldMenu;
        }
    }

    private static void initCreative(Screen s, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        var real = mc.gameMode;
        var p = mc.player;
        var oldMenu = p == null ? null : p.containerMenu;
        try {
            // the screen refuses to initialise unless the game mode has infinite items
            // (setLocalMode would rewrite the real player's abilities: only claim infinite items)
            mc.gameMode = new net.minecraft.client.multiplayer.MultiPlayerGameMode(mc, mc.getConnection()) {
                @Override
                public boolean hasInfiniteItems() { return true; }
            };
            withCreativeTab(s, () -> {
                s.init(mc, w, h);
                try { CREATIVE_TAB.put(s, (CreativeModeTab) tabField().get(null)); } catch (ReflectiveOperationException ignored) { }
            });
        } finally {
            mc.gameMode = real;
            if (p != null) p.containerMenu = oldMenu;
        }
    }

    /** runs {@code r} with the creative screen's static "selected tab" set to the tab of this preview, then puts the old one back */
    public static void withCreativeTab(Screen s, Runnable r) {
        CreativeModeTab want = CREATIVE_TAB.get(s);
        CreativeModeTab old = null;
        boolean swapped = false;
        if (want != null) {
            try {
                var f = tabField();
                old = (CreativeModeTab) f.get(null);
                f.set(null, want);
                swapped = true;
            } catch (ReflectiveOperationException ignored) { }
        }
        try {
            r.run();
        } finally {
            if (swapped) { try { tabField().set(null, old); } catch (ReflectiveOperationException ignored) { } }
        }
    }

    /** gives back what a preview screen holds (the creative one listens to the player's inventory) */
    public static void disposePreview(Screen s) {
        if (!(s instanceof CreativeModeInventoryScreen)) return;
        CREATIVE_TAB.remove(s);
        try { s.removed(); } catch (Throwable t) { Guard.LOG.warn("[ZaecGuiMod] could not dispose the creative preview", t); }
    }

    private static <T extends AbstractContainerMenu> Screen make(MenuType<T> type, String title, Inventory inv) {
        var factory = MenuScreens.getScreenFactory(type).orElse(null);
        if (factory == null) return null;
        T menu = type.create(0, inv);
        Screen s = (Screen) factory.create(menu, inv, Component.literal(title));
        fillSamples(menu);
        return s;
    }

    /** Sample stacks so the preview looks like a real, lived-in menu. */
    private static void fillSamples(AbstractContainerMenu menu) {
        try {
            // prefer the player's real stacks (copies), fall back to a few placeholders
            var real = Minecraft.getInstance().player.getInventory();
            boolean any = false;
            for (Slot slot : menu.slots) {
                if (slot.container instanceof Inventory && slot.getContainerSlot() < 36) {
                    ItemStack st = real.getItem(slot.getContainerSlot());
                    if (!st.isEmpty()) { slot.set(st.copy()); any = true; }
                }
            }
            if (any) return;
            var items = new net.minecraft.world.item.Item[] {
                    Items.OAK_PLANKS, Items.COBBLESTONE, Items.IRON_INGOT, Items.COAL, Items.BREAD, Items.TORCH };
            int n = 0;
            for (Slot slot : menu.slots) {
                if (slot.container instanceof Inventory && slot.getContainerSlot() >= 27 && slot.getContainerSlot() < 36) {
                    if ((n++) % 3 != 2) slot.set(new ItemStack(items[n % items.length], 64));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private MenuInfo() {}
}
