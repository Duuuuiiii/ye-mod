package com.ye.mod;

import com.mojang.blaze3d.platform.InputConstants;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.CapeManager;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.render.NoRenderModule;
import com.ye.mod.modules.system.BorderlessFullscreenModule;
import com.ye.mod.modules.visual.HudModule;
import com.ye.mod.modules.visual.NoFovModule;
import com.ye.mod.ui.YeMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint for the Ye 1.21.11 Fabric utility mod.
 */
public class YeClient implements ClientModInitializer {
    private static KeyMapping openMenuKey;

    @Override
    public void onInitializeClient() {
        // Register modular features
        ModuleManager.register(new CustomCapeModule());
        ModuleManager.register(new NoRenderModule());
        ModuleManager.register(new NoFovModule());
        ModuleManager.register(new BorderlessFullscreenModule());
        ModuleManager.register(new HudModule());

        // Load saved configuration
        ConfigManager.load();

        // Register Right-Shift menu keybinding
        KeyMapping.Category yeCategory = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ye", "general"));
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ye.gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                yeCategory
        ));

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            CapeManager.init();
            BorderlessFullscreenModule bf = ModuleManager.get(BorderlessFullscreenModule.class);
            if (bf != null) {
                bf.applyOnStartupIfEnabled();
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.saveNow());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new YeMenuScreen());
                }
            }
            ModuleManager.onClientTick();
        });
    }
}
