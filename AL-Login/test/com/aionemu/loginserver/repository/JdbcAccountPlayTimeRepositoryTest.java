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
 * Covers recording accumulated play time.
 * <p>
 * The DAO this replaces pasted the account id and the duration straight into the
 * statement text, so every call handed the database a statement it had never
 * seen and could not reuse, and it answered false on failure without saying why.
 *
 * @author Oraion
 */
class JdbcAccountPlayTimeRepositoryTest {

	private Connection connection;
	private PreparedStatement statement;
	private JdbcAccountPlayTimeRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);

		repository = new JdbcAccountPlayTimeRepository(dataSource);
	}

	@Test
	@DisplayName("Passes the account and the duration as parameters")
	void bindsItsValues() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(repository.accumulate(42, 3_600L));

		verify(statement).setInt(1, 42);
		// Bound twice: once for the insert, once for the addition on conflict.
		verify(statement).setLong(2, 3_600L);
		verify(statement).setLong(3, 3_600L);
	}

	@Test
	@DisplayName("Adds to the running total rather than replacing it")
	void addsToTheTotal() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		repository.accumulate(42, 60L);

		verify(connection).prepareStatement(contains("`accumulated_online` = `accumulated_online` + ?"));
	}

	@Test
	@DisplayName("Reports a write that failed")
	void reportsAFailedWrite() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("disk full"));

		RepositoryException failure = assertThrows(RepositoryException.class, () -> repository.accumulate(42, 1L));

		assertTrue(failure.getMessage().contains("42"), failure.getMessage());
	}

	@Test
	@DisplayName("Closes the connection even when the write throws")
	void releasesItsResourcesOnFailure() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("disk full"));

		assertThrows(RepositoryException.class, () -> repository.accumulate(42, 1L));

		verify(connection).close();
		verify(statement).close();
	}

	@Test
	@DisplayName("Refuses to be built without a data source")
	void refusesANullDataSource() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcAccountPlayTimeRepository(null));
	}
}
