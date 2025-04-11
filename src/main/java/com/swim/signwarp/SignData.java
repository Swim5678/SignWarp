package com.swim.signwarp;

import net.md_5.bungee.api.ChatColor;

public class SignData {
    static final String HEADER_WARP = "[Warp]";
    static final String HEADER_TARGET = "[WarpTarget]";
    static final String SHORT_HEADER_WARP = "[WP]";
    static final String SHORT_HEADER_TARGET_WARP = "[WPT]";

    static final String SMALL_SHORT_HEADER_WARP = "[wp]";
    static final String SMALL_SHORT_HEADER_TARGET_WARP = "[wpt]";

    private final String header;
    String warpName;

    SignData(String[] lines) {
        header = ChatColor.stripColor(lines[0]);
        warpName = lines[1];
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