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

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.aionemu.gameserver.model.gameobjects.player.motion.Motion;

/**
 * Covers the movement styles, the skill appearances and the account passkey.
 * <p>
 * The passkey is the one that matters. Both of its reads answer no when they
 * cannot tell, which is deliberate and inherited: this decides whether somebody
 * gets past a lock, and a database failure must not open it.
 *
 * @author Oraion
 */
class PlayerCosmeticRepositoriesTest {

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
	@DisplayName("Accepts the passkey the account set")
	void acceptsTheRightPasskey() throws SQLException {
		stubCount(1);

		assertTrue(new JdbcPlayerPasskeyRepository(dataSource).matches(42, "hashed"));
		verify(statement).setString(2, "hashed");
	}

	@Test
	@DisplayName("Refuses a passkey that does not match")
	void refusesTheWrongPasskey() throws SQLException {
		stubCount(0);

		assertFalse(new JdbcPlayerPasskeyRepository(dataSource).matches(42, "wrong"));
	}

	@Test
	@DisplayName("Refuses the passkey when the question cannot be answered")
	void refusesWhenTheDatabaseIsSilent() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		// Deliberate, and inherited: an unanswerable question must not open a lock.
		assertFalse(new JdbcPlayerPasskeyRepository(dataSource).matches(42, "hashed"));
		assertFalse(new JdbcPlayerPasskeyRepository(dataSource).exists(42));
	}

	@Test
	@DisplayName("Reports a passkey it could not write")
	void reportsAFailedPasskeyWrite() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("table is read only"));

		// A write that fails must be seen, unlike a read that must fail closed.
		assertThrows(RepositoryException.class,
				() -> new JdbcPlayerPasskeyRepository(dataSource).create(42, "hashed"));
	}

	@Test
	@DisplayName("Changes a passkey only for somebody who knows the old one")
	void changesOnlyWithTheOldPasskey() throws SQLException {
		when(statement.executeUpdate()).thenReturn(0);

		assertFalse(new JdbcPlayerPasskeyRepository(dataSource).replace(42, "wrong", "new"));
		verify(statement).setString(3, "wrong");
	}

	@Test
	@DisplayName("Switches a skill skin with a parameter, not two statements")
	void switchesASkinWithAParameter() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);
		JdbcPlayerSkillSkinRepository repository = new JdbcPlayerSkillSkinRepository(dataSource);

		repository.setActive(42, 5, true);
		verify(statement).setInt(1, 1);

		repository.setActive(42, 5, false);
		verify(statement).setInt(1, 0);
	}

	@Test
	@DisplayName("Stores a motion with whether it is being worn")
	void storesAMotion() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);
		Motion motion = new Motion(7, 3600, true);

		assertTrue(new JdbcPlayerMotionRepository(dataSource).add(42, motion));

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 7);
		verify(statement).setBoolean(3, true);
		verify(statement).setInt(4, 3600);
	}

	@Test
	@DisplayName("Refuses a null motion")
	void refusesANullMotion() {
		assertThrows(IllegalArgumentException.class,
				() -> new JdbcPlayerMotionRepository(dataSource).add(42, null));
	}

	@Test
	@DisplayName("Reports motions it could not read")
	void reportsUnreadableMotions() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class,
				() -> new JdbcPlayerSkillSkinRepository(dataSource).findAll(42));
		verify(connection).close();
	}
}
