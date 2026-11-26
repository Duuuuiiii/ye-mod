package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Step Decals (#8): walking on ground leaves temporary glowing lotus,
 * frost crack, lava scorch, or cyber rune decals on the surface.
 */
public class StepDecalsModule extends Module {
    public enum Style {
        GOLDEN_LOTUS("步步生莲"),
        FROST_CRACK("碎冰裂纹"),
        LAVA_SCORCH("熔岩焦痕"),
        CYBER_RUNE("赛博符文");

        public final String label;
        Style(String label) { this.label = label; }
    }

    private record Decal(double x, double y, double z, long spawnMs) {}

    public final EnumSetting<Style> style = addSetting(new EnumSetting<>(
            "style", "足迹样式", "步步生莲 / 碎冰裂纹 / 熔岩焦痕 / 赛博符文", Style.GOLDEN_LOTUS
    ));

    private final List<Decal> decals = new ArrayList<>();
    private double lastX = Double.NaN, lastZ = Double.NaN;

    public StepDecalsModule() {
        super("step_decals", "步步生莲足迹", "Step Decals", "走过的地面临时浮现发光莲花/碎冰/熔岩/符文贴花", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        decals.clear();
        lastX = Double.NaN;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        Iterator<Decal> it = decals.iterator();
        while (it.hasNext()) {
            if (now - it.next().spawnMs > 2600L) it.remove();
        }

        if (!mc.player.onGround()) return;
        double px = mc.player.getX();
        double py = mc.player.getY() + 0.02;
        double pz = mc.player.getZ();

        if (Double.isNaN(lastX) || Math.hypot(px - lastX, pz - lastZ) >= 0.72) {
            lastX = px;
            lastZ = pz;
            decals.add(new Decal(px, py, pz, now));
            if (decals.size() > 48) decals.remove(0);
        }
    }

    public void emitGizmos() {
        if (!isEnabled() || decals.isEmpty()) return;
        long now = System.currentTimeMillis();
        Style st = style.get();

        for (Decal d : decals) {
            float t = (now - d.spawnMs) / 2600.0f;
            int a = Math.max(10, Math.round((1.0f - t) * 215.0f));
            double r = 0.42 + t * 0.12;

            switch (st) {
                case GOLDEN_LOTUS -> {
                    int petalCol = (a << 24) | 0x00FACC15;
                    int coreCol = (a << 24) | 0x00F472B6;
                    double spin = (now / 600.0) + d.x;
                    for (int i = 0; i < 8; i++) {
                        double a0 = spin + i * (Math.PI / 4.0);
                        double a1 = a0 + (Math.PI / 4.0);
                        Gizmos.line(new Vec3(d.x + Math.cos(a0) * r, d.y, d.z + Math.sin(a0) * r),
                                new Vec3(d.x + Math.cos(a1) * r, d.y, d.z + Math.sin(a1) * r), petalCol, 2.2f);
                        Gizmos.line(new Vec3(d.x, d.y, d.z),
                                new Vec3(d.x + Math.cos(a0) * r * 0.85, d.y, d.z + Math.sin(a0) * r * 0.85), coreCol, 1.8f);
                    }
                }
                case FROST_CRACK -> {
                    int iceCol = (a << 24) | 0x0067E8F9;
                    for (int i = 0; i < 6; i++) {
                        double ang = i * (Math.PI / 3.0) + (d.x % 1.0);
                        Gizmos.line(new Vec3(d.x, d.y, d.z),
                                new Vec3(d.x + Math.cos(ang) * r * 1.1, d.y, d.z + Math.sin(ang) * r * 1.1), iceCol, 2.2f);
                    }
                }
                case LAVA_SCORCH -> {
                    int fill = ((a / 2) << 24) | 0x00EA580C;
                    int stroke = (a << 24) | 0x00FACC15;
                    Gizmos.cuboid(new AABB(d.x - 0.25, d.y - 0.01, d.z - 0.25, d.x + 0.25, d.y + 0.01, d.z + 0.25),
                            GizmoStyle.strokeAndFill(stroke, 2.0f, fill));
                }
                case CYBER_RUNE -> {
                    int cy = (a << 24) | 0x0022D3EE;
                    Gizmos.cuboid(new AABB(d.x - r * 0.7, d.y - 0.01, d.z - r * 0.7, d.x + r * 0.7, d.y + 0.01, d.z + r * 0.7),
                            GizmoStyle.stroke(cy, 2.0f));
                }
            }
        }
    }
}
