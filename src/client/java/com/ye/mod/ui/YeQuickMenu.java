package com.ye.mod.ui;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.core.setting.Setting;
import com.ye.mod.modules.render.CapeManager;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.render.CustomSkinModule;
import com.ye.mod.modules.render.SkinManager;
import com.ye.mod.modules.system.PlayerStatsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-blocking bottom-right floating QuickMenu (toggled with F9).
 * Supports Arrow/Numpad + Enter navigation without stealing gameplay focus,
 * and F10 mouse unlock via YeMouseScreen for dragging, sliders, and dropdowns.
 */
public final class YeQuickMenu {
    private enum TopTab {
        MODULES("模块", "Mods"),
        STATS("统计", "Stats"),
        THEME("外观", "Theme");

        final String zh;
        final String en;
        TopTab(String zh, String en) { this.zh = zh; this.en = en; }
        String label() { return ConfigManager.tr(zh, en); }
    }

    public static final int WIN_W = 228;
    public static final int WIN_H = 264;
    private static final int EDGE_MARGIN = 18;

    private static boolean open = false;
    private static float openAnim = 0.0f;

    private static TopTab activeTab = TopTab.MODULES;
    private static Category activeCategory = Category.RENDER;
    private static Module activeModule = null;
    private static int selectedIndex = 0;
    private static double scrollOffset = 0;

    private static EnumSetting<?> openDropdown = null;
    private static int dropX, dropY, dropW;

    private static final List<HitRect> HIT_RECTS = new ArrayList<>();
    public record HitRect(int x, int y, int w, int h, Runnable leftClick, Runnable rightClick, DoubleSetting slider) {}

    private YeQuickMenu() {}

    public static boolean isOpen() { return open; }

    public static void toggleOpen() {
        open = !open;
        playClick();
        if (!open) {
            openDropdown = null;
            ConfigManager.saveAsync();
        }
    }

    public static void setOpen(boolean v) {
        open = v;
        if (!open) openDropdown = null;
    }

    public static void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    public static int getWinX(int sw) {
        if (ConfigManager.widgetX >= 0) {
            return Math.max(8, Math.min(sw - WIN_W - 8, ConfigManager.widgetX));
        }
        return sw - WIN_W - EDGE_MARGIN;
    }

    public static int getWinY(int sh) {
        if (ConfigManager.widgetY >= 0) {
            return Math.max(8, Math.min(sh - WIN_H - 8, ConfigManager.widgetY));
        }
        return sh - WIN_H - EDGE_MARGIN;
    }

    public static void render(GuiGraphics g, int mouseX, int mouseY, boolean mouseUnlocked) {
        AppleUiRenderer.updateThemeAnim();
        openAnim = AppleUiRenderer.smooth(openAnim, open ? 1.0f : 0.0f, 16.0f, 0.03f);
        if (!open && openAnim < 0.02f) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int wx = getWinX(sw);
        int wy = getWinY(sh) + Math.round((1.0f - openAnim) * 24.0f);
        int accent = ConfigManager.getAccentColor();

        HIT_RECTS.clear();

        // Rounded window body
        AppleUiRenderer.drawRoundedRect(g, wx, wy, WIN_W, WIN_H, 11, AppleUiRenderer.bgWindow());
        AppleUiRenderer.drawRoundedRect(g, wx, wy, WIN_W, 24, 11, AppleUiRenderer.bgSidebar());

        // Header: Brand + Tabs + F10 hint
        g.fill(wx + 8, wy + 7, wx + 11, wy + 17, accent);
        AppleUiRenderer.drawText(g, font, "Ye", wx + 15, wy + 8, AppleUiRenderer.textPrimary());

        int tx = wx + 36;
        for (TopTab tab : TopTab.values()) {
            int tw = 34;
            boolean sel = activeTab == tab;
            int tbg = sel ? accent : AppleUiRenderer.bgCard();
            AppleUiRenderer.drawRoundedRect(g, tx, wy + 4, tw, 16, 5, tbg);
            AppleUiRenderer.drawCenteredText(g, font, tab.label(), tx + tw / 2, wy + 8,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
            HIT_RECTS.add(new HitRect(tx, wy + 4, tw, 16, () -> {
                playClick();
                activeTab = tab;
                activeModule = null;
                openDropdown = null;
                selectedIndex = 0;
                scrollOffset = 0;
            }, null, null));
            tx += tw + 4;
        }

        String hint = mouseUnlocked ? "ESC" : "F10鼠标";
        AppleUiRenderer.drawText(g, font, hint, wx + WIN_W - 8 - font.width(hint), wy + 8, AppleUiRenderer.textTertiary());

        int bodyX = wx + 8;
        int bodyY = wy + 28;
        int bodyW = WIN_W - 16;
        int bodyH = WIN_H - 36;

        if (activeTab == TopTab.MODULES) {
            if (activeModule == null) {
                renderModulesTab(g, font, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
            } else {
                renderModuleDetail(g, font, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
            }
        } else if (activeTab == TopTab.STATS) {
            renderStatsTab(g, font, bodyX, bodyY, bodyW, bodyH, accent);
        } else {
            renderThemeTab(g, font, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, accent);
        }

        if (openDropdown != null && mouseUnlocked) {
            renderDropdown(g, font, openDropdown, mouseX, mouseY, accent);
        }
    }

    private static void renderModulesTab(GuiGraphics g, Font font, int x, int y, int w, int h,
                                         int mx, int my, boolean mouseUnlocked, int accent) {
        // Category pills
        Category[] cats = Category.values();
        int cw = (w - (cats.length - 1) * 3) / cats.length;
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            int cx = x + i * (cw + 3);
            boolean sel = activeCategory == c;
            AppleUiRenderer.drawRoundedRect(g, cx, y, cw, 16, 5, sel ? accent : AppleUiRenderer.bgCard());
            String label = ConfigManager.english ? c.name().substring(0, Math.min(3, c.name().length())) : c.getDisplayName();
            AppleUiRenderer.drawCenteredText(g, font, label, cx + cw / 2, y + 4,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
            HIT_RECTS.add(new HitRect(cx, y, cw, 16, () -> {
                playClick();
                activeCategory = c;
                selectedIndex = 0;
                scrollOffset = 0;
            }, null, null));
        }

        int listY = y + 20;
        int listH = h - 20;
        List<Module> mods = ModuleManager.getByCategory(activeCategory);
        if (selectedIndex >= mods.size()) selectedIndex = Math.max(0, mods.size() - 1);

        g.enableScissor(x, listY, x + w, listY + listH);
        int ry = listY - (int) Math.round(scrollOffset);
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            boolean hovered = mouseUnlocked && inBounds(mx, my, x, ry, w, 18);
            boolean keySel = !mouseUnlocked && i == selectedIndex;
            int bg = m.isEnabled() ? accent : ((hovered || keySel) ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());

            if (ry + 18 >= listY && ry <= listY + listH) {
                AppleUiRenderer.drawRoundedRect(g, x, ry, w, 18, 5, bg);
                if (keySel) {
                    g.renderOutline(x, ry, w, 18, 0xAAFFFFFF);
                }
                String name = ConfigManager.english ? m.getSubtitle() : m.getName();
                AppleUiRenderer.drawText(g, font, name, x + 7, ry + 5,
                        m.isEnabled() ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());

                if (!m.getSettings().isEmpty()) {
                    AppleUiRenderer.drawText(g, font, "›", x + w - 10, ry + 5,
                            m.isEnabled() ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textTertiary());
                }

                if (ry >= listY && ry + 18 <= listY + listH) {
                    HIT_RECTS.add(new HitRect(x, ry, w, 18,
                            () -> {
                                playClick();
                                m.toggle();
                                ConfigManager.saveAsync();
                            },
                            () -> {
                                playClick();
                                activeModule = m;
                                selectedIndex = 0;
                                scrollOffset = 0;
                                openDropdown = null;
                            }, null));
                }
            }
            ry += 20;
        }
        g.disableScissor();
    }

    private static void renderModuleDetail(GuiGraphics g, Font font, int x, int y, int w, int h,
                                           int mx, int my, boolean mouseUnlocked, int accent) {
        // Back + Module title + Toggle
        AppleUiRenderer.drawRoundedRect(g, x, y, 42, 16, 5, AppleUiRenderer.bgCard());
        AppleUiRenderer.drawCenteredText(g, font, "←", x + 21, y + 4, AppleUiRenderer.textPrimary());
        HIT_RECTS.add(new HitRect(x, y, 42, 16, () -> {
            playClick();
            activeModule = null;
            openDropdown = null;
            selectedIndex = 0;
            scrollOffset = 0;
        }, null, null));

        String mName = ConfigManager.english ? activeModule.getSubtitle() : activeModule.getName();
        AppleUiRenderer.drawText(g, font, mName, x + 48, y + 4, AppleUiRenderer.textPrimary());
        AppleUiRenderer.drawPillSwitch(g, x + w - 28, y + 1, 28, 14, activeModule.isEnabled() ? 1f : 0f);
        HIT_RECTS.add(new HitRect(x + w - 28, y + 1, 28, 14, () -> {
            playClick();
            activeModule.toggle();
            ConfigManager.saveAsync();
        }, null, null));

        int listY = y + 20;
        int listH = h - 20;
        List<Setting<?>> settings = activeModule.getSettings();
        if (selectedIndex >= settings.size()) selectedIndex = Math.max(0, settings.size() - 1);

        g.enableScissor(x, listY, x + w, listY + listH);
        int ry = listY - (int) Math.round(scrollOffset);

        // Quick Cape/Skin gallery rows
        if (activeModule instanceof CustomCapeModule cm) {
            for (CapeManager.CapeEntry ce : CapeManager.getAllCapes()) {
                if (ry + 18 >= listY && ry <= listY + listH) {
                    boolean sel = ce.id().equals(cm.selectedCape.get());
                    AppleUiRenderer.drawRoundedRect(g, x, ry, w, 17, 5, sel ? accent : AppleUiRenderer.bgCard());
                    AppleUiRenderer.drawText(g, font, ce.displayName(), x + 6, ry + 5, sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
                    if (ry >= listY && ry + 17 <= listY + listH) {
                        HIT_RECTS.add(new HitRect(x, ry, w, 17, () -> {
                            playClick();
                            cm.selectedCape.set(ce.id());
                            cm.setEnabled(true);
                            ConfigManager.saveAsync();
                        }, null, null));
                    }
                }
                ry += 19;
            }
        } else if (activeModule instanceof CustomSkinModule sm) {
            for (SkinManager.SkinEntry se : SkinManager.getAllSkins()) {
                if (ry + 18 >= listY && ry <= listY + listH) {
                    boolean sel = se.id().equals(sm.selectedSkin.get());
                    AppleUiRenderer.drawRoundedRect(g, x, ry, w, 17, 5, sel ? accent : AppleUiRenderer.bgCard());
                    AppleUiRenderer.drawText(g, font, se.displayName(), x + 6, ry + 5, sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
                    if (ry >= listY && ry + 17 <= listY + listH) {
                        HIT_RECTS.add(new HitRect(x, ry, w, 17, () -> {
                            playClick();
                            sm.selectedSkin.set(se.id());
                            sm.setEnabled(true);
                            ConfigManager.saveAsync();
                        }, null, null));
                    }
                }
                ry += 19;
            }
        }

        for (int i = 0; i < settings.size(); i++) {
            Setting<?> s = settings.get(i);
            if ((activeModule instanceof CustomCapeModule && s.getId().equals("selectedCape"))
                    || (activeModule instanceof CustomSkinModule && s.getId().equals("selectedSkin"))) {
                continue;
            }
            int rowH = (s instanceof DoubleSetting) ? 28 : 20;
            if (ry + rowH >= listY && ry <= listY + listH) {
                boolean keySel = !mouseUnlocked && i == selectedIndex;
                AppleUiRenderer.drawRoundedRect(g, x, ry, w, rowH, 5, AppleUiRenderer.bgCard());
                if (keySel) g.renderOutline(x, ry, w, rowH, accent);

                AppleUiRenderer.drawText(g, font, s.getName(), x + 6, ry + 6, AppleUiRenderer.textPrimary());
                boolean inClip = ry >= listY - 2 && ry + rowH <= listY + listH + 2;

                if (s instanceof BoolSetting bs) {
                    AppleUiRenderer.drawPillSwitch(g, x + w - 30, ry + 3, 26, 14, bs.get() ? 1f : 0f);
                    if (inClip) {
                        HIT_RECTS.add(new HitRect(x, ry, w, rowH, () -> {
                            playClick();
                            openDropdown = null;
                            bs.toggle();
                            ConfigManager.saveAsync();
                        }, null, null));
                    }
                } else if (s instanceof DoubleSetting ds) {
                    String vt = formatDouble(ds.get());
                    AppleUiRenderer.drawText(g, font, vt, x + w - 6 - font.width(vt), ry + 5, accent);
                    int bw = w - 12;
                    int bx = x + 6;
                    int by = ry + 19;
                    double pct = (ds.get() - ds.getMin()) / Math.max(0.0001, ds.getMax() - ds.getMin());
                    g.fill(bx, by, bx + bw, by + 3, 0xFF334155);
                    g.fill(bx, by, bx + Math.max(3, (int) Math.round(bw * pct)), by + 3, accent);
                    if (inClip) {
                        HIT_RECTS.add(new HitRect(bx - 2, by - 5, bw + 4, 14, null, null, ds));
                    }
                } else if (s instanceof EnumSetting<?> es) {
                    String val = es.get().name() + " ▾";
                    int bw = Math.min(w / 2 + 16, Math.max(76, font.width(val) + 12));
                    int bx = x + w - 4 - bw;
                    int by = ry + 2;
                    boolean isOpen = (openDropdown == es);
                    AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 16, 4, isOpen ? accent : AppleUiRenderer.bgCardHover());
                    AppleUiRenderer.drawCenteredText(g, font, val, bx + bw / 2, by + 4,
                            isOpen ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
                    if (inClip) {
                        HIT_RECTS.add(new HitRect(bx, by, bw, 16, () -> {
                            playClick();
                            if (openDropdown == es) openDropdown = null;
                            else {
                                openDropdown = es;
                                dropX = bx;
                                dropY = by + 18;
                                dropW = bw;
                            }
                        }, null, null));
                    }
                } else if (s instanceof ActionSetting as) {
                    int bx = x + w - 46;
                    AppleUiRenderer.drawRoundedRect(g, bx, ry + 2, 42, 16, 4, accent);
                    AppleUiRenderer.drawCenteredText(g, font, "执行", bx + 21, ry + 6, AppleUiRenderer.TEXT_PRIMARY);
                    if (inClip) {
                        HIT_RECTS.add(new HitRect(bx, ry + 2, 42, 16, () -> {
                            playClick();
                            as.trigger();
                        }, null, null));
                    }
                }
            }
            ry += rowH + 2;
        }
        g.disableScissor();
    }

    private static <E extends Enum<E>> void renderDropdown(GuiGraphics g, Font font, EnumSetting<E> es, int mx, int my, int accent) {
        E[] vals = es.getDefaultValue().getDeclaringClass().getEnumConstants();
        int itemH = 16;
        int th = vals.length * itemH + 4;
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int dy = Math.min(dropY, sh - th - 6);
        AppleUiRenderer.drawRoundedRect(g, dropX, dy, dropW, th, 6, AppleUiRenderer.bgWindow());
        g.renderOutline(dropX, dy, dropW, th, accent);

        for (int i = 0; i < vals.length; i++) {
            E opt = vals[i];
            int iy = dy + 2 + i * itemH;
            boolean sel = opt == es.get();
            boolean hov = inBounds(mx, my, dropX + 2, iy, dropW - 4, itemH);
            int bg = sel ? accent : (hov ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
            g.fill(dropX + 2, iy, dropX + dropW - 2, iy + itemH - 1, bg);
            AppleUiRenderer.drawText(g, font, opt.name(), dropX + 5, iy + 4,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
            HIT_RECTS.add(new HitRect(dropX + 2, iy, dropW - 4, itemH, () -> {
                playClick();
                es.set(opt);
                openDropdown = null;
                ConfigManager.saveAsync();
            }, null, null));
        }
    }

    private static void renderStatsTab(GuiGraphics g, Font font, int x, int y, int w, int h, int accent) {
        List<PlayerStatsManager.PlayerRecord> recs = new ArrayList<>(PlayerStatsManager.getAllRecords());
        recs.sort((a, b) -> Integer.compare(b.messages, a.messages));
        g.enableScissor(x, y, x + w, y + h);
        int ry = y - (int) Math.round(scrollOffset);
        if (recs.isEmpty()) {
            AppleUiRenderer.drawCenteredText(g, font, ConfigManager.tr("暂无统计记录", "No stats yet"), x + w / 2, y + 30, AppleUiRenderer.textTertiary());
        }
        for (PlayerStatsManager.PlayerRecord r : recs) {
            if (ry + 18 >= y && ry <= y + h) {
                AppleUiRenderer.drawRoundedRect(g, x, ry, w, 18, 5, AppleUiRenderer.bgCard());
                AppleUiRenderer.drawText(g, font, r.name, x + 6, ry + 5, AppleUiRenderer.textPrimary());
                String st = r.messages + " / " + r.kills + " / " + r.deaths;
                AppleUiRenderer.drawText(g, font, st, x + w - 48 - font.width(st), ry + 5, AppleUiRenderer.textSecondary());
                boolean muted = PlayerStatsManager.isMuted(r.name);
                int bx = x + w - 42;
                AppleUiRenderer.drawRoundedRect(g, bx, ry + 2, 38, 14, 4, muted ? AppleUiRenderer.APPLE_RED : AppleUiRenderer.bgCardHover());
                AppleUiRenderer.drawCenteredText(g, font, muted ? "已屏蔽" : "屏蔽", bx + 19, ry + 5,
                        muted ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
                if (ry >= y && ry + 18 <= y + h) {
                    HIT_RECTS.add(new HitRect(bx, ry + 2, 38, 14, () -> {
                        playClick();
                        PlayerStatsManager.toggleMute(r.name);
                    }, null, null));
                }
            }
            ry += 20;
        }
        g.disableScissor();
    }

    private static void renderThemeTab(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my, int accent) {
        int ry = y;
        // Dark/Light
        AppleUiRenderer.drawRoundedRect(g, x, ry, w, 20, 5, AppleUiRenderer.bgCard());
        AppleUiRenderer.drawText(g, font, ConfigManager.tr("深浅色", "Theme"), x + 6, ry + 6, AppleUiRenderer.textPrimary());
        String tVal = ConfigManager.lightTheme ? ConfigManager.tr("浅色", "Light") : ConfigManager.tr("深色", "Dark");
        AppleUiRenderer.drawRoundedRect(g, x + w - 48, ry + 3, 44, 14, 4, accent);
        AppleUiRenderer.drawCenteredText(g, font, tVal, x + w - 26, ry + 6, AppleUiRenderer.TEXT_PRIMARY);
        HIT_RECTS.add(new HitRect(x, ry, w, 20, () -> {
            playClick();
            AppleUiRenderer.toggleTheme();
        }, null, null));
        ry += 24;

        // Language
        AppleUiRenderer.drawRoundedRect(g, x, ry, w, 20, 5, AppleUiRenderer.bgCard());
        AppleUiRenderer.drawText(g, font, ConfigManager.tr("语言", "Language"), x + 6, ry + 6, AppleUiRenderer.textPrimary());
        String lVal = ConfigManager.english ? "EN" : "中文";
        AppleUiRenderer.drawRoundedRect(g, x + w - 48, ry + 3, 44, 14, 4, accent);
        AppleUiRenderer.drawCenteredText(g, font, lVal, x + w - 26, ry + 6, AppleUiRenderer.TEXT_PRIMARY);
        HIT_RECTS.add(new HitRect(x, ry, w, 20, () -> {
            playClick();
            ConfigManager.english = !ConfigManager.english;
            ConfigManager.saveAsync();
        }, null, null));
        ry += 24;

        // Accent color swatches
        AppleUiRenderer.drawText(g, font, ConfigManager.tr("UI 主题色", "Accent Color"), x + 2, ry, AppleUiRenderer.textSecondary());
        ry += 14;
        ConfigManager.ThemeAccent[] accents = ConfigManager.ThemeAccent.values();
        int cw = (w - 10) / 3;
        for (int i = 0; i < accents.length; i++) {
            ConfigManager.ThemeAccent ta = accents[i];
            int cx = x + (i % 3) * (cw + 5);
            int cy = ry + (i / 3) * 22;
            boolean sel = ConfigManager.themeAccent == ta;
            AppleUiRenderer.drawRoundedRect(g, cx, cy, cw, 18, 5, sel ? ta.argb : AppleUiRenderer.bgCard());
            if (!sel) g.fill(cx + 4, cy + 4, cx + 10, cy + 14, ta.argb);
            String label = ConfigManager.english ? ta.en : ta.zh;
            AppleUiRenderer.drawCenteredText(g, font, label, cx + cw / 2 + (sel ? 0 : 4), cy + 5,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
            HIT_RECTS.add(new HitRect(cx, cy, cw, 18, () -> {
                playClick();
                ConfigManager.themeAccent = ta;
                ConfigManager.saveAsync();
            }, null, null));
        }
        ry += 50;

        // Reset position
        AppleUiRenderer.drawRoundedRect(g, x, ry, w, 20, 5, AppleUiRenderer.bgCard());
        AppleUiRenderer.drawCenteredText(g, font, ConfigManager.tr("重置窗口到右下角", "Reset Position"), x + w / 2, ry + 6, AppleUiRenderer.textPrimary());
        HIT_RECTS.add(new HitRect(x, ry, w, 20, () -> {
            playClick();
            ConfigManager.widgetX = -1;
            ConfigManager.widgetY = -1;
            ConfigManager.saveAsync();
        }, null, null));
    }

    public static void onKeyNav(int action) {
        // action: 0=UP, 1=DOWN, 2=LEFT, 3=RIGHT, 4=ENTER, 5=BACK
        if (!open) return;
        playClick();
        if (activeTab == TopTab.MODULES) {
            if (activeModule == null) {
                List<Module> mods = ModuleManager.getByCategory(activeCategory);
                if (action == 0 && selectedIndex > 0) selectedIndex--;
                else if (action == 1 && selectedIndex < mods.size() - 1) selectedIndex++;
                else if (action == 2) {
                    Category[] cs = Category.values();
                    activeCategory = cs[(activeCategory.ordinal() + cs.length - 1) % cs.length];
                    selectedIndex = 0;
                    scrollOffset = 0;
                } else if (action == 3) {
                    if (!mods.isEmpty() && !mods.get(selectedIndex).getSettings().isEmpty()) {
                        activeModule = mods.get(selectedIndex);
                        selectedIndex = 0;
                        scrollOffset = 0;
                    }
                } else if (action == 4 && !mods.isEmpty()) {
                    mods.get(selectedIndex).toggle();
                    ConfigManager.saveAsync();
                }
            } else {
                List<Setting<?>> sets = activeModule.getSettings();
                if (action == 5) {
                    activeModule = null;
                    selectedIndex = 0;
                    scrollOffset = 0;
                } else if (action == 0 && selectedIndex > 0) selectedIndex--;
                else if (action == 1 && selectedIndex < sets.size() - 1) selectedIndex++;
                else if (!sets.isEmpty()) {
                    Setting<?> s = sets.get(selectedIndex);
                    if (s instanceof BoolSetting bs && (action == 4 || action == 2 || action == 3)) {
                        bs.toggle();
                        ConfigManager.saveAsync();
                    } else if (s instanceof DoubleSetting ds && (action == 2 || action == 3)) {
                        ds.set(ds.get() + (action == 3 ? ds.getStep() : -ds.getStep()));
                        ConfigManager.saveAsync();
                    } else if (s instanceof EnumSetting<?> es && (action == 2 || action == 3 || action == 4)) {
                        es.cycle(action == 2 ? -1 : 1);
                        ConfigManager.saveAsync();
                    } else if (s instanceof ActionSetting as && action == 4) {
                        as.trigger();
                    }
                }
            }
        }
    }

    public static List<HitRect> getHitRects() { return HIT_RECTS; }
    public static void addScroll(double delta) { scrollOffset = Math.max(0, scrollOffset - delta * 18); }

    private static boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String formatDouble(double v) {
        if (Math.abs(v - Math.round(v)) < 0.001) return String.valueOf((long) Math.round(v));
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
