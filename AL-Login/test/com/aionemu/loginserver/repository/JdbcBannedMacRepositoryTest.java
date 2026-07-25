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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.model.base.BannedMacEntry;

/**
 * Covers reading and writing the MAC bans.
 * <p>
 * The DAO this replaces caught every SQLException and answered with an empty map
 * or false. A database that never answered therefore looked exactly like a table
 * with no bans, so an outage silently let in every address the list existed to
 * keep out. These pin down that a failure is now reported.
 *
 * @author Oraion
 */
class JdbcBannedMacRepositoryTest {

	private DataSource dataSource;
	private Connection connection;
	private PreparedStatement statement;
	private JdbcBannedMacRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);

		repository = new JdbcBannedMacRepository(dataSource);
	}

	/** Builds a result set holding exactly two bans. */
	private ResultSet twoRows() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getString("address")).thenReturn("00-11-22-33-44-55", "AA-BB-CC-DD-EE-FF");
		when(rows.getTimestamp("time")).thenReturn(new Timestamp(0L));
		when(rows.getString("details")).thenReturn("cheating");
		return rows;
	}

	@Test
	@DisplayName("Refuses to be built without a data source")
	void refusesANullDataSource() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcBannedMacRepository(null));
	}

	@Test
	@DisplayName("Reads every ban, keyed by address")
	void readsEveryBan() throws SQLException {
		// Build the rows first: stubbing inside a thenReturn argument leaves Mockito
		// with an unfinished stub.
		ResultSet rows = twoRows();
		when(statement.executeQuery()).thenReturn(rows);

		Map<String, BannedMacEntry> bans = repository.findAll();

		assertEquals(2, bans.size());
		assertTrue(bans.containsKey("00-11-22-33-44-55"));
		assertTrue(bans.containsKey("AA-BB-CC-DD-EE-FF"));
		assertEquals("cheating", bans.get("00-11-22-33-44-55").getDetails());
	}

	@Test
	@DisplayName("Answers an empty list when nothing is banned")
	void readsAnEmptyTable() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(Boolean.FALSE);
		when(statement.executeQuery()).thenReturn(rows);

		assertTrue(repository.findAll().isEmpty());
	}

	@Test
	@DisplayName("Reports a read that failed instead of answering empty")
	void reportsAFailedRead() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		RepositoryException failure = assertThrows(RepositoryException.class, () -> repository.findAll());

		assertTrue(failure.getMessage().contains("read"), failure.getMessage());
	}

	@Test
	@DisplayName("Writes a ban with its address, end date and reason")
	void writesABan() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);
		BannedMacEntry entry = new BannedMacEntry("00-11-22-33-44-55", new Timestamp(1_000L), "cheating");

		assertTrue(repository.save(entry));

		verify(statement).setString(1, "00-11-22-33-44-55");
		verify(statement).setTimestamp(2, new Timestamp(1_000L));
		verify(statement).setString(3, "cheating");
	}

	@Test
	@DisplayName("Reports a ban that could not be written")
	void reportsAFailedWrite() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("table is read only"));
		BannedMacEntry entry = new BannedMacEntry("00-11-22-33-44-55", new Timestamp(0L), "cheating");

		RepositoryException failure = assertThrows(RepositoryException.class, () -> repository.save(entry));

		assertTrue(failure.getMessage().contains("00-11-22-33-44-55"), failure.getMessage());
	}

	@Test
	@DisplayName("Refuses to store a null ban")
	void refusesANullBan() {
		assertThrows(IllegalArgumentException.class, () -> repository.save(null));
	}

	@Test
	@DisplayName("Says whether a ban was actually lifted")
	void reportsWhetherRemovalMatched() throws SQLException {
		when(statement.executeUpdate()).thenReturn(0);

		assertFalse(repository.remove("00-11-22-33-44-55"));
		verify(statement).setString(eq(1), eq("00-11-22-33-44-55"));
	}

	@Test
	@DisplayName("Reports a removal that failed")
	void reportsAFailedRemoval() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("deadlock"));

		assertThrows(RepositoryException.class, () -> repository.remove("00-11-22-33-44-55"));
	}

	@Test
	@DisplayName("Counts the expired bans it dropped")
	void countsExpiredBansRemoved() throws SQLException {
		when(statement.executeUpdate()).thenReturn(7);

		assertEquals(7, repository.removeExpired());
	}

	@Test
	@DisplayName("Closes the connection and the statement every time")
	void releasesItsResources() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(Boolean.FALSE);
		when(statement.executeQuery()).thenReturn(rows);

		repository.findAll();
		repository.removeExpired();

		// A leaked connection drains the pool and strands the server a few hours in.
		verify(connection, times(2)).close();
		verify(statement, times(2)).close();
	}

	@Test
	@DisplayName("Closes the connection even when the query throws")
	void releasesItsResourcesOnFailure() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("deadlock"));

		assertThrows(RepositoryException.class, () -> repository.removeExpired());

		verify(connection).close();
		verify(statement).close();
	}

	@Test
	@DisplayName("Reports a pool that cannot hand out a connection")
	void reportsAnExhaustedPool() throws SQLException {
		when(dataSource.getConnection()).thenThrow(new SQLException("pool exhausted"));

		assertThrows(RepositoryException.class, () -> repository.findAll());
		verify(statement, times(0)).setString(anyInt(), anyString());
	}
}
