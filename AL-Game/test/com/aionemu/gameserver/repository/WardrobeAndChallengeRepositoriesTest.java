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
import com.aionemu.gameserver.model.dorinerk_wardrobe.PlayerWardrobeEntry;
import com.aionemu.gameserver.model.dorinerk_wardrobe.PlayerWardrobeList;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Covers the wardrobe and the challenge tasks.
 * <p>
 * The wardrobe carried a plain copy-paste defect: the load read the slot column
 * twice, once into the slot and once into the restyle count, so every stored
 * look came back claiming it had been restyled as many times as its slot number.
 *
 * @author Oraion
 */
class WardrobeAndChallengeRepositoriesTest {

	private DataSource dataSource;
	private Connection connection;
	private PreparedStatement statement;
	private Player player;

	@BeforeEach
	void setUp() throws SQLException {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);
		player = mock(Player.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(player.getObjectId()).thenReturn(42);
	}

	@Test
	@DisplayName("Reads the restyle count from its own column, not the slot")
	void readsTheRestyleCountFromItsOwnColumn() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("item_id")).thenReturn(110900001);
		when(rows.getInt("slot")).thenReturn(3);
		when(rows.getInt("reskin_count")).thenReturn(7);
		when(statement.executeQuery()).thenReturn(rows);

		PlayerWardrobeList wardrobe = new JdbcPlayerWardrobeRepository(dataSource).findAll(player);

		PlayerWardrobeEntry entry = wardrobe.getAllWardrobe()[0];
		assertEquals(3, entry.getSlot());
		// The DAO answered 3 here, having read the slot column a second time.
		assertEquals(7, entry.getReskinCount());
	}

	@Test
	@DisplayName("Answers zero for a slot the character has not filled")
	void answersZeroForAnEmptySlot() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		JdbcPlayerWardrobeRepository repository = new JdbcPlayerWardrobeRepository(dataSource);
		assertEquals(0, repository.findItemInSlot(42, 3));
		assertEquals(0, repository.findReskinCount(42, 3));
	}

	@Test
	@DisplayName("Counts what a character has stored")
	void countsTheWardrobe() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getInt(1)).thenReturn(12);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(12, new JdbcPlayerWardrobeRepository(dataSource).count(42));
	}

	@Test
	@DisplayName("Stores a look with its slot and restyle count")
	void storesALook() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcPlayerWardrobeRepository(dataSource).save(42, 110900001, 3, 7));

		verify(statement).setInt(2, 110900001);
		verify(statement).setInt(3, 3);
		verify(statement).setInt(4, 7);
	}

	@Test
	@DisplayName("Reports a wardrobe it could not read")
	void reportsAnUnreadableWardrobe() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class,
				() -> new JdbcPlayerWardrobeRepository(dataSource).findAll(player));
		verify(connection).close();
	}

	@Test
	@DisplayName("Refuses a null challenge task")
	void refusesANullTask() {
		assertThrows(IllegalArgumentException.class,
				() -> new JdbcChallengeTaskRepository(dataSource).save(null));
	}

	@Test
	@DisplayName("Asks for the tasks of one owner and one kind")
	void asksForOneOwnerAndKind() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertTrue(new JdbcChallengeTaskRepository(dataSource)
				.findAll(42, com.aionemu.gameserver.model.templates.challenge.ChallengeType.LEGION).isEmpty());

		verify(statement).setInt(1, 42);
		verify(statement).setString(2, "LEGION");
	}
}
