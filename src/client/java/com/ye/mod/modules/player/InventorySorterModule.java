package com.ye.mod.modules.player;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.KeybindSetting;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One-Key Inventory & Chest Sorter (一键整理):
 * Press R inside any Inventory or Chest screen to stack-merge and sort items cleanly.
 */
public class InventorySorterModule extends Module {
    public final KeybindSetting sortKey = addSetting(new KeybindSetting(
            "sortKey", "整理快捷键", "在背包或箱子界面按下即刻整理", GLFW.GLFW_KEY_R
    ).functionGroup());

    private boolean wasDown = false;

    public InventorySorterModule() {
        super("inventory_sorter", "一键整理", "Inv Sorter", "在背包或箱子内一键合并堆叠并按类型排序", Category.UTILITY);
        setEnabled(true, true);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        boolean down = com.ye.mod.core.KeyCombo.isComboDown(mc.getWindow().handle(), sortKey.get());
        if (down && !wasDown && mc.screen instanceof AbstractContainerScreen<?> scr) {
            sortScreen(scr);
        }
        wasDown = down;
    }

    public static void sortScreen(AbstractContainerScreen<?> scr) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        AbstractContainerMenu menu = scr.getMenu();
        int containerId = menu.containerId;

        // Determine target slots: if chest/container slots exist (>0 non-player slots), sort container; else sort player main inv (9..35)
        List<Slot> targetSlots = new ArrayList<>();
        for (Slot s : menu.slots) {
            if (s.container != mc.player.getInventory() && s.mayPlace(ItemStack.EMPTY)) {
                targetSlots.add(s);
            }
        }
        if (targetSlots.isEmpty()) {
            for (Slot s : menu.slots) {
                if (s.container == mc.player.getInventory() && s.getContainerSlot() >= 9 && s.getContainerSlot() < 36) {
                    targetSlots.add(s);
                }
            }
        }
        if (targetSlots.isEmpty()) return;

        // 1) Merge partial stacks of identical items
        for (int i = 0; i < targetSlots.size(); i++) {
            Slot a = targetSlots.get(i);
            ItemStack sa = a.getItem();
            if (sa.isEmpty() || sa.getCount() >= sa.getMaxStackSize()) continue;
            for (int j = i + 1; j < targetSlots.size(); j++) {
                Slot b = targetSlots.get(j);
                ItemStack sb = b.getItem();
                if (!sb.isEmpty() && ItemStack.isSameItemSameComponents(sa, sb)) {
                    mc.gameMode.handleInventoryMouseClick(containerId, b.index, 0, ClickType.PICKUP, mc.player);
                    mc.gameMode.handleInventoryMouseClick(containerId, a.index, 0, ClickType.PICKUP, mc.player);
                    if (!menu.getCarried().isEmpty()) {
                        mc.gameMode.handleInventoryMouseClick(containerId, b.index, 0, ClickType.PICKUP, mc.player);
                    }
                    if (a.getItem().getCount() >= a.getItem().getMaxStackSize()) break;
                }
            }
        }

        // 2) Selection-sort by registry ID + count descending
        for (int i = 0; i < targetSlots.size(); i++) {
            int bestIdx = i;
            for (int j = i + 1; j < targetSlots.size(); j++) {
                if (compareStacks(targetSlots.get(j).getItem(), targetSlots.get(bestIdx).getItem()) < 0) {
                    bestIdx = j;
                }
            }
            if (bestIdx != i) {
                Slot si = targetSlots.get(i);
                Slot sb = targetSlots.get(bestIdx);
                if (!si.getItem().isEmpty() || !sb.getItem().isEmpty()) {
                    mc.gameMode.handleInventoryMouseClick(containerId, sb.index, 0, ClickType.PICKUP, mc.player);
                    mc.gameMode.handleInventoryMouseClick(containerId, si.index, 0, ClickType.PICKUP, mc.player);
                    if (!menu.getCarried().isEmpty()) {
                        mc.gameMode.handleInventoryMouseClick(containerId, sb.index, 0, ClickType.PICKUP, mc.player);
                    }
                }
            }
        }
        YeQuickMenu.showToast(ConfigManager.tr("已完成一键整理", "Sorted Inventory"), ConfigManager.getAccentColor());
    }

    private static int compareStacks(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return 0;
        if (a.isEmpty()) return 1;
        if (b.isEmpty()) return -1;
        String ka = BuiltInRegistries.ITEM.getKey(a.getItem()).toString();
        String kb = BuiltInRegistries.ITEM.getKey(b.getItem()).toString();
        int c = ka.compareTo(kb);
        return c != 0 ? c : Integer.compare(b.getCount(), a.getCount());
    }
}
