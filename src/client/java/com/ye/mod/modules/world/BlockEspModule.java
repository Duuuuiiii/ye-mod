package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Block ESP Module (方块透视):
 * Highlights selected blocks through walls with exact block VoxelShape bounds, natural ore colors or custom color,
 * and optional styled tracer lines — paired with our large searchable Block Matrix Picker!
 */
public class BlockEspModule extends Module {
    public final DoubleSetting range = addSetting(new DoubleSetting(
            "range", "透视半径", "扫描周围多少格内的选中方块", 48.0, 8.0, 128.0, 4.0
    ).functionGroup());

    public final DoubleSetting maxBlocks = addSetting(new DoubleSetting(
            "maxBlocks", "最大渲染数", "最多同时透视多少个匹配方块", 250.0, 30.0, 800.0, 20.0
    ).functionGroup());

    public final EnumSetting<StashRadarModule.BoxRenderMode> boxMode = addSetting(new EnumSetting<>(
            "boxMode", "渲染方式", "边框+填充 / 仅边框 / 仅填充", StashRadarModule.BoxRenderMode.BOTH
    ).functionGroup());

    public final BoolSetting tracers = addSetting(new BoolSetting(
            "tracers", "绘制追踪线", "向匹配方块绘制追踪射线", false
    ).functionGroup());

    public final EnumSetting<StashRadarModule.LineStyle> lineStyle = addSetting(new EnumSetting<>(
            "lineStyle", "线条样式", "实线 / 虚线 / 闪烁虚线", StashRadarModule.LineStyle.SOLID
    ).visible(tracers::get).functionGroup());

    public final DoubleSetting lineWidth = addSetting(new DoubleSetting(
            "lineWidth", "线条粗细", "边框与追踪线粗细", 1.6, 0.8, 4.0, 0.2
    ).functionGroup());

    public final BoolSetting naturalBlockColor = addSetting(new BoolSetting(
            "naturalBlockColor", "按矿石原色区分", "钻石矿青蓝、远古残骸棕金、绿宝石翠绿自动着色", true
    ));

    public final ColorSetting customColor = addSetting(new ColorSetting(
            "customColor", "统一高亮色", "关闭原色区分时使用的统一颜色", 0xFF38BDF8
    ).visible(() -> !naturalBlockColor.get()));

    public final DoubleSetting fillAlpha = addSetting(new DoubleSetting(
            "fillAlpha", "填充浓度", "方块内部半透明填充强度", 0.25, 0.05, 0.70, 0.05
    ).visible(() -> boxMode.get() != StashRadarModule.BoxRenderMode.OUTLINE_ONLY));

    private record FoundBlock(BlockPos pos, int rgb) {}
    private volatile List<FoundBlock> cached = List.of();
    private int tickCounter = 0;

    public BlockEspModule() {
        super("block_esp", "方块透视", "Block ESP", "穿墙透视任意选定方块，配全方块分类搜索大面板与矿石原色识别", Category.UTILITY);

        addSetting(new ActionSetting("openBlockMatrix", "打开方块选择大面板", "在分类平铺搜索面板中勾选或取消任意要透视的方块", () ->
                YeQuickMenu.openBlockPickerModal("方块透视 · 选择目标方块", ConfigManager.blockEspTargets)
        ).functionGroup());
    }

    @Override
    protected void onDisable() {
        cached = List.of();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            cached = List.of();
            return;
        }
        tickCounter++;
        if (tickCounter % 12 != 0) return;

        Set<String> targetIds = ConfigManager.blockEspTargets;
        if (targetIds.isEmpty()) {
            cached = List.of();
            return;
        }

        Level level = mc.level;
        BlockPos pPos = mc.player.blockPosition();
        int r = (int) Math.round(range.get());
        int limit = (int) Math.round(maxBlocks.get());
        double rSq = (double) r * r;

        int minCx = (pPos.getX() - r) >> 4, maxCx = (pPos.getX() + r) >> 4;
        int minCz = (pPos.getZ() - r) >> 4, maxCz = (pPos.getZ() + r) >> 4;
        int minSecY = Math.max(level.getMinSectionY(), (pPos.getY() - r) >> 4);
        int maxSecY = Math.min(level.getMaxSectionY() - 1, (pPos.getY() + r) >> 4);

        List<FoundBlock> found = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] secs = chunk.getSections();
                int bx = cx << 4, bz = cz << 4;

                for (int sy = minSecY; sy <= maxSecY; sy++) {
                    int secIdx = level.getSectionIndexFromSectionY(sy);
                    if (secIdx < 0 || secIdx >= secs.length) continue;
                    LevelChunkSection sec = secs[secIdx];
                    if (sec.hasOnlyAir()) continue;
                    if (!sec.maybeHas(st -> targetIds.contains(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString()))) {
                        continue;
                    }
                    int baseY = sy << 4;
                    for (int y = 0; y < 16; y++) {
                        int wy = baseY + y;
                        for (int x = 0; x < 16; x++) {
                            int wx = bx + x;
                            for (int z = 0; z < 16; z++) {
                                int wz = bz + z;
                                if (pPos.distToCenterSqr(wx + 0.5, wy + 0.5, wz + 0.5) > rSq) continue;
                                BlockState st = sec.getBlockState(x, y, z);
                                String key = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
                                if (targetIds.contains(key)) {
                                    m.set(wx, wy, wz);
                                    found.add(new FoundBlock(m.immutable(), resolveBlockColor(st, level, m)));
                                    if (found.size() >= limit) break;
                                }
                            }
                            if (found.size() >= limit) break;
                        }
                        if (found.size() >= limit) break;
                    }
                    if (found.size() >= limit) break;
                }
                if (found.size() >= limit) break;
            }
            if (found.size() >= limit) break;
        }
        cached = found;
    }

    public static int resolveBlockColor(BlockState st, Level level, BlockPos pos) {
        Block b = st.getBlock();
        if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE || b == Blocks.DIAMOND_BLOCK) return 0x22D3EE;
        if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE || b == Blocks.EMERALD_BLOCK) return 0x4ADE80;
        if (b == Blocks.ANCIENT_DEBRIS || b == Blocks.NETHERITE_BLOCK) return 0xF97316;
        if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE || b == Blocks.GOLD_BLOCK) return 0xFACC15;
        if (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE || b == Blocks.REDSTONE_BLOCK) return 0xEF4444;
        if (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE || b == Blocks.LAPIS_BLOCK) return 0x3B82F6;
        if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE) return 0xFDBA74;
        if (b == Blocks.AMETHYST_CLUSTER || b == Blocks.BUDDING_AMETHYST || b == Blocks.NETHER_PORTAL) return 0xC084FC;
        if (b == Blocks.SPAWNER) return 0xF43F5E;
        MapColor mc = st.getMapColor(level, pos);
        if (mc != null && mc.col != 0) return mc.col & 0x00FFFFFF;
        return 0x38BDF8;
    }

    public void emitGizmos(Vec3 tracerStart) {
        if (!isEnabled() || cached.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        boolean useNat = naturalBlockColor.get();
        int fallbackRgb = customColor.getRgb();
        float lw = lineWidth.get().floatValue();
        StashRadarModule.BoxRenderMode bm = boxMode.get();
        int fillA = (bm == StashRadarModule.BoxRenderMode.OUTLINE_ONLY) ? 0 : Math.max(8, Math.min(235, (int) Math.round(fillAlpha.get() * 255.0)));

        for (FoundBlock fb : cached) {
            int rgb = useNat ? fb.rgb : fallbackRgb;
            int stroke = (bm == StashRadarModule.BoxRenderMode.FILL_ONLY) ? 0 : (0xEE000000 | rgb);
            int fill = (fillA << 24) | rgb;

            BlockState st = mc.level.getBlockState(fb.pos);
            VoxelShape sh = st.getShape(mc.level, fb.pos);
            AABB box = sh.isEmpty() ? new AABB(fb.pos) : sh.bounds().move(fb.pos);

            if (bm == StashRadarModule.BoxRenderMode.OUTLINE_ONLY) {
                Gizmos.cuboid(box, GizmoStyle.stroke(stroke, lw)).setAlwaysOnTop();
            } else {
                Gizmos.cuboid(box, GizmoStyle.strokeAndFill(stroke, lw, fill)).setAlwaysOnTop();
            }

            if (tracers.get() && tracerStart != null) {
                StashRadarModule.drawStyledTracer(tracerStart, box.getCenter(), rgb, lw, lineStyle.get(), 2.5, now);
            }
        }
    }
}
