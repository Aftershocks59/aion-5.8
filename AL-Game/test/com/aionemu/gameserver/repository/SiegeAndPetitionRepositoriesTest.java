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
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.Petition;
import com.aionemu.gameserver.model.house.PlayerHouseBid;
import com.aionemu.gameserver.model.siege.SiegeLocation;

/**
 * Covers the siege locations, the house bids, the Atreian passports and the
 * petitions.
 *
 * @author Oraion
 */
class SiegeAndPetitionRepositoriesTest {

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
	@DisplayName("Ignores a stored siege location the world does not have")
	void ignoresAnUnknownSiegeLocation() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("id")).thenReturn(9999);
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO dereferenced the missing location and lost every remaining row to
		// its catch, so one stale row left every fortress unowned.
		new JdbcSiegeRepository(dataSource).load(new LinkedHashMap<Integer, SiegeLocation>());

		verify(connection).commit();
	}

	@Test
	@DisplayName("Reports siege locations it could not read")
	void reportsUnreadableSiegeLocations() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class,
				() -> new JdbcSiegeRepository(dataSource).load(new LinkedHashMap<Integer, SiegeLocation>()));
	}

	@Test
	@DisplayName("Refuses a null siege location")
	void refusesANullSiegeLocation() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcSiegeRepository(dataSource).save(null));
	}

	@Test
	@DisplayName("Keeps the house bids in the order they were read")
	void keepsTheHouseBidOrder() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("player_id")).thenReturn(1, 2);
		when(rows.getInt("house_id")).thenReturn(500, 500);
		when(rows.getLong("bid")).thenReturn(100L, 200L);
		when(statement.executeQuery()).thenReturn(rows);

		Set<PlayerHouseBid> bids = new JdbcHouseBidRepository(dataSource).findAll();

		Iterator<PlayerHouseBid> reading = bids.iterator();
		assertEquals(100L, reading.next().getBidOffer());
		assertEquals(200L, reading.next().getBidOffer());
	}

	@Test
	@DisplayName("Records a bid on a house")
	void recordsAHouseBid() throws SQLException {
		Timestamp at = new Timestamp(1_700_000_000_000L);
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcHouseBidRepository(dataSource).add(42, 500, 1500L, at));

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 500);
		verify(statement).setLong(3, 1500L);
		verify(statement).setTimestamp(4, at);
	}

	@Test
	@DisplayName("Clears every bid on a house at once")
	void clearsTheBidsOnAHouse() throws SQLException {
		when(statement.executeUpdate()).thenReturn(4);

		assertEquals(4, new JdbcHouseBidRepository(dataSource).removeAll(500));
		verify(statement).setInt(1, 500);
	}

	@Test
	@DisplayName("Answers no stamps for a passport an account does not hold")
	void answersNoStampsForAMissingPassport() throws SQLException {
		noRows();

		// The DAO read the row without checking there was one, and answered zero
		// from a catch when there was not.
		assertEquals(PassportRepository.NO_STAMPS, new JdbcPassportRepository(dataSource).findStamps(7, 8));
	}

	@Test
	@DisplayName("Answers nothing rather than the present moment for an unstamped passport")
	void answersNothingForAnUnstampedPassport() throws SQLException {
		noRows();

		// The DAO answered the present moment from its catch, which reads as "just
		// stamped" and cost the account that day's stamp.
		assertNull(new JdbcPassportRepository(dataSource).findLastStamp(7, 8));
	}

	@Test
	@DisplayName("Reports passports it could not read")
	void reportsUnreadablePassports() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcPassportRepository(dataSource).findLastStamp(7, 8));
		verify(connection).close();
	}

	@Test
	@DisplayName("Writes an account's progress through a passport")
	void writesPassportProgress() throws SQLException {
		Timestamp at = new Timestamp(1_700_000_000_000L);
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcPassportRepository(dataSource).update(7, 8, 12, true, at));

		verify(statement).setInt(1, 12);
		verify(statement).setInt(2, 1);
		verify(statement).setInt(4, 7);
		verify(statement).setInt(5, 8);
	}

	@Test
	@DisplayName("Starts petition ids at one on an empty table")
	void startsPetitionIdsAtOne() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getInt("highest")).thenReturn(0);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(1, new JdbcPetitionRepository(dataSource).nextId());
	}

	@Test
	@DisplayName("Reports open petitions it could not read")
	void reportsUnreadablePetitions() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		// The DAO answered null here, and its caller walked it straight into a null
		// pointer.
		assertThrows(RepositoryException.class, () -> new JdbcPetitionRepository(dataSource).findOpen());
		verify(connection).close();
	}

	@Test
	@DisplayName("Answers nothing for a petition that does not exist")
	void answersNothingForAMissingPetition() throws SQLException {
		noRows();

		assertNull(new JdbcPetitionRepository(dataSource).findById(77));
		verify(statement).setInt(1, 77);
	}

	@Test
	@DisplayName("Refuses a null petition")
	void refusesANullPetition() {
		assertThrows(IllegalArgumentException.class,
				() -> new JdbcPetitionRepository(dataSource).add((Petition) null, 0L));
	}

	@Test
	@DisplayName("Withdraws only the petitions still waiting on an answer")
	void withdrawsOnlyOpenPetitions() throws SQLException {
		when(statement.executeUpdate()).thenReturn(2);

		assertEquals(2, new JdbcPetitionRepository(dataSource).removeOpenFor(42));
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Records that a petition was answered")
	void recordsAnAnsweredPetition() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcPetitionRepository(dataSource).markReplied(77));
		verify(statement).setInt(1, 77);
	}
}
