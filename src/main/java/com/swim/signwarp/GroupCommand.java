package com.swim.signwarp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class GroupCommand {
    private final SignWarp plugin;

    public GroupCommand(SignWarp plugin) {
        this.plugin = plugin;
    }

    public boolean handleGroupCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此指令只能由玩家執行！");
            return true;
        }

        if (args.length < 2) {
            sendGroupHelp(player);
            return true;
        }

        String subCommand = args[1].toLowerCase();

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

    private boolean handleCreateGroup(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "用法: /signwarp group create <群組名稱>");
            return true;
        }

        if (!player.hasPermission("signwarp.group.create") && !player.hasPermission("signwarp.use")) {
            player.sendMessage(ChatColor.RED + "您沒有權限建立群組！");
            return true;
        }

        String groupName = args[2];

        // 檢查群組是否已存在
        if (WarpGroup.getByName(groupName) != null) {
            player.sendMessage(ChatColor.RED + "群組 '" + groupName + "' 已經存在！");
            return true;
        }

        // 檢查玩家群組數量限制
        int maxGroups = plugin.getConfig().getInt("warp-groups.max-groups-per-player", 5);
        List<WarpGroup> playerGroups = WarpGroup.getPlayerGroups(player.getUniqueId().toString());
        if (playerGroups.size() >= maxGroups && !player.hasPermission("signwarp.group.admin")) {
            player.sendMessage(plugin.getConfig().getString("messages.max_groups_reached", "&c您已達到群組建立上限！"));
            return true;
        }

        // 建立群組
        WarpGroup group = new WarpGroup(groupName, player.getUniqueId().toString(), player.getName(),
                java.time.LocalDateTime.now().toString());
        group.save();

        String message = plugin.getConfig().getString("messages.group_created", "&a成功建立群組 '{group-name}'！");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("{group-name}", groupName)));
        return true;
    }

    private boolean handleAddWarp(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "用法: /signwarp group add <群組名稱> <傳送點名稱> [傳送點名稱2] ...");
            return true;
        }

        String groupName = args[2];
        WarpGroup group = WarpGroup.getByName(groupName);

        if (group == null) {
            String message = plugin.getConfig().getString("messages.group_not_found", "&c找不到群組 '{group-name}'。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("{group-name}", groupName)));
            return true;
        }

        if (!group.getOwnerUuid().equals(player.getUniqueId().toString()) &&
                !player.hasPermission("signwarp.group.admin") &&
                !player.isOp()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.not_group_owner", "&c您不是此群組的擁有者！")));
            return true;
        }

        // 檢查群組中傳送點數量限制
        int maxWarpsPerGroup = plugin.getConfig().getInt("warp-groups.max-warps-per-group", 10);
        List<String> currentWarps = group.getGroupWarps();

        for (int i = 3; i < args.length; i++) {
            String warpName = args[i];

            if (currentWarps.size() >= maxWarpsPerGroup) {
                player.sendMessage(ChatColor.RED + "群組傳送點數量已達上限！");
                break;
            }

            Warp warp = Warp.getByName(warpName);
            if (warp == null) {
                player.sendMessage(ChatColor.RED + "傳送點 '" + warpName + "' 不存在！");
                continue;
            }

            if (!warp.isPrivate()) {
                String message = plugin.getConfig().getString("messages.warp_not_private", "&c只有私人傳送點才能加入群組！");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                continue;
            }

            if (!warp.getCreatorUuid().equals(player.getUniqueId().toString())) {
                player.sendMessage(ChatColor.RED + "您只能將自己的傳送點加入群組！");
                continue;
            }

            if (group.addWarp(warpName)) {
                String message = plugin.getConfig().getString("messages.warp_added_to_group",
                        "&a傳送點 '{warp-name}' 已加入群組 '{group-name}'。");
                message = message.replace("{warp-name}", warpName).replace("{group-name}", groupName);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                currentWarps.add(warpName);
            } else {
                player.sendMessage(ChatColor.RED + "無法將傳送點 '" + warpName + "' 加入群組！（可能已在其他群組中）");
            }
        }
        return true;
    }

    private boolean handleRemoveWarp(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "用法: /signwarp group remove <群組名稱> <傳送點名稱>");
            return true;
        }

        String groupName = args[2];
        String warpName = args[3];

        WarpGroup group = WarpGroup.getByName(groupName);
        if (group == null) {
            String message = plugin.getConfig().getString("messages.group_not_found", "&c找不到群組 '{group-name}'。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("{group-name}", groupName)));
            return true;
        }

        if (!group.getOwnerUuid().equals(player.getUniqueId().toString()) &&
                !player.hasPermission("signwarp.group.admin") &&
                !player.isOp()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.not_group_owner", "&c您不是此群組的擁有者！")));
            return true;
        }

        if (group.removeWarp(warpName)) {
            String message = plugin.getConfig().getString("messages.warp_removed_from_group",
                    "&a傳送點 '{warp-name}' 已從群組 '{group-name}' 中移除。");
            message = message.replace("{warp-name}", warpName).replace("{group-name}", groupName);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            player.sendMessage(ChatColor.RED + "無法從群組中移除傳送點 '" + warpName + "'！");
        }
        return true;
    }

    private boolean handleInvitePlayer(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "用法: /signwarp group invite <群組名稱> <玩家名稱>");
            return true;
        }

        String groupName = args[2];
        String targetPlayerName = args[3];

        WarpGroup group = WarpGroup.getByName(groupName);
        if (group == null) {
            String message = plugin.getConfig().getString("messages.group_not_found", "&c找不到群組 '{group-name}'。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("{group-name}", groupName)));
            return true;
        }

        if (!group.getOwnerUuid().equals(player.getUniqueId().toString()) &&
                !player.hasPermission("signwarp.group.admin") &&
                !player.isOp()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.not_group_owner", "&c您不是此群組的擁有者！")));
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            String message = plugin.getConfig().getString("messages.player_not_online",
                    "&c玩家 '{player}' 目前不在線上！請等待玩家上線後再進行操作。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    message.replace("{player}", targetPlayerName)));
            return true;
        }

        // 檢查群組成員數量限制
        int maxMembersPerGroup = plugin.getConfig().getInt("warp-groups.max-members-per-group", 20);
        List<WarpGroup.GroupMember> currentMembers = group.getGroupMembers();
        if (currentMembers.size() >= maxMembersPerGroup) {
            player.sendMessage(ChatColor.RED + "群組成員數量已達上限！");
            return true;
        }

        if (group.invitePlayer(targetPlayer.getUniqueId().toString(), targetPlayer.getName())) {
            String message = plugin.getConfig().getString("messages.player_invited_to_group",
                    "&a玩家 '{player}' 已被邀請加入群組 '{group-name}'。");
            message = message.replace("{player}", targetPlayerName).replace("{group-name}", groupName);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

            targetPlayer.sendMessage(ChatColor.GREEN + "您已被邀請加入群組 '" + groupName + "'！");
        } else {
            player.sendMessage(ChatColor.RED + "無法邀請玩家！（可能已經是群組成員）");
        }
        return true;
    }

    private boolean handleUninvitePlayer(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "用法: /signwarp group uninvite <群組名稱> <玩家名稱>");
            return true;
        }


        String groupName = args[2];
        String targetPlayerName = args[3];

        WarpGroup group = WarpGroup.getByName(groupName);
        if (group == null) {
            String message = plugin.getConfig().getString("messages.group_not_found", "&c找不到群組 '{group-name}'。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("{group-name}", groupName)));
            return true;
        }

        if (!group.getOwnerUuid().equals(player.getUniqueId().toString()) &&
                !player.hasPermission("signwarp.group.admin") &&
                !player.isOp()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.not_group_owner", "&c您不是此群組的擁有者！")));
            return true;
        }

        // 只允許移除在線玩家，確保即時通知
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            player.sendMessage(ChatColor.RED + "玩家 '" + targetPlayerName + "' 目前不在線上！請等待玩家上線後再進行操作。");
            return true;
        }

        String targetUuid = targetPlayer.getUniqueId().toString();

        if (group.removeMember(targetUuid)) {
            String message = plugin.getConfig().getString("messages.player_removed_from_group",
                    "&a玩家 '{player}' 已從群組 '{group-name}' 中移除。");
            message = message.replace("{player}", targetPlayerName).replace("{group-name}", groupName);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

            // 即時通知被移除的玩家
            targetPlayer.sendMessage(ChatColor.YELLOW + "您已被從群組 '" + groupName + "' 中移除。");
        } else {
            player.sendMessage(ChatColor.RED + "無法移除玩家！");
        }
        return true;
    }

    private boolean handleListGroups(Player player) {
        List<WarpGroup> groups = WarpGroup.getPlayerGroups(player.getUniqueId().toString());
        if (groups.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "您沒有任何群組。");
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "=== 您的群組列表 ===");
        for (WarpGroup group : groups) {
            List<String> warps = group.getGroupWarps();
            List<WarpGroup.GroupMember> members = group.getGroupMembers();
            player.sendMessage(ChatColor.AQUA + group.getGroupName() +
                    ChatColor.GRAY + " - 傳送點: " + warps.size() +
                    ", 成員: " + members.size());
        }
        return true;
    }

    private boolean handleGroupInfo(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "用法: /signwarp group info <群組名稱>");
            return true;
        }

        String groupName = args[2];
        WarpGroup group = WarpGroup.getByName(groupName);

        if (group == null) {
            String message = plugin.getConfig().getString("messages.group_not_found", "&c找不到群組 '{group-name}'。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("{group-name}", groupName)));
            return true;
        }

        // 只有群組擁有者、成員或管理員可以查看詳細資訊
        if (!group.getOwnerUuid().equals(player.getUniqueId().toString()) &&
                !WarpGroup.isPlayerInGroup(groupName, player.getUniqueId().toString()) &&
                !player.hasPermission("signwarp.group.admin")) {
            player.sendMessage(ChatColor.RED + "您沒有權限查看此群組資訊！");
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "=== 群組資訊: " + groupName + " ===");
        player.sendMessage(ChatColor.AQUA + "擁有者: " + group.getOwnerName());

        List<String> warps = group.getGroupWarps();
        player.sendMessage(ChatColor.YELLOW + "傳送點 (" + warps.size() + "):");
        for (String warp : warps) {
            player.sendMessage(ChatColor.GRAY + "- " + warp);
        }

        List<WarpGroup.GroupMember> members = group.getGroupMembers();
        player.sendMessage(ChatColor.LIGHT_PURPLE + "成員 (" + members.size() + "):");
        for (WarpGroup.GroupMember member : members) {
            player.sendMessage(ChatColor.GRAY + "- " + member.getName());
        }
        return true;
    }

    private boolean handleDeleteGroup(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "用法: /signwarp group delete <群組名稱>");
            return true;
        }


        String groupName = args[2];
        WarpGroup group = WarpGroup.getByName(groupName);

        if (group == null) {
            String message = plugin.getConfig().getString("messages.group_not_found", "&c找不到群組 '{group-name}'。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("{group-name}", groupName)));
            return true;
        }

        if (!group.getOwnerUuid().equals(player.getUniqueId().toString()) &&
                !player.hasPermission("signwarp.group.admin") &&
                !player.isOp()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.not_group_owner", "&c您不是此群組的擁有者！")));
            return true;
        }

        group.delete();
        String message = plugin.getConfig().getString("messages.group_deleted", "&a群組 '{group-name}' 已刪除。");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("{group-name}", groupName)));
        return true;
    }

    private void sendGroupHelp(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== SignWarp 群組指令 ===");
        player.sendMessage(ChatColor.AQUA + "/signwarp group create <群組名稱> - 建立新群組");
        player.sendMessage(ChatColor.AQUA + "/signwarp group add <群組名稱> <傳送點名稱> - 將傳送點加入群組");
        player.sendMessage(ChatColor.AQUA + "/signwarp group remove <群組名稱> <傳送點名稱> - 從群組中移除傳送點");
        player.sendMessage(ChatColor.AQUA + "/signwarp group invite <群組名稱> <玩家名稱> - 邀請玩家加入群組");
        player.sendMessage(ChatColor.AQUA + "/signwarp group uninvite <群組名稱> <玩家名稱> - 移除群組成員");
        player.sendMessage(ChatColor.AQUA + "/signwarp group list - 列出自己擁有的群組");
        player.sendMessage(ChatColor.AQUA + "/signwarp group info <群組名稱> - 顯示群組詳細資訊");
        player.sendMessage(ChatColor.AQUA + "/signwarp group delete <群組名稱> - 刪除群組");
    }
}