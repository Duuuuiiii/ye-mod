package com.ye.mod.modules.player;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/**
 * RusherHack-style AntiAFK + UnfocusedFPS:
 * - Micro-rotations/swings to prevent AFK kicks.
 * - Drops framerate limit to 10 FPS when Minecraft window is unfocused to save GPU/power.
 */
public class SmartAfkModule extends Module {
    public final BoolSetting antiAfkActions = addSetting(new BoolSetting(
            "antiAfkActions", "挂机防踢微动作", "每隔数秒轻微摆手并微调视角防止服务器踢出", true
    ));

    public final DoubleSetting intervalSeconds = addSetting(new DoubleSetting(
            "intervalSeconds", "动作间隔 (秒)", "每隔多少秒执行一次防踢动作", 18.0, 5.0, 60.0, 1.0
    ));

    public final BoolSetting unfocusedFpsLimit = addSetting(new BoolSetting(
            "unfocusedFpsLimit", "切屏后台降帧省电", "窗口失去焦点时自动将帧率降至 10 FPS，切回瞬间恢复", true
    ));

    private int tickCounter = 0;
    private int savedFpsLimit = -1;

    public SmartAfkModule() {
        super("smart_afk", "智能挂机省电", "Smart AFK", "挂机防踢微动作 + 切屏后台自动降帧省电", Category.PLAYER);
    }

    @Override
    protected void onDisable() {
        restoreFps();
        tickCounter = 0;
    }

    private void restoreFps() {
        if (savedFpsLimit > 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null) {
                mc.options.framerateLimit().set(savedFpsLimit);
            }
            savedFpsLimit = -1;
        }
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            if (unfocusedFpsLimit.get()) {
                if (!mc.isWindowActive()) {
                    if (savedFpsLimit < 0) {
                        savedFpsLimit = mc.options.framerateLimit().get();
                        mc.options.framerateLimit().set(10);
                    }
                } else {
                    restoreFps();
                }
            } else {
                restoreFps();
            }
        }

        if (antiAfkActions.get() && mc.player != null && mc.level != null && mc.screen == null) {
            tickCounter++;
            int intervalTicks = Math.max(40, (int) Math.round(intervalSeconds.get() * 20.0));
            if (tickCounter >= intervalTicks) {
                tickCounter = 0;
                mc.player.swing(InteractionHand.MAIN_HAND);
                mc.player.setYRot(mc.player.getYRot() + 0.4f);
            }
        }
    }
}
