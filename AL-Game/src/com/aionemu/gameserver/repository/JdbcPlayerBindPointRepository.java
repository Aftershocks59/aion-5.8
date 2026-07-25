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
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.BindPointPosition;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Reads and writes where a character comes back to life, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerBindPointRepository extends JdbcRepositorySupport
		implements PlayerBindPointRepository {

	private static final String SELECT_ONE = "SELECT `map_id`,`x`,`y`,`z`,`heading` "
			+ "FROM `player_bind_point` WHERE `player_id` = ?";

	/**
	 * Writes the bind point whether the character had one or not.
	 * <p>
	 * The DAO kept an insert and an update apart and chose between them from an
	 * in-memory flag. The table is keyed on the character, so one statement covers
	 * both.
	 */
	private static final String REPLACE_ONE = "REPLACE INTO `player_bind_point` "
			+ "(`player_id`,`map_id`,`x`,`y`,`z`,`heading`) VALUES (?,?,?,?,?,?)";

	public JdbcPlayerBindPointRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) {
					BindPointPosition bindPoint = new BindPointPosition(rows.getInt("map_id"), rows.getFloat("x"),
							rows.getFloat("y"), rows.getFloat("z"), rows.getByte("heading"));
					bindPoint.setPersistentState(PersistentState.UPDATED);
					player.setBindPoint(bindPoint);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the bind point of character " + player.getObjectId() + ".", e);
		}
	}

	@Override
	public boolean store(Player player) {
		BindPointPosition bindPoint = player.getBindPoint();
		if (bindPoint == null || bindPoint.getPersistentState() == PersistentState.UPDATED) {
			return false;
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(REPLACE_ONE)) {
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, bindPoint.getMapId());
			statement.setFloat(3, bindPoint.getX());
			statement.setFloat(4, bindPoint.getY());
			statement.setFloat(5, bindPoint.getZ());
			statement.setByte(6, bindPoint.getHeading());
			boolean written = statement.executeUpdate() > 0;
			bindPoint.setPersistentState(PersistentState.UPDATED);
			return written;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to write the bind point of character " + player.getObjectId() + ".", e);
		}
	}
}
