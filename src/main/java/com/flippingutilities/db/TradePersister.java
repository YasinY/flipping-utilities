package com.flippingutilities.db;

import com.flippingutilities.model.AccountData;
import com.flippingutilities.model.AccountWideData;
import com.flippingutilities.model.FlippingItem;
import com.flippingutilities.model.OfferEvent;
import com.flippingutilities.model.BackupCheckpoints;
import com.flippingutilities.model.RecipeFlipGroup;
import com.flippingutilities.ui.uiutilities.TimeFormatters;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class TradePersister {

	public static final File PARENT_DIRECTORY = new File(RuneLite.RUNELITE_DIR, "flipping");
	public static final File OLD_FILE = new File(PARENT_DIRECTORY, "trades.json");

	private final Gson gson;
	private final DatabaseConnectionManager connectionManager;
	private final DatabaseMigrator databaseMigrator;
	private final OfferEventRepository offerEventRepository;
	private final FlippingItemRepository flippingItemRepository;
	private final RecipeFlipRepository recipeFlipRepository;
	private final AccountRepository accountRepository;
	private final AccountWideDataRepository accountWideDataRepository;
	private final JsonToSqliteMigrator jsonToSqliteMigrator;

	/**
	 * Simple constructor for backward compatibility with FlippingPlugin.
	 * Creates all dependencies internally.
	 */
	public TradePersister(Gson gson) {
		this.gson = gson;
		this.connectionManager = new DatabaseConnectionManager();
		this.databaseMigrator = new DatabaseMigrator(connectionManager);
		this.offerEventRepository = new OfferEventRepository(connectionManager);
		this.flippingItemRepository = new FlippingItemRepository(connectionManager, offerEventRepository);
		this.recipeFlipRepository = new RecipeFlipRepository(connectionManager, offerEventRepository, gson);
		this.accountRepository = new AccountRepository(connectionManager, flippingItemRepository, recipeFlipRepository,
				offerEventRepository);
		this.accountWideDataRepository = new AccountWideDataRepository(connectionManager, gson);
		this.jsonToSqliteMigrator = new JsonToSqliteMigrator(
				gson, connectionManager, accountRepository, flippingItemRepository, recipeFlipRepository,
				accountWideDataRepository);
	}

	@Inject
	public TradePersister(
			Gson gson,
			DatabaseConnectionManager connectionManager,
			DatabaseMigrator databaseMigrator,
			OfferEventRepository offerEventRepository,
			FlippingItemRepository flippingItemRepository,
			RecipeFlipRepository recipeFlipRepository,
			AccountRepository accountRepository,
			AccountWideDataRepository accountWideDataRepository) {
		this.gson = gson;
		this.connectionManager = connectionManager;
		this.databaseMigrator = databaseMigrator;
		this.offerEventRepository = offerEventRepository;
		this.flippingItemRepository = flippingItemRepository;
		this.recipeFlipRepository = recipeFlipRepository;
		this.accountRepository = accountRepository;
		this.accountWideDataRepository = accountWideDataRepository;
		this.jsonToSqliteMigrator = new JsonToSqliteMigrator(
				gson, connectionManager, accountRepository, flippingItemRepository, recipeFlipRepository,
				accountWideDataRepository);
	}

	public void setupFlippingFolder() throws IOException {
		if (!PARENT_DIRECTORY.exists()) {
			log.debug("flipping directory doesn't exist yet so it's being created");
			if (!PARENT_DIRECTORY.mkdir()) {
				throw new IOException("unable to create parent directory!");
			}
		} else {
			log.debug("flipping directory already exists so it's not being created");
			if (OLD_FILE.exists()) {
				OLD_FILE.delete();
			}
		}

		try {
			databaseMigrator.migrate();
			if (jsonToSqliteMigrator.needsMigration()) {
				jsonToSqliteMigrator.migrate();
			}
		} catch (SQLException e) {
			log.error("Failed to initialize database", e);
			throw new IOException("Failed to initialize SQLite database", e);
		}
	}

	public Map<String, AccountData> loadAllAccounts() {
		try {
			return accountRepository.findAll();
		} catch (SQLException e) {
			log.error("Failed to load accounts from database", e);
			return new HashMap<>();
		}
	}

	public AccountData loadAccount(String displayName) {
		log.debug("loading data for {}", displayName);
		try {
			AccountData data = accountRepository.findByName(displayName);
			if (data == null) {
				log.debug("No data found for {}, returning new AccountData", displayName);
				return new AccountData();
			}
			return data;
		} catch (SQLException e) {
			log.error("Failed to load account data for {}", displayName, e);
			return new AccountData();
		}
	}

	public AccountWideData loadAccountWideData() throws IOException {
		try {
			AccountWideData data = accountWideDataRepository.load();
			return data != null ? data : new AccountWideData();
		} catch (SQLException e) {
			log.error("Failed to load account wide data", e);
			return new AccountWideData();
		}
	}

	public BackupCheckpoints fetchBackupCheckpoints() {
		return new BackupCheckpoints();
	}

	public void writeToFile(String displayName, Object data) throws IOException {
		log.debug("Writing to database for {}", displayName);
		try {
			if (data instanceof AccountData) {
				saveAccountData(displayName, (AccountData) data);
			} else if (data instanceof AccountWideData) {
				accountWideDataRepository.save((AccountWideData) data);
			} else {
				log.warn("Unknown data type for writeToFile: {}", data.getClass().getName());
			}
		} catch (SQLException e) {
			log.error("Failed to write data for {}", displayName, e);
			throw new IOException("Failed to write to database", e);
		}
	}

	private void saveAccountData(String displayName, AccountData data) throws SQLException {
		data.setLastStoredAt(Instant.now());
		accountRepository.insertOrUpdate(displayName, data);

		for (FlippingItem item : data.getTrades()) {
			Long existingId = flippingItemRepository.findIdByAccountAndItemId(displayName, item.getItemId());
			if (existingId != null) {
				flippingItemRepository.update(item, existingId);
				offerEventRepository.deleteByFlippingItemId(existingId);
				offerEventRepository.insertAll(item.getHistory().getCompressedOfferEvents(), existingId);
			} else {
				flippingItemRepository.insertWithOffers(item, displayName);
			}
		}

		if (data.getLastOffers() != null && !data.getLastOffers().isEmpty()) {
			accountRepository.saveLastOffers(displayName, data.getLastOffers());
		}
	}

	public static long lastModified(String fileName) {
		if (fileName.endsWith(".json")) {
			return new File(PARENT_DIRECTORY, fileName).lastModified();
		}
		return DatabaseConstants.DATABASE_FILE.lastModified();
	}

	public static void deleteFile(String fileName) {
		File accountFile = new File(PARENT_DIRECTORY, fileName);
		if (accountFile.exists()) {
			if (accountFile.delete()) {
				log.debug("{} deleted", fileName);
			} else {
				log.debug("unable to delete {}", fileName);
			}
		}
	}

	public static void exportToCsv(File file, List<FlippingItem> trades, String startOfIntervalName)
			throws IOException {
		FileWriter out = new FileWriter(file);
		CSVPrinter csvWriter = new CSVPrinter(out,
				CSVFormat.DEFAULT.withHeader("name", "date", "quantity", "price", "state").withCommentMarker('#')
						.withHeaderComments("Displaying trades for selected time interval: " + startOfIntervalName));

		for (FlippingItem item : trades) {
			for (OfferEvent offer : item.getHistory().getCompressedOfferEvents()) {
				csvWriter.printRecord(
						item.getItemName(),
						TimeFormatters.formatInstantToDate(offer.getTime()),
						offer.getCurrentQuantityInTrade(),
						offer.getPrice(),
						offer.getState());
			}
			csvWriter.printComment(String.format("Total profit: %d",
					FlippingItem.getProfit(item.getHistory().getCompressedOfferEvents())));
			csvWriter.println();
		}
		csvWriter.close();
	}

	public void close() {
		connectionManager.close();
	}
}
