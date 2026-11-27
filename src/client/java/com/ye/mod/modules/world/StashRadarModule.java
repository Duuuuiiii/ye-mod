package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.ui.YeHdFont;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Storage ESP (储存透视):
 * - Exact Block VoxelShape & Dynamic ShulkerBox Opening Lid Bounding Box (purple box follows opening lid 100%!)
 * - Exact 14x14x14 inset Chest geometry + Merge Double Chests toggle (合并大箱子边框 vs 分别渲染)
 * - Per-container independent settings: Color, Box Mode (边框/填充/边框+填充), Tracer (实线/闪烁虚线), Line Width, Blink Hz!
 * - Searchable Block Matrix Picker for natural chest ignore filter!
 */
public class StashRadarModule extends Module {
    public enum ContainerKind {
        SHULKER("潜影盒", true, 0xFFD946EF, true, LineStyle.SOLID, 2.2, 2.0),
        CHEST("箱子与木桶", true, 0xFFF59E0B, false, LineStyle.BLINK_DASH, 1.6, 3.0),
        ENDER_CHEST("末影箱", true, 0xFF38BDF8, false, LineStyle.SOLID, 1.8, 2.0),
        SPAWNER("刷怪笼", true, 0xFFEF4444, false, LineStyle.BLINK_DASH, 2.0, 4.0),
        HOPPER("漏斗", false, 0xFF4ADE80, false, LineStyle.SOLID, 1.4, 2.0);

        public final String label;
        final boolean defOn;
        final int defColor;
        final boolean defTracer;
        final LineStyle defLine;
        final double defWidth;
        final double defFreq;

        ContainerKind(String label, boolean defOn, int defColor, boolean defTracer, LineStyle defLine, double defWidth, double defFreq) {
            this.label = label;
            this.defOn = defOn;
            this.defColor = defColor;
            this.defTracer = defTracer;
            this.defLine = defLine;
            this.defWidth = defWidth;
            this.defFreq = defFreq;
        }
    }

    public enum BoxRenderMode {
        BOTH("边框+填充"),
        OUTLINE_ONLY("仅边框"),
        FILL_ONLY("仅填充");

        public final String label;
        BoxRenderMode(String label) { this.label = label; }
    }

    public enum LineStyle {
        SOLID("实线"),
        DASHED("常亮虚线"),
        BLINK_DASH("闪烁虚线"),
        BLINK_SOLID("闪烁实线");

        public final String label;
        LineStyle(String label) { this.label = label; }
    }

    public static final class ContainerConfig {
        public final BoolSetting enabled;
        public final ColorSetting color;
        public final EnumSetting<BoxRenderMode> boxMode;
        public final DoubleSetting fillAlpha;
        public final BoolSetting tracer;
        public final EnumSetting<LineStyle> lineStyle;
        public final DoubleSetting lineWidth;
        public final DoubleSetting blinkHz;

        ContainerConfig(BoolSetting enabled, ColorSetting color, EnumSetting<BoxRenderMode> boxMode,
                        DoubleSetting fillAlpha, BoolSetting tracer, EnumSetting<LineStyle> lineStyle,
                        DoubleSetting lineWidth, DoubleSetting blinkHz) {
            this.enabled = enabled;
            this.color = color;
            this.boxMode = boxMode;
            this.fillAlpha = fillAlpha;
            this.tracer = tracer;
            this.lineStyle = lineStyle;
            this.lineWidth = lineWidth;
            this.blinkHz = blinkHz;
        }
    }

    public final DoubleSetting rangeChunks = addSetting(new DoubleSetting(
            "rangeChunks", "扫描半径", "扫描周围多少个区块内的储存方块", 10.0, 2.0, 32.0, 1.0
    ).functionGroup());

    public final BoolSetting mergeDoubleChests = addSetting(new BoolSetting(
            "mergeDoubleChests", "合并连体大箱子", "相连的大箱子合并为一个完整边框渲染，关闭则各自独立渲染", true
    ).functionGroup());

    public final BoolSetting uniformOpacity = addSetting(new BoolSetting(
            "uniformOpacity", "统一填充深浅", "相邻或重叠的同类容器保持一致透明深浅，不叠加变深", true
    ));

    public final BoolSetting ignoreNatural = addSetting(new BoolSetting(
            "ignoreNatural", "过滤自然遗迹箱子", "自动跳过刷怪笼、矿井、沙漠神殿、地狱堡垒等自然生成箱子", true
    ).functionGroup());

    public final BoolSetting ignoreNetherBricks = addSetting(new BoolSetting(
            "ignoreNetherBricks", "过滤地狱砖周边", "跳过地狱砖结构旁边的箱子", true
    ).visible(ignoreNatural::get).functionGroup());

    public final BoolSetting soundAlert = addSetting(new BoolSetting(
            "soundAlert", "新发现提示音", "首次扫描到潜影盒或基地箱子时播放清脆提示音", true
    ).functionGroup());

    public final EnumSetting<ContainerKind> editingKind = addSetting(new EnumSetting<>(
            "editingKind", "当前配置容器", "选择下方要单独调节样式、线条与颜色的储存类型", ContainerKind.SHULKER
    ).functionGroup().gridPicker());

    public final Map<ContainerKind, ContainerConfig> configs = new EnumMap<>(ContainerKind.class);

    private record StorageTarget(BlockPos pos, BlockPos secondHalf, ContainerKind kind) {}

    private volatile List<StorageTarget> cached = List.of();
    private volatile int shulkerCount = 0;
    private volatile int totalCount = 0;
    private final Set<Long> alertedPositions = new HashSet<>();
    private int tickCounter = 0;

    public StashRadarModule() {
        super("stash_radar", "储存透视", "Storage ESP", "精确贴合模型渲染储存方块，支持分容器独立线条粗细、虚线闪烁、合并大箱子与动态潜影盒盖", Category.UTILITY);

        addSetting(new ActionSetting("openIgnoreBlockPicker", "选择自然过滤方块", "打开全方块分类搜索大面板勾选需排除的自然周边方块", () ->
                YeQuickMenu.openBlockPickerModal("自然箱子周边过滤方块", ConfigManager.customRadarIgnoreBlocks)
        ).visible(ignoreNatural::get).functionGroup());

        for (ContainerKind kind : ContainerKind.values()) {
            String p = kind.name().toLowerCase();
            BoolSetting en = addSetting(new BoolSetting(p + "_on", "启用·" + kind.label, "是否透视" + kind.label, kind.defOn)
                    .visible(() -> editingKind.get() == kind).functionGroup());
            EnumSetting<BoxRenderMode> bm = addSetting(new EnumSetting<>(p + "_box", kind.label + "·框体模式", "边框+填充 / 仅边框 / 仅填充", BoxRenderMode.BOTH)
                    .visible(() -> editingKind.get() == kind && en.get()).functionGroup());
            BoolSetting tr = addSetting(new BoolSetting(p + "_tr", kind.label + "·追踪线", "绘制从视野中心指向该容器的追踪线", kind.defTracer)
                    .visible(() -> editingKind.get() == kind && en.get()).functionGroup());
            EnumSetting<LineStyle> ls = addSetting(new EnumSetting<>(p + "_ls", kind.label + "·线条样式", "实线 / 常亮虚线 / 闪烁虚线 / 闪烁实线", kind.defLine)
                    .visible(() -> editingKind.get() == kind && en.get() && tr.get()).functionGroup());
            DoubleSetting lw = addSetting(new DoubleSetting(p + "_lw", kind.label + "·线条粗细", "设置追踪线与边框粗细数值", kind.defWidth, 0.8, 5.0, 0.2)
                    .visible(() -> editingKind.get() == kind && en.get()).functionGroup());
            DoubleSetting hz = addSetting(new DoubleSetting(p + "_hz", kind.label + "·闪烁频率", "每秒闪烁次数 (Hz)", kind.defFreq, 0.5, 10.0, 0.5)
                    .visible(() -> editingKind.get() == kind && en.get() && tr.get()
                            && (ls.get() == LineStyle.BLINK_DASH || ls.get() == LineStyle.BLINK_SOLID)).functionGroup());

            ColorSetting col = addSetting(new ColorSetting(p + "_col", kind.label + "·颜色", "单独设置" + kind.label + "的高亮与追踪线颜色", kind.defColor)
                    .visible(() -> editingKind.get() == kind && en.get()));
            DoubleSetting fa = addSetting(new DoubleSetting(p + "_fa", kind.label + "·填充浓度", "内部半透明填充不透明度", 0.24, 0.05, 0.70, 0.05)
                    .visible(() -> editingKind.get() == kind && en.get() && bm.get() != BoxRenderMode.OUTLINE_ONLY));

            configs.put(kind, new ContainerConfig(en, col, bm, fa, tr, ls, lw, hz));
        }
    }

    @Override
    protected void onDisable() {
        cached = List.of();
        shulkerCount = 0;
        totalCount = 0;
        alertedPositions.clear();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            cached = List.of();
            return;
        }
        tickCounter++;
        if (tickCounter % 10 != 0) return;

        Level level = mc.level;
        int cr = Math.max(2, Math.min(32, (int) Math.round(rangeChunks.get())));
        int pcx = mc.player.chunkPosition().x;
        int pcz = mc.player.chunkPosition().z;
        boolean filterNat = ignoreNatural.get();
        boolean filterNether = ignoreNetherBricks.get();
        boolean mergeChests = mergeDoubleChests.get();

        List<StorageTarget> found = new ArrayList<>();
        Set<Long> visitedDoubleHalf = new HashSet<>();
        int shulkersFound = 0;
        boolean playAlert = false;

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (visitedDoubleHalf.contains(pos.asLong())) continue;

                    if (be instanceof ShulkerBoxBlockEntity && configs.get(ContainerKind.SHULKER).enabled.get()) {
                        found.add(new StorageTarget(pos, null, ContainerKind.SHULKER));
                        shulkersFound++;
                        if (alertedPositions.add(pos.asLong())) playAlert = true;
                    } else if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && configs.get(ContainerKind.CHEST).enabled.get()) {
                        if (filterNat && isNaturalContainer(level, pos, be, filterNether)) continue;
                        BlockPos otherHalf = null;
                        if (mergeChests && be instanceof ChestBlockEntity) {
                            BlockState st = level.getBlockState(pos);
                            if (st.getBlock() instanceof ChestBlock && st.hasProperty(ChestBlock.TYPE)) {
                                ChestType ct = st.getValue(ChestBlock.TYPE);
                                if (ct != ChestType.SINGLE) {
                                    Direction connDir = ChestBlock.getConnectedDirection(st);
                                    BlockPos cand = pos.relative(connDir);
                                    if (level.getBlockEntity(cand) instanceof ChestBlockEntity) {
                                        otherHalf = cand;
                                        visitedDoubleHalf.add(cand.asLong());
                                    }
                                }
                            }
                        }
                        found.add(new StorageTarget(pos, otherHalf, ContainerKind.CHEST));
                        if (alertedPositions.add(pos.asLong())) playAlert = true;
                    } else if (be instanceof EnderChestBlockEntity && configs.get(ContainerKind.ENDER_CHEST).enabled.get()) {
                        found.add(new StorageTarget(pos, null, ContainerKind.ENDER_CHEST));
                    } else if (be instanceof SpawnerBlockEntity && configs.get(ContainerKind.SPAWNER).enabled.get()) {
                        found.add(new StorageTarget(pos, null, ContainerKind.SPAWNER));
                    } else if (be instanceof HopperBlockEntity && configs.get(ContainerKind.HOPPER).enabled.get()) {
                        found.add(new StorageTarget(pos, null, ContainerKind.HOPPER));
                    }
                    if (found.size() >= 600) break;
                }
                if (found.size() >= 600) break;
            }
            if (found.size() >= 600) break;
        }

        if (playAlert && soundAlert.get()) {
            mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.25f);
        }

        cached = found;
        shulkerCount = shulkersFound;
        totalCount = found.size();
    }

    /**
     * Computes exact model-hugging AABB on every render frame (including live Shulker Box opening lid animation!).
     */
    private static AABB resolveExactContainerBox(Level level, StorageTarget t) {
        BlockPos pos = t.pos;
        BlockState state = level.getBlockState(pos);

        if (t.kind == ContainerKind.SHULKER && level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity) {
            VoxelShape shape = state.getShape(level, pos);
            if (!shape.isEmpty()) {
                return shape.bounds().move(pos);
            }
        }

        AABB baseBox;
        VoxelShape shape = state.getShape(level, pos);
        if (!shape.isEmpty()) {
            baseBox = shape.bounds().move(pos);
        } else {
            baseBox = new AABB(pos);
        }

        if (t.secondHalf != null) {
            BlockState st2 = level.getBlockState(t.secondHalf);
            VoxelShape sh2 = st2.getShape(level, t.secondHalf);
            AABB b2 = sh2.isEmpty() ? new AABB(t.secondHalf) : sh2.bounds().move(t.secondHalf);
            return baseBox.minmax(b2);
        }
        return baseBox;
    }

    public int getTotalStorageCount() {
        return isEnabled() ? totalCount : 0;
    }

    public int getShulkerCount() {
        return isEnabled() ? shulkerCount : 0;
    }

    public void emitGizmos(Vec3 tracerStart) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long nowMs = System.currentTimeMillis();
        boolean uniform = uniformOpacity.get();

        // When uniformOpacity is enabled, group contiguous same-kind boxes so overlapping/adjacent fills never double-darken!
        Map<ContainerKind, List<AABB>> rawBoxesByKind = new EnumMap<>(ContainerKind.class);

        for (StorageTarget t : cached) {
            ContainerConfig cfg = configs.get(t.kind);
            if (cfg == null || !cfg.enabled.get()) continue;

            AABB box = resolveExactContainerBox(mc.level, t);
            int rgb = cfg.color.getRgb();
            float width = cfg.lineWidth.get().floatValue();
            BoxRenderMode bm = cfg.boxMode.get();

            int strokeCol = (bm == BoxRenderMode.FILL_ONLY) ? 0x00000000 : (0xF0000000 | rgb);
            int fillA = (bm == BoxRenderMode.OUTLINE_ONLY) ? 0 : Math.max(8, Math.min(240, (int) Math.round(cfg.fillAlpha.get() * 255.0)));
            int fillCol = (fillA << 24) | rgb;

            if (!uniform || bm == BoxRenderMode.OUTLINE_ONLY) {
                if (bm == BoxRenderMode.OUTLINE_ONLY) {
                    Gizmos.cuboid(box, GizmoStyle.stroke(strokeCol, width)).setAlwaysOnTop();
                } else {
                    Gizmos.cuboid(box, GizmoStyle.strokeAndFill(strokeCol, width, fillCol)).setAlwaysOnTop();
                }
            } else {
                if (bm == BoxRenderMode.BOTH) {
                    Gizmos.cuboid(box, GizmoStyle.stroke(strokeCol, width)).setAlwaysOnTop();
                }
                rawBoxesByKind.computeIfAbsent(t.kind, k -> new ArrayList<>()).add(box);
            }

            if (cfg.tracer.get() && tracerStart != null) {
                drawStyledTracer(tracerStart, box.getCenter(), rgb, width, cfg.lineStyle.get(), cfg.blinkHz.get(), nowMs);
            }
        }

        if (uniform) {
            for (Map.Entry<ContainerKind, List<AABB>> entry : rawBoxesByKind.entrySet()) {
                ContainerConfig cfg = configs.get(entry.getKey());
                int rgb = cfg.color.getRgb();
                int fillA = Math.max(8, Math.min(240, (int) Math.round(cfg.fillAlpha.get() * 255.0)));
                int fillCol = (fillA << 24) | rgb;
                List<AABB> merged = mergeTouchingBoxes(entry.getValue());
                for (AABB mb : merged) {
                    Gizmos.cuboid(mb, GizmoStyle.fill(fillCol)).setAlwaysOnTop();
                }
            }
        }
    }

    private static List<AABB> mergeTouchingBoxes(List<AABB> input) {
        List<AABB> boxes = new ArrayList<>(input);
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 6) {
            changed = false;
            for (int i = 0; i < boxes.size(); i++) {
                AABB a = boxes.get(i);
                for (int j = i + 1; j < boxes.size(); j++) {
                    AABB b = boxes.get(j);
                    if (canMergeAligned(a, b)) {
                        a = a.minmax(b);
                        boxes.set(i, a);
                        boxes.remove(j);
                        changed = true;
                        j--;
                    }
                }
            }
        }
        return boxes;
    }

    private static boolean canMergeAligned(AABB a, AABB b) {
        final double eps = 0.08;
        boolean sameY = Math.abs(a.minY - b.minY) < eps && Math.abs(a.maxY - b.maxY) < eps;
        boolean sameZ = Math.abs(a.minZ - b.minZ) < eps && Math.abs(a.maxZ - b.maxZ) < eps;
        boolean sameX = Math.abs(a.minX - b.minX) < eps && Math.abs(a.maxX - b.maxX) < eps;
        if (sameY && sameZ && (a.maxX + eps >= b.minX && b.maxX + eps >= a.minX)) return true;
        if (sameY && sameX && (a.maxZ + eps >= b.minZ && b.maxZ + eps >= a.minZ)) return true;
        if (sameX && sameZ && (a.maxY + eps >= b.minY && b.maxY + eps >= a.minY)) return true;
        return false;
    }

    public static void drawStyledTracer(Vec3 start, Vec3 end, int rgb, float width, LineStyle style, double blinkHz, long nowMs) {
        boolean blinking = (style == LineStyle.BLINK_DASH || style == LineStyle.BLINK_SOLID);
        int lineAlpha = 0xEE;
        if (blinking) {
            double wave = 0.5 + 0.5 * Math.sin((nowMs / 1000.0) * blinkHz * Math.PI * 2.0);
            if (wave < 0.18) return;
            lineAlpha = Math.max(35, Math.min(248, (int) Math.round(wave * 248.0)));
        }
        int argb = (lineAlpha << 24) | (rgb & 0x00FFFFFF);

        if (style == LineStyle.SOLID || style == LineStyle.BLINK_SOLID) {
            Gizmos.line(start, end, argb, width).setAlwaysOnTop();
        } else {
            // Crisp segmented 3D dashed tracer ray
            Vec3 diff = end.subtract(start);
            double totalLen = diff.length();
            if (totalLen < 0.05) return;
            Vec3 dir = diff.scale(1.0 / totalLen);
            double step = Math.max(0.8, Math.min(2.8, totalLen / 18.0));
            double dashLen = step * 0.56;
            double animShift = ((nowMs / 1000.0) * 2.4) % step;
            for (double d = animShift; d < totalLen; d += step) {
                double dEnd = Math.min(totalLen, d + dashLen);
                if (dEnd - d > 0.05) {
                    Gizmos.line(start.add(dir.scale(d)), start.add(dir.scale(dEnd)), argb, width).setAlwaysOnTop();
                }
            }
        }
    }

    private static boolean isNaturalContainer(Level level, BlockPos pos, BlockEntity be, boolean filterNether) {
        if (be instanceof RandomizableContainerBlockEntity rce && rce.getLootTable() != null) {
            return true;
        }
        boolean hasCustom = !ConfigManager.customRadarIgnoreBlocks.isEmpty();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    Block b = level.getBlockState(m).getBlock();
                    if (b == Blocks.SPAWNER || b == Blocks.COBWEB || b == Blocks.RAIL
                            || b == Blocks.MOSSY_COBBLESTONE || b == Blocks.SANDSTONE
                            || b == Blocks.CHISELED_SANDSTONE || b == Blocks.CUT_SANDSTONE
                            || b == Blocks.TERRACOTTA || b == Blocks.INFESTED_STONE_BRICKS
                            || b == Blocks.SUSPICIOUS_SAND || b == Blocks.SUSPICIOUS_GRAVEL) {
                        return true;
                    }
                    if (filterNether && (b == Blocks.NETHER_BRICKS || b == Blocks.RED_NETHER_BRICKS
                            || b == Blocks.CHISELED_NETHER_BRICKS || b == Blocks.CRACKED_NETHER_BRICKS
                            || b == Blocks.NETHER_BRICK_FENCE || b == Blocks.NETHER_BRICK_STAIRS)) {
                        return true;
                    }
                    if (hasCustom) {
                        String id = BuiltInRegistries.BLOCK.getKey(b).toString();
                        if (ConfigManager.customRadarIgnoreBlocks.contains(id)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public void renderHud(GuiGraphics g) {
        // Rendered by dedicated StorageCountHud module inside HUD tab!
    }
}
