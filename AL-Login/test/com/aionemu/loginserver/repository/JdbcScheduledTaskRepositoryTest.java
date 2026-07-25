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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;

/**
 * Covers reading the scheduled tasks.
 * <p>
 * A row naming a trigger or handler that no longer exists must cost its own task
 * and nothing else. The DAO already skipped those, but it also swallowed a
 * database failure and answered with an empty list, so an outage silently
 * cancelled every schedule.
 *
 * @author Oraion
 */
class JdbcScheduledTaskRepositoryTest {

	private Connection connection;
	private PreparedStatement statement;
	private JdbcScheduledTaskRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);

		repository = new JdbcScheduledTaskRepository(dataSource);
	}

	@Test
	@DisplayName("Answers an empty list when nothing is configured")
	void readsAnEmptyTable() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertTrue(repository.findAll().isEmpty());
	}

	@Test
	@DisplayName("Skips a row naming a trigger that does not exist")
	void skipsAnUnknownTrigger() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("id")).thenReturn(3);
		when(rows.getString("trigger_type")).thenReturn("NO_SUCH_TRIGGER");
		when(statement.executeQuery()).thenReturn(rows);

		// One unusable row must not cost the other schedules, nor throw.
		assertTrue(repository.findAll().isEmpty());
	}

	@Test
	@DisplayName("Reports a read that failed instead of answering empty")
	void reportsAFailedRead() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> repository.findAll());
	}

	@Test
	@DisplayName("Closes the connection even when the query throws")
	void releasesItsResourcesOnFailure() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> repository.findAll());

		verify(connection).close();
		verify(statement).close();
	}

	@Test
	@DisplayName("Reads the tasks in the order they were declared")
	void readsInDeclaredOrder() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		repository.findAll();

		verify(connection).prepareStatement(
				"SELECT `id`,`trigger_type`,`task_type`,`exec_param`,`trigger_param` FROM `tasks` ORDER BY `id`");
	}

	@Test
	@DisplayName("Refuses to be built without a data source")
	void refusesANullDataSource() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcScheduledTaskRepository(null));
	}
}
