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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.skill.linked_skill.EquippedStigmasEntry;
import com.aionemu.gameserver.model.skill.linked_skill.PlayerEquippedStigmaList;

/**
 * Reads and writes the stigmas a character wears, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcEquippedStigmaRepository extends JdbcRepositorySupport
		implements EquippedStigmaRepository {

	private static final String SELECT_WORN = "SELECT `item_id`,`item_name` FROM `player_stigmas_equipped` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_stigmas_equipped` (`player_id`,`item_id`,`item_name`) VALUES (?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `player_stigmas_equipped` SET `item_id` = ?, `item_name` = ? WHERE `player_id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `player_stigmas_equipped` WHERE `player_id` = ? AND `item_id` = ? AND `item_name` = ?";

	public JdbcEquippedStigmaRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public PlayerEquippedStigmaList load(int playerId) {
		List<EquippedStigmasEntry> worn = new ArrayList<EquippedStigmasEntry>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_WORN)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					worn.add(new EquippedStigmasEntry(rows.getInt("item_id"), rows.getString("item_name"),
							PersistentState.UPDATED));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the stigmas worn by character " + playerId + ".", e);
		}

		return new PlayerEquippedStigmaList(worn);
	}

	@Override
	public void save(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot store the stigmas of a null character.");
		}

		List<EquippedStigmasEntry> pending = new ArrayList<EquippedStigmasEntry>();
		Collections.addAll(pending, player.getEquipedStigmaList().getAllItems());
		Collections.addAll(pending, player.getEquipedStigmaList().getDeletedItems());
		if (pending.isEmpty()) {
			return;
		}

		int playerId = player.getObjectId();
		inTransaction(connection -> {
			// Remove first, so a stigma taken off and put back on again in the same
			// breath ends up present rather than gone.
			write(connection, DELETE_ONE, pending, PersistentState.DELETED, playerId);
			write(connection, INSERT_ONE, pending, PersistentState.NEW, playerId);
			write(connection, UPDATE_ONE, pending, PersistentState.UPDATE_REQUIRED, playerId);
			return null;
		}, "Failed to store the stigmas worn by character " + playerId + ".");

		// Mark them saved only now. The DAO did this in a finally, so a write that
		// never landed still looked like it had.
		for (EquippedStigmasEntry entry : pending) {
			entry.setPersistentState(PersistentState.UPDATED);
		}
	}

	private void write(Connection connection, String query, List<EquippedStigmasEntry> entries,
			PersistentState state, int playerId) throws SQLException {
		int queued = 0;
		try (PreparedStatement statement = connection.prepareStatement(query)) {
			for (EquippedStigmasEntry entry : entries) {
				if (entry == null || entry.getPersistentState() != state) {
					continue;
				}
				if (UPDATE_ONE.equals(query)) {
					// The DAO bound the character id where the name belonged and the
					// name where the character id belonged, so no update ever matched
					// a row.
					statement.setInt(1, entry.getItemId());
					statement.setString(2, entry.getItemName());
					statement.setInt(3, playerId);
				} else {
					statement.setInt(1, playerId);
					statement.setInt(2, entry.getItemId());
					statement.setString(3, entry.getItemName());
				}
				statement.addBatch();
				queued++;
			}
			if (queued > 0) {
				statement.executeBatch();
			}
		}
	}
}
