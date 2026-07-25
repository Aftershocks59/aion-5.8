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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.sql.Timestamp;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.model.AccountTime;

/**
 * Covers reading and writing the account timings.
 * <p>
 * The DAO answered null both when an account had no row and when the query
 * failed. A caller checking a penalty end could not tell "never punished" from
 * "could not ask", and the safe reading of those two is not the same.
 *
 * @author Oraion
 */
class JdbcAccountTimeRepositoryTest {

	private Connection connection;
	private PreparedStatement statement;
	private JdbcAccountTimeRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);

		repository = new JdbcAccountTimeRepository(dataSource);
	}

	@Test
	@DisplayName("Maps a stored row onto the timings")
	void mapsAStoredRow() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getTimestamp("last_active")).thenReturn(new Timestamp(1_000L));
		when(rows.getTimestamp("expiration_time")).thenReturn(new Timestamp(2_000L));
		when(rows.getLong("session_duration")).thenReturn(30L);
		when(rows.getLong("accumulated_online")).thenReturn(600L);
		when(rows.getLong("accumulated_rest")).thenReturn(120L);
		when(rows.getTimestamp("penalty_end")).thenReturn(new Timestamp(3_000L));
		when(statement.executeQuery()).thenReturn(rows);

		AccountTime found = repository.find(42);

		assertNotNull(found);
		assertEquals(30L, found.getSessionDuration());
		assertEquals(600L, found.getAccumulatedOnlineTime());
		assertEquals(120L, found.getAccumulatedRestTime());
		assertEquals(new Timestamp(3_000L), found.getPenaltyEnd());
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Answers nothing when the account has no row")
	void answersNothingWhenAbsent() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertNull(repository.find(42));
	}

	@Test
	@DisplayName("Reports a read that failed rather than answering nothing")
	void reportsAFailedRead() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		RepositoryException failure = assertThrows(RepositoryException.class, () -> repository.find(42));

		assertTrue(failure.getMessage().contains("42"), failure.getMessage());
	}

	@Test
	@DisplayName("Writes every timing column")
	void writesEveryColumn() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);
		AccountTime accountTime = new AccountTime();
		accountTime.setLastLoginTime(new Timestamp(1_000L));
		accountTime.setExpirationTime(new Timestamp(2_000L));
		accountTime.setSessionDuration(30L);
		accountTime.setAccumulatedOnlineTime(600L);
		accountTime.setAccumulatedRestTime(120L);
		accountTime.setPenaltyEnd(new Timestamp(3_000L));

		assertTrue(repository.save(42, accountTime));

		verify(statement).setInt(1, 42);
		verify(statement).setTimestamp(2, new Timestamp(1_000L));
		verify(statement).setTimestamp(3, new Timestamp(2_000L));
		verify(statement).setLong(4, 30L);
		verify(statement).setLong(5, 600L);
		verify(statement).setLong(6, 120L);
		verify(statement).setTimestamp(7, new Timestamp(3_000L));
	}

	@Test
	@DisplayName("Refuses to store null timings")
	void refusesNullTimings() {
		assertThrows(IllegalArgumentException.class, () -> repository.save(42, null));
	}

	@Test
	@DisplayName("Closes the connection even when the read throws")
	void releasesItsResourcesOnFailure() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> repository.find(42));

		verify(connection).close();
		verify(statement).close();
	}

	@Test
	@DisplayName("Names the columns it reads")
	void namesItsColumns() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		repository.find(42);

		// The DAO read SELECT *, so a column added later would arrive unnoticed.
		verify(connection).prepareStatement("SELECT `last_active`,`expiration_time`,`session_duration`,"
				+ "`accumulated_online`,`accumulated_rest`,`penalty_end` FROM `account_time` WHERE `account_id` = ?");
	}
}
