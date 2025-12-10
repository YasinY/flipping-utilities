package com.flippingutilities.db;

import net.runelite.client.RuneLite;

import java.io.File;

public final class DatabaseConstants {

    public static final File DATABASE_DIRECTORY = new File(RuneLite.RUNELITE_DIR, "flipping");
    public static final String DATABASE_FILE_NAME = "flipping.db";
    public static final File DATABASE_FILE = new File(DATABASE_DIRECTORY, DATABASE_FILE_NAME);
    public static final String JDBC_URL = "jdbc:sqlite:" + DATABASE_FILE.getAbsolutePath();

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private DatabaseConstants() {
    }
}
