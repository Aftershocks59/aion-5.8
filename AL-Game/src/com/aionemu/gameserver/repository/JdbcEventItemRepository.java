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
import java.util.Map;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.event.MaxCountOfDay;

/**
 * Reads and writes the daily event item counts over JDBC.
 *
 * @author Oraion
 */
public final class JdbcEventItemRepository extends JdbcRepositorySupport implements EventItemRepository {

	private static final String SELECT_ALL = "SELECT `item_id`,`counts` FROM `event_items` WHERE `player_id` = ?";
	private static final String DELETE_BY_PLAYER = "DELETE FROM `event_items` WHERE `player_id` = ?";
	private static final String DELETE_BY_ITEM = "DELETE FROM `event_items` WHERE `item_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `event_items` (`player_id`,`item_id`,`counts`) VALUES (?,?,?)";

	public JdbcEventItemRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					player.addItemMaxCountOfDay(rows.getInt("item_id"), rows.getInt("counts"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the event item counts of character " + player.getObjectId() + ".", e);
		}
	}

	@Override
	public void store(Player player) {
		// The delete and the inserts belong together: the DAO ran them on separate
		// connections, so a failure between the two left the character with none.
		inTransaction(connection -> {
			try (PreparedStatement delete = connection.prepareStatement(DELETE_BY_PLAYER)) {
				delete.setInt(1, player.getObjectId());
				delete.executeUpdate();
			}

			Map<Integer, MaxCountOfDay> counts = player.getItemMaxThisCounts();
			if (counts == null || counts.isEmpty()) {
				return null;
			}

			try (PreparedStatement insert = connection.prepareStatement(INSERT_ONE)) {
				int queued = 0;
				for (Map.Entry<Integer, MaxCountOfDay> count : counts.entrySet()) {
					if (count.getValue() == null) {
						continue;
					}
					insert.setInt(1, player.getObjectId());
					insert.setInt(2, count.getKey().intValue());
					insert.setInt(3, count.getValue().getThisCount());
					insert.addBatch();
					queued++;
				}
				if (queued > 0) {
					insert.executeBatch();
				}
			}
			return null;
		}, "Failed to write the event item counts of character " + player.getObjectId() + ".");

		// Only once the whole batch has landed, so a failure does not throw away
		// counts that were never stored.
		player.clearItemMaxThisCount();
	}

	@Override
	public int removeItem(int itemId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_BY_ITEM)) {
			statement.setInt(1, itemId);
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to forget the event item " + itemId + ".", e);
		}
	}
}
