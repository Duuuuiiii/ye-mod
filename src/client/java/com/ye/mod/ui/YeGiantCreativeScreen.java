package com.ye.mod.ui;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.YeZhCnDict;
import com.ye.mod.modules.player.PlayerUiModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Giant Customizable Widescreen Creative Inventory (大尺寸创造物品栏 — up to 40x20 items on screen at once!):
 * - Dynamically scales cell size to fit up to 40 columns x 20 rows cleanly on screen!
 * - Instant Chinese + English + Registry ID search bar + Creative Category Pills!
 * - Full Creative packet sync via gameMode.handleCreativeModeItemAdd (Left-click carry, Shift-click 64 to inv,
 *   Press 1..9 while hovering to put directly into hotbar 1..9, Trash button, plus 1-click toggle to Vanilla Creative!).
 */
public class YeGiantCreativeScreen extends Screen {
    private final List<CreativeModeTab> categoryTabs = new ArrayList<>();
    private int selectedCatIdx = 0; // 0 = All Items, 1..N = specific CreativeModeTab
    private String searchQuery = "";
    private boolean searchFocused = true;

    private final List<ItemStack> filteredItems = new ArrayList<>();
    private ItemStack carriedStack = ItemStack.EMPTY;
    private int scrollRow = 0;
    private int maxScrollRow = 0;

    private ItemStack hoveredTooltipStack = ItemStack.EMPTY;

    public YeGiantCreativeScreen() {
        super(Component.literal("Ye Creative"));
    }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            CreativeModeTabs.tryRebuildTabContents(
                    mc.player.connection.enabledFeatures(),
                    mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get(),
                    mc.level.registryAccess()
            );
        }
        categoryTabs.clear();
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() == CreativeModeTab.Type.CATEGORY && !tab.getDisplayItems().isEmpty()) {
                categoryTabs.add(tab);
            }
        }
        rebuildFilteredItems();
    }

    private void rebuildFilteredItems() {
        filteredItems.clear();
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        Set<Item> dedup = new LinkedHashSet<>();

        if (selectedCatIdx == 0) {
            for (CreativeModeTab tab : categoryTabs) {
                for (ItemStack st : tab.getDisplayItems()) {
                    if (!st.isEmpty() && dedup.add(st.getItem()) && matchesQuery(st, q)) {
                        filteredItems.add(st.copyWithCount(1));
                    }
                }
            }
            // Include operator / uncategorized items if searching
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR || !dedup.add(item)) continue;
                ItemStack st = item.getDefaultInstance();
                if (matchesQuery(st, q)) {
                    filteredItems.add(st);
                }
            }
        } else if (selectedCatIdx - 1 < categoryTabs.size()) {
            CreativeModeTab tab = categoryTabs.get(selectedCatIdx - 1);
            for (ItemStack st : tab.getDisplayItems()) {
                if (!st.isEmpty() && matchesQuery(st, q)) {
                    filteredItems.add(st.copyWithCount(1));
                }
            }
        }
        scrollRow = 0;
    }

    private static boolean matchesQuery(ItemStack st, String q) {
        if (q.isEmpty()) return true;
        String zh = YeZhCnDict.itemName(st).toLowerCase(Locale.ROOT);
        if (zh.contains(q)) return true;
        String en = st.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (en.contains(q)) return true;
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().toLowerCase(Locale.ROOT);
        return id.contains(q);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        PlayerUiModule mod = com.ye.mod.core.ModuleManager.get(PlayerUiModule.class);
        int cols = mod != null ? (int) Math.round(mod.creativeCols.get()) : 24;
        int rows = mod != null ? (int) Math.round(mod.creativeRows.get()) : 12;

        boolean mech = (ConfigManager.uiStyle == ConfigManager.UiStyle.MECHANICAL);
        int accent = ConfigManager.getAccentColor();
        hoveredTooltipStack = ItemStack.EMPTY;

        int maxAvailW = this.width - 28;
        int maxAvailH = this.height - 96;
        float cellStep = Math.min(20.0f, Math.min((float) maxAvailW / cols, (float) maxAvailH / rows));
        cellStep = Math.max(9.5f, cellStep);
        float itemScale = Math.min(1.0f, (cellStep - 1.6f) / 16.0f);

        int gridW = Math.round(cols * cellStep);
        int gridH = Math.round(rows * cellStep);
        int winW = Math.max(340, gridW + 24);
        int winH = gridH + 84;
        int wx = (this.width - winW) / 2;
        int wy = Math.max(6, (this.height - winH) / 2);
        int rad = mech ? 0 : 10;

        if (ConfigManager.uiTheme == ConfigManager.UiTheme.GAUSSIAN_GLASS) {
            g.blurBeforeThisStratum();
        }

        AppleUiRenderer.drawElevatedWindow(g, wx, wy, winW, winH, rad);

        // 1. Header: Title + Instant Search Box + Vanilla Switch Button
        YeHdFont.draw(g, ConfigManager.tr("创造物品库 (" + filteredItems.size() + ")", "Creative (" + filteredItems.size() + ")"),
                wx + 12, wy + 8, 9.2f, AppleUiRenderer.textPrimary(), false, true);

        int sbX = wx + 118;
        int sbW = Math.max(110, winW - 224);
        AppleUiRenderer.drawRoundedRect(g, sbX, wy + 5, sbW, 18, mech ? 0 : 5, 0xCC090D16);
        AppleUiRenderer.drawRoundedOutline(g, sbX, wy + 5, sbW, 18, mech ? 0 : 5, 1.1f, searchFocused ? accent : AppleUiRenderer.borderSubtle());
        String cursor = (searchFocused && (System.currentTimeMillis() / 450L) % 2 == 0) ? "|" : "";
        String dispQ = searchQuery.isEmpty() && !searchFocused
                ? ConfigManager.tr("搜索中文 / 英文 / ID...", "Search items...")
                : (searchQuery + cursor);
        YeHdFont.draw(g, dispQ, sbX + 7, wy + 9.0f, 8.4f,
                searchQuery.isEmpty() && !searchFocused ? AppleUiRenderer.textTertiary() : 0xFFF8FAFC, false, false);

        int vanBtnW = 68;
        int vanBtnX = wx + winW - vanBtnW - 10;
        AppleUiRenderer.drawRoundedRect(g, vanBtnX, wy + 5, vanBtnW, 18, mech ? 0 : 5, AppleUiRenderer.bgCardHover());
        AppleUiRenderer.drawCenteredSmooth(g, ConfigManager.tr("切换原版", "Vanilla"), vanBtnX + vanBtnW * 0.5f, wy + 9.0f, 8.0f, AppleUiRenderer.textPrimary(), false);

        // 2. Category Strip
        int catY = wy + 27;
        int cx = wx + 10;
        int totalCats = Math.min(categoryTabs.size() + 1, 13);
        for (int i = 0; i < totalCats; i++) {
            String lbl = (i == 0) ? ConfigManager.tr("全部", "All") : YeHdFont.clipToWidth(categoryTabs.get(i - 1).getDisplayName().getString(), 7.8f, false, 52);
            int pw = Math.max(28, YeHdFont.width(lbl, 7.8f, i == selectedCatIdx) + 10);
            if (cx + pw > wx + winW - 10) break;
            boolean sel = (i == selectedCatIdx);
            AppleUiRenderer.drawRoundedRect(g, cx, catY, pw, 15, mech ? 0 : 4, sel ? accent : AppleUiRenderer.bgCard());
            AppleUiRenderer.drawCenteredSmooth(g, lbl, cx + pw * 0.5f, catY + 3.2f, 7.8f,
                    sel ? 0xFFFFFFFF : AppleUiRenderer.textSecondary(), sel);
            cx += pw + 4;
        }

        // 3. Giant Scaled Item Grid
        int totalRows = (filteredItems.size() + cols - 1) / cols;
        maxScrollRow = Math.max(0, totalRows - rows);
        scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow));

        int gx0 = wx + (winW - gridW) / 2;
        int gy0 = wy + 46;

        for (int r = 0; r < rows; r++) {
            int dataRow = scrollRow + r;
            float cy = gy0 + r * cellStep;
            for (int c = 0; c < cols; c++) {
                float cellX = gx0 + c * cellStep;
                int idx = dataRow * cols + c;
                boolean hov = mouseX >= cellX && mouseX < cellX + cellStep && mouseY >= cy && mouseY < cy + cellStep;
                int cellBg = hov ? AppleUiRenderer.withAlpha(accent, 0.36f) : 0x241E293B;
                g.fill(Math.round(cellX), Math.round(cy), Math.round(cellX + cellStep - 1), Math.round(cy + cellStep - 1), cellBg);

                if (idx < filteredItems.size()) {
                    ItemStack st = filteredItems.get(idx);
                    if (hov) hoveredTooltipStack = st;
                    var pose = g.pose();
                    pose.pushMatrix();
                    pose.translate(cellX + (cellStep - 16f * itemScale) * 0.5f, cy + (cellStep - 16f * itemScale) * 0.5f);
                    pose.scale(itemScale, itemScale);
                    g.renderItem(st, 0, 0);
                    pose.popMatrix();
                }
            }
        }

        // Scrollbar
        if (maxScrollRow > 0) {
            int barX = gx0 + gridW + 3;
            g.fill(barX, gy0, barX + 3, gy0 + gridH, 0x22FFFFFF);
            int thumbH = Math.max(16, Math.round((rows / (float) totalRows) * gridH));
            int thumbY = gy0 + Math.round(((gridH - thumbH) * scrollRow) / (float) maxScrollRow);
            g.fill(barX, thumbY, barX + 3, thumbY + thumbH, accent);
        }

        // 4. Bottom Player Hotbar (Slots 0..8 -> Packet Slots 36..44) + Clear/Trash Button
        int hbY = wy + winH - 28;
        int hbW = 9 * 20;
        int hbX = wx + (winW - hbW) / 2;
        Inventory inv = mc.player.getInventory();
        for (int s = 0; s < 9; s++) {
            int sx = hbX + s * 20;
            boolean hov = mouseX >= sx && mouseX < sx + 18 && mouseY >= hbY && mouseY < hbY + 18;
            AppleUiRenderer.drawRoundedRect(g, sx, hbY, 18, 18, mech ? 0 : 4, hov ? AppleUiRenderer.withAlpha(accent, 0.42f) : AppleUiRenderer.bgCard());
            AppleUiRenderer.drawRoundedOutline(g, sx, hbY, 18, 18, mech ? 0 : 4, 1.0f, AppleUiRenderer.borderSubtle());
            ItemStack st = inv.getItem(s);
            if (!st.isEmpty()) {
                g.renderItem(st, sx + 1, hbY + 1);
                g.renderItemDecorations(mc.font, st, sx + 1, hbY + 1);
                if (hov) hoveredTooltipStack = st;
            }
        }

        // Trash Slot on right of hotbar
        int trX = hbX + hbW + 8;
        boolean trHov = mouseX >= trX && mouseX < trX + 46 && mouseY >= hbY && mouseY < hbY + 18;
        AppleUiRenderer.drawRoundedRect(g, trX, hbY, 46, 18, mech ? 0 : 4, trHov ? 0xFFEF4444 : AppleUiRenderer.bgCard());
        AppleUiRenderer.drawCenteredSmooth(g, ConfigManager.tr("清空手拿", "Trash"), trX + 23, hbY + 4.5f, 7.8f, 0xFFF8FAFC, false);

        // Carried item on cursor + Tooltip
        if (!carriedStack.isEmpty()) {
            g.renderItem(carriedStack, mouseX - 8, mouseY - 8);
            g.renderItemDecorations(mc.font, carriedStack, mouseX - 8, mouseY - 8);
        } else if (!hoveredTooltipStack.isEmpty()) {
            String nm = YeZhCnDict.itemName(hoveredTooltipStack);
            int tw = YeHdFont.width(nm, 8.4f, true) + 14;
            int tx = Math.min(this.width - tw - 4, mouseX + 10);
            int ty = Math.max(4, mouseY - 20);
            AppleUiRenderer.drawRoundedRect(g, tx, ty, tw, 17, mech ? 0 : 4, 0xF0090D16);
            AppleUiRenderer.drawRoundedOutline(g, tx, ty, tw, 17, mech ? 0 : 4, 1.0f, accent);
            YeHdFont.draw(g, nm, tx + 7, ty + 3.8f, 8.4f, 0xFFF8FAFC, false, true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return false;

        double mx = event.x(), my = event.y();
        PlayerUiModule mod = com.ye.mod.core.ModuleManager.get(PlayerUiModule.class);
        int cols = mod != null ? (int) Math.round(mod.creativeCols.get()) : 24;
        int rows = mod != null ? (int) Math.round(mod.creativeRows.get()) : 12;

        float cellStep = Math.max(9.5f, Math.min(20.0f, Math.min((float) (this.width - 28) / cols, (float) (this.height - 96) / rows)));
        int gridW = Math.round(cols * cellStep);
        int gridH = Math.round(rows * cellStep);
        int winW = Math.max(340, gridW + 24);
        int winH = gridH + 84;
        int wx = (this.width - winW) / 2;
        int wy = Math.max(6, (this.height - winH) / 2);

        int sbX = wx + 118, sbW = Math.max(110, winW - 224);
        if (mx >= sbX && mx <= sbX + sbW && my >= wy + 5 && my <= wy + 23) {
            searchFocused = true;
            if (event.button() == 1) {
                searchQuery = "";
                rebuildFilteredItems();
            }
            return true;
        }

        int vanBtnX = wx + winW - 78;
        if (mx >= vanBtnX && mx <= vanBtnX + 68 && my >= wy + 5 && my <= wy + 23) {
            YeQuickMenu.playClick();
            PlayerUiModule.bypassGiantCreativeOnce = true;
            mc.setScreen(new CreativeModeInventoryScreen(
                    mc.player,
                    mc.player.connection.enabledFeatures(),
                    mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get()
            ));
            return true;
        }

        // Category pills
        int catY = wy + 27, cx = wx + 10;
        int totalCats = Math.min(categoryTabs.size() + 1, 13);
        for (int i = 0; i < totalCats; i++) {
            String lbl = (i == 0) ? ConfigManager.tr("全部", "All") : YeHdFont.clipToWidth(categoryTabs.get(i - 1).getDisplayName().getString(), 7.8f, false, 52);
            int pw = Math.max(28, YeHdFont.width(lbl, 7.8f, i == selectedCatIdx) + 10);
            if (cx + pw > wx + winW - 10) break;
            if (mx >= cx && mx <= cx + pw && my >= catY && my <= catY + 15) {
                selectedCatIdx = i;
                YeQuickMenu.playClick();
                rebuildFilteredItems();
                return true;
            }
            cx += pw + 4;
        }

        // Grid click
        int gx0 = wx + (winW - gridW) / 2;
        int gy0 = wy + 46;
        if (mx >= gx0 && mx < gx0 + gridW && my >= gy0 && my < gy0 + gridH) {
            int c = (int) ((mx - gx0) / cellStep);
            int r = (int) ((my - gy0) / cellStep);
            int idx = (scrollRow + r) * cols + c;
            if (idx >= 0 && idx < filteredItems.size()) {
                ItemStack template = filteredItems.get(idx);
                boolean shift = GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                if (shift || event.button() == 2) {
                    giveToFirstEmptyHotbarOrSlot(mc, template.copyWithCount(template.getMaxStackSize()));
                } else if (carriedStack.isEmpty()) {
                    carriedStack = template.copyWithCount(event.button() == 1 ? template.getMaxStackSize() : 1);
                } else if (ItemStack.isSameItemSameComponents(carriedStack, template)) {
                    int add = event.button() == 1 ? 8 : 1;
                    carriedStack.setCount(Math.min(carriedStack.getMaxStackSize(), carriedStack.getCount() + add));
                } else {
                    carriedStack = template.copyWithCount(1);
                }
                YeQuickMenu.playClick();
                return true;
            }
        }

        // Hotbar slots (packet slots 36..44)
        int hbY = wy + winH - 28;
        int hbW = 9 * 20;
        int hbX = wx + (winW - hbW) / 2;
        for (int s = 0; s < 9; s++) {
            int sx = hbX + s * 20;
            if (mx >= sx && mx < sx + 18 && my >= hbY && my < hbY + 18) {
                ItemStack existing = mc.player.getInventory().getItem(s).copy();
                if (!carriedStack.isEmpty()) {
                    mc.player.getInventory().setItem(s, carriedStack.copy());
                    mc.gameMode.handleCreativeModeItemAdd(carriedStack.copy(), 36 + s);
                    carriedStack = (event.button() == 1) ? carriedStack : existing;
                } else if (!existing.isEmpty()) {
                    if (event.button() == 1) {
                        mc.player.getInventory().setItem(s, ItemStack.EMPTY);
                        mc.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, 36 + s);
                    } else {
                        carriedStack = existing;
                        mc.player.getInventory().setItem(s, ItemStack.EMPTY);
                        mc.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, 36 + s);
                    }
                }
                YeQuickMenu.playClick();
                return true;
            }
        }

        int trX = hbX + hbW + 8;
        if (mx >= trX && mx < trX + 46 && my >= hbY && my < hbY + 18) {
            carriedStack = ItemStack.EMPTY;
            YeQuickMenu.playClick();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private static void giveToFirstEmptyHotbarOrSlot(Minecraft mc, ItemStack stack) {
        Inventory inv = mc.player.getInventory();
        for (int s = 0; s < 9; s++) {
            if (inv.getItem(s).isEmpty()) {
                inv.setItem(s, stack.copy());
                mc.gameMode.handleCreativeModeItemAdd(stack.copy(), 36 + s);
                return;
            }
        }
        int sel = inv.getSelectedSlot();
        inv.setItem(sel, stack.copy());
        mc.gameMode.handleCreativeModeItemAdd(stack.copy(), 36 + sel);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow - (int) Math.signum(scrollY) * 3));
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchFocused) {
            String ch = event.codepointAsString();
            if (ch != null && !ch.isEmpty() && searchQuery.length() < 32) {
                searchQuery += ch;
                rebuildFilteredItems();
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        var mci = net.minecraft.client.Minecraft.getInstance();
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 && !searchFocused && hoveredTooltipStack != null && !hoveredTooltipStack.isEmpty()) {
            int slot = key - GLFW.GLFW_KEY_1;
            if (mci.gameMode != null) {
                mci.gameMode.handleCreativeModeItemAdd(hoveredTooltipStack.copyWithCount(hoveredTooltipStack.getMaxStackSize()), 36 + slot);
            }
            YeQuickMenu.playClick();
            return true;
        }
        if (mci.options.keySwapOffhand.matches(event) && !searchFocused && hoveredTooltipStack != null && !hoveredTooltipStack.isEmpty()) {
            if (mci.gameMode != null) {
                mci.gameMode.handleCreativeModeItemAdd(hoveredTooltipStack.copyWithCount(hoveredTooltipStack.getMaxStackSize()), 45);
            }
            YeQuickMenu.playClick();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!carriedStack.isEmpty()) {
                carriedStack = ItemStack.EMPTY;
                return true;
            }
            onClose();
            return true;
        }

        // Press 1..9 while hovering any item in the giant grid to put a full stack directly into hotbar 1..9!
        if (!hoveredTooltipStack.isEmpty() && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.gameMode != null) {
                int slot = key - GLFW.GLFW_KEY_1;
                ItemStack full = hoveredTooltipStack.copyWithCount(hoveredTooltipStack.getMaxStackSize());
                mc.player.getInventory().setItem(slot, full.copy());
                mc.gameMode.handleCreativeModeItemAdd(full, 36 + slot);
                YeQuickMenu.playClick();
                return true;
            }
        }

        if (searchFocused && key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                rebuildFilteredItems();
            }
            return true;
        }

        if (!searchFocused && Minecraft.getInstance().options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
