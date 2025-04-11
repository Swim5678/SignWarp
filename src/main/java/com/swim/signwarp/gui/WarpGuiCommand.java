package com.swim.signwarp.gui;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class WarpGuiCommand implements CommandExecutor {

    public WarpGuiCommand(JavaPlugin plugin) {
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (sender instanceof Player player) {
            WarpGui.openWarpGui(player, 0);
            return true;
        }
        return false;
    }
}