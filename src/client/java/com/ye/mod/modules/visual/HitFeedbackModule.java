package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.YeSoundPresets;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Combat Hit Shockwave FX + Independent Normal Hit Sound & Critical Hit Sound Overrides!
 */
public class HitFeedbackModule extends Module {
    public enum HitPreset {
        STARBURST("星芒闪耀", 0x38BDF8, 0xFFFFFF),
        CRIMSON_NOVA("绯红冲击", 0xEF4444, 0xFCA5A5),
        HOLY_GOLD("圣金辉耀", 0xFACC15, 0xFEF08A),
        VOID_PULSE("虚空震荡", 0xA855F7, 0x22D3EE);

        public final String label;
        public final int outerRgb;
        public final int coreRgb;

        HitPreset(String label, int outerRgb, int coreRgb) {
            this.label = label;
            this.outerRgb = outerRgb;
            this.coreRgb = coreRgb;
        }
    }

    public record HitPulse(Vec3 pos, long startMs, float rotOffset) {}

    public final BoolSetting showVisualFx = addSetting(new BoolSetting(
            "showVisualFx", "启用视觉光效", "命中目标时渲染多层扩散星芒冲击波", true
    ).functionGroup());

    public final EnumSetting<HitPreset> preset = addSetting(new EnumSetting<>(
            "preset", "视觉预设", "星芒闪耀 / 绯红冲击 / 圣金辉耀 / 虚空震荡", HitPreset.STARBURST
    ).visible(showVisualFx::get).functionGroup());

    public final EnumSetting<YeSoundPresets.CombatSoundType> normalHitSound = addSetting(new EnumSetting<>(
            "hitSoundType", "普通攻击音效", "普通攻击命中生物时的声音",
            YeSoundPresets.CombatSoundType.EXPERIENCE
    ).functionGroup().gridPicker());

    public final EnumSetting<YeSoundPresets.CombatSoundType> critHitSound = addSetting(new EnumSetting<>(
            "critSoundType", "暴击命中音效", "跳劈暴击命中生物时的声音",
            YeSoundPresets.CombatSoundType.ARCADE_CRIT
    ).functionGroup().gridPicker());

    public final BoolSetting replaceVanillaSound = addSetting(new BoolSetting(
            "replaceVanillaSound", "替换原版打击声", "使用自定义打击/暴击音效时静音原版挥砍肉击声", true
    ).functionGroup());

    public final DoubleSetting soundVolume = addSetting(new DoubleSetting(
            "soundVolume", "打击音量", "调节普通攻击与暴击音效音量", 0.85, 0.0, 1.50, 0.05
    ).functionGroup());

    public final DoubleSetting soundPitch = addSetting(new DoubleSetting(
            "soundPitch", "音效音调", "调节打击音效清脆高低音调", 1.0, 0.6, 1.6, 0.05
    ).functionGroup());

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "特效大小", "命中特效扩散尺寸", 1.0, 0.5, 2.0, 0.1
    ).visible(showVisualFx::get));

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "跟随同步色", "打击冲击波颜色跟随同步色模块", false
    ).visible(showVisualFx::get));

    private final List<HitPulse> pulses = new CopyOnWriteArrayList<>();
    private long lastAttackMs = 0L;

    public HitFeedbackModule() {
        super("hit_feedback", "打击特效", "Hit FX", "多层星芒扩散冲击波，支持独立修改普通攻击音效与暴击音效", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        pulses.clear();
    }

    public boolean shouldMuteVanillaAttackSound(String soundPath) {
        if (!isEnabled() || !replaceVanillaSound.get()) return false;
        if (System.currentTimeMillis() - lastAttackMs > 300L) return false;
        if (soundPath.contains("player.attack.crit")) {
            return critHitSound.get() != YeSoundPresets.CombatSoundType.VANILLA;
        }
        if (soundPath.contains("player.attack.")) {
            return normalHitSound.get() != YeSoundPresets.CombatSoundType.VANILLA;
        }
        return false;
    }

    public void onAttackEntity(Entity target) {
        if (!isEnabled() || target == null) return;
        long now = System.currentTimeMillis();
        lastAttackMs = now;

        if (showVisualFx.get()) {
            pulses.add(new HitPulse(target.position().add(0, target.getBbHeight() * 0.56, 0), now, (now % 360)));
            while (pulses.size() > 16) pulses.remove(0);
        }

        boolean isCrit = isLikelyCriticalHit();
        YeSoundPresets.CombatSoundType chosen = isCrit ? critHitSound.get() : normalHitSound.get();
        float pitch = soundPitch.get().floatValue() * (isCrit ? 1.08f : 1.0f);
        YeSoundPresets.playCombat(chosen, soundVolume.get().floatValue(), pitch);
    }

    private static boolean isLikelyCriticalHit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return mc.player.getAttackStrengthScale(0.5f) > 0.9f
                && mc.player.fallDistance > 0.0f
                && !mc.player.onGround()
                && !mc.player.onClimbable()
                && !mc.player.isInWater()
                && !mc.player.hasEffect(MobEffects.BLINDNESS)
                && !mc.player.isPassenger()
                && !mc.player.isSprinting();
    }

    public List<HitPulse> getPulses() {
        return pulses;
    }
}
