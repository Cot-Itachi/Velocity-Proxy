package com.cotitachi.velocityproxy.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class DatabasePool {

    private final HikariDataSource dataSource;

    public DatabasePool(Path dataFolder) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dataFolder.resolve("friends.db"));
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(10000);
        config.setLeakDetectionThreshold(60000);
        
        this.dataSource = new HikariDataSource(config);
        initTables();
    }

    private void initTables() {
        try (Connection conn = getConnection()) {
            // Friends tables
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS friends (" +
                "player_uuid TEXT NOT NULL," +
                "friend_uuid TEXT NOT NULL," +
                "since INTEGER NOT NULL," +
                "PRIMARY KEY (player_uuid, friend_uuid))"
            );
            
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS friend_requests (" +
                "sender_uuid TEXT NOT NULL," +
                "receiver_uuid TEXT NOT NULL," +
                "sent_at INTEGER NOT NULL," +
                "PRIMARY KEY (sender_uuid, receiver_uuid))"
            );
            
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS player_settings (" +
                "player_uuid TEXT PRIMARY KEY," +
                "requests_enabled INTEGER DEFAULT 1)"
            );

            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS uuid_cache (" +
                "username TEXT PRIMARY KEY COLLATE NOCASE," +
                "uuid TEXT NOT NULL," +
                "updated INTEGER NOT NULL)"
            );

            // Party tables
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS parties (" +
                "id INTEGER PRIMARY KEY," +
                "leader_uuid TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "disbanded INTEGER DEFAULT 0)"
            );

            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS party_members (" +
                "party_id INTEGER NOT NULL," +
                "player_uuid TEXT NOT NULL," +
                "joined_at INTEGER NOT NULL," +
                "PRIMARY KEY (party_id, player_uuid))"
            );

            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS party_invites (" +
                "party_id INTEGER NOT NULL," +
                "invited_uuid TEXT NOT NULL," +
                "sent_at INTEGER NOT NULL," +
                "PRIMARY KEY (party_id, invited_uuid))"
            );

            // Alt detection tables
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS player_ips (" +
                "player_uuid TEXT NOT NULL," +
                "ip_address TEXT NOT NULL," +
                "first_seen INTEGER NOT NULL," +
                "last_seen INTEGER NOT NULL," +
                "login_count INTEGER DEFAULT 1," +
                "PRIMARY KEY (player_uuid, ip_address))"
            );

            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS alt_links (" +
                "player1_uuid TEXT NOT NULL," +
                "player2_uuid TEXT NOT NULL," +
                "confidence INTEGER NOT NULL," +
                "reason TEXT NOT NULL," +
                "detected_at INTEGER NOT NULL," +
                "PRIMARY KEY (player1_uuid, player2_uuid))"
            );

            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS ip_whitelist (" +
                "ip_address TEXT PRIMARY KEY," +
                "reason TEXT NOT NULL," +
                "added_by TEXT NOT NULL," +
                "added_at INTEGER NOT NULL)"
            );

            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS login_history (" +
                "player_uuid TEXT NOT NULL," +
                "ip_address TEXT NOT NULL," +
                "timestamp INTEGER NOT NULL)"
            );

            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS alt_alerts (" +
                "player_uuid TEXT PRIMARY KEY," +
                "enabled INTEGER DEFAULT 1)"
            );

            // Indices
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_friends_lookup ON friends(player_uuid)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_requests_lookup ON friend_requests(receiver_uuid)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_uuid_cache ON uuid_cache(uuid)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_party_members ON party_members(player_uuid)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_party_invites ON party_invites(invited_uuid)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_player_ips ON player_ips(ip_address)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_player_ips_last_seen ON player_ips(last_seen)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_alt_links ON alt_links(player2_uuid)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_login_history ON login_history(player_uuid)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_login_history_time ON login_history(timestamp)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public CompletableFuture<Void> executeAsync(String sql, Consumer<PreparedStatement> prepare) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                prepare.accept(stmt);
                stmt.execute();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public <T> CompletableFuture<T> queryAsync(String sql, Consumer<PreparedStatement> prepare, Function<ResultSet, T> handler) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                prepare.accept(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    return handler.apply(rs);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}