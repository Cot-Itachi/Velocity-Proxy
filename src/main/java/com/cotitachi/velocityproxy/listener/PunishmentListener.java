package com.cotitachi.velocityproxy.listener;

import com.cotitachi.velocityproxy.manager.PunishmentManager;
import com.cotitachi.velocityproxy.punishment.Punishment;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.UUID;

public class PunishmentListener {

    private final PunishmentManager punishmentManager;

    public PunishmentListener(PunishmentManager punishmentManager) {
        this.punishmentManager = punishmentManager;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID pid = player.getUniqueId();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        if (punishmentManager.isIpBanned(ip)) {
            Punishment ipban = punishmentManager.getIpBan(ip);
            event.setResult(ResultedEvent.ComponentResult.denied(createIpBanMessage(ipban)));
            return;
        }

        if (punishmentManager.isBanned(pid)) {
            Punishment ban = punishmentManager.getBan(pid);
            event.setResult(ResultedEvent.ComponentResult.denied(createBanMessage(ban)));
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID pid = player.getUniqueId();

        if (punishmentManager.isMuted(pid)) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            
            Punishment mute = punishmentManager.getMute(pid);
            player.sendMessage(createMuteMessage(mute));
        }
    }

    private Component createBanMessage(Punishment punishment) {
        boolean temp = punishment.getType().name().contains("TEMP");
        
        Component message = Component.text(temp ? "TEMPORARILY BANNED" : "YOU ARE BANNED", NamedTextColor.RED)
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Reason: ", NamedTextColor.GRAY))
            .append(Component.text(punishment.getReason(), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Banned by: ", NamedTextColor.GRAY))
            .append(Component.text(punishmentManager.getName(punishment.getIssuer()), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Date: ", NamedTextColor.GRAY))
            .append(Component.text(punishmentManager.formatDate(punishment.getIssuedAt()), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.newline());

        if (temp) {
            message = message.append(Component.text("Expires in: ", NamedTextColor.GRAY))
                .append(Component.text(punishmentManager.formatDuration(punishment.getRemainingTime()), NamedTextColor.YELLOW));
        } else {
            message = message.append(Component.text("This ban is permanent.", NamedTextColor.RED));
        }

        message = message.append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Ban ID: " + punishment.getId(), NamedTextColor.DARK_GRAY));

        return message;
    }

    private Component createIpBanMessage(Punishment punishment) {
        return Component.text("YOUR IP IS BANNED", NamedTextColor.RED)
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Reason: ", NamedTextColor.GRAY))
            .append(Component.text(punishment.getReason(), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Banned by: ", NamedTextColor.GRAY))
            .append(Component.text(punishmentManager.getName(punishment.getIssuer()), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Date: ", NamedTextColor.GRAY))
            .append(Component.text(punishmentManager.formatDate(punishment.getIssuedAt()), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("This ban is permanent.", NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.text("All accounts from your IP are banned.", NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Ban ID: " + punishment.getId(), NamedTextColor.DARK_GRAY));
    }

    private Component createMuteMessage(Punishment punishment) {
        boolean temp = punishment.getType().name().contains("TEMP");
        
        return Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY)
            .append(Component.newline())
            .append(Component.text("[Muted] ", NamedTextColor.RED))
            .append(Component.text("You cannot chat", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("Reason: ", NamedTextColor.GRAY))
            .append(Component.text(punishment.getReason(), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Muted by: ", NamedTextColor.GRAY))
            .append(Component.text(punishmentManager.getName(punishment.getIssuer()), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Expires: ", NamedTextColor.GRAY))
            .append(Component.text(temp ? punishmentManager.formatDuration(punishment.getRemainingTime()) : "Never", temp ? NamedTextColor.YELLOW : NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.text("Mute ID: " + punishment.getId(), NamedTextColor.DARK_GRAY))
            .append(Component.newline())
            .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }
}