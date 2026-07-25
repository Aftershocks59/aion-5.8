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
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.guide.Guide;

/**
 * Reads and writes the guide notes over JDBC.
 *
 * @author Oraion
 */
public final class JdbcGuideRepository extends JdbcRepositorySupport implements GuideRepository {

	private static final String SELECT_BY_PLAYER = "SELECT `guide_id`,`player_id`,`title` FROM `guides` WHERE `player_id` = ?";
	private static final String SELECT_ONE = "SELECT `guide_id`,`player_id`,`title` FROM `guides` WHERE `guide_id` = ? AND `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `guides` (`guide_id`,`title`,`player_id`) VALUES (?,?,?)";
	private static final String DELETE_ONE = "DELETE FROM `guides` WHERE `guide_id` = ?";
	private static final String SELECT_IDS = "SELECT `guide_id` FROM `guides`";

	public JdbcGuideRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public List<Guide> findAll(int playerId) {
		List<Guide> guides = new ArrayList<Guide>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_BY_PLAYER)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					guides.add(new Guide(rows.getInt("guide_id"), rows.getInt("player_id"),
							rows.getString("title")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the guide notes of character " + playerId + ".", e);
		}
		return guides;
	}

	@Override
	public Guide find(int playerId, int guideId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, guideId);
			statement.setInt(2, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next()
						? new Guide(guideId, playerId, rows.getString("title"))
						: null;
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read guide note " + guideId + " of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean save(int guideId, Player player, String title) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, guideId);
			statement.setString(2, title);
			statement.setInt(3, player.getObjectId());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to send guide note " + guideId + " to character " + player.getObjectId() + ".", e);
		}
	}

	@Override
	public boolean remove(int guideId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, guideId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to remove guide note " + guideId + ".", e);
		}
	}

	/**
	 * Lists the ids already taken.
	 * <p>
	 * The DAO asked for a scrollable cursor so it could count the rows before
	 * reading them, which costs the driver a second pass. A growing list needs
	 * neither.
	 */
	@Override
	public int[] findUsedIds() {
		List<Integer> ids = new ArrayList<Integer>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_IDS);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				ids.add(Integer.valueOf(rows.getInt("guide_id")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to list the guide note ids already in use.", e);
		}

		int[] used = new int[ids.size()];
		for (int i = 0; i < used.length; i++) {
			used[i] = ids.get(i).intValue();
		}
		return used;
	}
}
