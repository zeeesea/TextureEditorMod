package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.texture.ItemTextureExtractor;
import com.zeeesea.textureeditor.texture.TextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import com.zeeesea.textureeditor.util.BlockFilter;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Browse screen showing all blocks, items, and mobs in a grid with tabs and search.
 * Clicking an entry opens the corresponding editor.
 */
public class BrowseScreen extends Screen {

    private enum Tab { ALL, BLOCKS, ITEMS, MOBS, GUI, SKY }

    private Tab currentTab = Tab.ALL;
    private TextFieldWidget searchField;
    private String searchQuery = "";

    // All entries
    private List<BrowseEntry> allEntries;
    private List<BrowseEntry> filteredEntries = new ArrayList<>();

    // Grid layout
    private static final int CELL_SIZE = 36;
    private static final int CELL_PADDING = 4;
    private static final int GRID_TOP = 60;
    private static final int GRID_SIDE_MARGIN = 10;
    private int columns;
    private int visibleRows;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Tab buttons
    private ButtonWidget tabAll, tabBlocks, tabItems, tabMobs, tabGui, tabSky;

    public BrowseScreen() {
        super(Text.literal("Texture Browser"));
    }

    @Override
    protected void init() {
        // Build entries list on first init
        if (allEntries == null) {
            allEntries = buildEntries();
        }

        // Tab buttons
        int tabY = 5;
        int guiScale = (int) net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor();

        if (guiScale >= 5) {
            // Alle Tabs als ein Cycle-Button
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Tab: " + currentTab.name()),
                    btn -> {
                        Tab[] tabs = Tab.values();
                        // SKY überspringen (öffnet extra screen)
                        Tab next = tabs[(currentTab.ordinal() + 1) % (tabs.length - 1)];
                        switchTab(next);
                        btn.setMessage(Text.literal("Tab: " + next.name()));
                    }
            ).position(10, tabY).size(80, 20).build());

            // Sky Button separat
            addDrawableChild(ButtonWidget.builder(Text.literal("Sky"), btn -> client.setScreen(new SkyEditorScreen(this)))
                    .position(94, tabY).size(40, 20).build());

            // Referenzen für den Tab-Underline-Indikator setzen (brauchen wir für render())
            tabAll = tabBlocks = tabItems = tabMobs = tabGui = null;
            tabSky = null;
        } else {
            // Normal: alle 6 Buttons
            int tabX = 10;
            int tabW = 50;
            tabAll = addDrawableChild(ButtonWidget.builder(Text.literal("All"), btn -> switchTab(Tab.ALL))
                    .position(tabX, tabY).size(tabW, 20).build());
            tabX += tabW + 4;
            tabBlocks = addDrawableChild(ButtonWidget.builder(Text.literal("Blocks"), btn -> switchTab(Tab.BLOCKS))
                    .position(tabX, tabY).size(tabW, 20).build());
            tabX += tabW + 4;
            tabItems = addDrawableChild(ButtonWidget.builder(Text.literal("Items"), btn -> switchTab(Tab.ITEMS))
                    .position(tabX, tabY).size(tabW, 20).build());
            tabX += tabW + 4;
            tabMobs = addDrawableChild(ButtonWidget.builder(Text.literal("Mobs"), btn -> switchTab(Tab.MOBS))
                    .position(tabX, tabY).size(tabW, 20).build());
            tabX += tabW + 4;
            tabGui = addDrawableChild(ButtonWidget.builder(Text.literal("GUI"), btn -> switchTab(Tab.GUI))
                    .position(tabX, tabY).size(tabW, 20).build());
            tabX += tabW + 4;
            tabSky = addDrawableChild(ButtonWidget.builder(Text.literal("Sky"), btn -> client.setScreen(new SkyEditorScreen(this)))
                    .position(tabX, tabY).size(tabW, 20).build());
        }

        // Search field
        searchField = new TextFieldWidget(this.textRenderer, this.width / 2 - 80, 32, 160, 18, Text.literal("Search"));
        searchField.setMaxLength(64);
        searchField.setText(searchQuery);
        searchField.setChangedListener(text -> {
            searchQuery = text.toLowerCase();
            applyFilter();
            scrollOffset = 0;
        });
        addDrawableChild(searchField);

        // Close button
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cClose"), btn -> this.close())
                .position(this.width - 65, 5).size(60, 20).build());

        // Export button
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a76Export"), btn -> client.setScreen(new ExportScreen(this)))
                .position(this.width - 130, 5).size(60, 20).build());

        // Settings button
        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), btn -> client.setScreen(new SettingsScreen(this)))
                .position(this.width - 195, 5).size(60, 20).build());

        // Calculate grid layout
        columns = Math.max(1, (this.width - GRID_SIDE_MARGIN * 2) / (CELL_SIZE + CELL_PADDING));
        visibleRows = (this.height - GRID_TOP - 10) / (CELL_SIZE + CELL_PADDING);

        applyFilter();
    }

    private void switchTab(Tab tab) {
        currentTab = tab;
        scrollOffset = 0;
        applyFilter();
    }

    private void applyFilter() {
        filteredEntries = allEntries.stream()
                .filter(e -> {
                    if (currentTab == Tab.BLOCKS && e.type != EntryType.BLOCK) return false;
                    if (currentTab == Tab.ITEMS && e.type != EntryType.ITEM) return false;
                    if (currentTab == Tab.MOBS && e.type != EntryType.MOB) return false;
                    if (currentTab == Tab.GUI && e.type != EntryType.GUI) return false;
                    if (currentTab == Tab.ALL && e.type == EntryType.GUI) return false; // GUI only shows in GUI tab
                    if (!searchQuery.isEmpty() && !e.name.toLowerCase().contains(searchQuery)
                            && !e.id.toString().toLowerCase().contains(searchQuery)) return false;
                    return true;
                })
                .collect(Collectors.toList());

        int totalRows = (filteredEntries.size() + columns - 1) / columns;
        maxScroll = Math.max(0, totalRows - visibleRows);
    }

    private List<BrowseEntry> buildEntries() {
        List<BrowseEntry> entries = new ArrayList<>();
        Set<Identifier> addedItems = new HashSet<>();

        // Add blocks (only editable ones - full cubes and simple flat models)
        for (Block block : Registries.BLOCK) {
            Identifier blockId = Registries.BLOCK.getId(block);
            if (blockId.getPath().equals("air")) continue;
            if (!BlockFilter.isEditableBlock(block.getDefaultState())) continue;
            ItemStack stack = new ItemStack(block);
            if (stack.isEmpty()) continue;
            entries.add(new BrowseEntry(
                    blockId,
                    block.getName().getString(),
                    EntryType.BLOCK,
                    stack
            ));
            addedItems.add(Registries.ITEM.getId(block.asItem()));
        }

        // Add items (that aren't block items)
        for (Item item : Registries.ITEM) {
            Identifier itemId = Registries.ITEM.getId(item);
            if (addedItems.contains(itemId)) continue;
            if (item instanceof BlockItem) continue;
            ItemStack stack = new ItemStack(item);

            EntryType type = (item instanceof SpawnEggItem) ? EntryType.MOB : EntryType.ITEM;
            entries.add(new BrowseEntry(
                    itemId,
                    item.getName().getString(),
                    type,
                    stack
            ));
        }

        // Add GUI texture entries
        entries.addAll(buildGuiEntries());
        entries.addAll(buildNotNormalEntries());

        return entries;
    }

    /**
     * Builds entries for Minecraft GUI/HUD textures that can be edited.
     */
    private List<BrowseEntry> buildGuiEntries() {
        List<BrowseEntry> entries = new ArrayList<>();
        // HUD elements
        addGUIEntry(entries, "gui/sprites/hud/hotbar", "Hotbar");
        addGUIEntry(entries, "gui/sprites/hud/hotbar_selection", "Hotbar Selection");
        addGUIEntry(entries, "gui/sprites/hud/crosshair", "Crosshair");
        addGUIEntry(entries, "gui/sprites/hud/experience_bar_background", "XP Bar Background");
        addGUIEntry(entries, "gui/sprites/hud/experience_bar_progress", "XP Bar Progress");
        addGUIEntry(entries, "gui/sprites/hud/armor_empty", "Armor Empty");
        addGUIEntry(entries, "gui/sprites/hud/armor_half", "Armor Half");
        addGUIEntry(entries, "gui/sprites/hud/armor_full", "Armor Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/full", "Heart Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/half", "Heart Half");
        addGUIEntry(entries, "gui/sprites/hud/heart/container", "Heart Container");
        addGUIEntry(entries, "gui/sprites/hud/food_empty", "Food Empty");
        addGUIEntry(entries, "gui/sprites/hud/food_half", "Food Half");
        addGUIEntry(entries, "gui/sprites/hud/food_full", "Food Full");
        addGUIEntry(entries, "gui/sprites/hud/air", "Air Bubble");
        addGUIEntry(entries, "gui/sprites/hud/air_bursting", "Air Bubble Bursting");
        // Container screens
        addGUIEntry(entries, "gui/container/inventory", "Inventory");
        addGUIEntry(entries, "gui/container/crafting_table", "Crafting Table");
        addGUIEntry(entries, "gui/container/furnace", "Furnace");
        addGUIEntry(entries, "gui/container/blast_furnace", "Blast Furnace");
        addGUIEntry(entries, "gui/container/smoker", "Smoker");
        addGUIEntry(entries, "gui/container/generic_54", "Large Chest");
        addGUIEntry(entries, "gui/container/shulker_box", "Shulker Box UI");
        addGUIEntry(entries, "gui/container/dispenser", "Dispenser/Dropper");
        addGUIEntry(entries, "gui/container/hopper", "Hopper");
        addGUIEntry(entries, "gui/container/brewing_stand", "Brewing Stand");
        addGUIEntry(entries, "gui/container/enchanting_table", "Enchanting Table");
        addGUIEntry(entries, "gui/container/anvil", "Anvil");
        addGUIEntry(entries, "gui/container/beacon", "Beacon");
        addGUIEntry(entries, "gui/container/villager", "Villager Trading");
        addGUIEntry(entries, "gui/container/grindstone", "Grindstone");
        addGUIEntry(entries, "gui/container/loom", "Loom");
        addGUIEntry(entries, "gui/container/cartography_table", "Cartography Table");
        addGUIEntry(entries, "gui/container/stonecutter", "Stonecutter");
        addGUIEntry(entries, "gui/container/smithing", "Smithing Table");
        addGUIEntry(entries, "gui/container/creative_inventory/tabs", "Creative Tabs");
        // General GUI
        addGUIEntry(entries, "gui/widgets", "Widgets (Buttons)");
        addGUIEntry(entries, "gui/title/minecraft", "Title Logo");
        addGUIEntry(entries, "gui/title/edition", "Edition Badge");
        return entries;
    }

    private List<BrowseEntry> buildNotNormalEntries() {
        List<BrowseEntry> entries = new ArrayList<>();

        // Boats
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/boat/oak.png"), "Oak Boat", EntryType.MOB, new ItemStack(net.minecraft.item.Items.OAK_BOAT)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/boat/spruce.png"), "Spruce Boat", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SPRUCE_BOAT)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/boat/birch.png"), "Birch Boat", EntryType.MOB, new ItemStack(net.minecraft.item.Items.BIRCH_BOAT)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/boat/jungle.png"), "Jungle Boat", EntryType.MOB, new ItemStack(net.minecraft.item.Items.JUNGLE_BOAT)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/boat/acacia.png"), "Acacia Boat", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ACACIA_BOAT)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/boat/dark_oak.png"), "Dark Oak Boat", EntryType.MOB, new ItemStack(net.minecraft.item.Items.DARK_OAK_BOAT)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/boat/mangrove.png"), "Mangrove Boat", EntryType.MOB, new ItemStack(net.minecraft.item.Items.MANGROVE_BOAT)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/boat/cherry.png"), "Cherry Boat", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CHERRY_BOAT)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/boat/bamboo.png"), "Bamboo Raft", EntryType.MOB, new ItemStack(net.minecraft.item.Items.BAMBOO_RAFT)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/boat/pale_oak.png"), "Pale Oak Boat", EntryType.MOB, new ItemStack(net.minecraft.item.Items.PALE_OAK_BOAT)));

        // Minecart
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/minecart.png"), "Minecart", EntryType.MOB, new ItemStack(net.minecraft.item.Items.MINECART)));

        // Elytra
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/elytra.png"), "Elytra", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ELYTRA)));

        // Sheep Fur
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/sheep/sheep_fur.png"), "Sheep Fur", EntryType.MOB, null));

        // Wolf
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf.png"), "Wolf", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_angry.png"), "Wolf (Angry)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_tame.png"), "Wolf (Tame)", EntryType.MOB, null));

        // Cat variants
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/tabby.png"), "Cat (Tabby)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/black.png"), "Cat (Black)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/british_shorthair.png"), "Cat (British Shorthair)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/calico.png"), "Cat (Calico)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/jellie.png"), "Cat (Jellie)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/ocelot.png"), "Cat (Ocelot)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/persian.png"), "Cat (Persian)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/ragdoll.png"), "Cat (Ragdoll)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/red.png"), "Cat (Red)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/siamese.png"), "Cat (Siamese)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/white.png"), "Cat (White)", EntryType.MOB, null));

        // Horse variants
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_white.png"), "Horse (White)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_creamy.png"), "Horse (Creamy)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_chestnut.png"), "Horse (Chestnut)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_brown.png"), "Horse (Brown)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_black.png"), "Horse (Black)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_gray.png"), "Horse (Gray)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_darkbrown.png"), "Horse (Dark Brown)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_markings_white.png"), "Horse Markings (White)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_markings_whitefield.png"), "Horse Markings (Whitefield)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_markings_whitedots.png"), "Horse Markings (White Dots)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_markings_blackdots.png"), "Horse Markings (Black Dots)", EntryType.MOB, null));

        // Villager professions
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/villager.png"), "Villager", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/armorer.png"), "Villager (Armorer)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/butcher.png"), "Villager (Butcher)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/cartographer.png"), "Villager (Cartographer)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/cleric.png"), "Villager (Cleric)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/farmer.png"), "Villager (Farmer)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/fisherman.png"), "Villager (Fisherman)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/fletcher.png"), "Villager (Fletcher)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/leatherworker.png"), "Villager (Leatherworker)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/librarian.png"), "Villager (Librarian)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/mason.png"), "Villager (Mason)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/nitwit.png"), "Villager (Nitwit)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/shepherd.png"), "Villager (Shepherd)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/toolsmith.png"), "Villager (Toolsmith)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/weaponsmith.png"), "Villager (Weaponsmith)", EntryType.MOB, null));

        // Zombie Villager
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/zombie_villager.png"), "Zombie Villager", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/armorer.png"), "Zombie Villager (Armorer)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/butcher.png"), "Zombie Villager (Butcher)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/cartographer.png"), "Zombie Villager (Cartographer)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/cleric.png"), "Zombie Villager (Cleric)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/farmer.png"), "Zombie Villager (Farmer)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/fisherman.png"), "Zombie Villager (Fisherman)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/fletcher.png"), "Zombie Villager (Fletcher)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/leatherworker.png"), "Zombie Villager (Leatherworker)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/librarian.png"), "Zombie Villager (Librarian)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/mason.png"), "Zombie Villager (Mason)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/nitwit.png"), "Zombie Villager (Nitwit)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/shepherd.png"), "Zombie Villager (Shepherd)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/toolsmith.png"), "Zombie Villager (Toolsmith)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/weaponsmith.png"), "Zombie Villager (Weaponsmith)", EntryType.MOB, null));

        // Shulker colors
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker.png"), "Shulker", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_white.png"), "Shulker (White)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_orange.png"), "Shulker (Orange)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_magenta.png"), "Shulker (Magenta)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_light_blue.png"), "Shulker (Light Blue)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_yellow.png"), "Shulker (Yellow)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_lime.png"), "Shulker (Lime)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_pink.png"), "Shulker (Pink)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_gray.png"), "Shulker (Gray)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_light_gray.png"), "Shulker (Light Gray)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_cyan.png"), "Shulker (Cyan)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_purple.png"), "Shulker (Purple)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_blue.png"), "Shulker (Blue)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_brown.png"), "Shulker (Brown)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_green.png"), "Shulker (Green)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_red.png"), "Shulker (Red)", EntryType.MOB, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_black.png"), "Shulker (Black)", EntryType.MOB, null));

        return entries;
    }

    private void addGUIEntry(List<BrowseEntry> entries, String texturePath, String displayName) {
        Identifier id = Identifier.of("minecraft", texturePath);
        entries.add(new BrowseEntry(id, displayName, EntryType.GUI, null));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Custom background
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Background
        context.fill(0, 0, this.width, this.height, 0xFF1A1A2E);

        // Header bar
        context.fill(0, 0, this.width, 28, 0xFF16213E);

        // Tab underline indicator
        if (tabAll != null) {
            ButtonWidget activeTab = switch (currentTab) {
                case ALL -> tabAll;
                case BLOCKS -> tabBlocks;
                case ITEMS -> tabItems;
                case MOBS -> tabMobs;
                case GUI -> tabGui;
                case SKY -> tabSky;
            };
            if (activeTab != null)
                context.fill(activeTab.getX(), activeTab.getY() + 20, activeTab.getX() + activeTab.getWidth(), activeTab.getY() + 22, 0xFFFFFF00);
        }

        // Render widgets
        super.render(context, mouseX, mouseY, delta);

        // Title info
        String entriesText = filteredEntries.size() + " entries";
        context.drawText(textRenderer, entriesText, this.width - textRenderer.getWidth(entriesText) - 5, 36, 0x999999, false);

        // Render grid
        renderGrid(context, mouseX, mouseY);
    }

    private void renderGrid(DrawContext context, int mouseX, int mouseY) {
        int startIdx = scrollOffset * columns;
        int gridX = GRID_SIDE_MARGIN;
        int gridY = GRID_TOP;

        String tooltipText = null;

        for (int i = startIdx; i < filteredEntries.size(); i++) {
            int localIdx = i - startIdx;
            int col = localIdx % columns;
            int row = localIdx / columns;
            if (row >= visibleRows + 1) break; // +1 for partial row

            int x = gridX + col * (CELL_SIZE + CELL_PADDING);
            int y = gridY + row * (CELL_SIZE + CELL_PADDING);

            if (y + CELL_SIZE > this.height) break;

            BrowseEntry entry = filteredEntries.get(i);

            // Cell background
            boolean hovered = mouseX >= x && mouseX < x + CELL_SIZE && mouseY >= y && mouseY < y + CELL_SIZE;
            int bgColor = hovered ? 0xFF3A3A5E : 0xFF2A2A4E;
            context.fill(x, y, x + CELL_SIZE, y + CELL_SIZE, bgColor);

            // Check if this entry has been modified (show indicator)
            boolean modified = isModified(entry);
            if (modified) {
                // Green border for modified textures
                drawRectOutline(context, x, y, x + CELL_SIZE, y + CELL_SIZE, 0xFF00FF00);
            } else {
                drawRectOutline(context, x, y, x + CELL_SIZE, y + CELL_SIZE, 0xFF333355);
            }

            // Render item icon or label
            if (entry.stack != null && !entry.stack.isEmpty()) {
                int iconX = x + (CELL_SIZE - 16) / 2;
                int iconY = y + (CELL_SIZE - 16) / 2;
                context.drawItem(entry.stack, iconX, iconY);
            } else {
                // GUI entries: draw abbreviated name
                String label = entry.name.length() > 5 ? entry.name.substring(0, 5) : entry.name;
                int lx = x + (CELL_SIZE - textRenderer.getWidth(label)) / 2;
                int ly = y + (CELL_SIZE - 8) / 2;
                context.drawText(textRenderer, label, lx, ly, 0xFFCCCCCC, false);
            }

            // Tooltip on hover
            if (hovered) {
                tooltipText = entry.name + " (" + entry.type.displayName + ")";
                if (modified) {
                    tooltipText += " \u00a7a[Modified]";
                }
            }
        }

        // Draw tooltip (with z-offset so it renders in front of items)
        if (tooltipText != null) {
            int tw = textRenderer.getWidth(tooltipText) + 8;
            int tx = Math.min(mouseX + 10, this.width - tw - 5);
            int ty = mouseY - 18;
            if (ty < 0) ty = mouseY + 15;
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);
            context.fill(tx - 2, ty - 2, tx + tw, ty + 12, 0xEE222244);
            drawRectOutline(context, tx - 2, ty - 2, tx + tw, ty + 12, 0xFFAAAAAA);
            context.drawText(textRenderer, tooltipText, tx + 2, ty, 0xFFFFFF, true);
            context.getMatrices().pop();
        }

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = this.width - 8;
            int sbY = GRID_TOP;
            int sbH = this.height - GRID_TOP - 10;
            context.fill(sbX, sbY, sbX + 6, sbY + sbH, 0xFF333355);
            int thumbH = Math.max(20, sbH * visibleRows / ((filteredEntries.size() + columns - 1) / columns));
            int thumbY = sbY + (int)((float)scrollOffset / maxScroll * (sbH - thumbH));
            context.fill(sbX, thumbY, sbX + 6, thumbY + thumbH, 0xFF8888CC);
        }
    }

    private boolean isModified(BrowseEntry entry) {
        // Quick check - if no textures modified at all, return false
        if (!TextureManager.getInstance().hasModifiedTextures()) return false;

        if (entry.type == EntryType.BLOCK) {
            Block block = Registries.BLOCK.get(entry.id);
            for (Direction dir : Direction.values()) {
                TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(block.getDefaultState(), dir);
                if (tex != null && TextureManager.getInstance().getPixels(tex.textureId()) != null) {
                    return true;
                }
            }
        } else if (entry.type == EntryType.ITEM && entry.stack != null) {
            ItemTextureExtractor.ItemTexture tex = ItemTextureExtractor.extract(entry.stack);
            if (tex != null && TextureManager.getInstance().getPixels(tex.textureId()) != null) {
                return true;
            }
        }
        return false;
    }

    private void drawRectOutline(DrawContext ctx, int x1, int y1, int x2, int y2, int c) {
        ctx.fill(x1, y1, x2, y1 + 1, c);
        ctx.fill(x1, y2 - 1, x2, y2, c);
        ctx.fill(x1, y1, x1 + 1, y2, c);
        ctx.fill(x2 - 1, y1, x2, y2, c);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0) {
            // Check grid click
            int startIdx = scrollOffset * columns;
            int gridX = GRID_SIDE_MARGIN;
            int gridY = GRID_TOP;

            for (int i = startIdx; i < filteredEntries.size(); i++) {
                int localIdx = i - startIdx;
                int col = localIdx % columns;
                int row = localIdx / columns;
                if (row >= visibleRows + 1) break;

                int x = gridX + col * (CELL_SIZE + CELL_PADDING);
                int y = gridY + row * (CELL_SIZE + CELL_PADDING);

                if (y + CELL_SIZE > this.height) break;

                if (mouseX >= x && mouseX < x + CELL_SIZE && mouseY >= y && mouseY < y + CELL_SIZE) {
                    openEditor(filteredEntries.get(i));
                    return true;
                }
            }
        }

        return false;
    }

    private void openEditor(BrowseEntry entry) {
        if (entry.type == EntryType.BLOCK) {
            Block block = Registries.BLOCK.get(entry.id);
            client.setScreen(new EditorScreen(block, this));
        } else if (entry.type == EntryType.MOB) {
            // If the ID is a direct texture path (e.g. textures/entity/elytra.png), open as GuiTextureEditorScreen
            if (entry.id.getPath().startsWith("textures/")) {
                client.setScreen(new GuiTextureEditorScreen(entry.id, entry.name, this));
            } else if (entry.stack == null) {
                // No entity (e.g sheep fur via non-texture path)
                client.setScreen(new GuiTextureEditorScreen(entry.id, entry.name, this));
            } else if (client.world != null) {
                net.minecraft.entity.Entity entity = com.zeeesea.textureeditor.util.EntityMapper.getEntityFromItem(entry.stack, client.world);
                if (entity != null) {
                    client.setScreen(new MobEditorScreen(entity, this));
                } else {
                    // Entity creation failed (e.g. elytra has no entity), open item editor
                    client.setScreen(new ItemEditorScreen(entry.stack, this));
                }
            }
        } else if (entry.type == EntryType.ITEM) {
            if (entry.stack != null) {
                client.setScreen(new ItemEditorScreen(entry.stack, this));
            }
        } else if (entry.type == EntryType.GUI) {
            client.setScreen(new GuiTextureEditorScreen(entry.id, entry.name, this));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 3);
        } else if (verticalAmount < 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 3);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // --- Entry data ---

    private enum EntryType {
        BLOCK("Block"),
        ITEM("Item"),
        MOB("Mob"),
        GUI("GUI");

        final String displayName;
        EntryType(String displayName) { this.displayName = displayName; }
    }

    private static class BrowseEntry {
        final Identifier id;
        final String name;
        final EntryType type;
        final ItemStack stack;

        BrowseEntry(Identifier id, String name, EntryType type, ItemStack stack) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.stack = stack;
        }
    }
}
