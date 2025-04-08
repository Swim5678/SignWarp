package com.swim.signwarp;

import com.swim.signwarp.utils.SignUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class EventListener implements Listener {
    private final SignWarp plugin;
    private static FileConfiguration config;
    private final HashMap<UUID, BukkitTask> teleportTasks = new HashMap<>();
    private final HashSet<UUID> invinciblePlayers = new HashSet<>();
    private final HashMap<UUID, Integer> pendingItemCosts = new HashMap<>();
    // 傳送冷卻：記錄玩家下次可傳送的時間（毫秒）
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    public EventListener(SignWarp plugin) {
        this.plugin = plugin;
        config = plugin.getConfig();
    }

    // 更新配置檔的靜態方法
    public static void updateConfig(JavaPlugin plugin) {
        config = plugin.getConfig();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        // 可根據需求於插件啟動時加入額外功能
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        SignData signData = new SignData(event.getLines());

        // 僅處理符合傳送標識牌格式的標誌
        if (!signData.isWarpSign()) {
            return;
        }

        Player player = event.getPlayer();

        // 檢查建立標識牌權限
        if (!player.hasPermission("signwarp.create")) {
            String noPermissionMessage = config.getString("messages.create_permission");
            if (noPermissionMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermissionMessage));
            }
            event.setCancelled(true);
            return;
        }

        // 傳送點名稱有效性檢查
        if (!signData.isValidWarpName()) {
            String noWarpNameMessage = config.getString("messages.no_warp_name");
            if (noWarpNameMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', noWarpNameMessage));
            }
            event.setCancelled(true);
            return;
        }

        // 取得已存在的傳送點
        Warp existingWarp = Warp.getByName(signData.warpName);

        // 如果標誌是 WARP（傳送點標誌），則不收費，只需檢查傳送點是否存在
        if (signData.isWarp()) {
            if (existingWarp == null) {
                String warpNotFoundMessage = config.getString("messages.warp_not_found");
                if (warpNotFoundMessage != null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', warpNotFoundMessage));
                }
                event.setCancelled(true);
                return;
            }
            event.setLine(0, ChatColor.BLUE + SignData.HEADER_WARP);
            String warpCreatedMessage = config.getString("messages.warp_created");
            if (warpCreatedMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', warpCreatedMessage));
            }
        }
        // 如果標誌是 WPT（傳送目標），則收取物品費用並建立新的傳送目標
        else if (signData.isWarpTarget()) {
            // 若傳送點名稱已經存在，不允許重複建立
            if (existingWarp != null) {
                String warpNameTakenMessage = config.getString("messages.warp_name_taken");
                if (warpNameTakenMessage != null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', warpNameTakenMessage));
                }
                event.setCancelled(true);
                return;
            }
            // 從配置中取得建立 WPT 所需的物品與扣除數量
            String createWPTItem = config.getString("create-wpt-item", "none");
            int createWPTItemCost = config.getInt("create-wpt-item-cost", 1);
            if (!"none".equalsIgnoreCase(createWPTItem)) {
                Material material = Material.getMaterial(createWPTItem.toUpperCase());
                if (material == null) {
                    player.sendMessage(ChatColor.RED + "配置中指定的物品無效，無法建立 WPT。");
                    event.setCancelled(true);
                    return;
                }
                // 檢查玩家主手是否持有足夠的指定物品
                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                if (itemInHand.getType() != material || itemInHand.getAmount() < createWPTItemCost) {
                    String notEnoughMessage = config.getString("messages.not_enough_item");
                    if (notEnoughMessage != null) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                notEnoughMessage.replace("{use-cost}", String.valueOf(createWPTItemCost))
                                        .replace("{use-item}", createWPTItem)));
                    }
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
            // 建立新的傳送目標
            String currentDateTime = java.time.LocalDateTime.now().toString();
            Warp warp = new Warp(signData.warpName, player.getLocation(), currentDateTime);
            warp.save();
            event.setLine(0, ChatColor.BLUE + SignData.HEADER_TARGET);
            String targetSignCreatedMessage = config.getString("messages.target_sign_created");
            if (targetSignCreatedMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', targetSignCreatedMessage));
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material blockType = block.getType();

        if (!Tag.ALL_SIGNS.isTagged(blockType)) {
            if (hasBlockWarpSign(block)) {
                event.setCancelled(true);
            }
            return;
        }

        Sign signBlock = SignUtils.getSignFromBlock(block);

        if (signBlock == null) {
            return;
        }

        SignData signData = new SignData(signBlock.getSide(Side.FRONT).getLines());

        if (!signData.isWarpTarget()) {
            return;
        }

        if (!signData.isValidWarpName()) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.hasPermission("signwarp.create")) {
            String noPermissionMessage = config.getString("messages.destroy_permission");
            if (noPermissionMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermissionMessage));
            }
            event.setCancelled(true);
            return;
        }

        Warp warp = Warp.getByName(signData.warpName);

        if (warp == null) {
            return;
        }

        warp.remove();

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(config.getString("messages.warp_destroyed"))));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();

        if (block == null) {
            return;
        }

        Sign signBlock = SignUtils.getSignFromBlock(block);

        if (signBlock == null) {
            return;
        }

        SignData signData = new SignData(signBlock.getSide(Side.FRONT).getLines());

        // 標識牌自動鎖定
        if (signData.isWarpSign() && !signBlock.isWaxed()) {
            signBlock.setWaxed(true);
            signBlock.update();
        }

        if (!signData.isWarp() || !signData.isValidWarpName()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 傳送冷卻檢查
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(playerId)) {
            long cooldownEnd = cooldowns.get(playerId);
            if (now < cooldownEnd) {
                long remainingSeconds = (cooldownEnd - now + 999) / 1000;
                String cooldownMessage = config.getString("messages.cooldown", "&cYou must wait {cooldown} seconds before teleporting again.");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', cooldownMessage.replace("{cooldown}", String.valueOf(remainingSeconds))));
                return;
            }
        }

        // 檢查使用傳送權限
        if (!player.hasPermission("signwarp.use")) {
            String noPermissionMessage = config.getString("messages.use_permission");
            if (noPermissionMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermissionMessage));
            }
            return;
        }

        String useItem = config.getString("use-item", "none");
        int useCost = config.getInt("use-cost", 0);

        if ("none".equalsIgnoreCase(useItem)) {
            useItem = null;
        }

        // 如果需要使用特定物品則進行檢查與扣除（點擊時即扣除）
        if (useItem != null) {
            Material requiredMaterial = Material.getMaterial(useItem.toUpperCase());
            if (requiredMaterial == null) {
                player.sendMessage(ChatColor.RED + "配置中指定的使用物品無效。");
                return;
            }
            ItemStack handItem = event.getItem();
            if (handItem == null || handItem.getType() != requiredMaterial) {
                String invalidItemMessage = config.getString("messages.invalid_item");
                if (invalidItemMessage != null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidItemMessage.replace("{use-item}", useItem)));
                }
                return;
            }
            if (handItem.getAmount() < useCost) {
                String notEnoughItemMessage = config.getString("messages.not_enough_item");
                if (notEnoughItemMessage != null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            notEnoughItemMessage.replace("{use-cost}", String.valueOf(useCost)).replace("{use-item}", useItem)));
                }
                return;
            }
            // 立即扣除物品
            int remaining = handItem.getAmount() - useCost;
            if (remaining <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                handItem.setAmount(remaining);
                player.getInventory().setItemInMainHand(handItem);
            }
            // 記錄已扣除數量，用於取消傳送時返還
            pendingItemCosts.put(player.getUniqueId(), useCost);
            teleportPlayer(player, signData.warpName);
        } else {
            // 若不需物品則直接傳送
            teleportPlayer(player, signData.warpName);
        }
    }

    private void teleportPlayer(Player player, String warpName) {
        Warp warp = Warp.getByName(warpName);

        if (warp == null) {
            String warpNotFoundMessage = config.getString("messages.warp_not_found");
            if (warpNotFoundMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', warpNotFoundMessage));
            }
            return;
        }

        // 讀取傳送延遲（單位：秒）
        int teleportDelay = config.getInt("teleport-delay", 5);

        String teleportMessage = config.getString("messages.teleport");
        if (teleportMessage != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    teleportMessage.replace("{warp-name}", warp.getName()).replace("{time}", String.valueOf(teleportDelay))));
        }

        UUID playerUUID = player.getUniqueId();

        // 取消之前的傳送任務（若有）
        BukkitTask previousTask = teleportTasks.get(playerUUID);
        if (previousTask != null) {
            previousTask.cancel();
        }

        // 加入無敵列表，防止傳送期間受傷
        invinciblePlayers.add(playerUUID);

        // 在傳送前先記錄玩家周圍20格內，被牽引且牽引者為玩家的生物
        Collection<Entity> leashedEntities = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
            if (entity instanceof LivingEntity livingEntity) {
                if (livingEntity.isLeashed() && livingEntity.getLeashHolder().equals(player)) {
                    leashedEntities.add(livingEntity);
                }
            }
        }

        // 排程傳送任務（延遲後執行）
        BukkitTask teleportTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location targetLocation = warp.getLocation();

            // 傳送玩家
            player.teleport(targetLocation);

            // 傳送之前記錄的所有生物
            for (Entity entity : leashedEntities) {
                entity.teleport(targetLocation);
            }

            // 播放傳送音效與特效
            String soundName = config.getString("teleport-sound", "ENTITY_ENDERMAN_TELEPORT");
            String effectName = config.getString("teleport-effect", "ENDER_SIGNAL");

            Sound sound = Sound.valueOf(soundName);
            Effect effect = Effect.valueOf(effectName);

            World world = targetLocation.getWorld();
            world.playSound(targetLocation, sound, 1, 1);
            world.playEffect(targetLocation, effect, 10);

            String successMessage = config.getString("messages.teleport-success");
            if (successMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMessage.replace("{warp-name}", warp.getName())));
            }

            // 傳送成功後，清除該玩家的待扣記錄（不需再扣除）
            pendingItemCosts.remove(playerUUID);

            // 設置傳送完成後的冷卻，從配置讀取（單位：秒，預設5秒）
            int useCooldown = config.getInt("teleport-use-cooldown", 5);
            cooldowns.put(playerUUID, System.currentTimeMillis() + useCooldown * 1000L);

            // 移除傳送任務記錄和無敵狀態
            teleportTasks.remove(playerUUID);
            invinciblePlayers.remove(playerUUID);

        }, teleportDelay * 20L); // 每秒20 tick

        teleportTasks.put(playerUUID, teleportTask);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (teleportTasks.containsKey(playerUUID)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                BukkitTask teleportTask = teleportTasks.get(playerUUID);
                if (teleportTask != null && !teleportTask.isCancelled()) {
                    teleportTask.cancel();
                    teleportTasks.remove(playerUUID);
                    invinciblePlayers.remove(playerUUID);

                    // 傳送取消，返還已扣除的物品
                    if (pendingItemCosts.containsKey(playerUUID)) {
                        String useItem = config.getString("use-item", "none");
                        if (!"none".equalsIgnoreCase(useItem)) {
                            Material material = Material.getMaterial(useItem.toUpperCase());
                            if (material != null) {
                                int cost = pendingItemCosts.get(playerUUID);
                                player.getInventory().addItem(new ItemStack(material, cost));
                            }
                        }
                        pendingItemCosts.remove(playerUUID);
                    }

                    String cancelMessage = config.getString("messages.teleport-cancelled", "&cTeleportation cancelled.");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', cancelMessage));
                }
            }
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
