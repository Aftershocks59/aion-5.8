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
 * Holds what the five cooldown tables do identically.
 * <p>
 * Each stores rows keyed by player and replaces them all on save. The DAOs did
 * that by deleting on one connection and inserting on another, with nothing
 * tying the two together: a failure in between left the player with no cooldowns
 * at all rather than the ones they had. Here both happen in one transaction.
 *
 * @author Oraion
 */
abstract class AbstractCooldownRepository extends JdbcRepositorySupport implements PlayerCooldownRepository {

	/** Names this kind of cooldown, for what a failure reports. */
	private final String kind;

	AbstractCooldownRepository(DataSource dataSource, String kind) {
		super(dataSource);
		this.kind = kind;
	}

	/** Reads every row this player holds. */
	protected abstract String selectQuery();

	/** Drops every row this player holds. */
	protected abstract String deleteQuery();

	/** Writes one row. */
	protected abstract String insertQuery();

	/**
	 * Applies the rows to the player.
	 *
	 * @param player the player being loaded
	 * @param rows   the cursor, positioned before the first row
	 */
	protected abstract void apply(Player player, ResultSet rows) throws SQLException;

	/**
	 * Queues what the player currently holds.
	 *
	 * @param player    the player being saved
	 * @param statement the insert to add batches to
	 * @return how many rows were queued
	 */
	protected abstract int queue(Player player, PreparedStatement statement) throws SQLException;

	/** Runs once the rows have been applied, for whatever the load has to announce. */
	protected void afterLoad(Player player) {
		// Nothing by default.
	}

	@Override
	public final void load(Player player) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(selectQuery())) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				apply(player, rows);
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the " + kind + " cooldowns of player " + player.getObjectId() + ".", e);
		}
		afterLoad(player);
	}

	@Override
	public final void store(Player player) {
		inTransaction(connection -> {
			try (PreparedStatement delete = connection.prepareStatement(deleteQuery())) {
				delete.setInt(1, player.getObjectId());
				delete.executeUpdate();
			}

			try (PreparedStatement insert = connection.prepareStatement(insertQuery())) {
				if (queue(player, insert) > 0) {
					insert.executeBatch();
				}
			}
			return null;
		}, "Failed to write the " + kind + " cooldowns of player " + player.getObjectId() + ".");
	}
}
