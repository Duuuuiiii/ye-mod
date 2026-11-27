package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.ui.YeQuickMenu;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Built-in Shaderpack Manager (光影):
 * Bundles Photon v1.4, Complementary Reimagined, and MakeUp UltraFast directly inside the mod JAR,
 * extracts them automatically into shaderpacks/, and drives Iris Shaders with 1-click / 1-key switching!
 */
public class ShaderPackModule extends Module {
    public enum BuiltInShader {
        PHOTON("Photon 大气光影", "photon_v1.4.zip"),
        COMPLEMENTARY("Complementary 重塑", "ComplementaryReimagined_r5.10.zip"),
        MAKEUP_FAST("MakeUp 极速竞技", "MakeUp-UltraFast-9.6a.zip");

        public final String label;
        public final String zipFileName;

        BuiltInShader(String label, String zipFileName) {
            this.label = label;
            this.zipFileName = zipFileName;
        }
    }

    public final EnumSetting<BuiltInShader> pack = addSetting(new EnumSetting<>(
            "pack", "内置光影", "Photon / Complementary / MakeUp 极速光影", BuiltInShader.PHOTON
    ).onChange(p -> {
        if (isEnabled()) {
            applyIrisShader(p, true);
        }
    }).functionGroup());

    private static boolean extractedOnce = false;

    public ShaderPackModule() {
        super("shader_pack", "光影", "Shaders", "内置 Photon、Complementary 与 MakeUp 三款光影包，联动 Iris 一键切换", Category.VISUAL);

        addSetting(new ActionSetting("openIrisGui", "打开光影详细设置", "打开 Iris 原生光影参数与本地光影列表界面", () -> {
            ensureBundledPacksExtracted();
            if (!openIrisScreen()) {
                YeQuickMenu.showToast(ConfigManager.tr("未检测到 Iris 模组", "Iris mod not found"), 0xFFEF4444);
            }
        }).functionGroup());

        addSetting(new ActionSetting("openShaderDir", "打开光影文件夹", "打开客户端 shaderpacks 目录放入更多光影包", () -> {
            Path dir = resolveShaderpacksDir();
            try { Files.createDirectories(dir); } catch (Exception ignored) {}
            Util.getPlatform().openPath(dir);
        }).functionGroup());
    }

    public static Path resolveShaderpacksDir() {
        try {
            Class<?> irisCls = Class.forName("net.irisshaders.iris.Iris");
            Method m = irisCls.getMethod("getShaderpacksDirectory");
            Object p = m.invoke(null);
            if (p instanceof Path path) return path;
        } catch (Throwable ignored) {}
        return FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
    }

    public static void ensureBundledPacksExtracted() {
        if (extractedOnce) return;
        extractedOnce = true;
        Path dir = resolveShaderpacksDir();
        try {
            Files.createDirectories(dir);
            for (BuiltInShader bs : BuiltInShader.values()) {
                Path target = dir.resolve(bs.zipFileName);
                if (!Files.exists(target) || Files.size(target) < 1024) {
                    String resPath = "/assets/ye-mod/shaderpacks/" + bs.zipFileName;
                    try (InputStream in = ShaderPackModule.class.getResourceAsStream(resPath)) {
                        if (in != null) {
                            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onEnable() {
        applyIrisShader(pack.get(), true);
    }

    @Override
    protected void onDisable() {
        disableIrisShaders();
    }

    public static boolean isIrisPresent() {
        try {
            Class.forName("net.irisshaders.iris.Iris");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void applyIrisShader(BuiltInShader shader, boolean toast) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        ensureBundledPacksExtracted();
        try {
            Class<?> irisCls = Class.forName("net.irisshaders.iris.Iris");
            Method getCfg = irisCls.getMethod("getIrisConfig");
            Object cfg = getCfg.invoke(null);
            if (cfg == null) return;

            Class<?> cfgCls = cfg.getClass();
            cfgCls.getMethod("setShaderPackName", String.class).invoke(cfg, shader.zipFileName);
            cfgCls.getMethod("setShadersEnabled", boolean.class).invoke(cfg, true);
            cfgCls.getMethod("save").invoke(cfg);
            irisCls.getMethod("reload").invoke(null);
            if (toast) {
                YeQuickMenu.showToast(ConfigManager.tr("已启用光影: ", "Loaded Shader: ") + shader.label, ConfigManager.getAccentColor());
            }
        } catch (Throwable t) {
            if (toast) {
                YeQuickMenu.showToast(ConfigManager.tr("切换光影失败，请确认已安装 Iris", "Iris reload failed"), 0xFFEF4444);
            }
        }
    }

    private void disableIrisShaders() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        try {
            Class<?> irisCls = Class.forName("net.irisshaders.iris.Iris");
            Object cfg = irisCls.getMethod("getIrisConfig").invoke(null);
            if (cfg == null) return;
            cfg.getClass().getMethod("setShadersEnabled", boolean.class).invoke(cfg, false);
            cfg.getClass().getMethod("save").invoke(cfg);
            irisCls.getMethod("reload").invoke(null);
        } catch (Throwable ignored) {}
    }

    private static boolean openIrisScreen() {
        try {
            Minecraft mc = Minecraft.getInstance();
            Class<?> scrCls = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
            Constructor<?> ctor = scrCls.getConstructor(Screen.class);
            Screen irisScreen = (Screen) ctor.newInstance(mc.screen);
            mc.setScreen(irisScreen);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
