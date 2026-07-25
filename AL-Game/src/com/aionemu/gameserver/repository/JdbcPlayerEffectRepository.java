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
import java.util.Iterator;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.model.Effect;

/**
 * Reads and writes the effects a character is under, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerEffectRepository extends JdbcRepositorySupport implements PlayerEffectRepository {

	/** Keeps an effect out of the database when it is nearly over, as the DAO did. */
	private static final int WORTH_STORING_MILLIS = 28_000;

	private static final String SELECT_ALL = "SELECT `skill_id`,`skill_lvl`,`current_time`,`end_time` "
			+ "FROM `player_effects` WHERE `player_id` = ?";
	private static final String DELETE_ALL = "DELETE FROM `player_effects` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_effects` "
			+ "(`player_id`,`skill_id`,`skill_lvl`,`current_time`,`end_time`) VALUES (?,?,?,?,?)";

	public JdbcPlayerEffectRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					int remaining = rows.getInt("current_time");
					if (remaining > 0) {
						player.getEffectController().addSavedEffect(rows.getInt("skill_id"),
								rows.getInt("skill_lvl"), remaining, rows.getLong("end_time"));
					}
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the effects of character " + player.getObjectId() + ".", e);
		}
		player.getEffectController().broadCastEffects();
	}

	@Override
	public void store(Player player) {
		// The delete and the inserts belong together: the DAO ran them on separate
		// connections, so a failure between the two left the character with none.
		inTransaction(connection -> {
			try (PreparedStatement delete = connection.prepareStatement(DELETE_ALL)) {
				delete.setInt(1, player.getObjectId());
				delete.executeUpdate();
			}

			try (PreparedStatement insert = connection.prepareStatement(INSERT_ONE)) {
				int queued = 0;
				for (Iterator<Effect> effects = player.getEffectController().iterator(); effects.hasNext();) {
					Effect effect = effects.next();
					if (effect == null || effect.getRemainingTime() <= WORTH_STORING_MILLIS) {
						continue;
					}
					insert.setInt(1, player.getObjectId());
					insert.setInt(2, effect.getSkillId());
					insert.setInt(3, effect.getSkillLevel());
					insert.setInt(4, effect.getRemainingTime());
					insert.setLong(5, effect.getEndTime());
					insert.addBatch();
					queued++;
				}
				if (queued > 0) {
					insert.executeBatch();
				}
			}
			return null;
		}, "Failed to write the effects of character " + player.getObjectId() + ".");
	}
}
