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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

/**
 * Covers the pets, the minions and the event windows.
 *
 * @author Oraion
 */
class PetAndEventRepositoriesTest {

	private DataSource dataSource;
	private Connection connection;
	private PreparedStatement statement;

	@BeforeEach
	void setUp() throws SQLException {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
	}

	private ResultSet noRows() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);
		return rows;
	}

	@Test
	@DisplayName("Records when a pet may next be summoned")
	void recordsThePetReuseTime() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcPetRepository(dataSource).setReuseTime(42, 5, 1_700_000_000_000L));

		verify(statement).setLong(1, 1_700_000_000_000L);
		verify(statement).setInt(2, 42);
		verify(statement).setInt(3, 5);
	}

	@Test
	@DisplayName("Records how well fed a pet is")
	void recordsThePetFeeding() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcPetRepository(dataSource).saveFeeding(42, 5, 2, 30, 900L));

		verify(statement).setInt(1, 2);
		verify(statement).setInt(2, 30);
		verify(statement).setLong(3, 900L);
	}

	@Test
	@DisplayName("Refuses a null pet")
	void refusesANullPet() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcPetRepository(dataSource).add(null));
	}

	@Test
	@DisplayName("Reports pets it could not read")
	void reportsUnreadablePets() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcPetRepository(dataSource).findAll(42));
		verify(connection).close();
	}

	@Test
	@DisplayName("Asks the database for one minion id rather than every one")
	void asksForOneMinionId() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO read every minion the character keeps and walked them in Java.
		// Its caller does this in a loop to find a free id.
		assertTrue(new JdbcMinionRepository(dataSource).isTaken(42, 700001));

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 700001);
		verify(rows, never()).getInt("object_id");
	}

	@Test
	@DisplayName("Answers that a free minion id is free")
	void answersAFreeMinionId() throws SQLException {
		noRows();

		assertFalse(new JdbcMinionRepository(dataSource).isTaken(42, 700001));
	}

	@Test
	@DisplayName("Locks and unlocks a minion")
	void locksAMinion() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcMinionRepository(dataSource).setLocked(42, 700001, true));
		verify(statement).setInt(1, 1);
	}

	@Test
	@DisplayName("Refuses a null minion")
	void refusesANullMinion() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcMinionRepository(dataSource).add(null));
	}

	@Test
	@DisplayName("Reports minions it could not read")
	void reportsUnreadableMinions() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcMinionRepository(dataSource).findAll(42));
		verify(connection).close();
	}

	@Test
	@DisplayName("Actually runs the statement that closes an event window")
	void closesAnEventWindow() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		// The DAO bound the parameters and never executed the statement, so no
		// event window was ever closed.
		assertTrue(new JdbcEventWindowRepository(dataSource).remove(7, 12));

		verify(statement).setInt(1, 7);
		verify(statement).setInt(2, 12);
		verify(statement).executeUpdate();
	}

	@Test
	@DisplayName("Writes the elapsed time along with the stamp")
	void writesTheElapsedTime() throws SQLException {
		Timestamp at = new Timestamp(1_700_000_000_000L);
		when(statement.executeUpdate()).thenReturn(1);

		// The DAO's upsert refreshed the event id and the stamp but not the elapsed
		// time, so time spent was thrown away on every existing row.
		assertTrue(new JdbcEventWindowRepository(dataSource).save(7, 12, at, 480));

		verify(statement).setInt(1, 7);
		verify(statement).setInt(2, 12);
		verify(statement).setTimestamp(3, at);
		verify(statement).setInt(4, 480);
	}

	@Test
	@DisplayName("Answers nothing rather than the present moment for a window that is not open")
	void answersNothingForAClosedWindow() throws SQLException {
		noRows();

		// The DAO answered the present moment from its catch, which reads as "just
		// touched".
		assertNull(new JdbcEventWindowRepository(dataSource).findLastStamp(7, 12));
	}

	@Test
	@DisplayName("Answers no elapsed time for a window that is not open")
	void answersNoElapsedTime() throws SQLException {
		noRows();

		assertEquals(0, new JdbcEventWindowRepository(dataSource).findElapsed(7, 12));
	}

	@Test
	@DisplayName("Answers no rewards taken for a window that is not open")
	void answersNoRewardsTaken() throws SQLException {
		noRows();

		assertEquals(0, new JdbcEventWindowRepository(dataSource).findRewardCount(7, 12));
	}

	@Test
	@DisplayName("Reports event windows it could not read")
	void reportsUnreadableEventWindows() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcEventWindowRepository(dataSource).findEventIds(7));
		verify(connection).close();
	}
}
