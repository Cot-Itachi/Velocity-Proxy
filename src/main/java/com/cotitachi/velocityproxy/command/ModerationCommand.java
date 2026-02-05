package com.cotitachi.velocityproxy.command;

import com.cotitachi.velocityproxy.VelocityProxy;
import com.cotitachi.velocityproxy.manager.AltDetectionManager;
import com.cotitachi.velocityproxy.manager.PunishmentManager;
import com.cotitachi.velocityproxy.manager.UUIDCache;
import com.cotitachi.velocityproxy.punishment.Punishment;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ModerationCommand {

    private final VelocityProxy plugin;

    public ModerationCommand(VelocityProxy plugin) {
        this.plugin = plugin;
    }

    public BrigadierCommand createKick() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("kick")
            .requires(source -> source.hasPermission("velocity.mod.kick"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        kick(ctx.getSource(), 
                            StringArgumentType.getString(ctx, "player"),
                            StringArgumentType.getString(ctx, "reason"));
                        return Command.SINGLE_SUCCESS;
                    }))
                .executes(ctx -> {
                    kick(ctx.getSource(), 
                        StringArgumentType.getString(ctx, "player"),
                        "No reason specified");
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createBan() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("ban")
            .requires(source -> source.hasPermission("velocity.mod.ban"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ban(ctx.getSource(), 
                            StringArgumentType.getString(ctx, "player"),
                            StringArgumentType.getString(ctx, "reason"));
                        return Command.SINGLE_SUCCESS;
                    }))
                .executes(ctx -> {
                    ban(ctx.getSource(), 
                        StringArgumentType.getString(ctx, "player"),
                        "No reason specified");
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createTempban() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("tempban")
            .requires(source -> source.hasPermission("velocity.mod.ban"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("duration", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            tempban(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "duration"),
                                StringArgumentType.getString(ctx, "reason"));
                            return Command.SINGLE_SUCCESS;
                        }))
                    .executes(ctx -> {
                        tempban(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            StringArgumentType.getString(ctx, "duration"),
                            "No reason specified");
                        return Command.SINGLE_SUCCESS;
                    })));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createUnban() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("unban")
            .requires(source -> source.hasPermission("velocity.mod.unban"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    unban(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createMute() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("mute")
            .requires(source -> source.hasPermission("velocity.mod.mute"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        mute(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            StringArgumentType.getString(ctx, "reason"));
                        return Command.SINGLE_SUCCESS;
                    }))
                .executes(ctx -> {
                    mute(ctx.getSource(),
                        StringArgumentType.getString(ctx, "player"),
                        "No reason specified");
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createTempmute() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("tempmute")
            .requires(source -> source.hasPermission("velocity.mod.mute"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("duration", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            tempmute(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "duration"),
                                StringArgumentType.getString(ctx, "reason"));
                            return Command.SINGLE_SUCCESS;
                        }))
                    .executes(ctx -> {
                        tempmute(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            StringArgumentType.getString(ctx, "duration"),
                            "No reason specified");
                        return Command.SINGLE_SUCCESS;
                    })));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createUnmute() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("unmute")
            .requires(source -> source.hasPermission("velocity.mod.unmute"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    unmute(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createWarn() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("warn")
            .requires(source -> source.hasPermission("velocity.mod.warn"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        warn(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            StringArgumentType.getString(ctx, "reason"));
                        return Command.SINGLE_SUCCESS;
                    })));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createUnwarn() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("unwarn")
            .requires(source -> source.hasPermission("velocity.mod.unwarn"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    unwarn(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createHistory() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("history")
            .requires(source -> source.hasPermission("velocity.mod.history"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    history(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createIpban() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("ipban")
            .requires(source -> source.hasPermission("velocity.mod.ipban"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("target", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ipban(ctx.getSource(),
                            StringArgumentType.getString(ctx, "target"),
                            StringArgumentType.getString(ctx, "reason"));
                        return Command.SINGLE_SUCCESS;
                    }))
                .executes(ctx -> {
                    ipban(ctx.getSource(),
                        StringArgumentType.getString(ctx, "target"),
                        "No reason specified");
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createUnipban() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("unipban")
            .requires(source -> source.hasPermission("velocity.mod.unipban"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("ip", StringArgumentType.word())
                .executes(ctx -> {
                    unipban(ctx.getSource(), StringArgumentType.getString(ctx, "ip"));
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createStaffChat() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("staffchat")
            .requires(source -> source.hasPermission("velocity.mod.staffchat"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("message", StringArgumentType.greedyString())
                .executes(ctx -> {
                    staffchat(ctx.getSource(), StringArgumentType.getString(ctx, "message"));
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createBroadcast() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("broadcast")
            .requires(source -> source.hasPermission("velocity.mod.broadcast"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("message", StringArgumentType.greedyString())
                .executes(ctx -> {
                    broadcast(ctx.getSource(), StringArgumentType.getString(ctx, "message"));
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createFind() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("find")
            .requires(source -> source.hasPermission("velocity.mod.find"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    find(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createAlert() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("alert")
            .requires(source -> source.hasPermission("velocity.mod.alert"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("message", StringArgumentType.greedyString())
                .executes(ctx -> {
                    alert(ctx.getSource(), StringArgumentType.getString(ctx, "message"));
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    private void kick(CommandSource source, String target, String reason) {
        PunishmentManager pm = plugin.getPunishmentManager();
        UUIDCache cache = plugin.getUuidCache();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (!plugin.getServer().getPlayer(tid).isPresent()) {
            source.sendMessage(Component.text("Player is not online", NamedTextColor.RED));
            return;
        }

        UUID issuer = source instanceof Player ? ((Player) source).getUniqueId() : null;
        pm.kick(tid, issuer, reason);
        source.sendMessage(Component.text("Kicked " + pm.getName(tid) + " for: " + reason, NamedTextColor.GREEN));
    }

    private void ban(CommandSource source, String target, String reason) {
        PunishmentManager pm = plugin.getPunishmentManager();
        UUIDCache cache = plugin.getUuidCache();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (pm.isBanned(tid)) {
            source.sendMessage(Component.text("Player is already banned", NamedTextColor.RED));
            return;
        }

        UUID issuer = source instanceof Player ? ((Player) source).getUniqueId() : null;
        pm.ban(tid, issuer, reason, true);
        source.sendMessage(Component.text("Banned " + pm.getName(tid) + " for: " + reason, NamedTextColor.GREEN));
        
        AltDetectionManager adm = plugin.getAltDetectionManager();
        Set<UUID> alts = adm.getAlts(tid);
        if (!alts.isEmpty()) {
            source.sendMessage(Component.text("Also banned " + alts.size() + " alt account(s)", NamedTextColor.YELLOW));
        }
    }

    private void tempban(CommandSource source, String target, String duration, String reason) {
        PunishmentManager pm = plugin.getPunishmentManager();
        UUIDCache cache = plugin.getUuidCache();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (pm.isBanned(tid)) {
            source.sendMessage(Component.text("Player is already banned", NamedTextColor.RED));
            return;
        }

        long durationMs = pm.parseDuration(duration);
        if (durationMs == -1) {
            source.sendMessage(Component.text("Invalid duration format (examples: 1d, 12h, 30m)", NamedTextColor.RED));
            return;
        }

        UUID issuer = source instanceof Player ? ((Player) source).getUniqueId() : null;
        pm.tempban(tid, issuer, reason, durationMs, true);
        source.sendMessage(Component.text("Tempbanned " + pm.getName(tid) + " for " + pm.formatDuration(durationMs) + ": " + reason, NamedTextColor.GREEN));
        
        AltDetectionManager adm = plugin.getAltDetectionManager();
        Set<UUID> alts = adm.getAlts(tid);
        if (!alts.isEmpty()) {
            source.sendMessage(Component.text("Also banned " + alts.size() + " alt account(s)", NamedTextColor.YELLOW));
        }
    }

    private void unban(CommandSource source, String target) {
        PunishmentManager pm = plugin.getPunishmentManager();
        UUIDCache cache = plugin.getUuidCache();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (!pm.isBanned(tid)) {
            source.sendMessage(Component.text("Player is not banned", NamedTextColor.RED));
            return;
        }

        pm.unban(tid);
        source.sendMessage(Component.text("Unbanned " + pm.getName(tid), NamedTextColor.GREEN));
    }

    private void mute(CommandSource source, String target, String reason) {
        PunishmentManager pm = plugin.getPunishmentManager();
        UUIDCache cache = plugin.getUuidCache();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (pm.isMuted(tid)) {
            source.sendMessage(Component.text("Player is already muted", NamedTextColor.RED));
            return;
        }

        UUID issuer = source instanceof Player ? ((Player) source).getUniqueId() : null;
        pm.mute(tid, issuer, reason);
        source.sendMessage(Component.text("Muted " + pm.getName(tid) + " for: " + reason, NamedTextColor.GREEN));
    }

    private void tempmute(CommandSource source, String target, String duration, String reason) {
        PunishmentManager pm = plugin.getPunishmentManager();
        UUIDCache cache = plugin.getUuidCache();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (pm.isMuted(tid)) {
            source.sendMessage(Component.text("Player is already muted", NamedTextColor.RED));
            return;
        }

        long durationMs = pm.parseDuration(duration);
        if (durationMs == -1) {
            source.sendMessage(Component.text("Invalid duration format (examples: 1d, 12h, 30m)", NamedTextColor.RED));
            return;
        }

        UUID issuer = source instanceof Player ? ((Player) source).getUniqueId() : null;
        pm.tempmute(tid, issuer, reason, durationMs);
        source.sendMessage(Component.text("Tempmuted " + pm.getName(tid) + " for " + pm.formatDuration(durationMs) + ": " + reason, NamedTextColor.GREEN));
    }

    private void unmute(CommandSource source, String target) {
        PunishmentManager pm = plugin.getPunishmentManager();
        UUIDCache cache = plugin.getUuidCache();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (!pm.isMuted(tid)) {
            source.sendMessage(Component.text("Player is not muted", NamedTextColor.RED));
            return;
        }

        pm.unmute(tid);
        source.sendMessage(Component.text("Unmuted " + pm.getName(tid), NamedTextColor.GREEN));
    }

    private void warn(CommandSource source, String target, String reason) {
        PunishmentManager pm = plugin.getPunishmentManager();
        UUIDCache cache = plugin.getUuidCache();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        UUID issuer = source instanceof Player ? ((Player) source).getUniqueId() : null;
        pm.warn(tid, issuer, reason);
        source.sendMessage(Component.text("Warned " + pm.getName(tid) + " for: " + reason, NamedTextColor.GREEN));
        source.sendMessage(Component.text("Total warnings: " + pm.getWarnCount(tid), NamedTextColor.YELLOW));
    }

    private void unwarn(CommandSource source, String target) {
        PunishmentManager pm = plugin.getPunishmentManager();
        UUIDCache cache = plugin.getUuidCache();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (pm.getWarnCount(tid) == 0) {
            source.sendMessage(Component.text("Player has no warnings", NamedTextColor.RED));
            return;
        }

        pm.unwarn(tid);
        source.sendMessage(Component.text("Removed warning from " + pm.getName(tid), NamedTextColor.GREEN));
        source.sendMessage(Component.text("Remaining warnings: " + pm.getWarnCount(tid), NamedTextColor.YELLOW));
    }

    private void history(CommandSource source, String target) {
        PunishmentManager pm = plugin.getPunishmentManager();
        UUIDCache cache = plugin.getUuidCache();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        List<Punishment> punishments = pm.getHistory(tid);

        source.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        source.sendMessage(Component.text("Punishment History ", NamedTextColor.RED, TextDecoration.BOLD)
            .append(Component.text("- " + pm.getName(tid), NamedTextColor.GRAY)));
        source.sendMessage(Component.text("Total: " + punishments.size() + " | Active Warns: " + pm.getWarnCount(tid), NamedTextColor.GRAY));
        source.sendMessage(Component.text(""));

        if (punishments.isEmpty()) {
            source.sendMessage(Component.text("No punishment history", NamedTextColor.GRAY));
        } else {
            int shown = 0;
            for (Punishment p : punishments) {
                if (shown >= 10) break;
                
                NamedTextColor color = p.isActive() ? NamedTextColor.RED : NamedTextColor.GRAY;
                String status = p.isActive() ? "[ACTIVE] " : "[EXPIRED] ";
                
                source.sendMessage(Component.text(status + p.getType().name(), color)
                    .append(Component.text(" - " + p.getReason(), NamedTextColor.WHITE)));
                source.sendMessage(Component.text("  By: " + pm.getName(p.getIssuer()) + " | ID: " + p.getId(), NamedTextColor.DARK_GRAY));
                
                shown++;
            }
            
            if (punishments.size() > 10) {
                source.sendMessage(Component.text(""));
                source.sendMessage(Component.text("Showing 10 of " + punishments.size() + " total punishments", NamedTextColor.GRAY));
            }
        }

        source.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private void ipban(CommandSource source, String target, String reason) {
        PunishmentManager pm = plugin.getPunishmentManager();
        AltDetectionManager adm = plugin.getAltDetectionManager();
        UUIDCache cache = plugin.getUuidCache();

        String ip = null;
        
        if (target.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            ip = target;
        } else {
            UUID tid = plugin.getServer().getPlayer(target)
                .map(Player::getUniqueId)
                .orElseGet(() -> cache.getUUID(target));

            if (tid == null) {
                source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
                return;
            }

            Optional<Player> player = plugin.getServer().getPlayer(tid);
            if (player.isPresent()) {
                ip = player.get().getRemoteAddress().getAddress().getHostAddress();
            } else {
                Set<String> ips = adm.getIps(tid);
                if (ips.isEmpty()) {
                    source.sendMessage(Component.text("No IP data for this player", NamedTextColor.RED));
                    return;
                }
                ip = ips.iterator().next();
            }
        }

        if (pm.isIpBanned(ip)) {
            source.sendMessage(Component.text("IP is already banned", NamedTextColor.RED));
            return;
        }

        UUID issuer = source instanceof Player ? ((Player) source).getUniqueId() : null;
        pm.ipban(ip, issuer, reason);
        
        Set<UUID> affected = adm.getPlayersOnIp(ip);
        source.sendMessage(Component.text("IP banned: " + adm.maskIp(ip), NamedTextColor.GREEN));
        source.sendMessage(Component.text("Affected " + affected.size() + " account(s)", NamedTextColor.YELLOW));
    }

    private void unipban(CommandSource source, String ip) {
        PunishmentManager pm = plugin.getPunishmentManager();

        if (!pm.isIpBanned(ip)) {
            source.sendMessage(Component.text("IP is not banned", NamedTextColor.RED));
            return;
        }

        pm.unipban(ip);
        source.sendMessage(Component.text("Unbanned IP: " + ip, NamedTextColor.GREEN));
    }

    private void staffchat(CommandSource source, String message) {
        String name = source instanceof Player ? ((Player) source).getUsername() : "Console";
        
        Component msg = Component.text("[Staff] ", NamedTextColor.AQUA)
            .append(Component.text(name, NamedTextColor.WHITE))
            .append(Component.text(": ", NamedTextColor.DARK_GRAY))
            .append(Component.text(message, NamedTextColor.GRAY));

        for (Player staff : plugin.getServer().getAllPlayers()) {
            if (staff.hasPermission("velocity.mod.staffchat")) {
                staff.sendMessage(msg);
            }
        }
    }

    private void broadcast(CommandSource source, String message) {
        Component msg = Component.text("[Broadcast] ", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text(message, NamedTextColor.YELLOW));

        for (Player player : plugin.getServer().getAllPlayers()) {
            player.sendMessage(msg);
        }
    }

    private void find(CommandSource source, String target) {
        Optional<Player> player = plugin.getServer().getPlayer(target);
        
        if (player.isEmpty()) {
            source.sendMessage(Component.text("Player is not online", NamedTextColor.RED));
            return;
        }

        Optional<RegisteredServer> server = player.get().getCurrentServer().map(s -> s.getServer());
        
        if (server.isPresent()) {
            source.sendMessage(Component.text(target + " is on ", NamedTextColor.GRAY)
                .append(Component.text(server.get().getServerInfo().getName(), NamedTextColor.AQUA)));
        } else {
            source.sendMessage(Component.text(target + " is not on any server", NamedTextColor.GRAY));
        }
    }

    private void alert(CommandSource source, String message) {
        String name = source instanceof Player ? ((Player) source).getUsername() : "Console";
        
        Component msg = Component.text("[Alert] ", NamedTextColor.RED, TextDecoration.BOLD)
            .append(Component.text(name + ": ", NamedTextColor.WHITE))
            .append(Component.text(message, NamedTextColor.YELLOW));

        for (Player staff : plugin.getServer().getAllPlayers()) {
            if (staff.hasPermission("velocity.mod.alert")) {
                staff.sendMessage(msg);
            }
        }
    }
}