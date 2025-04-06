package fr.nbstudio.signwarp;
import fr.nbstudio.signwarp.gui.WarpGuiListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class SignWarp extends JavaPlugin implements Listener {

    private static final int RESOURCE_ID = 116195;
    private static final String PLUGIN_URL = "https://www.spigotmc.org/resources/signwarp-teleport-using-the-signs." + RESOURCE_ID + "/";

    public void onEnable() {
        // Check for updates
        new UpdateChecker(this, RESOURCE_ID).getVersion(version -> {
            if (this.getDescription().getVersion().equals(version)) {
                getLogger().info("沒有可用的新版本");
            } else {
                getLogger().warning("該插件的新版本現已推出： " + version + " （目前的： " + this.getDescription().getVersion() + "）。在這裡下載： " + PLUGIN_URL);
            }
        });

        // Save default config
        saveDefaultConfig();


        // Initialize database and migrate table if needed
        Warp.createTable();

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

//    @EventHandler
//    public void onPlayerJoin(PlayerJoinEvent event) {
//        Player player = event.getPlayer();
//
//        if (player.isOp()) {
//        new UpdateChecker(this, RESOURCE_ID).getVersion(version -> {
//            if (!this.getDescription().getVersion().equals(version)) {
//                player.sendMessage(
//                        ChatColor.DARK_RED + "⚠ A new version of SignWarp is available: " +
//                                ChatColor.RED + version +
//                                " (current: " + this.getDescription().getVersion() + "). " +
//                                ChatColor.DARK_RED + "Download it here: " + ChatColor.RED + PLUGIN_URL
//                );
//                }
//            });
//        }
//    } for the soon release

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}