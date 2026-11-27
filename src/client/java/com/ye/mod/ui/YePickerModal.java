package com.ye.mod.ui;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.YeZhCnDict;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Full-Board Categorized Target Matrix Picker Modal (实体全览大平面 & 方块分类图标搜索大面板):
 * - Entity Matrix: Displays ALL entities laid out cleanly by category (敌对生物 / 友好动物 / 水生环境 / 特殊与投掷物),
 *   with 1-click [全选本类] / [清空本类] batch buttons AND individual 1-click toggles (e.g. check only 绵羊)!
 * - Block Matrix: Displays categorized blocks with 3D item icons + Chinese names + instant search bar + 1-click category select!
 */
public final class YePickerModal {
    public enum Mode { NONE, ENTITY, BLOCK }

    private static Mode activeMode = Mode.NONE;
    private static String modalTitle = "";
    private static Set<String> targetSet = null;
    public static String searchBuffer = "";
    public static boolean searchFocused = true;
    private static double scrollY = 0.0;
    private static double maxScrollY = 0.0;
    private static int selectedBlockTab = 0; // 0=推荐与已选, 1=矿石矿物, 2=容器功能, 3=自然方块, 4=下界末地, 5=红石建筑, 6=全部方块

    public record EntityItem(String id, String name, String group) {}
    public record BlockItem(String id, String name, ItemStack icon, int tabGroup) {}

    private static List<EntityItem> cachedEntities = null;
    private static List<BlockItem> cachedBlocks = null;

    private YePickerModal() {}

    public static boolean isOpen() {
        return activeMode != Mode.NONE && targetSet != null;
    }

    public static void openEntityModal(String title, Set<String> backingSet) {
        activeMode = Mode.ENTITY;
        modalTitle = title;
        targetSet = backingSet;
        searchBuffer = "";
        searchFocused = true;
        scrollY = 0.0;
        ensureCatalogBuilt();
    }

    public static void openBlockModal(String title, Set<String> backingSet) {
        activeMode = Mode.BLOCK;
        modalTitle = title;
        targetSet = backingSet;
        searchBuffer = "";
        searchFocused = true;
        scrollY = 0.0;
        selectedBlockTab = 0;
        ensureCatalogBuilt();
    }

    public static void close() {
        activeMode = Mode.NONE;
        targetSet = null;
        ConfigManager.saveAsync();
    }

    public static void onScroll(double delta) {
        scrollY = Math.max(0.0, Math.min(maxScrollY, scrollY - delta * 26.0));
    }

    private static void ensureCatalogBuilt() {
        YeZhCnDict.ensureInitAsync();
        if (cachedEntities == null) {
            List<EntityItem> list = new ArrayList<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                if (type == EntityType.PLAYER || type == EntityType.MARKER || type == EntityType.INTERACTION) continue;
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                String nm = YeZhCnDict.entityTypeName(type);
                MobCategory cat = type.getCategory();
                String grp = switch (cat) {
                    case MONSTER -> "敌对生物";
                    case CREATURE -> "友好陆生动物";
                    case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> "水生生物";
                    case AMBIENT -> "环境生物";
                    default -> "特殊实体与投掷载具";
                };
                list.add(new EntityItem(id, nm, grp));
            }
            list.sort(Comparator.comparing(EntityItem::name));
            cachedEntities = list;
        }

        if (cachedBlocks == null) {
            List<BlockItem> list = new ArrayList<>();
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) continue;
                String id = BuiltInRegistries.BLOCK.getKey(b).toString();
                String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
                String nm = YeZhCnDict.blockName(b);
                ItemStack icon = b.asItem() != Items.AIR ? b.asItem().getDefaultInstance() : ItemStack.EMPTY;
                int grp = classifyBlockTab(b, path);
                list.add(new BlockItem(id, nm, icon, grp));
            }
            cachedBlocks = list;
        }
    }

    private static int classifyBlockTab(Block b, String path) {
        if (path.contains("_ore") || path.contains("ancient_debris") || path.contains("amethyst")
                || b == Blocks.DIAMOND_BLOCK || b == Blocks.EMERALD_BLOCK || b == Blocks.GOLD_BLOCK
                || b == Blocks.NETHERITE_BLOCK || b == Blocks.RAW_IRON_BLOCK || b == Blocks.RAW_GOLD_BLOCK) {
            return 1; // 矿石矿物
        }
        if (b instanceof EntityBlock || path.contains("chest") || path.contains("shulker")
                || path.contains("portal") || path.contains("spawner") || path.contains("anvil")
                || path.contains("table") || path.contains("furnace") || path.contains("beacon")) {
            return 2; // 容器与功能方块
        }
        if (b.defaultBlockState().is(BlockTags.LOGS) || b.defaultBlockState().is(BlockTags.LEAVES)
                || b.defaultBlockState().is(BlockTags.DIRT) || b.defaultBlockState().is(BlockTags.FLOWERS)
                || b.defaultBlockState().is(BlockTags.SAND) || path.contains("grass") || path.contains("stone")
                || path.contains("moss") || path.contains("water") || path.contains("lava") || path.contains("ice")) {
            return 3; // 自然方块
        }
        if (path.contains("nether") || path.contains("warped") || path.contains("crimson")
                || path.contains("soul_") || path.contains("basalt") || path.contains("blackstone")
                || path.contains("end_") || path.contains("purpur") || path.contains("chorus") || path.contains("obsidian")) {
            return 4; // 下界与末地
        }
        return 5; // 红石与建筑方块
    }

    public static void render(GuiGraphics g, int sw, int sh, int mx, int my, int accent, List<YeQuickMenu.HitRect> outHits) {
        if (!isOpen()) return;
        ensureCatalogBuilt();

        // Refresh localized names dynamically once YeZhCnDict async load finishes!
        boolean mech = AppleUiRenderer.isMechanical();
        int rad = mech ? 0 : 11;
        int mw = Math.min(sw - 28, 560);
        int mh = Math.min(sh - 28, 376);
        int x = (sw - mw) / 2;
        int y = (sh - mh) / 2;

        // Full-screen dim backdrop (blocks any click underneath!)
        g.fill(0, 0, sw, sh, 0x8804070E);
        outHits.add(new YeQuickMenu.HitRect(0, 0, sw, sh, YePickerModal::close, null, null, 10));
        // Absorb clicks inside modal window
        outHits.add(new YeQuickMenu.HitRect(x, y, mw, mh, () -> {}, null, null, 11));

        AppleUiRenderer.drawElevatedWindow(g, x, y, mw, mh, rad);

        // Header Bar: Title + Search Input + Selected Count + Clear All + Close Button
        YeHdFont.draw(g, modalTitle + " (" + targetSet.size() + ")", x + 12, y + 8, 9.4f, AppleUiRenderer.textPrimary(), false, true);

        int sbX = x + 186;
        int sbW = Math.max(120, mw - 324);
        AppleUiRenderer.drawRoundedRect(g, sbX, y + 5, sbW, 18, mech ? 0 : 5, 0xDD080C15);
        AppleUiRenderer.drawRoundedOutline(g, sbX, y + 5, sbW, 18, mech ? 0 : 5, 1.1f, searchFocused ? accent : AppleUiRenderer.borderSubtle());
        String cursor = (searchFocused && (System.currentTimeMillis() / 450L) % 2 == 0) ? "|" : "";
        String qDisp = searchBuffer.isEmpty() && !searchFocused
                ? ConfigManager.tr("输入中文或英文筛选...", "Filter...")
                : (searchBuffer + cursor);
        YeHdFont.draw(g, qDisp, sbX + 7, y + 9.0f, 8.3f,
                searchBuffer.isEmpty() && !searchFocused ? AppleUiRenderer.textTertiary() : 0xFFF8FAFC, false, false);
        outHits.add(new YeQuickMenu.HitRect(sbX, y + 5, sbW, 18, () -> searchFocused = true, () -> searchBuffer = "", null, 12));

        // Clear All button
        int clrX = x + mw - 124;
        AppleUiRenderer.drawRoundedRect(g, clrX, y + 5, 58, 18, mech ? 0 : 5, AppleUiRenderer.bgCardHover());
        YeHdFont.drawCenteredInBox(g, ConfigManager.tr("清空已选", "Clear"), clrX, y + 5, 58, 18, 8.0f, 0xFFF87171, false, true);
        outHits.add(new YeQuickMenu.HitRect(clrX, y + 5, 58, 18, () -> {
            YeQuickMenu.playClick();
            targetSet.clear();
            ConfigManager.saveAsync();
        }, null, null, 12));

        // Done / Close button
        int clsX = x + mw - 60;
        AppleUiRenderer.drawRoundedRect(g, clsX, y + 5, 50, 18, mech ? 0 : 5, accent);
        YeHdFont.drawCenteredInBox(g, ConfigManager.tr("完成", "Done"), clsX, y + 5, 50, 18, 8.2f, 0xFFFFFFFF, false, true);
        outHits.add(new YeQuickMenu.HitRect(clsX, y + 5, 50, 18, () -> {
            YeQuickMenu.playClick();
            close();
        }, null, null, 12));

        g.fill(x + 10, y + 27, x + mw - 10, y + 28, AppleUiRenderer.borderSubtle());

        if (activeMode == Mode.ENTITY) {
            renderEntityMatrix(g, x + 10, y + 32, mw - 20, mh - 40, mx, my, accent, mech, outHits);
        } else {
            renderBlockMatrix(g, x + 10, y + 31, mw - 20, mh - 39, mx, my, accent, mech, outHits);
        }
    }

    private static void renderEntityMatrix(GuiGraphics g, int x, int y, int w, int h,
                                           int mx, int my, int accent, boolean mech, List<YeQuickMenu.HitRect> outHits) {
        String q = searchBuffer.trim().toLowerCase(Locale.ROOT);
        String[] groups = {"敌对生物", "友好陆生动物", "水生生物", "环境生物", "特殊实体与投掷载具"};
        Map<String, List<EntityItem>> byGrp = new LinkedHashMap<>();
        for (String gr : groups) byGrp.put(gr, new ArrayList<>());

        for (EntityItem ei : cachedEntities) {
            String liveZh = YeZhCnDict.entityTypeName(BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse(ei.id)));
            if (!q.isEmpty() && !liveZh.toLowerCase(Locale.ROOT).contains(q) && !ei.id.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            byGrp.computeIfAbsent(ei.group, k -> new ArrayList<>()).add(new EntityItem(ei.id, liveZh, ei.group));
        }

        g.enableScissor(x, y, x + w, y + h);
        int startRy = y - (int) Math.round(scrollY);
        int ry = startRy;
        int cols = 5;
        int gap = 5;
        int cellW = (w - 8 - (cols - 1) * gap) / cols;
        int cellH = 20;

        for (Map.Entry<String, List<EntityItem>> entry : byGrp.entrySet()) {
            List<EntityItem> items = entry.getValue();
            if (items.isEmpty()) continue;

            // Group Banner + 1-Click [全选本类] / [全不选]
            if (ry + 20 >= y && ry <= y + h) {
                AppleUiRenderer.drawRoundedRect(g, x, ry + 3, 3, 12, 1.5f, accent);
                YeHdFont.draw(g, entry.getKey() + " (" + items.size() + ")", x + 7, ry + 4.0f, 8.6f, AppleUiRenderer.textPrimary(), false, true);

                int btnAllX = x + w - 118;
                AppleUiRenderer.drawRoundedRect(g, btnAllX, ry + 1, 52, 15, mech ? 0 : 4, AppleUiRenderer.bgCardHover());
                YeHdFont.drawCenteredInBox(g, "全选本类", btnAllX, ry + 1, 52, 15, 7.6f, accent, false, true);
                if (ry + 16 >= y && ry <= y + h) {
                    int clipTop = Math.max(y, ry + 1), clipBot = Math.min(y + h, ry + 16);
                    if (clipBot > clipTop) {
                        outHits.add(new YeQuickMenu.HitRect(btnAllX, clipTop, 52, clipBot - clipTop, () -> {
                            YeQuickMenu.playClick();
                            for (EntityItem it : items) targetSet.add(it.id);
                            ConfigManager.saveAsync();
                        }, null, null, 12));
                    }
                }

                int btnNoneX = x + w - 62;
                AppleUiRenderer.drawRoundedRect(g, btnNoneX, ry + 1, 52, 15, mech ? 0 : 4, AppleUiRenderer.bgCardHover());
                YeHdFont.drawCenteredInBox(g, "取消本类", btnNoneX, ry + 1, 52, 15, 7.6f, AppleUiRenderer.textSecondary(), false, false);
                if (ry + 16 >= y && ry <= y + h) {
                    int clipTop = Math.max(y, ry + 1), clipBot = Math.min(y + h, ry + 16);
                    if (clipBot > clipTop) {
                        outHits.add(new YeQuickMenu.HitRect(btnNoneX, clipTop, 52, clipBot - clipTop, () -> {
                            YeQuickMenu.playClick();
                            for (EntityItem it : items) targetSet.remove(it.id);
                            ConfigManager.saveAsync();
                        }, null, null, 12));
                    }
                }
            }
            ry += 20;

            for (int i = 0; i < items.size(); i++) {
                EntityItem item = items.get(i);
                int cx = x + (i % cols) * (cellW + gap);
                int cy = ry + (i / cols) * (cellH + gap);
                if (cy + cellH >= y && cy <= y + h) {
                    boolean sel = targetSet.contains(item.id);
                    boolean hov = mx >= cx && mx <= cx + cellW && my >= Math.max(y, cy) && my <= Math.min(y + h, cy + cellH);
                    AppleUiRenderer.drawElevatedCard(g, cx, cy, cellW, cellH, 5, sel ? 1f : 0f, hov ? 1f : 0f, accent);
                    String lbl = YeHdFont.clipToWidth((sel ? "✓ " : "") + item.name, 8.1f, sel, cellW - 8);
                    YeHdFont.drawCenteredInBox(g, lbl, cx, cy, cellW, cellH, 8.1f,
                            sel ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, sel);

                    int ct = Math.max(y, cy), cb = Math.min(y + h, cy + cellH);
                    if (cb > ct) {
                        outHits.add(new YeQuickMenu.HitRect(cx, ct, cellW, cb - ct, () -> {
                            YeQuickMenu.playClick();
                            if (!targetSet.remove(item.id)) targetSet.add(item.id);
                            ConfigManager.saveAsync();
                        }, null, null, 12));
                    }
                }
            }
            int rows = (items.size() + cols - 1) / cols;
            ry += rows * (cellH + gap) + 6;
        }
        g.disableScissor();
        maxScrollY = Math.max(0.0, (ry - startRy) - h);
    }

    private static void renderBlockMatrix(GuiGraphics g, int x, int y, int w, int h,
                                          int mx, int my, int accent, boolean mech, List<YeQuickMenu.HitRect> outHits) {
        String[] tabs = {"已选与常用", "矿石矿物", "容器与功能", "自然方块", "下界末地", "红石建筑", "全部方块"};
        int tx = x;
        for (int i = 0; i < tabs.length; i++) {
            final int tabIdx = i;
            int pw = YeHdFont.width(tabs[i], 8.0f, i == selectedBlockTab) + 12;
            boolean sel = (i == selectedBlockTab);
            AppleUiRenderer.drawRoundedRect(g, tx, y, pw, 16, mech ? 0 : 4, sel ? accent : AppleUiRenderer.bgCard());
            YeHdFont.drawCenteredInBox(g, tabs[i], tx, y, pw, 16, 8.0f, sel ? 0xFFFFFFFF : AppleUiRenderer.textSecondary(), false, sel);
            outHits.add(new YeQuickMenu.HitRect(tx, y, pw, 16, () -> {
                YeQuickMenu.playClick();
                selectedBlockTab = tabIdx;
                scrollY = 0.0;
            }, null, null, 12));
            tx += pw + 4;
        }

        String q = searchBuffer.trim().toLowerCase(Locale.ROOT);
        List<BlockItem> filtered = new ArrayList<>();
        for (BlockItem bi : cachedBlocks) {
            Block blk = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(bi.id));
            String zh = YeZhCnDict.blockName(blk);
            if (!q.isEmpty()) {
                if (!zh.toLowerCase(Locale.ROOT).contains(q) && !bi.id.toLowerCase(Locale.ROOT).contains(q)) continue;
            } else {
                if (selectedBlockTab == 0) {
                    if (!targetSet.contains(bi.id) && bi.tabGroup != 1 && bi.tabGroup != 2) continue;
                } else if (selectedBlockTab <= 5 && bi.tabGroup != selectedBlockTab) {
                    continue;
                }
            }
            filtered.add(new BlockItem(bi.id, zh, bi.icon, bi.tabGroup));
        }

        // 1-Click Select Current Category button on right of tab bar
        if (selectedBlockTab >= 1 && selectedBlockTab <= 5 && filtered.size() <= 220) {
            int baX = x + w - 62;
            AppleUiRenderer.drawRoundedRect(g, baX, y, 60, 16, mech ? 0 : 4, AppleUiRenderer.bgCardHover());
            YeHdFont.drawCenteredInBox(g, "全选本页", baX, y, 60, 16, 7.8f, accent, false, true);
            outHits.add(new YeQuickMenu.HitRect(baX, y, 60, 16, () -> {
                YeQuickMenu.playClick();
                for (BlockItem bi : filtered) targetSet.add(bi.id);
                ConfigManager.saveAsync();
            }, null, null, 12));
        }

        int listY = y + 20;
        int listH = h - 20;
        int cols = 4;
        int gap = 5;
        int cellW = (w - 8 - (cols - 1) * gap) / cols;
        int cellH = 22;

        g.enableScissor(x, listY, x + w, listY + listH);
        int startRy = listY - (int) Math.round(scrollY);

        for (int i = 0; i < filtered.size(); i++) {
            BlockItem bi = filtered.get(i);
            int cx = x + (i % cols) * (cellW + gap);
            int cy = startRy + (i / cols) * (cellH + gap);
            if (cy + cellH < listY || cy > listY + listH) continue;

            boolean sel = targetSet.contains(bi.id);
            boolean hov = mx >= cx && mx <= cx + cellW && my >= Math.max(listY, cy) && my <= Math.min(listY + listH, cy + cellH);
            AppleUiRenderer.drawElevatedCard(g, cx, cy, cellW, cellH, 5, sel ? 1f : 0f, hov ? 1f : 0f, accent);

            int textOff = 6;
            if (!bi.icon.isEmpty()) {
                var pose = g.pose();
                pose.pushMatrix();
                pose.translate(cx + 4, cy + 3);
                pose.scale(0.95f, 0.95f);
                g.renderItem(bi.icon, 0, 0);
                pose.popMatrix();
                textOff = 22;
            }
            String nm = YeHdFont.clipToWidth(bi.name, 8.0f, sel, cellW - textOff - 5);
            YeHdFont.draw(g, nm, cx + textOff, YeHdFont.vCenterY(cy, cellH, 8.0f), 8.0f,
                    sel ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, sel);

            int ct = Math.max(listY, cy), cb = Math.min(listY + listH, cy + cellH);
            if (cb > ct) {
                outHits.add(new YeQuickMenu.HitRect(cx, ct, cellW, cb - ct, () -> {
                    YeQuickMenu.playClick();
                    if (!targetSet.remove(bi.id)) targetSet.add(bi.id);
                    ConfigManager.saveAsync();
                }, null, null, 12));
            }
        }
        g.disableScissor();

        int rows = (filtered.size() + cols - 1) / cols;
        int totalH = rows * (cellH + gap);
        maxScrollY = Math.max(0.0, totalH - listH);
    }
}
