// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.settings.model

import java.util.*

enum class BooleanSetting(
    private val file: String,
    private val section: String,
    private val key: String,
    private val defaultValue: Boolean
) : AbstractBooleanSetting {
    // These entries have the same names and order as in C++, just for consistency.
    MAIN_SKIP_IPL(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "SkipIPL", true),
    MAIN_DSP_HLE(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "DSPHLE", true),
    MAIN_FASTMEM(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "Fastmem", true),
    MAIN_PAGE_TABLE_FASTMEM(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "PageTableFastmem", true),
    MAIN_FASTMEM_ARENA(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "FastmemArena", true),
    MAIN_LARGE_ENTRY_POINTS_MAP(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "LargeEntryPointsMap", true),
    MAIN_CPU_THREAD(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "CPUThread", true),
    MAIN_SYNC_ON_SKIP_IDLE(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "SyncOnSkipIdle",
        true
    ),
    MAIN_ENABLE_CHEATS(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "EnableCheats", false),
    MAIN_OVERRIDE_REGION_SETTINGS(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "OverrideRegionSettings",
        false
    ),
    MAIN_AUDIO_FILL_GAPS(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "AudioFillGaps", true),
    MAIN_AUDIO_PRESERVE_PITCH(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "AudioPreservePitch", false),
    MAIN_BBA_XLINK_CHAT_OSD(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "BBA_XLINK_CHAT_OSD",
        false
    ),
    MAIN_ADAPTER_RUMBLE_0(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "AdapterRumble0", true),
    MAIN_ADAPTER_RUMBLE_1(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "AdapterRumble1", true),
    MAIN_ADAPTER_RUMBLE_2(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "AdapterRumble2", true),
    MAIN_ADAPTER_RUMBLE_3(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "AdapterRumble3", true),
    MAIN_SIMULATE_KONGA_0(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "SimulateKonga0",
        false
    ),
    MAIN_SIMULATE_KONGA_1(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "SimulateKonga1",
        false
    ),
    MAIN_SIMULATE_KONGA_2(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "SimulateKonga2",
        false
    ),
    MAIN_SIMULATE_KONGA_3(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "SimulateKonga3",
        false
    ),
    MAIN_WII_SD_CARD(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "WiiSDCard", true),
    MAIN_WII_SD_CARD_ENABLE_FOLDER_SYNC(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "WiiSDCardEnableFolderSync",
        false
    ),
    MAIN_WIIMOTE_CONTINUOUS_SCANNING(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "WiimoteContinuousScanning",
        false
    ),
    MAIN_WIIMOTE_ENABLE_SPEAKER(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "WiimoteEnableSpeaker",
        false
    ),
    MAIN_MMU(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "MMU", false),
    MAIN_PAUSE_ON_PANIC(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "PauseOnPanic", false),
    MAIN_ACCURATE_CPU_CACHE(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "AccurateCPUCache",
        false
    ),
    MAIN_SYNC_GPU(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "SyncGPU", false),
    MAIN_FAST_DISC_SPEED(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "FastDiscSpeed", false),
    MAIN_OVERCLOCK_ENABLE(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "OverclockEnable",
        false
    ),
    MAIN_VI_OVERCLOCK_ENABLE(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "VIOverclockEnable",
        false
    ),
    MAIN_RAM_OVERRIDE_ENABLE(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "RAMOverrideEnable",
        false
    ),
    MAIN_CUSTOM_RTC_ENABLE(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "EnableCustomRTC",
        false
    ),
    MAIN_AUTO_DISC_CHANGE(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "AutoDiscChange",
        false
    ),
    MAIN_ALLOW_SD_WRITES(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "WiiSDCardAllowWrites",
        true
    ),
    MAIN_ENABLE_SAVESTATES(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_CORE,
        "EnableSaveStates",
        false
    ),
    MAIN_WII_WIILINK_ENABLE(Settings.FILE_DOLPHIN, Settings.SECTION_INI_CORE, "EnableWiiLink", false),
    MAIN_DSP_JIT(Settings.FILE_DOLPHIN, Settings.SECTION_INI_DSP, "EnableJIT", true),
    MAIN_TIME_TRACKING(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_GENERAL,
        "EnablePlayTimeTracking",
        true
    ),
    MAIN_EXPAND_TO_CUTOUT_AREA(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_INTERFACE,
        "ExpandToCutoutArea",
        false
    ),
    MAIN_USE_PANIC_HANDLERS(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_INTERFACE,
        "UsePanicHandlers",
        true
    ),
    MAIN_OSD_MESSAGES(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_INTERFACE,
        "OnScreenDisplayMessages",
        true
    ),
    MAIN_ANALYTICS_ENABLED(Settings.FILE_DOLPHIN, Settings.SECTION_ANALYTICS, "Enabled", false),
    MAIN_ANALYTICS_PERMISSION_ASKED(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_ANALYTICS,
        "PermissionAsked",
        false
    ),
    MAIN_RECURSIVE_ISO_PATHS(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_GENERAL,
        "RecursiveISOPaths",
        false
    ),
    MAIN_USE_GAME_COVERS(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_GENERAL,
        "UseGameCovers",
        true
    ),
    MAIN_DEBUG_JIT_OFF(Settings.FILE_DOLPHIN, Settings.SECTION_DEBUG, "JitOff", false),
    MAIN_DEBUG_JIT_LOAD_STORE_OFF(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_DEBUG,
        "JitLoadStoreOff",
        false
    ),
    MAIN_DEBUG_JIT_LOAD_STORE_FLOATING_OFF(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_DEBUG,
        "JitLoadStoreFloatingOff",
        false
    ),
    MAIN_DEBUG_JIT_LOAD_STORE_PAIRED_OFF(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_DEBUG,
        "JitLoadStorePairedOff",
        false
    ),
    MAIN_DEBUG_JIT_FLOATING_POINT_OFF(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_DEBUG,
        "JitFloatingPointOff",
        false
    ),
    MAIN_DEBUG_JIT_INTEGER_OFF(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_DEBUG,
        "JitIntegerOff",
        false
    ),
    MAIN_DEBUG_JIT_PAIRED_OFF(Settings.FILE_DOLPHIN, Settings.SECTION_DEBUG, "JitPairedOff", false),
    MAIN_DEBUG_JIT_SYSTEM_REGISTERS_OFF(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_DEBUG,
        "JitSystemRegistersOff",
        false
    ),
    MAIN_DEBUG_JIT_BRANCH_OFF(Settings.FILE_DOLPHIN, Settings.SECTION_DEBUG, "JitBranchOff", false),
    MAIN_DEBUG_JIT_REGISTER_CACHE_OFF(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_DEBUG,
        "JitRegisterCacheOff",
        false
    ),
    MAIN_DEBUG_JIT_ENABLE_PROFILING(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_DEBUG,
        "JitEnableProfiling",
        false
    ),
    MAIN_EMULATE_SKYLANDER_PORTAL(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_EMULATED_USB_DEVICES,
        "EmulateSkylanderPortal",
        false
    ),
    MAIN_EMULATE_INFINITY_BASE(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_EMULATED_USB_DEVICES,
        "EmulateInfinityBase",
        false
    ),
    MAIN_EMULATE_WII_SPEAK(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_EMULATED_USB_DEVICES,
        "EmulateWiiSpeak",
        false
    ),
    MAIN_WII_SPEAK_MUTED(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_EMULATED_USB_DEVICES,
        "WiiSpeakMuted",
        true
    ),
    MAIN_SHOW_GAME_TITLES(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID,
        "ShowGameTitles",
        true
    ),
    MAIN_USE_BLACK_BACKGROUNDS(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID,
        "UseBlackBackgrounds",
        false
    ),
    MAIN_JOYSTICK_REL_CENTER(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID,
        "JoystickRelCenter",
        true
    ),
    MAIN_SHOW_INPUT_OVERLAY(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID,
        "ShowInputOverlay",
        true
    ),
    MAIN_IR_ALWAYS_RECENTER(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID,
        "IRAlwaysRecenter",
        false
    ),
    MAIN_BUTTON_TOGGLE_GC_0(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCButtonA",
        true
    ),
    MAIN_BUTTON_TOGGLE_GC_1(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCButtonB",
        true
    ),
    MAIN_BUTTON_TOGGLE_GC_2(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCButtonX",
        true
    ),
    MAIN_BUTTON_TOGGLE_GC_3(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCButtonY",
        true
    ),
    MAIN_BUTTON_TOGGLE_GC_4(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCButtonZ",
        true
    ),
    MAIN_BUTTON_TOGGLE_GC_5(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCButtonStart",
        true
    ),
    MAIN_BUTTON_TOGGLE_GC_6(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCTriggerL",
        true
    ),
    MAIN_BUTTON_TOGGLE_GC_7(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCTriggerR",
        true
    ),
    MAIN_BUTTON_TOGGLE_GC_8(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCDPad",
        true
    ),
    MAIN_BUTTON_TOGGLE_GC_9(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCStickMain",
        true
    ),
    MAIN_BUTTON_TOGGLE_GC_10(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCStickC",
        true
    ),
    MAIN_BUTTON_TOGGLE_GC_11(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCTriggerLHalf",
        false
    ),
    MAIN_BUTTON_TOGGLE_GC_12(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCTriggerRHalf",
        false
    ),
    MAIN_BUTTON_TOGGLE_GC_13(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleGCTriggerAnalogStick",
        false
    ),
    MAIN_BUTTON_LATCHING_GC_0(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingGCButtonA",
        false
    ),
    MAIN_BUTTON_LATCHING_GC_1(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingGCButtonB",
        false
    ),
    MAIN_BUTTON_LATCHING_GC_2(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingGCButtonX",
        false
    ),
    MAIN_BUTTON_LATCHING_GC_3(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingGCButtonY",
        false
    ),
    MAIN_BUTTON_LATCHING_GC_4(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingGCButtonZ",
        false
    ),
    MAIN_BUTTON_LATCHING_GC_5(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingGCButtonStart",
        false
    ),
    MAIN_BUTTON_LATCHING_GC_6(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingGCTriggerL",
        false
    ),
    MAIN_BUTTON_LATCHING_GC_7(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingGCTriggerR",
        false
    ),
    MAIN_BUTTON_LATCHING_GC_8(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingGCTriggerLHalf",
        false
    ),
    MAIN_BUTTON_LATCHING_GC_9(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingGCTriggerRHalf",
        false
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_0(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicButtonA",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_1(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicButtonB",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_2(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicButtonX",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_3(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicButtonY",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_4(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicButtonPlus",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_5(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicButtonMinus",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_6(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicButtonHome",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_7(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicTriggerL",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_8(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicTriggerR",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_9(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicButtonZL",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_10(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicButtonZR",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_11(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicDPad",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_12(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicStickLeft",
        true
    ),
    MAIN_BUTTON_TOGGLE_CLASSIC_13(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleClassicStickRight",
        true
    ),
    MAIN_BUTTON_LATCHING_CLASSIC_0(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingClassicButtonA",
        false
    ),
    MAIN_BUTTON_LATCHING_CLASSIC_1(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingClassicButtonB",
        false
    ),
    MAIN_BUTTON_LATCHING_CLASSIC_2(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingClassicButtonX",
        false
    ),
    MAIN_BUTTON_LATCHING_CLASSIC_3(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingClassicButtonY",
        false
    ),
    MAIN_BUTTON_LATCHING_CLASSIC_4(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingClassicButtonPlus",
        false
    ),
    MAIN_BUTTON_LATCHING_CLASSIC_5(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingClassicButtonMinus",
        false
    ),
    MAIN_BUTTON_LATCHING_CLASSIC_6(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingClassicButtonHome",
        false
    ),
    MAIN_BUTTON_LATCHING_CLASSIC_7(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingClassicTriggerL",
        false
    ),
    MAIN_BUTTON_LATCHING_CLASSIC_8(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingClassicTriggerR",
        false
    ),
    MAIN_BUTTON_LATCHING_CLASSIC_9(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingClassicButtonZL",
        false
    ),
    MAIN_BUTTON_LATCHING_CLASSIC_10(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingClassicButtonZR",
        false
    ),
	    // TaTaCon 专用 Toggle 设置（新增）
    MAIN_BUTTON_TOGGLE_TATACON_0(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleTatacon1",
        true
    ),
    MAIN_BUTTON_TOGGLE_TATACON_1(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleTatacon2",
        true
    ),
    MAIN_BUTTON_TOGGLE_TATACON_2(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleTataconPlus",
        true
    ),
    MAIN_BUTTON_TOGGLE_TATACON_3(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleTataconMinus",
        true
    ),
    MAIN_BUTTON_TOGGLE_TATACON_4(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleTataconHome",
        true
    ),
    MAIN_BUTTON_TOGGLE_TATACON_5(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleTataconRimLeft",
        true
    ),
    MAIN_BUTTON_TOGGLE_TATACON_6(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleTataconRimRight",
        true
    ),
    MAIN_BUTTON_TOGGLE_TATACON_7(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleTataconCenterLeft",
        true
    ),
    MAIN_BUTTON_TOGGLE_TATACON_8(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonToggleTataconCenterRight",
        true
    ),
	    // TaTaCon 专用 Latching 设置
    MAIN_BUTTON_LATCHING_TATACON_0(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingTatacon1",
        false
    ),
    MAIN_BUTTON_LATCHING_TATACON_1(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingTatacon2",
        false
    ),
    MAIN_BUTTON_LATCHING_TATACON_2(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingTataconPlus",
        false
    ),
    MAIN_BUTTON_LATCHING_TATACON_3(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingTataconMinus",
        false
    ),
    MAIN_BUTTON_LATCHING_TATACON_4(
        Settings.FILE_DOLPHIN,
        Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS,
        "ButtonLatchingTataconHome",
        false
    ),
    SYSCONF_SCREENSAVER(Settings.FILE_SYSCONF, "IPL", "SSV", false),
    SYSCONF_WIDESCREEN(Settings.FILE_SYSCONF, "IPL", "AR", true),
    SYSCONF_PROGRESSIVE_SCAN(Settings.FILE_SYSCONF, "IPL", "PGS", true),
    SYSCONF_PAL60(Settings.FILE_SYSCONF, "IPL", "E60", true),
    SYSCONF_WIIMOTE_MOTOR(Settings.FILE_SYSCONF, "BT", "MOT", true),
    GFX_VSYNC(Settings.FILE_GFX, Settings.SECTION_GFX_HARDWARE, "VSync", false),
    GFX_WIDESCREEN_HACK(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "wideScreenHack", false),
    GFX_CROP_TO_ASPECT_RATIO(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "Crop", false),
    GFX_CROP_CUSTOM(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "CropCustom", false),
    GFX_SHOW_FPS(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "ShowFPS", false),
    GFX_SHOW_FTIMES(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "ShowFTimes", false),
    GFX_SHOW_VPS(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "ShowVPS", false),
    GFX_SHOW_VTIMES(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "ShowVTimes", false),
    GFX_SHOW_GRAPHS(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "ShowGraphs", false),
    GFX_SHOW_SPEED(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "ShowSpeed", false),
    GFX_SHOW_SPEED_COLORS(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "ShowSpeedColors",
        true
    ),
    GFX_LOG_RENDER_TIME_TO_FILE(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "LogRenderTimeToFile",
        false
    ),
    GFX_OVERLAY_STATS(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "OverlayStats", false),
    GFX_DUMP_TEXTURES(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "DumpTextures", false),
    GFX_DUMP_MIP_TEXTURES(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "DumpMipTextures",
        false
    ),
    GFX_DUMP_BASE_TEXTURES(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "DumpBaseTextures",
        false
    ),
    GFX_HIRES_TEXTURES(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "HiresTextures", false),
    GFX_CACHE_HIRES_TEXTURES(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "CacheHiresTextures",
        false
    ),
    GFX_DUMP_EFB_TARGET(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "DumpEFBTarget", false),
    GFX_DUMP_XFB_TARGET(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "DumpXFBTarget", false),
    GFX_INTERNAL_RESOLUTION_FRAME_DUMPS(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "InternalResolutionFrameDumps",
        false
    ),
    GFX_ENABLE_GPU_TEXTURE_DECODING(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "EnableGPUTextureDecoding",
        false
    ),
    GFX_ENABLE_PIXEL_LIGHTING(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "EnablePixelLighting",
        false
    ),
    GFX_FAST_DEPTH_CALC(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "FastDepthCalc", true),
    GFX_TEXFMT_OVERLAY_ENABLE(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "TexFmtOverlayEnable",
        false
    ),
    GFX_ENABLE_WIREFRAME(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "WireFrame", false),
    GFX_DISABLE_FOG(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "DisableFog", false),
    GFX_ENABLE_VALIDATION_LAYER(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "EnableValidationLayer",
        false
    ),
    GFX_BACKEND_MULTITHREADING(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "BackendMultithreading",
        true
    ),
    GFX_WAIT_FOR_SHADERS_BEFORE_STARTING(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "WaitForShadersBeforeStarting",
        false
    ),
    GFX_SAVE_TEXTURE_CACHE_TO_STATE(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "SaveTextureCacheToState",
        true
    ),
    GFX_PREFER_VS_FOR_LINE_POINT_EXPANSION(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_SETTINGS,
        "PreferVSForLinePointExpansion",
        false
    ),
    GFX_CPU_CULL(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "CPUCull", false),
    GFX_MODS_ENABLE(Settings.FILE_GFX, Settings.SECTION_GFX_SETTINGS, "EnableMods", false),
    GFX_ENHANCE_FORCE_TRUE_COLOR(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_ENHANCEMENTS,
        "ForceTrueColor",
        true
    ),
    GFX_ENHANCE_DISABLE_COPY_FILTER(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_ENHANCEMENTS,
        "DisableCopyFilter",
        true
    ),
    GFX_ENHANCE_ARBITRARY_MIPMAP_DETECTION(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_ENHANCEMENTS,
        "ArbitraryMipmapDetection",
        false
    ),
    GFX_CC_CORRECT_COLOR_SPACE(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_COLOR_CORRECTION,
        "CorrectColorSpace",
        false
    ),
    GFX_CC_CORRECT_GAMMA(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_COLOR_CORRECTION,
        "CorrectGamma",
        false
    ),
    GFX_STEREO_SWAP_EYES(Settings.FILE_GFX, Settings.SECTION_STEREOSCOPY, "StereoSwapEyes", false),
    GFX_HACK_EFB_ACCESS_ENABLE(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_HACKS,
        "EFBAccessEnable",
        false
    ),
    GFX_HACK_EFB_DEFER_INVALIDATION(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_HACKS,
        "EFBAccessDeferInvalidation",
        false
    ),
    GFX_HACK_BBOX_ENABLE(Settings.FILE_GFX, Settings.SECTION_GFX_HACKS, "BBoxEnable", false),
    GFX_HACK_SKIP_EFB_COPY_TO_RAM(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_HACKS,
        "EFBToTextureEnable",
        true
    ),
    GFX_HACK_SKIP_XFB_COPY_TO_RAM(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_HACKS,
        "XFBToTextureEnable",
        true
    ),
    GFX_HACK_DISABLE_COPY_TO_VRAM(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_HACKS,
        "DisableCopyToVRAM",
        false
    ),
    GFX_HACK_DEFER_EFB_COPIES(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_HACKS,
        "DeferEFBCopies",
        true
    ),
    GFX_HACK_IMMEDIATE_XFB(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_HACKS,
        "ImmediateXFBEnable",
        false
    ),
    GFX_HACK_SKIP_DUPLICATE_XFBS(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_HACKS,
        "SkipDuplicateXFBs",
        true
    ),
    GFX_HACK_COPY_EFB_SCALED(Settings.FILE_GFX, Settings.SECTION_GFX_HACKS, "EFBScaledCopy", true),
    GFX_HACK_EFB_EMULATE_FORMAT_CHANGES(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_HACKS,
        "EFBEmulateFormatChanges",
        false
    ),
    GFX_HACK_VERTEX_ROUNDING(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_HACKS,
        "VertexRounding",
        false
    ),
    GFX_HACK_VI_SKIP(Settings.FILE_GFX, Settings.SECTION_GFX_HACKS, "VISkip", false),
    GFX_HACK_FAST_TEXTURE_SAMPLING(
        Settings.FILE_GFX,
        Settings.SECTION_GFX_HACKS,
        "FastTextureSampling",
        true
    ),
    LOGGER_WRITE_TO_FILE(
        Settings.FILE_LOGGER,
        Settings.SECTION_LOGGER_OPTIONS,
        "WriteToFile",
        false
    ),
    ACHIEVEMENTS_ENABLED(
        Settings.FILE_ACHIEVEMENTS,
        Settings.SECTION_ACHIEVEMENTS,
        "Enabled",
        false
    ),
    ACHIEVEMENTS_HARDCORE_ENABLED(
        Settings.FILE_ACHIEVEMENTS,
        Settings.SECTION_ACHIEVEMENTS,
        "HardcoreEnabled",
        true
    ),
    ACHIEVEMENTS_UNOFFICIAL_ENABLED(
        Settings.FILE_ACHIEVEMENTS,
        Settings.SECTION_ACHIEVEMENTS,
        "UnofficialEnabled",
        false
    ),
    ACHIEVEMENTS_ENCORE_ENABLED(
        Settings.FILE_ACHIEVEMENTS,
        Settings.SECTION_ACHIEVEMENTS,
        "EncoreEnabled",
        false
    ),
    ACHIEVEMENTS_SPECTATOR_ENABLED(
        Settings.FILE_ACHIEVEMENTS,
        Settings.SECTION_ACHIEVEMENTS,
        "SpectatorEnabled",
        false
    ),
    ACHIEVEMENTS_LEADERBOARD_TRACKER_ENABLED(
        Settings.FILE_ACHIEVEMENTS,
        Settings.SECTION_ACHIEVEMENTS,
        "LeaderboardTrackerEnabled",
        true
    ),
    ACHIEVEMENTS_CHALLENGE_INDICATORS_ENABLED(
        Settings.FILE_ACHIEVEMENTS,
        Settings.SECTION_ACHIEVEMENTS,
        "ChallengeIndicatorsEnabled",
        true
    ),
    ACHIEVEMENTS_PROGRESS_ENABLED(
        Settings.FILE_ACHIEVEMENTS,
        Settings.SECTION_ACHIEVEMENTS,
        "ProgressEnabled",
        false
	),
    // ==================== 纯 Wiimote 独立设置 (15条) ====================
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_0(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlyA", true),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_1(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlyB", true),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_2(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnly1", true),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_3(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnly2", true),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_4(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlyPlus", true),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_5(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlyMinus", true),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_6(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlyHome", true),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_7(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlyDPad", true),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_8(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlyShakeX", false),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_9(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlyShakeY", false),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_10(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlyShakeZ", false),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_11(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlySwing", false),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_12(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlySwingForward", false),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_13(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlySwingBackward", false),
    MAIN_BUTTON_TOGGLE_WIIMOTE_ONLY_14(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleWiimoteOnlyTilt", false),

    // ==================== Nunchuk 完整独立设置 (25条) ====================
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_0(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyA", true),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_1(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyB", true),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_2(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnly1", true),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_3(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnly2", true),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_4(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyPlus", true),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_5(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyMinus", true),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_6(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyHome", true),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_7(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyDPad", true),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_8(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyC", true),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_9(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyZ", true),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_10(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyStick", true),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_11(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyShakeX", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_12(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyShakeY", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_13(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyShakeZ", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_14(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlySwing", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_15(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlySwingForward", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_16(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlySwingBackward", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_17(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyTilt", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_18(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyNShakeX", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_19(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyNShakeY", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_20(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyNShakeZ", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_21(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyNSwing", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_22(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyNSwingForward", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_23(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyNSwingBackward", false),
    MAIN_BUTTON_TOGGLE_NUNCHUK_ONLY_24(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonToggleNunchukOnlyNTilt", false),
	
	    // ==================== 纯 Wiimote Latching 独立设置 ====================
    MAIN_BUTTON_LATCHING_WIIMOTE_ONLY_0(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingWiimoteOnlyA", false),
    MAIN_BUTTON_LATCHING_WIIMOTE_ONLY_1(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingWiimoteOnlyB", false),
    MAIN_BUTTON_LATCHING_WIIMOTE_ONLY_2(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingWiimoteOnly1", false),
    MAIN_BUTTON_LATCHING_WIIMOTE_ONLY_3(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingWiimoteOnly2", false),
    MAIN_BUTTON_LATCHING_WIIMOTE_ONLY_4(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingWiimoteOnlyPlus", false),
    MAIN_BUTTON_LATCHING_WIIMOTE_ONLY_5(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingWiimoteOnlyMinus", false),
    MAIN_BUTTON_LATCHING_WIIMOTE_ONLY_6(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingWiimoteOnlyHome", false),
    MAIN_BUTTON_LATCHING_WIIMOTE_ONLY_7(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingWiimoteOnlyShakeX", false),
    MAIN_BUTTON_LATCHING_WIIMOTE_ONLY_8(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingWiimoteOnlyShakeY", false),
    MAIN_BUTTON_LATCHING_WIIMOTE_ONLY_9(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingWiimoteOnlyShakeZ", false),

    // ==================== Nunchuk Latching 完整独立设置 (25条) ====================
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_0(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyA", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_1(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyB", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_2(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnly1", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_3(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnly2", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_4(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyPlus", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_5(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyMinus", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_6(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyHome", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_7(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyC", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_8(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyZ", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_9(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyShakeX", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_10(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyShakeY", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_11(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyShakeZ", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_12(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyNShakeX", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_13(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyNShakeY", false),
    MAIN_BUTTON_LATCHING_NUNCHUK_ONLY_14(Settings.FILE_DOLPHIN, Settings.SECTION_INI_ANDROID_OVERLAY_BUTTONS, "ButtonLatchingNunchukOnlyNShakeZ", false);
	
    override val isOverridden: Boolean
        get() = NativeConfig.isOverridden(file, section, key)

    override val isRuntimeEditable: Boolean
        get() {
            if (file == Settings.FILE_SYSCONF) return false
            for (setting in NOT_RUNTIME_EDITABLE) {
                if (setting == this) return false
            }
            return NativeConfig.isSettingSaveable(file, section, key)
        }

    override fun delete(settings: Settings): Boolean {
        if (!NativeConfig.isSettingSaveable(file, section, key)) {
            throw UnsupportedOperationException("Unsupported setting: $file, $section, $key")
        }
        return NativeConfig.deleteKey(settings.writeLayer, file, section, key)
    }

    override val boolean: Boolean
        get() = NativeConfig.getBoolean(
            NativeConfig.LAYER_ACTIVE,
            file,
            section,
            key,
            defaultValue
        )

    override fun setBoolean(settings: Settings, newValue: Boolean) {
        if (!NativeConfig.isSettingSaveable(file, section, key)) {
            throw UnsupportedOperationException("Unsupported setting: $file, $section, $key")
        }
        NativeConfig.setBoolean(settings.writeLayer, file, section, key, newValue)
    }

    fun setBoolean(layer: Int, newValue: Boolean) {
        if (!NativeConfig.isSettingSaveable(file, section, key)) {
            throw UnsupportedOperationException("Unsupported setting: $file, $section, $key")
        }
        NativeConfig.setBoolean(layer, file, section, key, newValue)
    }

    companion object {
        private val NOT_RUNTIME_EDITABLE_ARRAY = arrayOf(
            MAIN_DSP_HLE,
            MAIN_FASTMEM_ARENA,
            MAIN_LARGE_ENTRY_POINTS_MAP,
            MAIN_CPU_THREAD,
            MAIN_ENABLE_CHEATS,
            MAIN_OVERRIDE_REGION_SETTINGS,
            MAIN_MMU,
            MAIN_PAUSE_ON_PANIC,
            MAIN_RAM_OVERRIDE_ENABLE,
            MAIN_CUSTOM_RTC_ENABLE,
            MAIN_DSP_JIT,
            MAIN_TIME_TRACKING,
            MAIN_EMULATE_SKYLANDER_PORTAL,
            MAIN_EMULATE_INFINITY_BASE,
            MAIN_EMULATE_WII_SPEAK,
            ACHIEVEMENTS_ENABLED,
            ACHIEVEMENTS_HARDCORE_ENABLED,
            ACHIEVEMENTS_UNOFFICIAL_ENABLED,
            ACHIEVEMENTS_ENCORE_ENABLED,
            ACHIEVEMENTS_SPECTATOR_ENABLED
        )
        private val NOT_RUNTIME_EDITABLE: Set<BooleanSetting> =
            HashSet(listOf(*NOT_RUNTIME_EDITABLE_ARRAY))

        @JvmStatic
        fun getSettingForAdapterRumble(channel: Int): BooleanSetting {
            return arrayOf(
                MAIN_ADAPTER_RUMBLE_0,
                MAIN_ADAPTER_RUMBLE_1,
                MAIN_ADAPTER_RUMBLE_2,
                MAIN_ADAPTER_RUMBLE_3
            )[channel]
        }

        @JvmStatic
        fun getSettingForSimulateKonga(channel: Int): BooleanSetting {
            return arrayOf(
                MAIN_SIMULATE_KONGA_0,
                MAIN_SIMULATE_KONGA_1,
                MAIN_SIMULATE_KONGA_2,
                MAIN_SIMULATE_KONGA_3
            )[channel]
        }
    }
}
