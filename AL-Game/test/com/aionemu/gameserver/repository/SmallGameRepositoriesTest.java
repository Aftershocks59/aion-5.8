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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
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
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.templates.survey.SurveyItem;

/**
 * Covers the free-to-play clock, the towns, the queued deliveries and the
 * veteran rewards.
 * <p>
 * The free-to-play clock carried a defect that had never worked at all. Its
 * update statement takes the time and the character, and only the time was
 * bound; the second parameter was left commented out. Every call threw for the
 * missing value, the throw was swallowed, and the method answered false.
 *
 * @author Oraion
 */
class SmallGameRepositoriesTest {

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

	@Test
	@DisplayName("Binds both the character and the time when writing the free-to-play clock")
	void bindsBothFreeToPlayValues() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcF2pRepository(dataSource).save(42, 3600));

		// The DAO bound only the time, so the statement always threw for the missing
		// character and the update never landed.
		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 3600);
		verify(connection).prepareStatement(contains("ON DUPLICATE KEY UPDATE"));
	}

	@Test
	@DisplayName("Reports a free-to-play clock it could not write")
	void reportsAFailedFreeToPlayWrite() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("table is read only"));

		assertThrows(RepositoryException.class, () -> new JdbcF2pRepository(dataSource).save(42, 3600));
		verify(connection).close();
	}

	@Test
	@DisplayName("Asks for the towns of one race by name")
	void asksForTheTownsOfOneRace() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		// Building a Town spawns its objects and needs the static data, so this
		// settles the query rather than the mapping.
		assertTrue(new JdbcTownRepository(dataSource).findAll(Race.ELYOS).isEmpty());
		verify(statement).setString(1, "ELYOS");
		verify(connection).prepareStatement(
				"SELECT `id`,`level`,`points`,`level_up_date` FROM `towns` WHERE `race` = ?");
	}
	@Test
	@DisplayName("Refuses a null town")
	void refusesANullTown() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcTownRepository(dataSource).save(null));
	}

	@Test
	@DisplayName("Asks only for deliveries not yet made")
	void asksOnlyForPendingDeliveries() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertTrue(new JdbcSurveyRepository(dataSource).findPending().isEmpty());
		verify(statement).setInt(1, 0);
	}

	@Test
	@DisplayName("Maps a queued delivery onto its item")
	void mapsAQueuedDelivery() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("unique_id")).thenReturn(7);
		when(rows.getInt("owner_id")).thenReturn(42);
		when(rows.getInt("item_id")).thenReturn(110900001);
		when(rows.getLong("item_count")).thenReturn(5L);
		when(statement.executeQuery()).thenReturn(rows);

		List<SurveyItem> pending = new JdbcSurveyRepository(dataSource).findPending();

		assertEquals(1, pending.size());
		assertEquals(7, pending.get(0).uniqueId);
		assertEquals(5L, pending.get(0).count);
	}

	@Test
	@DisplayName("Marks a delivery done rather than deleting it")
	void marksADeliveryDone() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcSurveyRepository(dataSource).markDelivered(7));

		verify(statement).setInt(1, 1);
		verify(statement).setInt(2, 7);
	}

	@Test
	@DisplayName("Reads a veteran reward with every column it carries")
	void readsAVeteranReward() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("id")).thenReturn(1);
		when(rows.getString("player")).thenReturn("Maeron");
		when(rows.getInt("count")).thenReturn(3);
		when(rows.getInt("kinah")).thenReturn(1000);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(1, new JdbcVeteranRewardRepository(dataSource).findAll().size());
		// The count sits between the item and the kinah; dropping it shifts every
		// later value by one.
		verify(rows).getInt("count");
	}

	@Test
	@DisplayName("Reports veteran rewards it could not read")
	void reportsUnreadableRewards() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcVeteranRewardRepository(dataSource).findAll());
		verify(connection).close();
	}
}
