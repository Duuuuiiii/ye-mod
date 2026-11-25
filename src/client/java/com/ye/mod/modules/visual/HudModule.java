package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.BoolSetting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Minimalist HUD pill showing Ye watermark, FPS, coordinates, and active modules.
 */
public class HudModule extends Module {
    public final BoolSetting showCoords = addSetting(new BoolSetting(
            "showCoords", "显示坐标", "在左上角信息栏中显示当前 XYZ 坐标", true
    ));

    public final BoolSetting showModuleList = addSetting(new BoolSetting(
            "showModuleList", "显示已启用模块列表", "在屏幕右上角以极简标签列出当前开启的模块", true
    ));

    public HudModule() {
        super("hud", "极简 HUD", "Minimal HUD", "在屏幕角落显示现代极简状态胶囊与已启用功能列表", Category.VISUAL);
        setEnabled(true, true);
    }

    public void render(GuiGraphics g) {
        if (!isEnabled() || mc.options.hideGui) return;
        if (mc.player == null) return;

        // Ultra-clean top-left status line (no heavy box/outline)
        String info = mc.getFps() + " FPS";
        if (showCoords.get()) {
            info += String.format(" · %d, %d, %d",
                    mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        }
        int x = 8;
        int y = 8;
        g.drawString(mc.font, "Ye ", x, y, 0xFF38BDF8, true);
        g.drawString(mc.font, info, x + mc.font.width("Ye "), y, 0xFFE2E8F0, true);

        // Clean top-right active modules list (text-only with subtle shadow)
        if (showModuleList.get()) {
            int screenW = mc.getWindow().getGuiScaledWidth();
            List<Module> active = ModuleManager.getAll().stream()
                    .filter(m -> m.isEnabled() && m != this)
                    .sorted((a, b) -> Integer.compare(mc.font.width(b.getName()), mc.font.width(a.getName())))
                    .toList();

            int ry = 8;
            for (Module m : active) {
                String name = m.getName();
                int rx = screenW - mc.font.width(name) - 8;
                g.drawString(mc.font, name, rx, ry, 0xFFE2E8F0, true);
                ry += 11;
            }
        }
    }
}
