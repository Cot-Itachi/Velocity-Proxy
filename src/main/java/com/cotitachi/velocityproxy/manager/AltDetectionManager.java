package com.cotitachi.velocityproxy.manager;

import com.cotitachi.velocityproxy.database.DatabasePool;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AltDetectionManager {

    private final DatabasePool database;
    private final ProxyServer server;
    private final UUIDCache uuidCache;

    private final Map<String, Set<UUID>> ipToPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerToIps = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Integer>> altConfidence = new ConcurrentHashMap<>();
    private final Set<String> whitelistedIps = ConcurrentHashMap.newKeySet();
    private final Set<UUID> alertsEnabled = ConcurrentHashMap.newKeySet();
    
    private static final long THIRTY_DAYS = 30L * 24 * 60 * 60 * 1000;
    private static final long SEVEN_DAYS = 7L * 24 * 60 * 60 * 1000;
    private static final long NINETY_DAYS = 90L * 24 * 60 * 60 * 1000;

    public AltDetectionManager(DatabasePool database, ProxyServer server, UUIDCache uuidCache) {
        this.database = database;
        this.server = server;
        this.uuidCache = uuidCache;
        loadData();
    }

    private void loadData() {
        database.queryAsync("SELECT * FROM player_ips WHERE last_seen > ?", stmt -> {
            try {
                stmt.setLong(1, System.currentTimeMillis() - NINETY_DAYS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, rs -> {
            try {
                while (rs.next()) {
                    UUID player = UUID.fromString(rs.getString("player_uuid"));
                    String ip = rs.getString("ip_address");
                    long lastSeen = rs.getLong("last_seen");
                    
                    if (System.currentTimeMillis() - lastSeen < THIRTY_DAYS) {
                        ipToPlayers.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(player);
                    }
                    playerToIps.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(ip);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });

        database.queryAsync("SELECT * FROM alt_links", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    UUID p1 = UUID.fromString(rs.getString("player1_uuid"));
                    UUID p2 = UUID.fromString(rs.getString("player2_uuid"));
                    int confidence = rs.getInt("confidence");
                    
                    altConfidence.computeIfAbsent(p1, k -> new ConcurrentHashMap<>()).put(p2, confidence);
                    altConfidence.computeIfAbsent(p2, k -> new ConcurrentHashMap<>()).put(p1, confidence);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });

        database.queryAsync("SELECT * FROM ip_whitelist", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    whitelistedIps.add(rs.getString("ip_address"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });

        database.queryAsync("SELECT * FROM alt_alerts WHERE enabled = 1", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    UUID player = UUID.fromString(rs.getString("player_uuid"));
                    alertsEnabled.add(player);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public void trackLogin(UUID player, String ip) {
        long now = System.currentTimeMillis();

        playerToIps.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(ip);

        database.executeAsync(
            "INSERT INTO player_ips (player_uuid, ip_address, first_seen, last_seen, login_count) " +
            "VALUES (?, ?, ?, ?, 1) " +
            "ON CONFLICT(player_uuid, ip_address) DO UPDATE SET " +
            "last_seen = excluded.last_seen, login_count = login_count + 1",
            stmt -> {
                try {
                    stmt.setString(1, player.toString());
                    stmt.setString(2, ip);
                    stmt.setLong(3, now);
                    stmt.setLong(4, now);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        database.executeAsync(
            "INSERT INTO login_history (player_uuid, ip_address, timestamp) VALUES (?, ?, ?)",
            stmt -> {
                try {
                    stmt.setString(1, player.toString());
                    stmt.setString(2, ip);
                    stmt.setLong(3, now);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        if (!whitelistedIps.contains(ip)) {
            analyzeAndLinkAlts(player, ip, now);
        }
    }

    private void analyzeAndLinkAlts(UUID player, String ip, long loginTime) {
        database.queryAsync(
            "SELECT pi.player_uuid, pi.last_seen, pi.login_count, " +
            "(SELECT COUNT(*) FROM login_history WHERE player_uuid = pi.player_uuid AND timestamp > ?) as recent_logins " +
            "FROM player_ips pi WHERE pi.ip_address = ? AND pi.player_uuid != ?",
            stmt -> {
                try {
                    stmt.setLong(1, loginTime - SEVEN_DAYS);
                    stmt.setString(2, ip);
                    stmt.setString(3, player.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            rs -> {
                try {
                    Set<UUID> suspiciousAccounts = new HashSet<>();
                    Map<UUID, Integer> confidenceScores = new HashMap<>();
                    
                    while (rs.next()) {
                        UUID other = UUID.fromString(rs.getString("player_uuid"));
                        long otherLastSeen = rs.getLong("last_seen");
                        int otherLoginCount = rs.getInt("login_count");
                        int recentLogins = rs.getInt("recent_logins");
                        
                        if (isLinked(player, other)) continue;
                        
                        int confidence = calculateConfidence(
                            loginTime,
                            otherLastSeen,
                            otherLoginCount,
                            recentLogins
                        );
                        
                        if (confidence >= 50) {
                            confidenceScores.put(other, confidence);
                            suspiciousAccounts.add(other);
                            linkAlts(player, other, confidence, "Shared IP: " + maskIp(ip));
                        }
                    }
                    
                    if (!suspiciousAccounts.isEmpty()) {
                        ipToPlayers.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).addAll(suspiciousAccounts);
                        ipToPlayers.get(ip).add(player);
                        alertStaff(player, ip, suspiciousAccounts, confidenceScores);
                    } else {
                        ipToPlayers.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(player);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        );
    }

    private int calculateConfidence(long currentLogin, long otherLastSeen, int otherLoginCount, int recentLogins) {
        int confidence = 0;
        long timeDiff = currentLogin - otherLastSeen;
        
        if (timeDiff < SEVEN_DAYS) {
            confidence += 50;
            if (timeDiff < 24 * 60 * 60 * 1000) {
                confidence += 20;
            }
        } else if (timeDiff < THIRTY_DAYS) {
            confidence += 30;
        } else {
            confidence += 10;
        }
        
        if (recentLogins > 5) {
            confidence += 15;
        } else if (recentLogins > 2) {
            confidence += 10;
        }
        
        if (otherLoginCount > 10) {
            confidence += 10;
        } else if (otherLoginCount < 3) {
            confidence -= 10;
        }
        
        return Math.min(100, Math.max(0, confidence));
    }

    private void linkAlts(UUID p1, UUID p2, int confidence, String reason) {
        altConfidence.computeIfAbsent(p1, k -> new ConcurrentHashMap<>()).put(p2, confidence);
        altConfidence.computeIfAbsent(p2, k -> new ConcurrentHashMap<>()).put(p1, confidence);

        database.executeAsync(
            "INSERT OR REPLACE INTO alt_links (player1_uuid, player2_uuid, confidence, reason, detected_at) " +
            "VALUES (?, ?, ?, ?, ?)",
            stmt -> {
                try {
                    stmt.setString(1, p1.toString());
                    stmt.setString(2, p2.toString());
                    stmt.setInt(3, confidence);
                    stmt.setString(4, reason);
                    stmt.setLong(5, System.currentTimeMillis());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    private void alertStaff(UUID player, String ip, Set<UUID> alts, Map<UUID, Integer> confidences) {
        int highConfCount = 0;
        int avgConfidence = 0;
        
        for (UUID alt : alts) {
            int conf = confidences.getOrDefault(alt, 0);
            avgConfidence += conf;
            if (conf >= 75) highConfCount++;
        }
        
        avgConfidence /= Math.max(1, alts.size());
        
        String severity = avgConfidence >= 80 ? "HIGH" : avgConfidence >= 60 ? "MEDIUM" : "LOW";
        NamedTextColor color = avgConfidence >= 80 ? NamedTextColor.RED : 
                               avgConfidence >= 60 ? NamedTextColor.GOLD : NamedTextColor.YELLOW;

        Component alert = Component.text("[Alt Alert] ", color)
            .append(Component.text("[" + severity + "] ", color))
            .append(Component.text(getName(player), NamedTextColor.WHITE))
            .append(Component.text(" linked to ", NamedTextColor.GRAY))
            .append(Component.text(alts.size() + " account(s)", NamedTextColor.YELLOW))
            .append(Component.text(" (" + avgConfidence + "% confidence)", NamedTextColor.DARK_GRAY));

        for (Player staff : server.getAllPlayers()) {
            if (staff.hasPermission("velocity.alts.alerts") && alertsEnabled.contains(staff.getUniqueId())) {
                staff.sendMessage(alert);
                if (highConfCount > 0) {
                    staff.sendMessage(Component.text("  " + highConfCount + " high-confidence matches", NamedTextColor.RED));
                }
            }
        }
    }

    public Set<UUID> getAlts(UUID player) {
        return new HashSet<>(altConfidence.getOrDefault(player, Collections.emptyMap()).keySet());
    }

    public Map<UUID, Integer> getAltsWithConfidence(UUID player) {
        return new HashMap<>(altConfidence.getOrDefault(player, Collections.emptyMap()));
    }

    public Set<String> getIps(UUID player) {
        return new HashSet<>(playerToIps.getOrDefault(player, Collections.emptySet()));
    }

    public Set<UUID> getPlayersOnIp(String ip) {
        return new HashSet<>(ipToPlayers.getOrDefault(ip, Collections.emptySet()));
    }

    public boolean isLinked(UUID p1, UUID p2) {
        return altConfidence.getOrDefault(p1, Collections.emptyMap()).containsKey(p2);
    }

    public int getConfidence(UUID p1, UUID p2) {
        return altConfidence.getOrDefault(p1, Collections.emptyMap()).getOrDefault(p2, 0);
    }

    public void whitelistIp(String ip, String reason, UUID addedBy) {
        whitelistedIps.add(ip);

        database.executeAsync(
            "INSERT OR REPLACE INTO ip_whitelist (ip_address, reason, added_by, added_at) VALUES (?, ?, ?, ?)",
            stmt -> {
                try {
                    stmt.setString(1, ip);
                    stmt.setString(2, reason);
                    stmt.setString(3, addedBy.toString());
                    stmt.setLong(4, System.currentTimeMillis());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        ipToPlayers.remove(ip);
    }

    public void removeWhitelist(String ip) {
        whitelistedIps.remove(ip);

        database.executeAsync(
            "DELETE FROM ip_whitelist WHERE ip_address = ?",
            stmt -> {
                try {
                    stmt.setString(1, ip);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    public boolean isWhitelisted(String ip) {
        return whitelistedIps.contains(ip);
    }

    public Set<String> getWhitelistedIps() {
        return new HashSet<>(whitelistedIps);
    }

    public void toggleAlerts(UUID player) {
        if (alertsEnabled.contains(player)) {
            alertsEnabled.remove(player);
            database.executeAsync(
                "UPDATE alt_alerts SET enabled = 0 WHERE player_uuid = ?",
                stmt -> {
                    try {
                        stmt.setString(1, player.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            );
        } else {
            alertsEnabled.add(player);
            database.executeAsync(
                "INSERT OR REPLACE INTO alt_alerts (player_uuid, enabled) VALUES (?, 1)",
                stmt -> {
                    try {
                        stmt.setString(1, player.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            );
        }
    }

    public boolean hasAlertsEnabled(UUID player) {
        return alertsEnabled.contains(player);
    }

    public void cleanOldData() {
        long threshold = System.currentTimeMillis() - NINETY_DAYS;
        
        database.executeAsync(
            "DELETE FROM login_history WHERE timestamp < ?",
            stmt -> {
                try {
                    stmt.setLong(1, threshold);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
        
        database.executeAsync(
            "DELETE FROM alt_links WHERE confidence < 50 AND detected_at < ?",
            stmt -> {
                try {
                    stmt.setLong(1, threshold);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        database.executeAsync(
            "DELETE FROM player_ips WHERE last_seen < ? AND login_count < 3",
            stmt -> {
                try {
                    stmt.setLong(1, threshold);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    public void unlinkAlts(UUID p1, UUID p2) {
        Map<UUID, Integer> p1Map = altConfidence.get(p1);
        if (p1Map != null) p1Map.remove(p2);
        
        Map<UUID, Integer> p2Map = altConfidence.get(p2);
        if (p2Map != null) p2Map.remove(p1);

        database.executeAsync(
            "DELETE FROM alt_links WHERE (player1_uuid = ? AND player2_uuid = ?) OR (player1_uuid = ? AND player2_uuid = ?)",
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

    public String maskIp(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }
        return ip;
    }

    public String getName(UUID uuid) {
        String cached = uuidCache.getName(uuid);
        if (cached != null) return cached;
        return server.getPlayer(uuid).map(Player::getUsername).orElse("Unknown");
    }
}