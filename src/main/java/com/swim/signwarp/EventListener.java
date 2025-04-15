package com.swim.signwarp;

import com.swim.signwarp.utils.SignUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;

public class EventListener implements Listener {
    private final SignWarp plugin;
    private static FileConfiguration config;

    // 儲存傳送任務（排程）
    private final HashMap<UUID, BukkitTask> teleportTasks = new HashMap<>();
    // 傳送期間無敵玩家，避免在延遲中受傷
    private final HashSet<UUID> invinciblePlayers = new HashSet<>();
    // 暫存扣除的物品數量（用於傳送取消時返還）
    private final HashMap<UUID, Integer> pendingItemCosts = new HashMap<>();
    // 傳送冷卻：記錄玩家下次可傳送的時間（毫秒）
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    // 配置檔中傳送物品檢查旗標，若無效則停用對應功能
    private boolean validCreateWPTItem = true;
    private boolean validUseItem = true;

    public EventListener(SignWarp plugin) {
        this.plugin = plugin;
        config = plugin.getConfig();
    }

    // 更新配置檔的靜態方法
    public static void updateConfig(JavaPlugin plugin) {
        config = plugin.getConfig();
    }

    /**
     * 當插件啟動時，檢查配置檔設定項的合法性，並啟動定時清理任務。
     */
    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        // 驗證配置檔中的 create-wpt-item 與 use-item 是否設定有效
        validateConfigItems();

        // 啟動冷卻記錄的定時清理任務（例如每5分鐘清理一次過期冷卻）
        startCooldownCleanupTask();
    }

    /**
     * 檢查配置檔中指定的物品名稱是否有效，若無效則記錄日誌並停用相關功能。
     */
    private void validateConfigItems() {
        String createWPTItem = config.getString("create-wpt-item", "none");
        if (!"none".equalsIgnoreCase(createWPTItem)) {
            Material material = Material.getMaterial(createWPTItem.toUpperCase());
            if (material == null) {
                plugin.getLogger().log(Level.SEVERE, "[SignWarp] create-wpt-item 配置錯誤：無效的物品名稱 '" + createWPTItem + "'. 關聯傳送目標創建功能已停用。");
                validCreateWPTItem = false;
            }
        }
        String useItem = config.getString("use-item", "none");
        if (!"none".equalsIgnoreCase(useItem)) {
            Material material = Material.getMaterial(useItem.toUpperCase());
            if (material == null) {
                plugin.getLogger().log(Level.SEVERE, "[SignWarp] use-item 配置錯誤：無效的物品名稱 '" + useItem + "'. 關聯傳送使用功能已停用。");
                validUseItem = false;
            }
        }
    }

    /**
     * 啟動定時任務，定期從 cooldowns 中移除已過期的記錄，
     * 減少記憶體占用（每5分鐘一次）。
     */
    private void startCooldownCleanupTask() {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            cooldowns.values().removeIf(cooldownEnd -> cooldownEnd <= now);
        }, 6000L, 6000L);
    }

    /**
     * 處理標識牌建立事件，包含建立傳送門(WARP)與傳送目標(WPT)的邏輯。
     */
    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        SignData signData = new SignData(event.getLines());
        if (!signData.isWarpSign()) {
            return;
        }
        Player player = event.getPlayer();
        Block signBlock = event.getBlock();

        Material supportType;
        if (signBlock.getBlockData() instanceof WallSign wallSign) {
            Block attachedBlock = signBlock.getRelative(wallSign.getFacing().getOppositeFace());
            supportType = attachedBlock.getType();
        } else {
            Block attachedBlock = signBlock.getRelative(BlockFace.DOWN);
            supportType = attachedBlock.getType();
        }

        if (isGravityAffected(supportType)) {
            String msg = ChatColor.RED + "無法建立在沙子、礫石等重力方塊上！";
            player.sendMessage(msg);
            event.setCancelled(true);
            return;
        }

        if (!player.hasPermission("signwarp.create")) {
            String noPermissionMessage = config.getString("messages.create_permission");
            if (noPermissionMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermissionMessage));
            }
            event.setCancelled(true);
            return;
        }

        if (!signData.isValidWarpName()) {
            String noWarpNameMessage = config.getString("messages.no_warp_name");
            if (noWarpNameMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', noWarpNameMessage));
            }
            event.setCancelled(true);
            return;
        }

        Warp existingWarp = Warp.getByName(signData.warpName);

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
        } else if (signData.isWarpTarget()) {
            if (existingWarp != null) {
                String warpNameTakenMessage = config.getString("messages.warp_name_taken");
                if (warpNameTakenMessage != null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', warpNameTakenMessage));
                }
                event.setCancelled(true);
                return;
            }
            if (!validCreateWPTItem) {
                player.sendMessage(ChatColor.RED + "建立傳送目標功能暫停，請聯繫管理員。");
                event.setCancelled(true);
                return;
            }
            String createWPTItem = config.getString("create-wpt-item", "none");
            int createWPTItemCost = config.getInt("create-wpt-item-cost", 1);
            if (!"none".equalsIgnoreCase(createWPTItem)) {
                Material material = Material.getMaterial(createWPTItem.toUpperCase());
                if (material == null) {
                    player.sendMessage(ChatColor.RED + "配置中指定的物品無效，無法建立傳送目標。");
                    event.setCancelled(true);
                    return;
                }
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
                int remaining = itemInHand.getAmount() - createWPTItemCost;
                if (remaining <= 0) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    itemInHand.setAmount(remaining);
                    player.getInventory().setItemInMainHand(itemInHand);
                }
            }
            String currentDateTime = LocalDateTime.now().toString();
            Warp warp = new Warp(signData.warpName, player.getLocation(), currentDateTime);
            warp.save();
            event.setLine(0, ChatColor.BLUE + SignData.HEADER_TARGET);
            String targetSignCreatedMessage = config.getString("messages.target_sign_created");
            if (targetSignCreatedMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', targetSignCreatedMessage));
            }
        }
    }

    /**
     * 處理破壞標識牌事件。
     */
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
        if (!signData.isWarpTarget() || !signData.isValidWarpName()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("signwarp.destroy")) {
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
        String destroyedMsg = config.getString("messages.warp_destroyed");
        if (destroyedMsg != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', destroyedMsg));
        }
    }

    /**
     * 處理玩家點擊標識牌傳送事件。
     */
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
        if (signData.isWarpSign() && !signBlock.isWaxed()) {
            signBlock.setWaxed(true);
            signBlock.update();
        }
        if (!signData.isWarp() || !signData.isValidWarpName()) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
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
        if (useItem != null && !validUseItem) {
            player.sendMessage(ChatColor.RED + "傳送使用功能暫停，請聯繫管理員。");
            return;
        }
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
            int remaining = handItem.getAmount() - useCost;
            if (remaining <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                handItem.setAmount(remaining);
                player.getInventory().setItemInMainHand(handItem);
            }
            pendingItemCosts.put(player.getUniqueId(), useCost);
        }
        teleportPlayer(player, signData.warpName);
    }

    /**
     * 處理傳送邏輯：
     * - 檢查傳送目標是否存在，若不存在則記錄日誌並通知玩家。
     * - 設定傳送延遲、無敵狀態與特效。
     * - 當附近有符合條件的船隻時：
     *   * 先傳送點擊告示牌的玩家，
     *   * 檢查該船上所有乘客，將非點擊該告示牌的玩家移除（不會傳送），其他非玩家實體將暫時解除載具關係、傳送後再恢復。
     * - 同時支援玩家騎乘馬匹：先傳送玩家，再傳送馬匹並復原騎乘狀態。
     * - 傳送成功後設定冷卻並清理相關記錄。
     *
     * @param player   傳送玩家
     * @param warpName 傳送點名稱
     */
    private void teleportPlayer(Player player, String warpName) {
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            plugin.getLogger().log(Level.WARNING, "[SignWarp] 傳送失敗：玩家 " + player.getName()
                    + " 試圖傳送至不存在的傳送點 '" + warpName + "'.");
            String warpNotFoundMessage = config.getString("messages.warp_not_found");
            if (warpNotFoundMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', warpNotFoundMessage));
            }
            return;
        }
        int teleportDelay = config.getInt("teleport-delay", 5);
        String teleportMessage = config.getString("messages.teleport");
        if (teleportMessage != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    teleportMessage.replace("{warp-name}", warp.getName()).replace("{time}", String.valueOf(teleportDelay))));
        }
        UUID playerUUID = player.getUniqueId();
        BukkitTask previousTask = teleportTasks.get(playerUUID);
        if (previousTask != null) {
            previousTask.cancel();
        }
        invinciblePlayers.add(playerUUID);
        Collection<Entity> leashedEntities = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(12, 12, 12)) {
            if (entity instanceof LivingEntity livingEntity) {
                if (livingEntity.isLeashed() && livingEntity.getLeashHolder().equals(player)) {
                    leashedEntities.add(livingEntity);
                }
            }
        }
        final Horse playerHorse = (player.getVehicle() instanceof Horse) ? (Horse) player.getVehicle() : null;
        Boat nearestBoat = null;
        double minDistance = Double.MAX_VALUE;
        Location playerLoc = player.getLocation();
        for (Entity entity : player.getWorld().getNearbyEntities(playerLoc, 5, 5, 5)) {
            if (entity instanceof Boat boat) {
                // 只處理有乘客的船
                if (!boat.getPassengers().isEmpty()) {
                    double distance = boat.getLocation().distance(playerLoc);
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearestBoat = boat;
                    }
                }
            }
        }
        Boat finalNearestBoat = nearestBoat;
        BukkitTask teleportTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location targetLocation = warp.getLocation();
            // 先傳送玩家
            player.teleport(targetLocation);
            // 處理玩家騎乘的馬匹支援
            if (playerHorse != null && !playerHorse.isDead()) {
                playerHorse.teleport(targetLocation);
                if (!playerHorse.getPassengers().contains(player)) {
                    playerHorse.addPassenger(player);
                }
            }
            // 處理附近船隻傳送支援
            if (finalNearestBoat != null) {
                // 先檢查該船上所有乘客
                List<Entity> nonPlayerPassengers = new ArrayList<>();
                for (Entity passenger : finalNearestBoat.getPassengers()) {
                    if (passenger instanceof Player) {
                        // 若該玩家不是觸發傳送的玩家，則趕下船（移除乘客）
                        if (!passenger.equals(player)) {
                            finalNearestBoat.removePassenger(passenger);
                        }
                    } else {
                        nonPlayerPassengers.add(passenger);
                    }
                }
                // 臨時解除非玩家乘客載具關係的部分已在上方處理
                // 傳送船隻
                finalNearestBoat.teleport(targetLocation);
                // 傳送所有非玩家乘客
                for (Entity passenger : nonPlayerPassengers) {
                    passenger.teleport(targetLocation);
                }
                // 恢復非玩家乘客與船隻的載具關係
                for (Entity passenger : nonPlayerPassengers) {
                    finalNearestBoat.addPassenger(passenger);
                }
            }
            // 傳送被牽引生物
            for (Entity entity : leashedEntities) {
                entity.teleport(targetLocation);
            }
            // 播放傳送音效與特效
            String soundName = config.getString("teleport-sound", "ENTITY_ENDERMAN_TELEPORT");
            String effectName = config.getString("teleport-effect", "ENDER_SIGNAL");
            Sound sound = Sound.valueOf(soundName);
            Effect effect = Effect.valueOf(effectName);
            World world = targetLocation.getWorld();
            if (world != null) {
                world.playSound(targetLocation, sound, 1, 1);
                world.playEffect(targetLocation, effect, 10);
            }
            String successMessage = config.getString("messages.teleport-success");
            if (successMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        successMessage.replace("{warp-name}", warp.getName())));
            }
            pendingItemCosts.remove(playerUUID);
            int useCooldown = config.getInt("teleport-use-cooldown", 5);
            cooldowns.put(playerUUID, System.currentTimeMillis() + useCooldown * 1000L);
            teleportTasks.remove(playerUUID);
            invinciblePlayers.remove(playerUUID);
        }, teleportDelay * 20L);
        teleportTasks.put(playerUUID, teleportTask);
    }

    /**
     * 玩家移動時若尚在傳送延遲階段內，則取消傳送，
     * 並返還已扣除的物品，同時移除相關記錄。
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        if (teleportTasks.containsKey(playerUUID)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != Objects.requireNonNull(to).getX() ||
                    from.getY() != to.getY() ||
                    from.getZ() != to.getZ()) {
                BukkitTask teleportTask = teleportTasks.get(playerUUID);
                if (teleportTask != null && !teleportTask.isCancelled()) {
                    teleportTask.cancel();
                    teleportTasks.remove(playerUUID);
                    invinciblePlayers.remove(playerUUID);
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

    /**
     * 取消傳送期間對玩家造成的傷害
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (invinciblePlayers.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 當活塞推動時，若涉及傳送標識牌則取消該事件。
     */
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

    /**
     * 防止傳送告示牌依附的方塊受重力影響
     */
    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (isGravityAffected(block.getType())) {
            if (hasBlockWarpSign(block)) {
                event.setCancelled(true);
                return;
            }
            Block above = block.getRelative(BlockFace.UP);
            Sign signAbove = SignUtils.getSignFromBlock(above);
            if (signAbove != null && isWarpSign(signAbove)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 檢查方塊類型是否受重力影響
     */
    private boolean isGravityAffected(Material type) {
        return type == Material.SAND || type == Material.GRAVEL || type == Material.ANVIL || type == Material.RED_SAND;
    }

    /**
     * 當玩家離線時，清理該玩家相關的傳送記錄與冷卻、無敵、扣除物品等資訊
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (teleportTasks.containsKey(playerId)) {
            BukkitTask task = teleportTasks.get(playerId);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            teleportTasks.remove(playerId);
        }
        invinciblePlayers.remove(playerId);
        pendingItemCosts.remove(playerId);
        cooldowns.remove(playerId);
    }

    /**
     * 輔助方法：判斷單一區塊是否含有傳送標識牌
     */
    private boolean hasBlockWarpSign(Block block) {
        return SignUtils.hasBlockSign(block, this::isWarpSign);
    }

    /**
     * 輔助方法：判斷區塊列表中是否含有傳送標識牌
     */
    private boolean hasBlockWarpSign(List<Block> blocks) {
        return SignUtils.hasBlockSign(blocks, this::isWarpSign);
    }

    /**
     * 輔助方法：檢查某一 Sign 是否為傳送標識牌
     */
    private boolean isWarpSign(Sign signBlock) {
        SignData signData = new SignData(signBlock.getSide(Side.FRONT).getLines());
        return signData.isWarpSign();
    }
}