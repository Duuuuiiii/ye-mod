# AGENTS.md — ye-mod (Minecraft 1.21.11 Fabric Client Mod)

## 1. Project Overview
- **Mod ID / Name**: `ye-mod` (`Ye`)
- **Target**: Minecraft `1.21.11` · Fabric Loader `0.20.1` · Fabric API `0.141.6+1.21.11`
- **Current Version**: `1.7.4` (see `gradle.properties` and `ConfigManager.MOD_VERSION`)
- **Repository**: `https://github.com/Duuuuiiii/ye-mod`

---

## 2. User Preferences & Mandatory Rules
1. **Confirm Before Coding**: Always clarify ambiguous requirements and confirm the implementation plan with the user **before** making substantive code changes.
2. **Storage & Naming**:
   - Avoid writing project files, builds, or temp outputs to `C:` drive (except deploying the final mod JAR to the game's `mods` folder).
   - Output built JARs to: `D:\Desktop\yemp\Antigravity\ye-mod\ye-mod-<version>.jar`
   - Deploy to game mods folder (removing older `ye-mod-*.jar`): `C:\Users\35263\AppData\Roaming\.minecraft\versions\c3cu\mods\ye-mod-<version>.jar`
   - Always increment version cleanly (e.g., `1.7.4` -> `1.7.5`).
3. **UI & Copywriting Style**:
   - Never include boastful words like `"智能"`, `"高清"`, `"高帧数"` in module/setting names or descriptions.
   - Never put keybind/operation hints (e.g., `"按 F10"`, `"右键..."`) inside module descriptions — operational instructions belong strictly in the Settings -> Tutorial modal (`YeQuickMenu`).
   - All numeric sliders support double-click inline keyboard editing; all reset actions require confirmation.
4. **Shared Config**:
   - A single client/version folder shares one default config (`main.json` via `ConfigManager.SHARED_PROFILE`), regardless of player account ID, unless the user manually creates/switches profiles.

---

## 3. Codebase Architecture (`src/client/java/com/ye/mod/`)
- `YeClient.java`: Client entrypoint, module registration, tick/attack/command hooks.
- `core/`:
  - `ConfigManager.java`: Unified JSON config, `HudSlot` 9-zone + mutual-edge magnetic snapping & anti-overlap, UI style (`SILKY` / `MECHANICAL`), UI themes (`DARK` / `LIGHT` / `GAUSSIAN_GLASS`), `uiBlurStrength`, `dropTierHigh` / `dropTierMid`, `statPlayerKills` / `statPlayerDeaths`.
  - `Module.java` & `ModuleManager.java`: Module lifecycle, `KeyCombo` (single keys, mouse side buttons, Ctrl/Shift/Alt combos), `presetCycleKey`.
  - `FriendManager.java`: Persistent friend whitelist (`friends.json`) + fixed `4.5m` middle-click toggle.
  - `YeZhCnDict.java`: Loads official Mojang `zh_cn.json` from `.minecraft/assets/` so Chinese item/block/entity/effect names work even on English client language.
  - `YeSoundPresets.java`: `UiSoundType` (4 UI sounds) & `CombatSoundType` (normal/crit hit sounds).
  - `setting/`: `BoolSetting`, `DoubleSetting`, `EnumSetting` (supports `.gridPicker()`), `ColorSetting`, `KeybindSetting`, `StringSetting`, `ActionSetting`.
- `modules/`:
  - `player/`: `PlayerUiModule`, `FlightSpeedModule`
  - `render/`: `DragonWingsModule`, `FlightModelModule`, `AccessoriesModule`, `StandBuddyModule`, `CustomSkinModule`, `SkinManager` (bundled + LittleSkin/Minotar online downloader), `CustomCapeModule`, `BodyScaleModule`, `SwingSpeedModule`, `EntityEspModule`, `TrajectoryPreviewModule`, `LavaEspModule`, `AntiLagModule`, `NoRenderModule`
  - `visual/`: `GlobalColorModule` (`同步色`), `ShaderPackModule` (bundles Photon/Complementary/MakeUp in `assets/ye-mod/shaderpacks/` + Iris reflection), `EnchantGlintModule`, `MotionBlurModule`, `CameraTweaksModule`, `AmbienceModule`, `SinCityFilterModule`, `SoulRingsModule`, `StepDecalsModule`, `CrosshairModule`, `HitCrosshairModule`, `HitFeedbackModule`, `HealthPlatesModule`, `DamagePopupsModule`, `ZoomModule`, etc.
  - `world/`: `StashRadarModule` (`储存透视`), `BlockEspModule` (`方块透视`), `BlockSearchModule` (`方块搜索`), `AutoLogModule` (`自动下线`), `PortalFinderModule`, `WthitModule`, `LogoutGhostsModule`
  - `hud/`: `HudElementModule` & `HudElements` (`坐标`, `移动速度`, `时间`, `帧率与延迟`, `装备耐久`, `物资计数`, `背包预览`, `物品列表`, `功能列表`, `玩家预览`, `储存数量`)
  - `utility/`: `StatsModule` (`统计`)
  - `system/`: `BorderlessFullscreenModule`, `QuickChatModule`, `ChatFilterModule`, `SmartAfkModule`
- `ui/`:
  - `YeQuickMenu.java`: Dual-mode UI (compact bottom-right Arrow UI vs 82% adaptive Mouse UI), minimized `Yer` bar, RusherHack-style right-edge `功能 | HUD` switcher, `StashRadar` hierarchy panel, `AutoLog` condition cards, multi-column `gridPicker` tables.
  - `YeMouseScreen.java`: Mouse canvas, background blur layer (HUDs rendered above blur; blur & menu hidden while dragging HUDs).
  - `AppleUiRenderer.java`: Dual-persona (`SILKY` / `MECHANICAL`) renderer, `drawModuleIcon`, `drawResetCircleIcon`, HSV picker.
  - `YeHdFont.java`: Anti-aliased font renderer (`Built-in` vs `Client` font).
  - `YePreviewRenderer.java`: Live 3D/effect preview (only shown when module is enabled).
  - `YePickerModal.java`: Categorized full-entity & full-block search matrix modal.
  - `YeGiantCreativeScreen.java`: Widescreen creative inventory (`12×8` to `40×20`).
- `mixin/`: Mixins for `Camera`, `Gui`, `LevelRenderer`, `SkyRenderer`, `InventoryScreen`, `MouseHandler`, `GameRenderer`, `RenderTypes`, `SoundEngine`, etc.

---

## 4. Build, Deploy & Git Commands (Windows PowerShell)
```powershell
cd D:\Desktop\yemp\Antigravity\ye-mod\project
$env:GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=10793 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10793"
.\gradlew.bat build --no-daemon
```
Deploy after successful build:
```powershell
Copy-Item "build\libs\ye-mod-<ver>.jar" "D:\Desktop\yemp\Antigravity\ye-mod\ye-mod-<ver>.jar" -Force
$modsDir = "C:\Users\35263\AppData\Roaming\.minecraft\versions\c3cu\mods"
Get-ChildItem $modsDir -Filter "ye-mod-*.jar" | Remove-Item -Force -ErrorAction SilentlyContinue
Copy-Item "build\libs\ye-mod-<ver>.jar" "$modsDir\ye-mod-<ver>.jar" -Force
```
