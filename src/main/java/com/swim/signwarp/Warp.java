package com.swim.signwarp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Warp {
    // 資料庫連線字串
    private static final String DB_URL = "jdbc:sqlite:" + JavaPlugin.getPlugin(SignWarp.class).getDataFolder() + File.separator + "warps.db";

    // 原有欄位
    private final String warpName;
    private final Location location;
    private final String createdAt;
    // 新增欄位：creator
    private final String creator;

    /**
     * 修改建構子，新增 creator 參數
     */
    public Warp(String warpName, Location location, String createdAt, String creator) {
        this.warpName = warpName;
        this.location = location;
        this.createdAt = createdAt;
        this.creator = creator;
    }

    public String getName() {
        return warpName;
    }

    public Location getLocation() {
        return location;
    }

    public String getFormattedCreatedAt() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy | hh:mm:ss a");
        LocalDateTime dateTime = LocalDateTime.parse(createdAt);
        return dateTime.format(formatter);
    }

    // 新增 getter 取得 creator
    public String getCreator() {
        return creator;
    }

    /**
     * 修改 save() 方法，將 creator 也一併儲存
     */
    public void save() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT OR REPLACE INTO warps (name, world, x, y, z, yaw, pitch, created_at, creator) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT created_at FROM warps WHERE name = ?), ?), ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, warpName);
                pstmt.setString(2, Objects.requireNonNull(location.getWorld()).getName());
                pstmt.setDouble(3, location.getX());
                pstmt.setDouble(4, location.getY());
                pstmt.setDouble(5, location.getZ());
                pstmt.setFloat(6, location.getYaw());
                pstmt.setFloat(7, location.getPitch());
                pstmt.setString(8, warpName); // For COALESCE 檢查
                pstmt.setString(9, createdAt); // 預設 created_at 值
                pstmt.setString(10, creator); // 新增 creator 欄位
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void remove() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "DELETE FROM warps WHERE name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, warpName);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 修改 getByName()，多取得 creator 欄位
     */
    public static Warp getByName(String warpName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM warps WHERE name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, warpName);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String worldName = rs.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");
                    String createdAt = rs.getString("created_at");
                    if (createdAt == null) {
                        createdAt = LocalDateTime.now().toString(); // 若為空則用目前時間
                    }
                    // 若資料庫中 creator 為空，則預設為 "unknown"
                    String creator = rs.getString("creator");
                    if (creator == null) {
                        creator = "unknown";
                    }
                    Location location = new Location(world, x, y, z, yaw, pitch);
                    return new Warp(warpName, location, createdAt, creator);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 修改 getAll()，查詢時一併取得 creator 欄位
     */
    public static List<Warp> getAll() {
        List<Warp> warps = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM warps";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    String name = rs.getString("name");
                    String worldName = rs.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");
                    String createdAt = rs.getString("created_at");
                    if (createdAt == null) {
                        createdAt = LocalDateTime.now().toString();
                    }
                    String creator = rs.getString("creator");
                    if (creator == null) {
                        creator = "unknown";
                    }
                    Location location = new Location(world, x, y, z, yaw, pitch);
                    warps.add(new Warp(name, location, createdAt, creator));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return warps;
    }

    /**
     * 修改 createTable()，除了原有欄位外，增加 creator 欄位與 migration 檢查
     */
    public static void createTable() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // 修改資料表建立語法，增加 creator 欄位
            String sql = "CREATE TABLE IF NOT EXISTS warps (" +
                    "name TEXT PRIMARY KEY, " +
                    "world TEXT, " +
                    "x REAL, " +
                    "y REAL, " +
                    "z REAL, " +
                    "yaw REAL, " +
                    "pitch REAL, " +
                    "created_at TEXT, " +
                    "creator TEXT" +
                    ")";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }

            // Migration: 檢查是否有 creator 欄位，若無則自動新增，並對舊有資料設為 "unknown"
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getColumns(null, null, "warps", "creator");
            if (!rs.next()) {
                // 若未有 creator 欄位，則進行 ALTER TABLE 新增欄位
                String alterSql = "ALTER TABLE warps ADD COLUMN creator TEXT";
                try (Statement alterStmt = conn.createStatement()) {
                    alterStmt.execute(alterSql);
                }
                // 更新現有資料，預設 creator 為 "unknown"
                String updateSql = "UPDATE warps SET creator = 'unknown' WHERE creator IS NULL";
                try (Statement updateStmt = conn.createStatement()) {
                    updateStmt.execute(updateSql);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
