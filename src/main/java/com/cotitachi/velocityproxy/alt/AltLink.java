package com.cotitachi.velocityproxy.alt;

import java.util.UUID;

public class AltLink {

    private final UUID player1;
    private final UUID player2;
    private final int confidence;
    private final String reason;
    private final long detected;

    public AltLink(UUID player1, UUID player2, int confidence, String reason, long detected) {
        this.player1 = player1;
        this.player2 = player2;
        this.confidence = confidence;
        this.reason = reason;
        this.detected = detected;
    }

    public UUID getPlayer1() {
        return player1;
    }

    public UUID getPlayer2() {
        return player2;
    }

    public int getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    public long getDetected() {
        return detected;
    }
}