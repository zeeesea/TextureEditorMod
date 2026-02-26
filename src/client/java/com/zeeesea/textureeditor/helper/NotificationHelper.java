package com.zeeesea.textureeditor.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class NotificationHelper {
    private static long TOAST_COOLDOWN_MS = 5000;
    private static final Map<String, Long> lastToast = new HashMap<>();


    public static boolean addToast(SystemToast.Type type, String title, String description) {
        long now = System.currentTimeMillis();

        Long last = lastToast.get(title);
        if (last == null || now - last > TOAST_COOLDOWN_MS) {
            lastToast.put(title, now);
            showToast(type, title, description);
            return true;
        }
        return false;
    }
    public static boolean addToast(SystemToast.Type type, String title) {
        return addToast(type, title, "");
    }
    private static void showToast(SystemToast.Type type, String title, String description) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getToastManager().add(
                SystemToast.create(client, type, Text.literal(title), Text.literal(description))
        );
    }

    public static long getToastCooldown() {
        return TOAST_COOLDOWN_MS;
    }
    public static void setToastCooldown(long cooldown) {
        TOAST_COOLDOWN_MS = cooldown;
    }
}