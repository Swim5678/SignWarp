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
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class EventListener implements Listener {
    private final SignWarp plugin;
    private static FileConfiguration config;
    private final HashMap<UUID, BukkitTask> teleportTasks = new HashMap<>();
    private final HashSet<UUID> invinciblePlayers = new HashSet<>();
    private final HashMap<UUID, Double> pendingTeleportCosts = new HashMap<>();
    private final HashMap<UUID, Integer> pendingItemCosts = new HashMap<>();

    EventListener(SignWarp plugin) {
        this.plugin = plugin;
        config = plugin.getConfig();
    }

    // method static to update the config
    public static void updateConfig(JavaPlugin plugin) {
        config = plugin.getConfig();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) throws IOException {
        SignData signData = new SignData(event.getLines());

        if (!signData.isWarpSign()) {
            return;
        }

        Player player = event.getPlayer();

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

        // 檢查是否需要消耗物品
        String createItem = config.getString("create-item", "none");
        int createItemCost = config.getInt("create-item-cost", 1);

        if (!"none".equalsIgnoreCase(createItem)) {
            Material material = Material.getMaterial(createItem.toUpperCase());
            if (material == null) {
                player.sendMessage(ChatColor.RED + "Invalid item specified in config for creating a warp.");
                event.setCancelled(true);
                return;
            }

            if (!player.getInventory().contains(material, createItemCost)) {
                String notEnoughItemMessage = config.getString("messages.not_enough_item");
                if (notEnoughItemMessage != null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', notEnoughItemMessage.replace("{use-cost}", String.valueOf(createItemCost)).replace("{use-item}", createItem)));
                }
                event.setCancelled(true);
                return;
            }

            // 扣除物品
            player.getInventory().removeItem(new ItemStack(material, createItemCost));
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
        } else {
            if (existingWarp != null) {
                String warpNameTakenMessage = config.getString("messages.warp_name_taken");
                if (warpNameTakenMessage != null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', warpNameTakenMessage));
                }
                event.setCancelled(true);
                return;
            }

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
    public void onBlockBreak(BlockBreakEvent event) throws IOException {
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

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.warp_destroyed")));
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

        if (signData.isWarpSign() && !signBlock.isWaxed()) {
            signBlock.setWaxed(true);
            signBlock.update();
        }

        if (!signData.isWarp() || !signData.isValidWarpName()) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.hasPermission("signwarp.use")) {
            String noPermissionMessage = config.getString("messages.use_permission");
            if (noPermissionMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermissionMessage));
            }
            return;
        }

        double teleportCost = config.getDouble("teleport-cost", 100.0); // Default cost
        String useItem = config.getString("use-item", "none");
        int useCost = config.getInt("use-cost", 0);

        if ("none".equalsIgnoreCase(useItem)) {
            useItem = null;
        }

        if (teleportCost > 0 && useItem == null) {
            // Case where no item is required and a teleportation cost is applied
            Economy economy = VaultEconomy.getEconomy();
            if (economy == null) {
                player.sendMessage(ChatColor.RED + "Vault is required for teleportation cost, but it is not installed or enabled.");
                return;
            }

            if (economy.getBalance(player) < teleportCost) {
                player.sendMessage(ChatColor.RED + "You don't have enough money to teleport.");
                return;
            }

            // Temporarily store the teleport cost to be deducted after teleportation
            pendingTeleportCosts.put(player.getUniqueId(), teleportCost);
            teleportPlayer(player, signData.warpName, true, teleportCost);
        } else if (teleportCost == 0.0 && useItem != null) {
            // Case where a specific item is required and the teleportation cost is 0
            Material itemInHand = event.getMaterial();

            if (itemInHand != null && itemInHand.name().equalsIgnoreCase(useItem)) {
                if (useCost > event.getItem().getAmount()) {
                    String notEnoughItemMessage = config.getString("messages.not_enough_item");
                    if (notEnoughItemMessage != null) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', notEnoughItemMessage.replace("{use-cost}", String.valueOf(useCost)).replace("{use-item}", useItem)));
                    }
                    return;
                }

                pendingItemCosts.put(player.getUniqueId(), useCost);
                teleportPlayer(player, signData.warpName, false, 0);
            } else {
                String invalidItemMessage = config.getString("messages.invalid_item");
                if (invalidItemMessage != null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidItemMessage.replace("{use-item}", useItem != null ? useItem : "an item")));
                }
            }
        } else if (teleportCost == 0.0 && useItem == null) {
            // Case where neither an item nor a teleportation cost is required
            teleportPlayer(player, signData.warpName, false, 0);
        } else {
            // Case where both item and money can be used for teleportation
            player.sendMessage(ChatColor.RED + "You must use an item or pay to teleport.");
        }
    }


    private void teleportPlayer(Player player, String warpName, boolean useEconomy, double cost) {
        Warp warp = Warp.getByName(warpName);

        if (warp == null) {
            String warpNotFoundMessage = config.getString("messages.warp_not_found");
            if (warpNotFoundMessage != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', warpNotFoundMessage));
            }
            return;
        }

        int cooldown = config.getInt("teleport-cooldown", 5);

        String teleportMessage = config.getString("messages.teleport");
        if (teleportMessage != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', teleportMessage.replace("{warp-name}", warp.getName()).replace("{time}", String.valueOf(cooldown))));
        }

        UUID playerUUID = player.getUniqueId();

        // Cancel any previous teleport tasks for the player
        BukkitTask previousTask = teleportTasks.get(playerUUID);
        if (previousTask != null) {
            previousTask.cancel();
        }

        // Add the player to the invincible list
        invinciblePlayers.add(playerUUID);

        // Schedule the new teleport task
        BukkitTask teleportTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location targetLocation = warp.getLocation();
            player.teleport(targetLocation);

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

            // Deduct cost after successful teleportation
            if (useEconomy) {
                Double teleportCost = pendingTeleportCosts.remove(playerUUID);
                if (teleportCost != null) {
                    Economy economy = VaultEconomy.getEconomy();
                    economy.withdrawPlayer(player, teleportCost);

                    // Notify the player of the cost
                    String notifyCostMessage = config.getString("messages.notify-cost");
                    if (notifyCostMessage != null) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', notifyCostMessage.replace("{cost}", String.valueOf(teleportCost))));
                    }
                }
            } else {
                Integer itemCost = pendingItemCosts.remove(playerUUID);
                if (itemCost != null && itemCost > 0) {
                    Material itemInHand = player.getInventory().getItemInMainHand().getType();
                    player.getInventory().removeItem(new ItemStack(itemInHand, itemCost));
                }
            }

            // Remove the task from the map after completion
            teleportTasks.remove(playerUUID);
            // Remove the player from the invincible list
            invinciblePlayers.remove(playerUUID);

        }, cooldown * 20L); // 20 ticks = 1 second

        // Store the task in the map
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
                    invinciblePlayers.remove(playerUUID); // Remove invincibility
                    pendingTeleportCosts.remove(playerUUID); // Remove pending teleport cost
                    pendingItemCosts.remove(playerUUID); // Remove pending item cost
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