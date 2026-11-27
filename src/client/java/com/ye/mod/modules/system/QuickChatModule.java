package com.ye.mod.modules.system;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.core.setting.KeybindSetting;
import com.ye.mod.core.setting.StringSetting;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;

/**
 * Quick Chat / Command / Auto-Announcer Module (一键发言):
 * Provides 4 independent configurable message/command/whisper slots,
 * each with its own single/combo hotkey AND optional timed auto-send loop!
 */
public class QuickChatModule extends Module {
    public enum SlotIndex {
        SLOT_1("快捷栏 1"),
        SLOT_2("快捷栏 2"),
        SLOT_3("快捷栏 3"),
        SLOT_4("快捷栏 4");

        public final String label;
        SlotIndex(String label) { this.label = label; }
    }

    public static final class ChatSlot {
        public final StringSetting content;
        public final KeybindSetting hotkey;
        public final BoolSetting autoTimer;
        public final DoubleSetting intervalSec;
        public long lastSentMs = 0L;

        ChatSlot(StringSetting content, KeybindSetting hotkey, BoolSetting autoTimer, DoubleSetting intervalSec) {
            this.content = content;
            this.hotkey = hotkey;
            this.autoTimer = autoTimer;
            this.intervalSec = intervalSec;
        }
    }

    public final EnumSetting<SlotIndex> editingSlot = addSetting(new EnumSetting<>(
            "editingSlot", "当前编辑槽位", "选择要编辑指令/发言内容、快捷键或定时的槽位", SlotIndex.SLOT_1
    ).functionGroup());

    public final ChatSlot[] slots = new ChatSlot[4];

    public QuickChatModule() {
        super("quick_chat", "一键发言", "Quick Chat", "一键发送自定义指令、公屏发言或私信，支持多槽位独立快捷键与定时自动发送", Category.UTILITY);

        String[] defMsgs = {
                "/spawn",
                "GG!",
                "/msg Friend 你好",
                "/home"
        };
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            final SlotIndex si = SlotIndex.values()[i];
            String p = "s" + (i + 1);
            StringSetting txt = addSetting(new StringSetting(p + "_txt", "槽位" + (i + 1) + "·内容", "以 / 开头发送指令，否则发送聊天消息", defMsgs[i])
                    .visible(() -> editingSlot.get() == si).functionGroup());
            KeybindSetting key = addSetting(new KeybindSetting(p + "_key", "槽位" + (i + 1) + "·快捷键", "按下立即发送此条指令或消息", -1)
                    .visible(() -> editingSlot.get() == si).functionGroup());
            BoolSetting timer = addSetting(new BoolSetting(p + "_timer", "槽位" + (i + 1) + "·定时发送", "开启后每隔指定秒数自动循环发送", false)
                    .visible(() -> editingSlot.get() == si).functionGroup());
            DoubleSetting sec = addSetting(new DoubleSetting(p + "_sec", "槽位" + (i + 1) + "·间隔秒数", "定时自动发送间隔时间（秒）", 30.0, 2.0, 600.0, 1.0)
                    .visible(() -> editingSlot.get() == si && timer.get()).functionGroup());
            slots[idx] = new ChatSlot(txt, key, timer, sec);
        }
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;

        long now = System.currentTimeMillis();
        for (int i = 0; i < slots.length; i++) {
            ChatSlot s = slots[i];
            if (s.hotkey.consumePress()) {
                sendText(mc, s.content.get(), true);
                s.lastSentMs = now;
            } else if (s.autoTimer.get()) {
                long waitMs = Math.max(2000L, Math.round(s.intervalSec.get() * 1000.0));
                if (now - s.lastSentMs >= waitMs) {
                    sendText(mc, s.content.get(), false);
                    s.lastSentMs = now;
                }
            }
        }
    }

    public static void sendText(Minecraft mc, String raw, boolean showToast) {
        if (raw == null || raw.isBlank() || mc.player == null || mc.player.connection == null) return;
        String text = raw.trim();
        if (text.startsWith("/")) {
            mc.player.connection.sendCommand(text.substring(1));
        } else {
            mc.player.connection.sendChat(text);
        }
        if (showToast) {
            YeQuickMenu.showToast(ConfigManager.tr("已发送: ", "Sent: ") + text, ConfigManager.getAccentColor());
        }
    }
}
