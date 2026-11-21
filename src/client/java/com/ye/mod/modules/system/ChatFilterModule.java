package com.ye.mod.modules.system;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Filters noisy server chat messages (advancements, death messages, join/leave).
 */
public class ChatFilterModule extends Module {
    public final BoolSetting hideAdvancements = addSetting(new BoolSetting(
            "hideAdvancements", "屏蔽玩家成就/进度", "过滤聊天栏里的“xxx 达成了进度/完成了挑战”消息", true
    ));

    public final BoolSetting hideDeaths = addSetting(new BoolSetting(
            "hideDeaths", "屏蔽玩家死亡信息", "过滤聊天栏里的玩家击杀与死亡播报", true
    ));

    public final BoolSetting hideJoinLeave = addSetting(new BoolSetting(
            "hideJoinLeave", "屏蔽进出服务器提示", "过滤“xxx 加入了/退出了游戏”提示", false
    ));

    public ChatFilterModule() {
        super("chat_filter", "聊天过滤", "Chat Filter", "屏蔽聊天栏玩家成就、死亡播报与进出提示", Category.SYSTEM);
    }

    public boolean shouldBlock(Component message) {
        if (!isEnabled() || message == null) return false;
        return matchesRecursive(message);
    }

    private boolean matchesRecursive(Component comp) {
        if (comp.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            if (hideAdvancements.get() && key.startsWith("chat.type.advancement.")) {
                return true;
            }
            if (hideDeaths.get() && key.startsWith("death.")) {
                return true;
            }
            if (hideJoinLeave.get() && (key.equals("multiplayer.player.joined")
                    || key.equals("multiplayer.player.joined.renamed")
                    || key.equals("multiplayer.player.left"))) {
                return true;
            }
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component sub && matchesRecursive(sub)) {
                    return true;
                }
            }
        }

        for (Component sibling : comp.getSiblings()) {
            if (matchesRecursive(sibling)) {
                return true;
            }
        }
        return false;
    }
}
