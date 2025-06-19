package com.swim.signwarp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class GroupCommand {
    private final SignWarp plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public GroupCommand(SignWarp plugin) {
        this.plugin = plugin;
    }

    // === 提取的通用方法 ===

    /**
     * 統一的配置訊息發送方法
     * 處理配置訊息的讀取、佔位符替換和MiniMessage格式轉換
     */
    private void sendConfigMessage(Player player, String configKey, String defaultMessage, String... placeholders) {
        String message = plugin.getConfig().getString(configKey, defaultMessage);

        // 替換佔位符 - 成對處理
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                message = message.replace(placeholders[i], placeholders[i + 1]);
            }
        }

        // 將舊的顏色代碼轉換為MiniMessage格式
        message = convertLegacyToMiniMessage(message);
        Component component = miniMessage.deserialize(message);
        player.sendMessage(component);
    }

    /**
     * 將舊的&顏色代碼轉換為MiniMessage格式
     */
    private String convertLegacyToMiniMessage(String legacyText) {
        return legacyText
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&k", "<obfuscated>")
                .replace("&l", "<bold>")
                .replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>")
                .replace("&o", "<italic>")
                .replace("&r", "<reset>");
    }

    /**
     * 統一的群組驗證方法
     * 檢查群組是否存在，不存在則發送錯誤訊息
     */
    private WarpGroup validateAndGetGroup(Player player, String groupName) {
        WarpGroup group = WarpGroup.getByName(groupName);
        if (group == null) {
            sendConfigMessage(player, "messages.group_not_found", "<red>找不到群組 '{group-name}'。",
                    "{group-name}", groupName);
        }
        return group;
    }

    /**
     * 統一的管理權限檢查方法
     * 檢查玩家是否有群組管理權限，沒有則發送錯誤訊息
     */
    private boolean checkAndValidateAdminPermission(Player player, WarpGroup group) {
        if (!hasGroupAdminPermission(player, group)) {
            sendConfigMessage(player, "messages.not_group_owner", "<red>您沒有權限管理此群組！");
            return false;
        }
        return true;
    }

    /**
     * 檢查是否為 OP 或群組管理員
     */
    private boolean isOpOrAdmin(Player player) {
        return player.isOp() || player.hasPermission("signwarp.group.admin");
    }

    /**
     * 統一的線上玩家驗證方法
     * 檢查玩家是否在線，不在線則發送錯誤訊息
     */
    private Player validateOnlinePlayer(Player sender, String playerName) {
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sendConfigMessage(sender, "messages.player_not_online", "<red>玩家 '{player}' 目前不在線上！請等待玩家上線後再進行操作。", "{player}", playerName);
        }
        return targetPlayer;
    }

    /**
     * 檢查群組成員數量限制
     */
    private boolean checkMemberLimit(Player player, WarpGroup group) {
        if (isOpOrAdmin(player)) {
            return true; // OP 和管理員不受限制
        }

        int maxMembersPerGroup = plugin.getConfig().getInt("warp-groups.max-members-per-group", 20);
        List<WarpGroup.GroupMember> currentMembers = group.getGroupMembers();

        if (currentMembers.size() >= maxMembersPerGroup) {
            Component message = Component.text("群組成員數量已達上限（")
                    .color(NamedTextColor.RED)
                    .append(Component.text(maxMembersPerGroup))
                    .append(Component.text("）！"));
            player.sendMessage(message);
            return false;
        }
        return true;
    }

    /**
     * 檢查群組傳送點數量限制
     */
    private boolean checkWarpLimit(Player player, List<String> currentWarps) {
        if (isOpOrAdmin(player)) {
            return true; // OP 和管理員不受限制
        }

        int maxWarpsPerGroup = plugin.getConfig().getInt("warp-groups.max-warps-per-group", 10);
        if (currentWarps.size() >= maxWarpsPerGroup) {
            Component message = Component.text("群組傳送點數量已達上限（")
                    .color(NamedTextColor.RED)
                    .append(Component.text(maxWarpsPerGroup))
                    .append(Component.text("）！"));
            player.sendMessage(message);
            return false;
        }
        return true;
    }

    /**
     * 處理單個傳送點的添加邏輯
     */
    private boolean processWarpAddition(Player player, WarpGroup group, String groupName,
                                        String warpName, List<String> currentWarps) {
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            Component message = Component.text("傳送點 '")
                    .color(NamedTextColor.RED)
                    .append(Component.text(warpName))
                    .append(Component.text("' 不存在！"));
            player.sendMessage(message);
            return false;
        }

        if (!warp.isPrivate()) {
            sendConfigMessage(player, "messages.warp_not_private", "<red>只有私人傳送點才能加入群組！");
            return false;
        }

        // OP 和管理員可以管理任何人的傳送點
        if (!warp.getCreatorUuid().equals(player.getUniqueId().toString()) && !isOpOrAdmin(player)) {
            Component message = Component.text("您只能將自己的傳送點加入群組！")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
            return false;
        }

        if (group.addWarp(warpName)) {
            sendConfigMessage(player, "messages.warp_added_to_group",
                    "<green>傳送點 '{warp-name}' 已加入群組 '{group-name}'。",
                    "{warp-name}", warpName, "{group-name}", groupName);
            currentWarps.add(warpName);
            return true;
        } else {
            sendConfigMessage(player, "messages.warp_already_in_group",
                    "<red>傳送點 '{warp-name}' 已經在群組 '{existing-group}' 中。",
                    "{warp-name}", warpName);
            return false;
        }
    }

    /**
     * 顯示群組詳細資訊
     */
    private void displayGroupInfo(Player player, WarpGroup group, String groupName) {
        Component header = Component.text("=== 群組資訊: ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(groupName))
                .append(Component.text(" ==="));
        player.sendMessage(header);

        Component owner = Component.text("擁有者: ")
                .color(NamedTextColor.AQUA)
                .append(Component.text(group.ownerName()));
        player.sendMessage(owner);

        List<String> warps = group.getGroupWarps();
        Component warpsHeader = Component.text("傳送點 (")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(warps.size()))
                .append(Component.text("):"));
        player.sendMessage(warpsHeader);

        for (String warp : warps) {
            Component warpItem = Component.text("- ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(warp));
            player.sendMessage(warpItem);
        }

        List<WarpGroup.GroupMember> members = group.getGroupMembers();
        Component membersHeader = Component.text("成員 (")
                .color(NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(members.size()))
                .append(Component.text("):"));
        player.sendMessage(membersHeader);

        for (WarpGroup.GroupMember member : members) {
            Component memberItem = Component.text("- ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(member.name()));
            player.sendMessage(memberItem);
        }
    }

    // === 主要業務邏輯方法 ===

    public boolean handleGroupCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Component message = Component.text("此指令只能由玩家執行！")
                    .color(NamedTextColor.RED);
            sender.sendMessage(message);
            return true;
        }

        // 首先檢查群組功能是否啟用
        if (!isGroupFeatureEnabled()) {
            sendConfigMessage(player, "messages.group_feature_disabled", "<red>群組功能目前已停用！");
            return true;
        }

        if (!canPlayerUseGroups(player)) {
            sendConfigMessage(player, "messages.group_feature_no_permission", "<red>您沒有權限使用群組功能！");
            return true;
        }

        if (args.length < 2) {
            sendGroupHelp(player);
            return true;
        }

        String subCommand = args[1].toLowerCase();

        // 在執行任何群組子命令前，先清理無效的傳送點
        cleanupInvalidWarpsFromAllGroups();

        return switch (subCommand) {
            case "create" -> handleCreateGroup(player, args);
            case "add" -> handleAddWarp(player, args);
            case "remove" -> handleRemoveWarp(player, args);
            case "invite" -> handleInvitePlayer(player, args);
            case "uninvite" -> handleUninvitePlayer(player, args);
            case "list" -> handleListGroups(player);
            case "info" -> handleGroupInfo(player, args);
            case "delete" -> handleDeleteGroup(player, args);
            default -> {
                sendGroupHelp(player);
                yield true;
            }
        };
    }

    /**
     * 檢查群組功能是否啟用
     */
    private boolean isGroupFeatureEnabled() {
        return plugin.getConfig().getBoolean("warp-groups.enabled", true);
    }

    /**
     * 檢查玩家是否被允許使用群組功能
     */
    private boolean canPlayerUseGroups(Player player) {
        // OP 總是可以使用
        if (player.isOp()) {
            return true;
        }

        // 檢查管理員權限
        if (player.hasPermission("signwarp.group.admin")) {
            return true;
        }

        // 檢查配置是否允許普通玩家使用群組功能
        boolean allowNormalPlayers = plugin.getConfig().getBoolean("warp-groups.allow-normal-players", true);
        if (!allowNormalPlayers) {
            return false;
        }

        // 檢查基本群組權限
        return player.hasPermission("signwarp.group.create") || player.hasPermission("signwarp.use");
    }

    /**
     * 清理所有群組中不存在的傳送點
     * 無論使用者是否有權限，都會執行此清理作業
     */
    private void cleanupInvalidWarpsFromAllGroups() {
        try {
            // 取得所有群組
            List<WarpGroup> allGroups = WarpGroup.getAllGroups();

            for (WarpGroup group : allGroups) {
                List<String> groupWarps = group.getGroupWarps();
                List<String> invalidWarps = new ArrayList<>();

                // 檢查每個群組中的傳送點是否存在
                for (String warpName : groupWarps) {
                    Warp warp = Warp.getByName(warpName);
                    if (warp == null) {
                        // 傳送點不存在，加入待刪除清單
                        invalidWarps.add(warpName);
                    }
                }

                // 刪除無效的傳送點記錄
                for (String invalidWarp : invalidWarps) {
                    group.removeWarp(invalidWarp);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "清理群組中無效傳送點時發生錯誤: " + e.getMessage(), e);
        }
    }

    /**
     * 檢查玩家是否有群組管理權限
     * OP 擁有所有群組的完整管理權限，不受任何限制
     */
    private boolean hasGroupAdminPermission(Player player, WarpGroup group) {
        // OP 擁有完整權限
        if (player.isOp()) {
            return true;
        }

        // 檢查群組管理權限
        if (player.hasPermission("signwarp.group.admin")) {
            return true;
        }

        // 檢查是否為群組擁有者
        return group.ownerUuid().equals(player.getUniqueId().toString());
    }

    /**
     * 檢查玩家是否有群組檢視權限
     * OP 可以查看所有群組資訊
     */
    private boolean hasGroupViewPermission(Player player, WarpGroup group, String groupName) {
        // OP 擁有完整權限
        if (player.isOp()) {
            return true;
        }

        // 檢查群組管理權限
        if (player.hasPermission("signwarp.group.admin")) {
            return true;
        }

        // 檢查是否為群組擁有者
        if (group.ownerUuid().equals(player.getUniqueId().toString())) {
            return true;
        }

        // 檢查是否為群組成員
        return WarpGroup.isPlayerInGroup(groupName, player.getUniqueId().toString());
    }

    private boolean handleCreateGroup(Player player, String[] args) {
        if (args.length < 3) {
            Component message = Component.text("用法: /signwarp group create <群組名稱>")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
            return true;
        }

        String groupName = args[2];
        //名稱長度限制，最多 8 個字
        if (groupName.codePointCount(0, groupName.length()) > 8) {
            Component message = Component.text("群組名稱最多 8 個字！")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
            return true;
        }
        // 檢查群組是否已存在
        if (WarpGroup.getByName(groupName) != null) {
            Component message = Component.text("群組 '")
                    .color(NamedTextColor.RED)
                    .append(Component.text(groupName))
                    .append(Component.text("' 已經存在！"));
            player.sendMessage(message);
            return true;
        }

        // 檢查玩家群組數量限制 - OP 和管理員不受限制
        if (!isOpOrAdmin(player)) {
            int maxGroups = plugin.getConfig().getInt("warp-groups.max-groups-per-player", 5);
            List<WarpGroup> playerGroups = WarpGroup.getPlayerGroups(player.getUniqueId().toString());
            if (playerGroups.size() >= maxGroups) {
                sendConfigMessage(player, "messages.max_groups_reached", "<red>您已達到群組建立上限！");
                return true;
            }
        }

        // 建立群組
        WarpGroup group = new WarpGroup(groupName, player.getUniqueId().toString(), player.getName(),
                java.time.LocalDateTime.now().toString());
        group.save();

        sendConfigMessage(player, "messages.group_created", "<green>成功建立群組 '{group-name}'！",
                "{group-name}", groupName);
        return true;
    }

    private boolean handleAddWarp(Player player, String[] args) {
        if (args.length < 4) {
            Component message = Component.text("用法: /signwarp group add <群組名稱> <傳送點名稱> [傳送點名稱2] ...")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
            return true;
        }

        String groupName = args[2];
        WarpGroup group = validateAndGetGroup(player, groupName);
        if (group == null) return true;

        if (!checkAndValidateAdminPermission(player, group)) return true;

        // 檢查群組中傳送點數量限制
        List<String> currentWarps = group.getGroupWarps();

        for (int i = 3; i < args.length; i++) {
            String warpName = args[i];

            // 檢查數量限制
            if (!checkWarpLimit(player, currentWarps)) {
                break;
            }

            processWarpAddition(player, group, groupName, warpName, currentWarps);
        }
        return true;
    }

    private boolean handleRemoveWarp(Player player, String[] args) {
        if (args.length < 4) {
            Component message = Component.text("用法: /signwarp group remove <群組名稱> <傳送點名稱>")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
            return true;
        }

        String groupName = args[2];
        String warpName = args[3];

        WarpGroup group = validateAndGetGroup(player, groupName);
        if (group == null) return true;

        if (!checkAndValidateAdminPermission(player, group)) return true;

        if (group.removeWarp(warpName)) {
            sendConfigMessage(player, "messages.warp_removed_from_group",
                    "<green>傳送點 '{warp-name}' 已從群組 '{group-name}' 中移除。",
                    "{warp-name}", warpName, "{group-name}", groupName);
        } else {
            Component message = Component.text("無法從群組中移除傳送點 '")
                    .color(NamedTextColor.RED)
                    .append(Component.text(warpName))
                    .append(Component.text("'！"));
            player.sendMessage(message);
        }
        return true;
    }

    private boolean handleInvitePlayer(Player player, String[] args) {
        if (args.length < 4) {
            Component message = Component.text("用法: /signwarp group invite <群組名稱> <玩家名稱>")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
            return true;
        }

        String groupName = args[2];
        String targetPlayerName = args[3];

        WarpGroup group = validateAndGetGroup(player, groupName);
        if (group == null) return true;

        if (!checkAndValidateAdminPermission(player, group)) return true;

        Player targetPlayer = validateOnlinePlayer(player, targetPlayerName);
        if (targetPlayer == null) return true;

        // 檢查群組成員數量限制
        if (!checkMemberLimit(player, group)) return true;

        if (group.invitePlayer(targetPlayer.getUniqueId().toString(), targetPlayer.getName())) {
            sendConfigMessage(player, "messages.player_invited_to_group",
                    "<green>玩家 '{player}' 已被邀請加入群組 '{group-name}'。",
                    "{player}", targetPlayerName, "{group-name}", groupName);

            Component inviteMessage = Component.text("您已被邀請加入群組 '")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(groupName))
                    .append(Component.text("'！"));
            targetPlayer.sendMessage(inviteMessage);
        } else {
            Component message = Component.text("無法邀請玩家！（可能已經是群組成員）")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
        }
        return true;
    }

    private boolean handleUninvitePlayer(Player player, String[] args) {
        if (args.length < 4) {
            Component message = Component.text("用法: /signwarp group uninvite <群組名稱> <玩家名稱>")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
            return true;
        }

        String groupName = args[2];
        String targetPlayerName = args[3];

        WarpGroup group = validateAndGetGroup(player, groupName);
        if (group == null) return true;

        if (!checkAndValidateAdminPermission(player, group)) return true;

        // 只允許移除在線玩家，確保即時通知
        Player targetPlayer = validateOnlinePlayer(player, targetPlayerName);
        if (targetPlayer == null) return true;

        String targetUuid = targetPlayer.getUniqueId().toString();

        if (group.removeMember(targetUuid)) {
            sendConfigMessage(player, "messages.player_removed_from_group",
                    "<green>玩家 '{player}' 已從群組 '{group-name}' 中移除。",
                    "{player}", targetPlayerName, "{group-name}", groupName);

            // 即時通知被移除的玩家
            Component removeMessage = Component.text("您已被從群組 '")
                    .color(NamedTextColor.YELLOW)
                    .append(Component.text(groupName))
                    .append(Component.text("' 中移除。"));
            targetPlayer.sendMessage(removeMessage);
        } else {
            Component message = Component.text("無法移除玩家！")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
        }
        return true;
    }

    private boolean handleListGroups(Player player) {
        List<WarpGroup> groups = WarpGroup.getPlayerGroups(player.getUniqueId().toString());
        if (groups.isEmpty()) {
            Component message = Component.text("您沒有任何群組。")
                    .color(NamedTextColor.YELLOW);
            player.sendMessage(message);
            return true;
        }

        Component header = Component.text("=== 您的群組列表 ===")
                .color(NamedTextColor.GREEN);
        player.sendMessage(header);

        for (WarpGroup group : groups) {
            List<String> warps = group.getGroupWarps();
            List<WarpGroup.GroupMember> members = group.getGroupMembers();

            Component groupInfo = Component.text(group.groupName())
                    .color(NamedTextColor.AQUA)
                    .append(Component.text(" - 傳送點: ").color(NamedTextColor.GRAY))
                    .append(Component.text(warps.size()))
                    .append(Component.text(", 成員: "))
                    .append(Component.text(members.size()));
            player.sendMessage(groupInfo);
        }
        return true;
    }

    private boolean handleGroupInfo(Player player, String[] args) {
        if (args.length < 3) {
            Component message = Component.text("用法: /signwarp group info <群組名稱>")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
            return true;
        }

        String groupName = args[2];
        WarpGroup group = validateAndGetGroup(player, groupName);
        if (group == null) return true;

        if (!hasGroupViewPermission(player, group, groupName)) {
            Component message = Component.text("您沒有權限查看此群組資訊！")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
            return true;
        }

        displayGroupInfo(player, group, groupName);
        return true;
    }

    private boolean handleDeleteGroup(Player player, String[] args) {
        if (args.length < 3) {
            Component message = Component.text("用法: /signwarp group delete <群組名稱>")
                    .color(NamedTextColor.RED);
            player.sendMessage(message);
            return true;
        }

        String groupName = args[2];
        WarpGroup group = validateAndGetGroup(player, groupName);
        if (group == null) return true;

        if (!checkAndValidateAdminPermission(player, group)) return true;

        group.delete();
        sendConfigMessage(player, "messages.group_deleted", "<green>群組 '{group-name}' 已刪除。",
                "{group-name}", groupName);
        return true;
    }

    private void sendGroupHelp(Player player) {
        String header = plugin.getConfig().getString("messages.group_help_header", "<green>=== SignWarp 群組指令說明 ===");
        String convertedHeader = convertLegacyToMiniMessage(header);
        Component headerComponent = miniMessage.deserialize(convertedHeader);
        player.sendMessage(headerComponent);

        String[] helpMessages = {
                plugin.getConfig().getString("messages.group_help_create", "<aqua>/wp group create <群組名稱> <gray>- 建立新群組"),
                plugin.getConfig().getString("messages.group_help_list", "<aqua>/wp group list <gray>- 查看您的群組列表"),
                plugin.getConfig().getString("messages.group_help_info", "<aqua>/wp group info <群組名稱> <gray>- 查看群組詳細資訊"),
                plugin.getConfig().getString("messages.group_help_add", "<aqua>/wp group add <群組名稱> <傳送點> <gray>- 將傳送點加入群組"),
                plugin.getConfig().getString("messages.group_help_remove", "<aqua>/wp group remove <群組名稱> <傳送點> <gray>- 從群組移除傳送點"),
                plugin.getConfig().getString("messages.group_help_invite", "<aqua>/wp group invite <群組名稱> <玩家> <gray>- 邀請玩家加入群組"),
                plugin.getConfig().getString("messages.group_help_uninvite", "<aqua>/wp group uninvite <群組名稱> <玩家> <gray>- 移除群組成員"),
                plugin.getConfig().getString("messages.group_help_delete", "<aqua>/wp group delete <群組名稱> <gray>- 刪除群組")
        };

        for (String message : helpMessages) {
            if (message != null) {
                String convertedMessage = convertLegacyToMiniMessage(message);
                Component messageComponent = miniMessage.deserialize(convertedMessage);
                player.sendMessage(messageComponent);
            }
        }
    }
}