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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.ItemData;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.items.ManaStone;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;

/**
 * Covers the stones socketed into weapons and armour.
 *
 * @author Oraion
 */
class ItemStoneRepositoryTest {

	private DataSource dataSource;
	private Connection connection;
	private PreparedStatement statement;
	private ItemData itemData;

	@BeforeEach
	void setUp() throws SQLException {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);

		// A stone reaches for its template as it is built, so the static data has
		// to be present.
		itemData = DataManager.ITEM_DATA;
		ItemData items = mock(ItemData.class);
		when(items.getItemTemplate(167000191)).thenReturn(mock(ItemTemplate.class));
		DataManager.ITEM_DATA = items;
	}

	@AfterEach
	void tearDown() {
		DataManager.ITEM_DATA = itemData;
	}

	private static ManaStone stone(PersistentState state) {
		return new ManaStone(700001, 167000191, 2, state);
	}

	@Test
	@DisplayName("Queues a stone removal rather than running it twice")
	void queuesTheRemovalOnce() throws SQLException {
		Set<ManaStone> stones = Set.of(stone(PersistentState.DELETED));

		new JdbcItemStoneRepository(dataSource).saveManaStones(stones);

		// The DAO called execute() and addBatch() on the same statement, so every
		// delete went out a second time.
		verify(statement, never()).execute();
		verify(statement).addBatch();
		verify(statement).executeBatch();
	}

	@Test
	@DisplayName("Writes a new stone with its item, slot and category")
	void writesANewStone() throws SQLException {
		new JdbcItemStoneRepository(dataSource).saveManaStones(Set.of(stone(PersistentState.NEW)));

		verify(statement).setInt(1, 700001);
		verify(statement).setInt(2, 167000191);
		verify(statement).setInt(3, 2);
		verify(statement).executeBatch();
	}

	@Test
	@DisplayName("Leaves a stone pending when its write failed")
	void leavesAFailedStonePending() throws SQLException {
		ManaStone pending = stone(PersistentState.NEW);
		when(statement.executeBatch()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class,
				() -> new JdbcItemStoneRepository(dataSource).saveManaStones(Set.of(pending)));

		// The DAO did this whatever happened, so a stone whose write had failed
		// still looked saved and was never retried.
		assertNotEquals(PersistentState.UPDATED, pending.getPersistentState());
		verify(connection).rollback();
	}

	@Test
	@DisplayName("Marks a stone saved once its write has landed")
	void marksAStoneSaved() throws SQLException {
		ManaStone written = stone(PersistentState.NEW);

		new JdbcItemStoneRepository(dataSource).saveManaStones(Set.of(written));

		assertEquals(PersistentState.UPDATED, written.getPersistentState());
	}

	@Test
	@DisplayName("Writes nothing when there is no stone to write")
	void writesNothingForNoStones() throws SQLException {
		new JdbcItemStoneRepository(dataSource).saveManaStones(Set.of());
		new JdbcItemStoneRepository(dataSource).save(List.of());

		verify(dataSource, never()).getConnection();
	}

	@Test
	@DisplayName("Reads nothing for no items")
	void readsNothingForNoItems() throws SQLException {
		new JdbcItemStoneRepository(dataSource).load(List.of());

		verify(dataSource, never()).getConnection();
	}

	@Test
	@DisplayName("Refuses a null idian stone")
	void refusesANullIdianStone() {
		assertThrows(IllegalArgumentException.class,
				() -> new JdbcItemStoneRepository(dataSource).saveIdianStone(null));
	}
}
