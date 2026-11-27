package com.ye.mod;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.FriendManager;
import com.ye.mod.core.KeyCombo;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.YeZhCnDict;
import com.ye.mod.modules.hud.HudElements;
import com.ye.mod.modules.player.FlightSpeedModule;
import com.ye.mod.modules.player.PlayerUiModule;
import com.ye.mod.modules.player.SmartAfkModule;
import com.ye.mod.modules.render.AccessoriesModule;
import com.ye.mod.modules.render.AntiLagModule;
import com.ye.mod.modules.render.BodyScaleModule;
import com.ye.mod.modules.render.CapeManager;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.render.CustomSkinModule;
import com.ye.mod.modules.render.DragonWingsModule;
import com.ye.mod.modules.render.EntityEspModule;
import com.ye.mod.modules.render.FlightModelModule;
import com.ye.mod.modules.render.LavaEspModule;
import com.ye.mod.modules.render.NoRenderModule;
import com.ye.mod.modules.render.SkinManager;
import com.ye.mod.modules.render.StandBuddyModule;
import com.ye.mod.modules.render.SwingSpeedModule;
import com.ye.mod.modules.render.TrajectoryPreviewModule;
import com.ye.mod.modules.system.BorderlessFullscreenModule;
import com.ye.mod.modules.system.ChatFilterModule;
import com.ye.mod.modules.system.PlayerStatsManager;
import com.ye.mod.modules.system.QuickChatModule;
import com.ye.mod.modules.visual.AmbienceModule;
import com.ye.mod.modules.visual.CameraTweaksModule;
import com.ye.mod.modules.visual.CinematicBarsModule;
import com.ye.mod.modules.visual.CrosshairModule;
import com.ye.mod.modules.visual.DamagePopupsModule;
import com.ye.mod.modules.visual.EnchantGlintModule;
import com.ye.mod.modules.visual.GlobalColorModule;
import com.ye.mod.modules.visual.HealthPlatesModule;
import com.ye.mod.modules.visual.HitCrosshairModule;
import com.ye.mod.modules.visual.HitFeedbackModule;
import com.ye.mod.modules.visual.IdleWallpaperModule;
import com.ye.mod.modules.visual.LowHpVignetteModule;
import com.ye.mod.modules.visual.MotionBlurModule;
import com.ye.mod.modules.visual.NoFovModule;
import com.ye.mod.modules.visual.ShaderPackModule;
import com.ye.mod.modules.visual.SinCityFilterModule;
import com.ye.mod.modules.visual.SoulAscendModule;
import com.ye.mod.modules.visual.SoulRingsModule;
import com.ye.mod.modules.visual.SpeedLinesModule;
import com.ye.mod.modules.visual.StepDecalsModule;
import com.ye.mod.modules.visual.VoxelShatterModule;
import com.ye.mod.modules.visual.ZoomModule;
import com.ye.mod.modules.world.AutoLogModule;
import com.ye.mod.modules.world.BlockEspModule;
import com.ye.mod.modules.world.BlockSearchModule;
import com.ye.mod.modules.world.LogoutGhostsModule;
import com.ye.mod.modules.world.PortalFinderModule;
import com.ye.mod.modules.world.StashRadarModule;
import com.ye.mod.modules.world.WthitModule;
import com.ye.mod.ui.YeMouseScreen;
import com.ye.mod.ui.YeQuickMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Client entrypoint for the Ye 1.21.11 Fabric mod (v1.7.3).
 */
public class YeClient implements ClientModInitializer {
    private static boolean prevMenuKeyDown = false;
    private static boolean prevMouseKeyDown = false;
    private static int accountCheckTicks = 0;
    private static final Map<Integer, Boolean> PREV_NAV_KEYS = new HashMap<>();

    @Override
    public void onInitializeClient() {
        YeZhCnDict.ensureInitAsync();

        // 1. 玩家模型 (PLAYER_MODEL)
        ModuleManager.register(new DragonWingsModule());
        ModuleManager.register(new FlightModelModule());
        ModuleManager.register(new AccessoriesModule());
        ModuleManager.register(new StandBuddyModule());
        ModuleManager.register(new CustomSkinModule());
        ModuleManager.register(new CustomCapeModule());
        ModuleManager.register(new BodyScaleModule());
        ModuleManager.register(new SwingSpeedModule());

        // 2. 视觉渲染 (VISUAL)
        ModuleManager.register(new GlobalColorModule());
        ModuleManager.register(new ShaderPackModule());
        ModuleManager.register(new EnchantGlintModule());
        ModuleManager.register(new MotionBlurModule());
        ModuleManager.register(new CameraTweaksModule());
        ModuleManager.register(new EntityEspModule());
        ModuleManager.register(new SoulRingsModule());
        ModuleManager.register(new AmbienceModule());
        ModuleManager.register(new SinCityFilterModule());
        ModuleManager.register(new CrosshairModule());
        ModuleManager.register(new HitCrosshairModule());
        ModuleManager.register(new HitFeedbackModule());
        ModuleManager.register(new StepDecalsModule());
        ModuleManager.register(new SpeedLinesModule());
        ModuleManager.register(new VoxelShatterModule());
        ModuleManager.register(new SoulAscendModule());
        ModuleManager.register(new IdleWallpaperModule());
        ModuleManager.register(new CinematicBarsModule());
        ModuleManager.register(new LowHpVignetteModule());
        ModuleManager.register(new ZoomModule());

        // 3. 特定功能 (UTILITY)
        ModuleManager.register(new PlayerUiModule());
        ModuleManager.register(new FlightSpeedModule());
        ModuleManager.register(new QuickChatModule());
        ModuleManager.register(new AutoLogModule());
        ModuleManager.register(new HealthPlatesModule());
        ModuleManager.register(new DamagePopupsModule());
        ModuleManager.register(new StashRadarModule());
        ModuleManager.register(new BlockEspModule());
        ModuleManager.register(new BlockSearchModule());
        ModuleManager.register(new PortalFinderModule());
        ModuleManager.register(new LavaEspModule());
        ModuleManager.register(new TrajectoryPreviewModule());
        ModuleManager.register(new LogoutGhostsModule());
        ModuleManager.register(new AntiLagModule());
        ModuleManager.register(new NoRenderModule());
        ModuleManager.register(new NoFovModule());
        ModuleManager.register(new BorderlessFullscreenModule());
        ModuleManager.register(new ChatFilterModule());
        ModuleManager.register(new SmartAfkModule());

        // 4. 独立 HUD 子模块板块 (Category.HUD)
        ModuleManager.register(new HudElements.PotionsHud());
        ModuleManager.register(new HudElements.CoordsHud());
        ModuleManager.register(new HudElements.SpeedHud());
        ModuleManager.register(new HudElements.TimeHud());
        ModuleManager.register(new HudElements.FpsPingHud());
        ModuleManager.register(new HudElements.ArmorHud());
        ModuleManager.register(new HudElements.PvpItemsHud());
        ModuleManager.register(new HudElements.InventoryHud());
        ModuleManager.register(new HudElements.NearbyDropsHud());
        ModuleManager.register(new HudElements.ModuleListHud());
        ModuleManager.register(new HudElements.PlayerViewHud());
        ModuleManager.register(new WthitModule());

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) {
                HitFeedbackModule hit = ModuleManager.get(HitFeedbackModule.class);
                if (hit != null) hit.onAttackEntity(entity);
                HitCrosshairModule hc = ModuleManager.get(HitCrosshairModule.class);
                if (hc != null) hc.triggerHit();
                DamagePopupsModule dp = ModuleManager.get(DamagePopupsModule.class);
                if (dp != null) dp.onAttack(entity);
                HealthPlatesModule hp = ModuleManager.get(HealthPlatesModule.class);
                if (hp != null) hp.onAttack(entity);
            }
            return InteractionResult.PASS;
        });

        String initialUser = "Player";
        try {
            if (Minecraft.getInstance() != null && Minecraft.getInstance().getUser() != null) {
                initialUser = Minecraft.getInstance().getUser().getName();
            }
        } catch (Throwable ignored) {}
        ConfigManager.initSystem(initialUser);
        FriendManager.init();
        PlayerStatsManager.init();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("ye")
                        .then(ClientCommandManager.literal("friend")
                                .executes(ctx -> {
                                    var list = FriendManager.getFriends();
                                    String msg = list.isEmpty() ? "§7当前好友列表为空" : ("§a好友列表 (" + list.size() + "): §f" + String.join(", ", list));
                                    ctx.getSource().sendFeedback(Component.literal("§b[Ye] " + msg));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String n = StringArgumentType.getString(ctx, "name");
                                                    FriendManager.addFriend(n);
                                                    ctx.getSource().sendFeedback(Component.literal("§b[Ye] §a已添加好友: §f" + n));
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String n = StringArgumentType.getString(ctx, "name");
                                                    FriendManager.removeFriend(n);
                                                    ctx.getSource().sendFeedback(Component.literal("§b[Ye] §e已移除好友: §f" + n));
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("clear")
                                        .executes(ctx -> {
                                            FriendManager.clearFriends();
                                            ctx.getSource().sendFeedback(Component.literal("§b[Ye] §7已清空好友列表"));
                                            return 1;
                                        })))));

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            CapeManager.init();
            SkinManager.init();
            ShaderPackModule.ensureBundledPacksExtracted();
            if (client.getUser() != null) {
                ConfigManager.checkAccountSwitch(client.getUser().getName());
            }
            ConfigManager.markRuntimeReady();
            BorderlessFullscreenModule bf = ModuleManager.get(BorderlessFullscreenModule.class);
            if (bf != null) {
                bf.applyOnStartupIfEnabled();
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ConfigManager.saveNow();
            PlayerStatsManager.saveStatsNow();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++accountCheckTicks >= 40) {
                accountCheckTicks = 0;
                String who = client.player != null
                        ? client.player.getGameProfile().name()
                        : (client.getUser() != null ? client.getUser().getName() : null);
                if (who != null) {
                    ConfigManager.checkAccountSwitch(who);
                }
            }

            if (client.getWindow() != null) {
                long handle = client.getWindow().handle();

                // Unconditionally track F9 & F10 state every tick so closing YeMouseScreen NEVER immediately reopens!
                int key = ConfigManager.getMenuKey();
                boolean menuDown = key > 0 && KeyCombo.isComboDown(handle, key);
                int mKey = ConfigManager.getMouseUnlockKey();
                boolean mouseDown = mKey > 0 && KeyCombo.isComboDown(handle, mKey);

                if (client.screen == null) {
                    if (menuDown && !prevMenuKeyDown) {
                        YeQuickMenu.toggleOpen();
                    }
                    if (mouseDown && !prevMouseKeyDown) {
                        YeQuickMenu.setOpen(true);
                        YeQuickMenu.playClick();
                        client.setScreen(new YeMouseScreen());
                    }

                    if (YeQuickMenu.isOpen()) {
                        pollNav(handle, GLFW.GLFW_KEY_UP, 0);
                        pollNav(handle, GLFW.GLFW_KEY_KP_8, 0);
                        pollNav(handle, GLFW.GLFW_KEY_DOWN, 1);
                        pollNav(handle, GLFW.GLFW_KEY_KP_2, 1);
                        pollNav(handle, GLFW.GLFW_KEY_LEFT, 2);
                        pollNav(handle, GLFW.GLFW_KEY_KP_4, 2);
                        pollNav(handle, GLFW.GLFW_KEY_RIGHT, 3);
                        pollNav(handle, GLFW.GLFW_KEY_KP_6, 3);
                        pollNav(handle, GLFW.GLFW_KEY_ENTER, 4);
                        pollNav(handle, GLFW.GLFW_KEY_KP_ENTER, 4);
                        pollNav(handle, GLFW.GLFW_KEY_BACKSPACE, 5);
                        pollNav(handle, GLFW.GLFW_KEY_SPACE, 6);
                        pollNav(handle, GLFW.GLFW_KEY_KP_ADD, 6);
                    } else {
                        PREV_NAV_KEYS.clear();
                    }
                } else {
                    PREV_NAV_KEYS.clear();
                }

                prevMenuKeyDown = menuDown;
                prevMouseKeyDown = mouseDown;
            }

            FriendManager.onClientTick();
            ModuleManager.onClientTick();
        });
    }

    private static void pollNav(long handle, int glfwKey, int action) {
        boolean down = GLFW.glfwGetKey(handle, glfwKey) == GLFW.GLFW_PRESS;
        boolean was = PREV_NAV_KEYS.getOrDefault(glfwKey, false);
        if (down && !was) {
            YeQuickMenu.onKeyNav(action);
        }
        PREV_NAV_KEYS.put(glfwKey, down);
    }
}
