package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.ui.AppleUiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights nearby lava blocks through walls (X-ray ESP) with smart edge-only filtering
 * and close-range danger alerts.
 */
public class LavaEspModule extends Module {
    public enum ColorPreset {
        ORANGE_RED(0xFFFF6B1A),
        BRIGHT_RED(0xFFFF2A2A),
        NEON_YELLOW(0xFFFFD60A),
        CYBER_MAGENTA(0xFFFF2E93);

        final int rgb;
        ColorPreset(int rgb) { this.rgb = rgb; }
    }

    public final DoubleSetting radius = addSetting(new DoubleSetting(
            "radius", "扫描半径", "高亮周围多少格内的岩浆", 8.0, 3.0, 32.0, 1.0
    ));

    public final BoolSetting onlyExposedEdges = addSetting(new BoolSetting(
            "onlyExposedEdges", "智能只描外缘", "大岩浆海只高亮与非岩浆相邻的外缘方块，不糊屏也不卡", true
    ));

    public final BoolSetting onlySource = addSetting(new BoolSetting(
            "onlySource", "仅显示岩浆源头", "忽略流动岩浆，只高亮静止的岩浆源头", false
    ));

    public final BoolSetting dangerAlert = addSetting(new BoolSetting(
            "dangerAlert", "近距离高危预警", "距离 4 格内存在岩浆时框体变亮红并显示顶部提示", true
    ));

    public final EnumSetting<ColorPreset> colorPreset = addSetting(new EnumSetting<>(
            "colorPreset", "高亮配色", "选择岩浆高亮框的颜色风格", ColorPreset.ORANGE_RED
    ));

    public final DoubleSetting fillAlpha = addSetting(new DoubleSetting(
            "fillAlpha", "填充透明度", "岩浆方块内部半透明填充强度", 0.26, 0.0, 0.75, 0.05
    ));

    private record LavaEntry(BlockPos pos, boolean source, boolean danger) {}

    private volatile List<LavaEntry> cachedBlocks = List.of();
    private volatile boolean hasDangerNearby = false;
    private int tickCounter = 0;

    public LavaEspModule() {
        super("lava_esp", "岩浆高亮", "Lava ESP", "透视高亮周围几格内的岩浆，支持范围调节与挖矿防烫预警", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        cachedBlocks = List.of();
        hasDangerNearby = false;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            cachedBlocks = List.of();
            hasDangerNearby = false;
            return;
        }

        tickCounter++;
        if (tickCounter % 2 != 0) {
            return;
        }

        Level level = mc.level;
        BlockPos center = mc.player.blockPosition();
        int r = Math.max(3, Math.min(32, (int) Math.round(radius.get())));
        double rSq = r * r;
        boolean edgesOnly = onlyExposedEdges.get();
        boolean sourceOnly = onlySource.get();
        boolean alertEnabled = dangerAlert.get();

        List<LavaEntry> found = new ArrayList<>();
        boolean dangerFound = false;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos npos = new BlockPos.MutableBlockPos();

        int maxResults = 800;
        outer:
        for (int dy = -r; dy <= r; dy++) {
            int y = center.getY() + dy;
            if (y < level.getMinY() || y >= level.getMaxY()) continue;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dSq = dx * dx + dy * dy + dz * dz;
                    if (dSq > rSq) continue;

                    mpos.set(center.getX() + dx, y, center.getZ() + dz);
                    FluidState fluid = level.getFluidState(mpos);
                    if (!fluid.is(FluidTags.LAVA)) continue;

                    boolean isSource = fluid.isSource();
                    if (sourceOnly && !isSource) continue;

                    if (edgesOnly) {
                        boolean exposed = false;
                        for (Direction dir : Direction.values()) {
                            npos.setWithOffset(mpos, dir);
                            if (!level.getFluidState(npos).is(FluidTags.LAVA)) {
                                exposed = true;
                                break;
                            }
                        }
                        if (!exposed) continue;
                    }

                    boolean isDanger = alertEnabled && dSq <= 16.0;
                    if (isDanger) {
                        dangerFound = true;
                    }
                    found.add(new LavaEntry(mpos.immutable(), isSource, isDanger));
                    if (found.size() >= maxResults) {
                        break outer;
                    }
                }
            }
        }

        cachedBlocks = found;
        hasDangerNearby = dangerFound;
    }

    public void emitGizmos() {
        if (!isEnabled()) return;
        List<LavaEntry> blocks = cachedBlocks;
        if (blocks.isEmpty()) return;

        int baseRgb = colorPreset.get().rgb & 0x00FFFFFF;
        int alphaByte = Math.max(0, Math.min(255, (int) Math.round(fillAlpha.get() * 255.0)));

        for (LavaEntry entry : blocks) {
            int rgb = entry.danger ? 0xFF2020 : baseRgb;
            int strokeAlpha = entry.source ? 0xEE : 0x99;
            int fillA = entry.source ? alphaByte : Math.max(0, alphaByte / 2);

            int strokeArgb = (strokeAlpha << 24) | rgb;
            int fillArgb = (fillA << 24) | rgb;
            Gizmos.cuboid(entry.pos, GizmoStyle.strokeAndFill(strokeArgb, 2.0f, fillArgb)).setAlwaysOnTop();
        }
    }

    public void renderHudAlert(GuiGraphics g) {
        if (!isEnabled() || !dangerAlert.get() || !hasDangerNearby) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        String text = "⚠ 附近 4 格内有岩浆！";
        int sw = mc.getWindow().getGuiScaledWidth();
        int pillW = font.width(text) + 18;
        int x = (sw - pillW) / 2;
        int y = 30;
        AppleUiRenderer.drawRoundedRect(g, x, y, pillW, 18, 9, 0xE6B91C1C);
        g.drawString(font, text, x + 9, y + 5, 0xFFF8FAFC, false);
    }
}
