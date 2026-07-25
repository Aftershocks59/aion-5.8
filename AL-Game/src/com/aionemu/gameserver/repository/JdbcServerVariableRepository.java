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
 * Reads and writes the server's own variables over JDBC.
 *
 * @author Oraion
 */
public final class JdbcServerVariableRepository extends JdbcRepositorySupport implements ServerVariableRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcServerVariableRepository.class);

	private static final String SELECT_ONE = "SELECT `value` FROM `server_variables` WHERE `key` = ?";
	private static final String REPLACE_ONE = "REPLACE INTO `server_variables` (`key`,`value`) VALUES (?,?)";

	public JdbcServerVariableRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public int find(String key) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setString(1, key);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? parse(key, rows.getString("value")) : 0;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the server variable " + key + ".", e);
		}
	}

	@Override
	public boolean set(String key, int value) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(REPLACE_ONE)) {
			statement.setString(1, key);
			statement.setString(2, String.valueOf(value));
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to write the server variable " + key + ".", e);
		}
	}

	/**
	 * Reads the stored text as a number.
	 * <p>
	 * The column holds text, so a hand-edited row can carry something that is not a
	 * number. The DAO called parseInt inside a catch for SQLException only, which
	 * NumberFormatException is not, so a bad row escaped as an unchecked throw from
	 * whatever asked for the time. Answer zero, the same as a missing row.
	 */
	private static int parse(String key, String value) {
		if (value == null) {
			return 0;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			log.warn("Ignored the server variable " + key + ": " + value + " is not a number.");
			return 0;
		}
	}
}
