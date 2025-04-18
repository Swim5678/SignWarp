package com.swim.signwarp;

import com.swim.signwarp.gui.WarpGui;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SWCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;

    public SWCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /signwarp <gui/reload/set>");
            return true;
        }

        if (args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player player) {
                if (player.hasPermission("signwarp.admin")) {
                    WarpGui.openWarpGui(player, 0);
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.not_permission", "You don't have permission to use this command.")));
                }
            } else {
                sender.sendMessage("This command can only be executed by a player.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("signwarp.reload")) {
                plugin.reloadConfig();
                EventListener.updateConfig(plugin);
                sender.sendMessage(ChatColor.GREEN + "配置已重新載入");
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.not_permission", "You don't have permission to use this command.")));
            }
            return true;
        }

        // 新增設定傳送點可見性的指令
        if (args[0].equalsIgnoreCase("set")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be executed by a player.");
                return true;
            }

            // 檢查參數數量
            if (args.length < 3) {
                String message = plugin.getConfig().getString("messages.set_visibility_usage",
                        "&c用法: /wp set <公共|私人> <傳送點名稱>");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                return true;
            }

            // 檢查可見性參數
            String visibility = args[1].toLowerCase();
            if (!visibility.equals("公共") && !visibility.equals("私人")) {
                String message = plugin.getConfig().getString("messages.invalid_visibility",
                        "&c使用權限必須是 '公共' 或 '私人'");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                return true;
            }

            // 獲取並檢查傳送點
            String warpName = args[2];
            Warp warp = Warp.getByName(warpName);
            if (warp == null) {
                player.sendMessage(ChatColor.RED + "找不到傳送點: " + warpName);
                return true;
            }

            // 檢查修改權限
            if (!warp.getCreatorUuid().equals(player.getUniqueId().toString()) &&
                    !player.hasPermission("signwarp.admin")) {
                String message = plugin.getConfig().getString("messages.cant_modify_others_warp",
                        "&c您只能更改自己創建的傳送點！");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                return true;
            }

            // 更新傳送點可見性
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

            // 發送成功訊息
            String message = plugin.getConfig().getString("messages.warp_visibility_changed",
                            "&a傳送點 {warp-name} 的使用權限已更改為{visibility}。")
                    .replace("{warp-name}", warpName)
                    .replace("{visibility}", visibility);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return true;
        }

        sender.sendMessage("Unknown subcommand. Usage: /signwarp <gui/reload/set>");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 第一個參數的自動完成
            String[] commands = {"gui", "reload", "set"};
            for (String cmd : commands) {
                if (cmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            // 第二個參數的自動完成（set 指令後）
            String[] visibilities = {"公共", "私人"};
            for (String v : visibilities) {
                if (v.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(v);
                }
            }
        }
        else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            // 第三個參數的自動完成（傳送點名稱）
            if (sender instanceof Player player) {
                completions.addAll(
                        Warp.getAll().stream()
                                .filter(warp -> warp.getCreatorUuid().equals(player.getUniqueId().toString())
                                        || player.hasPermission("signwarp.admin"))
                                .map(Warp::getName)
                                .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                                .toList()
                );
            }
        }
        return completions;
    }
}