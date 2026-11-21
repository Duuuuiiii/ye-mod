package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.EnumSetting;
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
 * Local client-side custom skin replacement with Classic (Wide) / Slim arm model support.
 */
public class CustomSkinModule extends Module {
    public enum SkinModelMode {
        KEEP,
        CLASSIC,
        SLIM
    }

    public final StringSetting selectedSkin = addSetting(new StringSetting(
            "selectedSkin", "当前皮肤", "选择本地导入的皮肤 PNG", ""
    ));

    public final EnumSetting<SkinModelMode> modelMode = addSetting(new EnumSetting<>(
            "modelMode", "手臂模型", "KEEP=保持原样, CLASSIC=经典粗臂, SLIM=纤细细臂", SkinModelMode.KEEP
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅本地玩家", "只替换你自己的皮肤，不影响其他玩家", true
    ));

    public CustomSkinModule() {
        super("custom_skin", "自定义皮肤", "Custom Skin", "本地替换玩家皮肤贴图与手臂模型，支持导入本地 PNG", Category.RENDER);
        addSetting(new ActionSetting("importSkin", "导入本地皮肤 PNG...", "打开文件选择器导入 64x64 皮肤贴图", this::openFilePicker));
        addSetting(new ActionSetting("openFolder", "打开皮肤文件夹", "在资源管理器中打开 config/ye/skins 目录", this::openSkinsFolder));
        addSetting(new ActionSetting("refreshSkins", "刷新皮肤列表", "重新扫描 skins 文件夹中的 PNG 文件", SkinManager::refreshSkins));
    }

    public Identifier getActiveSkinTexture() {
        SkinManager.SkinEntry entry = SkinManager.getSkin(selectedSkin.get());
        return entry != null ? entry.textureId() : null;
    }

    private void openSkinsFolder() {
        try {
            Files.createDirectories(SkinManager.getSkinsDir());
        } catch (Exception ignored) {}
        Util.getPlatform().openPath(SkinManager.getSkinsDir());
    }

    private void openFilePicker() {
        Thread picker = new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();

                Files.createDirectories(SkinManager.getSkinsDir());
                String result = TinyFileDialogs.tinyfd_openFileDialog(
                        "选择皮肤 PNG 贴图 (64x64)",
                        SkinManager.getSkinsDir().toAbsolutePath() + "/",
                        filters,
                        "PNG 图像 (*.png)",
                        false
                );

                if (result != null && !result.isBlank()) {
                    Path src = Path.of(result);
                    if (Files.isRegularFile(src)) {
                        Path dest = SkinManager.getSkinsDir().resolve(src.getFileName().toString());
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                        String newId = "skin:" + dest.getFileName().toString();
                        Minecraft.getInstance().execute(() -> {
                            SkinManager.refreshSkins();
                            selectedSkin.set(newId);
                            setEnabled(true);
                        });
                    }
                }
            } catch (Exception ignored) {
            }
        }, "ye-skin-picker");
        picker.setDaemon(true);
        picker.start();
    }
}
