package com.zeeesea.textureeditor.editor;

public enum EditorTool {
    PENCIL("Pencil", "✏"),
    ERASER("Eraser", "⌫"),
    FILL("Fill", "🪣"),
    EYEDROPPER("Eyedropper", "💉"),
    RECTANGLE("Rectangle", "▭"),
    LINE("Line", "╱"),
    SELECT("Select", "▦");

    private final String name;
    private final String icon;

    EditorTool(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getDisplayName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public static EditorTool getToolByName(String name) {
        for (EditorTool tool : EditorTool.values()) {
            if (tool.name.equals(name)) {
                return tool;
            }
        }
        //Default fallback
        return PENCIL;
    }
}
