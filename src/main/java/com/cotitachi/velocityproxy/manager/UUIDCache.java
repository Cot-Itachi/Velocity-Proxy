package com.cotitachi.velocityproxy.manager;

import com.cotitachi.velocityproxy.database.DatabasePool;

import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UUIDCache {

    private final DatabasePool database;
    private final Map<String, UUID> nameToUUID = new ConcurrentHashMap<>();
    private final Map<UUID, String> uuidToName = new ConcurrentHashMap<>();

    public UUIDCache(DatabasePool database) {
        this.database = database;
        loadCache();
    }

    private void loadCache() {
        database.queryAsync("SELECT username, uuid FROM uuid_cache", stmt -> {}, rs -> {
            try {
                while (rs.next()) {
                    String name = rs.getString("username");
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    nameToUUID.put(name.toLowerCase(), uuid);
                    uuidToName.put(uuid, name);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public void cache(String username, UUID uuid) {
        nameToUUID.put(username.toLowerCase(), uuid);
        uuidToName.put(uuid, username);
        
        database.executeAsync(
            "INSERT OR REPLACE INTO uuid_cache (username, uuid, updated) VALUES (?, ?, ?)",
            stmt -> {
                try {
                    stmt.setString(1, username);
                    stmt.setString(2, uuid.toString());
                    stmt.setLong(3, System.currentTimeMillis());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        );
    }

    public UUID getUUID(String username) {
        return nameToUUID.get(username.toLowerCase());
    }

    public String getName(UUID uuid) {
        return uuidToName.get(uuid);
    }
}