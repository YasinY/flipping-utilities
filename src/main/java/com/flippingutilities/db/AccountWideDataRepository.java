package com.flippingutilities.db;

import com.flippingutilities.model.AccountWideData;
import com.flippingutilities.model.Option;
import com.flippingutilities.model.Section;
import com.flippingutilities.utilities.Recipe;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Singleton
public class AccountWideDataRepository {

    private static final int SINGLETON_ID = 1;

    private static final String UPSERT_SQL = "INSERT OR REPLACE INTO account_wide_data (id, options_json, sections_json, local_recipes_json, "
            +
            "should_make_new_additions, enhanced_slots, jwt) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_SQL = "SELECT * FROM account_wide_data WHERE id = ?";

    private static final String DELETE_SQL = "DELETE FROM account_wide_data WHERE id = ?";

    private final DatabaseConnectionManager connectionManager;
    private final Gson gson;

    @Inject
    public AccountWideDataRepository(DatabaseConnectionManager connectionManager, Gson gson) {
        this.connectionManager = connectionManager;
        this.gson = gson;
    }

    public void save(AccountWideData data) throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
            stmt.setInt(1, SINGLETON_ID);
            stmt.setString(2, gson.toJson(data.getOptions()));
            stmt.setString(3, gson.toJson(data.getSections()));
            stmt.setString(4, gson.toJson(data.getLocalRecipes()));
            stmt.setInt(5, data.isShouldMakeNewAdditions() ? 1 : 0);
            stmt.setInt(6, data.isEnhancedSlots() ? 1 : 0);
            stmt.setString(7, data.getJwt());
            stmt.executeUpdate();
        }
    }

    public AccountWideData load() throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_SQL)) {
            stmt.setInt(1, SINGLETON_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAccountWideData(rs);
                }
            }
        }
        return new AccountWideData();
    }

    public void delete() throws SQLException {
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
            stmt.setInt(1, SINGLETON_ID);
            stmt.executeUpdate();
        }
    }

    private AccountWideData mapResultSetToAccountWideData(ResultSet rs) throws SQLException {
        AccountWideData data = new AccountWideData();

        String optionsJson = rs.getString("options_json");
        if (optionsJson != null) {
            Type optionsType = new TypeToken<List<Option>>() {
            }.getType();
            List<Option> options = gson.fromJson(optionsJson, optionsType);
            data.setOptions(options != null ? options : new ArrayList<>());
        }

        String sectionsJson = rs.getString("sections_json");
        if (sectionsJson != null) {
            Type sectionsType = new TypeToken<List<Section>>() {
            }.getType();
            List<Section> sections = gson.fromJson(sectionsJson, sectionsType);
            data.setSections(sections != null ? sections : new ArrayList<>());
        }

        String localRecipesJson = rs.getString("local_recipes_json");
        if (localRecipesJson != null) {
            Type recipesType = new TypeToken<List<Recipe>>() {
            }.getType();
            List<Recipe> recipes = gson.fromJson(localRecipesJson, recipesType);
            data.setLocalRecipes(recipes != null ? recipes : new ArrayList<>());
        }

        data.setShouldMakeNewAdditions(rs.getInt("should_make_new_additions") == 1);
        data.setEnhancedSlots(rs.getInt("enhanced_slots") == 1);
        data.setJwt(rs.getString("jwt"));

        return data;
    }
}
