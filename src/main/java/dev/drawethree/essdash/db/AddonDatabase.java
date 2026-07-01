package dev.drawethree.essdash.db;

import dev.drawethree.essdash.auth.Permission;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * The dashboard's own embedded SQLite store: staff accounts and a fast player-search index.
 * Live game data is never stored here — it is read from EssentialsX on demand.
 */
public class AddonDatabase {

    private final File dataFolder;
    private final Logger logger;
    private Connection connection;

    public AddonDatabase(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void init() {
        try {
            dataFolder.mkdirs();
            String url = "jdbc:sqlite:" + new File(dataFolder, "dashboard.db").getAbsolutePath();
            connection = DriverManager.getConnection(url);
            createTables();
        } catch (SQLException e) {
            logger.severe("Failed to open dashboard database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_index (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    last_seen INTEGER NOT NULL
                )
                """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    username      TEXT PRIMARY KEY,
                    password_hash TEXT NOT NULL,
                    role          TEXT NOT NULL DEFAULT 'STAFF',
                    permissions   TEXT NOT NULL DEFAULT ''
                )
                """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS punishments (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid       TEXT NOT NULL,
                    name       TEXT NOT NULL,
                    type       TEXT NOT NULL,
                    reason     TEXT,
                    staff      TEXT NOT NULL,
                    duration_ms INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
                """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_punishments_uuid ON punishments(uuid)");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_notes (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid       TEXT NOT NULL,
                    note       TEXT NOT NULL,
                    staff      TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_notes_uuid ON player_notes(uuid)");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS metrics_history (
                    ts             INTEGER PRIMARY KEY,
                    online         INTEGER NOT NULL,
                    total_economy  TEXT,
                    tps            REAL,
                    memory_used_mb INTEGER
                )
                """);
            // Active login sessions — backs JWT revocation (each token carries a jti recorded here).
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sessions (
                    jti        TEXT PRIMARY KEY,
                    username   TEXT NOT NULL,
                    role       TEXT NOT NULL,
                    ip         TEXT,
                    user_agent TEXT,
                    issued_at  INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    last_seen  INTEGER NOT NULL,
                    revoked    INTEGER NOT NULL DEFAULT 0
                )
                """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sessions_username ON sessions(username)");
            // Recovery codes for 2FA — one row per single-use code (bcrypt-hashed).
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS totp_recovery (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    username  TEXT NOT NULL,
                    code_hash TEXT NOT NULL
                )
                """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_totp_recovery_username ON totp_recovery(username)");
            // Scheduled tasks — recurring/one-off broadcasts, commands, mail-all, timed restarts.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS scheduled_tasks (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    name              TEXT NOT NULL,
                    type              TEXT NOT NULL,
                    payload           TEXT,
                    countdown_seconds INTEGER NOT NULL DEFAULT 0,
                    schedule_type     TEXT NOT NULL,
                    next_run          INTEGER NOT NULL,
                    interval_ms       INTEGER NOT NULL DEFAULT 0,
                    enabled           INTEGER NOT NULL DEFAULT 1,
                    created_by        TEXT,
                    created_at        INTEGER NOT NULL,
                    last_run          INTEGER NOT NULL DEFAULT 0,
                    last_result       TEXT
                )
                """);
            // Generic key/value settings (white-label branding, future toggles).
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS settings (
                    key   TEXT PRIMARY KEY,
                    value TEXT
                )
                """);
            // Persisted in-game chat for moderation history/search (soft-deletable).
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS chat_log (
                    id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid    TEXT NOT NULL,
                    name    TEXT NOT NULL,
                    message TEXT NOT NULL,
                    ts      INTEGER NOT NULL,
                    deleted INTEGER NOT NULL DEFAULT 0
                )
                """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_chat_log_ts ON chat_log(ts)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_chat_log_uuid ON chat_log(uuid)");
            // Append-on-join login history — backs the player timeline and alt detection.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_logins (
                    id   INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL,
                    ip   TEXT,
                    ts   INTEGER NOT NULL
                )
                """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_logins_uuid ON player_logins(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_logins_ip ON player_logins(ip)");
            // Economy transaction ledger (dashboard + in-game balance changes).
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS economy_log (
                    id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid    TEXT NOT NULL,
                    name    TEXT NOT NULL,
                    delta   TEXT,
                    balance TEXT,
                    source  TEXT NOT NULL,
                    staff   TEXT,
                    ts      INTEGER NOT NULL
                )
                """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_economy_log_uuid ON economy_log(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_economy_log_ts ON economy_log(ts)");
            // Migrate existing user tables: add columns if missing (SQLite has no "ADD COLUMN IF NOT EXISTS").
            addColumnIfMissing(stmt, "users", "must_change_password INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(stmt, "users", "totp_secret TEXT");
            addColumnIfMissing(stmt, "users", "totp_enabled INTEGER NOT NULL DEFAULT 0");
            // Extra metric columns added after the initial release (NULL for older samples).
            addColumnIfMissing(stmt, "metrics_history", "loaded_chunks INTEGER");
            addColumnIfMissing(stmt, "metrics_history", "entities INTEGER");
        }
    }

    private void addColumnIfMissing(Statement stmt, String table, String columnDef) {
        try {
            stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + columnDef);
        } catch (SQLException ignored) {
            // Column already exists.
        }
    }

    // ── Player index ─────────────────────────────────────────────────────────

    public void upsertPlayer(UUID uuid, String name) {
        String sql = "INSERT OR REPLACE INTO player_index (uuid, name, last_seen) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to upsert player index: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> searchPlayers(String query, int limit, int offset) {
        String sql = """
            SELECT uuid, name, last_seen
            FROM player_index
            WHERE name LIKE ?
            ORDER BY last_seen DESC
            LIMIT ? OFFSET ?
            """;
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "%" + query + "%");
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uuid", rs.getString("uuid"));
                    row.put("name", rs.getString("name"));
                    row.put("lastSeen", rs.getLong("last_seen"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to search players: " + e.getMessage());
        }
        return results;
    }

    public int countPlayers(String query) {
        String sql = "SELECT COUNT(*) FROM player_index WHERE name LIKE ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "%" + query + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.warning("Failed to count players: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Filter the player index by name, an optional uuid allow/deny set (used for the online/offline
     * status filter, which the DB can't know on its own), a minimum last-seen cutoff, and sort order.
     * {@code includeUuids} (non-null) restricts to those uuids; {@code excludeUuids} (non-null) removes
     * them. {@code sortByName} sorts A→Z, otherwise most-recently-seen first.
     */
    public record PlayerFilter(String query, Set<String> includeUuids, Set<String> excludeUuids,
                               long minLastSeen, boolean sortByName) {}

    public List<Map<String, Object>> searchPlayersFiltered(PlayerFilter f, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT uuid, name, last_seen FROM player_index ");
        List<Object> args = buildFilter(sql, f);
        sql.append(f.sortByName() ? " ORDER BY name COLLATE NOCASE ASC" : " ORDER BY last_seen DESC");
        sql.append(" LIMIT ? OFFSET ?");
        args.add(limit); args.add(offset);

        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uuid", rs.getString("uuid"));
                    row.put("name", rs.getString("name"));
                    row.put("lastSeen", rs.getLong("last_seen"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to search players (filtered): " + e.getMessage());
        }
        return results;
    }

    public int countPlayersFiltered(PlayerFilter f) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM player_index ");
        List<Object> args = buildFilter(sql, f);
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.warning("Failed to count players (filtered): " + e.getMessage());
        }
        return 0;
    }

    /** Appends the shared WHERE clause for the filtered queries and returns its bind args. */
    private List<Object> buildFilter(StringBuilder sql, PlayerFilter f) {
        List<Object> args = new ArrayList<>();
        List<String> clauses = new ArrayList<>();
        clauses.add("name LIKE ?");
        args.add("%" + (f.query() == null ? "" : f.query()) + "%");
        if (f.minLastSeen() > 0) { clauses.add("last_seen >= ?"); args.add(f.minLastSeen()); }
        if (f.includeUuids() != null) {
            clauses.add("uuid IN (" + placeholders(f.includeUuids().size()) + ")");
            args.addAll(f.includeUuids());
        }
        if (f.excludeUuids() != null && !f.excludeUuids().isEmpty()) {
            clauses.add("uuid NOT IN (" + placeholders(f.excludeUuids().size()) + ")");
            args.addAll(f.excludeUuids());
        }
        sql.append("WHERE ").append(String.join(" AND ", clauses));
        return args;
    }

    private static String placeholders(int n) {
        if (n == 0) return "NULL"; // "uuid IN (NULL)" matches nothing — safe for an empty include set
        return String.join(",", Collections.nCopies(n, "?"));
    }

    public record PlayerIndexEntry(UUID uuid, String name, long lastSeen) {}

    public void batchUpsertPlayers(List<PlayerIndexEntry> players) {
        if (players.isEmpty()) return;
        String sql = "INSERT OR REPLACE INTO player_index (uuid, name, last_seen) VALUES (?, ?, ?)";
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (PlayerIndexEntry entry : players) {
                    ps.setString(1, entry.uuid().toString());
                    ps.setString(2, entry.name());
                    ps.setLong(3, entry.lastSeen());
                    ps.addBatch();
                }
                ps.executeBatch();
                connection.commit();
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.warning("Failed to batch upsert players: " + e.getMessage());
            try { connection.rollback(); } catch (SQLException ignored) {}
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    public boolean hasUsers() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            logger.warning("Failed to check users: " + e.getMessage());
        }
        return false;
    }

    /** Bootstrap: create the initial admin account if no users exist. */
    public void createUser(String username, String bcryptHash) {
        String sql = "INSERT OR IGNORE INTO users (username, password_hash, role, permissions) VALUES (?, ?, 'ADMIN', '')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, bcryptHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to create user: " + e.getMessage());
        }
    }

    public String getPasswordHash(String username) {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("password_hash");
            }
        } catch (SQLException e) {
            logger.warning("Failed to get password hash: " + e.getMessage());
        }
        return null;
    }

    public UserRecord getUser(String username) {
        String sql = "SELECT username, password_hash, role, permissions FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rowToUser(rs);
            }
        } catch (SQLException e) {
            logger.warning("Failed to get user: " + e.getMessage());
        }
        return null;
    }

    public List<UserRecord> listUsers() {
        String sql = "SELECT username, password_hash, role, permissions FROM users ORDER BY username";
        List<UserRecord> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(rowToUser(rs));
        } catch (SQLException e) {
            logger.warning("Failed to list users: " + e.getMessage());
        }
        return result;
    }

    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            logger.warning("Failed to check username: " + e.getMessage());
        }
        return false;
    }

    public void createStaffUser(String username, String bcryptHash, String role, String permissions) {
        String sql = "INSERT INTO users (username, password_hash, role, permissions) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, bcryptHash);
            ps.setString(3, role);
            ps.setString(4, permissions);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to create staff user: " + e.getMessage());
        }
    }

    /** Update role, permissions, and optionally password (pass null to keep existing). */
    public void updateStaffUser(String username, String bcryptHash, String role, String permissions) {
        String sql = bcryptHash != null
                ? "UPDATE users SET password_hash = ?, role = ?, permissions = ? WHERE username = ?"
                : "UPDATE users SET role = ?, permissions = ? WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int i = 1;
            if (bcryptHash != null) ps.setString(i++, bcryptHash);
            ps.setString(i++, role);
            ps.setString(i++, permissions);
            ps.setString(i, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to update staff user: " + e.getMessage());
        }
    }

    /** Update own username and/or password. Pass null newPasswordHash to keep the existing password. */
    public void updateSelfCredentials(String username, String newUsername, String newPasswordHash) {
        try {
            if (!username.equals(newUsername) && newPasswordHash != null) {
                exec("UPDATE users SET username = ?, password_hash = ? WHERE username = ?", newUsername, newPasswordHash, username);
            } else if (!username.equals(newUsername)) {
                exec("UPDATE users SET username = ? WHERE username = ?", newUsername, username);
            } else if (newPasswordHash != null) {
                exec("UPDATE users SET password_hash = ? WHERE username = ?", newPasswordHash, username);
            }
        } catch (SQLException e) {
            logger.warning("Failed to update credentials: " + e.getMessage());
        }
    }

    private void exec(String sql, String... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            ps.executeUpdate();
        }
    }

    public void deleteUser(String username) {
        try {
            exec("DELETE FROM users WHERE username = ?", username);
        } catch (SQLException e) {
            logger.warning("Failed to delete user: " + e.getMessage());
        }
    }

    private UserRecord rowToUser(ResultSet rs) throws SQLException {
        return new UserRecord(
            rs.getString("username"),
            rs.getString("role"),
            Permission.parse(rs.getString("permissions"))
        );
    }

    public record UserRecord(String username, String role, Set<Permission> permissions) {}

    /** Whether the given user must change their password before using the panel. */
    public boolean mustChangePassword(String username) {
        String sql = "SELECT must_change_password FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) != 0;
            }
        } catch (SQLException e) {
            logger.warning("Failed to read must_change_password: " + e.getMessage());
        }
        return false;
    }

    public void setMustChangePassword(String username, boolean value) {
        try {
            exec("UPDATE users SET must_change_password = " + (value ? 1 : 0) + " WHERE username = ?", username);
        } catch (SQLException e) {
            logger.warning("Failed to set must_change_password: " + e.getMessage());
        }
    }

    // ── Punishment history ──────────────────────────────────────────────────────

    public void insertPunishment(UUID uuid, String name, String type, String reason, String staff, long durationMs) {
        String sql = "INSERT INTO punishments (uuid, name, type, reason, staff, duration_ms, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, type);
            ps.setString(4, reason);
            ps.setString(5, staff);
            ps.setLong(6, durationMs);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to insert punishment: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listPunishments(UUID uuid, int limit, int offset) {
        String sql = """
            SELECT id, name, type, reason, staff, duration_ms, created_at
            FROM punishments
            WHERE uuid = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("name", rs.getString("name"));
                    row.put("type", rs.getString("type"));
                    row.put("reason", rs.getString("reason"));
                    row.put("staff", rs.getString("staff"));
                    row.put("durationMs", rs.getLong("duration_ms"));
                    row.put("createdAt", rs.getLong("created_at"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to list punishments: " + e.getMessage());
        }
        return results;
    }

    public int countPunishments(UUID uuid) {
        String sql = "SELECT COUNT(*) FROM punishments WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.warning("Failed to count punishments: " + e.getMessage());
        }
        return 0;
    }

    public int countPunishmentsOfType(UUID uuid, String type) {
        String sql = "SELECT COUNT(*) FROM punishments WHERE uuid = ? AND type = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.warning("Failed to count punishments of type: " + e.getMessage());
        }
        return 0;
    }

    // ── Player notes ────────────────────────────────────────────────────────────

    public void addNote(UUID uuid, String note, String staff) {
        String sql = "INSERT INTO player_notes (uuid, note, staff, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, note);
            ps.setString(3, staff);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to add note: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listNotes(UUID uuid) {
        String sql = "SELECT id, note, staff, created_at FROM player_notes WHERE uuid = ? ORDER BY created_at DESC";
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("note", rs.getString("note"));
                    row.put("staff", rs.getString("staff"));
                    row.put("createdAt", rs.getLong("created_at"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to list notes: " + e.getMessage());
        }
        return results;
    }

    public void deleteNote(UUID uuid, long id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_notes WHERE id = ? AND uuid = ?")) {
            ps.setLong(1, id);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to delete note: " + e.getMessage());
        }
    }

    // ── Chat log ──────────────────────────────────────────────────────────────

    public void insertChat(UUID uuid, String name, String message, long ts) {
        String sql = "INSERT INTO chat_log (uuid, name, message, ts) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, message);
            ps.setLong(4, ts);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to insert chat: " + e.getMessage());
        }
    }

    /** Newest-first chat history. {@code query} (substring on name/message) and {@code uuid}
     *  are optional filters; deleted lines are excluded. */
    public List<Map<String, Object>> listChat(String query, String uuid, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, uuid, name, message, ts FROM chat_log WHERE deleted = 0");
        List<Object> args = new ArrayList<>();
        if (uuid != null && !uuid.isBlank()) { sql.append(" AND uuid = ?"); args.add(uuid); }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (message LIKE ? OR name LIKE ?)");
            args.add("%" + query + "%"); args.add("%" + query + "%");
        }
        sql.append(" ORDER BY ts DESC LIMIT ? OFFSET ?");
        args.add(limit); args.add(offset);

        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("uuid", rs.getString("uuid"));
                    row.put("name", rs.getString("name"));
                    row.put("message", rs.getString("message"));
                    row.put("ts", rs.getLong("ts"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to list chat: " + e.getMessage());
        }
        return results;
    }

    public int countChat(String query, String uuid) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM chat_log WHERE deleted = 0");
        List<Object> args = new ArrayList<>();
        if (uuid != null && !uuid.isBlank()) { sql.append(" AND uuid = ?"); args.add(uuid); }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (message LIKE ? OR name LIKE ?)");
            args.add("%" + query + "%"); args.add("%" + query + "%");
        }
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.warning("Failed to count chat: " + e.getMessage());
        }
        return 0;
    }

    public void softDeleteChat(long id) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE chat_log SET deleted = 1 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to delete chat: " + e.getMessage());
        }
    }

    public void pruneChat(long beforeTs) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM chat_log WHERE ts < ?")) {
            ps.setLong(1, beforeTs);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to prune chat: " + e.getMessage());
        }
    }

    // ── Player logins (timeline + alt detection) ───────────────────────────────

    public void insertLogin(UUID uuid, String ip, long ts) {
        String sql = "INSERT INTO player_logins (uuid, ip, ts) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, ip);
            ps.setLong(3, ts);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to insert login: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listLogins(UUID uuid, int limit) {
        String sql = "SELECT ip, ts FROM player_logins WHERE uuid = ? ORDER BY ts DESC LIMIT ?";
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ip", rs.getString("ip"));
                    row.put("ts", rs.getLong("ts"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to list logins: " + e.getMessage());
        }
        return results;
    }

    /** Other accounts that have shared at least one IP with the given player. Returns one row per
     *  linked account: its uuid, latest known name, the shared IP(s), and its last-seen time. */
    public List<Map<String, Object>> findAlts(UUID uuid) {
        String sql = """
            SELECT a.uuid AS uuid,
                   (SELECT name FROM player_index pi WHERE pi.uuid = a.uuid) AS name,
                   GROUP_CONCAT(DISTINCT a.ip) AS ips,
                   MAX(a.ts) AS last_seen
            FROM player_logins a
            WHERE a.ip IS NOT NULL
              AND a.uuid <> ?
              AND a.ip IN (SELECT DISTINCT ip FROM player_logins WHERE uuid = ? AND ip IS NOT NULL)
            GROUP BY a.uuid
            ORDER BY last_seen DESC
            """;
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uuid", rs.getString("uuid"));
                    row.put("name", rs.getString("name"));
                    row.put("ips", rs.getString("ips"));
                    row.put("lastSeen", rs.getLong("last_seen"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to find alts: " + e.getMessage());
        }
        return results;
    }

    /** Login counts bucketed by weekday (0=Sunday … 6=Saturday) and hour (0–23) in the server's
     *  local time, for the activity heatmap. {@code uuid} is an optional filter (blank = server-wide).
     *  Returns one row per non-empty bucket: {weekday, hour, count}. */
    public List<Map<String, Object>> activityHeatmap(String uuid) {
        StringBuilder sql = new StringBuilder(
                "SELECT CAST(strftime('%w', ts/1000, 'unixepoch', 'localtime') AS INTEGER) AS weekday, "
              + "CAST(strftime('%H', ts/1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, "
              + "COUNT(*) AS cnt FROM player_logins");
        List<Object> args = new ArrayList<>();
        if (uuid != null && !uuid.isBlank()) { sql.append(" WHERE uuid = ?"); args.add(uuid); }
        sql.append(" GROUP BY weekday, hour");

        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("weekday", rs.getInt("weekday"));
                    row.put("hour", rs.getInt("hour"));
                    row.put("count", rs.getInt("cnt"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to compute activity heatmap: " + e.getMessage());
        }
        return results;
    }

    /** Distinct login IPs with the number of distinct players and total logins from each. Backs the
     *  GeoIP world map: each IP is geo-resolved once, then tallied into its country. */
    public List<Map<String, Object>> loginIpStats() {
        String sql = "SELECT ip, COUNT(DISTINCT uuid) AS players, COUNT(*) AS logins "
                + "FROM player_logins WHERE ip IS NOT NULL AND ip <> '' GROUP BY ip";
        List<Map<String, Object>> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ip", rs.getString("ip"));
                row.put("players", rs.getInt("players"));
                row.put("logins", rs.getInt("logins"));
                results.add(row);
            }
        } catch (SQLException e) {
            logger.warning("Failed to read login IP stats: " + e.getMessage());
        }
        return results;
    }

    // ── Economy ledger ──────────────────────────────────────────────────────────

    public void insertEconomyLog(UUID uuid, String name, String delta, String balance, String source, String staff, long ts) {
        String sql = "INSERT INTO economy_log (uuid, name, delta, balance, source, staff, ts) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, delta);
            ps.setString(4, balance);
            ps.setString(5, source);
            ps.setString(6, staff);
            ps.setLong(7, ts);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to insert economy log: " + e.getMessage());
        }
    }

    /** Newest-first transaction ledger; {@code uuid} is an optional filter. */
    public List<Map<String, Object>> listEconomyLog(String uuid, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, uuid, name, delta, balance, source, staff, ts FROM economy_log");
        List<Object> args = new ArrayList<>();
        if (uuid != null && !uuid.isBlank()) { sql.append(" WHERE uuid = ?"); args.add(uuid); }
        sql.append(" ORDER BY ts DESC LIMIT ? OFFSET ?");
        args.add(limit); args.add(offset);

        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("uuid", rs.getString("uuid"));
                    row.put("name", rs.getString("name"));
                    row.put("delta", rs.getString("delta"));
                    row.put("balance", rs.getString("balance"));
                    row.put("source", rs.getString("source"));
                    row.put("staff", rs.getString("staff"));
                    row.put("ts", rs.getLong("ts"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to list economy log: " + e.getMessage());
        }
        return results;
    }

    public int countEconomyLog(String uuid) {
        String sql = uuid != null && !uuid.isBlank()
                ? "SELECT COUNT(*) FROM economy_log WHERE uuid = ?"
                : "SELECT COUNT(*) FROM economy_log";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (uuid != null && !uuid.isBlank()) ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.warning("Failed to count economy log: " + e.getMessage());
        }
        return 0;
    }

    /** Net economy flow per player since {@code sinceTs}, ordered by total delta.
     *  Used for top earners (positive) / spenders (negative). */
    public List<Map<String, Object>> economyMovers(long sinceTs, boolean topEarners, int limit) {
        String sql = "SELECT uuid, name, SUM(CAST(delta AS REAL)) AS net FROM economy_log "
                + "WHERE ts >= ? AND delta IS NOT NULL "
                + "AND uuid <> '00000000-0000-0000-0000-000000000000' "  // skip bulk / debt-reset summary rows
                + "GROUP BY uuid "
                + "ORDER BY net " + (topEarners ? "DESC" : "ASC") + " LIMIT ?";
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sinceTs);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uuid", rs.getString("uuid"));
                    row.put("name", rs.getString("name"));
                    row.put("net", rs.getDouble("net"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to compute economy movers: " + e.getMessage());
        }
        return results;
    }

    public void pruneEconomyLog(long beforeTs) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM economy_log WHERE ts < ?")) {
            ps.setLong(1, beforeTs);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to prune economy log: " + e.getMessage());
        }
    }

    // ── Metrics history ─────────────────────────────────────────────────────────

    public void insertMetric(long ts, int online, String totalEconomy, Double tps, long memoryUsedMb,
                             int loadedChunks, int entities) {
        String sql = "INSERT OR REPLACE INTO metrics_history "
                + "(ts, online, total_economy, tps, memory_used_mb, loaded_chunks, entities) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, ts);
            ps.setInt(2, online);
            ps.setString(3, totalEconomy);
            if (tps == null) ps.setNull(4, Types.REAL); else ps.setDouble(4, tps);
            ps.setLong(5, memoryUsedMb);
            ps.setInt(6, loadedChunks);
            ps.setInt(7, entities);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to insert metric: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> recentMetrics(long sinceTs) {
        String sql = "SELECT ts, online, total_economy, tps, memory_used_mb, loaded_chunks, entities "
                + "FROM metrics_history WHERE ts >= ? ORDER BY ts ASC";
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sinceTs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ts", rs.getLong("ts"));
                    row.put("online", rs.getInt("online"));
                    row.put("totalEconomy", rs.getString("total_economy"));
                    double tps = rs.getDouble("tps");
                    row.put("tps", rs.wasNull() ? null : tps);
                    row.put("memoryUsedMb", rs.getLong("memory_used_mb"));
                    int chunks = rs.getInt("loaded_chunks");
                    row.put("loadedChunks", rs.wasNull() ? null : chunks);
                    int entities = rs.getInt("entities");
                    row.put("entities", rs.wasNull() ? null : entities);
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to read metrics: " + e.getMessage());
        }
        return results;
    }

    /** Drop metric samples older than the given timestamp to keep the table small. */
    public void pruneMetrics(long beforeTs) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM metrics_history WHERE ts < ?")) {
            ps.setLong(1, beforeTs);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to prune metrics: " + e.getMessage());
        }
    }

    // ── Sessions (JWT revocation) ─────────────────────────────────────────────────

    public void createSession(String jti, String username, String role, String ip,
                              String userAgent, long issuedAt, long expiresAt) {
        String sql = """
            INSERT OR REPLACE INTO sessions (jti, username, role, ip, user_agent, issued_at, expires_at, last_seen, revoked)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, jti);
            ps.setString(2, username);
            ps.setString(3, role);
            ps.setString(4, ip);
            ps.setString(5, userAgent);
            ps.setLong(6, issuedAt);
            ps.setLong(7, expiresAt);
            ps.setLong(8, issuedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to create session: " + e.getMessage());
        }
    }

    /** A session is usable only if it exists, is not revoked, and has not expired. */
    public boolean isSessionActive(String jti) {
        String sql = "SELECT 1 FROM sessions WHERE jti = ? AND revoked = 0 AND expires_at > ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, jti);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            logger.warning("Failed to check session: " + e.getMessage());
        }
        return false;
    }

    public void touchSession(String jti, String ip, long lastSeen) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE sessions SET last_seen = ?, ip = ? WHERE jti = ?")) {
            ps.setLong(1, lastSeen);
            ps.setString(2, ip);
            ps.setString(3, jti);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to touch session: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listSessions(String username) {
        return querySessions("WHERE username = ? AND revoked = 0 AND expires_at > ? ORDER BY last_seen DESC", username);
    }

    public List<Map<String, Object>> listAllSessions() {
        return querySessions("WHERE revoked = 0 AND expires_at > ? ORDER BY last_seen DESC", null);
    }

    private List<Map<String, Object>> querySessions(String whereClause, String username) {
        String sql = "SELECT jti, username, role, ip, user_agent, issued_at, expires_at, last_seen FROM sessions " + whereClause;
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int i = 1;
            if (username != null) ps.setString(i++, username);
            ps.setLong(i, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("jti", rs.getString("jti"));
                    row.put("username", rs.getString("username"));
                    row.put("role", rs.getString("role"));
                    row.put("ip", rs.getString("ip"));
                    row.put("userAgent", rs.getString("user_agent"));
                    row.put("issuedAt", rs.getLong("issued_at"));
                    row.put("expiresAt", rs.getLong("expires_at"));
                    row.put("lastSeen", rs.getLong("last_seen"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to list sessions: " + e.getMessage());
        }
        return results;
    }

    /** Returns the owner of a session, or null if it doesn't exist. */
    public String sessionOwner(String jti) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT username FROM sessions WHERE jti = ?")) {
            ps.setString(1, jti);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("username");
            }
        } catch (SQLException e) {
            logger.warning("Failed to read session owner: " + e.getMessage());
        }
        return null;
    }

    public void revokeSession(String jti) {
        try { exec("UPDATE sessions SET revoked = 1 WHERE jti = ?", jti); }
        catch (SQLException e) { logger.warning("Failed to revoke session: " + e.getMessage()); }
    }

    /** Revoke every session for a user, optionally keeping one (e.g. the caller's current session). */
    public int revokeAllForUser(String username, String exceptJti) {
        String sql = exceptJti != null
                ? "UPDATE sessions SET revoked = 1 WHERE username = ? AND revoked = 0 AND jti <> ?"
                : "UPDATE sessions SET revoked = 1 WHERE username = ? AND revoked = 0";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            if (exceptJti != null) ps.setString(2, exceptJti);
            return ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to revoke sessions: " + e.getMessage());
        }
        return 0;
    }

    /** Drop revoked or expired sessions to keep the table small. */
    public void pruneSessions() {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM sessions WHERE revoked = 1 OR expires_at < ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to prune sessions: " + e.getMessage());
        }
    }

    // ── Two-factor authentication ─────────────────────────────────────────────────

    public String getTotpSecret(String username) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT totp_secret FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("totp_secret");
            }
        } catch (SQLException e) {
            logger.warning("Failed to read totp secret: " + e.getMessage());
        }
        return null;
    }

    public boolean isTotpEnabled(String username) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT totp_enabled FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) != 0;
            }
        } catch (SQLException e) {
            logger.warning("Failed to read totp_enabled: " + e.getMessage());
        }
        return false;
    }

    public void setTotpSecret(String username, String secret) {
        try { exec("UPDATE users SET totp_secret = ? WHERE username = ?", secret, username); }
        catch (SQLException e) { logger.warning("Failed to set totp secret: " + e.getMessage()); }
    }

    public void setTotpEnabled(String username, boolean enabled) {
        try {
            exec("UPDATE users SET totp_enabled = " + (enabled ? 1 : 0) + " WHERE username = ?", username);
        } catch (SQLException e) {
            logger.warning("Failed to set totp_enabled: " + e.getMessage());
        }
    }

    /** Fully clear 2FA for a user (secret, flag, and any recovery codes). */
    public void clearTotp(String username) {
        try {
            exec("UPDATE users SET totp_secret = NULL, totp_enabled = 0 WHERE username = ?", username);
            exec("DELETE FROM totp_recovery WHERE username = ?", username);
        } catch (SQLException e) {
            logger.warning("Failed to clear totp: " + e.getMessage());
        }
    }

    public void replaceRecoveryCodes(String username, List<String> hashes) {
        try {
            exec("DELETE FROM totp_recovery WHERE username = ?", username);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO totp_recovery (username, code_hash) VALUES (?, ?)")) {
                for (String hash : hashes) {
                    ps.setString(1, username);
                    ps.setString(2, hash);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            logger.warning("Failed to store recovery codes: " + e.getMessage());
        }
    }

    public int countRecoveryCodes(String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM totp_recovery WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.warning("Failed to count recovery codes: " + e.getMessage());
        }
        return 0;
    }

    public record RecoveryCode(long id, String hash) {}

    public List<RecoveryCode> listRecoveryCodes(String username) {
        List<RecoveryCode> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, code_hash FROM totp_recovery WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new RecoveryCode(rs.getLong("id"), rs.getString("code_hash")));
            }
        } catch (SQLException e) {
            logger.warning("Failed to list recovery codes: " + e.getMessage());
        }
        return result;
    }

    public void deleteRecoveryCode(long id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM totp_recovery WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to delete recovery code: " + e.getMessage());
        }
    }

    // ── Scheduled tasks ───────────────────────────────────────────────────────────

    public long insertTask(String name, String type, String payload, int countdownSeconds,
                           String scheduleType, long nextRun, long intervalMs, boolean enabled, String createdBy) {
        String sql = """
            INSERT INTO scheduled_tasks (name, type, payload, countdown_seconds, schedule_type, next_run, interval_ms, enabled, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, type);
            ps.setString(3, payload);
            ps.setInt(4, countdownSeconds);
            ps.setString(5, scheduleType);
            ps.setLong(6, nextRun);
            ps.setLong(7, intervalMs);
            ps.setInt(8, enabled ? 1 : 0);
            ps.setString(9, createdBy);
            ps.setLong(10, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.warning("Failed to insert scheduled task: " + e.getMessage());
        }
        return -1;
    }

    public void updateTask(long id, String name, String type, String payload, int countdownSeconds,
                           String scheduleType, long nextRun, long intervalMs, boolean enabled) {
        String sql = """
            UPDATE scheduled_tasks SET name = ?, type = ?, payload = ?, countdown_seconds = ?,
                schedule_type = ?, next_run = ?, interval_ms = ?, enabled = ? WHERE id = ?
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, type);
            ps.setString(3, payload);
            ps.setInt(4, countdownSeconds);
            ps.setString(5, scheduleType);
            ps.setLong(6, nextRun);
            ps.setLong(7, intervalMs);
            ps.setInt(8, enabled ? 1 : 0);
            ps.setLong(9, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to update scheduled task: " + e.getMessage());
        }
    }

    public void setTaskEnabled(long id, boolean enabled) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE scheduled_tasks SET enabled = ? WHERE id = ?")) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to toggle scheduled task: " + e.getMessage());
        }
    }

    /** Record an execution: stamps last_run/last_result and advances or disables the task. */
    public void recordTaskRun(long id, long lastRun, String lastResult, long nextRun, boolean enabled) {
        String sql = "UPDATE scheduled_tasks SET last_run = ?, last_result = ?, next_run = ?, enabled = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, lastRun);
            ps.setString(2, lastResult);
            ps.setLong(3, nextRun);
            ps.setInt(4, enabled ? 1 : 0);
            ps.setLong(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to record task run: " + e.getMessage());
        }
    }

    public void deleteTask(long id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM scheduled_tasks WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to delete scheduled task: " + e.getMessage());
        }
    }

    public Map<String, Object> getTask(long id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM scheduled_tasks WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return taskRow(rs);
            }
        } catch (SQLException e) {
            logger.warning("Failed to get scheduled task: " + e.getMessage());
        }
        return null;
    }

    public List<Map<String, Object>> listTasks() {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM scheduled_tasks ORDER BY next_run ASC")) {
            while (rs.next()) results.add(taskRow(rs));
        } catch (SQLException e) {
            logger.warning("Failed to list scheduled tasks: " + e.getMessage());
        }
        return results;
    }

    private Map<String, Object> taskRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("name", rs.getString("name"));
        row.put("type", rs.getString("type"));
        row.put("payload", rs.getString("payload"));
        row.put("countdownSeconds", rs.getInt("countdown_seconds"));
        row.put("scheduleType", rs.getString("schedule_type"));
        row.put("nextRun", rs.getLong("next_run"));
        row.put("intervalMs", rs.getLong("interval_ms"));
        row.put("enabled", rs.getInt("enabled") != 0);
        row.put("createdBy", rs.getString("created_by"));
        row.put("createdAt", rs.getLong("created_at"));
        row.put("lastRun", rs.getLong("last_run"));
        row.put("lastResult", rs.getString("last_result"));
        return row;
    }

    // ── Settings (key/value) ──────────────────────────────────────────────────────

    public String getSetting(String key, String fallback) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString("value");
                    return v != null ? v : fallback;
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to read setting: " + e.getMessage());
        }
        return fallback;
    }

    public void setSetting(String key, String value) {
        String sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to write setting: " + e.getMessage());
        }
    }

    public void deleteSetting(String key) {
        try { exec("DELETE FROM settings WHERE key = ?", key); }
        catch (SQLException e) { logger.warning("Failed to delete setting: " + e.getMessage()); }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            logger.warning("Failed to close database: " + e.getMessage());
        }
    }
}
