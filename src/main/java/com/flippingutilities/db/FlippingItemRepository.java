package com.flippingutilities.db;

import com.flippingutilities.model.FlippingItem;
import com.flippingutilities.model.HistoryManager;
import com.flippingutilities.model.OfferEvent;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Singleton
public class FlippingItemRepository {

    private static final String INSERT_SQL = "INSERT OR REPLACE INTO flipping_item (account_name, item_id, item_name, total_ge_limit, flipped_by, "
            +
            "valid_flipping_panel_item, favorite, favorite_code, next_ge_limit_refresh, " +
            "items_bought_this_limit_window, items_bought_through_complete_offers) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL = "UPDATE flipping_item SET item_name = ?, total_ge_limit = ?, flipped_by = ?, "
            +
            "valid_flipping_panel_item = ?, favorite = ?, favorite_code = ?, next_ge_limit_refresh = ?, " +
            "items_bought_this_limit_window = ?, items_bought_through_complete_offers = ? WHERE id = ?";

    private static final String SELECT_BY_ACCOUNT_SQL = "SELECT * FROM flipping_item WHERE account_name = ?";

    private static final String SELECT_BY_ACCOUNT_AND_ITEM_ID_SQL = "SELECT * FROM flipping_item WHERE account_name = ? AND item_id = ?";

    private static final String DELETE_BY_ID_SQL = "DELETE FROM flipping_item WHERE id = ?";

    private static final String LAST_INSERT_ID_SQL = "SELECT last_insert_rowid()";

    private final DatabaseConnectionManager connectionManager;
    private final OfferEventRepository offerEventRepository;

    @Inject
    public FlippingItemRepository(DatabaseConnectionManager connectionManager,
            OfferEventRepository offerEventRepository) {
        this.connectionManager = connectionManager;
        this.offerEventRepository = offerEventRepository;
    }

    public long insert(FlippingItem item, String accountName) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
            stmt.setString(1, accountName);
            stmt.setInt(2, item.getItemId());
            stmt.setString(3, item.getItemName());
            stmt.setInt(4, item.getTotalGELimit());
            stmt.setString(5, item.getFlippedBy());
            stmt.setInt(6, Boolean.TRUE.equals(item.getValidFlippingPanelItem()) ? 1 : 0);
            stmt.setInt(7, item.isFavorite() ? 1 : 0);
            stmt.setString(8, item.getFavoriteCode());
            setNullableInstant(stmt, 9, item.getGeLimitResetTime());
            stmt.setInt(10, item.getItemsBoughtThisLimitWindow());
            stmt.setInt(11, 0);
            stmt.executeUpdate();
        }

        return getLastInsertId(conn);
    }

    private long getLastInsertId(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(LAST_INSERT_ID_SQL)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("Failed to get last insert id");
    }

    public long insertWithOffers(FlippingItem item, String accountName) throws SQLException {
        long itemId = insert(item, accountName);
        List<OfferEvent> offers = item.getHistory().getCompressedOfferEvents();
        if (!offers.isEmpty()) {
            offerEventRepository.insertAll(offers, itemId);
        }
        return itemId;
    }

    public void update(FlippingItem item, long id) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
            stmt.setString(1, item.getItemName());
            stmt.setInt(2, item.getTotalGELimit());
            stmt.setString(3, item.getFlippedBy());
            stmt.setInt(4, Boolean.TRUE.equals(item.getValidFlippingPanelItem()) ? 1 : 0);
            stmt.setInt(5, item.isFavorite() ? 1 : 0);
            stmt.setString(6, item.getFavoriteCode());
            setNullableInstant(stmt, 7, item.getGeLimitResetTime());
            stmt.setInt(8, item.getItemsBoughtThisLimitWindow());
            stmt.setInt(9, 0);
            stmt.setLong(10, id);
            stmt.executeUpdate();
        }
    }

    public List<FlippingItem> findByAccountName(String accountName) throws SQLException {
        List<FlippingItem> items = new ArrayList<>();
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ACCOUNT_SQL)) {
            stmt.setString(1, accountName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    items.add(mapResultSetToFlippingItem(rs));
                }
            }
        }
        return items;
    }

    public List<FlippingItem> findByAccountNameWithOffers(String accountName) throws SQLException {
        List<FlippingItem> items = findByAccountName(accountName);
        for (FlippingItem item : items) {
            Long itemId = findIdByAccountAndItemId(accountName, item.getItemId());
            if (itemId != null) {
                List<OfferEvent> offers = offerEventRepository.findByFlippingItemId(itemId);
                item.getHistory().setCompressedOfferEvents(offers);
            }
        }
        return items;
    }

    public Long findIdByAccountAndItemId(String accountName, int itemId) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ACCOUNT_AND_ITEM_ID_SQL)) {
            stmt.setString(1, accountName);
            stmt.setInt(2, itemId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        return null;
    }

    public void deleteById(long id) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(DELETE_BY_ID_SQL)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    private FlippingItem mapResultSetToFlippingItem(ResultSet rs) throws SQLException {
        FlippingItem item = new FlippingItem(
                rs.getInt("item_id"),
                rs.getString("item_name"),
                rs.getInt("total_ge_limit"),
                rs.getString("flipped_by"));
        item.setValidFlippingPanelItem(rs.getInt("valid_flipping_panel_item") == 1);
        item.setFavorite(rs.getInt("favorite") == 1);
        item.setFavoriteCode(rs.getString("favorite_code"));
        item.setHistory(new HistoryManager());
        return item;
    }

    private void setNullableInstant(PreparedStatement stmt, int index, Instant value) throws SQLException {
        if (value != null) {
            stmt.setString(index, value.toString());
        } else {
            stmt.setNull(index, java.sql.Types.VARCHAR);
        }
    }
}
