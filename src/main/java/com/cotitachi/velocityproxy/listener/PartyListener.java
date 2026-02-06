package com.cotitachi.velocityproxy.listener;

import com.cotitachi.velocityproxy.manager.PartyManager;
import com.cotitachi.velocityproxy.party.Party;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.UUID;

public class PartyListener {

    private final PartyManager partyManager;
    private final ProxyServer server;

    public PartyListener(PartyManager partyManager, ProxyServer server) {
        this.partyManager = partyManager;
        this.server = server;
    }

    @Subscribe
    public void onServerSwitch(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        UUID pid = player.getUniqueId();

        Party party = partyManager.getParty(pid);
        if (party == null || !party.isLeader(pid)) return;

        ServerConnection current = player.getCurrentServer().orElse(null);
        if (current == null) return;

        String serverName = current.getServerInfo().getName();

        for (UUID member : party.getMembers()) {
            if (member.equals(pid)) continue;

            server.getPlayer(member).ifPresent(m -> {
                ServerConnection memberServer = m.getCurrentServer().orElse(null);
                if (memberServer != null && !memberServer.getServerInfo().getName().equals(serverName)) {
                    m.createConnectionRequest(current.getServer()).fireAndForget();
                }
            });
        }
    }

    @Subscribe
    public void onQuit(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID pid = player.getUniqueId();

        Party party = partyManager.getParty(pid);
        if (party != null) {
            partyManager.leaveParty(pid);
        }
    }
}