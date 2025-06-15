package com.swim.signwarp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerCacheListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 當玩家加入時更新快取
        PlayerCache.updatePlayerCache(event.getPlayer());
    }
}