package com.ye.mod.core;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Encodes either a single key / mouse button OR a modifier combination (Ctrl / Shift / Alt + Key/Mouse)
 * into a single backwards-compatible int:
 * - bits 0..15  (0x0000FFFF): primary key code (GLFW key 1..512, or 1000 + mouseButton for MB3/MB4/MB5)
 * - bit  16     (0x00010000): CTRL required
 * - bit  17     (0x00020000): SHIFT required
 * - bit  18     (0x00040000): ALT required
 */
public final class KeyCombo {
    public static final int MASK_KEY = 0x0000FFFF;
    public static final int MOD_CTRL = 0x00010000;
    public static final int MOD_SHIFT = 0x00020000;
    public static final int MOD_ALT = 0x00040000;
    public static final int MOUSE_OFFSET = 1000;

    private KeyCombo() {}

    public static int pack(int primaryCode, boolean ctrl, boolean shift, boolean alt) {
        if (primaryCode <= 0) return -1;
        int code = primaryCode & MASK_KEY;
        if (ctrl && !isCtrlKey(code)) code |= MOD_CTRL;
        if (shift && !isShiftKey(code)) code |= MOD_SHIFT;
        if (alt && !isAltKey(code)) code |= MOD_ALT;
        return code;
    }

    public static int primary(int encoded) {
        return encoded <= 0 ? -1 : (encoded & MASK_KEY);
    }

    public static boolean hasCtrl(int encoded) {
        return encoded > 0 && (encoded & MOD_CTRL) != 0;
    }

    public static boolean hasShift(int encoded) {
        return encoded > 0 && (encoded & MOD_SHIFT) != 0;
    }

    public static boolean hasAlt(int encoded) {
        return encoded > 0 && (encoded & MOD_ALT) != 0;
    }

    public static boolean isModifierKey(int glfwKey) {
        return isCtrlKey(glfwKey) || isShiftKey(glfwKey) || isAltKey(glfwKey);
    }

    public static boolean isCtrlKey(int k) {
        return k == GLFW.GLFW_KEY_LEFT_CONTROL || k == GLFW.GLFW_KEY_RIGHT_CONTROL;
    }

    public static boolean isShiftKey(int k) {
        return k == GLFW.GLFW_KEY_LEFT_SHIFT || k == GLFW.GLFW_KEY_RIGHT_SHIFT;
    }

    public static boolean isAltKey(int k) {
        return k == GLFW.GLFW_KEY_LEFT_ALT || k == GLFW.GLFW_KEY_RIGHT_ALT;
    }

    public static boolean isCtrlDown(long handle) {
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    public static boolean isShiftDown(long handle) {
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    public static boolean isAltDown(long handle) {
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    public static boolean isPrimaryDown(long handle, int primary) {
        if (primary <= 0) return false;
        if (primary >= MOUSE_OFFSET) {
            int btn = primary - MOUSE_OFFSET;
            return btn >= 0 && btn <= 7 && GLFW.glfwGetMouseButton(handle, btn) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(handle, primary) == GLFW.GLFW_PRESS;
    }

    /**
     * Checks if the encoded single key or combo is currently held on the window.
     * If a single non-modifier key (e.g. 'C') is bound without modifiers, pressing 'Ctrl+C' will NOT falsely trigger it!
     */
    public static boolean isComboDown(long handle, int encoded) {
        if (encoded <= 0) return false;
        int prim = primary(encoded);
        if (!isPrimaryDown(handle, prim)) return false;

        boolean reqCtrl = hasCtrl(encoded);
        boolean reqShift = hasShift(encoded);
        boolean reqAlt = hasAlt(encoded);

        if (reqCtrl && !isCtrlDown(handle)) return false;
        if (reqShift && !isShiftDown(handle)) return false;
        if (reqAlt && !isAltDown(handle)) return false;

        // Prevent Ctrl+Key or Alt+Key combos from triggering bare single-key bindings
        if (!reqCtrl && !isCtrlKey(prim) && isCtrlDown(handle)) return false;
        if (!reqAlt && !isAltKey(prim) && isAltDown(handle)) return false;
        return true;
    }

    /**
     * Checks if a key event inside a Screen matches an encoded single-key or combo binding.
     */
    public static boolean matchesScreenKey(long handle, int pressedKey, int encoded) {
        if (encoded <= 0) return false;
        int prim = primary(encoded);
        if (prim != pressedKey) return false;
        if (hasCtrl(encoded) && !isCtrlDown(handle)) return false;
        if (hasShift(encoded) && !isShiftDown(handle)) return false;
        if (hasAlt(encoded) && !isAltDown(handle)) return false;
        return true;
    }

    public static String format(int encoded) {
        if (encoded <= 0) return ConfigManager.tr("无", "None");
        StringBuilder sb = new StringBuilder();
        if (hasCtrl(encoded)) sb.append("Ctrl+");
        if (hasShift(encoded)) sb.append("Shift+");
        if (hasAlt(encoded)) sb.append("Alt+");
        sb.append(singleName(primary(encoded)));
        return sb.toString();
    }

    public static String formatPendingModifiers(long handle) {
        StringBuilder sb = new StringBuilder();
        if (isCtrlDown(handle)) sb.append("Ctrl+");
        if (isShiftDown(handle)) sb.append("Shift+");
        if (isAltDown(handle)) sb.append("Alt+");
        sb.append(ConfigManager.tr("按键...", "Key..."));
        return sb.toString();
    }

    public static String singleName(int code) {
        if (code <= 0) return ConfigManager.tr("无", "None");
        if (code >= MOUSE_OFFSET) {
            int btn = code - MOUSE_OFFSET;
            return switch (btn) {
                case 2 -> ConfigManager.tr("中键", "MMB");
                case 3 -> "MB4";
                case 4 -> "MB5";
                default -> "MB" + (btn + 1);
            };
        }
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) {
            return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
        }
        if (code >= GLFW.GLFW_KEY_KP_0 && code <= GLFW.GLFW_KEY_KP_9) {
            return "NP" + (code - GLFW.GLFW_KEY_KP_0);
        }
        return switch (code) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            default -> {
                String n = GLFW.glfwGetKeyName(code, 0);
                yield n != null ? n.toUpperCase(Locale.ROOT) : ("K" + code);
            }
        };
    }
}
