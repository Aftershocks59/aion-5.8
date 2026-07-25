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
import com.aionemu.gameserver.model.gameobjects.player.title.Title;
import com.aionemu.gameserver.model.gameobjects.player.title.TitleList;

/**
 * Reads and writes the titles a character has earned, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerTitleRepository extends JdbcRepositorySupport implements PlayerTitleRepository {

	private static final String SELECT_ALL = "SELECT `title_id`,`remaining` FROM `player_titles` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_titles` (`player_id`,`title_id`,`remaining`) VALUES (?,?,?)";
	private static final String DELETE_ONE = "DELETE FROM `player_titles` WHERE `player_id` = ? AND `title_id` = ?";

	public JdbcPlayerTitleRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public TitleList findAll(int playerId) {
		TitleList titles = new TitleList();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					titles.addEntry(rows.getInt("title_id"), rows.getInt("remaining"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the titles of character " + playerId + ".", e);
		}
		return titles;
	}

	@Override
	public boolean add(int playerId, Title title) {
		if (title == null) {
			throw new IllegalArgumentException("Cannot grant a null title.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, title.getId());
			statement.setInt(3, title.getExpireTime());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to grant title " + title.getId() + " to character " + playerId + ".", e);
		}
	}

	@Override
	public boolean remove(int playerId, int titleId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, titleId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to take title " + titleId + " from character " + playerId + ".", e);
		}
	}
}
