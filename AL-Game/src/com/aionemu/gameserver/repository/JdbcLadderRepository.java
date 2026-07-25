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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;

/**
 * Reads and writes where each character stands on the arena ladder, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcLadderRepository extends JdbcRepositorySupport implements LadderRepository {

	/** A day, after which a character's rank is worth remembering as their last. */
	private static final long RANK_MEMORY = 24L * 60L * 60L * 1000L;

	private static final String SELECT_RANKED = "SELECT `player_id`,`last_update`,`rank` FROM `ladder_player`"
			+ " WHERE `wins` > 0 OR `losses` > 0 OR `leaves` > 0 ORDER BY `rating` DESC, `wins` DESC, `player_id` ASC";
	private static final String UPDATE_RANK = "UPDATE `ladder_player` SET `rank` = ? WHERE `player_id` = ?";
	private static final String UPDATE_LAST_RANK = "UPDATE `ladder_player` SET `last_rank` = ?, `last_update` = ?"
			+ " WHERE `player_id` = ?";

	public JdbcLadderRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void updateRanks() {
		List<int[]> ranked = new ArrayList<int[]>();
		List<Integer> toRemember = new ArrayList<Integer>();
		long now = System.currentTimeMillis();

		// The database orders them, where the DAO asked for the wrong order and
		// then sorted the whole ladder again in memory.
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_RANKED);
				ResultSet rows = statement.executeQuery()) {
			int rank = 1;
			while (rows.next()) {
				int playerId = rows.getInt("player_id");
				ranked.add(new int[] { playerId, rank });
				rank++;

				Timestamp lastUpdate = rows.getTimestamp("last_update");
				if (lastUpdate == null || lastUpdate.getTime() == 0L || lastUpdate.getTime() + RANK_MEMORY < now) {
					toRemember.add(Integer.valueOf(rows.getInt("rank")));
					toRemember.add(Integer.valueOf(playerId));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the arena ladder.", e);
		}

		if (ranked.isEmpty()) {
			return;
		}

		Timestamp stamped = new Timestamp(now);
		inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(UPDATE_RANK)) {
				for (int[] entry : ranked) {
					statement.setInt(1, entry[1]);
					statement.setInt(2, entry[0]);
					statement.addBatch();
				}
				statement.executeBatch();
			}
			if (!toRemember.isEmpty()) {
				try (PreparedStatement statement = connection.prepareStatement(UPDATE_LAST_RANK)) {
					for (int i = 0; i < toRemember.size(); i += 2) {
						statement.setInt(1, toRemember.get(i).intValue());
						statement.setTimestamp(2, stamped);
						statement.setInt(3, toRemember.get(i + 1).intValue());
						statement.addBatch();
					}
					statement.executeBatch();
				}
			}
			return null;
		}, "Failed to re-number the arena ladder.");
	}

	@Override
	public void addWin(int playerId) {
		increase(playerId, "wins", 1);
	}

	@Override
	public void addLoss(int playerId) {
		increase(playerId, "losses", 1);
	}

	@Override
	public void addLeave(int playerId) {
		increase(playerId, "leaves", 1);
	}

	@Override
	public void addRating(int playerId, int delta) {
		increase(playerId, "rating", delta);
	}

	@Override
	public int findRating(int playerId) {
		int rating = read(playerId, "rating");
		// A character with no row, or one that has never been rated, starts here.
		return rating == 0 ? STARTING_RATING : rating;
	}

	@Override
	public int findLeaves(int playerId) {
		return read(playerId, "leaves");
	}

	@Override
	public void setLeaves(int playerId, int leaves) {
		set(playerId, "leaves", leaves);
	}

	/**
	 * Adds to a counter, creating the character's row if they have none. One
	 * upsert, where the DAO asked whether the row existed and then wrote, which
	 * two matches finishing at once could both answer "no" to.
	 * <p>
	 * The column name is spliced into the statement because a placeholder cannot
	 * stand for one. Every caller is in this file and passes a literal.
	 */
	private void increase(int playerId, String column, int value) {
		String query = "INSERT INTO `ladder_player` (`player_id`,`" + column + "`) VALUES (?,?)"
				+ " ON DUPLICATE KEY UPDATE `" + column + "` = `" + column + "` + ?";
		int opening = "rating".equals(column) ? STARTING_RATING + value : value;

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, playerId);
			statement.setInt(2, opening);
			statement.setInt(3, value);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to add to the " + column + " of character " + playerId + " on the ladder.", e);
		}
	}

	private void set(int playerId, String column, int value) {
		String query = "INSERT INTO `ladder_player` (`player_id`,`" + column + "`) VALUES (?,?)"
				+ " ON DUPLICATE KEY UPDATE `" + column + "` = ?";

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, playerId);
			statement.setInt(2, value);
			statement.setInt(3, value);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to set the " + column + " of character " + playerId + " on the ladder.", e);
		}
	}

	private int read(int playerId, String column) {
		String query = "SELECT `" + column + "` FROM `ladder_player` WHERE `player_id` = ?";

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getInt(column) : 0;
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the " + column + " of character " + playerId + " on the ladder.", e);
		}
	}
}
