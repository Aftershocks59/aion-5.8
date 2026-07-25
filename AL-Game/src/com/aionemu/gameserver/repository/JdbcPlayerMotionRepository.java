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
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.motion.Motion;
import com.aionemu.gameserver.model.gameobjects.player.motion.MotionList;

/**
 * Reads and writes a character's movement styles over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerMotionRepository extends JdbcRepositorySupport implements PlayerMotionRepository {

	private static final String SELECT_ALL = "SELECT `motion_id`,`active`,`time` FROM `player_motions` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_motions` (`player_id`,`motion_id`,`active`,`time`) VALUES (?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `player_motions` SET `active` = ? WHERE `player_id` = ? AND `motion_id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `player_motions` WHERE `player_id` = ? AND `motion_id` = ?";

	public JdbcPlayerMotionRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		MotionList motions = new MotionList(player);
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					motions.add(new Motion(rows.getInt("motion_id"), rows.getInt("time"),
							rows.getBoolean("active")), false);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the motions of character " + player.getObjectId() + ".", e);
		}
		player.setMotions(motions);
	}

	@Override
	public boolean add(int playerId, Motion motion) {
		if (motion == null) {
			throw new IllegalArgumentException("Cannot store a null motion.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, motion.getId());
			statement.setBoolean(3, motion.isActive());
			statement.setInt(4, motion.getExpireTime());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to grant motion " + motion.getId() + " to character " + playerId + ".", e);
		}
	}

	@Override
	public boolean update(int playerId, Motion motion) {
		if (motion == null) {
			throw new IllegalArgumentException("Cannot update a null motion.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setBoolean(1, motion.isActive());
			statement.setInt(2, playerId);
			statement.setInt(3, motion.getId());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to update motion " + motion.getId() + " of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean remove(int playerId, int motionId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, motionId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to take motion " + motionId + " from character " + playerId + ".", e);
		}
	}
}
