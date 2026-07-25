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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;

/**
 * Covers the four small per-character stores: old names, loose variables, emotes
 * and macros.
 * <p>
 * Two DAO defects are settled here. Setting a variable used a plain insert
 * against a table keyed on the character and the name together, so a second set
 * hit the key, the error was swallowed and the value never changed. And the old
 * name check answers true when it cannot tell, which is deliberate and stays.
 *
 * @author Oraion
 */
class PlayerDataRepositoriesTest {

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

	private void stubCount(int count) throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getInt(1)).thenReturn(count);
		when(statement.executeQuery()).thenReturn(rows);
	}

	@Test
	@DisplayName("Says a name was worn before")
	void recognisesAnOldName() throws SQLException {
		stubCount(1);

		assertTrue(new JdbcOldNameRepository(dataSource).wasUsed("Maeron"));
		verify(statement).setString(1, "Maeron");
	}

	@Test
	@DisplayName("Says a name is free when nothing matches")
	void recognisesAFreeName() throws SQLException {
		stubCount(0);

		assertFalse(new JdbcOldNameRepository(dataSource).wasUsed("Maeron"));
	}

	@Test
	@DisplayName("Treats a name as taken when the question cannot be answered")
	void failsSafeOnAnUnansweredQuestion() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		// Deliberate, and inherited: refusing a rename costs a retry, while allowing
		// one hands out a name somebody may still be known by.
		assertTrue(new JdbcOldNameRepository(dataSource).wasUsed("Maeron"));
	}

	@Test
	@DisplayName("Reports a rename it could not record")
	void reportsAFailedRename() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("constraint violated"));

		assertThrows(RepositoryException.class,
				() -> new JdbcOldNameRepository(dataSource).recordRename(42, "Old", "New"));
	}

	@Test
	@DisplayName("Overwrites a variable the character already had")
	void overwritesAnExistingVariable() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcPlayerVariableRepository(dataSource).set(42, "tutorial", "done"));

		// A plain insert answered false here and left the old value in place.
		verify(connection).prepareStatement(contains("ON DUPLICATE KEY UPDATE"));
		verify(statement).setString(2, "tutorial");
		verify(statement).setString(3, "done");
	}

	@Test
	@DisplayName("Stores a null variable as a null, not as the word null")
	void storesANullVariable() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		new JdbcPlayerVariableRepository(dataSource).set(42, "tutorial", null);

		verify(statement).setString(3, null);
	}

	@Test
	@DisplayName("Refuses a variable with no name")
	void refusesANamelessVariable() {
		assertThrows(IllegalArgumentException.class,
				() -> new JdbcPlayerVariableRepository(dataSource).set(42, null, "x"));
	}

	@Test
	@DisplayName("Reads every variable a character holds")
	void readsEveryVariable() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getString("param")).thenReturn("tutorial", "wardrobe");
		when(rows.getString("value")).thenReturn("done", "3");
		when(statement.executeQuery()).thenReturn(rows);

		Map<String, Object> variables = new JdbcPlayerVariableRepository(dataSource).findAll(42);

		assertEquals(2, variables.size());
		assertEquals("done", variables.get("tutorial"));
	}

	@Test
	@DisplayName("Reports variables it could not read")
	void reportsUnreadableVariables() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcPlayerVariableRepository(dataSource).findAll(42));
		verify(connection).close();
	}

	@Test
	@DisplayName("Takes an emote away from one character only")
	void removesOneEmote() throws SQLException {
		new JdbcPlayerEmotionRepository(dataSource).remove(42, 7);

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 7);
		verify(connection).prepareStatement(
				"DELETE FROM `player_emotions` WHERE `player_id` = ? AND `emotion` = ?");
	}

	@Test
	@DisplayName("Reports an emote it could not take away")
	void reportsAFailedEmoteRemoval() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("deadlock"));

		assertThrows(RepositoryException.class, () -> new JdbcPlayerEmotionRepository(dataSource).remove(42, 7));
	}

	@Test
	@DisplayName("Reads the macros with their slot")
	void readsMacrosWithTheirSlot() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("order")).thenReturn(2);
		when(rows.getString("macro")).thenReturn("<macro/>");
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(1, new JdbcPlayerMacroRepository(dataSource).findAll(42).getMacrosses().size());
	}

	@Test
	@DisplayName("Quotes the slot column, which is a reserved word")
	void quotesTheReservedColumn() throws SQLException {
		new JdbcPlayerMacroRepository(dataSource).remove(42, 2);

		verify(connection).prepareStatement(contains("`order`"));
	}

	@Test
	@DisplayName("Binds the macro text last when storing, first when replacing")
	void bindsInTheRightOrder() throws SQLException {
		JdbcPlayerMacroRepository repository = new JdbcPlayerMacroRepository(dataSource);

		repository.add(42, 2, "<stored/>");
		verify(statement).setString(3, "<stored/>");

		repository.update(42, 2, "<replaced/>");
		verify(statement).setString(1, "<replaced/>");
	}
}
