package com.flippingutilities.db;

import com.flippingutilities.model.OfferEvent;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOfferState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Singleton
public class OfferEventRepository {

    private static final String INSERT_SQL = "INSERT INTO offer_event (uuid, flipping_item_id, is_buy, item_id, current_quantity_in_trade, "
            +
            "price, time, slot, state, tick_arrived_at, ticks_since_first_offer, total_quantity_in_trade, " +
            "trade_started_at, before_login) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL = "UPDATE offer_event SET is_buy = ?, item_id = ?, current_quantity_in_trade = ?, price = ?, "
            +
            "time = ?, slot = ?, state = ?, tick_arrived_at = ?, ticks_since_first_offer = ?, " +
            "total_quantity_in_trade = ?, trade_started_at = ?, before_login = ? WHERE uuid = ?";

    private static final String SELECT_BY_FLIPPING_ITEM_SQL = "SELECT * FROM offer_event WHERE flipping_item_id = ? ORDER BY time ASC";

    private static final String SELECT_BY_UUID_SQL = "SELECT * FROM offer_event WHERE uuid = ?";

    private static final String DELETE_BY_UUID_SQL = "DELETE FROM offer_event WHERE uuid = ?";

    private static final String DELETE_BY_FLIPPING_ITEM_SQL = "DELETE FROM offer_event WHERE flipping_item_id = ?";

    private final DatabaseConnectionManager connectionManager;

    @Inject
    public OfferEventRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void insert(OfferEvent offer, long flippingItemId) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
            stmt.setString(1, offer.getUuid());
            stmt.setLong(2, flippingItemId);
            stmt.setInt(3, offer.isBuy() ? 1 : 0);
            stmt.setInt(4, offer.getItemId());
            stmt.setInt(5, offer.getCurrentQuantityInTrade());
            stmt.setInt(6, offer.getPreTaxPrice());
            stmt.setString(7, offer.getTime().toString());
            stmt.setInt(8, offer.getSlot());
            stmt.setString(9, offer.getState().name());
            stmt.setInt(10, offer.getTickArrivedAt());
            stmt.setInt(11, offer.getTicksSinceFirstOffer());
            stmt.setInt(12, offer.getTotalQuantityInTrade());
            setNullableInstant(stmt, 13, offer.getTradeStartedAt());
            stmt.setInt(14, offer.isBeforeLogin() ? 1 : 0);
            stmt.executeUpdate();
        }
    }

    public void insertAll(List<OfferEvent> offers, long flippingItemId) throws SQLException {
        Connection conn = connectionManager.getConnection();
        conn.setAutoCommit(false);
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
            for (OfferEvent offer : offers) {
                stmt.setString(1, offer.getUuid());
                stmt.setLong(2, flippingItemId);
                stmt.setInt(3, offer.isBuy() ? 1 : 0);
                stmt.setInt(4, offer.getItemId());
                stmt.setInt(5, offer.getCurrentQuantityInTrade());
                stmt.setInt(6, offer.getPreTaxPrice());
                stmt.setString(7, offer.getTime().toString());
                stmt.setInt(8, offer.getSlot());
                stmt.setString(9, offer.getState().name());
                stmt.setInt(10, offer.getTickArrivedAt());
                stmt.setInt(11, offer.getTicksSinceFirstOffer());
                stmt.setInt(12, offer.getTotalQuantityInTrade());
                setNullableInstant(stmt, 13, offer.getTradeStartedAt());
                stmt.setInt(14, offer.isBeforeLogin() ? 1 : 0);
                stmt.addBatch();
            }
            stmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public void update(OfferEvent offer) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
            stmt.setInt(1, offer.isBuy() ? 1 : 0);
            stmt.setInt(2, offer.getItemId());
            stmt.setInt(3, offer.getCurrentQuantityInTrade());
            stmt.setInt(4, offer.getPreTaxPrice());
            stmt.setString(5, offer.getTime().toString());
            stmt.setInt(6, offer.getSlot());
            stmt.setString(7, offer.getState().name());
            stmt.setInt(8, offer.getTickArrivedAt());
            stmt.setInt(9, offer.getTicksSinceFirstOffer());
            stmt.setInt(10, offer.getTotalQuantityInTrade());
            setNullableInstant(stmt, 11, offer.getTradeStartedAt());
            stmt.setInt(12, offer.isBeforeLogin() ? 1 : 0);
            stmt.setString(13, offer.getUuid());
            stmt.executeUpdate();
        }
    }

    public List<OfferEvent> findByFlippingItemId(long flippingItemId) throws SQLException {
        List<OfferEvent> offers = new ArrayList<>();
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_BY_FLIPPING_ITEM_SQL)) {
            stmt.setLong(1, flippingItemId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    offers.add(mapResultSetToOfferEvent(rs));
                }
            }
        }
        return offers;
    }

    public OfferEvent findByUuid(String uuid) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_BY_UUID_SQL)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToOfferEvent(rs);
                }
            }
        }
        return null;
    }

    public void deleteByUuid(String uuid) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(DELETE_BY_UUID_SQL)) {
            stmt.setString(1, uuid);
            stmt.executeUpdate();
        }
    }

    public void deleteByFlippingItemId(long flippingItemId) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(DELETE_BY_FLIPPING_ITEM_SQL)) {
            stmt.setLong(1, flippingItemId);
            stmt.executeUpdate();
        }
    }

    private OfferEvent mapResultSetToOfferEvent(ResultSet rs) throws SQLException {
        String tradeStartedAtStr = rs.getString("trade_started_at");
        Instant tradeStartedAt = tradeStartedAtStr != null ? Instant.parse(tradeStartedAtStr) : null;

        return new OfferEvent(
                rs.getString("uuid"),
                rs.getInt("is_buy") == 1,
                rs.getInt("item_id"),
                rs.getInt("current_quantity_in_trade"),
                rs.getInt("price"),
                Instant.parse(rs.getString("time")),
                rs.getInt("slot"),
                GrandExchangeOfferState.valueOf(rs.getString("state")),
                rs.getInt("tick_arrived_at"),
                rs.getInt("ticks_since_first_offer"),
                rs.getInt("total_quantity_in_trade"),
                tradeStartedAt,
                rs.getInt("before_login") == 1,
                null,
                null,
                0,
                0);
    }

    private void setNullableInstant(PreparedStatement stmt, int index, Instant value) throws SQLException {
        if (value != null) {
            stmt.setString(index, value.toString());
        } else {
            stmt.setNull(index, java.sql.Types.VARCHAR);
        }
    }
}
