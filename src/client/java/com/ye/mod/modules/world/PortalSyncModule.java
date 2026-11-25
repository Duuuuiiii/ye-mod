package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;

/**
 * Displays live Overworld <-> Nether (x8 / ÷8) portal coordinates on HUD
 * plus distance & compass direction to a pinned target point.
 */
public class PortalSyncModule extends Module {
    public final BoolSetting showTargetDistance = addSetting(new BoolSetting(
            "showTargetDistance", "显示标记点导航", "记录坐标后在屏幕下方显示距离与方向箭头", true
    ));

    private boolean hasPin = false;
    private int pinX = 0;
    private int pinZ = 0;
    private String pinDim = "overworld";

    public PortalSyncModule() {
        super("portal_sync", "下界坐标换算", "Portal Sync", "实时显示主世界/下界对应传送门坐标，支持标记点导航", Category.WORLD);
        addSetting(new ActionSetting("pinHere", "标记当前位置为导航点", "记录当前坐标并实时指示距离与方向", this::pinCurrentPos));
        addSetting(new ActionSetting("clearPin", "清除导航点", "清除已标记的目标点", () -> hasPin = false));
    }

    private void pinCurrentPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        pinX = mc.player.getBlockX();
        pinZ = mc.player.getBlockZ();
        pinDim = mc.level.dimension() == Level.NETHER ? "nether" : "overworld";
        hasPin = true;
    }

    public void renderHud(GuiGraphics g) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        int x = mc.player.getBlockX();
        int y = mc.player.getBlockY();
        int z = mc.player.getBlockZ();

        String line;
        if (mc.level.dimension() == Level.NETHER) {
            line = String.format("主世界对应: %d, %d, %d", x * 8, y, z * 8);
        } else if (mc.level.dimension() == Level.OVERWORLD) {
            line = String.format("下界对应: %d, %d, %d", Math.floorDiv(x, 8), y, Math.floorDiv(z, 8));
        } else {
            line = String.format("末地坐标: %d, %d, %d", x, y, z);
        }

        if (hasPin && showTargetDistance.get()) {
            double tx = pinX;
            double tz = pinZ;
            String curDim = mc.level.dimension() == Level.NETHER ? "nether" : "overworld";
            if (!curDim.equals(pinDim)) {
                if (curDim.equals("nether")) {
                    tx /= 8.0;
                    tz /= 8.0;
                } else {
                    tx *= 8.0;
                    tz *= 8.0;
                }
            }
            double dx = tx - mc.player.getX();
            double dz = tz - mc.player.getZ();
            int dist = (int) Math.round(Math.hypot(dx, dz));
            String arrow = getRelativeArrow(mc.player.getYRot(), dx, dz);
            line += String.format("  |  目标: %s %dm", arrow, dist);
        }

        com.ye.mod.core.ConfigManager.HudSlot slot = com.ye.mod.core.ConfigManager.HUD_SLOTS.get("hud_portal");
        if (slot != null && !slot.enabled) return;
        int hx = slot != null ? slot.x : 8;
        int hy = slot != null ? slot.y : 22;
        g.drawString(mc.font, line, hx, hy, 0xFFFBBF24, true);
    }

    private static String getRelativeArrow(float yawDeg, double dx, double dz) {
        double targetAngle = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((targetAngle - yawDeg) % 360.0 + 540.0) % 360.0 - 180.0;
        if (rel >= -22.5 && rel < 22.5) return "↑";
        if (rel >= 22.5 && rel < 67.5) return "↗";
        if (rel >= 67.5 && rel < 112.5) return "→";
        if (rel >= 112.5 && rel < 157.5) return "↘";
        if (rel >= -67.5 && rel < -22.5) return "↖";
        if (rel >= -112.5 && rel < -67.5) return "←";
        if (rel >= -157.5 && rel < -112.5) return "↙";
        return "↓";
    }
}
