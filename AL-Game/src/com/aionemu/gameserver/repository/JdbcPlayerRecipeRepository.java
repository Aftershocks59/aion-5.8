/**
 * This file is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * It is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License along with
 * it. If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.RecipeList;

/**
 * Reads and writes the recipes a character has learnt, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerRecipeRepository extends JdbcRepositorySupport implements PlayerRecipeRepository {

	private static final String SELECT_ALL = "SELECT `recipe_id` FROM `player_recipes` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_recipes` (`player_id`,`recipe_id`) VALUES (?,?)";
	private static final String DELETE_ONE = "DELETE FROM `player_recipes` WHERE `player_id` = ? AND `recipe_id` = ?";

	public JdbcPlayerRecipeRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public RecipeList findAll(int playerId) {
		HashSet<Integer> recipes = new HashSet<Integer>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					recipes.add(Integer.valueOf(rows.getInt("recipe_id")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the recipes of character " + playerId + ".", e);
		}
		return new RecipeList(recipes);
	}

	@Override
	public boolean add(int playerId, int recipeId) {
		return write(INSERT_ONE, playerId, recipeId, "teach");
	}

	@Override
	public boolean remove(int playerId, int recipeId) {
		return write(DELETE_ONE, playerId, recipeId, "remove");
	}

	private boolean write(String query, int playerId, int recipeId, String what) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, playerId);
			statement.setInt(2, recipeId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to " + what + " recipe " + recipeId + " for character " + playerId + ".", e);
		}
	}
}
