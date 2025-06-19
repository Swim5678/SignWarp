package com.swim.signwarp.gui;

import com.swim.signwarp.Warp;
import com.swim.signwarp.WarpInvite;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

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
        Objects.requireNonNull(nextMeta).displayName(Component.text("下一頁").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        NEXT_PAGE.setItemMeta(nextMeta);

        PREVIOUS_PAGE = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = PREVIOUS_PAGE.getItemMeta();
        Objects.requireNonNull(prevMeta).displayName(Component.text("上一頁").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        PREVIOUS_PAGE.setItemMeta(prevMeta);

        FILLER = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = FILLER.getItemMeta();
        Objects.requireNonNull(fillerMeta).displayName(Component.text(" "));
        FILLER.setItemMeta(fillerMeta);
    }

    public static void openWarpGui(Player player, int page) {
        List<Warp> warps = Warp.getAll();
        int totalWarps = warps.size();
        int totalPages = (int) Math.ceil((double) totalWarps / ITEMS_PER_PAGE);

        Component title = Component.text("傳送點管理 - Page " + (page + 1)).color(NamedTextColor.DARK_BLUE);
        Inventory gui = Bukkit.createInventory(null, 54, title);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, totalWarps);

        for (int i = start; i < end; i++) {
            Warp warp = warps.get(i);
            ItemStack warpItem = new ItemStack(Material.OAK_SIGN);
            ItemMeta warpMeta = warpItem.getItemMeta();
            Objects.requireNonNull(warpMeta).displayName(
                    Component.text(warp.getName())
                            .color(NamedTextColor.DARK_GREEN)
                            .decoration(TextDecoration.ITALIC, false)
            );
            List<Component> lore = getComponents(warp);
            warpMeta.lore(lore);
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

    private static @NotNull List<Component> getComponents(Warp warp) {
        List<Component> lore = new ArrayList<>();

        lore.add(Component.text("世界: " + Objects.requireNonNull(warp.getLocation().getWorld()).getName())
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        // 將 XYZ 座標顯示在同一行，並四捨五入到整數位
        lore.add(Component.text("座標: " +
                        Math.round(warp.getLocation().getX()) + ", " +
                        Math.round(warp.getLocation().getY()) + ", " +
                        Math.round(warp.getLocation().getZ()))
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        lore.add(Component.text("建立時間: " + warp.getFormattedCreatedAt())
                .color(NamedTextColor.DARK_GREEN)
                .decoration(TextDecoration.ITALIC, false));

        lore.add(Component.text("建立者: " + warp.getCreator())
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        lore.add(Component.text("狀態: " + (warp.isPrivate() ? "私人" : "公共"))
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        // 新增：顯示被邀請的玩家
        if (warp.isPrivate()) {
            List<WarpInvite> invites = warp.getInvitedPlayers();
            if (!invites.isEmpty()) {
                lore.add(Component.text("已邀請玩家:")
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false));
                for (WarpInvite invite : invites) {
                    lore.add(Component.text("- " + invite.invitedName())
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                }
            }
        }

        lore.add(Component.text("點擊傳送")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        return lore;
    }
}