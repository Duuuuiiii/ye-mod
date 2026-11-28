package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.StringSetting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particle Manager (粒子管理):
 * Controls global particle visibility, reduces explosion clutter, hides block break & potion particles,
 * or filters specific particles by keyword.
 */
public class ParticleManagerModule extends Module {
    public final BoolSetting disableAll = addSetting(new BoolSetting(
            "disableAll", "关闭所有粒子", "完全屏蔽客户端所有粒子生成", false
    ).functionGroup());

    public final BoolSetting reduceExplosions = addSetting(new BoolSetting(
            "reduceExplosions", "减少爆炸粒子", "大幅减少 TNT 与末影水晶爆炸烟雾粒子", true
    ).visible(() -> !disableAll.get()).functionGroup());

    public final DoubleSetting explosionKeepRatio = addSetting(new DoubleSetting(
            "explosionKeepRatio", "爆炸粒子保留比例", "0.0=完全隐藏，0.2=保留20%", 0.15, 0.0, 1.0, 0.05
    ).visible(() -> !disableAll.get() && reduceExplosions.get()).functionGroup());

    public final BoolSetting hideBlockBreak = addSetting(new BoolSetting(
            "hideBlockBreak", "关闭方块挖掘粒子", "屏蔽破坏方块时的碎屑粒子", false
    ).visible(() -> !disableAll.get()).functionGroup());

    public final BoolSetting hidePotions = addSetting(new BoolSetting(
            "hidePotions", "关闭药水粒子", "屏蔽药水气泡与状态效果粒子", false
    ).visible(() -> !disableAll.get()).functionGroup());

    public final StringSetting blockKeywords = addSetting(new StringSetting(
            "blockKeywords", "屏蔽粒子关键词", "逗号分隔粒子ID关键词(如 smoke,flame)", ""
    ).visible(() -> !disableAll.get()).functionGroup());

    public ParticleManagerModule() {
        super("particle_manager", "粒子管理", "Particles", "自由控制爆炸、挖掘、药水等各类粒子显示与精简", Category.VISUAL);
    }

    public boolean shouldBlockParticle(ParticleOptions opts) {
        if (!isEnabled() || opts == null) return false;
        if (disableAll.get()) return true;
        String id = String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(opts.getType()));
        if (hideBlockBreak.get() && (id.contains("block") || id.contains("dust"))) return true;
        if (hidePotions.get() && (id.contains("effect") || id.contains("entity_effect") || id.contains("witch"))) return true;
        if (reduceExplosions.get() && id.contains("explosion")) {
            return Math.random() > explosionKeepRatio.get();
        }
        String kw = blockKeywords.get().trim();
        if (!kw.isEmpty()) {
            for (String part : kw.split(",")) {
                String p = part.trim().toLowerCase();
                if (!p.isEmpty() && id.toLowerCase().contains(p)) return true;
            }
        }
        return false;
    }
}
