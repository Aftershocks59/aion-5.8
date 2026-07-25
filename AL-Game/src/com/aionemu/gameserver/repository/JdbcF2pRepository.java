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
import com.aionemu.gameserver.model.gameobjects.player.f2p.F2p;
import com.aionemu.gameserver.model.gameobjects.player.f2p.F2pAccount;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Reads and writes the free-to-play time a character has left, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcF2pRepository extends JdbcRepositorySupport implements F2pRepository {

	private static final String SELECT_ONE = "SELECT `time` FROM `f2paccount` WHERE `player_id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `f2paccount` WHERE `player_id` = ?";

	/**
	 * Writes the remaining time whether the character had a row or not.
	 * <p>
	 * The DAO kept an insert and an update apart, and its update never worked: the
	 * statement takes the time and the character, and only the time was ever bound.
	 * Every call threw for the missing parameter, was swallowed, and answered
	 * false. One upsert settles both, with both values bound.
	 */
	private static final String UPSERT_ONE = "INSERT INTO `f2paccount` (`player_id`,`time`) VALUES (?,?) "
			+ "ON DUPLICATE KEY UPDATE `time` = VALUES(`time`)";

	public JdbcF2pRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		F2p f2p = new F2p(player);
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					f2p.add(new F2pAccount(rows.getInt("time")), false);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the free-to-play time of character " + player.getObjectId() + ".", e);
		}
		player.setF2p(f2p);
	}

	@Override
	public boolean save(int playerId, int time) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPSERT_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, time);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to write the free-to-play time of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean remove(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to forget the free-to-play time of character " + playerId + ".", e);
		}
	}
}
