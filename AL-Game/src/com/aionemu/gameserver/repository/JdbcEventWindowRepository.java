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
import com.aionemu.gameserver.model.event_window.PlayerEventWindowEntry;
import com.aionemu.gameserver.model.event_window.PlayerEventWindowList;
import com.aionemu.gameserver.model.gameobjects.PersistentState;

/**
 * Reads and writes how far each account has got through the timed event
 * windows, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcEventWindowRepository extends JdbcRepositorySupport implements EventWindowRepository {

	private static final String SELECT_OPEN = "SELECT `event_id`,`last_stamp`,`elapsed` FROM `player_events_window`"
			+ " WHERE `account_id` = ?";
	private static final String SELECT_EVENT_IDS = "SELECT `event_id` FROM `player_events_window` WHERE `account_id` = ?";
	private static final String SELECT_LAST_STAMP = "SELECT `last_stamp` FROM `player_events_window`"
			+ " WHERE `account_id` = ? AND `event_id` = ?";
	private static final String SELECT_ELAPSED = "SELECT `elapsed` FROM `player_events_window`"
			+ " WHERE `account_id` = ? AND `event_id` = ?";
	private static final String SELECT_REWARD_COUNT = "SELECT `reward_recived_count` FROM `player_events_window`"
			+ " WHERE `account_id` = ? AND `event_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_events_window` (`account_id`,`event_id`,`last_stamp`)"
			+ " VALUES (?,?,?)";
	private static final String UPSERT_ONE = "INSERT INTO `player_events_window`"
			+ " (`account_id`,`event_id`,`last_stamp`,`elapsed`) VALUES (?,?,?,?)"
			+ " ON DUPLICATE KEY UPDATE `last_stamp` = VALUES(`last_stamp`), `elapsed` = VALUES(`elapsed`)";
	private static final String DELETE_ONE = "DELETE FROM `player_events_window`"
			+ " WHERE `account_id` = ? AND `event_id` = ?";
	private static final String UPDATE_ELAPSED = "UPDATE `player_events_window` SET `elapsed` = ?"
			+ " WHERE `account_id` = ? AND `event_id` = ?";
	private static final String UPDATE_REWARD_COUNT = "UPDATE `player_events_window` SET `reward_recived_count` = ?,"
			+ " `elapsed` = 0, `last_stamp` = NOW() WHERE `account_id` = ? AND `event_id` = ?";

	public JdbcEventWindowRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public PlayerEventWindowList load(int accountId) {
		List<PlayerEventWindowEntry> open = new ArrayList<PlayerEventWindowEntry>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_OPEN)) {
			statement.setInt(1, accountId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					open.add(new PlayerEventWindowEntry(rows.getInt("event_id"), rows.getTimestamp("last_stamp"),
							rows.getInt("elapsed"), PersistentState.UPDATED));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the event windows of account " + accountId + ".", e);
		}

		return new PlayerEventWindowList(open);
	}

	@Override
	public List<Integer> findEventIds(int accountId) {
		List<Integer> eventIds = new ArrayList<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_EVENT_IDS)) {
			statement.setInt(1, accountId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					eventIds.add(Integer.valueOf(rows.getInt("event_id")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read which event windows account " + accountId + " has open.", e);
		}

		return eventIds;
	}

	@Override
	public boolean add(int accountId, int eventId, Timestamp lastStamp) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, accountId);
			statement.setInt(2, eventId);
			statement.setTimestamp(3, lastStamp);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to open event window " + eventId + " for account " + accountId + ".", e);
		}
	}

	@Override
	public boolean save(int accountId, int eventId, Timestamp lastStamp, int elapsed) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPSERT_ONE)) {
			statement.setInt(1, accountId);
			statement.setInt(2, eventId);
			statement.setTimestamp(3, lastStamp);
			statement.setInt(4, elapsed);
			// The DAO's upsert refreshed the event id and the stamp but not the
			// elapsed time, so time spent was thrown away on every existing row.
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to store event window " + eventId + " of account " + accountId + ".", e);
		}
	}

	@Override
	public boolean remove(int accountId, int eventId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, accountId);
			statement.setInt(2, eventId);
			// The DAO bound the parameters and never executed the statement, so no
			// event window was ever closed.
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to close event window " + eventId + " of account " + accountId + ".", e);
		}
	}

	@Override
	public Timestamp findLastStamp(int accountId, int eventId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_LAST_STAMP)) {
			statement.setInt(1, accountId);
			statement.setInt(2, eventId);
			try (ResultSet rows = statement.executeQuery()) {
				// The DAO answered the present moment from its catch, which reads as
				// "just touched" and hid a window that was in fact never opened.
				return rows.next() ? rows.getTimestamp("last_stamp") : null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read when account " + accountId + " last touched event window "
					+ eventId + ".", e);
		}
	}

	@Override
	public int findElapsed(int accountId, int eventId) {
		return readCount(accountId, eventId, SELECT_ELAPSED, "elapsed");
	}

	@Override
	public int findRewardCount(int accountId, int eventId) {
		return readCount(accountId, eventId, SELECT_REWARD_COUNT, "reward_recived_count");
	}

	private int readCount(int accountId, int eventId, String query, String column) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, accountId);
			statement.setInt(2, eventId);
			try (ResultSet rows = statement.executeQuery()) {
				// The DAO read the row without checking there was one, and answered
				// zero from a catch when there was not.
				return rows.next() ? rows.getInt(column) : 0;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the " + column + " of event window " + eventId
					+ " of account " + accountId + ".", e);
		}
	}

	@Override
	public boolean setElapsed(int accountId, int eventId, int elapsed) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ELAPSED)) {
			statement.setInt(1, elapsed);
			statement.setInt(2, accountId);
			statement.setInt(3, eventId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record the time account " + accountId + " spent on event window "
					+ eventId + ".", e);
		}
	}

	@Override
	public boolean setRewardCount(int accountId, int eventId, int count) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_REWARD_COUNT)) {
			statement.setInt(1, count);
			statement.setInt(2, accountId);
			statement.setInt(3, eventId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record the rewards account " + accountId
					+ " took from event window " + eventId + ".", e);
		}
	}
}
