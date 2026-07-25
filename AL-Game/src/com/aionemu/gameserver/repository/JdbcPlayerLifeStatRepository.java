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
import com.aionemu.gameserver.model.stats.container.PlayerLifeStats;

/**
 * Reads and writes a character's health, mana and flight over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerLifeStatRepository extends JdbcRepositorySupport implements PlayerLifeStatRepository {

	private static final String SELECT_ONE = "SELECT `hp`,`mp`,`fp` FROM `player_life_stats` WHERE `player_id` = ?";

	/**
	 * Writes the stats whether the character had a row or not.
	 * <p>
	 * The DAO kept an insert and an update apart, and picked between them by
	 * whether the load had found anything. The table is keyed on the character, so
	 * one statement settles both and nothing has to remember which case it is in.
	 */
	private static final String UPSERT_ONE = "INSERT INTO `player_life_stats` (`player_id`,`hp`,`mp`,`fp`) "
			+ "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE `hp` = VALUES(`hp`), `mp` = VALUES(`mp`), `fp` = VALUES(`fp`)";

	public JdbcPlayerLifeStatRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		boolean found;
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				found = rows.next();
				if (found) {
					PlayerLifeStats lifeStats = player.getLifeStats();
					lifeStats.setCurrentHp(rows.getInt("hp"));
					lifeStats.setCurrentMp(rows.getInt("mp"));
					lifeStats.setCurrentFp(rows.getInt("fp"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the life stats of character " + player.getObjectId() + ".", e);
		}

		if (!found) {
			// A character that has never been saved starts from whatever it holds.
			save(player);
		}
	}

	@Override
	public void save(Player player) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPSERT_ONE)) {
			PlayerLifeStats lifeStats = player.getLifeStats();
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, lifeStats.getCurrentHp());
			statement.setInt(3, lifeStats.getCurrentMp());
			statement.setInt(4, lifeStats.getCurrentFp());
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to write the life stats of character " + player.getObjectId() + ".", e);
		}
	}
}
