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
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.town.Town;

/**
 * Reads and writes how far each town has been developed, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcTownRepository extends JdbcRepositorySupport implements TownRepository {

	private static final String SELECT_BY_RACE = "SELECT `id`,`level`,`points`,`level_up_date` FROM `towns` WHERE `race` = ?";

	/**
	 * Writes a town whether or not it had a row.
	 * <p>
	 * The DAO kept an insert and an update apart and chose between them from an
	 * in-memory flag. The table is keyed on the town.
	 */
	private static final String UPSERT_ONE = "INSERT INTO `towns` (`id`,`level`,`points`,`race`,`level_up_date`) "
			+ "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE `level` = VALUES(`level`), "
			+ "`points` = VALUES(`points`), `level_up_date` = VALUES(`level_up_date`)";

	public JdbcTownRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Map<Integer, Town> findAll(Race race) {
		Map<Integer, Town> towns = new LinkedHashMap<Integer, Town>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_BY_RACE)) {
			statement.setString(1, race.toString());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					int id = rows.getInt("id");
					towns.put(Integer.valueOf(id), new Town(id, rows.getInt("level"), rows.getInt("points"),
							race, rows.getTimestamp("level_up_date")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the towns of the " + race + ".", e);
		}
		return towns;
	}

	@Override
	public void save(Town town) {
		if (town == null) {
			throw new IllegalArgumentException("Cannot store a null town.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPSERT_ONE)) {
			statement.setInt(1, town.getId());
			statement.setInt(2, town.getLevel());
			statement.setInt(3, town.getPoints());
			statement.setString(4, town.getRace().toString());
			statement.setTimestamp(5, town.getLevelUpDate());
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to write town " + town.getId() + ".", e);
		}
	}
}
