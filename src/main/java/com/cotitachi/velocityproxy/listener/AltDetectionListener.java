package com.cotitachi.velocityproxy.listener;

import com.cotitachi.velocityproxy.manager.AltDetectionManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;

import java.net.InetSocketAddress;

public class AltDetectionListener {

    private final AltDetectionManager altDetectionManager;

    public AltDetectionListener(AltDetectionManager altDetectionManager) {
        this.altDetectionManager = altDetectionManager;
    }

    @Subscribe
    public void onJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        InetSocketAddress address = player.getRemoteAddress();
        
        String ip = address.getAddress().getHostAddress();
        altDetectionManager.trackLogin(player.getUniqueId(), ip);
    }
}