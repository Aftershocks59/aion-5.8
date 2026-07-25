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
package com.aionemu.loginserver.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.model.AccountTime;

/**
 * Reads and writes the account timings over JDBC.
 *
 * @author Oraion
 */
public final class JdbcAccountTimeRepository extends JdbcRepositorySupport implements AccountTimeRepository {

	private static final String SELECT_ONE = "SELECT `last_active`,`expiration_time`,`session_duration`,"
			+ "`accumulated_online`,`accumulated_rest`,`penalty_end` FROM `account_time` WHERE `account_id` = ?";

	private static final String REPLACE_ONE = "REPLACE INTO `account_time` (`account_id`,`last_active`,"
			+ "`expiration_time`,`session_duration`,`accumulated_online`,`accumulated_rest`,`penalty_end`) "
			+ "VALUES (?,?,?,?,?,?,?)";

	public JdbcAccountTimeRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public AccountTime find(int accountId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, accountId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? read(rows) : null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the timings of account " + accountId + ".", e);
		}
	}

	@Override
	public boolean save(int accountId, AccountTime accountTime) {
		if (accountTime == null) {
			throw new IllegalArgumentException("Cannot store null timings.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(REPLACE_ONE)) {
			statement.setInt(1, accountId);
			statement.setTimestamp(2, accountTime.getLastLoginTime());
			statement.setTimestamp(3, accountTime.getExpirationTime());
			statement.setLong(4, accountTime.getSessionDuration());
			statement.setLong(5, accountTime.getAccumulatedOnlineTime());
			statement.setLong(6, accountTime.getAccumulatedRestTime());
			statement.setTimestamp(7, accountTime.getPenaltyEnd());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to write the timings of account " + accountId + ".", e);
		}
	}

	/** Builds the timings from the row the cursor sits on. */
	private static AccountTime read(ResultSet rows) throws SQLException {
		AccountTime accountTime = new AccountTime();
		accountTime.setLastLoginTime(rows.getTimestamp("last_active"));
		accountTime.setExpirationTime(rows.getTimestamp("expiration_time"));
		accountTime.setSessionDuration(rows.getLong("session_duration"));
		accountTime.setAccumulatedOnlineTime(rows.getLong("accumulated_online"));
		accountTime.setAccumulatedRestTime(rows.getLong("accumulated_rest"));
		accountTime.setPenaltyEnd(rows.getTimestamp("penalty_end"));
		return accountTime;
	}
}
