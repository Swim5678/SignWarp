package com.swim.signwarp;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public record WarpGroup(String groupName, String ownerUuid, String ownerName, String createdAt) {
    private static final String DB_URL = "jdbc:sqlite:" + JavaPlugin.getPlugin(SignWarp.class).getDataFolder() + File.separator + "warps.db";
    private static final Logger logger = JavaPlugin.getPlugin(SignWarp.class).getLogger();

    // 儲存群組到資料庫
    public void save() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT OR REPLACE INTO warp_groups (group_name, owner_uuid, owner_name, created_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, groupName);
                pstmt.setString(2, ownerUuid);
                pstmt.setString(3, ownerName);
                pstmt.setString(4, createdAt);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Failed to save warp group '" + groupName + "': " + e.getMessage());
        }
    }

    // 刪除群組
    public void delete() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // 刪除群組成員
            String deleteMembersSql = "DELETE FROM warp_group_members WHERE group_name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteMembersSql)) {
                pstmt.setString(1, groupName);
                pstmt.executeUpdate();
            }

            // 刪除群組中的傳送點關聯
            String deleteWarpsSql = "DELETE FROM warp_group_warps WHERE group_name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteWarpsSql)) {
                pstmt.setString(1, groupName);
                pstmt.executeUpdate();
            }

            // 刪除群組
            String deleteGroupSql = "DELETE FROM warp_groups WHERE group_name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteGroupSql)) {
                pstmt.setString(1, groupName);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Failed to delete warp group '" + groupName + "': " + e.getMessage());
        }
    }

    // 新增傳送點到群組
    public boolean addWarp(String warpName) {
        // 檢查傳送點是否存在且為私人傳送點
        Warp warp = Warp.getByName(warpName);
        if (warp == null) return false;
        if (!warp.isPrivate()) return false;
        if (!warp.getCreatorUuid().equals(ownerUuid)) return false;

        // 檢查傳送點是否已在其他群組中
        if (isWarpInAnyGroup(warpName)) return false;

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT OR IGNORE INTO warp_group_warps (group_name, warp_name) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, groupName);
                pstmt.setString(2, warpName);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.severe("Failed to add warp to group: " + e.getMessage());
            return false;
        }
    }

    // 從群組移除傳送點
    public boolean removeWarp(String warpName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "DELETE FROM warp_group_warps WHERE group_name = ? AND warp_name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, groupName);
                pstmt.setString(2, warpName);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.severe("Failed to remove warp from group: " + e.getMessage());
            return false;
        }
    }

    // 邀請玩家加入群組
    public boolean invitePlayer(String playerUuid, String playerName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT OR IGNORE INTO warp_group_members (group_name, member_uuid, member_name, invited_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, groupName);
                pstmt.setString(2, playerUuid);
                pstmt.setString(3, playerName);
                pstmt.setString(4, LocalDateTime.now().toString());
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.severe("Failed to invite player to group: " + e.getMessage());
            return false;
        }
    }

    // 移除群組成員
    public boolean removeMember(String playerUuid) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "DELETE FROM warp_group_members WHERE group_name = ? AND member_uuid = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, groupName);
                pstmt.setString(2, playerUuid);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.severe("Failed to remove member from group: " + e.getMessage());
            return false;
        }
    }

    // 取得群組中的傳送點
    public List<String> getGroupWarps() {
        List<String> warps = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT warp_name FROM warp_group_warps WHERE group_name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, groupName);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    warps.add(rs.getString("warp_name"));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get group warps: " + e.getMessage());
        }
        return warps;
    }

    // 取得群組成員
    public List<GroupMember> getGroupMembers() {
        List<GroupMember> members = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM warp_group_members WHERE group_name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, groupName);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    members.add(new GroupMember(
                            rs.getString("member_uuid"),
                            rs.getString("member_name"),
                            rs.getString("invited_at")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get group members: " + e.getMessage());
        }
        return members;
    }

    // 靜態方法：根據名稱取得群組
    public static WarpGroup getByName(String groupName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM warp_groups WHERE group_name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, groupName);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return new WarpGroup(
                            rs.getString("group_name"),
                            rs.getString("owner_uuid"),
                            rs.getString("owner_name"),
                            rs.getString("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get warp group '" + groupName + "': " + e.getMessage());
        }
        return null;
    }

    // 靜態方法：取得玩家擁有的群組
    public static List<WarpGroup> getPlayerGroups(String playerUuid) {
        List<WarpGroup> groups = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM warp_groups WHERE owner_uuid = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    groups.add(new WarpGroup(
                            rs.getString("group_name"),
                            rs.getString("owner_uuid"),
                            rs.getString("owner_name"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get player groups: " + e.getMessage());
        }
        return groups;
    }

    // 檢查玩家是否為群組成員（包含擁有者）
    public static boolean isPlayerInGroup(String groupName, String playerUuid) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // 檢查是否為擁有者
            String ownerSql = "SELECT 1 FROM warp_groups WHERE group_name = ? AND owner_uuid = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(ownerSql)) {
                pstmt.setString(1, groupName);
                pstmt.setString(2, playerUuid);
                if (pstmt.executeQuery().next()) return true;
            }

            // 檢查是否為成員
            String memberSql = "SELECT 1 FROM warp_group_members WHERE group_name = ? AND member_uuid = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(memberSql)) {
                pstmt.setString(1, groupName);
                pstmt.setString(2, playerUuid);
                return pstmt.executeQuery().next();
            }
        } catch (SQLException e) {
            logger.severe("Failed to check if player is in group: " + e.getMessage());
        }
        return false;
    }

    // 檢查傳送點是否在任何群組中
    private static boolean isWarpInAnyGroup(String warpName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT 1 FROM warp_group_warps WHERE warp_name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, warpName);
                return pstmt.executeQuery().next();
            }
        } catch (SQLException e) {
            logger.severe("Failed to check if warp is in any group: " + e.getMessage());
        }
        return false;
    }

    // 靜態方法：建立資料表
    public static void createTables() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // 建立群組表
            String groupTableSql = "CREATE TABLE IF NOT EXISTS warp_groups (" +
                    "group_name TEXT PRIMARY KEY, " +
                    "owner_uuid TEXT NOT NULL, " +
                    "owner_name TEXT NOT NULL, " +
                    "created_at TEXT NOT NULL" +
                    ")";

            // 建立群組成員表
            String membersTableSql = "CREATE TABLE IF NOT EXISTS warp_group_members (" +
                    "group_name TEXT, " +
                    "member_uuid TEXT, " +
                    "member_name TEXT, " +
                    "invited_at TEXT, " +
                    "PRIMARY KEY (group_name, member_uuid), " +
                    "FOREIGN KEY (group_name) REFERENCES warp_groups(group_name) ON DELETE CASCADE" +
                    ")";

            // 建立群組傳送點關聯表
            String warpsTableSql = "CREATE TABLE IF NOT EXISTS warp_group_warps (" +
                    "group_name TEXT, " +
                    "warp_name TEXT, " +
                    "PRIMARY KEY (group_name, warp_name), " +
                    "FOREIGN KEY (group_name) REFERENCES warp_groups(group_name) ON DELETE CASCADE, " +
                    "FOREIGN KEY (warp_name) REFERENCES warps(name) ON DELETE CASCADE" +
                    ")";

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(groupTableSql);
                stmt.execute(membersTableSql);
                stmt.execute(warpsTableSql);
            }
        } catch (SQLException e) {
            logger.severe("Failed to create warp group tables: " + e.getMessage());
        }
    }

    // 內部類別：群組成員
        public record GroupMember(String uuid, String name, String invitedAt) {
    }

    public static List<WarpGroup> getAllGroups() {
        List<WarpGroup> groups = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM warp_groups";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String groupName = rs.getString("group_name");
                    String ownerUuid = rs.getString("owner_uuid");
                    String ownerName = rs.getString("owner_name");
                    String createdAt = rs.getString("created_at");
                    groups.add(new WarpGroup(groupName, ownerUuid, ownerName, createdAt));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get all groups: " + e.getMessage());
        }
        return groups;
    }
}