package fr.nbstudio.signwarp.gui;

import fr.nbstudio.signwarp.Warp;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class WarpGuiListener implements Listener {
    private final JavaPlugin plugin;

    public WarpGuiListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith(ChatColor.DARK_BLUE + "Warps Admin")) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            Player player = (Player) event.getWhoClicked();
            String[] titleParts = event.getView().getTitle().split(" ");
            int currentPage;
            try {
                currentPage = Integer.parseInt(titleParts[titleParts.length - 1]) - 1;
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "An error occurred while determining the current page.");
                return;
            }

            if (clickedItem.getType() == Material.ARROW) {
                if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.GREEN + "Next Page")) {
                    int totalWarps = Warp.getAll().size();
                    int totalPages = (int) Math.ceil((double) totalWarps / 45);
                    if (currentPage + 1 < totalPages) {
                        WarpGui.openWarpGui(player, currentPage + 1);
                    }
                } else if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.RED + "Previous Page")) {
                    if (currentPage > 0) {
                        WarpGui.openWarpGui(player, currentPage - 1);
                    }
                }
            } else if (clickedItem.getType() == Material.OAK_SIGN) {
                String warpName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                Warp warp = Warp.getByName(warpName);
                if (warp != null) {
                    player.teleport(warp.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Teleported to " + warp.getName());
                    player.closeInventory();
                } else {
                    player.sendMessage(ChatColor.RED + "Warp not found: " + warpName);
                }
            }
        }
    }
}