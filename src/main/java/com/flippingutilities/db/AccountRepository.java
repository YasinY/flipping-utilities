package com.flippingutilities.db;

import com.flippingutilities.model.AccountData;
import com.flippingutilities.model.FlippingItem;
import com.flippingutilities.model.OfferEvent;
import com.flippingutilities.model.RecipeFlipGroup;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class AccountRepository {

    private static final String INSERT_SQL = "INSERT OR REPLACE INTO account (display_name, session_start_time, accumulated_session_time_millis, "
            +
            "last_session_time_update, last_stored_at, last_modified_at) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL = "UPDATE account SET session_start_time = ?, accumulated_session_time_millis = ?, "
            +
            "last_session_time_update = ?, last_stored_at = ?, last_modified_at = ? WHERE display_name = ?";

    private static final String SELECT_ALL_SQL = "SELECT * FROM account";

    private static final String SELECT_BY_NAME_SQL = "SELECT * FROM account WHERE display_name = ?";

    private static final String EXISTS_SQL = "SELECT 1 FROM account WHERE display_name = ?";

    private static final String DELETE_SQL = "DELETE FROM account WHERE display_name = ?";

    private static final String INSERT_LAST_OFFER_SQL = "INSERT OR REPLACE INTO last_offer (account_name, slot, offer_event_uuid) VALUES (?, ?, ?)";

    private static final String SELECT_LAST_OFFERS_SQL = "SELECT slot, offer_event_uuid FROM last_offer WHERE account_name = ?";

    private final DatabaseConnectionManager connectionManager;
    private final FlippingItemRepository flippingItemRepository;
    private final RecipeFlipRepository recipeFlipRepository;
    private final OfferEventRepository offerEventRepository;

    @Inject
    public AccountRepository(
            DatabaseConnectionManager connectionManager,
            FlippingItemRepository flippingItemRepository,
            RecipeFlipRepository recipeFlipRepository,
            OfferEventRepository offerEventRepository) {
        this.connectionManager = connectionManager;
        this.flippingItemRepository = flippingItemRepository;
        this.recipeFlipRepository = recipeFlipRepository;
        this.offerEventRepository = offerEventRepository;
    }

    public void insertOrUpdate(String displayName, AccountData data) throws SQLException {
        if (exists(displayName)) {
            update(displayName, data);
        } else {
            insert(displayName, data);
        }
    }

    public void insert(String displayName, AccountData data) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
            stmt.setString(1, displayName);
            setNullableInstant(stmt, 2, data.getSessionStartTime());
            stmt.setLong(3, data.getAccumulatedSessionTimeMillis());
            setNullableInstant(stmt, 4, data.getLastSessionTimeUpdate());
            setNullableInstant(stmt, 5, data.getLastStoredAt());
            setNullableInstant(stmt, 6, data.getLastModifiedAt());
            stmt.executeUpdate();
        }
    }

    public void update(String displayName, AccountData data) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
            setNullableInstant(stmt, 1, data.getSessionStartTime());
            stmt.setLong(2, data.getAccumulatedSessionTimeMillis());
            setNullableInstant(stmt, 3, data.getLastSessionTimeUpdate());
            setNullableInstant(stmt, 4, data.getLastStoredAt());
            setNullableInstant(stmt, 5, data.getLastModifiedAt());
            stmt.setString(6, displayName);
            stmt.executeUpdate();
        }
    }

    public boolean exists(String displayName) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(EXISTS_SQL)) {
            stmt.setString(1, displayName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<String> findAllAccountNames() throws SQLException {
        List<String> names = new ArrayList<>();
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("display_name"));
            }
        }
        return names;
    }

    public AccountData findByName(String displayName) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_BY_NAME_SQL)) {
            stmt.setString(1, displayName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAccountData(rs, displayName);
                }
            }
        }
        return null;
    }

    public Map<String, AccountData> findAll() throws SQLException {
        Map<String, AccountData> accounts = new HashMap<>();
        List<String> names = findAllAccountNames();
        for (String name : names) {
            AccountData data = findByName(name);
            if (data != null) {
                accounts.put(name, data);
            }
        }
        return accounts;
    }

    public void delete(String displayName) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
            stmt.setString(1, displayName);
            stmt.executeUpdate();
        }
    }

    public void saveLastOffers(String displayName, Map<Integer, OfferEvent> lastOffers) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_LAST_OFFER_SQL)) {
            for (Map.Entry<Integer, OfferEvent> entry : lastOffers.entrySet()) {
                stmt.setString(1, displayName);
                stmt.setInt(2, entry.getKey());
                stmt.setString(3, entry.getValue().getUuid());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public Map<Integer, OfferEvent> loadLastOffers(String displayName) throws SQLException {
        Map<Integer, OfferEvent> lastOffers = new HashMap<>();
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_LAST_OFFERS_SQL)) {
            stmt.setString(1, displayName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    String uuid = rs.getString("offer_event_uuid");
                    OfferEvent offer = offerEventRepository.findByUuid(uuid);
                    if (offer != null) {
                        lastOffers.put(slot, offer);
                    }
                }
            }
        }
        return lastOffers;
    }

    private AccountData mapResultSetToAccountData(ResultSet rs, String displayName) throws SQLException {
        AccountData data = new AccountData();

        String sessionStartStr = rs.getString("session_start_time");
        if (sessionStartStr != null) {
            data.setSessionStartTime(Instant.parse(sessionStartStr));
        }

        data.setAccumulatedSessionTimeMillis(rs.getLong("accumulated_session_time_millis"));

        String lastSessionUpdateStr = rs.getString("last_session_time_update");
        if (lastSessionUpdateStr != null) {
            data.setLastSessionTimeUpdate(Instant.parse(lastSessionUpdateStr));
        }

        String lastStoredStr = rs.getString("last_stored_at");
        if (lastStoredStr != null) {
            data.setLastStoredAt(Instant.parse(lastStoredStr));
        }

        String lastModifiedStr = rs.getString("last_modified_at");
        if (lastModifiedStr != null) {
            data.setLastModifiedAt(Instant.parse(lastModifiedStr));
        }

        List<FlippingItem> trades = flippingItemRepository.findByAccountNameWithOffers(displayName);
        data.setTrades(trades);

        Map<Integer, OfferEvent> lastOffers = loadLastOffers(displayName);
        data.setLastOffers(lastOffers);

        List<RecipeFlipGroup> recipeFlipGroups = recipeFlipRepository.findGroupsByAccountName(displayName);
        data.setRecipeFlipGroups(recipeFlipGroups);

        return data;
    }

    private void setNullableInstant(PreparedStatement stmt, int index, Instant value) throws SQLException {
        if (value != null) {
            stmt.setString(index, value.toString());
        } else {
            stmt.setNull(index, java.sql.Types.VARCHAR);
        }
    }
}
