package com.cotitachi.velocityproxy.manager;

import com.cotitachi.velocityproxy.database.DatabasePool;
import com.cotitachi.velocityproxy.punishment.Punishment;
import com.cotitachi.velocityproxy.punishment.PunishmentType;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentManager {

    private final DatabasePool database;
    private final ProxyServer server;
    private final UUIDCache uuidCache;
    private final AltDetectionManager altDetectionManager;

    private final Map<UUID, Punishment> activeBans = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> activeMutes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> warnCounts = new ConcurrentHashMap<>();
    private final Map<String, Punishment> activeIpBans = new ConcurrentHashMap<>();
    private final Map<UUID, List<Punishment>> history = new ConcurrentHashMap<>();

    private static final int MAX_WARNINGS = 3;

    public PunishmentManager(DatabasePool database, ProxyServer server, UUIDCache uuidCache, AltDetectionManager altDetectionManager) {
        this.database = database;
        this.server = server;
        this.uuidCache = uuidCache;
        this.altDetectionManager = altDetectionManager;
        loadPunishments();
    }

    private void loadPunishments() {
        database.queryAsync("SELECT * FROM punishments WHERE active = 1", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    String id = rs.getString("id");
                    UUID target = UUID.fromString(rs.getString("target_uuid"));
                    UUID issuer = rs.getString("issuer_uuid") != null ? UUID.fromString(rs.getString("issuer_uuid")) : null;
                    PunishmentType type = PunishmentType.valueOf(rs.getString("type"));
                    String reason = rs.getString("reason");
                    long issuedAt = rs.getLong("issued_at");
                    long expiresAt = rs.getLong("expires_at");
                    boolean active = rs.getInt("active") == 1;

                    Punishment punishment = new Punishment(id, target, issuer, type, reason, issuedAt, expiresAt, active);

                    if (punishment.isExpired()) {
                        expirePunishment(punishment);
                        continue;
                    }

                    switch (type) {
                        case BAN:
                        case TEMPBAN:
                            activeBans.put(target, punishment);
                            break;
                        case MUTE:
                        case TEMPMUTE:
                            activeMutes.put(target, punishment);
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });

        database.queryAsync("SELECT * FROM ip_bans WHERE active = 1", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String ip = rs.getString("ip_address");
                    UUID issuer = rs.getString("issuer_uuid") != null ? UUID.fromString(rs.getString("issuer_uuid")) : null;
                    String reason = rs.getString("reason");
                    long issuedAt = rs.getLong("issued_at");
                    boolean active = rs.getInt("active") == 1;

                    Punishment punishment = new Punishment(id, null, issuer, PunishmentType.IPBAN, reason, issuedAt, -1, active);
                    activeIpBans.put(ip, punishment);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });

        database.queryAsync("SELECT target_uuid, COUNT(*) as count FROM punishments WHERE type = 'WARN' AND active = 1 GROUP BY target_uuid", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    UUID target = UUID.fromString(rs.getString("target_uuid"));
                    int count = rs.getInt("count");
                    warnCounts.put(target, count);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public void ban(UUID target, UUID issuer, String reason, boolean banAlts) {
        String id = generateId("BAN");
        long now = System.currentTimeMillis();

        Punishment punishment = new Punishment(id, target, issuer, PunishmentType.BAN, reason, now, -1, true);
        activeBans.put(target, punishment);

        savePunishment(punishment);

        server.getPlayer(target).ifPresent(player -> 
            player.disconnect(createBanMessage(punishment))
        );

        if (banAlts) {
            banAllAlts(target, issuer, "Alt account of " + getName(target));
        }
    }

    public void tempban(UUID target, UUID issuer, String reason, long duration, boolean banAlts) {
        String id = generateId("TBAN");
        long now = System.currentTimeMillis();
        long expiresAt = now + duration;

        Punishment punishment = new Punishment(id, target, issuer, PunishmentType.TEMPBAN, reason, now, expiresAt, true);
        activeBans.put(target, punishment);

        savePunishment(punishment);

        server.getPlayer(target).ifPresent(player -> 
            player.disconnect(createBanMessage(punishment))
        );

        if (banAlts) {
            banAllAlts(target, issuer, "Alt account of " + getName(target));
        }
    }

    public void unban(UUID target) {
        Punishment ban = activeBans.remove(target);
        if (ban != null) {
            ban.setActive(false);
            database.executeAsync(
                "UPDATE punishments SET active = 0 WHERE id = ?",
                stmt -> {
                    try {
                        stmt.setString(1, ban.getId());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            );
        }
    }

    public void mute(UUID target, UUID issuer, String reason) {
        String id = generateId("MUTE");
        long now = System.currentTimeMillis();

        Punishment punishment = new Punishment(id, target, issuer, PunishmentType.MUTE, reason, now, -1, true);
        activeMutes.put(target, punishment);

        savePunishment(punishment);

        server.getPlayer(target).ifPresent(player ->
            player.sendMessage(createMuteMessage(punishment))
        );
    }

    public void tempmute(UUID target, UUID issuer, String reason, long duration) {
        String id = generateId("TMUTE");
        long now = System.currentTimeMillis();
        long expiresAt = now + duration;

        Punishment punishment = new Punishment(id, target, issuer, PunishmentType.TEMPMUTE, reason, now, expiresAt, true);
        activeMutes.put(target, punishment);

        savePunishment(punishment);

        server.getPlayer(target).ifPresent(player ->
            player.sendMessage(createMuteMessage(punishment))
        );
    }

    public void unmute(UUID target) {
        Punishment mute = activeMutes.remove(target);
        if (mute != null) {
            mute.setActive(false);
            database.executeAsync(
                "UPDATE punishments SET active = 0 WHERE id = ?",
                stmt -> {
                    try {
                        stmt.setString(1, mute.getId());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            );
        }
    }

    public void warn(UUID target, UUID issuer, String reason) {
        String id = generateId("WARN");
        long now = System.currentTimeMillis();

        Punishment punishment = new Punishment(id, target, issuer, PunishmentType.WARN, reason, now, -1, true);
        savePunishment(punishment);

        int count = warnCounts.getOrDefault(target, 0) + 1;
        warnCounts.put(target, count);

        server.getPlayer(target).ifPresent(player -> {
            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
            player.sendMessage(Component.text("You have been warned", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Reason: ", NamedTextColor.GRAY)
                .append(Component.text(reason, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("Warnings: " + count + "/" + MAX_WARNINGS, NamedTextColor.RED));
            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        });

        if (count >= MAX_WARNINGS) {
            tempban(target, issuer, "Too many warnings (" + count + "/" + MAX_WARNINGS + ")", 24 * 60 * 60 * 1000L, false);
        }
    }

    public void unwarn(UUID target) {
        int current = warnCounts.getOrDefault(target, 0);
        if (current > 0) {
            warnCounts.put(target, current - 1);
        }

        database.queryAsync(
            "SELECT id FROM punishments WHERE target_uuid = ? AND type = 'WARN' AND active = 1 ORDER BY issued_at DESC LIMIT 1",
            stmt -> {
                try {
                    stmt.setString(1, target.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            rs -> {
                try {
                    if (rs.next()) {
                        String id = rs.getString("id");
                        database.executeAsync(
                            "UPDATE punishments SET active = 0 WHERE id = ?",
                            stmt -> {
                                try {
                                    stmt.setString(1, id);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        );
    }

    public void kick(UUID target, UUID issuer, String reason) {
        server.getPlayer(target).ifPresent(player -> {
            Component message = Component.text("KICKED FROM SERVER", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Reason: ", NamedTextColor.GRAY))
                .append(Component.text(reason, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Kicked by: ", NamedTextColor.GRAY))
                .append(Component.text(getName(issuer), NamedTextColor.WHITE));
            
            player.disconnect(message);
        });

        String id = generateId("KICK");
        long now = System.currentTimeMillis();
        Punishment punishment = new Punishment(id, target, issuer, PunishmentType.KICK, reason, now, -1, true);
        savePunishment(punishment);
    }

    public void ipban(String ip, UUID issuer, String reason) {
        String id = generateId("IPBAN");
        long now = System.currentTimeMillis();

        Punishment punishment = new Punishment(id, null, issuer, PunishmentType.IPBAN, reason, now, -1, true);
        activeIpBans.put(ip, punishment);

        database.executeAsync(
            "INSERT INTO ip_bans (id, ip_address, issuer_uuid, reason, issued_at, active) VALUES (?, ?, ?, ?, ?, 1)",
            stmt -> {
                try {
                    stmt.setString(1, id);
                    stmt.setString(2, ip);
                    stmt.setString(3, issuer != null ? issuer.toString() : null);
                    stmt.setString(4, reason);
                    stmt.setLong(5, now);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );

        Set<UUID> affected = altDetectionManager.getPlayersOnIp(ip);
        for (UUID player : affected) {
            server.getPlayer(player).ifPresent(p -> 
                p.disconnect(createIpBanMessage(punishment))
            );
        }
    }

    public void unipban(String ip) {
        Punishment ipban = activeIpBans.remove(ip);
        if (ipban != null) {
            database.executeAsync(
                "UPDATE ip_bans SET active = 0 WHERE ip_address = ?",
                stmt -> {
                    try {
                        stmt.setString(1, ip);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            );
        }
    }

    private void banAllAlts(UUID target, UUID issuer, String reason) {
        Set<UUID> alts = altDetectionManager.getAlts(target);
        for (UUID alt : alts) {
            if (!activeBans.containsKey(alt)) {
                ban(alt, issuer, reason, false);
            }
        }
    }

    public boolean isBanned(UUID player) {
        Punishment ban = activeBans.get(player);
        if (ban != null && ban.isExpired()) {
            expirePunishment(ban);
            return false;
        }
        return ban != null;
    }

    public boolean isMuted(UUID player) {
        Punishment mute = activeMutes.get(player);
        if (mute != null && mute.isExpired()) {
            expirePunishment(mute);
            return false;
        }
        return mute != null;
    }

    public boolean isIpBanned(String ip) {
        return activeIpBans.containsKey(ip);
    }

    public Punishment getBan(UUID player) {
        return activeBans.get(player);
    }

    public Punishment getMute(UUID player) {
        return activeMutes.get(player);
    }

    public Punishment getIpBan(String ip) {
        return activeIpBans.get(ip);
    }

    public int getWarnCount(UUID player) {
        return warnCounts.getOrDefault(player, 0);
    }

    public List<Punishment> getHistory(UUID player) {
        if (history.containsKey(player)) {
            return history.get(player);
        }

        List<Punishment> punishments = new ArrayList<>();
        
        database.queryAsync(
            "SELECT * FROM punishments WHERE target_uuid = ? ORDER BY issued_at DESC",
            stmt -> {
                try {
                    stmt.setString(1, player.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            rs -> {
                try {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        UUID target = UUID.fromString(rs.getString("target_uuid"));
                        UUID issuer = rs.getString("issuer_uuid") != null ? UUID.fromString(rs.getString("issuer_uuid")) : null;
                        PunishmentType type = PunishmentType.valueOf(rs.getString("type"));
                        String reason = rs.getString("reason");
                        long issuedAt = rs.getLong("issued_at");
                        long expiresAt = rs.getLong("expires_at");
                        boolean active = rs.getInt("active") == 1;

                        punishments.add(new Punishment(id, target, issuer, type, reason, issuedAt, expiresAt, active));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        );

        history.put(player, punishments);
        return punishments;
    }

    private void expirePunishment(Punishment punishment) {
        punishment.setActive(false);
        
        switch (punishment.getType()) {
            case TEMPBAN:
                activeBans.remove(punishment.getTarget());
                server.getPlayer(punishment.getTarget()).ifPresent(player ->
                    player.sendMessage(Component.text("Your ban has expired", NamedTextColor.GREEN))
                );
                break;
            case TEMPMUTE:
                activeMutes.remove(punishment.getTarget());
                server.getPlayer(punishment.getTarget()).ifPresent(player ->
                    player.sendMessage(Component.text("Your mute has expired", NamedTextColor.GREEN))
                );
                break;
        }

        database.executeAsync(
            "UPDATE punishments SET active = 0 WHERE id = ?",
            stmt -> {
                try {
                    stmt.setString(1, punishment.getId());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    private void savePunishment(Punishment punishment) {
        database.executeAsync(
            "INSERT INTO punishments (id, target_uuid, issuer_uuid, type, reason, issued_at, expires_at, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            stmt -> {
                try {
                    stmt.setString(1, punishment.getId());
                    stmt.setString(2, punishment.getTarget().toString());
                    stmt.setString(3, punishment.getIssuer() != null ? punishment.getIssuer().toString() : null);
                    stmt.setString(4, punishment.getType().name());
                    stmt.setString(5, punishment.getReason());
                    stmt.setLong(6, punishment.getIssuedAt());
                    stmt.setLong(7, punishment.getExpiresAt());
                    stmt.setInt(8, punishment.isActive() ? 1 : 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    private Component createBanMessage(Punishment punishment) {
        boolean temp = punishment.getType() == PunishmentType.TEMPBAN;
        
        Component message = Component.text(temp ? "TEMPORARILY BANNED" : "YOU ARE BANNED", NamedTextColor.RED)
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Reason: ", NamedTextColor.GRAY))
            .append(Component.text(punishment.getReason(), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Banned by: ", NamedTextColor.GRAY))
            .append(Component.text(getName(punishment.getIssuer()), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Date: ", NamedTextColor.GRAY))
            .append(Component.text(formatDate(punishment.getIssuedAt()), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.newline());

        if (temp) {
            message = message.append(Component.text("Expires in: ", NamedTextColor.GRAY))
                .append(Component.text(formatDuration(punishment.getRemainingTime()), NamedTextColor.YELLOW));
        } else {
            message = message.append(Component.text("This ban is permanent.", NamedTextColor.RED));
        }

        message = message.append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Ban ID: " + punishment.getId(), NamedTextColor.DARK_GRAY));

        return message;
    }

    private Component createMuteMessage(Punishment punishment) {
        boolean temp = punishment.getType() == PunishmentType.TEMPMUTE;
        
        return Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY)
            .append(Component.newline())
            .append(Component.text("[Muted] ", NamedTextColor.RED))
            .append(Component.text("You cannot chat", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("Reason: ", NamedTextColor.GRAY))
            .append(Component.text(punishment.getReason(), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Muted by: ", NamedTextColor.GRAY))
            .append(Component.text(getName(punishment.getIssuer()), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Expires: ", NamedTextColor.GRAY))
            .append(Component.text(temp ? formatDuration(punishment.getRemainingTime()) : "Never", temp ? NamedTextColor.YELLOW : NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.text("Mute ID: " + punishment.getId(), NamedTextColor.DARK_GRAY))
            .append(Component.newline())
            .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private Component createIpBanMessage(Punishment punishment) {
        return Component.text("YOUR IP IS BANNED", NamedTextColor.RED)
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Reason: ", NamedTextColor.GRAY))
            .append(Component.text(punishment.getReason(), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Banned by: ", NamedTextColor.GRAY))
            .append(Component.text(getName(punishment.getIssuer()), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Date: ", NamedTextColor.GRAY))
            .append(Component.text(formatDate(punishment.getIssuedAt()), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("This ban is permanent.", NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.text("All accounts from your IP are banned.", NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Ban ID: " + punishment.getId(), NamedTextColor.DARK_GRAY));
    }

    public void checkExpirations() {
        List<Punishment> toExpire = new ArrayList<>();
        
        for (Punishment ban : activeBans.values()) {
            if (ban.isExpired()) {
                toExpire.add(ban);
            }
        }
        
        for (Punishment mute : activeMutes.values()) {
            if (mute.isExpired()) {
                toExpire.add(mute);
            }
        }
        
        toExpire.forEach(this::expirePunishment);
    }

    private String generateId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public String getName(UUID uuid) {
        if (uuid == null) return "System";
        String cached = uuidCache.getName(uuid);
        if (cached != null) return cached;
        return server.getPlayer(uuid).map(Player::getUsername).orElse("Unknown");
    }

    public String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy");
        return sdf.format(new Date(timestamp));
    }

    public String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            hours %= 24;
            return days + " day" + (days > 1 ? "s" : "") + (hours > 0 ? ", " + hours + " hour" + (hours > 1 ? "s" : "") : "");
        } else if (hours > 0) {
            minutes %= 60;
            return hours + " hour" + (hours > 1 ? "s" : "") + (minutes > 0 ? ", " + minutes + " minute" + (minutes > 1 ? "s" : "") : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "");
        }
    }

    public long parseDuration(String duration) {
        duration = duration.toLowerCase();
        long multiplier = 1;
        
        if (duration.endsWith("d")) {
            multiplier = 24 * 60 * 60 * 1000L;
            duration = duration.substring(0, duration.length() - 1);
        } else if (duration.endsWith("h")) {
            multiplier = 60 * 60 * 1000L;
            duration = duration.substring(0, duration.length() - 1);
        } else if (duration.endsWith("m")) {
            multiplier = 60 * 1000L;
            duration = duration.substring(0, duration.length() - 1);
        } else if (duration.endsWith("s")) {
            multiplier = 1000L;
            duration = duration.substring(0, duration.length() - 1);
        }
        
        try {
            return Long.parseLong(duration) * multiplier;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}