package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.core.setting.KeybindSetting;
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

    private static boolean extractedOnce = false;
    private boolean shaderActive = false;

    public final EnumSetting<BuiltInShader> pack = addSetting(new EnumSetting<>(
            "pack", "选择光影", "挑选内置或已加载的光影预设", BuiltInShader.PHOTON
    ).onChange(p -> {
        if (isEnabled() && shaderActive) {
            applyIrisShader(p, true);
        }
    }).functionGroup());

    public final BoolSetting autoApplyOnEnable = addSetting(new BoolSetting(
            "autoApplyOnEnable", "开启模块即加载", "默认关闭：开启模块后需手动启用或按独立光影键加载", false
    ).functionGroup());

    public final KeybindSetting shaderToggleKey = addSetting(new KeybindSetting(
            "shaderToggleKey", "独立光影开关键", "单独一键开启或关闭光影渲染", -1
    ).functionGroup());

    public ShaderPackModule() {
        super("shader_pack", "光影", "Shaders", "管理光影预设与 Iris 光影加载切换", Category.VISUAL);

        addSetting(new ActionSetting("toggleShaderNow", "启用 / 关闭当前光影", "立即加载或卸载当前所选光影", () -> {
            if (!isEnabled()) setEnabled(true);
            if (shaderActive) {
                disableIrisShaders();
                shaderActive = false;
                YeQuickMenu.showToast(ConfigManager.tr("已关闭光影", "Shaders Disabled"), 0xFF94A3B8);
            } else {
                shaderActive = true;
                applyIrisShader(pack.get(), true);
            }
        }).functionGroup());

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
        ensureBundledPacksExtracted();
        if (autoApplyOnEnable.get()) {
            shaderActive = true;
            applyIrisShader(pack.get(), true);
        }
    }

    @Override
    protected void onDisable() {
        if (shaderActive) {
            shaderActive = false;
            disableIrisShaders();
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (shaderToggleKey.consumePress()) {
            if (shaderActive) {
                shaderActive = false;
                disableIrisShaders();
                YeQuickMenu.showToast(ConfigManager.tr("已关闭光影", "Shaders Disabled"), 0xFF94A3B8);
            } else {
                shaderActive = true;
                applyIrisShader(pack.get(), true);
            }
        }
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
