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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;

/**
 * Covers publishing the server statistics.
 * <p>
 * The DAO closed its statement only on the happy path, so a failing update
 * leaked it, and it swallowed the failure so nothing said the table had stopped
 * being updated.
 *
 * @author Oraion
 */
class JdbcServerStatsRepositoryTest {

	private Connection connection;
	private PreparedStatement statement;
	private JdbcServerStatsRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);

		repository = new JdbcServerStatsRepository(dataSource);
	}

	@Test
	@DisplayName("Publishes an online server with its capacity")
	void publishesOnline() throws SQLException {
		repository.publishOnline(1, 2, 30, 100);

		verify(statement).setInt(1, 2);
		verify(statement).setInt(2, 30);
		verify(statement).setInt(3, 100);
		verify(statement).setInt(4, 1);
	}

	@Test
	@DisplayName("Leaves the capacity alone when a server goes offline")
	void leavesCapacityAloneWhenOffline() throws SQLException {
		repository.publishOffline(1, 0, 0);

		verify(connection).prepareStatement(
				"UPDATE `svstats` SET `status` = ?, `current` = ? WHERE `server` = ?");
	}

	@Test
	@DisplayName("Marks every server offline without naming one")
	void marksEveryServerOffline() throws SQLException {
		repository.publishAllOffline(0, 0);

		// No WHERE clause: this is the shutdown path and covers the whole table.
		verify(connection).prepareStatement("UPDATE `svstats` SET `status` = ?, `current` = ?");
		verify(statement).setInt(1, 0);
		verify(statement).setInt(2, 0);
	}

	@Test
	@DisplayName("Reports an update that failed, naming the server")
	void reportsAFailedUpdate() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("table is locked"));

		RepositoryException failure = assertThrows(RepositoryException.class,
				() -> repository.publishOnline(7, 1, 0, 50));

		assertTrue(failure.getMessage().contains("7"), failure.getMessage());
	}

	@Test
	@DisplayName("Closes the connection even when the update throws")
	void releasesItsResourcesOnFailure() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("table is locked"));

		assertThrows(RepositoryException.class, () -> repository.publishAllOffline(0, 0));

		verify(connection).close();
		verify(statement).close();
	}

	@Test
	@DisplayName("Refuses to be built without a data source")
	void refusesANullDataSource() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcServerStatsRepository(null));
	}

	@Test
	@DisplayName("Names the table it writes")
	void namesItsTable() throws SQLException {
		repository.publishOnline(1, 1, 1, 1);

		verify(connection).prepareStatement(contains("`svstats`"));
	}
}
