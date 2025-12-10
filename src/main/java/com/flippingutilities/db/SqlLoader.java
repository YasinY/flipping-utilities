package com.flippingutilities.db;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class SqlLoader {

    private static final String SQL_RESOURCE_PATH = "/db/";

    private SqlLoader() {
    }

    public static String load(String fileName) {
        String path = SQL_RESOURCE_PATH + fileName;
        try (InputStream is = SqlLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("SQL file not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch (IOException e) {
            log.error("Failed to load SQL file: {}", path, e);
            throw new RuntimeException("Failed to load SQL file: " + path, e);
        }
    }

    public static String[] loadStatements(String fileName) {
        String content = load(fileName);
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();

        for (String line : content.split("\n")) {
            String trimmed = line.trim();

            // skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }

            currentStatement.append(line).append(" ");

            // check if statement ends with semicolon
            if (trimmed.endsWith(";")) {
                String stmt = currentStatement.toString().trim();
                // remove trailing semicolon for execution
                if (stmt.endsWith(";")) {
                    stmt = stmt.substring(0, stmt.length() - 1).trim();
                }
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                currentStatement = new StringBuilder();
            }
        }

        return statements.toArray(new String[0]);
    }
}
