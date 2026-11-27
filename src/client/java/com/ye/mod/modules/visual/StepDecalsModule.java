package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Crisp 3D Glowing Voxel/Block Matrix Footprints (足迹):
 * Replaces blurry 2D circle textures with razor-sharp, layered 3D glowing pixel-blocks
 * (Cyber Matrix Tiles, Molten Magma Embers, Frost Crystal Prisms, Golden Jade Seals)!
 */
public class StepDecalsModule extends Module {
    public enum Style {
        CYBER_MATRIX("赛博方格", 0xFF22D3EE, 0xFF0EA5E9),
        MAGMA_EMBER("熔岩方块", 0xFFF97316, 0xFFFACC15),
        FROST_CRYSTAL("冰晶棱块", 0xFF7DD3FC, 0xFFF8FAFC),
        GOLD_SEAL("鎏金方印", 0xFFFACC15, 0xFFF59E0B);

        public final String label;
        public final int outerArgb;
        public final int coreArgb;

        Style(String label, int outerArgb, int coreArgb) {
            this.label = label;
            this.outerArgb = outerArgb;
            this.coreArgb = coreArgb;
        }
    }

    public record Decal(double x, double y, double z, float yaw, boolean leftFoot, long spawnMs) {}

    public final EnumSetting<Style> style = addSetting(new EnumSetting<>(
            "style", "样式", "赛博方格 / 熔岩方块 / 冰晶棱块 / 鎏金方印", Style.CYBER_MATRIX
    ));

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "同步全局色彩", "立体方块足迹颜色跟随全局色彩模块", false
    ));

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "大小", "地面立体方块足迹尺寸", 0.42, 0.25, 0.85, 0.05
    ));

    private final List<Decal> decals = new ArrayList<>();
    private double lastX = Double.NaN, lastZ = Double.NaN;
    private boolean nextLeftFoot = true;

    public StepDecalsModule() {
        super("step_decals", "足迹", "Footsteps", "行走时脚下生成清晰立体的发光方块矩阵印记", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        synchronized (decals) {
            decals.clear();
        }
        lastX = Double.NaN;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        synchronized (decals) {
            Iterator<Decal> it = decals.iterator();
            while (it.hasNext()) {
                if (now - it.next().spawnMs > 2400L) it.remove();
            }
        }

        if (!mc.player.onGround()) return;
        double px = mc.player.getX();
        double py = mc.player.getY() + 0.015;
        double pz = mc.player.getZ();

        if (Double.isNaN(lastX) || Math.hypot(px - lastX, pz - lastZ) >= 0.72) {
            lastX = px;
            lastZ = pz;
            float yaw = mc.player.getYRot();
            double rad = Math.toRadians(yaw);
            double sideSign = nextLeftFoot ? 0.16 : -0.16;
            nextLeftFoot = !nextLeftFoot;
            double fx = px + Math.cos(rad) * sideSign;
            double fz = pz + Math.sin(rad) * sideSign;
            synchronized (decals) {
                decals.add(new Decal(fx, py, fz, yaw, !nextLeftFoot, now));
                while (decals.size() > 42) decals.remove(0);
            }
        }
    }

    public List<Decal> snapshotDecals() {
        synchronized (decals) {
            return decals.isEmpty() ? List.of() : new ArrayList<>(decals);
        }
    }
}
