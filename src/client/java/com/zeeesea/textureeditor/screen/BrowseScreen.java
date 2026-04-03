package com.zeeesea.textureeditor.screen;

import com.zeeesea.textureeditor.texture.ItemTextureExtractor;
import com.zeeesea.textureeditor.texture.TextureExtractor;
import com.zeeesea.textureeditor.texture.TextureManager;
import com.zeeesea.textureeditor.util.BlockFilter;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import java.util.stream.Collectors;

/**
 * Browse screen showing all blocks, items, and mobs in a grid with tabs and search.
 * Clicking an entry opens the corresponding editor.
 */
public class BrowseScreen extends Screen {

    private enum Tab { ALL, BLOCKS, ITEMS, MOBS, GUI, ENTITY, SKY }

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

    // Preview cache for entries without an ItemStack (GUI, ENTITY, etc.)
    private final Map<Identifier, PreviewCache> previewCache = new ConcurrentHashMap<>();
    private final Map<Identifier, Identifier> previewTextureIds = new ConcurrentHashMap<>();

    // Tab buttons
    private ButtonWidget tabAll, tabBlocks, tabItems, tabMobs, tabEntity, tabGui, tabSky;

    public BrowseScreen() {
        super(Text.translatable("textureeditor.screen.browse.title"));
    }

    private boolean showCycleTabs() {
        MinecraftClient client = MinecraftClient.getInstance();
        int scaledHeight = client.getWindow().getScaledHeight();

        return scaledHeight > 300;
    }

    @Override
    protected void init() {
        // Build entries list on first init
        if (allEntries == null) {
            allEntries = buildAllEntries();
        }

        // Tab buttons
        int tabY = 5;

        if (!showCycleTabs()) {
            // All Tabs as a Cycle-Button
            addDrawableChild(ButtonWidget.builder(
                    Text.translatable("textureeditor.button.tab_cycle", Text.translatable("textureeditor.tab." + currentTab.name().toLowerCase())),
                    btn -> {
                        Tab[] tabs = Tab.values();
                        // SKY überspringen (öffnet extra screen)
                        Tab next = tabs[(currentTab.ordinal() + 1) % (tabs.length - 1)];
                        switchTab(next);
                        btn.setMessage(Text.translatable("textureeditor.button.tab_cycle", Text.translatable("textureeditor.tab." + next.name().toLowerCase())));
                    }
            ).position(10, tabY).size(80, 20).build());

            // Sky Button
            addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.tab.sky"), btn -> client.setScreen(new SkyEditorScreen(this)))
                    .position(94, tabY).size(40, 20).build());

            tabAll = tabBlocks = tabItems = tabEntity = tabMobs = tabGui = null;
            tabSky = null;
        } else {
            // Normal: all 7 Buttons
            int tabX = 10;
            int tabW = 50;
            tabAll = addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.tab.all"), btn -> switchTab(Tab.ALL))
                    .position(tabX, tabY).size(tabW, 20).build());
            tabX += tabW + 4;
            tabBlocks = addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.tab.blocks"), btn -> switchTab(Tab.BLOCKS))
                    .position(tabX, tabY).size(tabW, 20).build());
            tabX += tabW + 4;
            tabItems = addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.tab.items"), btn -> switchTab(Tab.ITEMS))
                    .position(tabX, tabY).size(tabW, 20).build());
            tabX += tabW + 4;
            tabMobs = addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.tab.mobs"), btn -> switchTab(Tab.MOBS))
                    .position(tabX, tabY).size(tabW, 20).build());
            tabX += tabW + 4;
            tabGui = addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.tab.gui"), btn -> switchTab(Tab.GUI))
                    .position(tabX, tabY).size(tabW, 20).build());
            tabX += tabW + 4;
            tabEntity = addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.tab.entity"), btn -> switchTab(Tab.ENTITY))
                    .position(tabX, tabY).size(tabW, 20).build());
            tabX += tabW + 4;
            tabSky = addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.tab.sky"), btn -> client.setScreen(new SkyEditorScreen(this)))
                    .position(tabX, tabY).size(tabW, 20).build());
        }

        // Search field
        searchField = new TextFieldWidget(this.textRenderer, this.width / 2 - 80, 32, 160, 18, Text.translatable("textureeditor.field.search"));
        searchField.setMaxLength(64);
        searchField.setText(searchQuery);
        searchField.setChangedListener(text -> {
            searchQuery = text.toLowerCase();
            applyFilter();
            scrollOffset = 0;
        });
        addDrawableChild(searchField);

        // Close button
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.close"), btn -> this.close())
                .position(this.width - 65, 5).size(60, 20).build());

        // Export button
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.export"), btn -> client.setScreen(new ExportScreen(this)))
                .position(this.width - 130, 5).size(60, 20).build());

        // Settings button
        addDrawableChild(ButtonWidget.builder(Text.translatable("textureeditor.button.settings"), btn -> client.setScreen(new SettingsScreen(this)))
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
                    if (currentTab == Tab.ENTITY && e.type != EntryType.ENTITY) return false;
                    if (!searchQuery.isEmpty() && !e.name.toLowerCase().contains(searchQuery)
                            && !e.id.toString().toLowerCase().contains(searchQuery)) return false;
                    return true;
                })
                .collect(Collectors.toList());

        int totalRows = (filteredEntries.size() + columns - 1) / columns;
        maxScroll = Math.max(0, totalRows - visibleRows);
    }

    private List<BrowseEntry> buildAllEntries() {
        List<BrowseEntry> entries = new ArrayList<>();

        entries.addAll(buildEntriesNew());
        //entries.addAll(buildParticleEntries());

        return entries;
    }

    @Deprecated
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
        entries.addAll(buildEntities());

        return entries;
    }

    private List<BrowseEntry> buildEntriesNew() {
        List<BrowseEntry> entries = new ArrayList<>();
        // Use a Set of texture IDs to avoid duplicates across different categories
        Set<Identifier> processedTextures = new HashSet<>();

        // 1. Add Editable Blocks
        for (Block block : net.minecraft.registry.Registries.BLOCK) {
            Identifier id = net.minecraft.registry.Registries.BLOCK.getId(block);
            if (id.getPath().equals("air")) continue;

            // Only add if your filter allows it (e.g. no complicated models)
            if (BlockFilter.isEditableBlock(block.getDefaultState())) {
                ItemStack stack = new ItemStack(block);
                if (!stack.isEmpty()) {
                    entries.add(new BrowseEntry(id, block.getName().getString(), EntryType.BLOCK, stack));
                    processedTextures.add(id);
                }
            }
        }

        // 2. Add Items (Everything that has a unique item texture)
        for (Item item : net.minecraft.registry.Registries.ITEM) {
            Identifier id = net.minecraft.registry.Registries.ITEM.getId(item);

            // Skip if already added as a block, UNLESS it's something like a sign or bed
            // which has a unique item texture different from the placed block.
            if (processedTextures.contains(id) && !(item instanceof net.minecraft.item.SignItem)) continue;

            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) continue;

            EntryType type = (item instanceof net.minecraft.item.SpawnEggItem) ? EntryType.MOB : EntryType.ITEM;

            entries.add(new BrowseEntry(
                    id,
                    item.getName().getString(),
                    type,
                    stack
            ));
        }

        // 3. Add Custom Categories (GUI, Particles, Entities, Variants)
        entries.addAll(buildGuiEntries());
        entries.addAll(buildNotNormalEntries());
        entries.addAll(buildEntities());

        return entries;
    }

    private List<BrowseEntry> buildParticleEntries() {
        List<BrowseEntry> entries = new ArrayList<>();

        for (Identifier particleId : net.minecraft.registry.Registries.PARTICLE_TYPE.getIds()) {
            String namespace = particleId.getNamespace();
            String path = particleId.getPath();

            // Skip block/item markers as they don't have a static texture
            if (path.contains("block") || path.contains("item")) continue;

            // In 1.21.11, textures are referenced as "textures/particle/name.png"
            // We build the full path identifier for the texture manager
            Identifier texturePath = Identifier.of(namespace, "textures/particle/" + path + ".png");

            entries.add(new BrowseEntry(
                    texturePath, // Use the full path as ID
                    generateGuiName(path),
                    EntryType.PARTICLE,
                    ItemStack.EMPTY
            ));
        }

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

        addGUIEntry(entries, "gui/sprites/hud/food_empty", "Food Empty");
        addGUIEntry(entries, "gui/sprites/hud/food_half", "Food Half");
        addGUIEntry(entries, "gui/sprites/hud/food_full", "Food Full");

        addGUIEntry(entries, "gui/sprites/hud/air", "Air Bubble");
        addGUIEntry(entries, "gui/sprites/hud/air_bursting", "Air Bubble Bursting");


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

        addGUIEntry(entries, "gui/container/creative_inventory/tab_inventory", "Creative Inventory BG");
        addGUIEntry(entries, "gui/container/creative_inventory/tab_items", "Creative Items BG");
        addGUIEntry(entries, "gui/container/creative_inventory/tab_item_search", "Creative Search BG");


        addGUIEntry(entries, "gui/sprites/widget/button", "Button");
        addGUIEntry(entries, "gui/sprites/widget/button_highlighted", "Button Highlighted");
        addGUIEntry(entries, "gui/sprites/widget/button_disabled", "Button Disabled");

        addGUIEntry(entries, "gui/sprites/widget/slider", "Slider");
        addGUIEntry(entries, "gui/sprites/widget/slider_highlighted", "Slider Highlighted");
        addGUIEntry(entries, "gui/sprites/widget/slider_handle", "Slider Handle");
        addGUIEntry(entries, "gui/sprites/widget/slider_handle_highlighted", "Slider Handle HL");

        addGUIEntry(entries, "gui/sprites/widget/text_field", "Text Field");
        addGUIEntry(entries, "gui/sprites/widget/text_field_highlighted", "Text Field HL");


        addGUIEntry(entries, "gui/title/minecraft", "Title Logo");
        addGUIEntry(entries, "gui/title/edition", "Edition Badge");

        // ICON
        addGUIEntry(entries, "gui/sprites/icon/accessibility", "Accessibility");
        addGUIEntry(entries, "gui/sprites/icon/chat_modified", "Chat Modified");
        addGUIEntry(entries, "gui/sprites/icon/checkmark", "Checkmark");
        addGUIEntry(entries, "gui/sprites/icon/draft_report", "Draft Report");
        addGUIEntry(entries, "gui/sprites/icon/info", "Info");
        addGUIEntry(entries, "gui/sprites/icon/invite", "Invite");
        addGUIEntry(entries, "gui/sprites/icon/language", "Language");
        addGUIEntry(entries, "gui/sprites/icon/link", "Link");
        addGUIEntry(entries, "gui/sprites/icon/link_highlighted", "Link Highlighted");
        addGUIEntry(entries, "gui/sprites/icon/new_realm", "New Realm");
        addGUIEntry(entries, "gui/sprites/icon/news", "News");
        addGUIEntry(entries, "gui/sprites/icon/ping_1", "Ping 1");
        addGUIEntry(entries, "gui/sprites/icon/ping_2", "Ping 2");
        addGUIEntry(entries, "gui/sprites/icon/ping_3", "Ping 3");
        addGUIEntry(entries, "gui/sprites/icon/ping_4", "Ping 4");
        addGUIEntry(entries, "gui/sprites/icon/ping_5", "Ping 5");
        addGUIEntry(entries, "gui/sprites/icon/ping_unknown", "Ping Unknown");
        addGUIEntry(entries, "gui/sprites/icon/search", "Search");
        addGUIEntry(entries, "gui/sprites/icon/trial_available", "Trial Available");
        addGUIEntry(entries, "gui/sprites/icon/unseen_notification", "Unseen Notification");
        addGUIEntry(entries, "gui/sprites/icon/video_link", "Video Link");
        addGUIEntry(entries, "gui/sprites/icon/video_link_highlighted", "Video Link Highlighted");

        // NOTIFICATION
        addGUIEntry(entries, "gui/sprites/notification/1", "Notification 1");
        addGUIEntry(entries, "gui/sprites/notification/2", "Notification 2");
        addGUIEntry(entries, "gui/sprites/notification/3", "Notification 3");
        addGUIEntry(entries, "gui/sprites/notification/4", "Notification 4");
        addGUIEntry(entries, "gui/sprites/notification/5", "Notification 5");
        addGUIEntry(entries, "gui/sprites/notification/more", "Notification More");

        // PENDING INVITE
        addGUIEntry(entries, "gui/sprites/pending_invite/accept", "Invite Accept");
        addGUIEntry(entries, "gui/sprites/pending_invite/accept_highlighted", "Invite Accept Highlighted");
        addGUIEntry(entries, "gui/sprites/pending_invite/reject", "Invite Reject");
        addGUIEntry(entries, "gui/sprites/pending_invite/reject_highlighted", "Invite Reject Highlighted");

        // PLAYER LIST
        addGUIEntry(entries, "gui/sprites/player_list/make_operator", "Make Operator");
        addGUIEntry(entries, "gui/sprites/player_list/remove_operator", "Remove Operator");
        addGUIEntry(entries, "gui/sprites/player_list/remove_player", "Remove Player");

        // REALM STATUS
        addGUIEntry(entries, "gui/sprites/realm_status/closed", "Realm Closed");
        addGUIEntry(entries, "gui/sprites/realm_status/expired", "Realm Expired");
        addGUIEntry(entries, "gui/sprites/realm_status/expires_soon", "Realm Expires Soon");
        addGUIEntry(entries, "gui/sprites/realm_status/open", "Realm Open");

        // RECIPE BOOK
        addGUIEntry(entries, "gui/sprites/recipe_book/button", "Recipe Button");
        addGUIEntry(entries, "gui/sprites/recipe_book/button_highlighted", "Recipe Button Highlighted");
        addGUIEntry(entries, "gui/sprites/recipe_book/crafting_overlay", "Crafting Overlay");
        addGUIEntry(entries, "gui/sprites/recipe_book/crafting_overlay_disabled", "Crafting Overlay Disabled");
        addGUIEntry(entries, "gui/sprites/recipe_book/crafting_overlay_disabled_highlighted", "Crafting Overlay Disabled HL");
        addGUIEntry(entries, "gui/sprites/recipe_book/crafting_overlay_highlighted", "Crafting Overlay HL");
        addGUIEntry(entries, "gui/sprites/recipe_book/filter_disabled", "Filter Disabled");
        addGUIEntry(entries, "gui/sprites/recipe_book/filter_disabled_highlighted", "Filter Disabled HL");
        addGUIEntry(entries, "gui/sprites/recipe_book/filter_enabled", "Filter Enabled");
        addGUIEntry(entries, "gui/sprites/recipe_book/filter_enabled_highlighted", "Filter Enabled HL");
        addGUIEntry(entries, "gui/sprites/recipe_book/furnace_filter_disabled", "Furnace Filter Disabled");
        addGUIEntry(entries, "gui/sprites/recipe_book/furnace_filter_disabled_highlighted", "Furnace Filter Disabled HL");
        addGUIEntry(entries, "gui/sprites/recipe_book/furnace_filter_enabled", "Furnace Filter Enabled");
        addGUIEntry(entries, "gui/sprites/recipe_book/furnace_filter_enabled_highlighted", "Furnace Filter Enabled HL");
        addGUIEntry(entries, "gui/sprites/recipe_book/furnace_overlay", "Furnace Overlay");
        addGUIEntry(entries, "gui/sprites/recipe_book/furnace_overlay_disabled", "Furnace Overlay Disabled");
        addGUIEntry(entries, "gui/sprites/recipe_book/furnace_overlay_disabled_highlighted", "Furnace Overlay Disabled HL");
        addGUIEntry(entries, "gui/sprites/recipe_book/furnace_overlay_highlighted", "Furnace Overlay HL");
        addGUIEntry(entries, "gui/sprites/recipe_book/page_backward", "Page Backward");
        addGUIEntry(entries, "gui/sprites/recipe_book/page_backward_highlighted", "Page Backward HL");
        addGUIEntry(entries, "gui/sprites/recipe_book/page_forward", "Page Forward");
        addGUIEntry(entries, "gui/sprites/recipe_book/page_forward_highlighted", "Page Forward HL");

        // SERVER LIST
        addGUIEntry(entries, "gui/sprites/server_list/join", "Server Join");
        addGUIEntry(entries, "gui/sprites/server_list/join_highlighted", "Server Join HL");
        addGUIEntry(entries, "gui/sprites/server_list/move_down", "Server Move Down");
        addGUIEntry(entries, "gui/sprites/server_list/move_down_highlighted", "Server Move Down HL");
        addGUIEntry(entries, "gui/sprites/server_list/move_up", "Server Move Up");
        addGUIEntry(entries, "gui/sprites/server_list/move_up_highlighted", "Server Move Up HL");

        // SOCIAL INTERACTIONS
        addGUIEntry(entries, "gui/sprites/social_interactions/mute_button", "Mute");
        addGUIEntry(entries, "gui/sprites/social_interactions/mute_button_highlighted", "Mute HL");
        addGUIEntry(entries, "gui/sprites/social_interactions/report_button", "Report");
        addGUIEntry(entries, "gui/sprites/social_interactions/report_button_disabled", "Report Disabled");
        addGUIEntry(entries, "gui/sprites/social_interactions/report_button_highlighted", "Report HL");
        addGUIEntry(entries, "gui/sprites/social_interactions/unmute_button", "Unmute");
        addGUIEntry(entries, "gui/sprites/social_interactions/unmute_button_highlighted", "Unmute HL");

        // SPECTATOR
        addGUIEntry(entries, "gui/sprites/spectator/close", "Spectator Close");
        addGUIEntry(entries, "gui/sprites/spectator/scroll_left", "Scroll Left");
        addGUIEntry(entries, "gui/sprites/spectator/scroll_right", "Scroll Right");
        addGUIEntry(entries, "gui/sprites/spectator/teleport_to_player", "Teleport Player");
        addGUIEntry(entries, "gui/sprites/spectator/teleport_to_team", "Teleport Team");

        // STATISTICS
        addGUIEntry(entries, "gui/sprites/statistics/block_mined", "Blocks Mined");
        addGUIEntry(entries, "gui/sprites/statistics/header", "Statistics Header");
        addGUIEntry(entries, "gui/sprites/statistics/item_broken", "Items Broken");
        addGUIEntry(entries, "gui/sprites/statistics/item_crafted", "Items Crafted");
        addGUIEntry(entries, "gui/sprites/statistics/item_dropped", "Items Dropped");
        addGUIEntry(entries, "gui/sprites/statistics/item_picked_up", "Items Picked Up");
        addGUIEntry(entries, "gui/sprites/statistics/item_used", "Items Used");
        addGUIEntry(entries, "gui/sprites/statistics/sort_down", "Sort Down");
        addGUIEntry(entries, "gui/sprites/statistics/sort_up", "Sort Up");

        // TOAST
        addGUIEntry(entries, "gui/sprites/toast/mouse", "Toast Mouse");
        addGUIEntry(entries, "gui/sprites/toast/movement_keys", "Toast Movement Keys");
        addGUIEntry(entries, "gui/sprites/toast/recipe_book", "Toast Recipe Book");
        addGUIEntry(entries, "gui/sprites/toast/right_click", "Toast Right Click");
        addGUIEntry(entries, "gui/sprites/toast/social_interactions", "Toast Social");
        addGUIEntry(entries, "gui/sprites/toast/system", "Toast System");
        addGUIEntry(entries, "gui/sprites/toast/tree", "Toast Tree");
        addGUIEntry(entries, "gui/sprites/toast/wooden_planks", "Toast Planks");

        // HUD (extended)
        addGUIEntry(entries, "gui/sprites/hud/hotbar_offhand_left", "Hotbar Offhand Left");
        addGUIEntry(entries, "gui/sprites/hud/hotbar_offhand_right", "Hotbar Offhand Right");

        addGUIEntry(entries, "gui/sprites/hud/crosshair_attack_indicator_background", "Crosshair Attack BG");
        addGUIEntry(entries, "gui/sprites/hud/crosshair_attack_indicator_full", "Crosshair Attack Full");
        addGUIEntry(entries, "gui/sprites/hud/crosshair_attack_indicator_progress", "Crosshair Attack Progress");

        addGUIEntry(entries, "gui/sprites/hud/hotbar_attack_indicator_background", "Hotbar Attack BG");
        addGUIEntry(entries, "gui/sprites/hud/hotbar_attack_indicator_progress", "Hotbar Attack Progress");

        addGUIEntry(entries, "gui/sprites/hud/jump_bar_background", "Jump Bar BG");
        addGUIEntry(entries, "gui/sprites/hud/jump_bar_cooldown", "Jump Bar Cooldown");
        addGUIEntry(entries, "gui/sprites/hud/jump_bar_progress", "Jump Bar Progress");

        addGUIEntry(entries, "gui/sprites/hud/food_empty_hunger", "Food Empty Hunger");
        addGUIEntry(entries, "gui/sprites/hud/food_half_hunger", "Food Half Hunger");
        addGUIEntry(entries, "gui/sprites/hud/food_full_hunger", "Food Full Hunger");

        // HEARTS (hud/heart)

        addGUIEntry(entries, "gui/sprites/hud/heart/absorbing_full", "Absorbing Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/absorbing_full_blinking", "Absorbing Full Blinking");
        addGUIEntry(entries, "gui/sprites/hud/heart/absorbing_half", "Absorbing Half");
        addGUIEntry(entries, "gui/sprites/hud/heart/absorbing_half_blinking", "Absorbing Half Blinking");

        addGUIEntry(entries, "gui/sprites/hud/heart/absorbing_hardcore_full", "Absorbing Hardcore Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/absorbing_hardcore_full_blinking", "Absorbing Hardcore Full Blinking");
        addGUIEntry(entries, "gui/sprites/hud/heart/absorbing_hardcore_half", "Absorbing Hardcore Half");
        addGUIEntry(entries, "gui/sprites/hud/heart/absorbing_hardcore_half_blinking", "Absorbing Hardcore Half Blinking");

        addGUIEntry(entries, "gui/sprites/hud/heart/container", "Heart Container");
        addGUIEntry(entries, "gui/sprites/hud/heart/container_blinking", "Heart Container Blinking");
        addGUIEntry(entries, "gui/sprites/hud/heart/container_hardcore", "Heart Container Hardcore");
        addGUIEntry(entries, "gui/sprites/hud/heart/container_hardcore_blinking", "Heart Container Hardcore Blinking");

        addGUIEntry(entries, "gui/sprites/hud/heart/frozen_full", "Frozen Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/frozen_full_blinking", "Frozen Full Blinking");
        addGUIEntry(entries, "gui/sprites/hud/heart/frozen_half", "Frozen Half");
        addGUIEntry(entries, "gui/sprites/hud/heart/frozen_half_blinking", "Frozen Half Blinking");

        addGUIEntry(entries, "gui/sprites/hud/heart/frozen_hardcore_full", "Frozen Hardcore Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/frozen_hardcore_full_blinking", "Frozen Hardcore Full Blinking");
        addGUIEntry(entries, "gui/sprites/hud/heart/frozen_hardcore_half", "Frozen Hardcore Half");
        addGUIEntry(entries, "gui/sprites/hud/heart/frozen_hardcore_half_blinking", "Frozen Hardcore Half Blinking");

        addGUIEntry(entries, "gui/sprites/hud/heart/full_blinking", "Heart Full Blinking");
        addGUIEntry(entries, "gui/sprites/hud/heart/half_blinking", "Heart Half Blinking");

        addGUIEntry(entries, "gui/sprites/hud/heart/hardcore_full", "Hardcore Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/hardcore_full_blinking", "Hardcore Full Blinking");
        addGUIEntry(entries, "gui/sprites/hud/heart/hardcore_half", "Hardcore Half");
        addGUIEntry(entries, "gui/sprites/hud/heart/hardcore_half_blinking", "Hardcore Half Blinking");

        addGUIEntry(entries, "gui/sprites/hud/heart/poisoned_full", "Poisoned Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/poisoned_full_blinking", "Poisoned Full Blinking");
        addGUIEntry(entries, "gui/sprites/hud/heart/poisoned_half", "Poisoned Half");
        addGUIEntry(entries, "gui/sprites/hud/heart/poisoned_half_blinking", "Poisoned Half Blinking");

        addGUIEntry(entries, "gui/sprites/hud/heart/poisoned_hardcore_full", "Poisoned Hardcore Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/poisoned_hardcore_full_blinking", "Poisoned Hardcore Full Blinking");
        addGUIEntry(entries, "gui/sprites/hud/heart/poisoned_hardcore_half", "Poisoned Hardcore Half");
        addGUIEntry(entries, "gui/sprites/hud/heart/poisoned_hardcore_half_blinking", "Poisoned Hardcore Half Blinking");

        addGUIEntry(entries, "gui/sprites/hud/heart/vehicle_container", "Vehicle Container");
        addGUIEntry(entries, "gui/sprites/hud/heart/vehicle_full", "Vehicle Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/vehicle_half", "Vehicle Half");

        addGUIEntry(entries, "gui/sprites/hud/heart/withered_full", "Withered Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/withered_full_blinking", "Withered Full Blinking");
        addGUIEntry(entries, "gui/sprites/hud/heart/withered_half", "Withered Half");
        addGUIEntry(entries, "gui/sprites/hud/heart/withered_half_blinking", "Withered Half Blinking");

        addGUIEntry(entries, "gui/sprites/hud/heart/withered_hardcore_full", "Withered Hardcore Full");
        addGUIEntry(entries, "gui/sprites/hud/heart/withered_hardcore_full_blinking", "Withered Hardcore Full Blinking");
        addGUIEntry(entries, "gui/sprites/hud/heart/withered_hardcore_half", "Withered Hardcore Half");
        addGUIEntry(entries, "gui/sprites/hud/heart/withered_hardcore_half_blinking", "Withered Hardcore Half Blinking");

        // TRANSFERABLE LIST
        addGUIEntry(entries, "gui/sprites/transferable_list/move_down", "Move Down");
        addGUIEntry(entries, "gui/sprites/transferable_list/move_down_highlighted", "Move Down HL");
        addGUIEntry(entries, "gui/sprites/transferable_list/move_up", "Move Up");
        addGUIEntry(entries, "gui/sprites/transferable_list/move_up_highlighted", "Move Up HL");
        addGUIEntry(entries, "gui/sprites/transferable_list/select", "Select");
        addGUIEntry(entries, "gui/sprites/transferable_list/select_highlighted", "Select HL");
        addGUIEntry(entries, "gui/sprites/transferable_list/unselect", "Unselect");
        addGUIEntry(entries, "gui/sprites/transferable_list/unselect_highlighted", "Unselect HL");

        // WIDGET (extra)
        addGUIEntry(entries, "gui/sprites/widget/checkbox_selected_highlighted", "Checkbox Selected HL");
        addGUIEntry(entries, "gui/sprites/widget/cross_button", "Cross Button");
        addGUIEntry(entries, "gui/sprites/widget/cross_button_highlighted", "Cross Button HL");

        addGUIEntry(entries, "gui/sprites/widget/locked_button", "Locked Button");
        addGUIEntry(entries, "gui/sprites/widget/locked_button_disabled", "Locked Button Disabled");
        addGUIEntry(entries, "gui/sprites/widget/locked_button_highlighted", "Locked Button HL");

        addGUIEntry(entries, "gui/sprites/widget/page_backward", "Page Backward");
        addGUIEntry(entries, "gui/sprites/widget/page_backward_highlighted", "Page Backward HL");
        addGUIEntry(entries, "gui/sprites/widget/page_forward", "Page Forward");
        addGUIEntry(entries, "gui/sprites/widget/page_forward_highlighted", "Page Forward HL");

        addGUIEntry(entries, "gui/sprites/widget/scroller", "Scroller");
        addGUIEntry(entries, "gui/sprites/widget/scroller_background", "Scroller Background");

        addGUIEntry(entries, "gui/sprites/widget/slot_frame", "Slot Frame");

        addGUIEntry(entries, "gui/sprites/widget/unlocked_button", "Unlocked Button");
        addGUIEntry(entries, "gui/sprites/widget/unlocked_button_disabled", "Unlocked Button Disabled");
        addGUIEntry(entries, "gui/sprites/widget/unlocked_button_highlighted", "Unlocked Button HL");

        // WORLD LIST
        addGUIEntry(entries, "gui/sprites/world_list/error", "World Error");
        addGUIEntry(entries, "gui/sprites/world_list/error_highlighted", "World Error HL");
        addGUIEntry(entries, "gui/sprites/world_list/join", "World Join");
        addGUIEntry(entries, "gui/sprites/world_list/join_highlighted", "World Join HL");
        addGUIEntry(entries, "gui/sprites/world_list/marked_join", "World Marked Join");
        addGUIEntry(entries, "gui/sprites/world_list/marked_join_highlighted", "World Marked Join HL");
        addGUIEntry(entries, "gui/sprites/world_list/warning", "World Warning");
        addGUIEntry(entries, "gui/sprites/world_list/warning_highlighted", "World Warning HL");

        // BOSS BAR
        addGUIEntry(entries, "gui/sprites/boss_bar/blue_background", "Boss Blue BG");
        addGUIEntry(entries, "gui/sprites/boss_bar/blue_progress", "Boss Blue Progress");

        addGUIEntry(entries, "gui/sprites/boss_bar/green_background", "Boss Green BG");
        addGUIEntry(entries, "gui/sprites/boss_bar/green_progress", "Boss Green Progress");

        addGUIEntry(entries, "gui/sprites/boss_bar/notched_6_background", "Boss Notched 6 BG");
        addGUIEntry(entries, "gui/sprites/boss_bar/notched_6_progress", "Boss Notched 6 Progress");

        addGUIEntry(entries, "gui/sprites/boss_bar/notched_10_background", "Boss Notched 10 BG");
        addGUIEntry(entries, "gui/sprites/boss_bar/notched_10_progress", "Boss Notched 10 Progress");

        addGUIEntry(entries, "gui/sprites/boss_bar/notched_12_background", "Boss Notched 12 BG");
        addGUIEntry(entries, "gui/sprites/boss_bar/notched_12_progress", "Boss Notched 12 Progress");

        addGUIEntry(entries, "gui/sprites/boss_bar/notched_20_background", "Boss Notched 20 BG");
        addGUIEntry(entries, "gui/sprites/boss_bar/notched_20_progress", "Boss Notched 20 Progress");

        addGUIEntry(entries, "gui/sprites/boss_bar/pink_background", "Boss Pink BG");
        addGUIEntry(entries, "gui/sprites/boss_bar/pink_progress", "Boss Pink Progress");

        addGUIEntry(entries, "gui/sprites/boss_bar/purple_background", "Boss Purple BG");
        addGUIEntry(entries, "gui/sprites/boss_bar/purple_progress", "Boss Purple Progress");

        addGUIEntry(entries, "gui/sprites/boss_bar/red_background", "Boss Red BG");
        addGUIEntry(entries, "gui/sprites/boss_bar/red_progress", "Boss Red Progress");

        addGUIEntry(entries, "gui/sprites/boss_bar/white_background", "Boss White BG");
        addGUIEntry(entries, "gui/sprites/boss_bar/white_progress", "Boss White Progress");

        addGUIEntry(entries, "gui/sprites/boss_bar/yellow_background", "Boss Yellow BG");
        addGUIEntry(entries, "gui/sprites/boss_bar/yellow_progress", "Boss Yellow Progress");

        // GAMEMODE SWITCHER
        addGUIEntry(entries, "gui/sprites/gamemode_switcher/selection", "Gamemode Selection");
        addGUIEntry(entries, "gui/sprites/gamemode_switcher/slot", "Gamemode Slot");
        


        return entries;
    }

    /*
    private List<BrowseEntry> buildGuiEntriesAuto() {
        List<BrowseEntry> entries = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();

        try {
            ResourceManager resourceManager = client.getResourceManager();

            Collection<Identifier> resources = resourceManager.findResources(
                    "textures/gui/sprites"
            );

            Set<String> seen = new HashSet<>();

            for (Identifier fullId : resources) {
                String path = fullId.getPath();
                // textures/gui/sprites/icon/test.png

                String cleanPath = path
                        .replace("textures/", "")
                        .replace(".png", "");

                // gui/sprites/icon/test

                if (!seen.add(cleanPath)) continue;

                String name = generateGuiName(cleanPath);

                entries.add(new BrowseEntry(
                        Identifier.of(fullId.getNamespace(), cleanPath),
                        name,
                        EntryType.GUI,
                        null
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return entries;
    }*/

    private List<BrowseEntry> buildNotNormalEntries() {
        List<BrowseEntry> entries = new ArrayList<>();

        //Grass Overhang
        entries.add(new BrowseEntry(
                Identifier.of("minecraft", "textures/block/grass_block_side_overlay.png"),
                "Grass Overhang",
                EntryType.GUI,
                null
        ));

        // Block breaking (destroy stages) - single browse entry that opens a frame-based editor
        entries.add(new BrowseEntry(
                Identifier.of("minecraft", "textures/block/destroy_stage_0.png"),
                "Block Breaking",
                EntryType.GUI,
                null
        ));

        /*
        // Empty Armor Slots
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/item/empty_armor_slot_boots.png"), "Empty Armor Slot Boots", EntryType.GUI, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/item/empty_armor_slot_chestplate.png"), "Empty Armor Slot Chestplate", EntryType.GUI, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/item/empty_armor_slot_helmet.png"), "Empty Armor Slot Helmet", EntryType.GUI, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/item/empty_armor_slot_leggings.png"), "Empty Armor Slot Leggings", EntryType.GUI, null));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/item/empty_armor_slot_shield.png"), "Empty Armor Slot Shield", EntryType.GUI, null));
        */

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

        // Minecart & Elytra
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/minecart.png"), "Minecart", EntryType.MOB, new ItemStack(net.minecraft.item.Items.MINECART)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/elytra.png"), "Elytra", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ELYTRA)));

        // Sheep Wool
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/sheep/sheep_wool.png"), "Sheep Wool", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHEEP_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/sheep/sheep_wool_undercoat.png"), "Sheep Wool Undercoat", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHEEP_SPAWN_EGG)));

        // Cow variants
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cow/temperate_cow.png"), "Cow (Temperate)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.COW_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cow/cold_cow.png"), "Cow (Cold)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.COW_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cow/warm_cow.png"), "Cow (Warm)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.COW_SPAWN_EGG)));

        // Pig variants
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/pig/temperate_pig.png"), "Pig (Temperate)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.PIG_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/pig/cold_pig.png"), "Pig (Cold)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.PIG_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/pig/warm_pig.png"), "Pig (Warm)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.PIG_SPAWN_EGG)));

        // Chicken variants
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/chicken/temperate_chicken.png"), "Chicken (Temperate)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CHICKEN_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/chicken/cold_chicken.png"), "Chicken (Cold)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CHICKEN_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/chicken/warm_chicken.png"), "Chicken (Warm)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CHICKEN_SPAWN_EGG)));

        // Wolf
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf.png"), "Wolf", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_angry.png"), "Wolf (Angry)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_tame.png"), "Wolf (Tame)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));

        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_pale.png"), "Wolf (Pale/Standard)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_ashen.png"), "Wolf (Ashen)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_black.png"), "Wolf (Black)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_chestnut.png"), "Wolf (Chestnut)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_rusty.png"), "Wolf (Rusty)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_snowy.png"), "Wolf (Snowy)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_spotted.png"), "Wolf (Spotted)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_striped.png"), "Wolf (Striped)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/wolf/wolf_woods.png"), "Wolf (Woods)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.WOLF_SPAWN_EGG)));

        // Cat variants
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/tabby.png"), "Cat (Tabby)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/black.png"), "Cat (Black)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/british_shorthair.png"), "Cat (British Shorthair)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/calico.png"), "Cat (Calico)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/jellie.png"), "Cat (Jellie)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/ocelot.png"), "Cat (Ocelot)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.OCELOT_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/persian.png"), "Cat (Persian)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/ragdoll.png"), "Cat (Ragdoll)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/red.png"), "Cat (Red)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/siamese.png"), "Cat (Siamese)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/white.png"), "Cat (White)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/all_black.png"), "Cat (All Black)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));

        // Horse variants
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_white.png"), "Horse (White)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.HORSE_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_creamy.png"), "Horse (Creamy)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.HORSE_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_chestnut.png"), "Horse (Chestnut)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.HORSE_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_brown.png"), "Horse (Brown)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.HORSE_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_black.png"), "Horse (Black)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.HORSE_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_gray.png"), "Horse (Gray)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.HORSE_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_darkbrown.png"), "Horse (Dark Brown)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.HORSE_SPAWN_EGG)));

        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_zombie.png"), "Zombie Horse", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_HORSE_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_skeleton.png"), "Skeleton Horse", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SKELETON_HORSE_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/donkey.png"), "Donkey", EntryType.MOB, new ItemStack(net.minecraft.item.Items.DONKEY_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/mule.png"), "Mule", EntryType.MOB, new ItemStack(net.minecraft.item.Items.MULE_SPAWN_EGG)));

        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_markings_white.png"), "Horse Markings (White)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.HORSE_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_markings_whitefield.png"), "Horse Markings (Whitefield)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.HORSE_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_markings_whitedots.png"), "Horse Markings (White Dots)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.HORSE_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/horse/horse_markings_blackdots.png"), "Horse Markings (Black Dots)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.HORSE_SPAWN_EGG)));

        // Villager professions
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/villager.png"), "Villager", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/armorer.png"), "Villager (Armorer)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/butcher.png"), "Villager (Butcher)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/cartographer.png"), "Villager (Cartographer)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/cleric.png"), "Villager (Cleric)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/farmer.png"), "Villager (Farmer)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/fisherman.png"), "Villager (Fisherman)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/fletcher.png"), "Villager (Fletcher)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/leatherworker.png"), "Villager (Leatherworker)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/librarian.png"), "Villager (Librarian)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/mason.png"), "Villager (Mason)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/nitwit.png"), "Villager (Nitwit)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/shepherd.png"), "Villager (Shepherd)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/toolsmith.png"), "Villager (Toolsmith)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/profession/weaponsmith.png"), "Villager (Weaponsmith)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));

        // Villager Biomes
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/type/desert.png"), "Villager (Desert)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/type/jungle.png"), "Villager (Jungle)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/type/plains.png"), "Villager (Plains)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/type/savanna.png"), "Villager (Savanna)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/type/snow.png"), "Villager (Snow)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/type/swamp.png"), "Villager (Swamp)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/villager/type/taiga.png"), "Villager (Taiga)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.VILLAGER_SPAWN_EGG)));

        // Zombie Villager
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/zombie_villager.png"), "Zombie Villager", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/armorer.png"), "Zombie Villager (Armorer)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/butcher.png"), "Zombie Villager (Butcher)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/cartographer.png"), "Zombie Villager (Cartographer)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/cleric.png"), "Zombie Villager (Cleric)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/farmer.png"), "Zombie Villager (Farmer)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/fisherman.png"), "Zombie Villager (Fisherman)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/fletcher.png"), "Zombie Villager (Fletcher)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/leatherworker.png"), "Zombie Villager (Leatherworker)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/librarian.png"), "Zombie Villager (Librarian)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/mason.png"), "Zombie Villager (Mason)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/nitwit.png"), "Zombie Villager (Nitwit)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/shepherd.png"), "Zombie Villager (Shepherd)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/toolsmith.png"), "Zombie Villager (Toolsmith)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie_villager/profession/weaponsmith.png"), "Zombie Villager (Weaponsmith)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.ZOMBIE_VILLAGER_SPAWN_EGG)));

        // Shulker colors
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker.png"), "Shulker", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_white.png"), "Shulker (White)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_orange.png"), "Shulker (Orange)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_magenta.png"), "Shulker (Magenta)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_light_blue.png"), "Shulker (Light Blue)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_yellow.png"), "Shulker (Yellow)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_lime.png"), "Shulker (Lime)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_pink.png"), "Shulker (Pink)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_gray.png"), "Shulker (Gray)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_light_gray.png"), "Shulker (Light Gray)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_cyan.png"), "Shulker (Cyan)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_purple.png"), "Shulker (Purple)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_blue.png"), "Shulker (Blue)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_brown.png"), "Shulker (Brown)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_green.png"), "Shulker (Green)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_red.png"), "Shulker (Red)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/shulker/shulker_black.png"), "Shulker (Black)", EntryType.MOB, new ItemStack(net.minecraft.item.Items.SHULKER_SPAWN_EGG)));

        // Special
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/cat/cat_collar.png"), "Cat Collar", EntryType.MOB, new ItemStack(net.minecraft.item.Items.CAT_SPAWN_EGG)));

        //Drowned
        entries.add(new BrowseEntry(Identifier.of("minecraft", "textures/entity/zombie/drowned_outer_layer.png"), "Drowned Outer Layer", EntryType.MOB, new ItemStack(Items.DROWNED_SPAWN_EGG)));


        return entries;
    }

    private List<BrowseEntry> buildEntities() {
        List<BrowseEntry> entries = new ArrayList<>();

        // Armor textures from assets/minecraft/textures/models/armor
        addEntityArmorEntry(entries, "chainmail_layer_1");
        addEntityArmorEntry(entries, "chainmail_layer_2");
        addEntityArmorEntry(entries, "diamond_layer_1");
        addEntityArmorEntry(entries, "diamond_layer_2");
        addEntityArmorEntry(entries, "gold_layer_1");
        addEntityArmorEntry(entries, "gold_layer_2");
        addEntityArmorEntry(entries, "iron_layer_1");
        addEntityArmorEntry(entries, "iron_layer_2");
        addEntityArmorEntry(entries, "leather_layer_1");
        addEntityArmorEntry(entries, "leather_layer_1_overlay");
        addEntityArmorEntry(entries, "leather_layer_2");
        addEntityArmorEntry(entries, "leather_layer_2_overlay");
        addEntityArmorEntry(entries, "netherite_layer_1");
        addEntityArmorEntry(entries, "netherite_layer_2");
        addEntityArmorEntry(entries, "piglin_leather_layer_1");
        addEntityArmorEntry(entries, "piglin_leather_layer_1_overlay");
        addEntityArmorEntry(entries, "turtle_layer_1");

        entries.sort(Comparator.comparing(e -> e.name));
        return entries;
    }

    private void addEntityArmorEntry(List<BrowseEntry> entries, String fileName) {
        Identifier id = Identifier.of("minecraft", "textures/models/armor/" + fileName + ".png");
        entries.add(new BrowseEntry(id, formatArmorDisplayName(fileName), EntryType.ENTITY, null));
    }

    private String formatArmorDisplayName(String fileName) {
        String[] words = fileName.split("_");
        StringBuilder sb = new StringBuilder();
        boolean previousWasLayer = false;
        for (String word : words) {
            if (sb.length() > 0) sb.append(' ');
            if (word.matches("\\d+")) {
                // If "layer" is already emitted, append only the number.
                if (previousWasLayer) {
                    sb.append(word);
                } else {
                    sb.append("Layer ").append(word);
                }
            } else {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            previousWasLayer = "layer".equals(word);
        }
        return sb.append(" Armor").toString();
    }

    private void addGUIEntry(List<BrowseEntry> entries, String texturePath, String displayName) {
        Identifier id = Identifier.of("minecraft", texturePath);
        entries.add(new BrowseEntry(id, displayName, EntryType.GUI, null));
    }

    private String generateGuiName(String path) {
        // gui/sprites/icon/ping_1 → Icon Ping 1

        String withoutPrefix = path.replace("gui/sprites/", "");

        String[] parts = withoutPrefix.split("/");

        StringBuilder name = new StringBuilder();

        for (String part : parts) {
            String[] words = part.split("_");

            for (String w : words) {
                if (w.isEmpty()) continue;
                name.append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1))
                        .append(" ");
            }
        }

        return name.toString().trim();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Custom background
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var pal = com.zeeesea.textureeditor.util.ColorPalette.INSTANCE;

        // Background
        context.fill(0, 0, this.width, this.height, pal.BROWSE_BACKGROUND);

        // Header bar
        context.fill(0, 0, this.width, 28, pal.HEADER_BAR);

        // Tab underline indicator
        if (tabAll != null) {
            ButtonWidget activeTab = switch (currentTab) {
                case ALL -> tabAll;
                case BLOCKS -> tabBlocks;
                case ITEMS -> tabItems;
                case MOBS -> tabMobs;
                case ENTITY -> tabEntity;
                case GUI -> tabGui;
                case SKY -> tabSky;
            };
            if (activeTab != null)
                context.fill(activeTab.getX(), activeTab.getY() + 20, activeTab.getX() + activeTab.getWidth(), activeTab.getY() + 22, pal.TAB_UNDERLINE);
        }

        // Render widgets
        super.render(context, mouseX, mouseY, delta);

        // Title info
        String entriesText = filteredEntries.size() + " entries";
        context.drawText(textRenderer, entriesText, this.width - textRenderer.getWidth(entriesText) - 5, 36, pal.TEXT_MUTED, false);

        // Render grid
        renderGrid(context, mouseX, mouseY);
    }

    private void renderGrid(DrawContext context, int mouseX, int mouseY) {
        var pal = com.zeeesea.textureeditor.util.ColorPalette.INSTANCE;
        int startIdx = scrollOffset * columns;
        int gridX = GRID_SIDE_MARGIN;
        int gridY = GRID_TOP;

        java.util.List<String> tooltipParts = null;
        java.util.List<Integer> tooltipColors = null;

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
            int bgColor = hovered ? pal.CELL_BG_HOVER : pal.CELL_BG;
            context.fill(x, y, x + CELL_SIZE, y + CELL_SIZE, bgColor);

            // Check if this entry has been modified (show indicator)
            boolean modified = isModified(entry);
            if (modified) {
                // Green border for modified textures
                drawRectOutline(context, x, y, x + CELL_SIZE, y + CELL_SIZE, pal.MODIFIED_BORDER);
            } else {
                drawRectOutline(context, x, y, x + CELL_SIZE, y + CELL_SIZE, pal.CELL_BORDER);
            }

            // Render item icon or label
            if (entry.stack != null && !entry.stack.isEmpty()) {
                int iconX = x + (CELL_SIZE - 16) / 2;
                int iconY = y + (CELL_SIZE - 16) / 2;
                context.drawItem(entry.stack, iconX, iconY);
            } else {
                // Try to render a cropped/scaled preview texture for GUI / ENTITY / other non-item entries
                PreviewCache cache = getOrBuildPreview(entry);
                if (cache != null && cache.dynamicId != null) {
                    // Fit preview into the icon cell with padding; preserve aspect ratio.
                    // Do NOT upscale previews here (preserve original look). Only downscale if larger than maxSize.
                    int pad = 4;
                    int maxSize = CELL_SIZE - pad * 2;
                    int srcW = cache.width;
                    int srcH = cache.height;
                    float fit = Math.min((float)maxSize / srcW, (float)maxSize / srcH);
                    if (fit > 1f) fit = 1f; // prevent upscaling
                    int drawW = Math.max(1, Math.round(srcW * fit));
                    int drawH = Math.max(1, Math.round(srcH * fit));
                    int drawX = x + (CELL_SIZE - drawW) / 2;
                    int drawY = y + (CELL_SIZE - drawH) / 2;
                    // Use same form as ExportScreen to render a dynamic NativeImageBackedTexture
                    context.drawTexture(RenderLayer::getGuiTextured,
                            cache.dynamicId, drawX, drawY, 0, 0, drawW, drawH, cache.width, cache.height, cache.width, cache.height);
                } else {
                    // GUI entries: draw abbreviated name as fallback
                    String label = entry.name.length() > 5 ? entry.name.substring(0, 5) : entry.name;
                    int lx = x + (CELL_SIZE - textRenderer.getWidth(label)) / 2;
                    int ly = y + (CELL_SIZE - 8) / 2;
                    context.drawText(textRenderer, label, lx, ly, pal.ENTRY_TEXT, false);
                }
            }

            // Tooltip on hover
            if (hovered) {
                tooltipParts = new java.util.ArrayList<>();
                tooltipColors = new java.util.ArrayList<>();
                tooltipParts.add(entry.name + " (" + entry.type.displayName + ")");
                tooltipColors.add(pal.TEXT_NORMAL);
                if (modified) {
                    tooltipParts.add(" [Modified] ");
                    tooltipColors.add(pal.STATUS_OK);
                    tooltipParts.add("- Right-click to reset");
                    tooltipColors.add(pal.TEXT_SUBTLE);
                }
            }
        }
        // Draw tooltip — use createNewRootLayer to render above item icons
        if (tooltipParts != null && !tooltipParts.isEmpty()) {
            int tw = 8; // padding
            for (String s : tooltipParts) tw += textRenderer.getWidth(s);
            int tx = Math.min(mouseX + 10, this.width - tw - 5);
            int ty = mouseY - 18;
            if (ty < 0) ty = mouseY + 15;
            context.fill(tx - 2, ty - 2, tx + tw, ty + 12, pal.PANEL_BG);
            drawRectOutline(context, tx - 2, ty - 2, tx + tw, ty + 12, pal.TEXT_SUBTLE);

            int cursorX = tx + 2;
            for (int pi = 0; pi < tooltipParts.size(); pi++) {
                String part = tooltipParts.get(pi);
                int color = tooltipColors.get(pi);
                // Draw tooltip text without shadow so it doesn't render an outline
                context.drawText(textRenderer, part, cursorX, ty, color, false);
                cursorX += textRenderer.getWidth(part);
            }
        }

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = this.width - 8;
            int sbY = GRID_TOP;
            int sbH = this.height - GRID_TOP - 10;
            // track/background
            context.fill(sbX, sbY, sbX + 6, sbY + sbH, pal.SCROLLBAR_BG);
            int thumbH = Math.max(20, sbH * visibleRows / ((filteredEntries.size() + columns - 1) / columns));
            int thumbY = sbY + (int)((float)scrollOffset / maxScroll * (sbH - thumbH));
            // thumb
            context.fill(sbX, thumbY, sbX + 6, thumbY + thumbH, pal.SCROLL_THUMB);
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
            // Don't call ItemTextureExtractor.extract() here — it's expensive and called every frame.
            // Instead check common texture path patterns for item textures.
            Identifier itemId = Registries.ITEM.getId(entry.stack.getItem());
            Identifier texId = Identifier.of(itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
            if (TextureManager.getInstance().getPixels(texId) != null) return true;

        } else if (entry.type == EntryType.MOB) {
            // MOB entries can be either direct texture IDs (some special entries) or spawn-egg items.
            // If this entry contains a spawn egg item, check the shared spawn_egg sprite and common entity texture paths.
            if (entry.stack != null) {
                try {
                    if (entry.stack.getItem() instanceof net.minecraft.item.SpawnEggItem) {
                        // Shared spawn egg sprite (tinted)
                        Identifier spawnTex = Identifier.of("minecraft", "textures/item/spawn_egg.png");
                        if (TextureManager.getInstance().getPixels(spawnTex) != null) return true;

                        // Also try common entity texture candidates derived from the spawn egg item id
                        Identifier itemId2 = Registries.ITEM.getId(entry.stack.getItem());
                        String path = itemId2.getPath();
                        if (path.endsWith("_spawn_egg")) {
                            String entityName = path.substring(0, path.length() - "_spawn_egg".length());
                            Identifier ent1 = Identifier.of(itemId2.getNamespace(), "textures/entity/" + entityName + ".png");
                            if (TextureManager.getInstance().getPixels(ent1) != null) return true;
                            Identifier ent2 = Identifier.of(itemId2.getNamespace(), "textures/entity/" + entityName + "/" + entityName + ".png");
                            if (TextureManager.getInstance().getPixels(ent2) != null) return true;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Fallback: treat the entry id as a texture path (existing behavior)
            Identifier fullId = asFullTextureId(entry.id);
            if (TextureManager.getInstance().getPixels(fullId) != null) return true;

        } else if (entry.type == EntryType.GUI || entry.type == EntryType.ENTITY) {
            Identifier fullId = asFullTextureId(entry.id);
            if (TextureManager.getInstance().getPixels(fullId) != null) return true;

            // If this might be an armor model texture, also check common alias paths
            try {
                if (fullId.getPath().startsWith("textures/models/armor/") && fullId.getPath().endsWith(".png")) {
                    for (Identifier alt : generateArmorAliasCandidates(fullId)) {
                        if (TextureManager.getInstance().getPixels(alt) != null) return true;
                    }
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Generate common alternative texture IDs for armor model textures.
     * Mirrors logic used in GuiTextureEditorScreen.addArmorAliasCandidates.
     */
    private java.util.List<Identifier> generateArmorAliasCandidates(Identifier baseId) {
        java.util.List<Identifier> candidates = new java.util.ArrayList<>();
        String path = baseId.getPath();
        if (!path.startsWith("textures/models/armor/") || !path.endsWith(".png")) return candidates;

        String name = path.substring("textures/models/armor/".length(), path.length() - 4);
        String suffix = "";
        if (name.endsWith("_overlay")) {
            suffix = "_overlay";
            name = name.substring(0, name.length() - "_overlay".length());
        }

        if (name.endsWith("_layer_1")) {
            String rawMaterial = name.substring(0, name.length() - "_layer_1".length());
            addEquipmentCandidate(candidates, baseId, "textures/entity/equipment/humanoid/", rawMaterial, suffix);
            return candidates;
        }
        if (name.endsWith("_layer_2")) {
            String rawMaterial = name.substring(0, name.length() - "_layer_2".length());
            addEquipmentCandidate(candidates, baseId, "textures/entity/equipment/humanoid_leggings/", rawMaterial, suffix);
            return candidates;
        }

        if (name.contains("_piglin_helmet")) {
            String rawMaterial = name.replace("_piglin_helmet", "");
            addEquipmentCandidate(candidates, baseId, "textures/entity/equipment/piglin_head/", rawMaterial, suffix);
            addEquipmentCandidate(candidates, baseId, "textures/entity/equipment/humanoid/", rawMaterial, suffix);
            return candidates;
        }

        return candidates;
    }

    private String normalizeArmorMaterial(String material) {
        String normalized = material;
        if (normalized.startsWith("piglin_")) {
            normalized = normalized.substring("piglin_".length());
        }
        if ("turtle".equals(normalized)) {
            normalized = "turtle_scute";
        }
        return normalized;
    }

    private void addEquipmentCandidate(java.util.List<Identifier> candidates, Identifier baseId, String folderPath, String rawMaterial, String suffix) {
        String normalized = normalizeArmorMaterial(rawMaterial);
        candidates.add(Identifier.of(baseId.getNamespace(), folderPath + rawMaterial + suffix + ".png"));
        if (!normalized.equals(rawMaterial)) {
            candidates.add(Identifier.of(baseId.getNamespace(), folderPath + normalized + suffix + ".png"));
        }
    }

    private void drawRectOutline(DrawContext ctx, int x1, int y1, int x2, int y2, int c) {
        ctx.fill(x1, y1, x2, y1 + 1, c);
        ctx.fill(x1, y2 - 1, x2, y2, c);
        ctx.fill(x1, y1, x1 + 1, y2, c);
        ctx.fill(x2 - 1, y1, x2, y2, c);
    }

    // --- Preview generation helpers ---

    private static class PreviewCache {
        Identifier sourceId;
        Identifier dynamicId;
        int width;
        int height;
        int cropX, cropY, cropW, cropH;
        int checksum;
    }

    private PreviewCache getOrBuildPreview(BrowseEntry entry) {
        try {
            Identifier fullId = asFullTextureId(entry.id);
            PreviewCache existing = previewCache.get(fullId);

            // Try to obtain pixel data from TextureManager (modified textures) first
            int[][] pixels = TextureManager.getInstance().getPixels(fullId);
            int w = 0, h = 0;
            if (pixels != null) {
                w = pixels.length > 0 ? pixels.length : 0;
                h = w > 0 ? pixels[0].length : 0;
            } else {
                // Try to load from resources. Use candidate searching to resolve moved/aliased textures (armor/entity)
                try {
                    Identifier resolved = findBestResource(fullId);
                    if (resolved != null) {
                        InputStream stream = client.getResourceManager().getResource(resolved).get().getInputStream();
                        NativeImage img = NativeImage.read(stream);
                        w = img.getWidth(); h = img.getHeight();
                        pixels = new int[w][h];
                        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) pixels[x][y] = img.getColorArgb(x, y);
                        img.close();
                        stream.close();
                        // if we found an alias, update fullId so previewCache keys align
                        fullId = resolved;
                    }
                } catch (Exception e) {
                    // ignore: resource not available
                }
            }

            if (pixels == null || w == 0 || h == 0) return null;

            int checksum = computePixelsChecksum(pixels, w, h);
            if (existing != null && existing.checksum == checksum && existing.dynamicId != null) return existing;

            // Decide whether to crop transparent border.
            int[] crop;
            if (entry.type == EntryType.ENTITY || entry.type == EntryType.MOB) {
                // For entity/armor textures show full texture exactly as in editor (no crop)
                crop = new int[]{0, 0, w, h};
            } else {
                crop = cropTransparentBounds(pixels, w, h, 8);
                if (crop == null) return null; // fully transparent
            }

            int cropW = crop[2], cropH = crop[3];
            int[][] cropped = new int[cropW][cropH];
            for (int cx = 0; cx < cropW; cx++) for (int cy = 0; cy < cropH; cy++) cropped[cx][cy] = pixels[crop[0] + cx][crop[1] + cy];

            // Register preview texture at the original cropped pixel size (no resampling)
            NativeImage out = nativeImageFromPixels(cropped, cropW, cropH);
            Identifier dynId = registerOrUpdatePreviewTexture(fullId, out);

            PreviewCache cache = new PreviewCache();
            cache.sourceId = fullId;
            cache.dynamicId = dynId;
            cache.width = cropW; cache.height = cropH;
            cache.cropX = crop[0]; cache.cropY = crop[1]; cache.cropW = cropW; cache.cropH = cropH;
            cache.checksum = checksum;

            previewCache.put(fullId, cache);
            return cache;
        } catch (Exception e) {
            return null;
        }
    }

    private Identifier findBestResource(Identifier requestId) {
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier full = requestId.getPath().startsWith("textures/") ? requestId : Identifier.of(requestId.getNamespace(), "textures/" + requestId.getPath() + ".png");
        try {
            var opt = client.getResourceManager().getResource(full);
            if (opt.isPresent()) return full;
        } catch (Exception ignored) {}

        // Armor aliases
        try {
            if (full.getPath().startsWith("textures/models/armor/") && full.getPath().endsWith(".png")) {
                for (Identifier alt : generateArmorAliasCandidates(full)) {
                    try { if (client.getResourceManager().getResource(alt).isPresent()) return alt; } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        // Entity alternatives for textures/entity/<name>.png -> textures/entity/<name>/<name>.png etc.
        try {
            if (full.getPath().startsWith("textures/entity/") && full.getPath().endsWith(".png")) {
                String filename = full.getPath().replace("textures/entity/", "").replace(".png", "");
                if (!filename.contains("/")) {
                    Identifier a1 = Identifier.of(full.getNamespace(), "textures/entity/" + filename + "/" + filename + ".png");
                    if (client.getResourceManager().getResource(a1).isPresent()) return a1;
                    Identifier a2 = Identifier.of(full.getNamespace(), "textures/entity/equipment/wings/" + filename + ".png");
                    if (client.getResourceManager().getResource(a2).isPresent()) return a2;
                    Identifier a3 = Identifier.of(full.getNamespace(), "textures/entity/equipment/" + filename + ".png");
                    if (client.getResourceManager().getResource(a3).isPresent()) return a3;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private int computePixelsChecksum(int[][] pixels, int w, int h) {
        int hash = 1;
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) hash = 31 * hash + pixels[x][y];
        return hash;
    }

    private int[] cropTransparentBounds(int[][] pixels, int w, int h, int alphaThreshold) {
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int argb = pixels[x][y];
                int a = (argb >> 24) & 0xFF;
                if (a > alphaThreshold) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0 || maxY < 0) return null;
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    private int[][] scalePixelsPreserveAlpha(int[][] src, int srcW, int srcH, int dstW, int dstH) {
        // Nearest-neighbor down/up-scaling using floor mapping to avoid introducing blended pixels.
        int[][] out = new int[dstW][dstH];
        for (int x = 0; x < dstW; x++) {
            int sx = Math.min(srcW - 1, (int)((long)x * srcW / dstW));
            for (int y = 0; y < dstH; y++) {
                int sy = Math.min(srcH - 1, (int)((long)y * srcH / dstH));
                out[x][y] = src[sx][sy];
            }
        }
        return out;
    }

    private NativeImage nativeImageFromPixels(int[][] pixels, int w, int h) {
        NativeImage img = new NativeImage(w, h, false);
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) img.setColorArgb(x, y, pixels[x][y]);
        return img;
    }

    private Identifier registerOrUpdatePreviewTexture(Identifier sourceId, NativeImage img) {
        try {
            String safe = sourceId.toString().replaceAll("[^a-zA-Z0-9._-]", "_");
            Identifier dyn = Identifier.of("textureeditor", "preview/" + safe);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "textureeditor_preview", img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(dyn, tex);
            try { tex.upload(); } catch (Exception ignored) {}
            previewTextureIds.put(sourceId, dyn);
            // Try to disable linear filtering to preserve pixel-perfect look (best-effort via reflection)
            try {
                Object reg = MinecraftClient.getInstance().getTextureManager().getTexture(dyn);
                if (reg != null) {
                    try {
                        java.lang.reflect.Method mf = reg.getClass().getMethod("setFilter", boolean.class);
                        mf.invoke(reg, false);
                    } catch (NoSuchMethodException ignored) {
                        // Some MC versions may not have setFilter; ignore
                    }
                }
            } catch (Exception ignored) {}
            return dyn;
        } catch (Exception e) {
            return null;
        }
    }

    private void invalidatePreviewForSource(Identifier source) {
        PreviewCache c = previewCache.remove(source);
        Identifier dyn = previewTextureIds.remove(source);
        if (dyn != null) {
            try {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(dyn);
            } catch (Exception ignored) {}
        }
        if (c != null) {
            // nothing else to free — NativeImage owned by texture manager
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // Left-click: open editor, Right-click: reset texture
        if (button == 0 || button == 1) {
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
                    if (button == 0) {
                        openEditor(filteredEntries.get(i));
                    } else {
                        resetEntry(filteredEntries.get(i));
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private void openEditor(BrowseEntry entry) {
        com.zeeesea.textureeditor.editor.ExternalEditorManager extMgr = com.zeeesea.textureeditor.editor.ExternalEditorManager.getInstance();
        boolean useExternal = extMgr.isExternalEditorEnabled();

        if (entry.type == EntryType.BLOCK) {
            Block block = Registries.BLOCK.get(entry.id);
            if (useExternal) {
                openExternalForBlock(block);
            } else {
                client.setScreen(new EditorScreen(block, this));
            }
        } else if (entry.type == EntryType.MOB) {
            if (entry.id.getPath().startsWith("textures/")) {
                if (useExternal) {
                    if (!openExternalForDirectTexture(entry.id, entry.name)) {
                        client.setScreen(new GuiTextureEditorScreen(entry.id, entry.name, this));
                    }
                } else {
                    client.setScreen(new GuiTextureEditorScreen(entry.id, entry.name, this));
                }
            } else if (entry.stack == null) {
                if (useExternal) {
                    if (!openExternalForDirectTexture(entry.id, entry.name)) {
                        client.setScreen(new GuiTextureEditorScreen(entry.id, entry.name, this));
                    }
                } else {
                    client.setScreen(new GuiTextureEditorScreen(entry.id, entry.name, this));
                }
            } else if (client.world != null) {
                net.minecraft.entity.Entity entity = com.zeeesea.textureeditor.util.EntityMapper.getEntityFromItem(entry.stack, client.world);
                if (entity != null) {
                    if (useExternal) {
                        openExternalForMob(entity);
                    } else {
                        client.setScreen(new MobEditorScreen(entity, this));
                    }
                } else {
                    if (useExternal) {
                        openExternalForItem(entry.stack);
                    } else {
                        client.setScreen(new ItemEditorScreen(entry.stack, this));
                    }
                }
            }
        } else if (entry.type == EntryType.ITEM) {
            if (entry.stack != null) {
                if (useExternal) {
                    openExternalForItem(entry.stack);
                } else {
                    client.setScreen(new ItemEditorScreen(entry.stack, this));
                }
            }
        } else if (entry.type == EntryType.ENTITY) {
            if (useExternal) {
                if (!openExternalForDirectTexture(entry.id, entry.name)) {
                    client.setScreen(new GuiTextureEditorScreen(entry.id, entry.name, this));
                }
            } else {
                client.setScreen(new GuiTextureEditorScreen(entry.id, entry.name, this));
            }
        } else if (entry.type == EntryType.GUI) {
            if (useExternal) {
                if (!openExternalForDirectTexture(entry.id, entry.name)) {
                    // Special-case: block breaking frames (destroy_stage_0..9) should open the BreakingEditorScreen
                    if (entry.id.getPath().contains("destroy_stage_0.png") || "Block Breaking".equals(entry.name)) {
                        client.setScreen(new BreakingEditorScreen(this));
                    } else {
                        client.setScreen(createGuiEditorWithTint(entry));
                    }
                }
            } else {
                if (entry.id.getPath().contains("destroy_stage_0.png") || "Block Breaking".equals(entry.name)) {
                    client.setScreen(new BreakingEditorScreen(this));
                } else {
                    client.setScreen(createGuiEditorWithTint(entry));
                }
            }
        } else if (entry.type == EntryType.PARTICLE) {
            if (useExternal) {
                if (!openExternalForDirectTexture(entry.id, entry.name)) {
                    client.setScreen(new GuiTextureEditorScreen(entry.id, entry.name, this));
                }
            } else {
                client.setScreen(new GuiTextureEditorScreen(entry.id, entry.name, this));
            }
        }
    }

    /**
     * Right-click on a browser entry to reset its texture to default.
     */
    private void resetEntry(BrowseEntry entry) {
        if (!isModified(entry)) return;

        if (entry.type == EntryType.BLOCK) {
            Block block = Registries.BLOCK.get(entry.id);
            for (Direction dir : Direction.values()) {
                TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(block.getDefaultState(), dir);
                if (tex != null && TextureManager.getInstance().getPixels(tex.textureId()) != null) {
                    Identifier spriteId = Identifier.of(tex.textureId().getNamespace(),
                            tex.textureId().getPath().replace("textures/", "").replace(".png", ""));
                    com.zeeesea.textureeditor.editor.ExternalEditorManager.resetTextureStatic(
                            tex.textureId(), spriteId, tex.pixels(), tex.width(), tex.height());
                }
            }
        } else if (entry.type == EntryType.ITEM && entry.stack != null) {
            ItemTextureExtractor.ItemTexture tex = ItemTextureExtractor.extract(entry.stack);
            if (tex != null && TextureManager.getInstance().getPixels(tex.textureId()) != null) {
                com.zeeesea.textureeditor.editor.ExternalEditorManager.resetTextureStatic(
                        tex.textureId(), tex.spriteId(), tex.pixels(), tex.width(), tex.height());
            }
        } else if (entry.type == EntryType.MOB || entry.type == EntryType.GUI || entry.type == EntryType.ENTITY) {
            Identifier fullId = asFullTextureId(entry.id);
            if (TextureManager.getInstance().getPixels(fullId) != null) {
                TextureManager.getInstance().removeTexture(fullId);
                String safeName = fullId.toString().replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
                java.io.File tempFile = new java.io.File(
                        com.zeeesea.textureeditor.editor.ExternalEditorSession.getTempDir(), safeName);
                if (tempFile.exists()) tempFile.delete();
                if (client != null) client.reloadResources();
            }
        }
    }

    private Identifier asFullTextureId(Identifier id) {
        return id.getPath().startsWith("textures/")
                ? id
                : Identifier.of(id.getNamespace(), "textures/" + id.getPath() + ".png");
    }

    private GuiTextureEditorScreen createGuiEditorWithTint(BrowseEntry entry) {
        GuiTextureEditorScreen screen = new GuiTextureEditorScreen(entry.id, entry.name, this);

        // Tint für bekannte tinted Block-Texturen setzen
        if (entry.id.getPath().contains("grass_block_side_overlay") ||
                entry.id.getPath().contains("grass_block_top")) {
            if (client.world != null && client.player != null) {
                net.minecraft.block.BlockState grassState =
                        net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState();
                int color = client.getBlockColors().getColor(
                        grassState, client.world, client.player.getBlockPos(), 0);
                if (color != -1) {
                    screen.setTint(color);
                }
            }
        }

        return screen;
    }

    // --- External editor helpers ---

    private void openExternalForBlock(Block block) {
        TextureExtractor.BlockFaceTexture tex = TextureExtractor.extract(block.getDefaultState(), Direction.UP);
        if (tex == null) {
            for (Direction dir : Direction.values()) {
                tex = TextureExtractor.extract(block.getDefaultState(), dir);
                if (tex != null) break;
            }
        }
        if (tex != null) {
            Identifier spriteId = Identifier.of(tex.textureId().getNamespace(),
                    tex.textureId().getPath().replace("textures/", "").replace(".png", ""));
            int[][] origCopy = copyPixels(tex.pixels(), tex.width(), tex.height());
            com.zeeesea.textureeditor.editor.ExternalEditorManager.getInstance().startAtlasSession(
                    tex.textureId(), spriteId, tex.pixels(), origCopy, tex.width(), tex.height());
        }
    }

    private void openExternalForItem(ItemStack stack) {
        ItemTextureExtractor.ItemTexture tex = ItemTextureExtractor.extract(stack);
        if (tex != null) {
            int[][] origCopy = copyPixels(tex.pixels(), tex.width(), tex.height());
            if (tex.spriteId() != null && !tex.textureId().getPath().startsWith("textures/entity/")) {
                com.zeeesea.textureeditor.editor.ExternalEditorManager.getInstance().startAtlasSession(
                        tex.textureId(), tex.spriteId(), tex.pixels(), origCopy, tex.width(), tex.height());
            } else {
                com.zeeesea.textureeditor.editor.ExternalEditorManager.getInstance().startEntitySession(
                        tex.textureId(), tex.pixels(), origCopy, tex.width(), tex.height());
            }
        }
    }

    private void openExternalForMob(net.minecraft.entity.Entity entity) {
        com.zeeesea.textureeditor.texture.MobTextureExtractor.MobTexture tex =
                com.zeeesea.textureeditor.texture.MobTextureExtractor.extract(entity);
        if (tex != null) {
            int[][] origCopy = copyPixels(tex.pixels(), tex.width(), tex.height());
            com.zeeesea.textureeditor.editor.ExternalEditorManager.getInstance().startEntitySession(
                    tex.textureId(), tex.pixels(), origCopy, tex.width(), tex.height());
        }
    }

    private boolean openExternalForDirectTexture(Identifier textureId, String name) {
        boolean isGuiSprite = textureId.getPath().startsWith("gui/sprites/")
                || textureId.getPath().startsWith("gui/container/")
                || textureId.getPath().startsWith("gui/");

        Identifier fullId = textureId.getPath().startsWith("textures/") ? textureId :
                Identifier.of(textureId.getNamespace(), "textures/" + textureId.getPath() + ".png");
        try {
            var optResource = client.getResourceManager().getResource(fullId);
            if (optResource.isPresent()) {
                java.io.InputStream stream = optResource.get().getInputStream();
                net.minecraft.client.texture.NativeImage image = net.minecraft.client.texture.NativeImage.read(stream);
                int w = image.getWidth(), h = image.getHeight();
                int[][] pixels = new int[w][h];
                for (int x = 0; x < w; x++)
                    for (int y = 0; y < h; y++)
                        pixels[x][y] = image.getColorArgb(x, y);
                image.close();
                stream.close();
                int[][] origCopy = copyPixels(pixels, w, h);

                if (isGuiSprite) {
                    com.zeeesea.textureeditor.editor.ExternalEditorManager.getInstance().startGuiSession(
                            fullId, textureId, pixels, origCopy, w, h);
                } else {
                    com.zeeesea.textureeditor.editor.ExternalEditorManager.getInstance().startEntitySession(
                            fullId, pixels, origCopy, w, h);
                }
                return true;
            }
        } catch (Exception e) {
            System.out.println("[TextureEditor] Failed to load texture for external editor: " + fullId + " - " + e.getMessage());
        }
        return false;
    }

    private static int[][] copyPixels(int[][] src, int w, int h) {
        int[][] copy = new int[w][h];
        for (int x = 0; x < w; x++) System.arraycopy(src[x], 0, copy[x], 0, h);
        return copy;
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
        GUI("GUI"),
        ENTITY("Entity"),
        PARTICLE("Particle");

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
