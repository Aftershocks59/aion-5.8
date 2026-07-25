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
import com.aionemu.gameserver.model.gameobjects.player.emotion.Emotion;
import com.aionemu.gameserver.model.gameobjects.player.emotion.EmotionList;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Reads and writes a character's emotes over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerEmotionRepository extends JdbcRepositorySupport implements PlayerEmotionRepository {

	private static final String SELECT_ALL = "SELECT `emotion`,`remaining` FROM `player_emotions` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_emotions` (`player_id`,`emotion`,`remaining`) VALUES (?,?,?)";
	private static final String DELETE_ONE = "DELETE FROM `player_emotions` WHERE `player_id` = ? AND `emotion` = ?";

	public JdbcPlayerEmotionRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		EmotionList emotions = new EmotionList(player);
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					emotions.add(rows.getInt("emotion"), rows.getInt("remaining"), false);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the emotes of character " + player.getObjectId() + ".", e);
		}
		player.setEmotions(emotions);
	}

	@Override
	public void add(Player player, Emotion emotion) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, emotion.getId());
			statement.setInt(3, emotion.getExpireTime());
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to grant the emote " + emotion.getId() + " to character "
					+ player.getObjectId() + ".", e);
		}
	}

	@Override
	public void remove(int playerId, int emotionId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, emotionId);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to take the emote " + emotionId + " from character " + playerId + ".", e);
		}
	}
}
