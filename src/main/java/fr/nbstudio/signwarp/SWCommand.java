package fr.nbstudio.signwarp;

import fr.nbstudio.signwarp.gui.WarpGui;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class SWCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;

    public SWCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /signwarp <gui|reload>");
            return true;
        }

        if (args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
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
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.not_permission", "You don't have permission to use this command.")));
            }
            return true;
        }

        sender.sendMessage("Unknown subcommand. Usage: /signwarp <gui|reload>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("gui".startsWith(args[0].toLowerCase())) {
                completions.add("gui");
            }
            if ("reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
        }
        return completions;
    }
}
