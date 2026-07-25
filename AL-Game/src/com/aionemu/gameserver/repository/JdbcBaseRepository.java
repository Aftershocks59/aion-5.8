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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.base.BaseLocation;

/**
 * Reads and writes who owns each base, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcBaseRepository extends JdbcRepositorySupport implements BaseRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcBaseRepository.class);

	private static final String SELECT_ALL = "SELECT `id`,`race` FROM `base_location`";
	private static final String UPDATE_ONE = "UPDATE `base_location` SET `race` = ? WHERE `id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `base_location` (`id`,`race`) VALUES (?,?)";

	public JdbcBaseRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Map<Integer, BaseLocation> locations) {
		Set<Integer> stored = new HashSet<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				int id = rows.getInt("id");
				BaseLocation location = locations.get(Integer.valueOf(id));
				if (location == null) {
					// A row naming a base the world does not have. The DAO
					// dereferenced this and lost every remaining row to the catch.
					log.warn("Ignoring stored base " + id + ": the world has no such base.");
					continue;
				}
				location.setRace(Race.valueOf(rows.getString("race")));
				stored.add(Integer.valueOf(id));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read who owns the bases.", e);
		}

		// Give every base the world knows a row, so the next save has one to
		// update. One transaction, where the DAO took a connection per base.
		inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
				int queued = 0;
				for (BaseLocation location : locations.values()) {
					if (stored.contains(Integer.valueOf(location.getId()))) {
						continue;
					}
					statement.setInt(1, location.getId());
					statement.setString(2, Race.NPC.toString());
					statement.addBatch();
					queued++;
				}
				if (queued > 0) {
					statement.executeBatch();
				}
			}
			return null;
		}, "Failed to create the missing base rows.");
	}

	@Override
	public boolean save(BaseLocation location) {
		if (location == null) {
			throw new IllegalArgumentException("Cannot store a null base.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setString(1, location.getRace().toString());
			statement.setInt(2, location.getId());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record who owns base " + location.getId() + ".", e);
		}
	}
}
