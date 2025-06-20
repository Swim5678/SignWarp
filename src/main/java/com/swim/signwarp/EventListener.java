package com.swim.signwarp;

import com.swim.signwarp.utils.SignUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class EventListener implements Listener {
    private static FileConfiguration config;
    private final SignWarp plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // 儲存傳送任務（排程）
    private final ConcurrentHashMap<UUID, BukkitTask> teleportTasks = new ConcurrentHashMap<>();
    // 暫存扣除的物品數量（用於傳送取消時返還）
    private final ConcurrentHashMap<UUID, Integer> pendingItemCosts = new ConcurrentHashMap<>();
    // 傳送冷卻：記錄玩家下次可傳送的時間（毫秒）
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

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

    // ============= 共用方法區域 =============

    /**
     * 統一的權限檢查方法
     */
    private enum PermissionType {
        CREATE("signwarp.create", "messages.create_permission"),
        USE("signwarp.use", "messages.use_permission"),
        DESTROY("signwarp.destroy", "messages.destroy_permission"),
        ADMIN("signwarp.admin", null);

        private final String permission;
        private final String messageKey;

        PermissionType(String permission, String messageKey) {
            this.permission = permission;
            this.messageKey = messageKey;
        }
    }

    private boolean hasPermission(Player player, PermissionType type) {
        if (player.hasPermission(type.permission)) {
            return true;
        }

        if (type.messageKey != null) {
            sendConfigMessage(player, type.messageKey);
        }
        return false;
    }

    /**
     * 統一的配置訊息發送方法
     */
    private void sendConfigMessage(Player player, String messageKey) {
        sendConfigMessage(player, messageKey, null);
    }

    private void sendConfigMessage(Player player, String messageKey, Map<String, String> placeholders) {
        String message = config.getString(messageKey);
        if (message != null) {
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    message = message.replace(entry.getKey(), entry.getValue());
                }
            }
            player.sendMessage(miniMessage.deserialize(message));
        }
    }

    /**
     * 統一的物品返還方法
     */
    private void returnPendingItems(Player player) {
        UUID playerUUID = player.getUniqueId();
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
    }

    /**
     * 統一的傳送任務取消方法
     */
    private void cancelTeleportTask(Player player, String messageKey) {
        UUID playerUUID = player.getUniqueId();
        BukkitTask teleportTask = teleportTasks.get(playerUUID);
        if (teleportTask != null && !teleportTask.isCancelled()) {
            teleportTask.cancel();
            teleportTasks.remove(playerUUID);
            returnPendingItems(player);
            sendConfigMessage(player, messageKey);
        }
    }

    /**
     * 統一的傳送權限檢查方法
     */
    private boolean canUseWarp(Player player, Warp warp) {
        // 管理員可以使用所有傳送點
        if (hasPermission(player, PermissionType.ADMIN)) {
            return true;
        }

        // 創建者可以使用自己的傳送點
        if (player.getUniqueId().toString().equals(warp.getCreatorUuid())) {
            return true;
        }

        // 如果是公共傳送點，任何人都可以使用
        if (!warp.isPrivate()) {
            return true;
        }

        // 使用 Warp 類別的完整權限檢查方法（包含群組成員檢查）
        if (warp.canUseWarp(player.getUniqueId().toString())) {
            return true;
        }

        // 發送私人傳送點錯誤訊息
        sendConfigMessage(player, "messages.private_warp");
        return false;
    }

    /**
     * 統一的 SignData 獲取方法
     */
    private SignData getSignData(Block block) {
        Sign signBlock = SignUtils.getSignFromBlock(block);
        if (signBlock == null) {
            return null;
        }

        Component[] lines = new Component[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = signBlock.getSide(Side.FRONT).line(i);
        }
        return new SignData(lines);
    }

    /**
     * 統一的物品檢查與扣除方法
     */
    private boolean checkAndConsumeItem(Player player) {
        String itemName = config.getString("create-wpt-item", "none");
        if ("none".equalsIgnoreCase(itemName)) {
            return true; // 不需要物品
        }

        Material material = Material.getMaterial(itemName.toUpperCase());
        if (material == null) {
            player.sendMessage(Component.text("配置中指定的物品無效。", NamedTextColor.RED));
            return false;
        }

        int cost = config.getInt("create-wpt-item-cost", 1);
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() != material) {
            Map<String, String> placeholders = Map.of("{use-item}", itemName);
            sendConfigMessage(player, "messages.invalid_item", placeholders);
            return false;
        }

        if (itemInHand.getAmount() < cost) {
            Map<String, String> placeholders = Map.of(
                    "{use-cost}", String.valueOf(cost),
                    "{use-item}", itemName
            );
            sendConfigMessage(player, "messages.not_enough_item", placeholders);
            return false;
        }

        // 扣除物品
        int remaining = itemInHand.getAmount() - cost;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            itemInHand.setAmount(remaining);
            player.getInventory().setItemInMainHand(itemInHand);
        }

        // 記錄扣除數量（用於傳送取消時返還）
        if ("use-item".equals("create-wpt-item")) {
            pendingItemCosts.put(player.getUniqueId(), cost);
        }

        return true;
    }

    /**
     * 統一的冷卻檢查方法
     */
    private boolean checkCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long cooldownEnd = cooldowns.get(playerId);
            if (now < cooldownEnd) {
                long remainingSeconds = (cooldownEnd - now + 999) / 1000;
                Map<String, String> placeholders = Map.of("{cooldown}", String.valueOf(remainingSeconds));
                sendConfigMessage(player, "messages.cooldown", placeholders);
                return false;
            }
        }
        return true;
    }

    // ============= 事件處理方法 =============

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        validateConfigItems();
        startCooldownCleanupTask();
    }

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

    private void startCooldownCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            cooldowns.values().removeIf(cooldownEnd -> cooldownEnd <= now);
        }, 6000L, 6000L);
    }

    private boolean canCreateWarp(Player player) {
        int maxWarps = config.getInt("max-warps-per-player", 10);
        if (maxWarps == -1) {
            return true;
        }

        boolean opUnlimited = config.getBoolean("op-unlimited-warps", true);
        if (opUnlimited && player.isOp()) {
            return true;
        }

        int currentWarps = Warp.getPlayerWarpCount(player.getUniqueId().toString());
        return currentWarps < maxWarps;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Component[] lines = new Component[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = event.line(i);
        }
        SignData signData = new SignData(lines);

        if (!signData.isWarpSign()) {
            return;
        }

        Player player = event.getPlayer();
        Block signBlock = event.getBlock();

        // 檢查依附方塊
        Material supportType = getSupportBlockType(signBlock);
        if (isGravityAffected(supportType)) {
            Component msg = Component.text("無法建立在沙子、礫石等重力方塊上！", NamedTextColor.RED);
            player.sendMessage(msg);
            event.setCancelled(true);
            return;
        }

        // 統一權限檢查
        if (!hasPermission(player, PermissionType.CREATE)) {
            event.setCancelled(true);
            return;
        }

        // 傳送點名稱有效性檢查
        if (!signData.isValidWarpName()) {
            sendConfigMessage(player, "messages.no_warp_name");
            event.setCancelled(true);
            return;
        }

        Warp existingWarp = Warp.getByName(signData.warpName);

        if (signData.isWarp()) {
            handleWarpSignCreation(event, player, signData, existingWarp);
        } else if (signData.isWarpTarget()) {
            handleWarpTargetCreation(event, player, signData, existingWarp);
        }
    }

    private Material getSupportBlockType(Block signBlock) {
        if (signBlock.getBlockData() instanceof WallSign wallSign) {
            Block attachedBlock = signBlock.getRelative(wallSign.getFacing().getOppositeFace());
            return attachedBlock.getType();
        } else {
            Block attachedBlock = signBlock.getRelative(BlockFace.DOWN);
            return attachedBlock.getType();
        }
    }

    private void handleWarpSignCreation(SignChangeEvent event, Player player, SignData signData, Warp existingWarp) {
        if (existingWarp == null) {
            sendConfigMessage(player, "messages.warp_not_found");
            event.setCancelled(true);
            return;
        }
        event.line(0, Component.text(SignData.HEADER_WARP).color(NamedTextColor.BLUE));
        sendConfigMessage(player, "messages.warp_created");
    }

    private void handleWarpTargetCreation(SignChangeEvent event, Player player, SignData signData, Warp existingWarp) {
        if (existingWarp != null) {
            sendConfigMessage(player, "messages.warp_name_taken");
            event.setCancelled(true);
            return;
        }

        if (!canCreateWarp(player)) {
            int maxWarps = config.getInt("max-warps-per-player", 10);
            int currentWarps = Warp.getPlayerWarpCount(player.getUniqueId().toString());
            Map<String, String> placeholders = Map.of(
                    "{current}", String.valueOf(currentWarps),
                    "{max}", String.valueOf(maxWarps)
            );
            sendConfigMessage(player, "messages.warp_limit_reached", placeholders);
            event.setCancelled(true);
            return;
        }

        if (!validCreateWPTItem) {
            player.sendMessage(Component.text("建立傳送目標功能暫停，請聯繫管理員。", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        // 檢查並扣除物品
        if (!checkAndConsumeItem(player
        )) {
            event.setCancelled(true);
            return;
        }

        // 建立傳送目標
        createWarpTarget(event, player, signData);
    }

    private void createWarpTarget(SignChangeEvent event, Player player, SignData signData) {
        String currentDateTime = LocalDateTime.now().toString();
        boolean defaultVisibility = config.getBoolean("default-visibility", false);
        Warp warp = new Warp(signData.warpName, player.getLocation(), currentDateTime,
                player.getName(), player.getUniqueId().toString(), defaultVisibility);
        warp.save();
        event.line(0, Component.text(SignData.HEADER_TARGET).color(NamedTextColor.BLUE));
        boolean showCreatorOnSign = config.getBoolean("show-creator-on-sign", true);
        if (showCreatorOnSign) {
            String creatorDisplayFormat = config.getString("messages.creator-display-format", "<gray>建立者: <white>{creator}");
            String formattedCreatorInfo = creatorDisplayFormat.replace("{creator}", player.getName());
            event.line(2, miniMessage.deserialize(formattedCreatorInfo));
        }

        sendConfigMessage(player, "messages.target_sign_created");
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

        SignData signData = getSignData(block);
        if (signData == null || !signData.isWarpTarget() || !signData.isValidWarpName()) {
            return;
        }

        Player player = event.getPlayer();
        Warp warp = Warp.getByName(signData.warpName);
        if (warp == null) {
            return;
        }

        // 權限檢查
        boolean hasPermission = hasPermission(player, PermissionType.DESTROY) ||
                player.getUniqueId().toString().equals(warp.getCreatorUuid());

        if (!hasPermission) {
            sendConfigMessage(player, "messages.destroy_permission");
            event.setCancelled(true);
            return;
        }

        // 執行破壞
        warp.remove();
        sendConfigMessage(player, "messages.warp_destroyed");
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

        SignData signData = getSignData(block);
        if (signData == null) {
            return;
        }

        // 自動鎖定標識牌
        Sign signBlock = SignUtils.getSignFromBlock(block);
        if (signData.isWarpSign() && signBlock != null && !signBlock.isWaxed()) {
            signBlock.setWaxed(true);
            signBlock.update();
        }

        if (!signData.isWarp() || !signData.isValidWarpName()) {
            return;
        }

        Player player = event.getPlayer();
        handleTeleportRequest(player, signData.warpName, event.getItem());
    }

    private void handleTeleportRequest(Player player, String warpName, ItemStack handItem) {
        UUID playerId = player.getUniqueId();
        Warp warp = Warp.getByName(warpName);

        // 檢查傳送點權限
        if (warp != null && !canUseWarp(player, warp)) {
            return;
        }

        // 檢查冷卻
        if (!checkCooldown(player)) {
            return;
        }

        // 檢查使用權限
        if (!hasPermission(player, PermissionType.USE)) {
            return;
        }

        // 檢查配置有效性
        if (!validUseItem) {
            player.sendMessage(Component.text("傳送使用功能暫停，請聯繫管理員。", NamedTextColor.RED));
            return;
        }

        // 檢查是否已在傳送中
        if (teleportTasks.containsKey(playerId)) {
            return;
        }

        // 檢查船隻限制
        if (player.getVehicle() instanceof Boat) {
            sendConfigMessage(player, "messages.cannot_teleport_on_boat");
            return;
        }

        // 檢查並扣除使用物品
        String useItem = config.getString("use-item", "none");
        if (!"none".equalsIgnoreCase(useItem)) {
            Material requiredMaterial = Material.getMaterial(useItem.toUpperCase());
            if (requiredMaterial == null) {
                player.sendMessage(Component.text("配置中指定的使用物品無效。", NamedTextColor.RED));
                return;
            }

            if (handItem == null || handItem.getType() != requiredMaterial) {
                Map<String, String> placeholders = Map.of("{use-item}", useItem);
                sendConfigMessage(player, "messages.invalid_item", placeholders);
                return;
            }

            int useCost = config.getInt("use-cost", 0);
            if (handItem.getAmount() < useCost) {
                Map<String, String> placeholders = Map.of(
                        "{use-cost}", String.valueOf(useCost),
                        "{use-item}", useItem
                );
                sendConfigMessage(player, "messages.not_enough_item", placeholders);
                return;
            }

            // 扣除物品
            int remaining = handItem.getAmount() - useCost;
            if (remaining <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                handItem.setAmount(remaining);
                player.getInventory().setItemInMainHand(handItem);
            }
            pendingItemCosts.put(playerId, useCost);
        }

        teleportPlayer(player, warpName);
    }

    private void teleportPlayer(Player player, String warpName) {
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            plugin.getLogger().log(Level.WARNING, "[SignWarp] 傳送失敗：玩家 " + player.getName()
                    + " 試圖傳送至不存在的傳送點 '" + warpName + "'.");
            returnPendingItems(player);
            sendConfigMessage(player, "messages.warp_not_found");
            return;
        }

        // 再次檢查權限（防止競態條件）
        if (warp.isPrivate() && !canUseWarp(player, warp)) {
            returnPendingItems(player);
            return;
        }

        // 檢查跨次元傳送限制
        if (!canCrossDimensionTeleport(player, warp)) {
            return;
        }

        int teleportDelay = config.getInt("teleport-delay", 5);
        Map<String, String> placeholders = Map.of(
                "{warp-name}", warp.getName(),
                "{time}", String.valueOf(teleportDelay)
        );
        sendConfigMessage(player, "messages.teleport", placeholders);

        scheduleTeleportTask(player, warp, teleportDelay);
    }

    private void scheduleTeleportTask(Player player, Warp warp, int delay) {
        UUID playerUUID = player.getUniqueId();

        // 取消之前的傳送任務
        BukkitTask previousTask = teleportTasks.get(playerUUID);
        if (previousTask != null) {
            previousTask.cancel();
        }

        // 準備傳送相關實體
        Collection<Entity> leashedEntities = collectLeashedEntities(player);
        Entity playerVehicle = player.getVehicle();
        Boat nearestBoat = findNearestBoatWithPassengers(player);

        // 排程傳送任務
        BukkitTask teleportTask = Bukkit.getScheduler().runTaskLater(plugin, () -> executeTeleport(player, warp, playerVehicle, nearestBoat, leashedEntities), delay * 20L);

        teleportTasks.put(playerUUID, teleportTask);
    }

    private Collection<Entity> collectLeashedEntities(Player player) {
        Collection<Entity> leashedEntities = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(14, 14, 14)) {
            if (entity instanceof LivingEntity livingEntity) {
                if (livingEntity.isLeashed() && livingEntity.getLeashHolder().equals(player)) {
                    leashedEntities.add(livingEntity);
                }
            }
        }
        return leashedEntities;
    }

    private Boat findNearestBoatWithPassengers(Player player) {
        Boat nearestBoat = null;
        double minDistance = Double.MAX_VALUE;
        Location playerLoc = player.getLocation();

        for (Entity entity : player.getWorld().getNearbyEntities(playerLoc, 5, 5, 5)) {
            if (entity instanceof Boat boat) {
                boolean hasNonPlayerPassenger = boat.getPassengers().stream()
                        .anyMatch(passenger -> !(passenger instanceof Player));

                if (hasNonPlayerPassenger) {
                    double distance = boat.getLocation().distance(playerLoc);
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearestBoat = boat;
                    }
                }
            }
        }
        return nearestBoat;
    }

    private void executeTeleport(Player player, Warp warp, Entity playerVehicle,
                                 Boat nearestBoat, Collection<Entity> leashedEntities) {
        Location targetLocation = warp.getLocation();
        UUID playerUUID = player.getUniqueId();

        // 傳送玩家
        player.teleport(targetLocation);

        // 處理載具傳送
        if (playerVehicle != null && !playerVehicle.isDead()) {
            playerVehicle.teleport(targetLocation);
            if (!playerVehicle.getPassengers().contains(player)) {
                playerVehicle.addPassenger(player);
            }
        }

        // 處理船隻傳送
        if (nearestBoat != null) {
            teleportBoatWithPassengers(nearestBoat, targetLocation);
        }

        // 傳送被牽引生物
        leashedEntities.forEach(entity -> entity.teleport(targetLocation));

        // 播放音效和特效
        World world = targetLocation.getWorld();
        if (world != null) {
            world.playSound(targetLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            world.playEffect(targetLocation, Effect.ENDER_SIGNAL, 10);
        }

        // 完成傳送
        finalizeTeleport(player, warp);
    }

    private void teleportBoatWithPassengers(Boat boat, Location targetLocation) {
        List<Entity> nonPlayerPassengers = boat.getPassengers().stream()
                .filter(passenger -> !(passenger instanceof Player))
                .toList();

        // 解除乘客關係
        nonPlayerPassengers.forEach(boat::removePassenger);

        // 傳送船隻和乘客
        boat.teleport(targetLocation);
        nonPlayerPassengers.forEach(passenger -> passenger.teleport(targetLocation));

        // 恢復乘客關係
        nonPlayerPassengers.forEach(boat::addPassenger);
    }

    private void finalizeTeleport(Player player, Warp warp) {
        UUID playerUUID = player.getUniqueId();

        Map<String, String> placeholders = Map.of("{warp-name}", warp.getName());
        sendConfigMessage(player, "messages.teleport-success", placeholders);

        // 清除扣除物品記錄
        pendingItemCosts.remove(playerUUID);

        // 設定冷卻
        int useCooldown = config.getInt("teleport-use-cooldown", 5);
        cooldowns.put(playerUUID, System.currentTimeMillis() + useCooldown * 1000L);

        // 清除傳送任務記錄
        teleportTasks.remove(playerUUID);
    }

    private boolean canCrossDimensionTeleport(Player player, Warp warp) {
        World playerWorld = player.getWorld();
        World warpWorld = warp.getLocation().getWorld();

        if (warpWorld == null) {
            sendConfigMessage(player, "messages.warp_world_not_found");
            returnPendingItems(player);
            return false;
        }

        if (!isDifferentWorld(playerWorld, warpWorld)) {
            return true;
        }

        boolean crossDimensionEnabled = config.getBoolean("cross-dimension-teleport.enabled", true);
        if (!crossDimensionEnabled) {
            boolean opBypass = config.getBoolean("cross-dimension-teleport.op-bypass", true);
            if (opBypass && player.isOp()) {
                return true;
            }

            String targetWorldName = getDisplayWorldName(warpWorld.getName());
            Map<String, String> placeholders = Map.of("{target-world}", targetWorldName);
            sendConfigMessage(player, "messages.cross_dimension_disabled", placeholders);
            returnPendingItems(player);
            return false;
        }

        return true;
    }

    private String getDisplayWorldName(String worldName) {
        if (worldName == null) {
            return "未知世界";
        }

        if (worldName.equals("world") || worldName.endsWith("_overworld")) {
            return "主世界";
        } else if (worldName.equals("world_nether") || worldName.endsWith("_nether")) {
            return "地獄";
        } else if (worldName.equals("world_the_end") || worldName.endsWith("_the_end")) {
            return "終界";
        }

        return worldName;
    }

    private boolean isDifferentWorld(World world1, World world2) {
        if (world1 == null || world2 == null) {
            return true;
        }
        return !world1.equals(world2);
    }

    // ============= 其他事件處理 =============

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (teleportTasks.containsKey(playerUUID)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                cancelTeleportTask(player, "messages.teleport-cancelled");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Player player = event.getPlayer();

        if (teleportTasks.containsKey(playerId)) {
            BukkitTask task = teleportTasks.get(playerId);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            teleportTasks.remove(playerId);
            returnPendingItems(player);
        }

        pendingItemCosts.remove(playerId);
        cooldowns.remove(playerId);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        cancelTeleportTask(player, "messages.teleport-death-cancelled");
    }

    // ============= 方塊保護相關事件 =============

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

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (hasBlockWarpSign(event.blockList())) {
            event.setCancelled(true);
        }
    }

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

    // ============= 輔助方法 =============

    private boolean isGravityAffected(Material type) {
        return type == Material.SAND || type == Material.GRAVEL ||
                type == Material.ANVIL || type == Material.RED_SAND;
    }

    private boolean hasBlockWarpSign(Block block) {
        return SignUtils.hasBlockSign(block, this::isWarpSign);
    }

    private boolean hasBlockWarpSign(List<Block> blocks) {
        return SignUtils.hasBlockSign(blocks, this::isWarpSign);
    }

    private boolean isWarpSign(Sign signBlock) {
        Component[] lines = new Component[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = signBlock.getSide(Side.FRONT).line(i);
        }
        SignData signData = new SignData(lines);
        return signData.isWarpSign();
    }
}