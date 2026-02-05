package com.cotitachi.velocityproxy.command;

import com.cotitachi.velocityproxy.VelocityProxy;
import com.cotitachi.velocityproxy.manager.CooldownManager;
import com.cotitachi.velocityproxy.manager.FriendManager;
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

import java.util.Set;
import java.util.UUID;

public class FriendCommand {

    private final VelocityProxy plugin;

    public FriendCommand(VelocityProxy plugin) {
        this.plugin = plugin;
    }

    public BrigadierCommand create() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("friend")
            .executes(ctx -> {
                if (ctx.getSource() instanceof Player) {
                    help((Player) ctx.getSource());
                }
                return Command.SINGLE_SUCCESS;
            })
            .then(LiteralArgumentBuilder.<CommandSource>literal("add")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        if (ctx.getSource() instanceof Player) {
                            add((Player) ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                        }
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(LiteralArgumentBuilder.<CommandSource>literal("remove")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        if (ctx.getSource() instanceof Player) {
                            remove((Player) ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                        }
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(LiteralArgumentBuilder.<CommandSource>literal("accept")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        if (ctx.getSource() instanceof Player) {
                            accept((Player) ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                        }
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(LiteralArgumentBuilder.<CommandSource>literal("deny")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        if (ctx.getSource() instanceof Player) {
                            deny((Player) ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                        }
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                .executes(ctx -> {
                    if (ctx.getSource() instanceof Player) {
                        list((Player) ctx.getSource());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
            .then(LiteralArgumentBuilder.<CommandSource>literal("requests")
                .executes(ctx -> {
                    if (ctx.getSource() instanceof Player) {
                        requests((Player) ctx.getSource());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
            .then(LiteralArgumentBuilder.<CommandSource>literal("toggle")
                .executes(ctx -> {
                    if (ctx.getSource() instanceof Player) {
                        toggle((Player) ctx.getSource());
                    }
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    private void help(Player p) {
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("Friends", NamedTextColor.AQUA, TextDecoration.BOLD));
        p.sendMessage(Component.text("/f add <player>", NamedTextColor.WHITE)
            .append(Component.text(" - Send request", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/f remove <player>", NamedTextColor.WHITE)
            .append(Component.text(" - Remove friend", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/f accept <player>", NamedTextColor.WHITE)
            .append(Component.text(" - Accept request", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/f deny <player>", NamedTextColor.WHITE)
            .append(Component.text(" - Deny request", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/f list", NamedTextColor.WHITE)
            .append(Component.text(" - View friends", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/f requests", NamedTextColor.WHITE)
            .append(Component.text(" - Pending requests", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/f toggle", NamedTextColor.WHITE)
            .append(Component.text(" - Toggle requests", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private void add(Player p, String target) {
        FriendManager fm = plugin.getFriendManager();
        CooldownManager cm = plugin.getCooldownManager();
        UUIDCache cache = plugin.getUuidCache();
        UUID pid = p.getUniqueId();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            p.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (pid.equals(tid)) {
            p.sendMessage(Component.text("Can't add yourself", NamedTextColor.RED));
            return;
        }

        if (fm.areFriends(pid, tid)) {
            p.sendMessage(Component.text("Already friends", NamedTextColor.RED));
            return;
        }

        if (fm.hasOutgoing(pid, tid)) {
            p.sendMessage(Component.text("Request already sent", NamedTextColor.RED));
            return;
        }

        if (cm.has(pid, tid)) {
            p.sendMessage(Component.text("Wait " + cm.format(cm.remaining(pid, tid)), NamedTextColor.RED));
            return;
        }

        if (!fm.requestsEnabled(tid)) {
            p.sendMessage(Component.text("Requests disabled", NamedTextColor.RED));
            return;
        }

        fm.sendRequest(pid, tid);
        cm.set(pid, tid);
        p.sendMessage(Component.text("Request sent", NamedTextColor.GREEN));
    }

    private void remove(Player p, String target) {
        FriendManager fm = plugin.getFriendManager();
        UUIDCache cache = plugin.getUuidCache();
        UUID pid = p.getUniqueId();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            p.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (!fm.areFriends(pid, tid)) {
            p.sendMessage(Component.text("Not friends", NamedTextColor.RED));
            return;
        }

        fm.remove(pid, tid);
        p.sendMessage(Component.text("Removed " + fm.getName(tid), NamedTextColor.YELLOW));
    }

    private void accept(Player p, String target) {
        FriendManager fm = plugin.getFriendManager();
        UUIDCache cache = plugin.getUuidCache();
        UUID pid = p.getUniqueId();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            p.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (!fm.hasIncoming(pid, tid)) {
            p.sendMessage(Component.text("No request from that player", NamedTextColor.RED));
            return;
        }

        fm.accept(pid, tid);
        p.sendMessage(Component.text("Accepted request from ", NamedTextColor.GREEN)
            .append(Component.text(fm.getName(tid), NamedTextColor.YELLOW)));
    }

    private void deny(Player p, String target) {
        FriendManager fm = plugin.getFriendManager();
        UUIDCache cache = plugin.getUuidCache();
        UUID pid = p.getUniqueId();

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            p.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (!fm.hasIncoming(pid, tid)) {
            p.sendMessage(Component.text("No request from that player", NamedTextColor.RED));
            return;
        }

        fm.deny(pid, tid);
        p.sendMessage(Component.text("Denied request", NamedTextColor.YELLOW));
    }

    private void list(Player p) {
        FriendManager fm = plugin.getFriendManager();
        Set<UUID> friends = fm.getFriends(p.getUniqueId());

        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("Friends ", NamedTextColor.AQUA, TextDecoration.BOLD)
            .append(Component.text("(" + friends.size() + ")", NamedTextColor.GRAY, TextDecoration.BOLD)));

        if (friends.isEmpty()) {
            p.sendMessage(Component.text("No friends yet", NamedTextColor.GRAY));
        } else {
            for (UUID fid : friends) {
                plugin.getServer().getPlayer(fid).ifPresentOrElse(
                    friend -> {
                        String server = friend.getCurrentServer()
                            .map(s -> s.getServerInfo().getName())
                            .orElse("lobby");
                        p.sendMessage(Component.text("● ", NamedTextColor.GREEN)
                            .append(Component.text(friend.getUsername(), NamedTextColor.WHITE))
                            .append(Component.text(" [" + server + "]", NamedTextColor.DARK_GRAY)));
                    },
                    () -> p.sendMessage(Component.text("● ", NamedTextColor.RED)
                        .append(Component.text(fm.getName(fid), NamedTextColor.GRAY)))
                );
            }
        }

        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private void requests(Player p) {
        FriendManager fm = plugin.getFriendManager();
        Set<UUID> in = fm.getIncoming(p.getUniqueId());
        Set<UUID> out = fm.getOutgoing(p.getUniqueId());

        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("Requests", NamedTextColor.AQUA, TextDecoration.BOLD));

        if (in.isEmpty() && out.isEmpty()) {
            p.sendMessage(Component.text("No pending requests", NamedTextColor.GRAY));
        } else {
            if (!in.isEmpty()) {
                p.sendMessage(Component.text("Incoming:", NamedTextColor.GREEN));
                in.forEach(uid -> p.sendMessage(Component.text("  " + fm.getName(uid), NamedTextColor.WHITE)));
            }
            if (!out.isEmpty()) {
                p.sendMessage(Component.text("Outgoing:", NamedTextColor.YELLOW));
                out.forEach(uid -> p.sendMessage(Component.text("  " + fm.getName(uid), NamedTextColor.WHITE)));
            }
        }

        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private void toggle(Player p) {
        FriendManager fm = plugin.getFriendManager();
        UUID pid = p.getUniqueId();

        boolean current = fm.requestsEnabled(pid);
        fm.setRequestsEnabled(pid, !current);

        p.sendMessage(Component.text("Friend requests ", NamedTextColor.GRAY)
            .append(Component.text(!current ? "enabled" : "disabled", 
                !current ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }
}