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
import com.aionemu.gameserver.model.siege.SiegeLocation;
import com.aionemu.gameserver.model.siege.SiegeRace;

/**
 * Reads and writes who holds each siege location, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcSiegeRepository extends JdbcRepositorySupport implements SiegeRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcSiegeRepository.class);

	private static final String SELECT_ALL = "SELECT `id`,`race`,`legion_id` FROM `siege_locations`";
	private static final String UPDATE_ONE = "UPDATE `siege_locations` SET `race` = ?, `legion_id` = ? WHERE `id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `siege_locations` (`id`,`race`,`legion_id`) VALUES (?,?,?)";

	public JdbcSiegeRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Map<Integer, SiegeLocation> locations) {
		Set<Integer> stored = new HashSet<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				int id = rows.getInt("id");
				SiegeLocation location = locations.get(Integer.valueOf(id));
				if (location == null) {
					// A row naming a location the world does not have. The DAO
					// dereferenced this and lost every remaining row to the catch,
					// so one stale row left every fortress unowned.
					log.warn("Ignoring stored siege location " + id + ": the world has no such location.");
					continue;
				}
				location.setRace(SiegeRace.valueOf(rows.getString("race")));
				location.setLegionId(rows.getInt("legion_id"));
				stored.add(Integer.valueOf(id));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read who holds the siege locations.", e);
		}

		// Give every location the world knows a row, so the next save has one to
		// update. One transaction, where the DAO took a connection per location.
		inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
				int queued = 0;
				for (SiegeLocation location : locations.values()) {
					if (stored.contains(Integer.valueOf(location.getLocationId()))) {
						continue;
					}
					statement.setInt(1, location.getLocationId());
					statement.setString(2, location.getRace().toString());
					statement.setInt(3, location.getLegionId());
					statement.addBatch();
					queued++;
				}
				if (queued > 0) {
					statement.executeBatch();
				}
			}
			return null;
		}, "Failed to create the missing siege location rows.");
	}

	@Override
	public boolean save(SiegeLocation location) {
		if (location == null) {
			throw new IllegalArgumentException("Cannot store a null siege location.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setString(1, location.getRace().toString());
			statement.setInt(2, location.getLegionId());
			statement.setInt(3, location.getLocationId());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to record who holds siege location " + location.getLocationId() + ".", e);
		}
	}
}
