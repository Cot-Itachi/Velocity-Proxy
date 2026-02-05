package com.cotitachi.velocityproxy.command;

import com.cotitachi.velocityproxy.VelocityProxy;
import com.cotitachi.velocityproxy.manager.PartyManager;
import com.cotitachi.velocityproxy.manager.UUIDCache;
import com.cotitachi.velocityproxy.party.Party;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.UUID;

public class PartyCommand {

    private final VelocityProxy plugin;

    public PartyCommand(VelocityProxy plugin) {
        this.plugin = plugin;
    }

    public BrigadierCommand create() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("party")
            .executes(ctx -> {
                if (ctx.getSource() instanceof Player) {
                    help((Player) ctx.getSource());
                }
                return Command.SINGLE_SUCCESS;
            })
            .then(LiteralArgumentBuilder.<CommandSource>literal("create")
                .executes(ctx -> {
                    if (ctx.getSource() instanceof Player) {
                        create((Player) ctx.getSource());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
            .then(LiteralArgumentBuilder.<CommandSource>literal("disband")
                .executes(ctx -> {
                    if (ctx.getSource() instanceof Player) {
                        disband((Player) ctx.getSource());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
            .then(LiteralArgumentBuilder.<CommandSource>literal("invite")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        if (ctx.getSource() instanceof Player) {
                            invite((Player) ctx.getSource(), StringArgumentType.getString(ctx, "player"));
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
            .then(LiteralArgumentBuilder.<CommandSource>literal("kick")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        if (ctx.getSource() instanceof Player) {
                            kick((Player) ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                        }
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(LiteralArgumentBuilder.<CommandSource>literal("leave")
                .executes(ctx -> {
                    if (ctx.getSource() instanceof Player) {
                        leave((Player) ctx.getSource());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
            .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                .executes(ctx -> {
                    if (ctx.getSource() instanceof Player) {
                        list((Player) ctx.getSource());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
            .then(LiteralArgumentBuilder.<CommandSource>literal("transfer")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        if (ctx.getSource() instanceof Player) {
                            transfer((Player) ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                        }
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(LiteralArgumentBuilder.<CommandSource>literal("warp")
                .executes(ctx -> {
                    if (ctx.getSource() instanceof Player) {
                        warp((Player) ctx.getSource());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
            .then(LiteralArgumentBuilder.<CommandSource>literal("chat")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        if (ctx.getSource() instanceof Player) {
                            chat((Player) ctx.getSource(), StringArgumentType.getString(ctx, "message"));
                        }
                        return Command.SINGLE_SUCCESS;
                    })));

        return new BrigadierCommand(cmd.build());
    }

    public BrigadierCommand createChatAlias() {
        LiteralArgumentBuilder<CommandSource> cmd = LiteralArgumentBuilder
            .<CommandSource>literal("pc")
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("message", StringArgumentType.greedyString())
                .executes(ctx -> {
                    if (ctx.getSource() instanceof Player) {
                        chat((Player) ctx.getSource(), StringArgumentType.getString(ctx, "message"));
                    }
                    return Command.SINGLE_SUCCESS;
                }));

        return new BrigadierCommand(cmd.build());
    }

    private void help(Player p) {
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("Party", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        p.sendMessage(Component.text("/party create", NamedTextColor.WHITE)
            .append(Component.text(" - Create party", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/party disband", NamedTextColor.WHITE)
            .append(Component.text(" - Disband party", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/party invite <player>", NamedTextColor.WHITE)
            .append(Component.text(" - Invite player", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/party accept <player>", NamedTextColor.WHITE)
            .append(Component.text(" - Accept invite", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/party kick <player>", NamedTextColor.WHITE)
            .append(Component.text(" - Kick member", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/party leave", NamedTextColor.WHITE)
            .append(Component.text(" - Leave party", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/party list", NamedTextColor.WHITE)
            .append(Component.text(" - View members", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/party transfer <player>", NamedTextColor.WHITE)
            .append(Component.text(" - Transfer lead", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/party warp", NamedTextColor.WHITE)
            .append(Component.text(" - Warp party", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("/pc <message>", NamedTextColor.WHITE)
            .append(Component.text(" - Party chat", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private void create(Player p) {
        PartyManager pm = plugin.getPartyManager();
        UUID pid = p.getUniqueId();

        if (pm.hasParty(pid)) {
            p.sendMessage(Component.text("You're already in a party", NamedTextColor.RED));
            return;
        }

        pm.createParty(pid);
        p.sendMessage(Component.text("Party created", NamedTextColor.GREEN));
    }

    private void disband(Player p) {
        PartyManager pm = plugin.getPartyManager();
        UUID pid = p.getUniqueId();

        Party party = pm.getParty(pid);
        if (party == null) {
            p.sendMessage(Component.text("You're not in a party", NamedTextColor.RED));
            return;
        }

        if (!party.isLeader(pid)) {
            p.sendMessage(Component.text("Only the leader can disband", NamedTextColor.RED));
            return;
        }

        pm.disbandParty(party.getId());
    }

    private void invite(Player p, String target) {
        PartyManager pm = plugin.getPartyManager();
        UUIDCache cache = plugin.getUuidCache();
        UUID pid = p.getUniqueId();

        Party party = pm.getParty(pid);
        if (party == null) {
            p.sendMessage(Component.text("You're not in a party", NamedTextColor.RED));
            return;
        }

        if (!party.isLeader(pid)) {
            p.sendMessage(Component.text("Only the leader can invite", NamedTextColor.RED));
            return;
        }

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null) {
            p.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        if (party.isMember(tid)) {
            p.sendMessage(Component.text("Already in party", NamedTextColor.RED));
            return;
        }

        if (pm.hasParty(tid)) {
            p.sendMessage(Component.text("Player is in another party", NamedTextColor.RED));
            return;
        }

        if (party.size() >= 10) {
            p.sendMessage(Component.text("Party is full", NamedTextColor.RED));
            return;
        }

        pm.invitePlayer(party.getId(), tid);
        p.sendMessage(Component.text("Invited " + pm.getName(tid), NamedTextColor.GREEN));

        plugin.getServer().getPlayer(tid).ifPresent(target -> 
            target.sendMessage(Component.text("Party invite from ", NamedTextColor.GRAY)
                .append(Component.text(p.getUsername(), NamedTextColor.YELLOW))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text("/party accept " + p.getUsername(), NamedTextColor.AQUA)))
        );
    }

    private void accept(Player p, String inviter) {
        PartyManager pm = plugin.getPartyManager();
        UUIDCache cache = plugin.getUuidCache();
        UUID pid = p.getUniqueId();

        if (pm.hasParty(pid)) {
            p.sendMessage(Component.text("You're already in a party", NamedTextColor.RED));
            return;
        }

        UUID inviterUuid = plugin.getServer().getPlayer(inviter)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(inviter));

        if (inviterUuid == null) {
            p.sendMessage(Component.text("Player not found", NamedTextColor.RED));
            return;
        }

        Party party = pm.getParty(inviterUuid);
        if (party == null) {
            p.sendMessage(Component.text("That party doesn't exist", NamedTextColor.RED));
            return;
        }

        if (!pm.hasInvite(pid, party.getId())) {
            p.sendMessage(Component.text("No invite from that party", NamedTextColor.RED));
            return;
        }

        pm.acceptInvite(pid, party.getId());
        p.sendMessage(Component.text("Joined party", NamedTextColor.GREEN));
    }

    private void kick(Player p, String target) {
        PartyManager pm = plugin.getPartyManager();
        UUIDCache cache = plugin.getUuidCache();
        UUID pid = p.getUniqueId();

        Party party = pm.getParty(pid);
        if (party == null) {
            p.sendMessage(Component.text("You're not in a party", NamedTextColor.RED));
            return;
        }

        if (!party.isLeader(pid)) {
            p.sendMessage(Component.text("Only the leader can kick", NamedTextColor.RED));
            return;
        }

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null || !party.isMember(tid)) {
            p.sendMessage(Component.text("Player not in party", NamedTextColor.RED));
            return;
        }

        if (tid.equals(pid)) {
            p.sendMessage(Component.text("Can't kick yourself", NamedTextColor.RED));
            return;
        }

        pm.kickMember(party.getId(), tid);
    }

    private void leave(Player p) {
        PartyManager pm = plugin.getPartyManager();
        UUID pid = p.getUniqueId();

        if (!pm.hasParty(pid)) {
            p.sendMessage(Component.text("You're not in a party", NamedTextColor.RED));
            return;
        }

        pm.leaveParty(pid);
        p.sendMessage(Component.text("Left party", NamedTextColor.YELLOW));
    }

    private void list(Player p) {
        PartyManager pm = plugin.getPartyManager();
        UUID pid = p.getUniqueId();

        Party party = pm.getParty(pid);
        if (party == null) {
            p.sendMessage(Component.text("You're not in a party", NamedTextColor.RED));
            return;
        }

        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("Party Members ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
            .append(Component.text("(" + party.size() + "/10)", NamedTextColor.GRAY, TextDecoration.BOLD)));

        for (UUID member : party.getMembers()) {
            plugin.getServer().getPlayer(member).ifPresentOrElse(
                player -> {
                    String server = player.getCurrentServer()
                        .map(s -> s.getServerInfo().getName())
                        .orElse("lobby");
                    Component line = Component.text(party.isLeader(member) ? "★ " : "  ", NamedTextColor.YELLOW)
                        .append(Component.text(player.getUsername(), NamedTextColor.WHITE))
                        .append(Component.text(" [" + server + "]", NamedTextColor.DARK_GRAY));
                    p.sendMessage(line);
                },
                () -> p.sendMessage(Component.text("  " + pm.getName(member), NamedTextColor.GRAY))
            );
        }

        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private void transfer(Player p, String target) {
        PartyManager pm = plugin.getPartyManager();
        UUIDCache cache = plugin.getUuidCache();
        UUID pid = p.getUniqueId();

        Party party = pm.getParty(pid);
        if (party == null) {
            p.sendMessage(Component.text("You're not in a party", NamedTextColor.RED));
            return;
        }

        if (!party.isLeader(pid)) {
            p.sendMessage(Component.text("Only the leader can transfer", NamedTextColor.RED));
            return;
        }

        UUID tid = plugin.getServer().getPlayer(target)
            .map(Player::getUniqueId)
            .orElseGet(() -> cache.getUUID(target));

        if (tid == null || !party.isMember(tid)) {
            p.sendMessage(Component.text("Player not in party", NamedTextColor.RED));
            return;
        }

        pm.transferLeadership(party.getId(), tid);
    }

    private void warp(Player p) {
        PartyManager pm = plugin.getPartyManager();
        UUID pid = p.getUniqueId();

        Party party = pm.getParty(pid);
        if (party == null) {
            p.sendMessage(Component.text("You're not in a party", NamedTextColor.RED));
            return;
        }

        if (!party.isLeader(pid)) {
            p.sendMessage(Component.text("Only the leader can warp", NamedTextColor.RED));
            return;
        }

        ServerConnection leaderServer = p.getCurrentServer().orElse(null);
        if (leaderServer == null) return;

        for (UUID member : party.getMembers()) {
            if (member.equals(pid)) continue;
            
            plugin.getServer().getPlayer(member).ifPresent(player -> 
                player.createConnectionRequest(leaderServer.getServer()).fireAndForget()
            );
        }

        pm.broadcast(party, Component.text("Warping to ", NamedTextColor.GRAY)
            .append(Component.text(leaderServer.getServerInfo().getName(), NamedTextColor.AQUA)));
    }

    private void chat(Player p, String message) {
        PartyManager pm = plugin.getPartyManager();
        UUID pid = p.getUniqueId();

        Party party = pm.getParty(pid);
        if (party == null) {
            p.sendMessage(Component.text("You're not in a party", NamedTextColor.RED));
            return;
        }

        pm.chat(party, pid, message);
    }
}