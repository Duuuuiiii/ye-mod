package com.ye.mod.modules.system;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;

/**
 * Filters noisy chat categories (advancements, deaths, all player chat, join/leave, muted players)
 * while PlayerStatsManager continues recording stats & logs in the background.
 */
public class ChatFilterModule extends Module {
    public final BoolSetting hideAdvancements = addSetting(new BoolSetting(
            "hideAdvancements", "屏蔽玩家成就/进度", "过滤聊天栏里的“xxx 达成了进度/完成了挑战”消息", true
    ));

    public final BoolSetting hideDeaths = addSetting(new BoolSetting(
            "hideDeaths", "屏蔽玩家死亡信息", "过滤聊天栏里的玩家击杀与死亡播报（后台仍会记录统计）", true
    ));

    public final BoolSetting hidePlayerChat = addSetting(new BoolSetting(
            "hidePlayerChat", "屏蔽全部玩家发言", "隐藏所有玩家的公屏发言（后台仍会记录统计与日志）", false
    ));

    public final BoolSetting hideJoinLeave = addSetting(new BoolSetting(
            "hideJoinLeave", "屏蔽进出服务器提示", "过滤“xxx 加入了/退出了游戏”提示", false
    ));

    public final BoolSetting enableMuteList = addSetting(new BoolSetting(
            "enableMuteList", "启用黑名单屏蔽特定玩家", "屏蔽在「玩家统计」页拉黑的玩家的发言", true
    ));

    public ChatFilterModule() {
        super("chat_filter", "聊天过滤", "Chat Filter", "分别隐藏成就/死亡/玩家发言/进出提示，支持黑名单屏蔽特定玩家", Category.SYSTEM);
        addSetting(new ActionSetting("openLogs", "打开聊天与击杀日志文件夹", "查看本地保存的 chat_log.txt 与 kill_log.txt", PlayerStatsManager::openLogsFolder));
        addSetting(new ActionSetting("clearStats", "清空当前服务器玩家统计", "重置当前服务器记录的发言、击杀与死亡次数", PlayerStatsManager::clearCurrentStats));
    }

    public boolean shouldBlock(PlayerStatsManager.ParsedMessage parsed) {
        if (!isEnabled() || parsed == null) return false;

        return switch (parsed.kind()) {
            case ADVANCEMENT -> hideAdvancements.get();
            case DEATH -> hideDeaths.get();
            case JOIN_LEAVE -> hideJoinLeave.get();
            case PLAYER_CHAT -> hidePlayerChat.get()
                    || (enableMuteList.get() && PlayerStatsManager.isMuted(parsed.primaryPlayer()));
            case OTHER -> false;
        };
    }
}
