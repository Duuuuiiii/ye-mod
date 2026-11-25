package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * RusherHack-style Logout Spots: remembers where nearby players disconnected
 * and renders a wireframe ghost + name/coordinate tag.
 */
public class LogoutGhostsModule extends Module {
    public final BoolSetting showCoords = addSetting(new BoolSetting(
            "showCoords", "显示掉线坐标", "在掉线幽灵头顶显示玩家名与 XYZ 坐标", true
    ));

    private record TrackedPlayer(UUID uuid, String name, AABB box, Vec3 pos, String dimId) {}
    private record LogoutSpot(String name, AABB box, Vec3 pos, String dimId, long timestamp) {}

    private final Map<UUID, TrackedPlayer> lastSeen = new HashMap<>();
    private final Map<UUID, LogoutSpot> spots = new HashMap<>();
    private String lastDim = "";

    public LogoutGhostsModule() {
        super("logout_ghosts", "掉线幽灵标记", "Logout Ghosts", "记录视野内离线玩家的最后位置，在原地留下 3D 线框幽灵与坐标", Category.WORLD);
        addSetting(new ActionSetting("clearSpots", "清空掉线标记", "清除当前保存的所有玩家掉线位置", spots::clear));
    }

    @Override
    protected void onDisable() {
        lastSeen.clear();
        spots.clear();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            lastSeen.clear();
            return;
        }

        String dimId = mc.level.dimension().identifier().toString();
        if (!dimId.equals(lastDim)) {
            lastSeen.clear();
            lastDim = dimId;
        }

        Map<UUID, TrackedPlayer> current = new HashMap<>();
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player) continue;
            UUID id = p.getUUID();
            String name = p.getGameProfile().name();
            current.put(id, new TrackedPlayer(id, name, p.getBoundingBox(), p.position(), dimId));
            // Remove logout spot if player reconnected
            spots.remove(id);
        }

        // Any player who was seen last tick, is no longer in level, AND is no longer in tab list -> logged out!
        if (mc.getConnection() != null) {
            Iterator<Map.Entry<UUID, TrackedPlayer>> it = lastSeen.entrySet().iterator();
            while (it.hasNext()) {
                TrackedPlayer prev = it.next().getValue();
                if (!current.containsKey(prev.uuid) && mc.getConnection().getPlayerInfo(prev.uuid) == null) {
                    spots.put(prev.uuid, new LogoutSpot(prev.name, prev.box, prev.pos, prev.dimId, System.currentTimeMillis()));
                }
            }
        }

        lastSeen.clear();
        lastSeen.putAll(current);
    }

    public void emitGizmos() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        String dimId = mc.level.dimension().identifier().toString();

        for (LogoutSpot spot : spots.values()) {
            if (!spot.dimId.equals(dimId)) continue;
            Gizmos.cuboid(spot.box, GizmoStyle.strokeAndFill(0xFFF43F5E, 2.0f, 0x33F43F5E)).setAlwaysOnTop();
            if (showCoords.get()) {
                String label = spot.name + String.format(" [%d, %d, %d]",
                        Math.round(spot.pos.x), Math.round(spot.pos.y), Math.round(spot.pos.z));
                Gizmos.billboardText(label, spot.pos.add(0, 2.3, 0),
                        net.minecraft.gizmos.TextGizmo.Style.whiteAndCentered().withScale(0.65f)).setAlwaysOnTop();
            }
        }
    }
}
