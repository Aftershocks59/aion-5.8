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
import java.util.List;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.cp.PlayerCPEntry;
import com.aionemu.gameserver.model.cp.PlayerCPList;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Reads and writes the creativity points a character has spent, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcCreativityPointRepository extends JdbcRepositorySupport
		implements CreativityPointRepository {

	private static final String SELECT_SPENT = "SELECT `slot`,`point` FROM `player_cp` WHERE `player_id` = ?";
	private static final String UPSERT_ONE = "INSERT INTO `player_cp` (`player_id`,`slot`,`point`) VALUES (?,?,?)"
			+ " ON DUPLICATE KEY UPDATE `point` = VALUES(`point`)";
	private static final String DELETE_ONE = "DELETE FROM `player_cp` WHERE `player_id` = ? AND `slot` = ?";

	public JdbcCreativityPointRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public PlayerCPList load(int playerId) {
		List<PlayerCPEntry> spent = new ArrayList<PlayerCPEntry>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_SPENT)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					spent.add(new PlayerCPEntry(rows.getInt("slot"), rows.getInt("point"), PersistentState.UPDATED));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the creativity points of character " + playerId + ".", e);
		}

		return new PlayerCPList(spent);
	}

	@Override
	public void save(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot store the creativity points of a null character.");
		}

		int playerId = player.getObjectId();
		List<Integer> cleared = new ArrayList<Integer>(player.getCP().getRemoveItems());
		PlayerCPEntry[] spent = player.getCP().getAllCP();

		// One transaction over two prepared statements, where the DAO opened a
		// statement per slot and could leave half the change written.
		inTransaction(connection -> {
			if (!cleared.isEmpty()) {
				try (PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
					for (Integer slot : cleared) {
						statement.setInt(1, playerId);
						statement.setInt(2, slot.intValue());
						statement.addBatch();
					}
					statement.executeBatch();
				}
			}
			if (spent.length > 0) {
				try (PreparedStatement statement = connection.prepareStatement(UPSERT_ONE)) {
					for (PlayerCPEntry entry : spent) {
						statement.setInt(1, playerId);
						statement.setInt(2, entry.getSlot());
						statement.setInt(3, entry.getPoint());
						statement.addBatch();
					}
					statement.executeBatch();
				}
			}
			return null;
		}, "Failed to store the creativity points of character " + playerId + ".");
	}

}
