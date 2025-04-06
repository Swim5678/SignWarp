package fr.nbstudio.signwarp.gui;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class WarpGuiCommand implements CommandExecutor {
    private final JavaPlugin plugin;

    public WarpGuiCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            WarpGui.openWarpGui(player, 0);
            return true;
        }
        return false;
    }
}