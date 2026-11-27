package com.ye.mod.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Rich Sound Presets for both Combat Hit Feedback & UI Button Clicks!
 * Includes Meow ("喵叫"), Windows/Source Error chord ("Error报错"), Mechanical Switch ("机械轴"),
 * Wooden Fish ("电子木鱼"), Crystal Chime ("紫晶"), Bubble ("气泡"), and Arcade Crit ("暴击").
 */
public final class YeSoundPresets {
    public enum SoundType {
        CLICK("清脆微动"),
        MECH_SWITCH("机械青轴"),
        BUBBLE("灵动气泡"),
        CRYSTAL("紫晶清鸣"),
        MEOW("可爱喵叫"),
        ERROR_BEEP("Error报错"),
        WOODEN_FISH("电子木鱼"),
        EXPERIENCE("经典叮声"),
        ARCADE_CRIT("街机重击"),
        MUTE("静音");

        public final String label;
        SoundType(String label) { this.label = label; }
    }

    private YeSoundPresets() {}

    public static void playUi(SoundType type, float volume) {
        if (type == null || type == SoundType.MUTE || volume <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        var sm = mc.getSoundManager();

        switch (type) {
            case CLICK -> sm.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.15f, volume * 0.65f));
            case MECH_SWITCH -> {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.85f, volume * 0.75f));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.55f, volume * 0.45f));
            }
            case BUBBLE -> sm.play(SimpleSoundInstance.forUI(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.35f, volume * 0.9f));
            case CRYSTAL -> sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.30f, volume * 0.85f));
            case MEOW -> sm.play(SimpleSoundInstance.forUI(SoundEvents.CAT_AMBIENT, 1.22f, volume * 0.75f));
            case ERROR_BEEP -> {
                // Authentic two-tone Windows / Source Engine "Error" buzz-chord!
                sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 0.59f, volume * 0.85f));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.53f, volume * 0.75f));
            }
            case WOODEN_FISH -> sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.88f, volume * 0.85f));
            case EXPERIENCE -> sm.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.35f, volume * 0.55f));
            case ARCADE_CRIT -> {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_CRIT, 1.25f, volume * 0.75f));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.65f, volume * 0.50f));
            }
            default -> {}
        }
    }
}
