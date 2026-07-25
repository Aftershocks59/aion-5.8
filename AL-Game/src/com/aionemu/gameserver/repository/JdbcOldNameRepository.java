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
 * Reads and records the names characters have given up, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcOldNameRepository extends JdbcRepositorySupport implements OldNameRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcOldNameRepository.class);

	private static final String COUNT_BY_NAME = "SELECT COUNT(`player_id`) FROM `old_names` WHERE `old_name` = ?";
	private static final String INSERT_ONE = "INSERT INTO `old_names` (`player_id`,`old_name`,`new_name`) VALUES (?,?,?)";

	public JdbcOldNameRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public boolean wasUsed(String name) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(COUNT_BY_NAME)) {
			statement.setString(1, name);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() && rows.getInt(1) > 0;
			}
		} catch (SQLException e) {
			// Answer as though the name were taken, which is what the DAO did. The
			// alternative is handing out a name somebody may still be known by.
			log.error("Cannot tell whether the name " + name + " was used before; treating it as taken.", e);
			return true;
		}
	}

	@Override
	public void recordRename(int playerId, String oldName, String newName) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, playerId);
			statement.setString(2, oldName);
			statement.setString(3, newName);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to record the rename of character " + playerId + " from " + oldName + ".", e);
		}
	}
}
