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
import com.aionemu.gameserver.services.events.thievesguildservice.ThievesStatusList;

/**
 * Reads and writes where each character stands in the thieves guild, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcThievesGuildRepository extends JdbcRepositorySupport implements ThievesGuildRepository {

	private static final String SELECT_ONE = "SELECT `rank`,`thieves_count`,`prison_count`,`last_kinah`,`revenge_name`,`revenge_count`,`revenge_date`"
			+ " FROM `player_thieves` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_thieves`"
			+ " (`player_id`,`rank`,`thieves_count`,`prison_count`,`last_kinah`,`revenge_name`,`revenge_count`,`revenge_date`)"
			+ " VALUES (?,?,?,?,?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `player_thieves` SET `rank` = ?, `thieves_count` = ?,"
			+ " `prison_count` = ?, `last_kinah` = ?, `revenge_name` = ?, `revenge_count` = ?, `revenge_date` = ?"
			+ " WHERE `player_id` = ?";

	public JdbcThievesGuildRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public ThievesStatusList load(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return null;
				}
				ThievesStatusList standing = new ThievesStatusList();
				standing.setPlayerId(playerId);
				standing.setRankId(rows.getInt("rank"));
				standing.setThievesCount(rows.getInt("thieves_count"));
				standing.setPrisonCount(rows.getInt("prison_count"));
				standing.setLastThievesKinah(rows.getLong("last_kinah"));
				standing.setRevengeName(rows.getString("revenge_name"));
				standing.setRevengeCount(rows.getInt("revenge_count"));
				standing.setRevengeDate(rows.getTimestamp("revenge_date"));
				return standing;
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the thieves guild standing of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean add(ThievesStatusList standing) {
		if (standing == null) {
			throw new IllegalArgumentException("Cannot store a null thieves guild standing.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, standing.getPlayerId());
			bindStanding(statement, standing, 2);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to enrol character " + standing.getPlayerId() + " in the thieves guild.", e);
		}
	}

	@Override
	public boolean save(ThievesStatusList standing) {
		if (standing == null) {
			throw new IllegalArgumentException("Cannot store a null thieves guild standing.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			// The DAO bound nine parameters onto eight placeholders, writing the
			// avenger's name into the revenge date, so this write always threw and
			// nothing was ever saved.
			bindStanding(statement, standing, 1);
			statement.setInt(8, standing.getPlayerId());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to store the thieves guild standing of character " + standing.getPlayerId() + ".", e);
		}
	}

	private static void bindStanding(PreparedStatement statement, ThievesStatusList standing, int from)
			throws SQLException {
		statement.setInt(from, standing.getRankId());
		statement.setInt(from + 1, standing.getThievesCount());
		statement.setInt(from + 2, standing.getPrisonCount());
		statement.setLong(from + 3, standing.getLastThievesKinah());
		statement.setString(from + 4, standing.getRevengeName());
		statement.setInt(from + 5, standing.getRevengeCount());
		statement.setTimestamp(from + 6, standing.getRevengeDate());
	}
}
