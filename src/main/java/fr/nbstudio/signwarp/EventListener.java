package fr.nbstudio.signwarp;

import fr.nbstudio.signwarp.utils.SignUtils;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class EventListener implements Listener {
    private final SignWarp plugin;
    private static FileConfiguration config;
    private final HashMap<UUID, BukkitTask> teleportTasks = new HashMap<>();
    private final HashSet<UUID> invinciblePlayers = new HashSet<>();
    private final HashMap<UUID, Integer> pendingItemCosts = new HashMap<>();

    public EventListener(SignWarp plugin) {
        this.plugin = plugin;
        config = plugin.getConfig();
    }

    // 提供外部更新 config 的方法
    public static void updateConfig(JavaPlugin plugin) {
        config = plugin.getConfig();
    }

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

        // 若標誌為 WP（Warp），則不收費，只需檢查目標是否存在
        if (signData.isWarp()) {
            if (existingWarp == null) {
                sendMessage(player, "messages.warp_not_found");
                event.setCancelled(true);
                return;
            }
            event.setLine(0, ChatColor.BLUE + SignData.HEADER_WARP);
            sendMessage(player, "messages.warp_created");
        }
        // 若標誌為 WPT（Warp Target），則建立新目標並扣除物品
        else if (signData.isWarpTarget()) {
            // 從配置中取得建立 WPT 所需的物品（金錢扣除已移除）
            String createWPTItem = config.getString("create-wpt-item", "none");
            int createWPTItemCost = config.getInt("create-wpt-item-cost", 1);
            // 若設定了物品扣除，僅從玩家主手檢查與扣除（例如綠寶石）
            if (!"none".equalsIgnoreCase(createWPTItem)) {
                Material material = Material.getMaterial(createWPTItem.toUpperCase());
                if (material == null) {
                    player.sendMessage(ChatColor.RED + "配置中指定的物品無效，無法建立 WPT。");
                    event.setCancelled(true);
                    return;
                }
                // 檢查玩家主手是否持有足夠的該物品
                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                if (itemInHand == null || itemInHand.getType() != material || itemInHand.getAmount() < createWPTItemCost) {
                    sendMessageReplace(player, "messages.not_enough_item", "{use-cost}", String.valueOf(createWPTItemCost), "{use-item}", createWPTItem);
                    event.setCancelled(true);
                    return;
                }
                // 從玩家主手扣除指定數量的物品
                int remaining = itemInHand.getAmount() - createWPTItemCost;
                if (remaining <= 0) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    itemInHand.setAmount(remaining);
                    player.getInventory().setItemInMainHand(itemInHand);
                }
            }
            // 金錢扣除部分已移除，僅建立新的 Warp 目標
            String currentDateTime = java.time.LocalDateTime.now().toString();
            Warp warp = new Warp(signData.warpName, player.getLocation(), currentDateTime);
            warp.save();
            event.setLine(0, ChatColor.BLUE + SignData.HEADER_TARGET);
            sendMessage(player, "messages.target_sign_created");
        }
    }

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
        // 處理傳送時的物品扣除（僅支援物品消耗，不收金錢）
        String useItem = config.getString("use-item", "none");
        int useCost = config.getInt("use-cost", 0);
        if ("none".equalsIgnoreCase(useItem)) {
            useItem = null;
        }
        if (useItem != null) {
            Material itemInHand = event.getMaterial();
            if (itemInHand != null && itemInHand.name().equalsIgnoreCase(useItem)) {
                if (useCost > event.getItem().getAmount()) {
                    sendMessageReplace(player, "messages.not_enough_item", "{use-cost}", String.valueOf(useCost), "{use-item}", useItem);
                    return;
                }
                // 從玩家主手扣除使用物品（這裡以物品使用時扣除作為示範）
                ItemStack hand = player.getInventory().getItemInMainHand();
                int remaining = hand.getAmount() - useCost;
                if (remaining <= 0) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    hand.setAmount(remaining);
                    player.getInventory().setItemInMainHand(hand);
                }
                teleportPlayer(player, signData.warpName);
            } else {
                sendMessageReplace(player, "messages.invalid_item", "{use-item}", useItem);
            }
        } else {
            // 若未設定使用物品，直接傳送
            teleportPlayer(player, signData.warpName);
        }
    }

    // 處理傳送，僅進行傳送動作，不進行金錢扣除
    private void teleportPlayer(Player player, String warpName) {
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendMessage(player, "messages.warp_not_found");
            return;
        }
        player.teleport(warp.getLocation());
        sendMessageReplace(player, "messages.teleport_message", "{warp-name}", warpName);
    }

    // 依據配置路徑發送訊息
    private void sendMessage(Player player, String configPath) {
        String msg = config.getString(configPath);
        if (msg != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    // 支援參數替換後發送訊息
    private void sendMessageReplace(Player player, String configPath, String placeholder, String value, String... extra) {
        String msg = config.getString(configPath);
        if (msg != null) {
            msg = msg.replace(placeholder, value);
            for (int i = 0; i < extra.length - 1; i += 2) {
                msg = msg.replace(extra[i], extra[i + 1]);
            }
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (teleportTasks.containsKey(player.getUniqueId())) {
            BukkitTask task = teleportTasks.get(player.getUniqueId());
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            teleportTasks.remove(player.getUniqueId());
            invinciblePlayers.remove(player.getUniqueId());
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
