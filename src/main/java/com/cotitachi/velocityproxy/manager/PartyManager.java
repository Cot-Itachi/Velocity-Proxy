package com.cotitachi.velocityproxy.manager;

import com.cotitachi.velocityproxy.database.DatabasePool;
import com.cotitachi.velocityproxy.party.Party;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PartyManager {

    private final DatabasePool database;
    private final ProxyServer server;
    private final UUIDCache uuidCache;
    
    private final Map<Integer, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerParty = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> invites = new ConcurrentHashMap<>();
    private final AtomicInteger partyIdCounter = new AtomicInteger(0);
    
    private static final int MAX_PARTY_SIZE = 10;

    public PartyManager(DatabasePool database, ProxyServer server, UUIDCache uuidCache) {
        this.database = database;
        this.server = server;
        this.uuidCache = uuidCache;
        loadParties();
    }

    private void loadParties() {
        database.queryAsync("SELECT * FROM parties WHERE disbanded = 0", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    UUID leader = UUID.fromString(rs.getString("leader_uuid"));
                    long created = rs.getLong("created_at");
                    
                    Party party = new Party(id, leader, created);
                    parties.put(id, party);
                    
                    if (id > partyIdCounter.get()) {
                        partyIdCounter.set(id);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }).thenRun(() -> {
            database.queryAsync("SELECT * FROM party_members", stmt -> {}, rs -> {
                try {
                    while (rs.next()) {
                        int partyId = rs.getInt("party_id");
                        UUID member = UUID.fromString(rs.getString("player_uuid"));
                        
                        Party party = parties.get(partyId);
                        if (party != null) {
                            party.addMember(member);
                            playerParty.put(member, partyId);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            });
        });

        database.queryAsync("SELECT * FROM party_invites", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    int partyId = rs.getInt("party_id");
                    UUID invited = UUID.fromString(rs.getString("invited_uuid"));
                    invites.computeIfAbsent(invited, k -> ConcurrentHashMap.newKeySet()).add(partyId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public Party createParty(UUID leader) {
        int id = partyIdCounter.incrementAndGet();
        long now = System.currentTimeMillis();
        
        Party party = new Party(id, leader, now);
        parties.put(id, party);
        playerParty.put(leader, id);

        database.executeAsync(
            "INSERT INTO parties (id, leader_uuid, created_at, disbanded) VALUES (?, ?, ?, 0)",
            stmt -> {
                try {
                    stmt.setInt(1, id);
                    stmt.setString(2, leader.toString());
                    stmt.setLong(3, now);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        database.executeAsync(
            "INSERT INTO party_members (party_id, player_uuid, joined_at) VALUES (?, ?, ?)",
            stmt -> {
                try {
                    stmt.setInt(1, id);
                    stmt.setString(2, leader.toString());
                    stmt.setLong(3, now);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        return party;
    }

    public void disbandParty(int partyId) {
        Party party = parties.remove(partyId);
        if (party == null) return;

        for (UUID member : party.getMembers()) {
            playerParty.remove(member);
            server.getPlayer(member).ifPresent(p ->
                p.sendMessage(Component.text("Party disbanded", NamedTextColor.RED))
            );
        }

        database.executeAsync(
            "UPDATE parties SET disbanded = 1 WHERE id = ?",
            stmt -> {
                try {
                    stmt.setInt(1, partyId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        database.executeAsync(
            "DELETE FROM party_members WHERE party_id = ?",
            stmt -> {
                try {
                    stmt.setInt(1, partyId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        database.executeAsync(
            "DELETE FROM party_invites WHERE party_id = ?",
            stmt -> {
                try {
                    stmt.setInt(1, partyId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    public void invitePlayer(int partyId, UUID invited) {
        invites.computeIfAbsent(invited, k -> ConcurrentHashMap.newKeySet()).add(partyId);

        database.executeAsync(
            "INSERT INTO party_invites (party_id, invited_uuid, sent_at) VALUES (?, ?, ?)",
            stmt -> {
                try {
                    stmt.setInt(1, partyId);
                    stmt.setString(2, invited.toString());
                    stmt.setLong(3, System.currentTimeMillis());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    public void acceptInvite(UUID player, int partyId) {
        Party party = parties.get(partyId);
        if (party == null) return;

        if (party.size() >= MAX_PARTY_SIZE) return;

        party.addMember(player);
        playerParty.put(player, partyId);
        removeInvite(player, partyId);

        database.executeAsync(
            "INSERT INTO party_members (party_id, player_uuid, joined_at) VALUES (?, ?, ?)",
            stmt -> {
                try {
                    stmt.setInt(1, partyId);
                    stmt.setString(2, player.toString());
                    stmt.setLong(3, System.currentTimeMillis());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        broadcast(party, Component.text(getName(player), NamedTextColor.YELLOW)
            .append(Component.text(" joined the party", NamedTextColor.GRAY)));
    }

    public void kickMember(int partyId, UUID member) {
        Party party = parties.get(partyId);
        if (party == null) return;

        party.removeMember(member);
        playerParty.remove(member);

        database.executeAsync(
            "DELETE FROM party_members WHERE party_id = ? AND player_uuid = ?",
            stmt -> {
                try {
                    stmt.setInt(1, partyId);
                    stmt.setString(2, member.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        server.getPlayer(member).ifPresent(p ->
            p.sendMessage(Component.text("You were kicked from the party", NamedTextColor.RED))
        );

        broadcast(party, Component.text(getName(member), NamedTextColor.YELLOW)
            .append(Component.text(" was kicked", NamedTextColor.RED)));
    }

    public void leaveParty(UUID player) {
        Integer partyId = playerParty.remove(player);
        if (partyId == null) return;

        Party party = parties.get(partyId);
        if (party == null) return;

        party.removeMember(player);

        database.executeAsync(
            "DELETE FROM party_members WHERE party_id = ? AND player_uuid = ?",
            stmt -> {
                try {
                    stmt.setInt(1, partyId);
                    stmt.setString(2, player.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        if (party.isLeader(player)) {
            if (party.size() > 0) {
                UUID newLeader = party.getMembers().iterator().next();
                party.setLeader(newLeader);
                
                database.executeAsync(
                    "UPDATE parties SET leader_uuid = ? WHERE id = ?",
                    stmt -> {
                        try {
                            stmt.setString(1, newLeader.toString());
                            stmt.setInt(2, partyId);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                );

                broadcast(party, Component.text(getName(newLeader), NamedTextColor.YELLOW)
                    .append(Component.text(" is now the leader", NamedTextColor.AQUA)));
            } else {
                disbandParty(partyId);
                return;
            }
        }

        broadcast(party, Component.text(getName(player), NamedTextColor.YELLOW)
            .append(Component.text(" left the party", NamedTextColor.GRAY)));
    }

    public void transferLeadership(int partyId, UUID newLeader) {
        Party party = parties.get(partyId);
        if (party == null) return;

        party.setLeader(newLeader);

        database.executeAsync(
            "UPDATE parties SET leader_uuid = ? WHERE id = ?",
            stmt -> {
                try {
                    stmt.setString(1, newLeader.toString());
                    stmt.setInt(2, partyId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        broadcast(party, Component.text(getName(newLeader), NamedTextColor.YELLOW)
            .append(Component.text(" is now the leader", NamedTextColor.AQUA)));
    }

    private void removeInvite(UUID player, int partyId) {
        Set<Integer> playerInvites = invites.get(player);
        if (playerInvites != null) {
            playerInvites.remove(partyId);
            if (playerInvites.isEmpty()) {
                invites.remove(player);
            }
        }

        database.executeAsync(
            "DELETE FROM party_invites WHERE party_id = ? AND invited_uuid = ?",
            stmt -> {
                try {
                    stmt.setInt(1, partyId);
                    stmt.setString(2, player.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    public void broadcast(Party party, Component message) {
        Component formatted = Component.text("[Party] ", NamedTextColor.LIGHT_PURPLE).append(message);
        for (UUID member : party.getMembers()) {
            server.getPlayer(member).ifPresent(p -> p.sendMessage(formatted));
        }
    }

    public void chat(Party party, UUID sender, String message) {
        Component msg = Component.text("[Party] ", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text(getName(sender), NamedTextColor.WHITE))
            .append(Component.text(": ", NamedTextColor.DARK_GRAY))
            .append(Component.text(message, NamedTextColor.GRAY));

        for (UUID member : party.getMembers()) {
            server.getPlayer(member).ifPresent(p -> p.sendMessage(msg));
        }
    }

    public Party getParty(int id) {
        return parties.get(id);
    }

    public Party getParty(UUID player) {
        Integer id = playerParty.get(player);
        return id != null ? parties.get(id) : null;
    }

    public boolean hasParty(UUID player) {
        return playerParty.containsKey(player);
    }

    public boolean hasInvite(UUID player, int partyId) {
        Set<Integer> playerInvites = invites.get(player);
        return playerInvites != null && playerInvites.contains(partyId);
    }

    public Set<Integer> getInvites(UUID player) {
        return invites.getOrDefault(player, Collections.emptySet());
    }

    public String getName(UUID uuid) {
        String cached = uuidCache.getName(uuid);
        if (cached != null) return cached;
        return server.getPlayer(uuid).map(Player::getUsername).orElse("Unknown");
    }
}