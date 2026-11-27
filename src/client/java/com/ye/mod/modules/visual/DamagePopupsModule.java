package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.ui.YeHdFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Floating Damage Popups rendered with crisp 1:1 physical-pixel YeHdFont!
 */
public class DamagePopupsModule extends Module {
    public enum DisplayMode {
        ALL("全部显示"),
        NUMBERS_ONLY("仅数字"),
        CRIT_ONLY("仅暴击");

        public final String label;
        DisplayMode(String label) { this.label = label; }
    }

    private record Popup(String text, int color, long spawnMs, int offsetX, boolean isCrit) {}

    public final EnumSetting<DisplayMode> mode = addSetting(new EnumSetting<>(
            "mode", "显示预设", "全部显示 / 仅伤害数字 / 仅暴击提示", DisplayMode.ALL
    ));

    public final BoolSetting showCombo = addSetting(new BoolSetting(
            "showCombo", "连击计数", "单独开关准星下方连击提示", true
    ));

    private final List<Popup> popups = new CopyOnWriteArrayList<>();
    private int comboCount = 0;
    private long lastHitMs = 0L;
    private int lastTargetId = -1;
    private float lastTargetHp = -1f;
    private boolean lastWasCrit = false;

    public DamagePopupsModule() {
        super("damage_popups", "伤害飘字", "Damage Popups", "实时显示攻击伤害数值、暴击标记与连击计数", Category.UTILITY);
    }

    public void onAttack(Entity target) {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        comboCount = (now - lastHitMs <= 2600L) ? comboCount + 1 : 1;
        lastHitMs = now;
        if (target instanceof LivingEntity le) {
            lastTargetId = le.getId();
            lastTargetHp = le.getHealth();
        }
        Minecraft mc = Minecraft.getInstance();
        boolean crit = mc.player != null && mc.player.fallDistance > 0.0f && !mc.player.onGround();
        lastWasCrit = crit;

        DisplayMode dm = mode.get();
        if (dm == DisplayMode.ALL || (dm == DisplayMode.CRIT_ONLY && crit)) {
            String txt = crit ? ConfigManager.tr("✦ 暴击!", "✦ CRIT!") : (ConfigManager.tr("命中 ×", "Hit ×") + comboCount);
            int col = crit ? 0xFFFACC15 : 0xFF38BDF8;
            int ox = ((comboCount % 3) - 1) * 20;
            popups.add(new Popup(txt, col, now, ox, crit));
            while (popups.size() > 8) popups.remove(0);
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled() || lastTargetId < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(lastTargetId);
        if (e instanceof LivingEntity le) {
            float hp = le.getHealth();
            if (lastTargetHp > hp && (lastTargetHp - hp) > 0.05f) {
                float dmg = lastTargetHp - hp;
                DisplayMode dm = mode.get();
                if (dm == DisplayMode.ALL || dm == DisplayMode.NUMBERS_ONLY || (dm == DisplayMode.CRIT_ONLY && lastWasCrit)) {
                    String num = (dm == DisplayMode.NUMBERS_ONLY)
                            ? String.format(Locale.ROOT, "%.1f", dmg)
                            : (lastWasCrit ? String.format(Locale.ROOT, "✦ -%.1f", dmg) : String.format(Locale.ROOT, "-%.1f", dmg));
                    int col = lastWasCrit ? 0xFFFACC15 : 0xFFEF4444;
                    popups.add(new Popup(num, col, System.currentTimeMillis(), 16, lastWasCrit));
                    while (popups.size() > 8) popups.remove(0);
                }
                lastTargetHp = hp;
            }
        }
    }

    public void render(GuiGraphics g) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        IdleWallpaperModule idle = com.ye.mod.core.ModuleManager.get(IdleWallpaperModule.class);
        if (idle != null && idle.shouldHideHud()) return;

        long now = System.currentTimeMillis();
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;

        if (showCombo.get() && comboCount > 1 && now - lastHitMs <= 2600L) {
            String cStr = ConfigManager.english ? (comboCount + " COMBO") : (comboCount + " 连击");
            YeHdFont.drawCentered(g, cStr, cx, cy + 16, 9.2f, 0xFFFACC15, true, true);
        }

        Iterator<Popup> it = popups.iterator();
        while (it.hasNext()) {
            Popup p = it.next();
            long age = now - p.spawnMs;
            if (age > 950L) {
                popups.remove(p);
                continue;
            }
            float t = age / 950.0f;
            float y = cy - 16 - t * 28.0f;
            int alpha = Math.max(15, Math.round((1.0f - t) * 255.0f));
            int col = (alpha << 24) | (p.color & 0x00FFFFFF);
            YeHdFont.drawCentered(g, p.text, cx + p.offsetX, y, p.isCrit ? 10.0f : 9.0f, col, true, true);
        }
    }
}
