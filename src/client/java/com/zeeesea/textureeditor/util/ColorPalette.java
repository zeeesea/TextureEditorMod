package com.zeeesea.textureeditor.util;

/**
 * Centralized color palette for the texture editor UI.
 * All UI color hex literals (except the editor's pixel palette) should be accessed via
 * ColorPalette.INSTANCE to make theme switching easier later.
 *
 * This class now supports multiple built-in presets. Change the active preset via
 * ColorPalette.INSTANCE.setPreset(ColorPalette.Preset.X) to switch colors at runtime.
 */
public class ColorPalette {
    public static final ColorPalette INSTANCE = new ColorPalette();

    public enum Preset {
        DEFAULT,
        ALL_WHITE,
        ALL_BLACK,
        DARK_BLUE,
        LIGHT,
        VINTAGE,
        OLED
    }

    private Preset currentPreset;

    // General backgrounds
    public int BROWSE_BACKGROUND;
    public int EDITOR_BACKGROUND;
    public int SKY_BACKGROUND;

    // Header / bars
    public int HEADER_BAR;
    public int HEADER_UNDERLINE;

    // Grid / cells
    public int CELL_BG;
    public int CELL_BG_HOVER;
    public int CELL_BORDER;
    public int MODIFIED_BORDER;

    // Tooltip / panels
    public int PANEL_BG; // semi-transparent dark by default
    public int TOOLTIP_BORDER;

    // Text colors
    public int TEXT_NORMAL;
    public int TEXT_LIGHT;
    public int TEXT_MUTED;
    public int TEXT_SUBTLE;
    public int TEXT_ALERT;

    // Quick wheel
    public int WHEEL_DEFAULT;
    public int WHEEL_HIGHLIGHT;

    // Status / misc
    public int STATUS_OK;

    // Panels & UI separators
    public int PANEL_DARK;
    public int PANEL_SEPARATOR;
    public int TAB_UNDERLINE;

    // Title & status
    public int TITLE_BAR_BG;
    public int TITLE_TEXT;
    public int STATUS_BAR_BG;
    public int STATUS_TEXT;
    // Title text shadow colors (centralized so themes can control shadow appearance)
    public int TITLE_TEXT_SHADOW;
    // Shadow used when the title text is pure white (user requested white shadow for white text)
    public int TITLE_TEXT_SHADOW_ON_WHITE;

    // Picker & swatch
    public int PICKER_BORDER;
    public int SWATCH_BORDER;

    // Scrollbar / thumbs
    public int SCROLL_THUMB;
    // Scrollbar track/background (right scroll bar)
    public int SCROLLBAR_BG;

    // Entry text color (list entry text / browse entry text)
    public int ENTRY_TEXT;

    // Alpha/overlay masks (keep as ARGB values)
    public int OVERLAY_HALF_WHITE;

    // Checkerboard used for transparent backgrounds
    public int CHECKER_DARK;
    public int CHECKER_LIGHT;

    // Active layer border color
    public int ACTIVE_LAYER_BORDER;

    private ColorPalette() {
        // start with the original palette as the DEFAULT preset
        setPreset(Preset.DEFAULT);
    }

    public Preset getPreset() {
        return currentPreset;
    }

    public void setPreset(Preset preset) {
        if (preset == null) return;
        this.currentPreset = preset;
        applyPreset(preset);
    }

    private void applyPreset(Preset p) {
        switch (p) {
            case ALL_WHITE:
                // Everything white / high-contrast light
                BROWSE_BACKGROUND = 0xFFFFFFFF;
                EDITOR_BACKGROUND = 0xFFFFFFFF;
                SKY_BACKGROUND = 0xFFFFFFFF;

                HEADER_BAR = 0xFFFFFFFF;
                HEADER_UNDERLINE = 0xFFFFFFFF;

                CELL_BG = 0xFFFFFFFF;
                CELL_BG_HOVER = 0xFFFFFFFF;
                CELL_BORDER = 0xFFDDDDDD;
                MODIFIED_BORDER = 0xFF000000;

                PANEL_BG = 0xFFFFFFFF;
                TOOLTIP_BORDER = 0xFFAAAAAA;

                TEXT_NORMAL = 0xFF000000;
                TEXT_LIGHT = 0xFF222222;
                TEXT_MUTED = 0xFF555555;
                TEXT_SUBTLE = 0xFF666666;
                TEXT_ALERT = 0xFFAA0000;

                WHEEL_DEFAULT = 0xFFFFFFFF;
                WHEEL_HIGHLIGHT = 0xFFEEEEEE;

                STATUS_OK = 0xFF00AA00;

                PANEL_DARK = 0xFFFAFAFA;
                PANEL_SEPARATOR = 0xFFEEEEEE;
                TAB_UNDERLINE = 0xFF000000;

                TITLE_BAR_BG = 0xFFFFFFFF;
                TITLE_TEXT = 0xFF000000;
                STATUS_BAR_BG = 0xFFFFFFFF;
                STATUS_TEXT = 0xFF333333;
                TITLE_TEXT_SHADOW = 0xFF000000;
                TITLE_TEXT_SHADOW_ON_WHITE = 0xFFFFFFFF;

                PICKER_BORDER = 0xFFCCCCCC;
                SWATCH_BORDER = 0xFFBBBBBB;

                SCROLL_THUMB = 0xFFCCCCCC;

                OVERLAY_HALF_WHITE = 0x40FFFFFF;

                CHECKER_DARK = 0xFFB0B0B0;
                CHECKER_LIGHT = 0xFFD0D0D0;

                ACTIVE_LAYER_BORDER = 0xFF000000;


                break;

            case ALL_BLACK:
                // Everything black (OLED-like dark)
                BROWSE_BACKGROUND = 0xFF000000;
                EDITOR_BACKGROUND = 0xFF000000;
                SKY_BACKGROUND = 0xFF000000;

                HEADER_BAR = 0xFF000000;
                HEADER_UNDERLINE = 0xFF000000;

                CELL_BG = 0xFF000000;
                CELL_BG_HOVER = 0xFF101010;
                CELL_BORDER = 0xFF111111;
                MODIFIED_BORDER = 0xFFFFFFFF; // white highlight for modified

                PANEL_BG = 0xEE000000;
                TOOLTIP_BORDER = 0xFF444444;

                TEXT_NORMAL = 0xFFFFFFFF;
                TEXT_LIGHT = 0xFFCCCCCC;
                TEXT_MUTED = 0xFF888888;
                TEXT_SUBTLE = 0xFF666666;
                TEXT_ALERT = 0xFFFF4444;

                WHEEL_DEFAULT = 0xFF000000;
                WHEEL_HIGHLIGHT = 0xFF101010;

                STATUS_OK = 0xFF00FF00;

                PANEL_DARK = 0xFF000000;
                PANEL_SEPARATOR = 0xFF111111;
                TAB_UNDERLINE = 0xFFFFFFFF;

                TITLE_BAR_BG = 0xFF000000;
                TITLE_TEXT = 0xFFFFFFFF;
                STATUS_BAR_BG = 0xFF000000;
                STATUS_TEXT = 0xFFAAAAAA;
                TITLE_TEXT_SHADOW = 0xFF000000;
                TITLE_TEXT_SHADOW_ON_WHITE = 0xFFFFFFFF;

                PICKER_BORDER = 0xFF222222;
                SWATCH_BORDER = 0xFF333333;

                SCROLL_THUMB = 0xFF444444;

                SCROLLBAR_BG = 0xFF000000;
                ENTRY_TEXT = 0xFFFFFFFF;

                OVERLAY_HALF_WHITE = 0x40000000;

                CHECKER_DARK = 0xFF101010;
                CHECKER_LIGHT = 0xFF202020;

                ACTIVE_LAYER_BORDER = 0xFFFFFFFF;
                break;

            case DARK_BLUE:
                // Dark blue / dark mode, similar to existing colors but shifted to blue tones
                BROWSE_BACKGROUND = 0xFF0E1426; // deep blue-black
                EDITOR_BACKGROUND = 0xFF0B0F1A;
                SKY_BACKGROUND = 0xFF071021;

                HEADER_BAR = 0xFF09122B;
                HEADER_UNDERLINE = 0xFF2E6BA0;

                CELL_BG = 0xFF1A2740;
                CELL_BG_HOVER = 0xFF263452;
                CELL_BORDER = 0xFF23314F;
                MODIFIED_BORDER = 0xFF66FFCC;

                PANEL_BG = 0xEE0B1630;
                TOOLTIP_BORDER = 0xFF9FB6D9;

                TEXT_NORMAL = 0xFFECEFF6;
                TEXT_LIGHT = 0xFFD6DCE6;
                TEXT_MUTED = 0xFF9FB0C8;
                TEXT_SUBTLE = 0xFF99A7C0;
                TEXT_ALERT = 0xFFFF7F7F;

                WHEEL_DEFAULT = 0xFF0D1230;
                WHEEL_HIGHLIGHT = 0xFF122040;

                STATUS_OK = 0xFF4CEB9E;

                PANEL_DARK = 0xFF081026;
                PANEL_SEPARATOR = 0xFF11223A;
                TAB_UNDERLINE = 0xFF4DA3E6;

                TITLE_BAR_BG = 0xFF0F1A2D;
                TITLE_TEXT = 0xFFE8EEFF;
                STATUS_BAR_BG = 0xFF07121B;
                STATUS_TEXT = 0xFF9FB6D9;
                TITLE_TEXT_SHADOW = 0xFF000000;
                TITLE_TEXT_SHADOW_ON_WHITE = 0xFFFFFFFF;

                PICKER_BORDER = 0xFF2A3A56;
                SWATCH_BORDER = 0xFF334468;

                SCROLL_THUMB = 0xFF47628F;

                SCROLLBAR_BG = 0xFF07121B;
                ENTRY_TEXT = 0xFFECEFF6;

                OVERLAY_HALF_WHITE = 0x40FFFFFF;

                CHECKER_DARK = 0xFF596376;
                CHECKER_LIGHT = 0xFF6F7E91;

                ACTIVE_LAYER_BORDER = 0xFF6BE8C3;
                break;

            case LIGHT:
                // Light theme: soft grays and off-whites
                BROWSE_BACKGROUND = 0xFFF2F4F8;
                EDITOR_BACKGROUND = 0xFFF8F9FB;
                SKY_BACKGROUND = 0xFFEEF2F7;

                HEADER_BAR = 0xFFEEF3F8;
                HEADER_UNDERLINE = 0xFFDDDDDD;

                CELL_BG = 0xFFFFFFFF;
                CELL_BG_HOVER = 0xFFF7F9FB;
                CELL_BORDER = 0xFFE6E7EA;
                MODIFIED_BORDER = 0xFFE55E5E;

                PANEL_BG = 0xEEFFFFFF;
                TOOLTIP_BORDER = 0xFFBBBBBB;

                TEXT_NORMAL = 0xFF222222;
                TEXT_LIGHT = 0xFF444444;
                TEXT_MUTED = 0xFF777777;
                TEXT_SUBTLE = 0xFF999999;
                TEXT_ALERT = 0xFFFF4444;

                WHEEL_DEFAULT = 0xFFF0F2F6;
                WHEEL_HIGHLIGHT = 0xFFE8EAF0;

                STATUS_OK = 0xFF007F3F;

                PANEL_DARK = 0xFFECEFF3;
                PANEL_SEPARATOR = 0xFFD9DDE3;
                TAB_UNDERLINE = 0xFF0077CC;

                TITLE_BAR_BG = 0xFFF7FAFC;
                TITLE_TEXT = 0xFF1A1A1A;
                STATUS_BAR_BG = 0xFFF1F3F6;
                STATUS_TEXT = 0xFF666666;
                TITLE_TEXT_SHADOW = 0xFF666666;
                TITLE_TEXT_SHADOW_ON_WHITE = 0xFFFFFFFF;

                PICKER_BORDER = 0xFFCCCCCC;
                SWATCH_BORDER = 0xFFBBBBBB;

                SCROLL_THUMB = 0xFFBFC7D6;

                SCROLLBAR_BG = 0xFFF1F3F6;
                ENTRY_TEXT = 0xFF222222;

                OVERLAY_HALF_WHITE = 0x40FFFFFF;

                CHECKER_DARK = 0xFFB0B0B0;
                CHECKER_LIGHT = 0xFFD8D8D8;

                ACTIVE_LAYER_BORDER = 0xFF0077CC;
                break;

            case VINTAGE:
                // Vintage / sepia-like tones
                BROWSE_BACKGROUND = 0xFF22180F;
                EDITOR_BACKGROUND = 0xFF2B1F14;
                SKY_BACKGROUND = 0xFF1A1209;

                HEADER_BAR = 0xFF2E1E12;
                HEADER_UNDERLINE = 0xFF8B6B4A;

                CELL_BG = 0xFF3A2B20;
                CELL_BG_HOVER = 0xFF4B382A;
                CELL_BORDER = 0xFF3B2F24;
                MODIFIED_BORDER = 0xFFFFCC66;

                PANEL_BG = 0xEE3A2B20;
                TOOLTIP_BORDER = 0xFF9E7D63;

                TEXT_NORMAL = 0xFFFFF6EA;
                TEXT_LIGHT = 0xFFFFF1DE;
                TEXT_MUTED = 0xFFBDA990;
                TEXT_SUBTLE = 0xFF8D745D;
                TEXT_ALERT = 0xFFFF7755;

                WHEEL_DEFAULT = 0xFF2A2118;
                WHEEL_HIGHLIGHT = 0xFF35281C;

                STATUS_OK = 0xFF88CC66;

                PANEL_DARK = 0xFF241B12;
                PANEL_SEPARATOR = 0xFF3A2F24;
                TAB_UNDERLINE = 0xFFB77F4A;

                TITLE_BAR_BG = 0xFF2E2218;
                TITLE_TEXT = 0xFFFFF6EA;
                STATUS_BAR_BG = 0xFF21180F;
                STATUS_TEXT = 0xFFBDA790;
                TITLE_TEXT_SHADOW = 0xFF000000;
                TITLE_TEXT_SHADOW_ON_WHITE = 0xFFFFFFFF;

                PICKER_BORDER = 0xFF5A4636;
                SWATCH_BORDER = 0xFF6A5341;

                SCROLL_THUMB = 0xFF7A654F;

                SCROLLBAR_BG = 0xFF21180F;
                ENTRY_TEXT = 0xFFFFF6EA;

                OVERLAY_HALF_WHITE = 0x40FFF4E0;

                CHECKER_DARK = 0xFF9A7F67;
                CHECKER_LIGHT = 0xFFB89E85;

                ACTIVE_LAYER_BORDER = 0xFFFFDDAA;
                break;

            case OLED:
                // OLED: very deep black background with vivid accent colors
                BROWSE_BACKGROUND = 0xFF000000;
                EDITOR_BACKGROUND = 0xFF000000;
                SKY_BACKGROUND = 0xFF000000;

                HEADER_BAR = 0xFF000000;
                HEADER_UNDERLINE = 0xFF00FFAA;

                CELL_BG = 0xFF000000;
                CELL_BG_HOVER = 0xFF040404;
                CELL_BORDER = 0xFFFFFFFF;
                MODIFIED_BORDER = 0xFF00FF00;

                PANEL_BG = 0xEE000000;
                TOOLTIP_BORDER = 0xFF00AA88;

                TEXT_NORMAL = 0xFFFFFFFF;
                TEXT_LIGHT = 0xFFCCCCCC;
                TEXT_MUTED = 0xFF888888;
                TEXT_SUBTLE = 0xFF666666;
                TEXT_ALERT = 0xFFFF0077;

                WHEEL_DEFAULT = 0xFF000000;
                WHEEL_HIGHLIGHT = 0xFF001111;

                STATUS_OK = 0xFF00FF00;

                PANEL_DARK = 0xFF000000;
                PANEL_SEPARATOR = 0xFF001111;
                TAB_UNDERLINE = 0xFF00FFAA;

                TITLE_BAR_BG = 0xFF000000;
                TITLE_TEXT = 0xFFFFFFFF;
                STATUS_BAR_BG = 0xFF000000;
                STATUS_TEXT = 0xFFFFFFFF;
                TITLE_TEXT_SHADOW = 0xFF000000;
                TITLE_TEXT_SHADOW_ON_WHITE = 0xFFFFFFFF;

                PICKER_BORDER = 0xFF003333;
                SWATCH_BORDER = 0xFF004444;

                SCROLL_THUMB = 0xFF005555;

                SCROLLBAR_BG = 0xFF000000;
                ENTRY_TEXT = 0xFFFFFFFF;

                OVERLAY_HALF_WHITE = 0x40FFFFFF;

                CHECKER_DARK = 0xFF050505;
                CHECKER_LIGHT = 0xFF0A0A0A;

                ACTIVE_LAYER_BORDER = 0xFF00FFAA;
                break;

            case DEFAULT:
            default:
                // Original values (kept as DEFAULT)
                BROWSE_BACKGROUND = 0xFF1A1A2E; // used for main browse background
                EDITOR_BACKGROUND = 0xFF0F0F1A; // default editor background
                SKY_BACKGROUND = 0xFF0A0A1E; // sky editor background

                // Header / bars
                HEADER_BAR = 0xFF16213E;
                HEADER_UNDERLINE = 0xFFFFFF00;

                // Grid / cells
                CELL_BG = 0xFF2A2A4E;
                CELL_BG_HOVER = 0xFF3A3A5E;
                CELL_BORDER = 0xFF333355;
                MODIFIED_BORDER = 0xFF00FF00;

                // Tooltip / panels
                PANEL_BG = 0xEE222244; // semi-transparent dark
                TOOLTIP_BORDER = 0xFFAAAAAA;

                // Text colors
                TEXT_NORMAL = 0xFFFFFFFF;
                TEXT_LIGHT = 0xFFCCCCCC;
                TEXT_MUTED = 0xFF999999;
                TEXT_SUBTLE = 0xFFAAAAAA;
                TEXT_ALERT = 0xFFFF5555;

                // Quick wheel
                WHEEL_DEFAULT = 0xFF17172e;
                WHEEL_HIGHLIGHT = 0xFF222245;

                // Status / misc
                STATUS_OK = 0xFF00FF00;

                // Panels & UI separators
                PANEL_DARK = 0xFF141420;
                PANEL_SEPARATOR = 0xFF2A2A44;
                TAB_UNDERLINE = 0xFFFFAA00;

                // Title & status
                TITLE_BAR_BG = 0xFF1A1A30;
                TITLE_TEXT = 0xFFDDDDFF;
                STATUS_BAR_BG = 0xFF111122;
                STATUS_TEXT = 0xFF888899;
                TITLE_TEXT_SHADOW = 0xFF000000;
                TITLE_TEXT_SHADOW_ON_WHITE = 0xFFFFFFFF;

                // Picker & swatch
                PICKER_BORDER = 0xFF444466;
                SWATCH_BORDER = 0xFF555577;

                // Scrollbar / thumbs
                SCROLL_THUMB = 0xFF8888CC;
                SCROLLBAR_BG = 0xFF111122;

                ENTRY_TEXT = 0xFFFFFFFF;

                // Alpha/overlay masks (keep as ARGB values)
                OVERLAY_HALF_WHITE = 0x40FFFFFF;

                // Checkerboard used for transparent backgrounds
                CHECKER_DARK = 0xFF808080;
                CHECKER_LIGHT = 0xFFA0A0A0;

                // Active layer border color
                ACTIVE_LAYER_BORDER = 0xFF6666AA;
                break;
        }
    }
}
