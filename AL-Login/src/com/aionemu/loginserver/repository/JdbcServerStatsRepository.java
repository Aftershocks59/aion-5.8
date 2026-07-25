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
package com.aionemu.loginserver.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;

/**
 * Publishes the server statistics over JDBC.
 *
 * @author Oraion
 */
public final class JdbcServerStatsRepository extends JdbcRepositorySupport implements ServerStatsRepository {

	private static final String UPDATE_ONLINE = "UPDATE `svstats` SET `status` = ?, `current` = ?, `max` = ? WHERE `server` = ?";
	private static final String UPDATE_OFFLINE = "UPDATE `svstats` SET `status` = ?, `current` = ? WHERE `server` = ?";
	private static final String UPDATE_ALL_OFFLINE = "UPDATE `svstats` SET `status` = ?, `current` = ?";

	public JdbcServerStatsRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void publishOnline(int serverId, int status, int current, int maximum) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONLINE)) {
			statement.setInt(1, status);
			statement.setInt(2, current);
			statement.setInt(3, maximum);
			statement.setInt(4, serverId);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to publish the statistics of server " + serverId + ".", e);
		}
	}

	@Override
	public void publishOffline(int serverId, int status, int current) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_OFFLINE)) {
			statement.setInt(1, status);
			statement.setInt(2, current);
			statement.setInt(3, serverId);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to publish server " + serverId + " as offline.", e);
		}
	}

	@Override
	public void publishAllOffline(int status, int current) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ALL_OFFLINE)) {
			statement.setInt(1, status);
			statement.setInt(2, current);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to publish every server as offline.", e);
		}
	}
}
