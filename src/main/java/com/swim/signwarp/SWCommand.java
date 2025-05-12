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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /signwarp <gui/reload/set/invite/uninvite/list-invites>");
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

        sender.sendMessage("Unknown subcommand. Usage: /signwarp <gui/reload/set/invite/uninvite/list-invites>");
        return true;
    }

    private void handleGuiCommand(CommandSender sender) {
        if (sender instanceof Player player) {
            if (player.hasPermission("signwarp.admin")) {
                WarpGui.openWarpGui(player, 0);
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.not_permission", "You don't have permission to use this command.")));
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
                    plugin.getConfig().getString("messages.not_permission", "You don't have permission to use this command.")));
        }
    }

    private void handleSetCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return;
        }

        if (args.length < 3) {
            String message = plugin.getConfig().getString("messages.set_visibility_usage",
                    "&c用法: /wp set <公共|私人> <傳送點名稱>");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        String visibility = args[1].toLowerCase();
        if (!visibility.equals("公共") && !visibility.equals("私人")) {
            String message = plugin.getConfig().getString("messages.invalid_visibility",
                    "&c使用權限必須是 '公共' 或 '私人'");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        String warpName = args[2];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            player.sendMessage(ChatColor.RED + "找不到傳送點: " + warpName);
            return;
        }

        if (!canModifyWarp(player, warp)) {
            String message = plugin.getConfig().getString("messages.cant_modify_others_warp",
                    "&c您只能更改自己創建的傳送點！");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        boolean isPrivate = visibility.equals("私人");
        Warp updatedWarp = new Warp(
                warp.getName(),
                warp.getLocation(),
                warp.getFormattedCreatedAt(),
                warp.getCreator(),
                warp.getCreatorUuid(),
                isPrivate
        );
        updatedWarp.save();

        String message = plugin.getConfig().getString("messages.warp_visibility_changed",
                        "&a傳送點 {warp-name} 的使用權限已更改為{visibility}。")
                .replace("{warp-name}", warpName)
                .replace("{visibility}", visibility);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
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
            String message = plugin.getConfig().getString("messages.invite_usage");
            if (message != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
            return;
        }

        String targetPlayerName = args[1];
        String warpName = args[2];

        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            String message = Objects.requireNonNull(plugin.getConfig().getString("messages.player_not_found"))
                    .replace("{player}", targetPlayerName);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            player.sendMessage(ChatColor.RED + "找不到傳送點: " + warpName);
            return;
        }

        if (!canModifyWarp(player, warp)) {
            String message = plugin.getConfig().getString("messages.not_your_warp");
            if (message != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
            return;
        }

        if (warp.isPlayerInvited(targetPlayer.getUniqueId().toString())) {
            String message = Objects.requireNonNull(plugin.getConfig().getString("messages.already_invited"))
                    .replace("{player}", targetPlayer.getName());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        warp.invitePlayer(targetPlayer);

        // 發送成功訊息給邀請者
        String inviteSuccess = Objects.requireNonNull(plugin.getConfig().getString("messages.invite_success"))
                .replace("{player}", targetPlayer.getName())
                .replace("{warp-name}", warpName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', inviteSuccess));

        // 發送通知給被邀請者
        String inviteReceived = Objects.requireNonNull(plugin.getConfig().getString("messages.invite_received"))
                .replace("{inviter}", player.getName())
                .replace("{warp-name}", warpName);
        targetPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', inviteReceived));
    }

    private void handleUninviteCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return;
        }

        if (!player.hasPermission("signwarp.invite")) {
            String message = plugin.getConfig().getString("messages.not_permission", "&c您沒有權限！");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        if (args.length < 3) {
            String message = plugin.getConfig().getString("messages.uninvite_usage",
                    "&c用法: /wp uninvite <玩家> <傳送點名稱>");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        String targetPlayerName = args[1];
        String warpName = args[2];

        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            player.sendMessage(ChatColor.RED + "找不到傳送點: " + warpName);
            return;
        }

        // 修改權限檢查和錯誤訊息
        boolean isOwner = warp.getCreatorUuid().equals(player.getUniqueId().toString());
        boolean isAdmin = player.hasPermission("signwarp.admin");

        if (!isOwner && !isAdmin) {
            // 改用更合適的錯誤訊息
            String message = plugin.getConfig().getString("messages.cant_modify_warp",
                    "&c您無法修改此傳送點的邀請名單！");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            String message = plugin.getConfig().getString("messages.player_not_found",
                            "&c找不到玩家 '{player}' 或該玩家離線！")
                    .replace("{player}", targetPlayerName);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        if (!warp.isPlayerInvited(targetPlayer.getUniqueId().toString())) {
            String message = plugin.getConfig().getString("messages.not_invited",
                            "&c玩家 {player} 未被邀請使用此傳送點。")
                    .replace("{player}", targetPlayer.getName());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        warp.removeInvite(targetPlayer.getUniqueId().toString());

        String message = plugin.getConfig().getString("messages.uninvite_success",
                        "&a已移除 {player} 使用傳送點 '{warp-name}' 的權限。")
                .replace("{player}", targetPlayer.getName())
                .replace("{warp-name}", warpName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
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

        // 修改權限檢查邏輯
        // 檢查是否是自己的傳送點或是否有管理員權限
        boolean isOwner = warp.getCreatorUuid().equals(player.getUniqueId().toString());
        boolean isAdmin = player.hasPermission("signwarp.admin");

        // 如果不是自己的傳送點，需要特殊權限
        if (!isOwner && !player.hasPermission("signwarp.invite.list")) {
            String message = plugin.getConfig().getString("messages.not_permission", "&c您沒有權限！");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        // 如果是別人的傳送點，且沒有管理員權限
        if (!isOwner && !isAdmin) {
            String message = plugin.getConfig().getString("messages.not_permission", "&c您沒有權限！");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        List<WarpInvite> invites = warp.getInvitedPlayers();
        String header = Objects.requireNonNull(plugin.getConfig().getString("messages.invite_list"))
                .replace("{warp-name}", warpName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', header));

        if (invites.isEmpty()) {
            String noInvites = plugin.getConfig().getString("messages.no_invites");
            if (noInvites != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', noInvites));
            }
        } else {
            for (WarpInvite invite : invites) {
                player.sendMessage(ChatColor.GRAY + "- " + invite.getInvitedName());
            }
        }
    }

    private boolean canModifyWarp(Player player, Warp warp) {
        return warp.getCreatorUuid().equals(player.getUniqueId().toString()) ||
                player.hasPermission("signwarp.admin");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 主要指令補全
            List<String> commands = new ArrayList<>();
            if (sender.hasPermission("signwarp.admin")) commands.add("gui");
            if (sender.hasPermission("signwarp.reload")) commands.add("reload");
            commands.add("set");
            if (sender.hasPermission("signwarp.invite")) {
                commands.add("invite");
                commands.add("uninvite");
                commands.add("list-invites");
            }

            for (String cmd : commands) {
                if (cmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
        }
        else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "set":
                    String[] visibilities = {"公共", "私人"};
                    for (String v : visibilities) {
                        if (v.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(v);
                        }
                    }
                    break;
                case "invite":
                case "uninvite":
                    // 線上玩家補全
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    });
                    break;
                case "list-invites":
                    // 傳送點補全
                    if (sender instanceof Player player) {
                        completions.addAll(getAccessibleWarps(player, args[1]));
                    }
                    break;
            }
        }
        else if (args.length == 3 && (args[0].equalsIgnoreCase("set") ||
                args[0].equalsIgnoreCase("invite") ||
                args[0].equalsIgnoreCase("uninvite"))) {
            // 傳送點補全
            if (sender instanceof Player player) {
                completions.addAll(getAccessibleWarps(player, args[2]));
            }
        }

        return completions;
    }

    private List<String> getAccessibleWarps(Player player, String prefix) {
        return Warp.getAll().stream()
                .filter(warp -> warp.getCreatorUuid().equals(player.getUniqueId().toString())
                        || player.hasPermission("signwarp.admin"))
                .map(Warp::getName)
                .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}