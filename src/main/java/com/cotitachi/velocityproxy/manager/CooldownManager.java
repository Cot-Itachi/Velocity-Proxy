package com.cotitachi.velocityproxy.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final Map<UUID, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 300000; // 5 minutes

    public void set(UUID sender, UUID receiver) {
        cooldowns.computeIfAbsent(sender, k -> new ConcurrentHashMap<>())
            .put(receiver, System.currentTimeMillis());
    }

    public boolean has(UUID sender, UUID receiver) {
        Map<UUID, Long> senderMap = cooldowns.get(sender);
        if (senderMap == null) return false;

        Long time = senderMap.get(receiver);
        if (time == null) return false;

        return System.currentTimeMillis() - time < COOLDOWN_MS;
    }

    public long remaining(UUID sender, UUID receiver) {
        Map<UUID, Long> senderMap = cooldowns.get(sender);
        if (senderMap == null) return 0;

        Long time = senderMap.get(receiver);
        if (time == null) return 0;

        long elapsed = System.currentTimeMillis() - time;
        return Math.max(0, COOLDOWN_MS - elapsed);
    }

    public String format(long ms) {
        long secs = ms / 1000;
        long mins = secs / 60;
        secs %= 60;
        return String.format("%d:%02d", mins, secs);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        cooldowns.values().forEach(map -> 
            map.entrySet().removeIf(entry -> now - entry.getValue() >= COOLDOWN_MS)
        );
        cooldowns.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}