package fr.nbstudio.signwarp;

import fr.nbstudio.signwarp.utils.SignUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class EventListener implements Listener {
    private final SignWarp plugin;
    private static FileConfiguration config;
    // 用來記錄傳送任務與等待扣款
    private final HashMap<UUID, BukkitTask> teleportTasks = new HashMap<>();
    private final HashSet<UUID> invinciblePlayers = new HashSet<>();
    private final HashMap<UUID, Double> pendingTeleportCosts = new HashMap<>();
    private final HashMap<UUID, Integer> pendingItemCosts = new HashMap<>();

    public EventListener(SignWarp plugin) {
        this.plugin = plugin;
        config = plugin.getConfig();
    }

    // 提供外部更新 config 的方法
    public static void updateConfig(JavaPlugin plugin) {
        config = plugin.getConfig();
    }

    // 當玩家在標誌上輸入文字時觸發
    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        SignData signData = new SignData(event.getLines());
        // 僅處理符合傳送門格式的標誌
        if (!signData.isWarpSign()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("signwarp.create")) {
            sendMessage(player, "messages.create_permission");
            event.setCancelled(true);
            return;
        }
        if (!signData.isValidWarpName()) {
            sendMessage(player, "messages.no_warp_name");
            event.setCancelled(true);
            return;
        }

        // 取得已有的 Warp 物件（若存在的話）
        Warp existingWarp = Warp.getByName(signData.warpName);

        // 若標誌為 WP（傳送目標），則不收費，只需檢查目標是否存在
        if (signData.isWarp()) {
            if (existingWarp == null) {
                sendMessage(player, "messages.warp_not_found");
                event.setCancelled(true);
                return;
            }
            event.setLine(0, ChatColor.BLUE + SignData.HEADER_WARP);
            sendMessage(player, "messages.warp_created");
        }
        // 若標誌為 WPT（Warp Target），則建立新目標並收費
        else if (signData.isWarpTarget()) {
            // 從配置中取得建立 WPT 所需的物品與金錢
            String createWPTItem = config.getString("create-wpt-item", "none");
            int createWPTItemCost = config.getInt("create-wpt-item-cost", 1);
            double createWPTTeleportCost = config.getDouble("create-wpt-teleport-cost", 0.0);

            // 若設定了物品扣除
            if (!"none".equalsIgnoreCase(createWPTItem)) {
                Material material = Material.getMaterial(createWPTItem.toUpperCase());
                if (material == null) {
                    player.sendMessage(ChatColor.RED + "Invalid item specified in config for WPT creation.");
                    event.setCancelled(true);
                    return;
                }
                if (!player.getInventory().contains(material, createWPTItemCost)) {
                    sendMessageReplace(player, "messages.not_enough_item", "{use-item}", createWPTItem, "{use-cost}", String.valueOf(createWPTItemCost));
                    event.setCancelled(true);
                    return;
                }
                // 扣除指定數量的物品
                player.getInventory().removeItem(new ItemStack(material, createWPTItemCost));
            }
            // 若設定了金錢扣除
            if (createWPTTeleportCost > 0) {
                Economy economy = VaultEconomy.getEconomy();
                if (economy == null) {
                    player.sendMessage(ChatColor.RED + "Vault is required for money deduction but is not enabled.");
                    event.setCancelled(true);
                    return;
                }
                if (economy.getBalance(player) < createWPTTeleportCost) {
                    player.sendMessage(ChatColor.RED + "You don't have enough money to create a Warp Target.");
                    event.setCancelled(true);
                    return;
                }
                economy.withdrawPlayer(player, createWPTTeleportCost);
                sendMessageReplace(player, "messages.teleport_cost", "{cost}", String.valueOf(createWPTTeleportCost));
            }
            // 建立新的 Warp 目標
            String currentDateTime = java.time.LocalDateTime.now().toString();
            Warp warp = new Warp(signData.warpName, player.getLocation(), currentDateTime);
            warp.save();
            event.setLine(0, ChatColor.BLUE + SignData.HEADER_TARGET);
            sendMessage(player, "messages.target_sign_created");
        }
    }

    // 處理玩家點擊標誌後的傳送動作（此處僅保留原有傳送邏輯）
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Sign signBlock = SignUtils.getSignFromBlock(block);
        if (signBlock == null) return;
        SignData signData = new SignData(signBlock.getSide(Side.FRONT).getLines());

        // 自動將尚未鎖定（waxed）的標誌鎖定，避免重複使用
        if (signData.isWarpSign() && !signBlock.isWaxed()) {
            signBlock.setWaxed(true);
            signBlock.update();
        }
        if (!signData.isWarp() || !signData.isValidWarpName()) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("signwarp.use")) {
            sendMessage(player, "messages.use_permission");
            return;
        }
        // 依照配置處理傳送費用或物品扣除
        double teleportCost = config.getDouble("teleport-cost", 0.0);
        String useItem = config.getString("use-item", "none");
        int useCost = config.getInt("use-cost", 0);
        if ("none".equalsIgnoreCase(useItem)) {
            useItem = null;
        }
        if (teleportCost > 0 && useItem == null) {
            Economy economy = VaultEconomy.getEconomy();
            if (economy == null) {
                player.sendMessage(ChatColor.RED + "Vault is required for teleport cost but is not available.");
                return;
            }
            if (economy.getBalance(player) < teleportCost) {
                player.sendMessage(ChatColor.RED + "You don't have enough money to teleport.");
                return;
            }
            pendingTeleportCosts.put(player.getUniqueId(), teleportCost);
            teleportPlayer(player, signData.warpName, true, teleportCost);
        } else if (teleportCost == 0.0 && useItem != null) {
            Material itemInHand = event.getMaterial();
            if (itemInHand != null && itemInHand.name().equalsIgnoreCase(useItem)) {
                if (useCost > event.getItem().getAmount()) {
                    sendMessageReplace(player, "messages.not_enough_item", "{use-cost}", String.valueOf(useCost), "{use-item}", useItem);
                    return;
                }
                pendingItemCosts.put(player.getUniqueId(), useCost);
                teleportPlayer(player, signData.warpName, false, 0);
            } else {
                sendMessageReplace(player, "messages.invalid_item", "{use-item}", useItem);
            }
        } else if (teleportCost == 0.0 && useItem == null) {
            teleportPlayer(player, signData.warpName, false, 0);
        } else {
            player.sendMessage(ChatColor.RED + "You must use an item or pay to teleport.");
        }
    }

    // 簡化傳送訊息與傳送處理（此處不做冷卻與取消動作，可依需求擴充）
    private void teleportPlayer(Player player, String warpName, boolean useEconomy, double cost) {
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendMessage(player, "messages.warp_not_found");
            return;
        }
        if (useEconomy) {
            Economy economy = VaultEconomy.getEconomy();
            economy.withdrawPlayer(player, cost);
            sendMessageReplace(player, "messages.teleport_cost", "{cost}", String.valueOf(cost));
        }
        player.teleport(warp.getLocation());
        sendMessageReplace(player, "messages.teleport_message", "{warp-name}", warpName);
    }

    // 提供簡單的訊息發送工具：依據配置檔中的路徑取得訊息
    private void sendMessage(Player player, String configPath) {
        String msg = config.getString(configPath);
        if (msg != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }
    // 提供可替換參數的訊息發送工具
    private void sendMessageReplace(Player player, String configPath, String placeholder, String value, String... extra) {
        String msg = config.getString(configPath);
        if (msg != null) {
            msg = msg.replace(placeholder, value);
            // 處理額外的替換（若有偶數個 extra 字串）
            for (int i = 0; i < extra.length - 1; i += 2) {
                msg = msg.replace(extra[i], extra[i + 1]);
            }
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    // 以下保留其他事件處理（例如移動、傷害、活塞、爆炸等），可根據需要擴充
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 當玩家移動時若正在傳送則取消任務（若有實作冷卻功能，可在此處理）
        Player player = event.getPlayer();
        if (teleportTasks.containsKey(player.getUniqueId())) {
            BukkitTask task = teleportTasks.get(player.getUniqueId());
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            teleportTasks.remove(player.getUniqueId());
            invinciblePlayers.remove(player.getUniqueId());
            pendingTeleportCosts.remove(player.getUniqueId());
            pendingItemCosts.remove(player.getUniqueId());
            sendMessage(player, "messages.teleport_cancelled");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (invinciblePlayers.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (hasBlockWarpSign(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (hasBlockWarpSign(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (hasBlockWarpSign(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (hasBlockWarpSign(event.blockList())) {
            event.setCancelled(true);
        }
    }

    // 輔助方法：檢查區塊或區塊清單中是否包含 Warp 標誌
    private boolean hasBlockWarpSign(Block block) {
        return SignUtils.hasBlockSign(block, this::isWarpSign);
    }
    private boolean hasBlockWarpSign(List<Block> blocks) {
        return SignUtils.hasBlockSign(blocks, this::isWarpSign);
    }
    private boolean isWarpSign(Sign signBlock) {
        SignData signData = new SignData(signBlock.getSide(Side.FRONT).getLines());
        return signData.isWarpSign();
    }
}
