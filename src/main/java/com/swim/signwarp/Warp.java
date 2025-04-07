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

public class Warp {
    private static final String DB_URL = "jdbc:sqlite:" + JavaPlugin.getPlugin(SignWarp.class).getDataFolder() + File.separator + "warps.db";
    private final String warpName;
    private final Location location;
    private final String createdAt;

    public Warp(String warpName, Location location, String createdAt) {
        this.warpName = warpName;
        this.location = location;
        this.createdAt = createdAt;
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

    public void save() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT OR REPLACE INTO warps (name, world, x, y, z, yaw, pitch, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT created_at FROM warps WHERE name = ?), ?))";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, warpName);
                pstmt.setString(2, location.getWorld().getName());
                pstmt.setDouble(3, location.getX());
                pstmt.setDouble(4, location.getY());
                pstmt.setDouble(5, location.getZ());
                pstmt.setFloat(6, location.getYaw());
                pstmt.setFloat(7, location.getPitch());
                pstmt.setString(8, warpName); // For COALESCE
                pstmt.setString(9, createdAt); // Default value if not exists
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
                        createdAt = java.time.LocalDateTime.now().toString(); // Set current time if not present
                    }
                    Location location = new Location(world, x, y, z, yaw, pitch);
                    return new Warp(warpName, location, createdAt);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

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
                        createdAt = java.time.LocalDateTime.now().toString(); // Set current time if not present
                    }
                    Location location = new Location(world, x, y, z, yaw, pitch);
                    warps.add(new Warp(name, location, createdAt));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return warps;
    }

    public static void createTable() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "CREATE TABLE IF NOT EXISTS warps (" +
                    "name TEXT PRIMARY KEY, " +
                    "world TEXT, " +
                    "x REAL, " +
                    "y REAL, " +
                    "z REAL, " +
                    "yaw REAL, " +
                    "pitch REAL, " +
                    "created_at TEXT)";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }

            // Migration logic to add created_at column if it doesn't exist
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getColumns(null, null, "warps", "created_at");
            if (!rs.next()) {
                String alterSql = "ALTER TABLE warps ADD COLUMN created_at TEXT";
                try (Statement alterStmt = conn.createStatement()) {
                    alterStmt.execute(alterSql);
                }

                // Update existing rows with current timestamp
                String updateSql = "UPDATE warps SET created_at = ? WHERE created_at IS NULL";
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    String currentDateTime = java.time.LocalDateTime.now().toString();
                    pstmt.setString(1, currentDateTime);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}