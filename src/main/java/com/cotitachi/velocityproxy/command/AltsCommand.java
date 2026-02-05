package com.cotitachi.velocityproxy.command;

import com.cotitachi.velocityproxy.VelocityProxy;
import com.cotitachi.velocityproxy.manager.AltDetectionManager;
import com.cotitachi.velocityproxy.manager.UUIDCache;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;

public class AltsCommand {

    private final VelocityProxy plugin;

    public AltsCommand(VelocityProxy plugin) {
        this.plugin = plugin;
    }

    public BrigadierCommand create() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("alts")
            .requires(source -> source.hasPermission("velocity.alts.check"))
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    check(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createWhitelist() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("ipwhitelist")
            .requires(source -> source.hasPermission("velocity.alts.whitelist"))
            .executes(ctx -> {
                help(ctx.getSource());
                return Command.SINGLE_SUCCESS;
            })
            .then(LiteralArgumentBuilder.<CommandSource>literal("add")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("ip", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            add(ctx.getSource(), 
                                StringArgumentType.getString(ctx, "ip"),
                                StringArgumentType.getString(ctx, "reason"));
                            return Command.SINGLE_SUCCESS;
                        }))))
            .then(LiteralArgumentBuilder.<CommandSource>literal("remove")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("ip", StringArgumentType.word())
                    .executes(ctx -> {
                        remove(ctx.getSource(), StringArgumentType.getString(ctx, "ip"));
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                .executes(ctx -> {
                    list(ctx.getSource());
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createAlerts() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("altalerts")
            .requires(source -> source.hasPermission("velocity.alts.alerts"))
            .executes(ctx -> {
                if (ctx.getSource() instanceof Player) {
                    toggle((Player) ctx.getSource());
                }
                return Command.SINGLE_SUCCESS;
            });

        return new BrigadierCommand(cmd.build());
    }

    private void check(CommandSource source, String target) {
        AltDetectionManager adm = plugin.getAltDetectionManager();
        UUIDCache cache = plugin.getUuidCache();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            source.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        Map<UUID, Integer> alts = adm.getAltsWithConfidence(tid);
        Set<String> ips = adm.getIps(tid);

        source.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        source.sendMessage(Component.text("Alt Accounts ", NamedTextColor.RED, TextDecoration.BOLD)
            .append(Component.text("- " + adm.getName(tid), NamedTextColor.GRAY)));

        if (alts.isEmpty()) {
            source.sendMessage(Component.text("No linked accounts detected", NamedTextColor.GRAY));
        } else {
            source.sendMessage(Component.text("Linked Accounts (" + alts.size() + "):", NamedTextColor.YELLOW));
            
            List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(alts.entrySet());
            sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
            
            for (Map.Entry<UUID, Integer> entry : sorted) {
                UUID alt = entry.getKey();
                int confidence = entry.getValue();
                boolean online = plugin.getServer().getPlayer(alt).isPresent();
                
                NamedTextColor confColor = confidence >= 80 ? NamedTextColor.RED :
                                           confidence >= 60 ? NamedTextColor.GOLD : NamedTextColor.YELLOW;
                
                Component status = Component.text(online ? "● " : "○ ", online ? NamedTextColor.GREEN : NamedTextColor.RED);
                source.sendMessage(status
                    .append(Component.text(adm.getName(alt), NamedTextColor.WHITE))
                    .append(Component.text(" [" + confidence + "%]", confColor)));
            }
        }

        source.sendMessage(Component.text(""));
        source.sendMessage(Component.text("Known IPs (" + ips.size() + "):", NamedTextColor.AQUA));
        for (String ip : ips) {
            Set<UUID> shared = adm.getPlayersOnIp(ip);
            String masked = adm.maskIp(ip);
            Component whitelisted = adm.isWhitelisted(ip) 
                ? Component.text(" [WHITELISTED]", NamedTextColor.GREEN) 
                : Component.text("");
            
            source.sendMessage(Component.text("  " + masked, NamedTextColor.WHITE)
                .append(Component.text(" (" + shared.size() + " accounts)", NamedTextColor.DARK_GRAY))
                .append(whitelisted));
        }

        source.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private void help(CommandSource source) {
        source.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        source.sendMessage(Component.text("IP Whitelist", NamedTextColor.AQUA, TextDecoration.BOLD));
        source.sendMessage(Component.text("/ipwhitelist add <ip> <reason>", NamedTextColor.WHITE)
            .append(Component.text(" - Add IP", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/ipwhitelist remove <ip>", NamedTextColor.WHITE)
            .append(Component.text(" - Remove IP", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/ipwhitelist list", NamedTextColor.WHITE)
            .append(Component.text(" - View all", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private void add(CommandSource source, String ip, String reason) {
        AltDetectionManager adm = plugin.getAltDetectionManager();
        
        UUID addedBy = source instanceof Player ? ((Player) source).getUniqueId() : null;
        if (addedBy == null) {
            source.sendMessage(Component.text("Console cannot whitelist IPs", NamedTextColor.RED));
            return;
        }

        if (adm.isWhitelisted(ip)) {
            source.sendMessage(Component.text("IP already whitelisted", NamedTextColor.RED));
            return;
        }

        adm.whitelistIp(ip, reason, addedBy);
        source.sendMessage(Component.text("Whitelisted " + ip, NamedTextColor.GREEN));
    }

    private void remove(CommandSource source, String ip) {
        AltDetectionManager adm = plugin.getAltDetectionManager();

        if (!adm.isWhitelisted(ip)) {
            source.sendMessage(Component.text("IP not whitelisted", NamedTextColor.RED));
            return;
        }

        adm.removeWhitelist(ip);
        source.sendMessage(Component.text("Removed " + ip + " from whitelist", NamedTextColor.YELLOW));
    }

    private void list(CommandSource source) {
        AltDetectionManager adm = plugin.getAltDetectionManager();
        Set<String> whitelisted = adm.getWhitelistedIps();

        source.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        source.sendMessage(Component.text("Whitelisted IPs ", NamedTextColor.AQUA, TextDecoration.BOLD)
            .append(Component.text("(" + whitelisted.size() + ")", NamedTextColor.GRAY)));

        if (whitelisted.isEmpty()) {
            source.sendMessage(Component.text("No whitelisted IPs", NamedTextColor.GRAY));
        } else {
            for (String ip : whitelisted) {
                source.sendMessage(Component.text("  " + adm.maskIp(ip), NamedTextColor.WHITE));
            }
        }

        source.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private void toggle(Player p) {
        AltDetectionManager adm = plugin.getAltDetectionManager();
        UUID pid = p.getUniqueId();

        adm.toggleAlerts(pid);
        boolean enabled = adm.hasAlertsEnabled(pid);

        p.sendMessage(Component.text("Alt alerts ", NamedTextColor.GRAY)
            .append(Component.text(enabled ? "enabled" : "disabled", 
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }
}