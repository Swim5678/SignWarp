package com.swim.signwarp;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.logging.Logger;

public class PlayerCache {
    private static final String DB_URL = "jdbc:sqlite:" + JavaPlugin.getPlugin(SignWarp.class).getDataFolder() + File.separator + "warps.db";
    private static final Logger logger = JavaPlugin.getPlugin(SignWarp.class).getLogger();

    // 建立玩家快取表
    public static void createPlayerCacheTable() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "CREATE TABLE IF NOT EXISTS player_cache (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "last_seen TEXT NOT NULL" +
                    ")";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            logger.severe("Failed to create player_cache table: " + e.getMessage());
        }
    }

    // 更新玩家快取
    public static void updatePlayerCache(Player player) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT OR REPLACE INTO player_cache (uuid, name, last_seen) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, player.getUniqueId().toString());
                pstmt.setString(2, player.getName());
                pstmt.setString(3, java.time.LocalDateTime.now().toString());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Failed to update player cache: " + e.getMessage());
        }
    }

    // 根據玩家名稱取得 UUID
    public static String getUuidByName(String playerName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT uuid FROM player_cache WHERE LOWER(name) = LOWER(?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerName);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("uuid");
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get UUID by name: " + e.getMessage());
        }
        return null;
    }

    // 根據 UUID 取得玩家名稱
    public static String getNameByUuid(String uuid) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT name FROM player_cache WHERE uuid = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, uuid);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get name by UUID: " + e.getMessage());
        }
        return null;
    }
}