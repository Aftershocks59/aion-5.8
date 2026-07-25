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
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.items.storage.StorageType;

/**
 * Covers what a character, an account or a legion has stored away.
 *
 * @author Oraion
 */
class InventoryRepositoryTest {

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

	@Test
	@DisplayName("Walks the used item ids forward")
	void walksTheItemIds() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("item_unique_id")).thenReturn(700001, 700002);
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO asked for a scrollable cursor only to count the rows first.
		assertArrayEquals(new int[] { 700001, 700002 }, new JdbcInventoryRepository(dataSource).findUsedIds());
		verify(rows, never()).last();
	}

	@Test
	@DisplayName("Reports item ids it could not read")
	void reportsUnreadableItemIds() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcInventoryRepository(dataSource).findUsedIds());
		verify(connection).close();
	}

	@Test
	@DisplayName("Asks for the worn equipment of the character it was given")
	void asksForTheRightEquipment() throws SQLException {
		noRows();

		assertEquals(0, new JdbcInventoryRepository(dataSource).loadEquipment(42).size());

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 0);
		verify(statement).setInt(3, 1);
	}

	@Test
	@DisplayName("Reads a character's own storage under their own id")
	void readsACharacterStorage() throws SQLException {
		noRows();

		new JdbcInventoryRepository(dataSource).loadStorageItems(42, StorageType.CUBE);

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, StorageType.CUBE.getId());
	}

	@Test
	@DisplayName("Reads an account warehouse under the account id")
	void readsAnAccountWarehouse() throws SQLException {
		ResultSet account = mock(ResultSet.class);
		when(account.next()).thenReturn(true);
		when(account.getInt("account_id")).thenReturn(9001);
		ResultSet empty = mock(ResultSet.class);
		when(empty.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(account, empty);

		new JdbcInventoryRepository(dataSource).loadStorageItems(42, StorageType.ACCOUNT_WAREHOUSE);

		verify(statement).setInt(1, 9001);
	}

	@Test
	@DisplayName("Writes nothing when there is no item to write")
	void writesNothingForNoItems() throws SQLException {
		assertTrue(new JdbcInventoryRepository(dataSource).save(List.of(), 42));

		verify(dataSource, never()).getConnection();
	}

	@Test
	@DisplayName("Leaves items pending when their write failed")
	void leavesFailedItemsPending() throws SQLException {
		Item item = mock(Item.class);
		when(item.getPersistentState()).thenReturn(PersistentState.DELETED);
		when(item.getObjectId()).thenReturn(700001);
		when(statement.executeBatch()).thenThrow(new SQLException("connection lost"));

		// The DAO marked them saved whatever happened, and its insert pass had even
		// had its error log commented out, so a lost item looked stored and was
		// never written again.
		assertThrows(RepositoryException.class, () -> new JdbcInventoryRepository(dataSource)
				.save(List.of(item), Integer.valueOf(42), null, null));

		verify(item, never()).setPersistentState(PersistentState.UPDATED);
		verify(connection).rollback();
	}

	@Test
	@DisplayName("Clears a character's items without touching the legion warehouse")
	void clearsACharacterItems() throws SQLException {
		when(statement.executeUpdate()).thenReturn(31);

		assertEquals(31, new JdbcInventoryRepository(dataSource).removeFor(42));
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Clears an account warehouse")
	void clearsAnAccountWarehouse() throws SQLException {
		when(statement.executeUpdate()).thenReturn(7);

		assertEquals(7, new JdbcInventoryRepository(dataSource).removeAccountWarehouse(9001));
		verify(statement).setInt(1, 9001);
	}

	@Test
	@DisplayName("Refuses to read the equipment of a null character")
	void refusesANullCharacter() {
		assertThrows(IllegalArgumentException.class,
				() -> new JdbcInventoryRepository(dataSource).loadEquipment(null));
	}

	@Test
	@DisplayName("Refuses to store an item for a null character")
	void refusesANullOwner() {
		assertThrows(IllegalArgumentException.class,
				() -> new JdbcInventoryRepository(dataSource).save(mock(Item.class), null));
	}
}
