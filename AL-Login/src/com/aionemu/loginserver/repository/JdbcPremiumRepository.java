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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;

/**
 * Reads and writes the premium balances over JDBC.
 * <p>
 * <b>Two things here are preserved deliberately, not endorsed.</b> The DAO this
 * replaces claimed at most one pending reward per call, because it tested the
 * result set with an if rather than a loop, and it marked that reward as
 * received without ever adding its points to account_data. The balance it
 * returns reaches the game server, which sends it back later through
 * CM_ACCOUNT_TOLL_INFO, and only then is it written down. An account that
 * disconnects in between loses the reward.
 * <p>
 * Both are carried across unchanged: this is currency crossing a server
 * boundary, and correcting it is a decision about the protocol rather than about
 * the data access layer.
 *
 * @author Oraion
 */
public final class JdbcPremiumRepository extends JdbcRepositorySupport implements PremiumRepository {

	private static final Logger log = LoggerFactory.getLogger("PREMIUM_CTRL");

	private static final String SELECT_TOLL = "SELECT `toll` FROM `account_data` WHERE `id` = ?";
	private static final String SELECT_LUNA = "SELECT `luna` FROM `account_data` WHERE `id` = ?";
	private static final String SELECT_PENDING_REWARD = "SELECT `uniqId`,`points` FROM `account_rewards` "
			+ "WHERE `accountId` = ? AND `rewarded` = 0";
	private static final String MARK_REWARD_RECEIVED = "UPDATE `account_rewards` SET `rewarded` = 1, "
			+ "`received` = NOW() WHERE `uniqId` = ?";
	private static final String UPDATE_TOLL = "UPDATE `account_data` SET `toll` = ? WHERE `id` = ?";
	private static final String UPDATE_LUNA = "UPDATE `account_data` SET `luna` = ? WHERE `id` = ?";

	public JdbcPremiumRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public long claimAndGetPoints(int accountId) {
		// One connection for the three statements, where the DAO borrowed three.
		try (Connection connection = connection()) {
			long points = readBalance(connection, SELECT_TOLL, "toll", accountId);

			int claimedReward = -1;
			try (PreparedStatement statement = connection.prepareStatement(SELECT_PENDING_REWARD)) {
				statement.setInt(1, accountId);
				try (ResultSet rows = statement.executeQuery()) {
					// Deliberately one reward, matching the DAO. See the class note.
					if (rows.next()) {
						claimedReward = rows.getInt("uniqId");
						points += rows.getLong("points");
					}
				}
			}

			if (claimedReward >= 0) {
				try (PreparedStatement statement = connection.prepareStatement(MARK_REWARD_RECEIVED)) {
					statement.setInt(1, claimedReward);
					statement.executeUpdate();
				}
				log.info("Account " + accountId + " has received uniqId #" + claimedReward);
			}

			return points;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the toll balance of account " + accountId + ".", e);
		}
	}

	@Override
	public long getLuna(int accountId) {
		try (Connection connection = connection()) {
			return readBalance(connection, SELECT_LUNA, "luna", accountId);
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the luna balance of account " + accountId + ".", e);
		}
	}

	@Override
	public boolean spendPoints(int accountId, long points, long required) {
		return writeBalance(UPDATE_TOLL, accountId, points - required, "toll");
	}

	@Override
	public boolean setLuna(int accountId, long luna) {
		return writeBalance(UPDATE_LUNA, accountId, luna, "luna");
	}

	/** Reads one balance column, answering zero when the account has no row. */
	private static long readBalance(Connection connection, String query, String column, int accountId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, accountId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getLong(column) : 0L;
			}
		}
	}

	/** Writes one balance column. */
	private boolean writeBalance(String query, int accountId, long value, String what) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setLong(1, value);
			statement.setInt(2, accountId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to write the " + what + " balance of account " + accountId + ".", e);
		}
	}
}
