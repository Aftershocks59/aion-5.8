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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
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

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.team.legion.LegionJoinRequest;

/**
 * Covers the legions and their members.
 *
 * @author Oraion
 */
class LegionRepositoriesTest {

	private DataSource dataSource;
	private Connection connection;
	private PreparedStatement statement;
	private InventoryRepository inventories;

	@BeforeEach
	void setUp() throws SQLException {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);
		inventories = mock(InventoryRepository.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(inventories.loadStorageItems(anyInt(), any(StorageType.class))).thenReturn(List.of());
	}

	private void noRows() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);
	}

	private JdbcLegionRepository legions() {
		return new JdbcLegionRepository(dataSource, inventories);
	}

	private JdbcLegionMemberRepository members() {
		return new JdbcLegionMemberRepository(dataSource);
	}

	@Test
	@DisplayName("Asks the database for one legion name rather than a count")
	void asksForOneLegionName() throws SQLException {
		noRows();

		assertFalse(legions().isNameUsed("Nowhere"));
		verify(statement).setString(1, "Nowhere");
	}

	@Test
	@DisplayName("Treats a legion name it could not check as taken")
	void treatsADoubtfulNameAsTaken() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertTrue(legions().isNameUsed("Nowhere"));
	}

	@Test
	@DisplayName("Answers nothing for a legion that does not exist")
	void answersNothingForAMissingLegion() throws SQLException {
		noRows();

		// The DAO decided this by comparing the legion's name against the empty
		// literal by reference.
		assertNull(legions().load(9999));
	}

	@Test
	@DisplayName("Walks the used legion ids forward")
	void walksTheLegionIds() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("id")).thenReturn(2000001, 2000002);
		when(statement.executeQuery()).thenReturn(rows);

		assertArrayEquals(new int[] { 2000001, 2000002 }, legions().findUsedIds());
		verify(rows, never()).last();
	}

	@Test
	@DisplayName("Reads the message a candidacy carries")
	void readsTheCandidacyMessage() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("legionId")).thenReturn(2000001);
		when(rows.getInt("playerId")).thenReturn(42);
		when(rows.getString("joinRequestMsg")).thenReturn("Let me in");
		when(statement.executeQuery()).thenReturn(rows);

		List<LegionJoinRequest> requests = legions().loadJoinRequests(2000001);

		// The DAO wrote this and never read it back, so every candidacy came back
		// with no message on it.
		assertEquals(1, requests.size());
		assertEquals("Let me in", requests.get(0).getMsg());
	}

	@Test
	@DisplayName("Writes a candidacy as an upsert rather than a second insert")
	void writesACandidacyAsAnUpsert() throws SQLException {
		LegionJoinRequest request = new LegionJoinRequest();
		request.setLegionId(2000001);
		request.setPlayerId(42);
		request.setDate(new Timestamp(1_700_000_000_000L));
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(legions().saveJoinRequest(request));

		// The table keys on the legion and the character, so the DAO's plain insert
		// threw a duplicate key error that nothing reported.
		verify(connection).prepareStatement(
				org.mockito.ArgumentMatchers.contains("ON DUPLICATE KEY UPDATE"));
	}

	@Test
	@DisplayName("Disbands a legion and releases its fortress together")
	void disbandsALegionInOneTransaction() throws SQLException {
		legions().remove(2000001);

		verify(statement, times(2)).setInt(1, 2000001);
		verify(connection).commit();
	}

	@Test
	@DisplayName("Reads a legion warehouse through the inventory repository")
	void readsTheWarehouseThroughTheInventory() throws SQLException {
		com.aionemu.gameserver.model.team.legion.Legion legion = mock(
				com.aionemu.gameserver.model.team.legion.Legion.class);
		when(legion.getLegionId()).thenReturn(2000001);

		assertNotNull(legions().loadWarehouse(legion));

		// The DAO had its own copy of the thirty-odd column reader.
		verify(inventories).loadStorageItems(2000001, StorageType.LEGION_WAREHOUSE);
	}

	@Test
	@DisplayName("Writes an emblem without asking whether one is already there")
	void writesAnEmblemAsAnUpsert() throws SQLException {
		com.aionemu.gameserver.model.team.legion.LegionEmblem emblem = new com.aionemu.gameserver.model.team.legion.LegionEmblem();
		when(statement.executeUpdate()).thenReturn(1);

		legions().saveEmblem(2000001, emblem);

		verify(connection).prepareStatement(
				org.mockito.ArgumentMatchers.contains("ON DUPLICATE KEY UPDATE"));
		verify(statement).setInt(1, 2000001);
	}

	@Test
	@DisplayName("Refuses a null legion")
	void refusesANullLegion() {
		assertThrows(IllegalArgumentException.class, () -> legions().save(null));
	}

	@Test
	@DisplayName("Reports legion notices it could not read")
	void reportsUnreadableNotices() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> legions().loadNotices(2000001));
		verify(connection).close();
	}

	@Test
	@DisplayName("Answers an empty list for a legion whose last member left")
	void answersAnEmptyMemberList() throws SQLException {
		noRows();

		// The DAO answered null here, which its caller assigned straight onto the
		// legion.
		assertEquals(0, members().loadMembers(2000001).size());
	}

	@Test
	@DisplayName("Answers nothing for a character who belongs to no legion")
	void answersNothingForANonMember() throws SQLException {
		noRows();

		// The DAO read the row without checking there was one and let the resulting
		// exception stand in for the answer.
		assertNull(members().load(42));
	}

	@Test
	@DisplayName("Answers nothing rather than searching for character zero")
	void answersNothingForCharacterZero() throws SQLException {
		assertNull(members().load(0));

		verify(dataSource, never()).getConnection();
	}

	@Test
	@DisplayName("Asks the database for one membership rather than a count")
	void asksForOneMembership() throws SQLException {
		noRows();

		assertFalse(members().isEnrolled(42));
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Treats a character it could not check as already enrolled")
	void treatsADoubtfulCharacterAsEnrolled() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertTrue(members().isEnrolled(42));
	}

	@Test
	@DisplayName("Takes a character out of their legion")
	void takesACharacterOut() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(members().remove(42));
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Refuses to enrol a character with no legion")
	void refusesAnEnrolmentWithoutALegion() {
		assertThrows(IllegalArgumentException.class, () -> members().add(null));
	}
}
