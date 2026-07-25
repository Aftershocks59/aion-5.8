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
import java.sql.Statement;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.model.Account;

/**
 * Reads and writes the accounts over JDBC.
 *
 * @author Oraion
 */
public final class JdbcAccountRepository extends JdbcRepositorySupport implements AccountRepository {

	/** Names the columns rather than selecting everything, so the mapping stays honest. */
	private static final String COLUMNS = "`id`,`name`,`password`,`access_level`,`membership`,`activated`,"
			+ "`last_server`,`last_ip`,`last_mac`,`ip_force`,`return_account`,`return_end`";

	private static final String SELECT_BY_NAME = "SELECT " + COLUMNS + " FROM `account_data` WHERE `name` = ?";
	private static final String SELECT_BY_ID = "SELECT " + COLUMNS + " FROM `account_data` WHERE `id` = ?";
	private static final String SELECT_ID_BY_NAME = "SELECT `id` FROM `account_data` WHERE `name` = ?";
	private static final String SELECT_LAST_IP = "SELECT `last_ip` FROM `account_data` WHERE `id` = ?";
	private static final String COUNT_ALL = "SELECT COUNT(*) FROM `account_data`";

	private static final String INSERT_ONE = "INSERT INTO `account_data` (`name`,`password`,`access_level`,"
			+ "`membership`,`activated`,`last_server`,`last_ip`,`last_mac`,`ip_force`,`toll`) "
			+ "VALUES (?,?,?,?,?,?,?,?,?,?)";

	private static final String UPDATE_ONE = "UPDATE `account_data` SET `name` = ?, `password` = ?, "
			+ "`access_level` = ?, `membership` = ?, `last_server` = ?, `last_ip` = ?, `last_mac` = ?, "
			+ "`ip_force` = ?, `return_account` = ?, `return_end` = ? WHERE `id` = ?";

	private static final String UPDATE_LAST_SERVER = "UPDATE `account_data` SET `last_server` = ? WHERE `id` = ?";
	private static final String UPDATE_LAST_IP = "UPDATE `account_data` SET `last_ip` = ? WHERE `id` = ?";
	private static final String UPDATE_LAST_MAC = "UPDATE `account_data` SET `last_mac` = ? WHERE `id` = ?";

	private static final String RESTORE_MEMBERSHIP = "UPDATE `account_data` SET `membership` = `old_membership`, "
			+ "`expire` = NULL WHERE `id` = ? AND `expire` < CURRENT_TIMESTAMP";

	private static final String DELETE_INACTIVE = "DELETE FROM `account_data` WHERE `id` IN "
			+ "(SELECT `account_id` FROM `account_time` "
			+ "WHERE UNIX_TIMESTAMP(CURDATE()) - UNIX_TIMESTAMP(`last_active`) > ? * 24 * 60 * 60)";

	public JdbcAccountRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Account findByName(String name) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_BY_NAME)) {
			statement.setString(1, name);
			return readOne(statement);
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the account named " + name + ".", e);
		}
	}

	@Override
	public Account findById(int id) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
			statement.setInt(1, id);
			return readOne(statement);
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the account with id " + id + ".", e);
		}
	}

	@Override
	public int findIdByName(String name) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ID_BY_NAME)) {
			statement.setString(1, name);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getInt("id") : NO_ACCOUNT;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the id of account " + name + ".", e);
		}
	}

	@Override
	public int count() {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(COUNT_ALL);
				ResultSet rows = statement.executeQuery()) {
			return rows.next() ? rows.getInt(1) : 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to count the accounts.", e);
		}
	}

	@Override
	public boolean save(Account account) {
		if (account == null) {
			throw new IllegalArgumentException("Cannot store a null account.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE,
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, account.getName());
			statement.setString(2, account.getPasswordHash());
			statement.setByte(3, account.getAccessLevel());
			statement.setByte(4, account.getMembership());
			statement.setByte(5, account.getActivated());
			statement.setByte(6, account.getLastServer());
			statement.setString(7, account.getLastIp());
			statement.setString(8, account.getLastMac());
			statement.setString(9, account.getIpForce());
			statement.setLong(10, 0L);
			if (statement.executeUpdate() == 0) {
				return false;
			}

			// Ask for the key rather than querying the name back, which is what the
			// DAO did and which races another insert of the same name.
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (keys.next()) {
					account.setId(keys.getInt(1));
				}
			}
			return true;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to store the account " + account.getName() + ".", e);
		}
	}

	/**
	 * Writes back an account.
	 * <p>
	 * The DAO always answered false here: it declared a result, ran the update
	 * without assigning it, then compared the untouched zero. Anything checking
	 * whether the write landed was told it had not.
	 */
	@Override
	public boolean update(Account account) {
		if (account == null) {
			throw new IllegalArgumentException("Cannot update a null account.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setString(1, account.getName());
			statement.setString(2, account.getPasswordHash());
			statement.setByte(3, account.getAccessLevel());
			statement.setByte(4, account.getMembership());
			statement.setByte(5, account.getLastServer());
			statement.setString(6, account.getLastIp());
			statement.setString(7, account.getLastMac());
			statement.setString(8, account.getIpForce());
			statement.setByte(9, account.getReturn());
			statement.setTimestamp(10, account.getReturnEnd());
			statement.setInt(11, account.getId());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to update the account " + account.getName() + ".", e);
		}
	}

	@Override
	public boolean updateLastServer(int accountId, byte lastServer) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_LAST_SERVER)) {
			statement.setByte(1, lastServer);
			statement.setInt(2, accountId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record the last server of account " + accountId + ".", e);
		}
	}

	@Override
	public boolean updateLastIp(int accountId, String ip) {
		return updateColumn(UPDATE_LAST_IP, accountId, ip, "address");
	}

	@Override
	public boolean updateLastMac(int accountId, String mac) {
		return updateColumn(UPDATE_LAST_MAC, accountId, mac, "machine");
	}

	@Override
	public String findLastIp(int accountId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_LAST_IP)) {
			statement.setInt(1, accountId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getString("last_ip") : null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the last address of account " + accountId + ".", e);
		}
	}

	@Override
	public boolean restoreExpiredMembership(int accountId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(RESTORE_MEMBERSHIP)) {
			statement.setInt(1, accountId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to restore the membership of account " + accountId + ".", e);
		}
	}

	@Override
	public int deleteInactive(int daysOfInactivity) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_INACTIVE)) {
			statement.setInt(1, daysOfInactivity);
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to delete the accounts idle for more than " + daysOfInactivity + " days.", e);
		}
	}

	/** Runs a prepared lookup and maps the row, if there is one. */
	private static Account readOne(PreparedStatement statement) throws SQLException {
		try (ResultSet rows = statement.executeQuery()) {
			return rows.next() ? read(rows) : null;
		}
	}

	/** Writes a single text column against an account. */
	private boolean updateColumn(String query, int accountId, String value, String what) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setString(1, value);
			statement.setInt(2, accountId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record the last " + what + " of account " + accountId + ".", e);
		}
	}

	/** Builds an account from the row the cursor sits on. */
	private static Account read(ResultSet rows) throws SQLException {
		Account account = new Account();
		account.setId(rows.getInt("id"));
		account.setName(rows.getString("name"));
		account.setPasswordHash(rows.getString("password"));
		account.setAccessLevel(rows.getByte("access_level"));
		account.setMembership(rows.getByte("membership"));
		account.setActivated(rows.getByte("activated"));
		account.setLastServer(rows.getByte("last_server"));
		account.setLastIp(rows.getString("last_ip"));
		account.setLastMac(rows.getString("last_mac"));
		account.setIpForce(rows.getString("ip_force"));
		account.setReturn(rows.getByte("return_account"));
		account.setReturnEnd(rows.getTimestamp("return_end"));
		return account;
	}
}
