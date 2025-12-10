package com.flippingutilities.db;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

@Slf4j
@Singleton
public class DatabaseMigrator {

    private static final String SCHEMA_FILE_PREFIX = "V";
    private static final String SCHEMA_FILE_SUFFIX = "__";
    private static final String CREATE_SCHEMA_VERSION_TABLE = "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)";
    private static final String SELECT_MAX_VERSION = "SELECT MAX(version) as version FROM schema_version";
    private static final String INSERT_VERSION = "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)";

    private final DatabaseConnectionManager connectionManager;

    @Inject
    public DatabaseMigrator(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void migrate() throws SQLException {
        Connection conn = connectionManager.getConnection();
        int currentVersion = getCurrentSchemaVersion(conn);

        if (currentVersion < DatabaseConstants.CURRENT_SCHEMA_VERSION) {
            log.info("Migrating database from version {} to {}", currentVersion,
                    DatabaseConstants.CURRENT_SCHEMA_VERSION);
            applyMigrations(conn, currentVersion);
        }
    }

    private int getCurrentSchemaVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_SCHEMA_VERSION_TABLE);
        }

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(SELECT_MAX_VERSION)) {
            if (rs.next()) {
                int version = rs.getInt("version");
                return rs.wasNull() ? 0 : version;
            }
        }
        return 0;
    }

    private void applyMigrations(Connection conn, int fromVersion) throws SQLException {
        conn.setAutoCommit(false);
        try {
            for (int version = fromVersion + 1; version <= DatabaseConstants.CURRENT_SCHEMA_VERSION; version++) {
                applyVersion(conn, version);
            }
            conn.commit();
            log.info("Database migration completed successfully");
        } catch (SQLException e) {
            conn.rollback();
            log.error("Database migration failed, rolled back changes", e);
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private void applyVersion(Connection conn, int version) throws SQLException {
        log.debug("Applying schema version {}", version);
        String fileName = findMigrationFile(version);
        String[] statements = SqlLoader.loadStatements(fileName);

        try (Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    stmt.execute(trimmed);
                }
            }
        }

        recordSchemaVersion(conn, version);
    }

    private String findMigrationFile(int version) {
        return SCHEMA_FILE_PREFIX + version + SCHEMA_FILE_SUFFIX + "initial_schema.sql";
    }

    private void recordSchemaVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_VERSION)) {
            stmt.setInt(1, version);
            stmt.setString(2, Instant.now().toString());
            stmt.executeUpdate();
        }
    }
}
