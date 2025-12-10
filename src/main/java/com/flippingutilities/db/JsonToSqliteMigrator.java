package com.flippingutilities.db;

import com.flippingutilities.model.AccountData;
import com.flippingutilities.model.AccountWideData;
import com.flippingutilities.model.FlippingItem;
import com.flippingutilities.model.RecipeFlipGroup;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;

@Slf4j
@Singleton
public class JsonToSqliteMigrator {

    private static final String JSON_EXTENSION = ".json";
    private static final String BACKUP_SUFFIX = ".backup.json";
    private static final String SPECIAL_SUFFIX = ".special.json";
    private static final String ACCOUNT_WIDE_FILE = "accountwide.json";
    private static final String MIGRATION_MARKER = ".sqlite_migrated";

    private final Gson gson;
    private final DatabaseConnectionManager connectionManager;
    private final AccountRepository accountRepository;
    private final FlippingItemRepository flippingItemRepository;
    private final RecipeFlipRepository recipeFlipRepository;
    private final AccountWideDataRepository accountWideDataRepository;

    @Inject
    public JsonToSqliteMigrator(
            Gson gson,
            DatabaseConnectionManager connectionManager,
            AccountRepository accountRepository,
            FlippingItemRepository flippingItemRepository,
            RecipeFlipRepository recipeFlipRepository,
            AccountWideDataRepository accountWideDataRepository) {
        this.gson = gson;
        this.connectionManager = connectionManager;
        this.accountRepository = accountRepository;
        this.flippingItemRepository = flippingItemRepository;
        this.recipeFlipRepository = recipeFlipRepository;
        this.accountWideDataRepository = accountWideDataRepository;
    }

    public boolean needsMigration() {
        File migrationMarker = new File(TradePersister.PARENT_DIRECTORY, MIGRATION_MARKER);
        if (migrationMarker.exists()) {
            return false;
        }
        return hasJsonFilesToMigrate();
    }

    private boolean hasJsonFilesToMigrate() {
        File[] files = TradePersister.PARENT_DIRECTORY.listFiles();
        if (files == null) {
            return false;
        }
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(JSON_EXTENSION) && !name.endsWith(BACKUP_SUFFIX) && !name.endsWith(SPECIAL_SUFFIX)) {
                return true;
            }
        }
        return false;
    }

    public void migrate() throws IOException, SQLException {
        log.info("Starting JSON to SQLite migration");
        File[] files = TradePersister.PARENT_DIRECTORY.listFiles();
        if (files == null) {
            log.warn("No files found in flipping directory");
            return;
        }

        for (File f : files) {
            String name = f.getName();
            if (!name.endsWith(JSON_EXTENSION)) {
                continue;
            }
            if (name.endsWith(BACKUP_SUFFIX) || name.endsWith(SPECIAL_SUFFIX)) {
                continue;
            }
            if (name.equals(ACCOUNT_WIDE_FILE)) {
                migrateAccountWideData(f);
                continue;
            }
            migrateAccountData(f);
        }

        markMigrationComplete();
        log.info("JSON to SQLite migration completed");
    }

    private void migrateAccountData(File jsonFile) throws IOException, SQLException {
        String displayName = jsonFile.getName().replace(JSON_EXTENSION, "");
        log.debug("Migrating account data for: {}", displayName);

        String json = new String(Files.readAllBytes(jsonFile.toPath()));
        AccountData accountData = gson.fromJson(json, AccountData.class);

        if (accountData == null) {
            log.warn("Could not parse account data from: {}", jsonFile.getName());
            return;
        }

        accountRepository.insert(displayName, accountData);

        for (FlippingItem item : accountData.getTrades()) {
            flippingItemRepository.insertWithOffers(item, displayName);
        }

        for (RecipeFlipGroup group : accountData.getRecipeFlipGroups()) {
            recipeFlipRepository.insertGroup(group, displayName);
        }

        if (accountData.getLastOffers() != null && !accountData.getLastOffers().isEmpty()) {
            accountRepository.saveLastOffers(displayName, accountData.getLastOffers());
        }

        createJsonBackup(jsonFile);
    }

    private void migrateAccountWideData(File jsonFile) throws IOException, SQLException {
        log.debug("Migrating account wide data");

        String json = new String(Files.readAllBytes(jsonFile.toPath()));
        AccountWideData data = gson.fromJson(json, AccountWideData.class);

        if (data == null) {
            log.warn("Could not parse account wide data from: {}", jsonFile.getName());
            return;
        }

        accountWideDataRepository.save(data);
        createJsonBackup(jsonFile);
    }

    private void createJsonBackup(File file) throws IOException {
        String backupName = file.getName().replace(JSON_EXTENSION, ".pre_sqlite" + BACKUP_SUFFIX);
        File backupFile = new File(file.getParent(), backupName);
        Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.debug("Created backup: {}", backupFile.getName());
    }

    private void markMigrationComplete() throws IOException {
        File marker = new File(TradePersister.PARENT_DIRECTORY, MIGRATION_MARKER);
        if (!marker.createNewFile()) {
            log.warn("Could not create migration marker file");
        }
    }
}
