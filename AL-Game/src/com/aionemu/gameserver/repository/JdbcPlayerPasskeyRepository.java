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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;

/**
 * Reads and writes the account passkey over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerPasskeyRepository extends JdbcRepositorySupport implements PlayerPasskeyRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcPlayerPasskeyRepository.class);

	private static final String INSERT_ONE = "INSERT INTO `player_passkey` (`account_id`,`passkey`) VALUES (?,?)";
	private static final String REPLACE_KNOWN = "UPDATE `player_passkey` SET `passkey` = ? WHERE `account_id` = ? AND `passkey` = ?";
	private static final String REPLACE_FORCED = "UPDATE `player_passkey` SET `passkey` = ? WHERE `account_id` = ?";
	private static final String COUNT_MATCHING = "SELECT COUNT(*) FROM `player_passkey` WHERE `account_id` = ? AND `passkey` = ?";
	private static final String COUNT_ANY = "SELECT COUNT(*) FROM `player_passkey` WHERE `account_id` = ?";

	public JdbcPlayerPasskeyRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public boolean create(int accountId, String passkey) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, accountId);
			statement.setString(2, passkey);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to set the passkey of account " + accountId + ".", e);
		}
	}

	@Override
	public boolean replace(int accountId, String oldPasskey, String newPasskey) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(REPLACE_KNOWN)) {
			statement.setString(1, newPasskey);
			statement.setInt(2, accountId);
			statement.setString(3, oldPasskey);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to change the passkey of account " + accountId + ".", e);
		}
	}

	@Override
	public boolean reset(int accountId, String newPasskey) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(REPLACE_FORCED)) {
			statement.setString(1, newPasskey);
			statement.setInt(2, accountId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to reset the passkey of account " + accountId + ".", e);
		}
	}

	@Override
	public boolean matches(int accountId, String passkey) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(COUNT_MATCHING)) {
			statement.setInt(1, accountId);
			statement.setString(2, passkey);
			return countIsOne(statement);
		} catch (SQLException e) {
			// Answer no. This decides whether somebody gets past a lock, and an
			// unanswerable question must not open it. The DAO chose the same.
			log.error("Cannot check the passkey of account " + accountId + "; refusing it.", e);
			return false;
		}
	}

	@Override
	public boolean exists(int accountId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(COUNT_ANY)) {
			statement.setInt(1, accountId);
			return countIsOne(statement);
		} catch (SQLException e) {
			log.error("Cannot tell whether account " + accountId + " has a passkey; answering no.", e);
			return false;
		}
	}

	/** Answers whether the count came back as exactly one, as the DAO required. */
	private static boolean countIsOne(PreparedStatement statement) throws SQLException {
		try (ResultSet rows = statement.executeQuery()) {
			return rows.next() && rows.getInt(1) == 1;
		}
	}
}
