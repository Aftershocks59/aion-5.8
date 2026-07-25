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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.team.legion.LegionJoinRequestState;

/**
 * Covers the characters themselves.
 *
 * @author Oraion
 */
class PlayerRepositoryTest {

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

	private void noRows() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);
	}

	private JdbcPlayerRepository players() {
		return new JdbcPlayerRepository(dataSource);
	}

	@Test
	@DisplayName("Actually runs the statement that forgets a legion request")
	void forgetsALegionRequest() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		// The DAO prepared this statement, bound its parameters and never ran it,
		// so a character's request to join a legion was never cleared.
		assertTrue(players().clearJoinRequest(42));

		verify(statement).setInt(1, 42);
		verify(statement).executeUpdate();
	}

	@Test
	@DisplayName("Asks the database for one character name rather than a count")
	void asksForOneName() throws SQLException {
		noRows();

		assertFalse(players().isNameUsed("Nobody"));
		verify(statement).setString(1, "Nobody");
	}

	@Test
	@DisplayName("Treats a name it could not check as taken")
	void treatsADoubtfulNameAsTaken() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertTrue(players().isNameUsed("Nobody"));
	}

	@Test
	@DisplayName("Binds one placeholder per character when reading names in bulk")
	void bindsOnePlaceholderPerName() throws SQLException {
		noRows();

		Map<Integer, String> names = players()
				.findNames(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3)));

		// The DAO spliced the ids straight into the statement.
		assertEquals(0, names.size());
		verify(connection).prepareStatement("SELECT `id`,`name` FROM `players` WHERE `id` IN (?,?,?)");
		verify(statement, times(3)).setInt(org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	@DisplayName("Reads no names for no characters")
	void readsNoNamesForNobody() throws SQLException {
		assertEquals(0, players().findNames(List.of()).size());

		verify(dataSource, never()).getConnection();
	}

	@Test
	@DisplayName("Answers nothing for a character who does not exist")
	void answersNothingForAMissingCharacter() throws SQLException {
		noRows();

		assertNull(players().load(9999));
	}

	@Test
	@DisplayName("Reports a character it could not read")
	void reportsAnUnreadableCharacter() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		// The DAO caught this and did nothing at all, not even a log line, so a
		// character whose read had failed looked as though they had never existed.
		assertThrows(RepositoryException.class, () -> players().load(42));
		verify(connection).close();
	}

	@Test
	@DisplayName("Answers no name for a character who does not exist")
	void answersNoNameForAMissingCharacter() throws SQLException {
		noRows();

		assertNull(players().findName(9999));
	}

	@Test
	@DisplayName("Answers no character for a name nobody carries")
	void answersNoCharacterForAnUnknownName() throws SQLException {
		noRows();

		assertEquals(PlayerRepository.NO_CHARACTER, players().findIdByName("Nobody"));
	}

	@Test
	@DisplayName("Answers no account for a character who does not exist")
	void answersNoAccountForAMissingCharacter() throws SQLException {
		noRows();

		assertEquals(PlayerRepository.NO_CHARACTER, players().findAccountIdByName("Nobody"));
	}

	@Test
	@DisplayName("Walks the used character ids forward")
	void walksTheCharacterIds() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("id")).thenReturn(75001, 75002);
		when(statement.executeQuery()).thenReturn(rows);

		assertArrayEquals(new int[] { 75001, 75002 }, players().findUsedIds());
		verify(rows, never()).last();
	}

	@Test
	@DisplayName("Answers an empty list for an account with no character")
	void answersAnEmptyAccount() throws SQLException {
		noRows();

		// The DAO answered null when the read failed.
		assertEquals(0, players().findIdsOnAccount(9001).size());
	}

	@Test
	@DisplayName("Limits the inactive characters when asked to")
	void limitsTheInactiveCharacters() throws SQLException {
		noRows();

		players().findInactive(90, 500);

		verify(connection).prepareStatement(org.mockito.ArgumentMatchers.endsWith("LIMIT 500"));
		verify(statement).setInt(1, 90);
	}

	@Test
	@DisplayName("Records whether one character is online")
	void recordsOneCharacterOnline() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(players().setOnline(42, true));

		verify(statement).setBoolean(1, true);
		verify(statement).setInt(2, 42);
	}

	@Test
	@DisplayName("Records every character as offline at once")
	void recordsEveryCharacterOffline() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1200);

		assertEquals(1200, players().setAllOnline(false));
		verify(statement).setBoolean(1, false);
	}

	@Test
	@DisplayName("Records where a character stands on joining a legion")
	void recordsTheLegionRequestState() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(players().setJoinRequestState(42, LegionJoinRequestState.ACCEPTED));

		verify(statement).setString(1, LegionJoinRequestState.ACCEPTED.name());
		verify(statement).setInt(2, 42);
	}

	@Test
	@DisplayName("Records when a character is due to go")
	void recordsTheDeletionTime() throws SQLException {
		Timestamp at = new Timestamp(1_700_000_000_000L);
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(players().setDeletionTime(42, at));

		verify(statement).setTimestamp(1, at);
		verify(statement).setInt(2, 42);
	}

	@Test
	@DisplayName("Refuses to store a null character")
	void refusesANullCharacter() {
		assertThrows(IllegalArgumentException.class, () -> players().save(null));
	}

	@Test
	@DisplayName("Refuses to create a null character")
	void refusesToCreateANullCharacter() {
		assertThrows(IllegalArgumentException.class, () -> players().add(null, 9001, "someone"));
	}
}
