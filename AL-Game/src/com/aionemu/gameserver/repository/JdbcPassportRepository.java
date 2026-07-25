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
 * Reads and writes how far each account has got through its Atreian passports,
 * over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPassportRepository extends JdbcRepositorySupport implements PassportRepository {

	private static final String SELECT_HELD = "SELECT `passport_id` FROM `player_passports` WHERE `account_id` = ?";
	private static final String SELECT_STAMPS = "SELECT `stamps` FROM `player_passports`"
			+ " WHERE `account_id` = ? AND `passport_id` = ?";
	private static final String SELECT_LAST_STAMP = "SELECT `last_stamp` FROM `player_passports`"
			+ " WHERE `account_id` = ? AND `passport_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_passports` (`account_id`,`passport_id`,`stamps`,`last_stamp`)"
			+ " VALUES (?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `player_passports` SET `stamps` = ?, `rewarded` = ?, `last_stamp` = ?"
			+ " WHERE `account_id` = ? AND `passport_id` = ?";

	public JdbcPassportRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public List<Integer> findPassports(int accountId) {
		List<Integer> held = new ArrayList<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_HELD)) {
			statement.setInt(1, accountId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					held.add(Integer.valueOf(rows.getInt("passport_id")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the passports of account " + accountId + ".", e);
		}

		return held;
	}

	@Override
	public boolean add(int accountId, int passportId, int stamps, Timestamp lastStamp) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, accountId);
			statement.setInt(2, passportId);
			statement.setInt(3, stamps);
			statement.setTimestamp(4, lastStamp);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to give account " + accountId + " passport " + passportId + ".", e);
		}
	}

	@Override
	public boolean update(int accountId, int passportId, int stamps, boolean rewarded, Timestamp lastStamp) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setInt(1, stamps);
			statement.setInt(2, rewarded ? 1 : 0);
			statement.setTimestamp(3, lastStamp);
			statement.setInt(4, accountId);
			statement.setInt(5, passportId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to store passport " + passportId + " of account " + accountId + ".", e);
		}
	}

	@Override
	public int findStamps(int accountId, int passportId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_STAMPS)) {
			statement.setInt(1, accountId);
			statement.setInt(2, passportId);
			try (ResultSet rows = statement.executeQuery()) {
				// The DAO read the row without checking there was one, and answered
				// zero from a catch when there was not.
				return rows.next() ? rows.getInt("stamps") : NO_STAMPS;
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the stamps on passport " + passportId + " of account " + accountId + ".", e);
		}
	}

	@Override
	public Timestamp findLastStamp(int accountId, int passportId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_LAST_STAMP)) {
			statement.setInt(1, accountId);
			statement.setInt(2, passportId);
			try (ResultSet rows = statement.executeQuery()) {
				// The DAO answered the present moment from its catch, which reads as
				// "just stamped" and cost the account that day's stamp.
				return rows.next() ? rows.getTimestamp("last_stamp") : null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read when passport " + passportId + " of account " + accountId
					+ " was last stamped.", e);
		}
	}
}
