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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.Announcement;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.outpost.OutpostLocation;

/**
 * Covers the announcements, the marriages, the outposts and the daily event
 * item counts.
 *
 * @author Oraion
 */
class WorldStateRepositoriesTest {

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
	@DisplayName("Keeps the announcements in the order they were read")
	void keepsTheAnnouncementOrder() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, true, false);
		when(rows.getInt("id")).thenReturn(1, 2, 3);
		when(rows.getString("announce")).thenReturn("first", "second", "third");
		when(rows.getString("faction")).thenReturn("ALL");
		when(rows.getString("type")).thenReturn("SHOUT");
		when(statement.executeQuery()).thenReturn(rows);

		Set<Announcement> announcements = new JdbcAnnouncementRepository(dataSource).findAll();

		// The DAO asked the database to order these and then dropped them into a
		// HashSet, so the rotation came out in hash order.
		Iterator<Announcement> reading = announcements.iterator();
		assertEquals("first", reading.next().getAnnounce());
		assertEquals("second", reading.next().getAnnounce());
		assertEquals("third", reading.next().getAnnounce());
	}

	@Test
	@DisplayName("Refuses a null announcement")
	void refusesANullAnnouncement() {
		assertThrows(IllegalArgumentException.class,
				() -> new JdbcAnnouncementRepository(dataSource).add(null));
	}

	@Test
	@DisplayName("Answers the other name on a marriage row")
	void answersTheOtherPartner() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getInt("player1")).thenReturn(42);
		when(rows.getInt("player2")).thenReturn(99);
		when(statement.executeQuery()).thenReturn(rows);

		JdbcWeddingRepository repository = new JdbcWeddingRepository(dataSource);
		assertEquals(99, repository.findPartner(42));
	}

	@Test
	@DisplayName("Answers no partner when a character is unmarried")
	void answersNoPartner() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(WeddingRepository.NO_PARTNER, new JdbcWeddingRepository(dataSource).findPartner(42));
	}

	@Test
	@DisplayName("Ends a marriage recorded either way round")
	void divorcesEitherWayRound() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcWeddingRepository(dataSource).divorce(42, 99));

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 99);
		verify(statement).setInt(3, 42);
		verify(statement).setInt(4, 99);
	}

	@Test
	@DisplayName("Ignores a stored outpost the world does not have")
	void ignoresAnUnknownOutpost() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("id")).thenReturn(9999);
		when(rows.getString("race")).thenReturn("ELYOS");
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO dereferenced the missing outpost and lost every remaining row to
		// its catch.
		new JdbcOutpostRepository(dataSource).load(new LinkedHashMap<Integer, OutpostLocation>());

		verify(connection).commit();
	}

	@Test
	@DisplayName("Creates a row for an outpost that has none")
	void createsMissingOutpostRows() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		OutpostLocation location = mock(OutpostLocation.class);
		when(location.getId()).thenReturn(1);
		Map<Integer, OutpostLocation> world = new LinkedHashMap<Integer, OutpostLocation>();
		world.put(Integer.valueOf(1), location);

		new JdbcOutpostRepository(dataSource).load(world);

		verify(statement).setString(2, Race.NPC.toString());
		verify(statement).executeBatch();
	}

	@Test
	@DisplayName("Refuses a null outpost")
	void refusesANullOutpost() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcOutpostRepository(dataSource).save(null));
	}

	@Test
	@DisplayName("Reports outposts it could not read")
	void reportsUnreadableOutposts() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class,
				() -> new JdbcOutpostRepository(dataSource).load(new LinkedHashMap<Integer, OutpostLocation>()));
	}

	@Test
	@DisplayName("Forgets one event item for everybody")
	void forgetsOneEventItem() throws SQLException {
		when(statement.executeUpdate()).thenReturn(12);

		assertEquals(12, new JdbcEventItemRepository(dataSource).removeItem(110900001));
		verify(statement).setInt(1, 110900001);
	}

	@Test
	@DisplayName("Reports event item counts it could not read")
	void reportsUnreadableEventItems() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcEventItemRepository(dataSource)
				.load(mock(com.aionemu.gameserver.model.gameobjects.player.Player.class)));
		verify(connection).close();
	}
}
