package com.ye.mod.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Separated Sound Presets:
 * - UiSoundType: Strictly the 4 user-approved UI button sounds (清脆微动 / 机械青轴 / 电子木鱼 / 静音)
 * - CombatSoundType: Rich customizable normal-hit & critical-hit combat sounds (喵叫 / Error报错 / 电子木鱼 / 紫晶清鸣 / 灵动气泡 / 经典叮声 / 街机重击 / 原版默认 / 静音)
 */
public final class YeSoundPresets {
    public enum UiSoundType {
        CLICK("清脆微动"),
        MECH_SWITCH("机械青轴"),
        WOODEN_FISH("电子木鱼"),
        MUTE("静音");

        public final String label;
        UiSoundType(String label) { this.label = label; }
    }

    public enum CombatSoundType {
        VANILLA("原版默认"),
        MEOW("可爱喵叫"),
        ERROR_BEEP("Error报错"),
        WOODEN_FISH("电子木鱼"),
        CRYSTAL("紫晶清鸣"),
        BUBBLE("灵动气泡"),
        EXPERIENCE("经典叮声"),
        ARCADE_CRIT("街机重击"),
        ANVIL_DING("清脆铁砧"),
        MUTE("静音");

        public final String label;
        CombatSoundType(String label) { this.label = label; }
    }

    private YeSoundPresets() {}

    public static void playUi(UiSoundType type, float volume) {
        if (type == null || type == UiSoundType.MUTE || volume <= 0.005f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        var sm = mc.getSoundManager();

        switch (type) {
            case CLICK -> sm.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.15f, volume * 0.65f));
            case MECH_SWITCH -> {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.85f, volume * 0.75f));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.55f, volume * 0.45f));
            }
            case WOODEN_FISH -> sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.88f, volume * 0.85f));
            default -> {}
        }
    }

    public static void playCombat(CombatSoundType type, float volume, float pitchBoost) {
        if (type == null || type == CombatSoundType.VANILLA || type == CombatSoundType.MUTE || volume <= 0.005f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        var sm = mc.getSoundManager();
        float p = Math.max(0.5f, Math.min(2.0f, pitchBoost));

        switch (type) {
            case MEOW -> sm.play(SimpleSoundInstance.forUI(SoundEvents.CAT_AMBIENT, 1.18f * p, volume * 0.80f));
            case ERROR_BEEP -> {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 0.59f * p, volume * 0.85f));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.53f * p, volume * 0.75f));
            }
            case WOODEN_FISH -> sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.88f * p, volume * 0.88f));
            case CRYSTAL -> sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.30f * p, volume * 0.90f));
            case BUBBLE -> sm.play(SimpleSoundInstance.forUI(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.35f * p, volume * 0.90f));
            case EXPERIENCE -> sm.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.32f * p, volume * 0.60f));
            case ARCADE_CRIT -> {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_CRIT, 1.25f * p, volume * 0.80f));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.65f * p, volume * 0.55f));
            }
            case ANVIL_DING -> sm.play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 1.15f * p, volume * 0.65f));
            default -> {}
        }
    }
}
