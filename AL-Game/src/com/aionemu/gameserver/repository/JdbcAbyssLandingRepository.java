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
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.landing.LandingLocation;

/**
 * Reads and writes how far each abyss landing has been built up, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcAbyssLandingRepository extends JdbcRepositorySupport implements AbyssLandingRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcAbyssLandingRepository.class);

	private static final String SELECT_ALL = "SELECT `id`,`level`,`siege`,`commander`,`artefact`,`base`,`monuments`,`quest`,`facility`,`points`"
			+ " FROM `abyss_landing`";
	private static final String UPDATE_ONE = "UPDATE `abyss_landing` SET `level` = ?, `siege` = ?, `commander` = ?,"
			+ " `artefact` = ?, `base` = ?, `monuments` = ?, `quest` = ?, `facility` = ?, `points` = ? WHERE `id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `abyss_landing`"
			+ " (`id`,`level`,`siege`,`commander`,`artefact`,`base`,`monuments`,`quest`,`facility`,`level_up_date`,`race`,`points`)"
			+ " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

	public JdbcAbyssLandingRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Map<Integer, LandingLocation> locations) {
		Set<Integer> stored = new HashSet<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				int id = rows.getInt("id");
				LandingLocation location = locations.get(Integer.valueOf(id));
				if (location == null) {
					// A row naming a landing the world does not have. The DAO
					// dereferenced this and lost every remaining row to the catch.
					log.warn("Ignoring stored abyss landing " + id + ": the world has no such landing.");
					continue;
				}
				location.setLevel(rows.getInt("level"));
				location.setPoints(rows.getInt("points"));
				location.setArtifactPoints(rows.getInt("artefact"));
				location.setBasePoints(rows.getInt("base"));
				location.setCommanderPoints(rows.getInt("commander"));
				location.setQuestPoints(rows.getInt("quest"));
				location.setFacilityPoints(rows.getInt("facility"));
				location.setSiegePoints(rows.getInt("siege"));
				location.setMonumentsPoints(rows.getInt("monuments"));
				stored.add(Integer.valueOf(id));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the abyss landings.", e);
		}

		// Give every landing the world knows a row, so the next save has one to
		// update. One transaction, where the DAO took a connection per landing.
		inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
				int queued = 0;
				for (LandingLocation location : locations.values()) {
					if (stored.contains(Integer.valueOf(location.getId()))) {
						continue;
					}
					statement.setInt(1, location.getId());
					statement.setInt(2, location.getLevel());
					statement.setInt(3, location.getSiegePoints());
					statement.setInt(4, location.getCommanderPoints());
					statement.setInt(5, location.getArtifactPoints());
					statement.setInt(6, location.getBasePoints());
					statement.setInt(7, location.getMonumentsPoints());
					statement.setInt(8, location.getQuestPoints());
					statement.setInt(9, location.getFacilityPoints());
					statement.setTimestamp(10, new Timestamp(System.currentTimeMillis()));
					statement.setString(11, location.getTemplate().getRace().toString());
					statement.setInt(12, location.getPoints());
					statement.addBatch();
					queued++;
				}
				if (queued > 0) {
					statement.executeBatch();
				}
			}
			return null;
		}, "Failed to create the missing abyss landing rows.");
	}

	@Override
	public boolean save(LandingLocation location) {
		if (location == null) {
			throw new IllegalArgumentException("Cannot store a null abyss landing.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setInt(1, location.getLevel());
			statement.setInt(2, location.getSiegePoints());
			statement.setInt(3, location.getCommanderPoints());
			statement.setInt(4, location.getArtifactPoints());
			statement.setInt(5, location.getBasePoints());
			statement.setInt(6, location.getMonumentsPoints());
			statement.setInt(7, location.getQuestPoints());
			statement.setInt(8, location.getFacilityPoints());
			statement.setInt(9, location.getPoints());
			statement.setInt(10, location.getId());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to store abyss landing " + location.getId() + ".", e);
		}
	}
}
