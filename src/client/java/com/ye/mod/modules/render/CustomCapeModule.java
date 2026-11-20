package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Local client-side custom cape renderer with built-in presets and PNG import.
 */
public class CustomCapeModule extends Module {
    public final StringSetting selectedCape = addSetting(new StringSetting(
            "selectedCape", "当前披风", "选择预设披风或本地自定义 PNG 披风", "preset:3c3u"
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅本地玩家", "只替换你自己的披风，不影响其他玩家", true
    ));

    public final BoolSetting applyToElytra = addSetting(new BoolSetting(
            "applyToElytra", "同步应用到鞘翅", "装备鞘翅时也使用当前披风贴图", true
    ));

    public CustomCapeModule() {
        super("custom_cape", "自定义披风", "Custom Cape", "本地渲染自定义披风，支持预设切换与导入本地 PNG", Category.RENDER);

        addSetting(new ActionSetting("importCape", "导入本地 PNG 披风...", "打开文件选择器导入自定义披风贴图", this::openFilePicker));
        addSetting(new ActionSetting("openFolder", "打开披风文件夹", "在资源管理器中打开 config/ye/capes 目录", this::openCapesFolder));
        addSetting(new ActionSetting("refreshCapes", "刷新自定义披风", "重新扫描 capes 文件夹中的 PNG 文件", CapeManager::refreshCustomCapes));
    }

    public Identifier getActiveCapeTexture() {
        CapeManager.CapeEntry entry = CapeManager.getCape(selectedCape.get());
        return entry != null ? entry.textureId() : null;
    }

    private void openCapesFolder() {
        ConfigManager.ensureDirectories();
        Util.getPlatform().openPath(ConfigManager.getCapesDir());
    }

    private void openFilePicker() {
        Thread picker = new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();

                String result = TinyFileDialogs.tinyfd_openFileDialog(
                        "选择披风 PNG 贴图 (64x32)",
                        ConfigManager.getCapesDir().toAbsolutePath() + "/",
                        filters,
                        "PNG 图像 (*.png)",
                        false
                );

                if (result != null && !result.isBlank()) {
                    Path src = Path.of(result);
                    if (Files.isRegularFile(src)) {
                        ConfigManager.ensureDirectories();
                        Path dest = ConfigManager.getCapesDir().resolve(src.getFileName().toString());
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                        String newId = "custom:" + dest.getFileName().toString();
                        Minecraft.getInstance().execute(() -> {
                            CapeManager.refreshCustomCapes();
                            selectedCape.set(newId);
                            setEnabled(true);
                        });
                    }
                }
            } catch (Exception ignored) {
            }
        }, "ye-cape-picker");
        picker.setDaemon(true);
        picker.start();
    }
}
