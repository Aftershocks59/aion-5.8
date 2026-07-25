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
import com.aionemu.gameserver.model.atreian_bestiary.PlayerABEntry;
import com.aionemu.gameserver.model.atreian_bestiary.PlayerABList;
import com.aionemu.gameserver.model.gameobjects.PersistentState;

/**
 * Reads and writes what each character has hunted for the Atreian bestiary,
 * over JDBC.
 *
 * @author Oraion
 */
public final class JdbcAtreianBestiaryRepository extends JdbcRepositorySupport
		implements AtreianBestiaryRepository {

	private static final String SELECT_HUNTED = "SELECT `id`,`kill_count`,`level`,`claim_reward`"
			+ " FROM `player_atreian_bestiary` WHERE `player_id` = ?";
	private static final String UPSERT_ONE = "INSERT INTO `player_atreian_bestiary`"
			+ " (`player_id`,`id`,`kill_count`,`level`,`claim_reward`) VALUES (?,?,?,?,?)"
			+ " ON DUPLICATE KEY UPDATE `kill_count` = VALUES(`kill_count`), `level` = VALUES(`level`),"
			+ " `claim_reward` = VALUES(`claim_reward`)";
	private static final String DELETE_ONE = "DELETE FROM `player_atreian_bestiary` WHERE `player_id` = ? AND `id` = ?";

	public JdbcAtreianBestiaryRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public PlayerABList load(int playerId) {
		List<PlayerABEntry> hunted = new ArrayList<PlayerABEntry>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_HUNTED)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					hunted.add(new PlayerABEntry(rows.getInt("id"), rows.getInt("kill_count"), rows.getInt("level"),
							rows.getInt("claim_reward"), PersistentState.UPDATED));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the bestiary of character " + playerId + ".", e);
		}

		return new PlayerABList(hunted);
	}

	@Override
	public boolean save(int playerId, int beastId, int killCount, int level, int rewarded) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPSERT_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, beastId);
			statement.setInt(3, killCount);
			statement.setInt(4, level);
			statement.setInt(5, rewarded);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to store bestiary entry " + beastId + " of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean remove(int playerId, int beastId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, beastId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to clear bestiary entry " + beastId + " of character " + playerId + ".", e);
		}
	}

	@Override
	public int findKillCount(int playerId, int beastId) {
		return readColumn(playerId, beastId, "kill_count");
	}

	@Override
	public int findLevel(int playerId, int beastId) {
		return readColumn(playerId, beastId, "level");
	}

	@Override
	public int findClaimedReward(int playerId, int beastId) {
		return readColumn(playerId, beastId, "claim_reward");
	}

	private int readColumn(int playerId, int beastId, String column) {
		String query = "SELECT `" + column + "` FROM `player_atreian_bestiary` WHERE `player_id` = ? AND `id` = ?";

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, playerId);
			statement.setInt(2, beastId);
			try (ResultSet rows = statement.executeQuery()) {
				// The DAO read the row without checking there was one, and answered
				// zero from a catch when there was not.
				return rows.next() ? rows.getInt(column) : NOT_HUNTED;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the " + column + " of bestiary entry " + beastId
					+ " of character " + playerId + ".", e);
		}
	}
}
