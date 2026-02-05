package com.cotitachi.velocityproxy.manager;

import com.cotitachi.velocityproxy.database.DatabasePool;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FriendManager {

    private final DatabasePool database;
    private final ProxyServer server;
    private final UUIDCache uuidCache;
    
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> outgoing = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> incoming = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> settings = new ConcurrentHashMap<>();

    public FriendManager(DatabasePool database, ProxyServer server, UUIDCache uuidCache) {
        this.database = database;
        this.server = server;
        this.uuidCache = uuidCache;
        loadAll();
    }

    private void loadAll() {
        database.queryAsync("SELECT * FROM friends", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    UUID p1 = UUID.fromString(rs.getString("player_uuid"));
                    UUID p2 = UUID.fromString(rs.getString("friend_uuid"));
                    friends.computeIfAbsent(p1, k -> ConcurrentHashMap.newKeySet()).add(p2);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });

        database.queryAsync("SELECT * FROM friend_requests", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    UUID sender = UUID.fromString(rs.getString("sender_uuid"));
                    UUID receiver = UUID.fromString(rs.getString("receiver_uuid"));
                    outgoing.computeIfAbsent(sender, k -> ConcurrentHashMap.newKeySet()).add(receiver);
                    incoming.computeIfAbsent(receiver, k -> ConcurrentHashMap.newKeySet()).add(sender);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });

        database.queryAsync("SELECT * FROM player_settings", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    UUID player = UUID.fromString(rs.getString("player_uuid"));
                    boolean enabled = rs.getInt("requests_enabled") == 1;
                    settings.put(player, enabled);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public void sendRequest(UUID sender, UUID receiver) {
        if (sender.equals(receiver) || areFriends(sender, receiver) || hasOutgoing(sender, receiver)) {
            return;
        }

        outgoing.computeIfAbsent(sender, k -> ConcurrentHashMap.newKeySet()).add(receiver);
        incoming.computeIfAbsent(receiver, k -> ConcurrentHashMap.newKeySet()).add(sender);

        database.executeAsync(
            "INSERT INTO friend_requests (sender_uuid, receiver_uuid, sent_at) VALUES (?, ?, ?)",
            stmt -> {
                try {
                    stmt.setString(1, sender.toString());
                    stmt.setString(2, receiver.toString());
                    stmt.setLong(3, System.currentTimeMillis());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        server.getPlayer(receiver).ifPresent(p -> 
            p.sendMessage(Component.text("Friend request from ", NamedTextColor.GRAY)
                .append(Component.text(getName(sender), NamedTextColor.YELLOW)))
        );
    }

    public void accept(UUID accepter, UUID sender) {
        if (!hasIncoming(accepter, sender)) return;

        removeRequest(sender, accepter);

        friends.computeIfAbsent(accepter, k -> ConcurrentHashMap.newKeySet()).add(sender);
        friends.computeIfAbsent(sender, k -> ConcurrentHashMap.newKeySet()).add(accepter);

        long now = System.currentTimeMillis();
        database.executeAsync(
            "INSERT INTO friends (player_uuid, friend_uuid, since) VALUES (?, ?, ?)",
            stmt -> {
                try {
                    stmt.setString(1, accepter.toString());
                    stmt.setString(2, sender.toString());
                    stmt.setLong(3, now);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        server.getPlayer(sender).ifPresent(p ->
            p.sendMessage(Component.text(getName(accepter), NamedTextColor.YELLOW)
                .append(Component.text(" accepted your friend request", NamedTextColor.GRAY)))
        );
    }

    public void deny(UUID denier, UUID sender) {
        if (!hasIncoming(denier, sender)) return;
        removeRequest(sender, denier);
    }

    public void remove(UUID p1, UUID p2) {
        if (!areFriends(p1, p2)) return;

        friends.getOrDefault(p1, Collections.emptySet()).remove(p2);
        friends.getOrDefault(p2, Collections.emptySet()).remove(p1);

        database.executeAsync(
            "DELETE FROM friends WHERE (player_uuid = ? AND friend_uuid = ?) OR (player_uuid = ? AND friend_uuid = ?)",
            stmt -> {
                try {
                    stmt.setString(1, p1.toString());
                    stmt.setString(2, p2.toString());
                    stmt.setString(3, p2.toString());
                    stmt.setString(4, p1.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    private void removeRequest(UUID sender, UUID receiver) {
        outgoing.getOrDefault(sender, Collections.emptySet()).remove(receiver);
        incoming.getOrDefault(receiver, Collections.emptySet()).remove(sender);

        database.executeAsync(
            "DELETE FROM friend_requests WHERE sender_uuid = ? AND receiver_uuid = ?",
            stmt -> {
                try {
                    stmt.setString(1, sender.toString());
                    stmt.setString(2, receiver.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    public boolean areFriends(UUID p1, UUID p2) {
        return friends.getOrDefault(p1, Collections.emptySet()).contains(p2);
    }

    public boolean hasOutgoing(UUID sender, UUID receiver) {
        return outgoing.getOrDefault(sender, Collections.emptySet()).contains(receiver);
    }

    public boolean hasIncoming(UUID receiver, UUID sender) {
        return incoming.getOrDefault(receiver, Collections.emptySet()).contains(sender);
    }

    public Set<UUID> getFriends(UUID player) {
        return new HashSet<>(friends.getOrDefault(player, Collections.emptySet()));
    }

    public Set<UUID> getOutgoing(UUID player) {
        return new HashSet<>(outgoing.getOrDefault(player, Collections.emptySet()));
    }

    public Set<UUID> getIncoming(UUID player) {
        return new HashSet<>(incoming.getOrDefault(player, Collections.emptySet()));
    }

    public boolean requestsEnabled(UUID player) {
        return settings.getOrDefault(player, true);
    }

    public void setRequestsEnabled(UUID player, boolean enabled) {
        settings.put(player, enabled);
        database.executeAsync(
            "INSERT OR REPLACE INTO player_settings (player_uuid, requests_enabled) VALUES (?, ?)",
            stmt -> {
                try {
                    stmt.setString(1, player.toString());
                    stmt.setInt(2, enabled ? 1 : 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    public String getName(UUID uuid) {
        String cached = uuidCache.getName(uuid);
        if (cached != null) return cached;
        return server.getPlayer(uuid).map(Player::getUsername).orElse("Unknown");
    }
}