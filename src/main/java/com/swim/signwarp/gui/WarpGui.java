package com.swim.signwarp.gui;

import com.swim.signwarp.Warp;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WarpGui {

    private static final int ITEMS_PER_PAGE = 45; // 5 rows of items
    private static final ItemStack NEXT_PAGE;
    private static final ItemStack PREVIOUS_PAGE;
    private static final ItemStack FILLER;

    static {
        NEXT_PAGE = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = NEXT_PAGE.getItemMeta();
        nextMeta.setDisplayName(ChatColor.GREEN + "下一頁");
        NEXT_PAGE.setItemMeta(nextMeta);

        PREVIOUS_PAGE = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = PREVIOUS_PAGE.getItemMeta();
        prevMeta.setDisplayName(ChatColor.RED + "上一頁");
        PREVIOUS_PAGE.setItemMeta(prevMeta);

        FILLER = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = FILLER.getItemMeta();
        fillerMeta.setDisplayName(" ");
        FILLER.setItemMeta(fillerMeta);
    }

    public static void openWarpGui(Player player, int page) {
        List<Warp> warps = Warp.getAll();
        int totalWarps = warps.size();
        int totalPages = (int) Math.ceil((double) totalWarps / ITEMS_PER_PAGE);

        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "傳送點管理 - Page " + (page + 1));

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, totalWarps);

        for (int i = start; i < end; i++) {
            Warp warp = warps.get(i);
            ItemStack warpItem = new ItemStack(Material.OAK_SIGN);
            ItemMeta warpMeta = warpItem.getItemMeta();
            warpMeta.setDisplayName(ChatColor.DARK_GREEN + warp.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GOLD + "世界: " + Objects.requireNonNull(warp.getLocation().getWorld()).getName());
            lore.add(ChatColor.YELLOW + "X: " + warp.getLocation().getX());
            lore.add(ChatColor.YELLOW + "Y: " + warp.getLocation().getY());
            lore.add(ChatColor.YELLOW + "Z: " + warp.getLocation().getZ());
            lore.add(ChatColor.DARK_GREEN + "Created: " + warp.getFormattedCreatedAt());
            lore.add(ChatColor.RED + "Click to teleport");
            warpMeta.setLore(lore);
            warpItem.setItemMeta(warpMeta);
            gui.addItem(warpItem);
        }

        // Fill the bottom row with the filler item
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, FILLER);
        }

        // Always add pagination buttons
        gui.setItem(47, PREVIOUS_PAGE);
        gui.setItem(51, NEXT_PAGE);

        player.openInventory(gui);
    }
}