package com.cotitachi.velocityproxy.punishment;

import java.util.UUID;

public class Punishment {

    private final String id;
    private final UUID target;
    private final UUID issuer;
    private final PunishmentType type;
    private final String reason;
    private final long issuedAt;
    private final long expiresAt;
    private boolean active;

    public Punishment(String id, UUID target, UUID issuer, PunishmentType type, String reason, long issuedAt, long expiresAt, boolean active) {
        this.id = id;
        this.target = target;
        this.issuer = issuer;
        this.type = type;
        this.reason = reason;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public UUID getTarget() {
        return target;
    }

    public UUID getIssuer() {
        return issuer;
    }

    public PunishmentType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPermanent() {
        return expiresAt == -1;
    }

    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= expiresAt;
    }

    public long getRemainingTime() {
        if (isPermanent()) return -1;
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }
}