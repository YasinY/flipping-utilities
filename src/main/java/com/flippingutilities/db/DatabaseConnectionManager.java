package com.flippingutilities.db;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Singleton
public class DatabaseConnectionManager {

    private Connection connection;

    @Inject
    public DatabaseConnectionManager() {
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            ensureDirectoryExists();
            connection = DriverManager.getConnection(DatabaseConstants.JDBC_URL);
            connection.setAutoCommit(true);
            enableForeignKeys(connection);
        }
        return connection;
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
            connection = null;
            log.debug("Database connection closed");
        } catch (SQLException e) {
            log.error("Failed to close database connection", e);
        }
    }

    private void ensureDirectoryExists() {
        if (DatabaseConstants.DATABASE_DIRECTORY.exists()) {
            return;
        }
        boolean created = DatabaseConstants.DATABASE_DIRECTORY.mkdirs();
        if (!created) {
            log.error("Failed to create database directory: {}", DatabaseConstants.DATABASE_DIRECTORY);
        }
    }

    private void enableForeignKeys(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
    }

    public boolean isDatabaseInitialized() {
        return DatabaseConstants.DATABASE_FILE.exists();
    }
}
