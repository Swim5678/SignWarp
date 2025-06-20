package com.swim.signwarp;

import com.swim.signwarp.gui.WarpGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("ALL")
public class SWCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final GroupCommand groupCommand;
    private final MiniMessage miniMessage;

    public SWCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.groupCommand = new GroupCommand((SignWarp) plugin);
        this.miniMessage = MiniMessage.miniMessage();
    }

    private static @NotNull String getString(List<Warp> playerWarps, int i, String warpListFormat) {
        Warp warp = playerWarps.get(i);
        String visibility = warp.isPrivate() ? "私人" : "公共";
        return warpListFormat
                .replace("{index}", String.valueOf(i + 1))
                .replace("{warp-name}", warp.getName())
                .replace("{visibility}", visibility)
                .replace("{world}", Objects.requireNonNull(warp.getLocation().getWorld()).getName())
                .replace("{x}", String.valueOf((int) warp.getLocation().getX()))
                .replace("{y}", String.valueOf((int) warp.getLocation().getY()))
                .replace("{z}", String.valueOf((int) warp.getLocation().getZ()))
                .replace("{creator}", warp.getCreator())
                .replace("{created-at}", warp.getFormattedCreatedAt());
    }

    /**
     * 發送訊息給CommandSender，使用Adventure API
     */
    private void sendMessage(CommandSender sender, String message) {
        Component component = miniMessage.deserialize(message);
        sender.sendMessage(component);
    }

    /**
     * 從配置文件獲取訊息並發送
     */
    private void sendConfigMessage(CommandSender sender, String key, String defaultMessage) {
        String message = plugin.getConfig().getString(key, defaultMessage);
        sendMessage(sender, message);
    }

    /**
     * 從配置文件獲取訊息，替換占位符後發送
     */
    private void sendConfigMessage(CommandSender sender, String key, String defaultMessage, String... replacements) {
        String message = plugin.getConfig().getString(key, defaultMessage);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        sendMessage(sender, message);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String[] args) {
        if (args.length == 0) {
            return true;
        }

        // 新增群組指令處理
        if (args[0].equalsIgnoreCase("group")) {
            return groupCommand.handleGroupCommand(sender, args);
        }

        // 既有的指令處理...
        if (args[0].equalsIgnoreCase("gui")) {
            handleGuiCommand(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            handleReloadCommand(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            handleSetCommand(sender, args);
            return true;
        }

        // 新增的邀請系統指令
        if (args[0].equalsIgnoreCase("invite")) {
            handleInviteCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("uninvite")) {
            handleUninviteCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("list-invites")) {
            handleListInvitesCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("list-own")) {
            handleMyWarpsCommand(sender, args);
            return true;
        }

        // 僅限 OP 傳送指定 Warp
        if (args[0].equalsIgnoreCase("tp")) {
            return handleWptCommand(sender, args);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> cmds = new ArrayList<>();
            if (sender.hasPermission("signwarp.admin")) cmds.add("gui");
            if (sender.hasPermission("signwarp.reload")) cmds.add("reload");
            cmds.add("set");
            cmds.add("list-own");
            if (sender.hasPermission("signwarp.invite")) {
                cmds.add("invite");
                cmds.add("uninvite");
                cmds.add("list-invites");
            }
            if (sender instanceof Player) {
                cmds.add("group");
            }
            if (sender instanceof Player p && p.isOp()) {
                cmds.add("tp");
            }
            for (String c : cmds) {
                if (c.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(c);
                }
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "group":
                    // 直接呼叫群組 tab 補全方法
                    return handleGroupTabCompletion(sender, args);
                case "set":
                    for (String v : new String[]{"公共", "私人"}) {
                        if (v.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(v);
                        }
                    }
                    break;
                case "invite":
                case "uninvite":
                    Bukkit.getOnlinePlayers().forEach(pl -> {
                        if (pl.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(pl.getName());
                        }
                    });
                    break;
                case "list-invites":
                    if (sender instanceof Player pl) {
                        completions.addAll(getAccessibleWarps(pl, args[1]));
                    }
                    break;
                case "tp":
                    if (sender instanceof Player p && p.isOp()) {
                        Warp.getAll().stream()
                                .map(Warp::getName)
                                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                                .forEach(completions::add);
                    }
                    break;
                case "list-own":
                    // 只有 OP 或管理員可以查看其他玩家的傳送點
                    if (sender instanceof Player p && (p.isOp() || p.hasPermission("signwarp.admin"))) {
                        // 補全線上玩家名稱
                        Bukkit.getOnlinePlayers().forEach(pl -> {
                            if (pl.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                completions.add(pl.getName());
                            }
                        });

                        // 補全曾經創建過傳送點的玩家名稱
                        Warp.getAll().stream()
                                .map(Warp::getCreator)
                                .distinct()
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .forEach(completions::add);
                    }
                    break;
            }
        } else if (args.length >= 3) {
            if (args[0].equalsIgnoreCase("group")) {
                return handleGroupTabCompletion(sender, args);
            } else if (args[0].equalsIgnoreCase("set")
                    || args[0].equalsIgnoreCase("invite")
                    || args[0].equalsIgnoreCase("uninvite")) {
                if (sender instanceof Player pl && args.length == 3) {
                    completions.addAll(getAccessibleWarps(pl, args[2]));
                }
            }
        }

        return completions;
    }

    /**
     * 處理群組指令的 Tab 補全
     */
    private List<String> handleGroupTabCompletion(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player player)) {
            return completions;
        }

        // 檢查群組功能是否啟用
        if (!plugin.getConfig().getBoolean("warp-groups.enabled", true)) {
            return completions;
        }

        // 檢查玩家權限（與 GroupCommand 中的邏輯一致）
        if (!canPlayerUseGroups(player)) {
            return completions;
        }

        if (args.length == 2) {
            // 第一層子指令補全
            List<String> subCommands = Arrays.asList("create", "list", "info", "add", "remove", "invite", "uninvite", "delete");
            return subCommands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length < 3) {
            return completions;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "add":
            case "remove":
            case "invite":
            case "uninvite":
            case "info":
            case "delete":
                if (args.length == 3) {
                    // 補全群組名稱 - 根據權限顯示不同的群組
                    List<WarpGroup> accessibleGroups;
                    if (player.isOp() || player.hasPermission("signwarp.group.admin")) {
                        // OP 和管理員可以看到所有群組
                        accessibleGroups = WarpGroup.getAllGroups();
                    } else {
                        // 普通玩家只能看到自己的群組
                        accessibleGroups = WarpGroup.getPlayerGroups(player.getUniqueId().toString());

                        // 加入玩家有權限存取的群組（是成員的群組）
                        List<WarpGroup> allGroups = WarpGroup.getAllGroups();
                        for (WarpGroup group : allGroups) {
                            if (WarpGroup.isPlayerInGroup(group.groupName(), player.getUniqueId().toString())
                                    && !accessibleGroups.contains(group)) {
                                accessibleGroups.add(group);
                            }
                        }
                    }

                    for (WarpGroup group : accessibleGroups) {
                        if (group.groupName().toLowerCase().startsWith(args[2].toLowerCase())) {
                            completions.add(group.groupName());
                        }
                    }
                } else if (args.length == 4) {
                    // 第四個參數的補全
                    String groupName = args[2];
                    WarpGroup group = WarpGroup.getByName(groupName);

                    if (group != null && hasGroupPermission(player, group, subCommand)) {
                        switch (subCommand) {
                            case "add":
                                // 補全可加入的傳送點（玩家的私人傳送點，且不在任何群組中）
                                List<Warp> playerWarps = Warp.getPlayerWarps(player.getUniqueId().toString());
                                List<String> groupWarps = group.getGroupWarps();

                                for (Warp warp : playerWarps) {
                                    if (warp.isPrivate() &&
                                            !isWarpInAnyGroup(warp.getName()) &&
                                            warp.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(warp.getName());
                                    }
                                }
                                break;

                            case "remove":
                                // 補全群組中的傳送點
                                List<String> warpsInGroup = group.getGroupWarps();
                                for (String warpName : warpsInGroup) {
                                    if (warpName.toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(warpName);
                                    }
                                }
                                break;

                            case "invite":
                                // 補全所有線上玩家（除了指令執行者）
                                Bukkit.getOnlinePlayers().forEach(pl -> {
                                    if (!pl.equals(player) &&
                                            pl.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(pl.getName());
                                    }
                                });
                                break;
                            case "uninvite":
                                // 補全該群組的成員
                                List<WarpGroup.GroupMember> groupMembers = group.getGroupMembers();
                                for (WarpGroup.GroupMember member : groupMembers) {
                                    if (member.name().toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(member.name());
                                    }
                                }
                                break;
                        }
                    }
                } else if (subCommand.equals("add")) {
                    // 多個傳送點的補全
                    String groupName = args[2];
                    WarpGroup group = WarpGroup.getByName(groupName);

                    if (group != null && hasGroupPermission(player, group, subCommand)) {
                        List<Warp> playerWarps = Warp.getPlayerWarps(player.getUniqueId().toString());
                        List<String> groupWarps = group.getGroupWarps();

                        // 排除已經在參數中的傳送點
                        List<String> alreadyAdded = new ArrayList<>(Arrays.asList(args).subList(3, args.length - 1));

                        for (Warp warp : playerWarps) {
                            if (warp.isPrivate() &&
                                    !groupWarps.contains(warp.getName()) &&
                                    !alreadyAdded.contains(warp.getName()) &&
                                    !isWarpInAnyGroup(warp.getName()) &&
                                    warp.getName().toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
                                completions.add(warp.getName());
                            }
                        }
                    }
                }
                break;
        }

        return completions;
    }

    /**
     * 檢查玩家是否有使用群組功能的權限（與 GroupCommand 中的邏輯一致）
     */
    private boolean canPlayerUseGroups(Player player) {
        // OP 總是可以使用
        if (player.isOp()) {
            return true;
        }

        // 檢查管理員權限
        if (player.hasPermission("signwarp.group.admin")) {
            return true;
        }

        // 檢查配置是否允許普通玩家使用群組功能
        boolean allowNormalPlayers = plugin.getConfig().getBoolean("warp-groups.allow-normal-players", true);
        if (!allowNormalPlayers) {
            return false;
        }

        // 檢查基本群組權限
        return player.hasPermission("signwarp.group.create") || player.hasPermission("signwarp.use");
    }

    /**
     * 檢查玩家是否有特定群組操作的權限
     */
    private boolean hasGroupPermission(Player player, WarpGroup group, String operation) {
        // OP 和管理員擁有所有權限
        if (player.isOp() || player.hasPermission("signwarp.group.admin")) {
            return true;
        }

        // 檢查是否為群組擁有者
        if (group.ownerUuid().equals(player.getUniqueId().toString())) {
            return true;
        }

        // 對於某些操作，群組成員也有權限
        if (operation.equalsIgnoreCase("info")) {
            return WarpGroup.isPlayerInGroup(group.groupName(), player.getUniqueId().toString());
        }
        return false; // 其他操作只有擁有者和管理員可以執行
    }

    /**
     * 檢查傳送點是否已在任何群組中
     */
    private boolean isWarpInAnyGroup(String warpName) {
        List<WarpGroup> allGroups = WarpGroup.getAllGroups();
        for (WarpGroup group : allGroups) {
            if (group.getGroupWarps().contains(warpName)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getAccessibleWarps(Player player, String prefix) {
        return Warp.getAll().stream()
                .filter(w -> w.getCreatorUuid().equals(player.getUniqueId().toString())
                        || player.hasPermission("signwarp.admin"))
                .map(Warp::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void handleGuiCommand(CommandSender sender) {
        if (sender instanceof Player player) {
            if (player.hasPermission("signwarp.admin")) {
                WarpGui.openWarpGui(player, 0);
            } else {
                sendConfigMessage(sender, "messages.not_permission",
                        "<red>您沒有權限使用此指令。");
            }
        } else {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
        }
    }

    private void handleReloadCommand(CommandSender sender) {
        if (sender.hasPermission("signwarp.reload")) {
            plugin.reloadConfig();
            EventListener.updateConfig(plugin);
            sendMessage(sender, "<green>配置已重新載入");
        } else {
            sendConfigMessage(sender, "messages.not_permission",
                    "<red>您沒有權限使用此指令。");
        }
    }

    private void handleSetCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
            return;
        }
        if (args.length < 3) {
            sendConfigMessage(sender, "messages.set_visibility_usage",
                    "<red>用法: /wp set <公共|私人> <傳送點名稱>");
            return;
        }
        String visibility = args[1].toLowerCase();
        if (!visibility.equals("公共") && !visibility.equals("私人")) {
            sendConfigMessage(sender, "messages.invalid_visibility",
                    "<red>使用權限必須是 '公共' 或 '私人'");
            return;
        }
        String warpName = args[2];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendMessage(player, "<red>找不到傳送點: " + warpName);
            return;
        }
        if (!canModifyWarp(player, warp)) {
            sendConfigMessage(sender, "messages.cant_modify_others_warp",
                    "<red>您只能更改自己創建的傳送點！");
            return;
        }
        boolean isPrivate = visibility.equals("私人");
        Warp updated = new Warp(
                warp.getName(),
                warp.getLocation(),
                warp.getFormattedCreatedAt(),
                warp.getCreator(),
                warp.getCreatorUuid(),
                isPrivate
        );
        updated.save();
        sendConfigMessage(sender, "messages.warp_visibility_changed",
                "<green>傳送點 {warp-name} 的使用權限已更改為{visibility}。",
                "{warp-name}", warpName, "{visibility}", visibility);
    }

    private void handleInviteCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
            return;
        }
        if (!player.hasPermission("signwarp.invite")) {
            sendConfigMessage(sender, "messages.not_permission",
                    "<red>您沒有權限使用此指令。");
            return;
        }
        if (args.length < 3) {
            sendConfigMessage(sender, "messages.invite_usage",
                    "<red>用法: /wp invite <玩家> <傳送點名稱>");
            return;
        }
        String target = args[1], warpName = args[2];
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer == null) {
            sendConfigMessage(sender, "messages.player_not_found",
                    "<red>找不到玩家 '{player}' 或該玩家離線！",
                    "{player}", target);
            return;
        }
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendMessage(player, "<red>找不到傳送點: " + warpName);
            return;
        }
        if (!canModifyWarp(player, warp)) {
            sendConfigMessage(sender, "messages.not_your_warp",
                    "<red>這不是您的傳送點！");
            return;
        }
        if (warp.isPlayerInvited(targetPlayer.getUniqueId().toString())) {
            sendConfigMessage(sender, "messages.already_invited",
                    "<yellow>玩家 {player} 已經被邀請使用此傳送點。",
                    "{player}", targetPlayer.getName());
            return;
        }
        warp.invitePlayer(targetPlayer);
        sendConfigMessage(sender, "messages.invite_success",
                "<green>已邀請 {player} 使用傳送點 '{warp-name}'。",
                "{player}", targetPlayer.getName(), "{warp-name}", warpName);
        sendConfigMessage(targetPlayer, "messages.invite_received",
                "<green>{inviter} 邀請您使用傳送點 '{warp-name}'。",
                "{inviter}", player.getName(), "{warp-name}", warpName);
    }

    private void handleUninviteCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
            return;
        }
        if (!player.hasPermission("signwarp.invite")) {
            sendConfigMessage(sender, "messages.not_permission",
                    "<red>您沒有權限！");
            return;
        }
        if (args.length < 3) {
            sendConfigMessage(sender, "messages.uninvite_usage",
                    "<red>用法: /wp uninvite <玩家> <傳送點名稱>");
            return;
        }
        String target = args[1], warpName = args[2];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendMessage(player, "<red>找不到傳送點: " + warpName);
            return;
        }
        boolean isOwner = warp.getCreatorUuid().equals(player.getUniqueId().toString());
        boolean isAdmin = player.hasPermission("signwarp.admin");
        if (!isOwner && !isAdmin) {
            sendConfigMessage(sender, "messages.cant_modify_warp",
                    "<red>您無法修改此傳送點的邀請名單！");
            return;
        }
        Player tgt = Bukkit.getPlayer(target);
        if (tgt == null) {
            sendConfigMessage(sender, "messages.player_not_found",
                    "<red>找不到玩家 '{player}' 或該玩家離線！",
                    "{player}", target);
            return;
        }
        if (!warp.isPlayerInvited(tgt.getUniqueId().toString())) {
            sendConfigMessage(sender, "messages.not_invited",
                    "<red>玩家 {player} 未被邀請使用此傳送點。",
                    "{player}", tgt.getName());
            return;
        }
        warp.removeInvite(tgt.getUniqueId().toString());
        sendConfigMessage(sender, "messages.uninvite_success",
                "<green>已移除 {player} 使用傳送點 '{warp-name}' 的權限。",
                "{player}", tgt.getName(), "{warp-name}", warpName);
    }

    private void handleListInvitesCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
            return;
        }
        if (args.length < 2) {
            sendMessage(player, "<red>用法: /wp list-invites <傳送點名稱>");
            return;
        }
        String warpName = args[1];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendMessage(player, "<red>找不到傳送點: " + warpName);
            return;
        }
        boolean isOwner = warp.getCreatorUuid().equals(player.getUniqueId().toString());
        boolean isAdmin = player.hasPermission("signwarp.admin");
        if (!isOwner && !player.hasPermission("signwarp.invite.list-own")) {
            sendConfigMessage(sender, "messages.not_permission",
                    "<red>您沒有權限！");
            return;
        }
        if (!isOwner && !isAdmin) {
            sendConfigMessage(sender, "messages.not_permission",
                    "<red>您沒有權限！");
            return;
        }
        List<WarpInvite> invites = warp.getInvitedPlayers();
        sendConfigMessage(sender, "messages.invite_list",
                "<green>=== 傳送點 '{warp-name}' 的邀請列表 ===",
                "{warp-name}", warpName);
        if (invites.isEmpty()) {
            sendConfigMessage(sender, "messages.no_invites",
                    "<gray>沒有玩家被邀請使用此傳送點。");
        } else {
            for (WarpInvite wi : invites) {
                sendMessage(player, "<gray>- " + wi.invitedName());
            }
        }
    }

    /**
     * 新增方法：處理查看玩家自己擁有的傳送點指令
     * OP 可以使用 /signwarp list-own <玩家名稱> 來查看指定玩家的傳送點
     */
    private void handleMyWarpsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
            return;
        }

        String targetPlayerName;
        String targetPlayerUuid;
        boolean isViewingOthers = false;

        // 檢查是否有指定玩家參數
        if (args.length > 1) {
            // 檢查是否為 OP 或有管理員權限
            if (!player.isOp() && !player.hasPermission("signwarp.admin")) {
                sendConfigMessage(sender, "messages.not_permission_view_others",
                        "<red>您沒有權限查看其他玩家的傳送點！");
                return;
            }

            targetPlayerName = args[1];
            isViewingOthers = true;

            // 嘗試從線上玩家獲取 UUID
            Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
            if (targetPlayer != null) {
                targetPlayerUuid = targetPlayer.getUniqueId().toString();
            } else {
                // 如果玩家不在線，嘗試從已有的傳送點資料中找到該玩家
                targetPlayerUuid = getPlayerUuidByName(targetPlayerName);
                if (targetPlayerUuid == null) {
                    sendConfigMessage(sender, "messages.player_not_found_or_no_warps",
                            "<red>找不到玩家 '{player}' 或該玩家沒有任何傳送點。",
                            "{player}", targetPlayerName);
                    return;
                }
            }
        } else {
            // 沒有指定玩家，查看自己的傳送點
            targetPlayerName = player.getName();
            targetPlayerUuid = player.getUniqueId().toString();
        }

        List<Warp> playerWarps = Warp.getPlayerWarps(targetPlayerUuid);

        // 取得配置中的訊息，如果沒有設定則使用預設值
        String headerMsg = plugin.getConfig().getString("messages.my_warps_header",
                        "<green>=== {player} 擁有的傳送點 ===")
                .replace("{player}", isViewingOthers ? targetPlayerName : "您");
        String noWarpsMsg = plugin.getConfig().getString("messages.no_warps_owned",
                        "<gray>{player}目前沒有擁有任何傳送點。")
                .replace("{player}", isViewingOthers ? targetPlayerName : "您");
        String warpListFormat = plugin.getConfig().getString("messages.warp_list_format",
                "<white>{index}. <aqua>{warp-name} <gray>({visibility}) - <yellow>{world} <gray>({x}, {y}, {z})");
        String totalWarpsMsg = plugin.getConfig().getString("messages.total_warps",
                        "<green>{player}總共擁有 {count} 個傳送點")
                .replace("{player}", isViewingOthers ? targetPlayerName : "您");

        // 顯示標題
        sendMessage(player, headerMsg);

        if (playerWarps.isEmpty()) {
            // 沒有傳送點
            sendMessage(player, noWarpsMsg);
        } else {
            // 列出所有傳送點
            for (int i = 0; i < playerWarps.size(); i++) {
                String formattedMsg = getString(playerWarps, i, warpListFormat);
                sendMessage(player, formattedMsg);
            }

            // 顯示總數
            String totalMsg = totalWarpsMsg.replace("{count}", String.valueOf(playerWarps.size()));
            sendMessage(player, totalMsg);
        }
    }

    /**
     * 輔助方法：根據玩家名稱查找 UUID
     * 從現有的傳送點資料中查找該玩家的 UUID
     */
    private String getPlayerUuidByName(String playerName) {
        List<Warp> allWarps = Warp.getAll();
        for (Warp warp : allWarps) {
            if (warp.getCreator().equalsIgnoreCase(playerName)) {
                return warp.getCreatorUuid();
            }
        }
        return null;
    }

    private boolean canModifyWarp(Player player, Warp warp) {
        return warp.getCreatorUuid().equals(player.getUniqueId().toString())
                || player.hasPermission("signwarp.admin");
    }

    /**
     * OP 專用：/signwarp tp <傳送點名稱>
     */
    private boolean handleWptCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
            return true;
        }
        if (!player.isOp()) {
            sendConfigMessage(sender, "messages.tp_op_only",
                    "<red>只有伺服器 OP 可以使用此指令。");
            return true;
        }
        if (args.length < 2) {
            sendConfigMessage(sender, "messages.tp_usage",
                    "<yellow>用法: /signwarp tp <傳送點名稱>");
            return true;
        }
        String warpName = args[1];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendConfigMessage(sender, "messages.warp_not_found",
                    "<red>找不到傳送點: {warp-name}",
                    "{warp-name}", warpName);
            return true;
        }
        player.teleport(warp.getLocation());
        sendConfigMessage(sender, "messages.tp_success",
                "<green>已傳送到: {warp-name} (建立者: {creator})",
                "{warp-name}", warp.getName(), "{creator}", warp.getCreator());
        return true;
    }
}