package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 256x256 High-Resolution Ground Step Decals (no wireframe lines).
 */
public class StepDecalsModule extends Module {
    public enum Style {
        LAVA_SCORCH("熔岩焦痕", 0xFFF97316),
        CYBER_RUNE("赛博矩阵", 0xFF22D3EE),
        VOID_ASTROLABE("虚空星盘", 0xFFA855F7),
        STAR_SIGIL("星芒法阵", 0xFFFACC15);

        public final String label;
        public final int previewRgb;
        Style(String label, int previewRgb) {
            this.label = label;
            this.previewRgb = previewRgb;
        }
    }

    public record Decal(double x, double y, double z, float yaw, long spawnMs) {}

    public final EnumSetting<Style> style = addSetting(new EnumSetting<>(
            "style", "样式", "熔岩焦痕 / 赛博矩阵 / 虚空星盘 / 星芒法阵", Style.CYBER_RUNE
    ));

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "大小", "地面足迹阵纹尺寸", 0.65, 0.35, 1.30, 0.05
    ));

    private final List<Decal> decals = new CopyOnWriteArrayList<>();
    private double lastX = Double.NaN, lastZ = Double.NaN;

    public StepDecalsModule() {
        super("step_decals", "足迹", "Step Decals", "行走时地面浮现 256×256 高清发光印记", Category.VISUAL);
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
            Decal d = it.next();
            if (now - d.spawnMs > 2600L) decals.remove(d);
        }

        if (!mc.player.onGround()) return;
        double px = mc.player.getX();
        double py = mc.player.getY() + 0.025;
        double pz = mc.player.getZ();

        if (Double.isNaN(lastX) || Math.hypot(px - lastX, pz - lastZ) >= 0.78) {
            lastX = px;
            lastZ = pz;
            decals.add(new Decal(px, py, pz, mc.player.getYRot(), now));
            while (decals.size() > 36) decals.remove(0);
        }
    }

    public List<Decal> getDecals() {
        return decals;
    }
}
