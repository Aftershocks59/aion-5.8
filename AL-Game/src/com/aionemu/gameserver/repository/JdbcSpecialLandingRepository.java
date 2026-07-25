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
import com.aionemu.gameserver.model.landing_special.LandingSpecialLocation;
import com.aionemu.gameserver.model.landing_special.LandingSpecialStateType;

/**
 * Reads and writes whether each special landing is active, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcSpecialLandingRepository extends JdbcRepositorySupport implements SpecialLandingRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcSpecialLandingRepository.class);

	private static final String SELECT_ALL = "SELECT `id`,`type` FROM `special_landing`";
	private static final String UPDATE_ONE = "UPDATE `special_landing` SET `type` = ? WHERE `id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `special_landing` (`id`,`type`) VALUES (?,?)";

	public JdbcSpecialLandingRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Map<Integer, LandingSpecialLocation> locations) {
		Set<Integer> stored = new HashSet<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				int id = rows.getInt("id");
				LandingSpecialLocation location = locations.get(Integer.valueOf(id));
				if (location == null) {
					// A row naming a landing the world does not have. The DAO
					// dereferenced this and lost every remaining row to the catch.
					log.warn("Ignoring stored special landing " + id + ": the world has no such landing.");
					continue;
				}
				location.setType(LandingSpecialStateType.valueOf(rows.getString("type")));
				stored.add(Integer.valueOf(id));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the special landings.", e);
		}

		// Give every special landing the world knows a row, so the next save has
		// one to update. One transaction, where the DAO took a connection each.
		inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
				int queued = 0;
				for (LandingSpecialLocation location : locations.values()) {
					if (stored.contains(Integer.valueOf(location.getId()))) {
						continue;
					}
					statement.setInt(1, location.getId());
					statement.setString(2, LandingSpecialStateType.NO_ACTIVE.toString());
					statement.addBatch();
					queued++;
				}
				if (queued > 0) {
					statement.executeBatch();
				}
			}
			return null;
		}, "Failed to create the missing special landing rows.");
	}

	@Override
	public boolean save(LandingSpecialLocation location) {
		if (location == null) {
			throw new IllegalArgumentException("Cannot store a null special landing.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setString(1, location.getType().toString());
			statement.setInt(2, location.getId());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to store special landing " + location.getId() + ".", e);
		}
	}
}
