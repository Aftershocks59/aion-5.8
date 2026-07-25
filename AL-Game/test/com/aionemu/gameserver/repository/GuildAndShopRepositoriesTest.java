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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerLunaShop;
import com.aionemu.gameserver.services.events.thievesguildservice.ThievesStatusList;

/**
 * Covers the thieves guild, the Atreian bestiary, the Luna shop and the house
 * registries.
 *
 * @author Oraion
 */
class GuildAndShopRepositoriesTest {

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

	private static ThievesStatusList standing() {
		ThievesStatusList standing = new ThievesStatusList();
		standing.setPlayerId(42);
		standing.setRankId(3);
		standing.setThievesCount(7);
		standing.setPrisonCount(1);
		standing.setLastThievesKinah(9000L);
		standing.setRevengeName("Someone");
		standing.setRevengeCount(2);
		standing.setRevengeDate(new Timestamp(1_700_000_000_000L));
		return standing;
	}

	@Test
	@DisplayName("Binds a thieves guild update onto the placeholders the statement has")
	void bindsTheThievesUpdateInOrder() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcThievesGuildRepository(dataSource).save(standing()));

		// The DAO bound nine parameters onto eight placeholders, writing the
		// avenger's name into the revenge date, so this write always threw.
		verify(statement).setInt(1, 3);
		verify(statement).setString(5, "Someone");
		verify(statement).setInt(6, 2);
		verify(statement).setTimestamp(7, new Timestamp(1_700_000_000_000L));
		verify(statement).setInt(8, 42);
		verify(statement, never()).setInt(9, 42);
	}

	@Test
	@DisplayName("Enrols a character with their standing after their id")
	void enrolsACharacter() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcThievesGuildRepository(dataSource).add(standing()));

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 3);
		verify(statement).setString(6, "Someone");
	}

	@Test
	@DisplayName("Answers nothing for a character who never joined the thieves guild")
	void answersNothingForANonMember() throws SQLException {
		noRows();

		assertNull(new JdbcThievesGuildRepository(dataSource).load(42));
	}

	@Test
	@DisplayName("Refuses a null thieves guild standing")
	void refusesANullStanding() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcThievesGuildRepository(dataSource).save(null));
	}

	@Test
	@DisplayName("Answers nothing hunted for a beast with no entry")
	void answersNothingHunted() throws SQLException {
		noRows();

		// The DAO read the row without checking there was one, and answered zero
		// from a catch when there was not.
		assertEquals(AtreianBestiaryRepository.NOT_HUNTED,
				new JdbcAtreianBestiaryRepository(dataSource).findKillCount(42, 210001));
	}

	@Test
	@DisplayName("Reports a bestiary entry it could not read")
	void reportsAnUnreadableBestiaryEntry() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class,
				() -> new JdbcAtreianBestiaryRepository(dataSource).findLevel(42, 210001));
		verify(connection).close();
	}

	@Test
	@DisplayName("Writes one bestiary entry")
	void writesABestiaryEntry() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcAtreianBestiaryRepository(dataSource).save(42, 210001, 15, 2, 1));

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 210001);
		verify(statement).setInt(3, 15);
	}

	@Test
	@DisplayName("Writes a Luna shop record that has changed")
	void writesAChangedLunaShop() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		PlayerLunaShop shop = new PlayerLunaShop(true, false, false);
		shop.setLunaConsumePoint(120);
		shop.setLunaConsumeCount(3);
		shop.setPersistentState(PersistentState.UPDATE_REQUIRED);
		when(player.getPlayerLunaShop()).thenReturn(shop);
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcLunaShopRepository(dataSource).save(player));

		// The consume point and the consume count are distinct values; the service
		// passed the point twice.
		verify(statement).setInt(4, 120);
		verify(statement).setInt(5, 3);
		verify(statement).setInt(10, 42);
	}

	@Test
	@DisplayName("Writes nothing for a Luna shop record that has not changed")
	void writesNothingForAnUnchangedLunaShop() throws SQLException {
		Player player = mock(Player.class);
		PlayerLunaShop shop = new PlayerLunaShop(true, false, false);
		shop.setPersistentState(PersistentState.UPDATED);
		when(player.getPlayerLunaShop()).thenReturn(shop);

		assertFalse(new JdbcLunaShopRepository(dataSource).save(player));
		verify(connection, never()).prepareStatement(anyString());
	}

	@Test
	@DisplayName("Leaves a Luna shop record pending when its write failed")
	void leavesAFailedLunaShopPending() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		PlayerLunaShop shop = new PlayerLunaShop(true, false, false);
		shop.setPersistentState(PersistentState.UPDATE_REQUIRED);
		when(player.getPlayerLunaShop()).thenReturn(shop);
		when(statement.executeUpdate()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcLunaShopRepository(dataSource).save(player));

		assertNotEquals(PersistentState.UPDATED, shop.getPersistentState());
	}

	@Test
	@DisplayName("Leaves a character without a Luna shop record alone")
	void leavesAnUnknownLunaShopAlone() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		noRows();

		new JdbcLunaShopRepository(dataSource).load(player);

		verify(player, never()).setPlayerLunaShop(null);
	}

	@Test
	@DisplayName("Refuses to read a Luna shop record for a null character")
	void refusesANullLunaShopCharacter() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcLunaShopRepository(dataSource).load(null));
	}

	@Test
	@DisplayName("Walks the placed house object ids forward")
	void walksThePlacedIds() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt(1)).thenReturn(700001, 700002);
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO asked for a scrollable cursor only to count the rows first.
		assertArrayEquals(new int[] { 700001, 700002 },
				new JdbcHouseRegistryRepository(dataSource).findUsedIds());
		verify(rows, never()).last();
	}

	@Test
	@DisplayName("Reports placed house object ids it could not read")
	void reportsUnreadablePlacedIds() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcHouseRegistryRepository(dataSource).findUsedIds());
		verify(connection).close();
	}

	@Test
	@DisplayName("Takes up everything but the decoration")
	void takesUpEverythingButDecoration() throws SQLException {
		when(statement.executeUpdate()).thenReturn(6);

		assertEquals(6, new JdbcHouseRegistryRepository(dataSource).reset(42));
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Refuses a null house registry")
	void refusesANullRegistry() throws SQLException {
		assertThrows(IllegalArgumentException.class,
				() -> new JdbcHouseRegistryRepository(dataSource).save(null, 42));
		verify(connection, never()).prepareStatement(anyString());
	}

	@Test
	@DisplayName("Answers no bestiary level for a beast with no entry")
	void answersNoBestiaryLevel() throws SQLException {
		noRows();

		assertEquals(AtreianBestiaryRepository.NOT_HUNTED,
				new JdbcAtreianBestiaryRepository(dataSource).findClaimedReward(42, 210001));
		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 210001);
	}
}
