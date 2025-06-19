package com.swim.signwarp.gui;

import com.swim.signwarp.Warp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
        // 使用 Adventure API 取得標題並轉換為純文字進行比較
        Component titleComponent = event.getView().title();
        String title = PlainTextComponentSerializer.plainText().serialize(titleComponent);

        if (title.startsWith("傳送點管理")) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            Player player = (Player) event.getWhoClicked();
            String[] titleParts = title.split(" ");
            int currentPage;
            try {
                currentPage = Integer.parseInt(titleParts[titleParts.length - 1]) - 1;
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("確定當前頁面時發生錯誤。").color(NamedTextColor.RED));
                return;
            }

            if (clickedItem.getType() == Material.ARROW) {
                // 使用新的 Adventure API 方式取得顯示名稱
                Component displayNameComponent = Objects.requireNonNull(clickedItem.getItemMeta()).displayName();
                if (displayNameComponent != null) {
                    String displayName = PlainTextComponentSerializer.plainText().serialize(displayNameComponent);

                    if (displayName.equals("下一頁")) {
                        int totalWarps = Warp.getAll().size();
                        int totalPages = (int) Math.ceil((double) totalWarps / 45);
                        if (currentPage + 1 < totalPages) {
                            WarpGui.openWarpGui(player, currentPage + 1);
                        }
                    } else if (displayName.equals("上一頁")) {
                        if (currentPage > 0) {
                            WarpGui.openWarpGui(player, currentPage - 1);
                        }
                    }
                }
            } else if (clickedItem.getType() == Material.OAK_SIGN) {
                // 使用新的 Adventure API 方式處理傳送點名稱
                Component displayNameComponent = Objects.requireNonNull(clickedItem.getItemMeta()).displayName();
                if (displayNameComponent != null) {
                    String warpName = PlainTextComponentSerializer.plainText().serialize(displayNameComponent);
                    Warp warp = Warp.getByName(warpName);
                    if (warp != null) {
                        player.teleport(warp.getLocation());
                        // 使用 Adventure API 發送訊息
                        Component message = Component.text()
                                .append(Component.text("已傳送到 ").color(NamedTextColor.GREEN))
                                .append(Component.text(warp.getName()).color(NamedTextColor.YELLOW))
                                .append(Component.text(" (建立者: ").color(NamedTextColor.GREEN))
                                .append(Component.text(warp.getCreator()).color(NamedTextColor.AQUA))
                                .append(Component.text(")").color(NamedTextColor.GREEN))
                                .build();
                        player.sendMessage(message);
                        player.closeInventory();
                    } else {
                        Component errorMessage = Component.text()
                                .append(Component.text("找不到傳送點: ").color(NamedTextColor.RED))
                                .append(Component.text(warpName).color(NamedTextColor.YELLOW))
                                .build();
                        player.sendMessage(errorMessage);
                    }
                }
            }
        }
    }
}