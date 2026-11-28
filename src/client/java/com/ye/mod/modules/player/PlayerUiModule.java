package com.ye.mod.modules.player;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.ui.AppleUiRenderer;
import com.ye.mod.ui.YeHdFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

import java.util.Locale;

/**
 * Player UI Overhaul Module (玩家界面):
 * 1) Auto-Hide Hotbar after X.X seconds of not switching slots or using items (slides smoothly down out of sight!)
 * 2) Sleek Bar-Style Health (with Absorption Gold Shield & Damage-Drain Trail), Hunger + Saturation, Armor & Air Bars!
 * 3) Side-Slide Drawer Survival Inventory (left or right dock — leaves center screen clear!)
 * 4) Giant Customizable Widescreen Creative Inventory (up to 40x20 grid with instant Chinese search!)
 */
public class PlayerUiModule extends Module {
    public enum DrawerSide {
        LEFT("左侧滑出"),
        RIGHT("右侧滑出");

        public final String label;
        DrawerSide(String label) { this.label = label; }
    }

    public final BoolSetting autoHideHotbar = addSetting(new BoolSetting(
            "autoHideHotbar", "快捷栏闲置自动隐藏", "长时间不切换格子或使用物品时快捷栏平滑下滑隐藏，切换立即浮现", false
    ).functionGroup());

    public final DoubleSetting hotbarIdleSec = addSetting(new DoubleSetting(
            "hotbarIdleSec", "快捷栏隐藏秒数", "无切换与物品交互多少秒后收起快捷栏", 4.5, 1.0, 20.0, 0.5
    ).visible(autoHideHotbar::get).functionGroup());

    public final BoolSetting barStatus = addSetting(new BoolSetting(
            "barStatus", "条状生命与饥饿栏", "将原版爱心、鸡腿、护甲与氧气替换为带动画缓冲的现代条状面板", true
    ).functionGroup());

    public final BoolSetting showBarNumbers = addSetting(new BoolSetting(
            "showBarNumbers", "显示精确数值", "在生命条与饥饿条上显示精确数值", true
    ).visible(barStatus::get).functionGroup());

    public final BoolSetting hideArmorBar = addSetting(new BoolSetting(
            "hideArmorBar", "隐藏原版盔甲值", "隐藏原版护甲图标栏", false
    ).functionGroup());

    public final BoolSetting hideRecipeBook = addSetting(new BoolSetting(
            "hideRecipeBook", "隐藏背包配方书", "打开生存背包时自动隐藏绿色配方书按钮与面板", true
    ).functionGroup());

    public final BoolSetting showShulkerSidebar = addSetting(new BoolSetting(
            "showShulkerSidebar", "背包潜影盒清单", "打开背包或箱子时在右侧展示每个潜影盒内的物品与数量", true
    ).functionGroup());

    public final DoubleSetting invBgOpacity = addSetting(new DoubleSetting(
            "invBgOpacity", "物品栏背景透明度", "调节背包面板背景暗化与不透明度", 0.72, 0.0, 1.0, 0.05
    ));

    public final DoubleSetting invBgBlur = addSetting(new DoubleSetting(
            "invBgBlur", "物品栏背景模糊度", "调节打开背包时的背景模糊强度", 0.45, 0.0, 1.0, 0.05
    ));

    public final BoolSetting sideSurvivalInv = addSetting(new BoolSetting(
            "sideSurvivalInv", "生存背包侧边弹出", "打开生存背包时从屏幕侧边滑出，不遮挡屏幕正中央视野", true
    ).functionGroup());

    public final EnumSetting<DrawerSide> survivalSide = addSetting(new EnumSetting<>(
            "survivalSide", "生存背包停靠侧", "左侧滑出 / 右侧滑出", DrawerSide.LEFT
    ).visible(sideSurvivalInv::get).functionGroup());

    public final BoolSetting giantCreativeInv = addSetting(new BoolSetting(
            "giantCreativeInv", "大尺寸创造物品栏", "替换狭小的原版创造菜单，展开自定义大网格全屏物品库", true
    ).functionGroup());

    public final DoubleSetting creativeCols = addSetting(new DoubleSetting(
            "creativeCols", "创造栏列数", "横向每排显示物品格数 (12 ~ 40列)", 24.0, 12.0, 40.0, 2.0
    ).visible(giantCreativeInv::get).functionGroup());

    public final DoubleSetting creativeRows = addSetting(new DoubleSetting(
            "creativeRows", "创造栏行数", "纵向显示物品排数 (8 ~ 20行)", 12.0, 8.0, 20.0, 1.0
    ).visible(giantCreativeInv::get).functionGroup());

    private int lastSelectedSlot = -1;
    private long lastHotbarActionMs = System.currentTimeMillis();
    private float hotbarHideProg = 0.0f; // 0 = fully visible, 1 = hidden downward

    private float smoothHpPct = 1.0f;
    private float trailHpPct = 1.0f;
    private float smoothFoodPct = 1.0f;
    private long lastBarNs = 0L;

    public static boolean bypassGiantCreativeOnce = false;

    public PlayerUiModule() {
        super("player_ui", "玩家界面", "Player UI", "快捷栏闲置隐藏、现代条状血量饥饿栏、侧边弹出式生存背包与超大网格创造模式物品栏", Category.UTILITY);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int curSlot = mc.player.getInventory().getSelectedSlot();
        boolean activeUse = curSlot != lastSelectedSlot
                || mc.player.swingTime > 0
                || mc.player.isUsingItem()
                || mc.options.keyAttack.isDown()
                || mc.options.keyUse.isDown();
        lastSelectedSlot = curSlot;
        if (activeUse || mc.screen != null) {
            lastHotbarActionMs = System.currentTimeMillis();
        }
    }

    /**
     * Computes vertical downward slide offset in GUI pixels for the vanilla hotbar (0 = visible, ~28 = hidden).
     */
    public float computeHotbarSlideOffset() {
        boolean shouldHide = isEnabled() && autoHideHotbar.get()
                && (System.currentTimeMillis() - lastHotbarActionMs >= Math.round(hotbarIdleSec.get() * 1000.0));
        float target = shouldHide ? 1.0f : 0.0f;
        if (ConfigManager.uiStyle == ConfigManager.UiStyle.MECHANICAL) {
            hotbarHideProg = target;
        } else {
            hotbarHideProg += (target - hotbarHideProg) * 0.16f;
        }
        return hotbarHideProg * 30.0f;
    }

    public boolean shouldReplaceStatusBars() {
        Minecraft mc = Minecraft.getInstance();
        return isEnabled() && barStatus.get() && mc.gameMode != null && mc.gameMode.canHurtPlayer();
    }

    public void renderCustomStatusBars(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) return;

        long now = System.nanoTime();
        float dt = lastBarNs == 0L ? 0.016f : Math.min(0.05f, Math.max(0.001f, (now - lastBarNs) / 1_000_000_000.0f));
        lastBarNs = now;

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        float slideY = computeHotbarSlideOffset() * 0.45f;
        boolean mech = (ConfigManager.uiStyle == ConfigManager.UiStyle.MECHANICAL);
        float rad = mech ? 0.0f : 3.2f;

        float hp = Math.max(0f, p.getHealth());
        float maxHp = Math.max(1f, p.getMaxHealth());
        float abs = Math.max(0f, p.getAbsorptionAmount());
        float rawHpPct = Math.min(1.0f, hp / maxHp);

        smoothHpPct += (rawHpPct - smoothHpPct) * (1.0f - (float) Math.exp(-18.0f * dt));
        if (rawHpPct > trailHpPct) trailHpPct = smoothHpPct;
        else trailHpPct += (rawHpPct - trailHpPct) * (1.0f - (float) Math.exp(-4.5f * dt));

        FoodData fd = p.getFoodData();
        int food = fd.getFoodLevel();
        float sat = fd.getSaturationLevel();
        float rawFoodPct = Math.max(0f, Math.min(1f, food / 20.0f));
        smoothFoodPct += (rawFoodPct - smoothFoodPct) * (1.0f - (float) Math.exp(-16.0f * dt));

        float barW = 82.0f;
        float barH = 8.0f;
        float slideDown = hotbarHideProg * 54.0f;
        if (hotbarHideProg >= 0.98f) return;
        float baseY = sh - 38.0f + slideDown;
        float leftX = sw * 0.5f - 91.0f;
        float rightX = sw * 0.5f + 91.0f - barW;

        // 1. Health Bar + Bright Outline + Damage Trail + Golden Absorption Overlay
        AppleUiRenderer.drawRoundedRect(g, leftX - 1.0f, baseY - 1.0f, barW + 2.0f, barH + 2.0f, rad + 0.8f, 0xCC090D16);
        AppleUiRenderer.drawRoundedOutline(g, leftX - 1.0f, baseY - 1.0f, barW + 2.0f, barH + 2.0f, rad + 0.8f, 1.0f, 0x78E2E8F0);
        if (trailHpPct > smoothHpPct + 0.005f) {
            AppleUiRenderer.drawRoundedRect(g, leftX, baseY, barW * trailHpPct, barH, rad, 0xCCFDE68A);
        }
        int hpCol = smoothHpPct > 0.50f ? 0xFFEF4444 : (smoothHpPct > 0.25f ? 0xFFF97316 : 0xFFDC2626);
        AppleUiRenderer.drawRoundedRect(g, leftX, baseY, barW * smoothHpPct, barH, rad, hpCol);
        if (abs > 0.05f) {
            float absPct = Math.min(1.0f, abs / maxHp);
            AppleUiRenderer.drawRoundedRect(g, leftX, baseY, barW * absPct, barH * 0.48f, rad * 0.6f, 0xFFFACC15);
        }
        if (showBarNumbers.get()) {
            String hpTxt = abs > 0.1f
                    ? String.format(Locale.ROOT, "%.0f+%.0f", hp, abs)
                    : String.format(Locale.ROOT, "%.0f/%.0f", hp, maxHp);
            YeHdFont.drawCentered(g, hpTxt, leftX + barW * 0.5f, baseY - 0.8f, 7.2f, 0xFFF8FAFC, true, true);
        }

        // 2. Armor Slim Bar (Above Health if armor > 0 and not hidden)
        int armor = p.getArmorValue();
        if (armor > 0 && !hideArmorBar.get()) {
            float armPct = Math.min(1.0f, armor / 20.0f);
            float ay = baseY - 7.0f;
            AppleUiRenderer.drawRoundedRect(g, leftX - 0.8f, ay - 0.8f, barW + 1.6f, 4.6f, rad * 0.7f, 0xBB090D16);
            AppleUiRenderer.drawRoundedRect(g, leftX, ay, barW * armPct, 3.0f, rad * 0.6f, 0xFF38BDF8);
        }

        // 3. Hunger + Bright Outline + Golden Saturation Rim Bar (Right above hotbar)
        AppleUiRenderer.drawRoundedRect(g, rightX - 1.0f, baseY - 1.0f, barW + 2.0f, barH + 2.0f, rad + 0.8f, 0xCC090D16);
        AppleUiRenderer.drawRoundedOutline(g, rightX - 1.0f, baseY - 1.0f, barW + 2.0f, barH + 2.0f, rad + 0.8f, 1.0f, 0x78E2E8F0);
        // Right-to-left fill matching vanilla hunger symmetry
        float foodW = barW * smoothFoodPct;
        AppleUiRenderer.drawRoundedRect(g, rightX + (barW - foodW), baseY, foodW, barH, rad, 0xFFF59E0B);
        if (sat > 0.1f) {
            float satW = barW * Math.min(1.0f, sat / 20.0f);
            AppleUiRenderer.drawRoundedOutline(g, rightX + (barW - satW), baseY, satW, barH, rad, 1.0f, 0xFFFDE047);
        }
        if (showBarNumbers.get()) {
            YeHdFont.drawCentered(g, food + "/20", rightX + barW * 0.5f, baseY - 0.8f, 7.2f, 0xFFF8FAFC, true, true);
        }

        // 4. Air Oxygen Bar (Above Hunger when underwater)
        int maxAir = p.getMaxAirSupply();
        int air = p.getAirSupply();
        if (air < maxAir) {
            float airPct = Math.max(0f, Math.min(1f, air / (float) Math.max(1, maxAir)));
            float oy = baseY - 7.0f;
            AppleUiRenderer.drawRoundedRect(g, rightX - 0.8f, oy - 0.8f, barW + 1.6f, 4.6f, rad * 0.7f, 0xBB090D16);
            AppleUiRenderer.drawRoundedRect(g, rightX + (barW - barW * airPct), oy, barW * airPct, 3.0f, rad * 0.6f, 0xFF22D3EE);
        }
    }
}
