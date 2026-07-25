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

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Reads and writes the shape a character is transformed into, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerTransformRepository extends JdbcRepositorySupport
		implements PlayerTransformRepository {

	private static final String SELECT_ONE = "SELECT `panel_id`,`item_id` FROM `player_transform` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_transform` (`player_id`,`panel_id`,`item_id`) VALUES (?,?,?)";
	private static final String DELETE_ONE = "DELETE FROM `player_transform` WHERE `player_id` = ?";

	public JdbcPlayerTransformRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot read a transformation for a null character.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				// A character holds one shape at a time, so the last row wins as it
				// did before.
				while (rows.next()) {
					player.getTransformModel().setPanelId(rows.getInt("panel_id"));
					player.getTransformModel().setItemId(rows.getInt("item_id"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the transformation of character " + player.getObjectId() + ".", e);
		}
	}

	@Override
	public boolean save(int playerId, int panelId, int itemId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, panelId);
			statement.setInt(3, itemId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record the transformation of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean remove(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to forget the transformation of character " + playerId + ".", e);
		}
	}
}
