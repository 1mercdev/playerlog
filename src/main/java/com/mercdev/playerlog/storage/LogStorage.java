package com.mercdev.playerlog.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All database access lives here. Every public method here does blocking
 * JDBC work and must be called from an async task, never the main thread.
 *
 * A single JDBC connection is reused for the plugin's lifetime; methods are
 * synchronized because the sqlite-jdbc driver does not support concurrent
 * statement execution on one connection.
 */
public class LogStorage {

    private static final int PAGE_SIZE = 8;

    private final JavaPlugin plugin;
    private Connection connection;

    // In-memory caches so tab completion never has to touch the DB
    // (tab completion runs synchronously on the main thread).
    private final Map<UUID, Integer> entryCounts = new ConcurrentHashMap<>();
    private final Set<String> knownPlayerNames = ConcurrentHashMap.newKeySet();

    public LogStorage(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void init() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, "logs.db");

        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    author_uuid TEXT NOT NULL,
                    author_name TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    message TEXT NOT NULL
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_player_uuid ON logs(player_uuid)");
        }

        loadCaches();
    }

    private void loadCaches() throws SQLException {
        String sql = "SELECT player_uuid, player_name, COUNT(*) as cnt FROM logs GROUP BY player_uuid";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                entryCounts.put(uuid, rs.getInt("cnt"));
                knownPlayerNames.add(rs.getString("player_name"));
            }
        }
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // shutting down anyway
            }
        }
    }

    /** Appends a new entry. Call from an async task. */
    public synchronized void addEntry(UUID playerUuid, String playerName,
                                       UUID authorUuid, String authorName,
                                       String message) throws SQLException {
        String sql = "INSERT INTO logs (player_uuid, player_name, author_uuid, author_name, timestamp, message) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, authorUuid.toString());
            ps.setString(4, authorName);
            ps.setLong(5, System.currentTimeMillis());
            ps.setString(6, message);
            ps.executeUpdate();
        }
        entryCounts.merge(playerUuid, 1, Integer::sum);
        knownPlayerNames.add(playerName);
    }

    /** Returns one page (newest first) of a player's log. Call from an async task. */
    public synchronized List<LogEntry> readPage(UUID playerUuid, int page) throws SQLException {
        int offset = Math.max(0, (page - 1) * PAGE_SIZE);
        String sql = "SELECT * FROM logs WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        List<LogEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, PAGE_SIZE);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new LogEntry(
                            rs.getLong("id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            UUID.fromString(rs.getString("author_uuid")),
                            rs.getString("author_name"),
                            rs.getLong("timestamp"),
                            rs.getString("message")
                    ));
                }
            }
        }
        return entries;
    }

    /** Deletes all entries for a player. Returns number of rows removed. Call from an async task. */
    public synchronized int clear(UUID playerUuid) throws SQLException {
        String sql = "DELETE FROM logs WHERE player_uuid = ?";
        int removed;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            removed = ps.executeUpdate();
        }
        entryCounts.remove(playerUuid);
        return removed;
    }

    // ---- cache accessors, safe to call from the main thread ----

    public int getEntryCount(UUID playerUuid) {
        return entryCounts.getOrDefault(playerUuid, 0);
    }

    public int getPageCount(UUID playerUuid) {
        int count = getEntryCount(playerUuid);
        return Math.max(1, (int) Math.ceil(count / (double) PAGE_SIZE));
    }

    public Set<String> getKnownPlayerNames() {
        return knownPlayerNames;
    }

    public static int pageSize() {
        return PAGE_SIZE;
    }
}
