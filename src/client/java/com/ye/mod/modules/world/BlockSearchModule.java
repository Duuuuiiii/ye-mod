package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Block Search Radar & Coordinate List Module (方块搜索):
 * Scans a wide chunk radius for target blocks chosen in the Block Matrix Picker,
 * lists exact coordinates + distances right inside its detail panel (with 1-click Baritone #goto & pin tracer!),
 * and alerts on new discoveries!
 */
public class BlockSearchModule extends Module {
    public record SearchResult(BlockPos pos, String blockName, double distance, int rgb) {}

    public final DoubleSetting chunkRadius = addSetting(new DoubleSetting(
            "chunkRadius", "搜索区块半径", "大范围扫描周围多少区块内的目标方块", 10.0, 2.0, 28.0, 1.0
    ).functionGroup());

    public final BoolSetting traceNearest = addSetting(new BoolSetting(
            "traceNearest", "追踪最近目标", "自动向距离最近或手动锁定的搜索结果绘制引导线", true
    ).functionGroup());

    public final BoolSetting soundOnFind = addSetting(new BoolSetting(
            "soundOnFind", "发现提示音", "扫描到新目标方块坐标时播放提示音", true
    ).functionGroup());

    public final ColorSetting beaconColor = addSetting(new ColorSetting(
            "beaconColor", "锁定引导颜色", "目标引导线与坐标标记颜色", 0xFF22D3EE
    ));

    private volatile List<SearchResult> results = List.of();
    private volatile BlockPos pinnedTarget = null;
    private final Set<Long> alerted = new HashSet<>();
    private int tickCounter = 0;
    private Level lastTrackedLevel = null;

    public BlockSearchModule() {
        super("block_search", "方块搜索", "Block Search", "大范围雷达检索指定方块并生成精确坐标距离清单，支持一键寻路与锁定引导", Category.UTILITY);

        addSetting(new ActionSetting("openSearchBlockPicker", "选择搜索目标方块", "打开全方块分类搜索大面板勾选要搜寻的方块", () ->
                YeQuickMenu.openBlockPickerModal("方块搜索 · 选择搜寻目标", ConfigManager.blockSearchTargets)
        ).functionGroup());

        addSetting(new ActionSetting("rescanNow", "立即重新扫描", "清空提示缓存并立刻重新扫描周围区块", () -> {
            alerted.clear();
            tickCounter = 19;
            onTick();
            YeQuickMenu.showToast(ConfigManager.tr("已完成扫描，共找到 " + results.size() + " 处目标", "Found " + results.size() + " blocks"),
                    ConfigManager.getAccentColor());
        }).functionGroup());
    }

    public List<SearchResult> getResults() {
        return results;
    }

    public BlockPos getPinnedTarget() {
        return pinnedTarget;
    }

    public void setPinnedTarget(BlockPos pos) {
        this.pinnedTarget = pos;
    }

    public void navigateBaritoneWalk(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null && pos != null) {
            pinnedTarget = pos;
            mc.player.connection.sendChat(String.format("#goto %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
            YeQuickMenu.showToast(String.format("走路前往: %d, %d, %d", pos.getX(), pos.getY(), pos.getZ()), 0xFF34D399);
        }
    }

    public void navigateBaritoneFly(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null && pos != null) {
            pinnedTarget = pos;
            mc.player.connection.sendChat(String.format("#elytra %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
            YeQuickMenu.showToast(String.format("飞行前往: %d, %d, %d", pos.getX(), pos.getY(), pos.getZ()), 0xFF0EA5E9);
        }
    }

    @Override
    protected void onDisable() {
        results = List.of();
        pinnedTarget = null;
        alerted.clear();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            lastTrackedLevel = null;
            results = List.of();
            pinnedTarget = null;
            return;
        }
        if (mc.level != lastTrackedLevel) {
            lastTrackedLevel = mc.level;
            results = List.of();
            pinnedTarget = null;
            alerted.clear();
            tickCounter = 19;
        }
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        Set<String> targets = ConfigManager.blockSearchTargets;
        if (targets.isEmpty()) {
            results = List.of();
            return;
        }

        Level level = mc.level;
        int cr = (int) Math.round(chunkRadius.get());
        int pcx = mc.player.chunkPosition().x;
        int pcz = mc.player.chunkPosition().z;
        Vec3 eye = mc.player.position();

        List<SearchResult> found = new ArrayList<>();
        boolean newlyFound = false;

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] secs = chunk.getSections();
                int bx = cx << 4, bz = cz << 4;
                for (int si = 0; si < secs.length; si++) {
                    LevelChunkSection sec = secs[si];
                    if (sec.hasOnlyAir()) continue;
                    if (!sec.maybeHas(st -> targets.contains(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString()))) continue;
                    int by = level.getSectionYFromSectionIndex(si) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState st = sec.getBlockState(x, y, z);
                                String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
                                if (targets.contains(id)) {
                                    BlockPos pos = new BlockPos(bx + x, by + y, bz + z);
                                    double dist = Math.sqrt(pos.distToCenterSqr(eye.x, eye.y, eye.z));
                                    String nm = st.getBlock().getName().getString();
                                    found.add(new SearchResult(pos, nm, dist, BlockEspModule.resolveBlockColor(st, level, pos)));
                                    if (alerted.add(pos.asLong())) newlyFound = true;
                                    if (found.size() >= 160) break;
                                }
                            }
                            if (found.size() >= 160) break;
                        }
                        if (found.size() >= 160) break;
                    }
                    if (found.size() >= 160) break;
                }
                if (found.size() >= 160) break;
            }
            if (found.size() >= 160) break;
        }

        found.sort(Comparator.comparingDouble(SearchResult::distance));
        if (newlyFound && soundOnFind.get() && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.35f, 0.65f));
        }
        results = found;
    }

    public void emitGizmos(Vec3 tracerStart) {
        if (!isEnabled() || results.isEmpty()) return;
        int col = 0xEE000000 | beaconColor.getRgb();
        int fill = 0x44000000 | beaconColor.getRgb();

        BlockPos focus = pinnedTarget != null ? pinnedTarget : (traceNearest.get() ? results.get(0).pos : null);
        if (focus != null) {
            Gizmos.cuboid(focus, GizmoStyle.strokeAndFill(col, 2.4f, fill)).setAlwaysOnTop();
            if (tracerStart != null) {
                Gizmos.line(tracerStart, Vec3.atCenterOf(focus), col, 2.2f).setAlwaysOnTop();
            }
        }
    }
}
