package com.swim.signwarp.gui;

import com.swim.signwarp.Warp;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class WarpGuiListener implements Listener {

    public WarpGuiListener(JavaPlugin plugin) {
        // 可在此處進行相關初始化
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith(ChatColor.DARK_BLUE + "傳送點管理")) {
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
                String displayName = Objects.requireNonNull(clickedItem.getItemMeta()).getDisplayName();
                if (displayName.equals(ChatColor.GREEN + "下一頁")) {
                    int totalWarps = Warp.getAll().size();
                    int totalPages = (int) Math.ceil((double) totalWarps / 45);
                    if (currentPage + 1 < totalPages) {
                        WarpGui.openWarpGui(player, currentPage + 1);
                    }
                } else if (displayName.equals(ChatColor.RED + "上一頁")) {
                    if (currentPage > 0) {
                        WarpGui.openWarpGui(player, currentPage - 1);
                    }
                }
            } else if (clickedItem.getType() == Material.OAK_SIGN) {
                String warpName = ChatColor.stripColor(Objects.requireNonNull(clickedItem.getItemMeta()).getDisplayName());
                Warp warp = Warp.getByName(warpName);
                if (warp != null) {
                    player.teleport(warp.getLocation());
                    // 修改傳送訊息中加入創建者資訊的顯示
                    player.sendMessage(ChatColor.GREEN + "Teleported to " + warp.getName() +
                            " (created by " + warp.getCreator() + ")");
                    player.closeInventory();
                } else {
                    player.sendMessage(ChatColor.RED + "Warp not found: " + warpName);
                }
            }
        }
    }
}