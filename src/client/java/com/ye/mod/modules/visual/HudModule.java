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

        // Top-left status pill
        StringBuilder sb = new StringBuilder("ye");
        sb.append("  ").append(mc.getFps()).append(" FPS");
        if (showCoords.get()) {
            sb.append(String.format("  %d, %d, %d",
                    mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()));
        }
        String text = sb.toString();
        int textW = mc.font.width(text);
        int x = 8;
        int y = 8;
        int padX = 8;
        int h = 18;

        g.fill(x, y, x + textW + padX * 2, y + h, 0xB811131A);
        g.fill(x, y, x + 2, y + h, 0xFF38BDF8);
        g.renderOutline(x, y, textW + padX * 2, h, 0x40FFFFFF);
        g.drawString(mc.font, "ye", x + padX, y + 5, 0xFF38BDF8, false);
        g.drawString(mc.font, text.substring(2), x + padX + mc.font.width("ye"), y + 5, 0xFFE2E8F0, false);

        // Top-right active modules list
        if (showModuleList.get()) {
            int screenW = mc.getWindow().getGuiScaledWidth();
            List<Module> active = ModuleManager.getAll().stream()
                    .filter(m -> m.isEnabled() && m != this)
                    .sorted((a, b) -> Integer.compare(mc.font.width(b.getName()), mc.font.width(a.getName())))
                    .toList();

            int ry = 8;
            for (Module m : active) {
                String name = m.getName();
                int w = mc.font.width(name) + 10;
                int rx = screenW - w - 8;
                g.fill(rx, ry, rx + w, ry + 14, 0x9E11131A);
                g.fill(rx + w - 2, ry, rx + w, ry + 14, m.getCategory().getAccentColor());
                g.drawString(mc.font, name, rx + 5, ry + 3, 0xFFF1F5F9, false);
                ry += 16;
            }
        }
    }
}
