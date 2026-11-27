package com.ye.mod.modules.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.ye.mod.core.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Manages 50 bundled 64x64 HD skins + user-provided local PNG skins under config/ye/skins/.
 */
public final class SkinManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ye-skins");
    private static final Map<String, SkinEntry> SKINS = new LinkedHashMap<>();

    public record SkinEntry(String id, String displayName, Identifier textureId) {}

    private static final String[][] BUNDLED_50 = {
            {"technoblade", "Technoblade 皇冠战猪"},
            {"ranboo", "Ranboo 双色末影"},
            {"philza", "Philza 翠绿死神"},
            {"grian", "Grian 红衣建筑师"},
            {"sapnap", "Sapnap 烈焰少年"},
            {"illumina", "Illumina 暗影速通"},
            {"purpled", "Purpled 紫晶战神"},
            {"wallibear", "Wallibear 冰熊战士"},
            {"hannahxxrose", "Hannah 玫瑰女武神"},
            {"gamerboy80", "Gamerboy80 墨镜刺客"},
            {"inteledits", "IntelEdits 极简蓝调"},
            {"xnestorio", "xNestorio 金眸猎手"},
            {"tapl", "TapL 苹果骑士"},
            {"fruitberries", "Fruitberries 翠果高手"},
            {"feinberg", "Feinberg 黄金智者"},
            {"couriway", "Couriway 圣光游侠"},
            {"petezahhutt", "PeteZahHutt 史莱姆王"},
            {"hbomb94", "HBomb 暗黑绅士"},
            {"punz", "Punz 白金卫衣"},
            {"krtzyy", "Krtzyy 紫焰斗士"},
            {"seapeekay", "Seapeekay 灵狐少年"},
            {"smajor1995", "Smajor 星彩礼服"},
            {"ldshadowlady", "LDShadowLady 樱粉魔女"},
            {"geminitay", "GeminiTay 森系精灵"},
            {"pearlescentmoon", "Pearl 月光少女"},
            {"mumbojumbo", "Mumbo 西装绅士"},
            {"goodtimeswithscar", "Scar 荒野游侠"},
            {"iskall85", "Iskall 机械义眼"},
            {"ethoslab", "Etho 暗部忍者"},
            {"bdoubleo100", "Bdubs 苔原旅人"},
            {"docm77", "Docm77 赛博半机械"},
            {"impulsesv", "Impulse 黑黄极客"},
            {"zombiecleo", "Cleo 幽魂女王"},
            {"smallishbeans", "Joel 神殿武士"},
            {"solidaritygaming", "Jimmy 金发警长"},
            {"inthelittlewood", "Martyn 森林猎人"},
            {"captainsparklez", "Sparklez 皇家舰长"},
            {"dantdm", "DanTDM 蓝钻博士"},
            {"sb737", "SB737 极地企鹅"},
            {"rekrap2", "Rekrap 暗夜礼帽"},
            {"clownpierce", "ClownPierce 死神小丑"},
            {"branzy", "Branzy 虚空紫客"},
            {"parrotx2", "Parrot 绯红羽客"},
            {"spokeishere", "Spoke 暗影特工"},
            {"flamefrags", "FlameFrags 业火剑圣"},
            {"sammygreen", "SammyGreen 翡翠潮人"},
            {"tommyinnit", "Tommy 红白少年"},
            {"tubbo", "Tubbo 翠蜂少年"},
            {"wilbursoot", "Wilbur 风衣诗人"},
            {"georgenotfound", "George 蓝白护目镜"}
    };

    private SkinManager() {}

    public static Path getSkinsDir() {
        return ConfigManager.getConfigDir().resolve("skins");
    }

    public static void init() {
        refreshSkins();
    }

    public static void refreshSkins() {
        SKINS.clear();
        for (String[] row : BUNDLED_50) {
            registerBundledPreset("preset:" + row[0], row[1], row[0]);
        }
        try {
            Files.createDirectories(getSkinsDir());
        } catch (IOException e) {
            LOGGER.warn("Failed to create skins directory", e);
            return;
        }

        try (Stream<Path> files = Files.list(getSkinsDir())) {
            files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .forEach(SkinManager::loadSkin);
        } catch (IOException e) {
            LOGGER.warn("Failed to scan skins directory", e);
        }
    }

    private static void registerBundledPreset(String id, String name, String slug) {
        Identifier texId = Identifier.fromNamespaceAndPath("ye-mod", "textures/skins/" + slug + ".png");
        SKINS.put(id, new SkinEntry(id, name, texId));
    }

    private static void loadSkin(Path path) {
        String fileName = path.getFileName().toString();
        String baseName = fileName.substring(0, fileName.length() - 4);
        String id = "skin:" + fileName;
        String safeSlug = baseName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        Identifier texId = Identifier.fromNamespaceAndPath("ye", "skins/" + safeSlug);

        try (InputStream in = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(in);
            DynamicTexture texture = new DynamicTexture(() -> "ye_skin_" + safeSlug, image);
            Minecraft.getInstance().getTextureManager().register(texId, texture);
            SKINS.put(id, new SkinEntry(id, baseName, texId));
        } catch (Exception e) {
            LOGGER.warn("Failed to load custom skin: {}", path, e);
        }
    }

    public static List<SkinEntry> getAllSkins() {
        return Collections.unmodifiableList(new ArrayList<>(SKINS.values()));
    }

    public static SkinEntry getSkin(String id) {
        if (id == null || id.isEmpty()) {
            return SKINS.isEmpty() ? null : SKINS.values().iterator().next();
        }
        SkinEntry entry = SKINS.get(id);
        if (entry == null && !SKINS.isEmpty()) {
            return SKINS.values().iterator().next();
        }
        return entry;
    }
}
