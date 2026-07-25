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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.house.House;
import com.aionemu.gameserver.model.ingameshop.IGItem;
import com.aionemu.gameserver.model.templates.housing.HousingLand;

/**
 * Covers the arena ladder, the in-game shop and the houses.
 *
 * @author Oraion
 */
class LadderAndHouseRepositoriesTest {

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
	@DisplayName("Creates a ladder row and counts the win in one statement")
	void countsAWinWithoutAskingFirst() throws SQLException {
		new JdbcLadderRepository(dataSource).addWin(42);

		// The DAO asked whether the row existed and then wrote, which two matches
		// finishing at once could both answer "no" to.
		verify(connection).prepareStatement(anyString());
		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 1);
		verify(statement).setInt(3, 1);
	}

	@Test
	@DisplayName("Opens a new ladder row at the starting rating")
	void opensAtTheStartingRating() throws SQLException {
		new JdbcLadderRepository(dataSource).addRating(42, 25);

		verify(statement).setInt(2, LadderRepository.STARTING_RATING + 25);
		verify(statement).setInt(3, 25);
	}

	@Test
	@DisplayName("Answers the starting rating for a character who never played")
	void answersTheStartingRating() throws SQLException {
		noRows();

		assertEquals(LadderRepository.STARTING_RATING, new JdbcLadderRepository(dataSource).findRating(42));
	}

	@Test
	@DisplayName("Answers no walkouts for a character who never played")
	void answersNoWalkouts() throws SQLException {
		noRows();

		assertEquals(0, new JdbcLadderRepository(dataSource).findLeaves(42));
	}

	@Test
	@DisplayName("Numbers the ladder in the order the database answered")
	void numbersTheLadderInOrder() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("player_id")).thenReturn(11, 22);
		when(rows.getInt("rank")).thenReturn(4, 9);
		when(statement.executeQuery()).thenReturn(rows);

		new JdbcLadderRepository(dataSource).updateRanks();

		// The database orders them, where the DAO asked for the wrong order and
		// then sorted the whole ladder again in memory.
		verify(statement).setInt(1, 1);
		verify(statement).setInt(2, 11);
		verify(statement).setInt(1, 2);
		verify(statement).setInt(2, 22);
		verify(connection).commit();
	}

	@Test
	@DisplayName("Writes nothing when nobody is on the ladder")
	void writesNothingForAnEmptyLadder() throws SQLException {
		noRows();

		new JdbcLadderRepository(dataSource).updateRanks();

		verify(connection, never()).commit();
	}

	@Test
	@DisplayName("Reports a ladder it could not read")
	void reportsAnUnreadableLadder() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcLadderRepository(dataSource).updateRanks());
		verify(connection).close();
	}

	@Test
	@DisplayName("Keeps the shop shelves in the order they were read")
	void keepsTheShelfOrder() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getByte("category")).thenReturn((byte) 5, (byte) 5);
		when(rows.getInt("item_id")).thenReturn(188000001, 188000002);
		when(statement.executeQuery()).thenReturn(rows);

		Map<Byte, List<IGItem>> shelves = new JdbcInGameShopRepository(dataSource).findAll();

		assertEquals(1, shelves.size());
		assertEquals(2, shelves.get(Byte.valueOf((byte) 5)).size());
	}

	@Test
	@DisplayName("Refuses a null shop item")
	void refusesANullShopItem() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcInGameShopRepository(dataSource).add(null));
	}

	@Test
	@DisplayName("Records the sales of a shop entry")
	void recordsShopSales() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcInGameShopRepository(dataSource).setSales(700, 12));

		verify(statement).setInt(1, 12);
		verify(statement).setInt(2, 700);
	}

	@Test
	@DisplayName("Reports a shop it could not read")
	void reportsAnUnreadableShop() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcInGameShopRepository(dataSource).findAll());
		verify(connection).close();
	}

	@Test
	@DisplayName("Walks the used house ids forward")
	void walksTheHouseIds() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt(1)).thenReturn(3001, 3002);
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO asked for a scrollable cursor only to count the rows first.
		assertArrayEquals(new int[] { 3001, 3002 }, new JdbcHouseRepository(dataSource).findUsedIds());
		verify(rows, never()).last();
	}

	@Test
	@DisplayName("Ignores a house whose address the world does not have")
	void ignoresAHouseWithoutAnAddress() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("id")).thenReturn(4001);
		when(rows.getInt("address")).thenReturn(9999);
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO dereferenced the missing address and lost every remaining house
		// to its catch.
		Map<Integer, House> built = new JdbcHouseRepository(dataSource).load(new ArrayList<HousingLand>(), false);

		assertEquals(0, built.size());
	}

	@Test
	@DisplayName("Reports houses it could not read")
	void reportsUnreadableHouses() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class,
				() -> new JdbcHouseRepository(dataSource).load(new ArrayList<HousingLand>(), false));
		verify(connection).close();
	}

	@Test
	@DisplayName("Pulls down whatever a character owns")
	void pullsDownAHouse() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertEquals(1, new JdbcHouseRepository(dataSource).removeFor(42));
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Refuses a null house")
	void refusesANullHouse() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcHouseRepository(dataSource).save(null));
	}
}
