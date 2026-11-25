package com.ye.mod;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.CapeManager;
import com.ye.mod.modules.render.AntiLagModule;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.render.DragonWingsModule;
import com.ye.mod.modules.render.FlightModelModule;
import com.ye.mod.modules.render.LavaEspModule;
import com.ye.mod.modules.visual.AmbienceModule;
import com.ye.mod.modules.render.CustomSkinModule;
import com.ye.mod.modules.render.NoRenderModule;
import com.ye.mod.modules.render.SkinManager;
import com.ye.mod.modules.system.BorderlessFullscreenModule;
import com.ye.mod.modules.system.ChatFilterModule;
import com.ye.mod.modules.system.PlayerStatsManager;
import com.ye.mod.modules.visual.HudModule;
import com.ye.mod.modules.visual.NoFovModule;
import com.ye.mod.ui.YeMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint for the Ye 1.21.11 Fabric utility mod.
 */
public class YeClient implements ClientModInitializer {
    private static boolean prevMenuKeyDown = false;

    @Override
    public void onInitializeClient() {
        ModuleManager.register(new CustomCapeModule());
        ModuleManager.register(new CustomSkinModule());
        ModuleManager.register(new AntiLagModule());
        ModuleManager.register(new LavaEspModule());
        ModuleManager.register(new DragonWingsModule());
        ModuleManager.register(new FlightModelModule());
        ModuleManager.register(new AmbienceModule());
        ModuleManager.register(new NoRenderModule());
        ModuleManager.register(new NoFovModule());
        ModuleManager.register(new BorderlessFullscreenModule());
        ModuleManager.register(new ChatFilterModule());
        ModuleManager.register(new HudModule());

        ConfigManager.load();
        PlayerStatsManager.init();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            CapeManager.init();
            SkinManager.init();
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
            if (client.screen == null && client.getWindow() != null) {
                int key = ConfigManager.getMenuKey();
                if (key > 0) {
                    boolean down = GLFW.glfwGetKey(client.getWindow().handle(), key) == GLFW.GLFW_PRESS;
                    if (down && !prevMenuKeyDown) {
                        client.setScreen(new YeMenuScreen());
                    }
                    prevMenuKeyDown = down;
                }
            } else {
                prevMenuKeyDown = false;
            }
            ModuleManager.onClientTick();
        });
    }
}
