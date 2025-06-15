package com.swim.signwarp;
import com.swim.signwarp.gui.WarpGuiListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class SignWarp extends JavaPlugin implements Listener {

    public void onEnable() {
            // Save default config
            saveDefaultConfig();

            // Initialize database and migrate table if needed
            Warp.createTable();
            Warp.createInvitesTable();// Create the invites table if it doesn't exist
            WarpGroup.createTables();
            // Register commands and tab completer
            PluginCommand command = getCommand("signwarp");
            if (command != null) {
                SWCommand swCommand = new SWCommand(this);
                command.setExecutor(swCommand);
                command.setTabCompleter(swCommand);
            } else {
                getLogger().warning("Command 'signwarp' not found!");
            }

            // Register event listener
            PluginManager pluginManager = getServer().getPluginManager();
            pluginManager.registerEvents(new EventListener(this), this);
            pluginManager.registerEvents(new WarpGuiListener(this), this);
            pluginManager.registerEvents(this, this);
    }
    @Override
    public void onDisable() {
    }
}
