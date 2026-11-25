package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.BoolSetting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Minimalist HUD with individually toggleable & draggable elements.
 */
public class HudModule extends Module {
    public final BoolSetting showCoords = addSetting(new BoolSetting(
            "showCoords", "显示坐标", "在状态栏显示当前 XYZ 坐标", true
    ));

    public final BoolSetting showModuleList = addSetting(new BoolSetting(
            "showModuleList", "显示模块列表", "在屏幕上列出当前开启的模块", true
    ));

    public HudModule() {
        super("hud", "极简 HUD", "HUD", "极简无框状态栏与模块列表，支持单独开关和拖拽位置", Category.VISUAL);
        setEnabled(true, true);
    }

    public void render(GuiGraphics g) {
        if (!isEnabled() || mc.options.hideGui) return;
        if (mc.player == null) return;

        ConfigManager.HudSlot infoSlot = ConfigManager.HUD_SLOTS.get("hud_info");
        if (infoSlot != null && infoSlot.enabled) {
            String info = mc.getFps() + " FPS";
            if (showCoords.get()) {
                info += String.format(" · %d, %d, %d",
                        mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
            }
            g.drawString(mc.font, "Ye ", infoSlot.x, infoSlot.y, 0xFF38BDF8, true);
            g.drawString(mc.font, info, infoSlot.x + mc.font.width("Ye "), infoSlot.y, 0xFFE2E8F0, true);
        }

        ConfigManager.HudSlot listSlot = ConfigManager.HUD_SLOTS.get("hud_modules");
        if (showModuleList.get() && listSlot != null && listSlot.enabled) {
            List<Module> active = ModuleManager.getAll().stream()
                    .filter(m -> m.isEnabled() && m != this)
                    .sorted((a, b) -> Integer.compare(
                            mc.font.width(ConfigManager.english ? b.getSubtitle() : b.getName()),
                            mc.font.width(ConfigManager.english ? a.getSubtitle() : a.getName())))
                    .toList();

            int ry = listSlot.y;
            for (Module m : active) {
                String name = ConfigManager.english ? m.getSubtitle() : m.getName();
                g.drawString(mc.font, name, listSlot.x, ry, 0xFFE2E8F0, true);
                ry += 11;
            }
        }
    }
}
