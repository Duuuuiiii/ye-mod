package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sky Black Hole / Gravitational Singularity (#5) with event horizon core,
 * multi-band rotating accretion disk, and perpendicular Einstein lensing halo.
 */
public class BlackHoleSkyModule extends Module {
    public enum Theme {
        GARGANTUA("星际黑金", 0xFFFFF3C4, 0xFFF59E0B, 0xFF9A3412),
        VOID_PURPLE("虚空紫洞", 0xFFF5D0FE, 0xFFA855F7, 0xFF4C1D95),
        BLOOD_ECLIPSE("猩红日蚀", 0xFFFECACA, 0xFFEF4444, 0xFF7F1D1D);

        public final String label;
        final int innerCol;
        final int midCol;
        final int outerCol;
        Theme(String label, int innerCol, int midCol, int outerCol) {
            this.label = label;
            this.innerCol = innerCol;
            this.midCol = midCol;
            this.outerCol = outerCol;
        }
    }

    public final EnumSetting<Theme> theme = addSetting(new EnumSetting<>(
            "theme", "黑洞配色", "星际黑金 / 虚空紫洞 / 猩红日蚀", Theme.GARGANTUA
    ));

    public final DoubleSetting size = addSetting(new DoubleSetting(
            "size", "黑洞尺寸", "调节天空中黑洞与吸积盘的大小", 1.0, 0.5, 2.2, 0.1
    ));

    public final DoubleSetting spinSpeed = addSetting(new DoubleSetting(
            "spinSpeed", "旋转速度", "吸积盘引力流旋转速度", 1.0, 0.2, 3.0, 0.1
    ));

    public BlackHoleSkyModule() {
        super("black_hole_sky", "天空黑洞", "Black Hole", "天幕悬挂真实旋转吸积盘与引力透镜黑洞奇点", Category.VISUAL);
    }

    public void emitGizmos() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Vec3 center = cam.add(0.0, 68.0, -52.0);
        double s = size.get();
        double coreR = 7.5 * s;
        long now = System.currentTimeMillis();
        double spin = (now / 1000.0) * spinSpeed.get() * 1.4;
        Theme th = theme.get();

        // 1. Abyssal Event Horizon core (solid black layered cuboids)
        Gizmos.cuboid(new AABB(
                center.x - coreR, center.y - coreR * 0.85, center.z - coreR,
                center.x + coreR, center.y + coreR * 0.85, center.z + coreR),
                GizmoStyle.fill(0xFF010103));
        Gizmos.cuboid(new AABB(
                center.x - coreR * 0.82, center.y - coreR * 1.02, center.z - coreR * 0.82,
                center.x + coreR * 0.82, center.y + coreR * 1.02, center.z + coreR * 0.82),
                GizmoStyle.fill(0xFF010103));

        // 2. Tilted Multi-band Accretion Disk
        int segs = 40;
        drawTiltedRing(center, coreR * 1.32, th.innerCol, 4.0f, 0.35, spin, segs);
        drawTiltedRing(center, coreR * 1.75, th.midCol, 3.5f, 0.35, spin * 0.8, segs);
        drawTiltedRing(center, coreR * 2.25, th.outerCol, 2.5f, 0.35, spin * 0.55, segs);

        // Orbiting plasma streaks inside accretion disk
        for (int k = 0; k < 6; k++) {
            double a0 = spin * 1.6 + k * (Math.PI / 3.0);
            double r = coreR * (1.45 + (k % 3) * 0.28);
            Vec3 p0 = diskPoint(center, r, a0, 0.35);
            Vec3 p1 = diskPoint(center, r, a0 + 0.35, 0.35);
            Gizmos.line(p0, p1, th.innerCol, 3.2f);
        }

        // 3. Gravitational Lensing vertical halo wrapping over/under core
        for (int i = 0; i < segs; i++) {
            double a0 = (i / (double) segs) * Math.PI * 2.0;
            double a1 = ((i + 1) / (double) segs) * Math.PI * 2.0;
            double r = coreR * 1.24;
            Vec3 p0 = center.add(Math.cos(a0) * r, Math.sin(a0) * r, Math.sin(a0) * r * 0.25);
            Vec3 p1 = center.add(Math.cos(a1) * r, Math.sin(a1) * r, Math.sin(a1) * r * 0.25);
            Gizmos.line(p0, p1, th.midCol, 3.0f);
        }
    }

    private static void drawTiltedRing(Vec3 center, double r, int col, float width, double tilt, double phase, int segs) {
        for (int i = 0; i < segs; i++) {
            double a0 = phase + (i / (double) segs) * Math.PI * 2.0;
            double a1 = phase + ((i + 1) / (double) segs) * Math.PI * 2.0;
            Gizmos.line(diskPoint(center, r, a0, tilt), diskPoint(center, r, a1, tilt), col, width);
        }
    }

    private static Vec3 diskPoint(Vec3 c, double r, double ang, double tilt) {
        double x = Math.cos(ang) * r;
        double z = Math.sin(ang) * r;
        double y = z * Math.sin(tilt);
        double zRot = z * Math.cos(tilt);
        return c.add(x, y, zRot);
    }
}
