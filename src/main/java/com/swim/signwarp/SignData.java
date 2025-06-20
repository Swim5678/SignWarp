package com.swim.signwarp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class SignData {
    static final String HEADER_WARP = "[Warp]";
    static final String HEADER_TARGET = "[WarpTarget]";
    static final String SHORT_HEADER_WARP = "[WP]";
    static final String SHORT_HEADER_TARGET_WARP = "[WPT]";

    static final String SMALL_SHORT_HEADER_WARP = "[wp]";
    static final String SMALL_SHORT_HEADER_TARGET_WARP = "[wpt]";
    final String warpName;
    private final String header;

    // 原有的構造方法（用於向後兼容）
    SignData(String[] lines) {
        header = stripColor(lines[0]);
        warpName = lines[1];
    }

    // 新的構造方法（使用 Component[]）
    SignData(Component[] lines) {
        header = stripColor(PlainTextComponentSerializer.plainText().serialize(lines[0]));
        warpName = PlainTextComponentSerializer.plainText().serialize(lines[1]);
    }

    // 簡單的顏色代碼移除方法
    private String stripColor(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-or]", "").replaceAll("&[0-9a-fk-or]", "");
    }

    Boolean isValidWarpName() {
        return warpName != null && !warpName.isEmpty();
    }

    Boolean isWarp() {
        return header.equalsIgnoreCase(HEADER_WARP) || header.equalsIgnoreCase(SHORT_HEADER_WARP) || header.equalsIgnoreCase(SMALL_SHORT_HEADER_WARP);
    }

    Boolean isWarpTarget() {
        return header.equalsIgnoreCase(HEADER_TARGET) || header.equalsIgnoreCase(SHORT_HEADER_TARGET_WARP) || header.equalsIgnoreCase(SMALL_SHORT_HEADER_TARGET_WARP);
    }

    Boolean isWarpSign() {
        return isWarp() || isWarpTarget();
    }
}