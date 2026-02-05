package com.cotitachi.velocityproxy.listener;

import com.cotitachi.velocityproxy.manager.FriendManager;
import com.cotitachi.velocityproxy.manager.UUIDCache;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.UUID;

public class PlayerListener {

    private final FriendManager friendManager;
    private final UUIDCache uuidCache;

    public PlayerListener(FriendManager friendManager, UUIDCache uuidCache) {
        this.friendManager = friendManager;
        this.uuidCache = uuidCache;
    }

    @Subscribe
    public void onJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        uuidCache.cache(player.getUsername(), player.getUniqueId());

        for (UUID fid : friendManager.getFriends(player.getUniqueId())) {
            player.getServer().getPlayer(fid).ifPresent(friend ->
                friend.sendMessage(Component.text("● ", NamedTextColor.GREEN)
                    .append(Component.text(player.getUsername(), NamedTextColor.GRAY))
                    .append(Component.text(" joined", NamedTextColor.DARK_GRAY)))
            );
        }
    }

    @Subscribe
    public void onQuit(DisconnectEvent event) {
        Player player = event.getPlayer();

        for (UUID fid : friendManager.getFriends(player.getUniqueId())) {
            player.getServer().getPlayer(fid).ifPresent(friend ->
                friend.sendMessage(Component.text("● ", NamedTextColor.RED)
                    .append(Component.text(player.getUsername(), NamedTextColor.GRAY))
                    .append(Component.text(" left", NamedTextColor.DARK_GRAY)))
            );
        }
    }
}