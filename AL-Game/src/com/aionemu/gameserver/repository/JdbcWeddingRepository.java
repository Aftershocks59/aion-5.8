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

/**
 * Reads and writes who is married to whom, over JDBC.
 * <p>
 * A marriage is one row naming both partners, in whichever order it was
 * recorded, so every query has to look at both columns.
 *
 * @author Oraion
 */
public final class JdbcWeddingRepository extends JdbcRepositorySupport implements WeddingRepository {

	private static final String SELECT_BY_PARTNER = "SELECT `player1`,`player2` FROM `weddings` "
			+ "WHERE `player1` = ? OR `player2` = ?";
	private static final String INSERT_ONE = "INSERT INTO `weddings` (`player1`,`player2`) VALUES (?,?)";
	private static final String DELETE_ONE = "DELETE FROM `weddings` WHERE (`player1` = ? AND `player2` = ?) "
			+ "OR (`player2` = ? AND `player1` = ?)";

	public JdbcWeddingRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public int findPartner(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_BY_PARTNER)) {
			statement.setInt(1, playerId);
			statement.setInt(2, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return NO_PARTNER;
				}
				int first = rows.getInt("player1");
				int second = rows.getInt("player2");
				// The row names both partners; the one that is not the asker is the
				// answer.
				return playerId == first ? second : first;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the partner of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean marry(int firstId, int secondId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, firstId);
			statement.setInt(2, secondId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to marry characters " + firstId + " and " + secondId + ".", e);
		}
	}

	@Override
	public boolean divorce(int firstId, int secondId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, firstId);
			statement.setInt(2, secondId);
			statement.setInt(3, firstId);
			statement.setInt(4, secondId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to divorce characters " + firstId + " and " + secondId + ".", e);
		}
	}
}
