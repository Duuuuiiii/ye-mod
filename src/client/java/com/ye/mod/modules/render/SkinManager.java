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
 * Manages 73 bundled 64x64 HD skins (23 Iconic Anime Characters + 50 PvP/Creator Classics)
 * + user-provided local PNG skins under config/ye/skins/.
 */
public final class SkinManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ye-skins");
    private static final Map<String, SkinEntry> SKINS = new LinkedHashMap<>();

    public record SkinEntry(String id, String displayName, Identifier textureId) {}

    private static final String[][] BUNDLED_SKINS = {
            // === 23 Featured Anime Character Skins ===
            {"anime_miku", "初音未来 · 苍绿歌姬"},
            {"anime_frieren", "芙莉莲 · 葬送魔法使"},
            {"anime_hutao", "胡桃 · 往生堂主"},
            {"anime_furina", "芙宁娜 · 水神礼装"},
            {"anime_raiden", "雷电将军 · 鸣神影"},
            {"anime_firefly", "流萤 · 星核猎手"},
            {"anime_kafka", "卡芙卡 · 支配剧幕"},
            {"anime_hoshinoai", "星野爱 · 传奇偶像"},
            {"anime_bocchi", "后藤一里 · 孤独摇滚"},
            {"anime_rem", "蕾姆 · 蓝发女仆"},
            {"anime_nezuko", "灶门祢豆子 · 鬼灭"},
            {"anime_gojo", "五条悟 · 六眼无下限"},
            {"anime_jotaro", "空条承太郎 · 替身使者"},
            {"anime_dio", "迪奥 DIO · 世界"},
            {"anime_makima", "玛奇玛 · 支配恶魔"},
            {"anime_power", "帕瓦 · 血之魔人"},
            {"anime_megumin", "惠惠 · 爆裂红魔族"},
            {"anime_zerotwo", "02 · 鹤望兰"},
            {"anime_asuna", "亚丝娜 · 闪光剑士"},
            {"anime_kirito", "桐人 · 黑衣剑士"},
            {"anime_saber", "阿尔托莉雅 · 圣剑王"},
            {"anime_kurumi", "时崎狂三 · 梦魇精灵"},
            {"anime_rei", "绫波丽 · 零号机"},
            {"anime_asuka", "明日香 · 贰号机"},
            {"dream", "Dream · 经典笑脸"},
            // === Chinese Classic Creators (国内经典主播) ===
            {"cn_zimin", "籽岷 · 经典蓝衫"},
            {"cn_xiaoshen", "小神先生 · 经典皮肤"},
            {"cn_muyu", "木鱼 · 经典像素"},
            {"cn_xiaoben", "小本 · 经典皮肤"},
            {"cn_xiatiany", "夏天Y · 经典皮肤"},
            {"cn_youran", "悠然小天 · 经典皮肤"},
            {"cn_kage", "卡哥 · 经典皮肤"},
            {"cn_dazui", "大嘴 · 经典皮肤"},
            {"cn_dahai", "大海 · 经典皮肤"},
            // === 50 Classic PvP & Creator Skins ===
            {"technoblade", "皇冠战猪 Technoblade"},
            {"ranboo", "双色末影 Ranboo"},
            {"philza", "翠绿死神 Philza"},
            {"grian", "红衣建筑师 Grian"},
            {"sapnap", "烈焰少年 Sapnap"},
            {"illumina", "暗影速通 Illumina"},
            {"purpled", "紫晶战神 Purpled"},
            {"wallibear", "冰熊战士 Wallibear"},
            {"hannahxxrose", "玫瑰女武神 Hannah"},
            {"gamerboy80", "墨镜刺客 Gamerboy80"},
            {"inteledits", "极简蓝调 IntelEdits"},
            {"xnestorio", "金眸猎手 xNestorio"},
            {"tapl", "苹果骑士 TapL"},
            {"fruitberries", "翠果高手 Fruitberries"},
            {"feinberg", "黄金智者 Feinberg"},
            {"couriway", "圣光游侠 Couriway"},
            {"petezahhutt", "史莱姆王 Pete"},
            {"hbomb94", "暗黑绅士 HBomb"},
            {"punz", "白金卫衣 Punz"},
            {"krtzyy", "紫焰斗士 Krtzyy"},
            {"seapeekay", "灵狐少年 Seapeekay"},
            {"smajor1995", "星彩礼服 Smajor"},
            {"ldshadowlady", "樱粉魔女 ShadowLady"},
            {"geminitay", "森系精灵 GeminiTay"},
            {"pearlescentmoon", "月光少女 Pearl"},
            {"mumbojumbo", "西装绅士 Mumbo"},
            {"goodtimeswithscar", "荒野游侠 Scar"},
            {"iskall85", "机械义眼 Iskall"},
            {"ethoslab", "暗部忍者 Etho"},
            {"bdoubleo100", "苔原旅人 Bdubs"},
            {"docm77", "赛博半机械 Docm77"},
            {"impulsesv", "黑黄极客 Impulse"},
            {"zombiecleo", "幽魂女王 Cleo"},
            {"smallishbeans", "神殿武士 Joel"},
            {"solidaritygaming", "金发警长 Jimmy"},
            {"inthelittlewood", "森林猎人 Martyn"},
            {"captainsparklez", "皇家舰长 Sparklez"},
            {"dantdm", "蓝钻博士 DanTDM"},
            {"sb737", "极地企鹅 SB737"},
            {"rekrap2", "暗夜礼帽 Rekrap"},
            {"clownpierce", "死神小丑 ClownPierce"},
            {"branzy", "虚空紫客 Branzy"},
            {"parrotx2", "绯红羽客 Parrot"},
            {"spokeishere", "暗影特工 Spoke"},
            {"flamefrags", "业火剑圣 FlameFrags"},
            {"sammygreen", "翡翠潮人 SammyGreen"},
            {"tommyinnit", "红白少年 Tommy"},
            {"tubbo", "翠蜂少年 Tubbo"},
            {"wilbursoot", "风衣诗人 Wilbur"},
            {"georgenotfound", "蓝白护目镜 George"}
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
        for (String[] row : BUNDLED_SKINS) {
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

    /**
     * Online skin lookup & local PNG cache (supports Mojang player names & LittleSkin Chinese character keywords).
     */
    public static void fetchOnlineSkinAsync(String query, java.util.function.Consumer<String> onResult) {
        if (query == null || query.isBlank()) return;
        String clean = query.trim();
        Thread t = new Thread(() -> {
            try {
                Files.createDirectories(getSkinsDir());
                String safeName = clean.replaceAll("[\\\\/:*?\"<>|]", "_");
                Path outPath = getSkinsDir().resolve(safeName + ".png");

                // 1) Try LittleSkin keyword search first (great for Chinese anime / creator names)
                String enc = java.net.URLEncoder.encode(clean, java.nio.charset.StandardCharsets.UTF_8);
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(6)).build();
                java.net.http.HttpRequest req1 = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://littleskin.cn/skinlib/list?filter=skin&keyword=" + enc + "&sort=likes&page=1"))
                        .header("Accept", "application/json")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .timeout(java.time.Duration.ofSeconds(8)).GET().build();
                var res1 = client.send(req1, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (res1.statusCode() == 200) {
                    var json = com.google.gson.JsonParser.parseString(res1.body()).getAsJsonObject();
                    if (json.has("data") && json.getAsJsonArray("data").size() > 0) {
                        int tid = json.getAsJsonArray("data").get(0).getAsJsonObject().get("tid").getAsInt();
                        var infoReq = java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create("https://littleskin.cn/skinlib/info/" + tid))
                                .timeout(java.time.Duration.ofSeconds(8)).GET().build();
                        var infoRes = client.send(infoReq, java.net.http.HttpResponse.BodyHandlers.ofString());
                        var infoObj = com.google.gson.JsonParser.parseString(infoRes.body()).getAsJsonObject();
                        if (infoObj.has("hash")) {
                            String hash = infoObj.get("hash").getAsString();
                            var pngReq = java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create("https://littleskin.cn/textures/" + hash))
                                    .timeout(java.time.Duration.ofSeconds(10)).GET().build();
                            var pngRes = client.send(pngReq, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                            if (pngRes.statusCode() == 200 && pngRes.body().length > 128) {
                                Files.write(outPath, pngRes.body());
                                Minecraft.getInstance().execute(() -> {
                                    refreshSkins();
                                    if (onResult != null) onResult.accept("skin:" + safeName + ".png");
                                });
                                return;
                            }
                        }
                    }
                }

                // 2) Fallback to Minotar Mojang official username skin
                var mReq = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://minotar.net/skin/" + enc))
                        .timeout(java.time.Duration.ofSeconds(8)).GET().build();
                var mRes = client.send(mReq, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                if (mRes.statusCode() == 200 && mRes.body().length > 128) {
                    Files.write(outPath, mRes.body());
                    Minecraft.getInstance().execute(() -> {
                        refreshSkins();
                        if (onResult != null) onResult.accept("skin:" + safeName + ".png");
                    });
                    return;
                }
            } catch (Exception e) {
                LOGGER.warn("Online skin fetch failed for {}", clean, e);
            }
            Minecraft.getInstance().execute(() -> {
                if (onResult != null) onResult.accept(null);
            });
        }, "ye-skin-fetch");
        t.setDaemon(true);
        t.start();
    }
}
