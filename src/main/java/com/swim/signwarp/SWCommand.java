package com.swim.signwarp;

import com.swim.signwarp.gui.WarpGui;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SWCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;

    public SWCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /signwarp <gui/reload/set/invite/uninvite/list-invites/list-own/tp>");
            return true;
        }

        // 既有的指令處理...
        if (args[0].equalsIgnoreCase("gui")) {
            handleGuiCommand(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            handleReloadCommand(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            handleSetCommand(sender, args);
            return true;
        }

        // 新增的邀請系統指令
        if (args[0].equalsIgnoreCase("invite")) {
            handleInviteCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("uninvite")) {
            handleUninviteCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("list-invites")) {
            handleListInvitesCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("list-own")) {
            handleMyWarpsCommand(sender, args);
            return true;
        }

        // 僅限 OP 傳送指定 Warp
        if (args[0].equalsIgnoreCase("tp")) {
            return handleWptCommand(sender, args);
        }

        sender.sendMessage("Unknown subcommand. Usage: /signwarp <gui/reload/set/invite/uninvite/list-invites/list/tp>");
        return true;
    }

    private void handleGuiCommand(CommandSender sender) {
        if (sender instanceof Player player) {
            if (player.hasPermission("signwarp.admin")) {
                WarpGui.openWarpGui(player, 0);
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.not_permission",
                                "You don't have permission to use this command.")));
            }
        } else {
            sender.sendMessage("This command can only be executed by a player.");
        }
    }

    private void handleReloadCommand(CommandSender sender) {
        if (sender.hasPermission("signwarp.reload")) {
            plugin.reloadConfig();
            EventListener.updateConfig(plugin);
            sender.sendMessage(ChatColor.GREEN + "配置已重新載入");
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.not_permission",
                            "You don't have permission to use this command.")));
        }
    }

    private void handleSetCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return;
        }
        if (args.length < 3) {
            String msg = plugin.getConfig().getString("messages.set_visibility_usage",
                    "&c用法: /wp set <公共|私人> <傳送點名稱>");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        String visibility = args[1].toLowerCase();
        if (!visibility.equals("公共") && !visibility.equals("私人")) {
            String msg = plugin.getConfig().getString("messages.invalid_visibility",
                    "&c使用權限必須是 '公共' 或 '私人'");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        String warpName = args[2];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            player.sendMessage(ChatColor.RED + "找不到傳送點: " + warpName);
            return;
        }
        if (!canModifyWarp(player, warp)) {
            String msg = plugin.getConfig().getString("messages.cant_modify_others_warp",
                    "&c您只能更改自己創建的傳送點！");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        boolean isPrivate = visibility.equals("私人");
        Warp updated = new Warp(
                warp.getName(),
                warp.getLocation(),
                warp.getFormattedCreatedAt(),
                warp.getCreator(),
                warp.getCreatorUuid(),
                isPrivate
        );
        updated.save();
        String msg = plugin.getConfig().getString("messages.warp_visibility_changed",
                        "&a傳送點 {warp-name} 的使用權限已更改為{visibility}。")
                .replace("{warp-name}", warpName)
                .replace("{visibility}", visibility);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private void handleInviteCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return;
        }
        if (!player.hasPermission("signwarp.invite")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    Objects.requireNonNull(plugin.getConfig().getString("messages.not_permission"))));
            return;
        }
        if (args.length < 3) {
            String msg = plugin.getConfig().getString("messages.invite_usage");
            if (msg != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            }
            return;
        }
        String target = args[1], warpName = args[2];
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer == null) {
            String msg = Objects.requireNonNull(
                            plugin.getConfig().getString("messages.player_not_found"))
                    .replace("{player}", target);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            player.sendMessage(ChatColor.RED + "找不到傳送點: " + warpName);
            return;
        }
        if (!canModifyWarp(player, warp)) {
            String msg = plugin.getConfig().getString("messages.not_your_warp");
            if (msg != null) player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        if (warp.isPlayerInvited(targetPlayer.getUniqueId().toString())) {
            String msg = Objects.requireNonNull(
                            plugin.getConfig().getString("messages.already_invited"))
                    .replace("{player}", targetPlayer.getName());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        warp.invitePlayer(targetPlayer);
        String msg = Objects.requireNonNull(
                        plugin.getConfig().getString("messages.invite_success"))
                .replace("{player}", targetPlayer.getName())
                .replace("{warp-name}", warpName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        String recv = Objects.requireNonNull(
                        plugin.getConfig().getString("messages.invite_received"))
                .replace("{inviter}", player.getName())
                .replace("{warp-name}", warpName);
        targetPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', recv));
    }

    private void handleUninviteCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return;
        }
        if (!player.hasPermission("signwarp.invite")) {
            String msg = plugin.getConfig().getString("messages.not_permission",
                    "&c您沒有權限！");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        if (args.length < 3) {
            String msg = plugin.getConfig().getString("messages.uninvite_usage",
                    "&c用法: /wp uninvite <玩家> <傳送點名稱>");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        String target = args[1], warpName = args[2];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            player.sendMessage(ChatColor.RED + "找不到傳送點: " + warpName);
            return;
        }
        boolean isOwner = warp.getCreatorUuid().equals(player.getUniqueId().toString());
        boolean isAdmin = player.hasPermission("signwarp.admin");
        if (!isOwner && !isAdmin) {
            String msg = plugin.getConfig().getString("messages.cant_modify_warp",
                    "&c您無法修改此傳送點的邀請名單！");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        Player tgt = Bukkit.getPlayer(target);
        if (tgt == null) {
            String msg = plugin.getConfig().getString("messages.player_not_found",
                            "&c找不到玩家 '{player}' 或該玩家離線！")
                    .replace("{player}", target);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        if (!warp.isPlayerInvited(tgt.getUniqueId().toString())) {
            String msg = plugin.getConfig().getString("messages.not_invited",
                            "&c玩家 {player} 未被邀請使用此傳送點。")
                    .replace("{player}", tgt.getName());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        warp.removeInvite(tgt.getUniqueId().toString());
        String msg = plugin.getConfig().getString("messages.uninvite_success",
                        "&a已移除 {player} 使用傳送點 '{warp-name}' 的權限。")
                .replace("{player}", tgt.getName())
                .replace("{warp-name}", warpName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private void handleListInvitesCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法: /wp list-invites <傳送點名稱>");
            return;
        }
        String warpName = args[1];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            player.sendMessage(ChatColor.RED + "找不到傳送點: " + warpName);
            return;
        }
        boolean isOwner = warp.getCreatorUuid().equals(player.getUniqueId().toString());
        boolean isAdmin = player.hasPermission("signwarp.admin");
        if (!isOwner && !player.hasPermission("signwarp.invite.list-own")) {
            String msg = plugin.getConfig().getString("messages.not_permission",
                    "&c您沒有權限！");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        if (!isOwner && !isAdmin) {
            String msg = plugin.getConfig().getString("messages.not_permission",
                    "&c您沒有權限！");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        List<WarpInvite> invites = warp.getInvitedPlayers();
        String header = Objects.requireNonNull(
                        plugin.getConfig().getString("messages.invite_list"))
                .replace("{warp-name}", warpName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', header));
        if (invites.isEmpty()) {
            String none = plugin.getConfig().getString("messages.no_invites");
            if (none != null) player.sendMessage(ChatColor.translateAlternateColorCodes('&', none));
        } else {
            for (WarpInvite wi : invites) {
                player.sendMessage(ChatColor.GRAY + "- " + wi.getInvitedName());
            }
        }
    }

    /**
     * 新增方法：處理查看玩家自己擁有的傳送點指令
     * OP 可以使用 /signwarp list-own <玩家名稱> 來查看指定玩家的傳送點
     */
    private void handleMyWarpsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return;
        }

        String targetPlayerName;
        String targetPlayerUuid;
        boolean isViewingOthers = false;

        // 檢查是否有指定玩家參數
        if (args.length > 1) {
            // 檢查是否為 OP 或有管理員權限
            if (!player.isOp() && !player.hasPermission("signwarp.admin")) {
                String msg = plugin.getConfig().getString("messages.not_permission_view_others",
                        "&c您沒有權限查看其他玩家的傳送點！");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                return;
            }

            targetPlayerName = args[1];
            isViewingOthers = true;

            // 嘗試從線上玩家獲取 UUID
            Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
            if (targetPlayer != null) {
                targetPlayerUuid = targetPlayer.getUniqueId().toString();
            } else {
                // 如果玩家不在線，嘗試從已有的傳送點資料中找到該玩家
                targetPlayerUuid = getPlayerUuidByName(targetPlayerName);
                if (targetPlayerUuid == null) {
                    String msg = plugin.getConfig().getString("messages.player_not_found_or_no_warps",
                                    "&c找不到玩家 '{player}' 或該玩家沒有任何傳送點。")
                            .replace("{player}", targetPlayerName);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                    return;
                }
            }
        } else {
            // 沒有指定玩家，查看自己的傳送點
            targetPlayerName = player.getName();
            targetPlayerUuid = player.getUniqueId().toString();
        }

        List<Warp> playerWarps = Warp.getPlayerWarps(targetPlayerUuid);

        // 取得配置中的訊息，如果沒有設定則使用預設值
        String headerMsg = plugin.getConfig().getString("messages.my_warps_header",
                        "&a=== {player} 擁有的傳送點 ===")
                .replace("{player}", isViewingOthers ? targetPlayerName : "您");
        String noWarpsMsg = plugin.getConfig().getString("messages.no_warps_owned",
                        "&7{player}目前沒有擁有任何傳送點。")
                .replace("{player}", isViewingOthers ? targetPlayerName : "您");
        String warpListFormat = plugin.getConfig().getString("messages.warp_list_format",
                "&f{index}. &b{warp-name} &7({visibility}) - &e{world} &7({x}, {y}, {z})");
        String totalWarpsMsg = plugin.getConfig().getString("messages.total_warps",
                        "&a{player}總共擁有 {count} 個傳送點")
                .replace("{player}", isViewingOthers ? targetPlayerName : "您");

        // 顯示標題
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', headerMsg));

        if (playerWarps.isEmpty()) {
            // 沒有傳送點
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noWarpsMsg));
        } else {
            // 列出所有傳送點
            for (int i = 0; i < playerWarps.size(); i++) {
                Warp warp = playerWarps.get(i);
                String visibility = warp.isPrivate() ? "私人" : "公共";
                String formattedMsg = warpListFormat
                        .replace("{index}", String.valueOf(i + 1))
                        .replace("{warp-name}", warp.getName())
                        .replace("{visibility}", visibility)
                        .replace("{world}", Objects.requireNonNull(warp.getLocation().getWorld()).getName())
                        .replace("{x}", String.valueOf((int) warp.getLocation().getX()))
                        .replace("{y}", String.valueOf((int) warp.getLocation().getY()))
                        .replace("{z}", String.valueOf((int) warp.getLocation().getZ()))
                        .replace("{creator}", warp.getCreator())
                        .replace("{created-at}", warp.getFormattedCreatedAt());

                player.sendMessage(ChatColor.translateAlternateColorCodes('&', formattedMsg));
            }

            // 顯示總數
            String totalMsg = totalWarpsMsg.replace("{count}", String.valueOf(playerWarps.size()));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', totalMsg));
        }
    }

    /**
     * 輔助方法：根據玩家名稱查找 UUID
     * 從現有的傳送點資料中查找該玩家的 UUID
     */
    private String getPlayerUuidByName(String playerName) {
        List<Warp> allWarps = Warp.getAll();
        for (Warp warp : allWarps) {
            if (warp.getCreator().equalsIgnoreCase(playerName)) {
                return warp.getCreatorUuid();
            }
        }
        return null;
    }

    private boolean canModifyWarp(Player player, Warp warp) {
        return warp.getCreatorUuid().equals(player.getUniqueId().toString())
                || player.hasPermission("signwarp.admin");
    }

    /**
     * OP 專用：/signwarp tp <傳送點名稱>
     */
    private boolean handleWptCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "只有伺服器 OP 可以使用此指令。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "用法: /signwarp tp <傳送點名稱>");
            return true;
        }
        String warpName = args[1];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            player.sendMessage(ChatColor.RED + "找不到傳送點: " + warpName);
            return true;
        }
        player.teleport(warp.getLocation());
        player.sendMessage(ChatColor.GREEN + "已傳送到 Warp: " + warp.getName() +
                " (建立者: " + warp.getCreator() + ")");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> cmds = new ArrayList<>();
            if (sender.hasPermission("signwarp.admin")) cmds.add("gui");
            if (sender.hasPermission("signwarp.reload")) cmds.add("reload");
            cmds.add("set");
            cmds.add("list-own");
            if (sender.hasPermission("signwarp.invite")) {
                cmds.add("invite");
                cmds.add("uninvite");
                cmds.add("list-invites");
            }
            if (sender instanceof Player p && p.isOp()) {
                cmds.add("tp");
            }
            for (String c : cmds) {
                if (c.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(c);
                }
            }
        }
        else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "set":
                    for (String v : new String[]{"公共","私人"}) {
                        if (v.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(v);
                        }
                    }
                    break;
                case "invite":
                case "uninvite":
                    Bukkit.getOnlinePlayers().forEach(pl -> {
                        if (pl.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(pl.getName());
                        }
                    });
                    break;
                case "list-invites":
                    if (sender instanceof Player pl) {
                        completions.addAll(getAccessibleWarps(pl, args[1]));
                    }
                    break;
                case "tp":
                    // 列出所有 Warp 名稱給 OP 補全
                    Warp.getAll().stream()
                            .map(Warp::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .forEach(completions::add);
                    break;
                case "list-own":
                    // 只有 OP 或管理員可以查看其他玩家的傳送點
                    if (sender instanceof Player p && (p.isOp() || p.hasPermission("signwarp.admin"))) {
                        // 補全線上玩家名稱
                        Bukkit.getOnlinePlayers().forEach(pl -> {
                            if (pl.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                completions.add(pl.getName());
                            }
                        });

                        // 補全曾經創建過傳送點的玩家名稱
                        Warp.getAll().stream()
                                .map(Warp::getCreator)
                                .distinct()
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .forEach(completions::add);
                    }
                    break;
            }
        }
        else if (args.length == 3 && (args[0].equalsIgnoreCase("set")
                || args[0].equalsIgnoreCase("invite")
                || args[0].equalsIgnoreCase("uninvite"))) {
            if (sender instanceof Player pl) {
                completions.addAll(getAccessibleWarps(pl, args[2]));
            }
        }

        return completions;
    }

    private List<String> getAccessibleWarps(Player player, String prefix) {
        return Warp.getAll().stream()
                .filter(w -> w.getCreatorUuid().equals(player.getUniqueId().toString())
                        || player.hasPermission("signwarp.admin"))
                .map(Warp::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}