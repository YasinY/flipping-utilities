package com.flippingutilities.db;

import com.flippingutilities.model.OfferEvent;
import com.flippingutilities.model.PartialOffer;
import com.flippingutilities.model.RecipeFlip;
import com.flippingutilities.model.RecipeFlipGroup;
import com.flippingutilities.utilities.Recipe;
import com.google.gson.Gson;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class RecipeFlipRepository {

    private static final String INSERT_GROUP_SQL = "INSERT OR REPLACE INTO recipe_flip_group (account_name, recipe_name, recipe_data) VALUES (?, ?, ?)";

    private static final String INSERT_FLIP_SQL = "INSERT INTO recipe_flip (recipe_flip_group_id, time_of_creation, coin_cost) VALUES (?, ?, ?)";

    private static final String INSERT_PARTIAL_OFFER_SQL = "INSERT INTO partial_offer (recipe_flip_id, offer_event_uuid, amount_consumed, is_input, item_id) "
            +
            "VALUES (?, ?, ?, ?, ?)";

    private static final String SELECT_GROUPS_BY_ACCOUNT_SQL = "SELECT * FROM recipe_flip_group WHERE account_name = ?";

    private static final String SELECT_FLIPS_BY_GROUP_SQL = "SELECT * FROM recipe_flip WHERE recipe_flip_group_id = ? ORDER BY time_of_creation ASC";

    private static final String SELECT_PARTIAL_OFFERS_BY_FLIP_SQL = "SELECT * FROM partial_offer WHERE recipe_flip_id = ?";

    private static final String DELETE_GROUP_SQL = "DELETE FROM recipe_flip_group WHERE id = ?";

    private static final String DELETE_FLIP_SQL = "DELETE FROM recipe_flip WHERE id = ?";

    private static final String LAST_INSERT_ID_SQL = "SELECT last_insert_rowid()";

    private final DatabaseConnectionManager connectionManager;
    private final OfferEventRepository offerEventRepository;
    private final Gson gson;

    @Inject
    public RecipeFlipRepository(DatabaseConnectionManager connectionManager, OfferEventRepository offerEventRepository,
            Gson gson) {
        this.connectionManager = connectionManager;
        this.offerEventRepository = offerEventRepository;
        this.gson = gson;
    }

    public long insertGroup(RecipeFlipGroup group, String accountName) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_GROUP_SQL)) {
            stmt.setString(1, accountName);
            stmt.setString(2, group.getRecipe().getName());
            stmt.setString(3, gson.toJson(group.getRecipe()));
            stmt.executeUpdate();
        }

        long groupId = getLastInsertId(conn);
        for (RecipeFlip flip : group.getRecipeFlips()) {
            insertFlip(flip, groupId);
        }
        return groupId;
    }

    public long insertFlip(RecipeFlip flip, long groupId) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_FLIP_SQL)) {
            stmt.setLong(1, groupId);
            stmt.setString(2, flip.getTimeOfCreation().toString());
            stmt.setLong(3, flip.getCoinCost());
            stmt.executeUpdate();
        }

        long flipId = getLastInsertId(conn);
        insertPartialOffers(flip.getInputs(), flipId, true);
        insertPartialOffers(flip.getOutputs(), flipId, false);
        return flipId;
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

    private void insertPartialOffers(Map<Integer, Map<String, PartialOffer>> offerMap, long flipId, boolean isInput)
            throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_PARTIAL_OFFER_SQL)) {
            for (Map.Entry<Integer, Map<String, PartialOffer>> itemEntry : offerMap.entrySet()) {
                int itemId = itemEntry.getKey();
                for (PartialOffer partial : itemEntry.getValue().values()) {
                    stmt.setLong(1, flipId);
                    stmt.setString(2, partial.getOffer().getUuid());
                    stmt.setInt(3, partial.getAmountConsumed());
                    stmt.setInt(4, isInput ? 1 : 0);
                    stmt.setInt(5, itemId);
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();
        }
    }

    public List<RecipeFlipGroup> findGroupsByAccountName(String accountName) throws SQLException {
        List<RecipeFlipGroup> groups = new ArrayList<>();
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_GROUPS_BY_ACCOUNT_SQL)) {
            stmt.setString(1, accountName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long groupId = rs.getLong("id");
                    String recipeData = rs.getString("recipe_data");
                    Recipe recipe = gson.fromJson(recipeData, Recipe.class);
                    RecipeFlipGroup group = new RecipeFlipGroup(recipe);
                    group.setRecipeFlips(findFlipsByGroupId(groupId));
                    groups.add(group);
                }
            }
        }
        return groups;
    }

    public List<RecipeFlip> findFlipsByGroupId(long groupId) throws SQLException {
        List<RecipeFlip> flips = new ArrayList<>();
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_FLIPS_BY_GROUP_SQL)) {
            stmt.setLong(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long flipId = rs.getLong("id");
                    Instant timeOfCreation = Instant.parse(rs.getString("time_of_creation"));
                    long coinCost = rs.getLong("coin_cost");

                    Map<Integer, Map<String, PartialOffer>> inputs = new HashMap<>();
                    Map<Integer, Map<String, PartialOffer>> outputs = new HashMap<>();
                    loadPartialOffers(flipId, inputs, outputs);

                    RecipeFlip flip = new RecipeFlip(timeOfCreation, outputs, inputs, coinCost);
                    flips.add(flip);
                }
            }
        }
        return flips;
    }

    private void loadPartialOffers(long flipId, Map<Integer, Map<String, PartialOffer>> inputs,
            Map<Integer, Map<String, PartialOffer>> outputs) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_PARTIAL_OFFERS_BY_FLIP_SQL)) {
            stmt.setLong(1, flipId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String offerUuid = rs.getString("offer_event_uuid");
                    int amountConsumed = rs.getInt("amount_consumed");
                    boolean isInput = rs.getInt("is_input") == 1;
                    int itemId = rs.getInt("item_id");

                    OfferEvent offer = offerEventRepository.findByUuid(offerUuid);
                    if (offer == null) {
                        log.warn("Could not find offer event with uuid {} for partial offer", offerUuid);
                        continue;
                    }

                    PartialOffer partial = new PartialOffer(offer, amountConsumed);
                    Map<Integer, Map<String, PartialOffer>> targetMap = isInput ? inputs : outputs;
                    targetMap.computeIfAbsent(itemId, k -> new HashMap<>()).put(offerUuid, partial);
                }
            }
        }
    }

    public void deleteGroup(long groupId) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(DELETE_GROUP_SQL)) {
            stmt.setLong(1, groupId);
            stmt.executeUpdate();
        }
    }

    public void deleteFlip(long flipId) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(DELETE_FLIP_SQL)) {
            stmt.setLong(1, flipId);
            stmt.executeUpdate();
        }
    }
}
