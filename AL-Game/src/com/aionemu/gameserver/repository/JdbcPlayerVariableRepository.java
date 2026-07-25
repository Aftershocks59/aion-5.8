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
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;

/**
 * Reads and writes a character's loose variables over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerVariableRepository extends JdbcRepositorySupport implements PlayerVariableRepository {

	private static final String SELECT_ALL = "SELECT `param`,`value` FROM `player_vars` WHERE `player_id` = ?";

	/**
	 * Writes a variable, whether or not the character already had one.
	 * <p>
	 * The DAO issued a plain insert. player_vars is keyed on the character and the
	 * name together, so setting a variable a second time hit the primary key, the
	 * error was swallowed, and the method answered false: a variable could be
	 * created but never changed.
	 */
	private static final String UPSERT_ONE = "INSERT INTO `player_vars` (`player_id`,`param`,`value`,`time`) "
			+ "VALUES (?,?,?,NOW()) ON DUPLICATE KEY UPDATE `value` = VALUES(`value`), `time` = NOW()";

	private static final String DELETE_ONE = "DELETE FROM `player_vars` WHERE `player_id` = ? AND `param` = ?";

	public JdbcPlayerVariableRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Map<String, Object> findAll(int playerId) {
		Map<String, Object> variables = new LinkedHashMap<String, Object>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					variables.put(rows.getString("param"), rows.getString("value"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the variables of character " + playerId + ".", e);
		}
		return variables;
	}

	@Override
	public boolean set(int playerId, String key, Object value) {
		if (key == null) {
			throw new IllegalArgumentException("A variable needs a name.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPSERT_ONE)) {
			statement.setInt(1, playerId);
			statement.setString(2, key);
			statement.setString(3, value == null ? null : value.toString());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to write the variable " + key + " of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean remove(int playerId, String key) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			statement.setString(2, key);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to drop the variable " + key + " of character " + playerId + ".", e);
		}
	}
}
