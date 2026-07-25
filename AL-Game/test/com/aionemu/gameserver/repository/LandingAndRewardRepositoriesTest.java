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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.landing.LandingLocation;
import com.aionemu.gameserver.model.landing_special.LandingSpecialLocation;
import com.aionemu.gameserver.model.landing_special.LandingSpecialStateType;
import com.aionemu.gameserver.model.templates.rewards.RewardEntryItem;

/**
 * Covers the abyss landings, the special landings, the web shop rewards and the
 * house scripts.
 *
 * @author Oraion
 */
class LandingAndRewardRepositoriesTest {

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
	@DisplayName("Ignores a stored abyss landing the world does not have")
	void ignoresAnUnknownAbyssLanding() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("id")).thenReturn(9999);
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO dereferenced the missing landing and lost every remaining row to
		// its catch.
		new JdbcAbyssLandingRepository(dataSource).load(new LinkedHashMap<Integer, LandingLocation>());

		verify(connection).commit();
	}

	@Test
	@DisplayName("Reports abyss landings it could not read")
	void reportsUnreadableAbyssLandings() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class,
				() -> new JdbcAbyssLandingRepository(dataSource).load(new LinkedHashMap<Integer, LandingLocation>()));
	}

	@Test
	@DisplayName("Writes every abyss landing counter in one statement")
	void writesAnAbyssLanding() throws SQLException {
		LandingLocation location = mock(LandingLocation.class);
		when(location.getId()).thenReturn(3);
		when(location.getLevel()).thenReturn(5);
		when(location.getPoints()).thenReturn(1200);
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcAbyssLandingRepository(dataSource).save(location));

		verify(statement).setInt(1, 5);
		verify(statement).setInt(9, 1200);
		verify(statement).setInt(10, 3);
	}

	@Test
	@DisplayName("Refuses a null abyss landing")
	void refusesANullAbyssLanding() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcAbyssLandingRepository(dataSource).save(null));
	}

	@Test
	@DisplayName("Ignores a stored special landing the world does not have")
	void ignoresAnUnknownSpecialLanding() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("id")).thenReturn(9999);
		when(statement.executeQuery()).thenReturn(rows);

		new JdbcSpecialLandingRepository(dataSource).load(new LinkedHashMap<Integer, LandingSpecialLocation>());

		verify(connection).commit();
	}

	@Test
	@DisplayName("Creates a row for a special landing that has none")
	void createsMissingSpecialLandingRows() throws SQLException {
		noRows();

		LandingSpecialLocation location = mock(LandingSpecialLocation.class);
		when(location.getId()).thenReturn(7);
		Map<Integer, LandingSpecialLocation> world = new LinkedHashMap<Integer, LandingSpecialLocation>();
		world.put(Integer.valueOf(7), location);

		new JdbcSpecialLandingRepository(dataSource).load(world);

		verify(statement).setInt(1, 7);
		verify(statement).setString(2, LandingSpecialStateType.NO_ACTIVE.toString());
		verify(statement).executeBatch();
	}

	@Test
	@DisplayName("Refuses a null special landing")
	void refusesANullSpecialLanding() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcSpecialLandingRepository(dataSource).save(null));
	}

	@Test
	@DisplayName("Reads what the web shop still owes a character")
	void readsUnclaimedRewards() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("unique")).thenReturn(11);
		when(rows.getInt("item_id")).thenReturn(186000030);
		when(rows.getLong("item_count")).thenReturn(3L);
		when(statement.executeQuery()).thenReturn(rows);

		List<RewardEntryItem> owed = new JdbcWebRewardRepository(dataSource).findUnclaimed(42);

		assertEquals(1, owed.size());
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Reports web rewards it could not read")
	void reportsUnreadableRewards() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		// The DAO swallowed this, so a character was told they were owed nothing
		// whenever the read failed.
		assertThrows(RepositoryException.class, () -> new JdbcWebRewardRepository(dataSource).findUnclaimed(42));
		verify(connection).close();
	}

	@Test
	@DisplayName("Marks several web rewards handed over in one batch")
	void marksSeveralRewardsClaimed() throws SQLException {
		new JdbcWebRewardRepository(dataSource).markClaimed(List.of(Integer.valueOf(11), Integer.valueOf(12)));

		verify(statement).setInt(1, 11);
		verify(statement).setInt(1, 12);
		verify(statement).executeBatch();
		verify(connection).commit();
	}

	@Test
	@DisplayName("Writes nothing when no web reward was handed over")
	void writesNothingForNoRewards() throws SQLException {
		new JdbcWebRewardRepository(dataSource).markClaimed(List.of());

		verify(connection, never()).prepareStatement(anyString());
	}

	@Test
	@DisplayName("Puts a web reward back on the pile")
	void putsARewardBack() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcWebRewardRepository(dataSource).markUnclaimed(11));
		verify(statement).setInt(1, 11);
	}

	@Test
	@DisplayName("Reads the scripts saved against a house")
	void readsHouseScripts() throws SQLException {
		noRows();

		// getSize() answers how many slots a house has, not how many carry a
		// script, so the read is checked on the house it asked for.
		assertNotNull(new JdbcHouseScriptRepository(dataSource).load(1234));
		verify(statement).setInt(1, 1234);
	}

	@Test
	@DisplayName("Reports house scripts it could not read")
	void reportsUnreadableHouseScripts() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		// The DAO caught this and did nothing at all, not even a log line, so the
		// house came back empty and the next save overwrote what was there.
		assertThrows(RepositoryException.class, () -> new JdbcHouseScriptRepository(dataSource).load(1234));
		verify(connection).close();
	}

	@Test
	@DisplayName("Saves an empty house script slot as null")
	void savesAnEmptyScriptAsNull() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcHouseScriptRepository(dataSource).add(1234, 2, null));

		verify(statement).setNull(3, Types.LONGNVARCHAR);
		verify(statement, never()).setString(anyInt(), anyString());
	}

	@Test
	@DisplayName("Answers false when a house script slot held nothing to clear")
	void answersFalseClearingAnEmptySlot() throws SQLException {
		when(statement.executeUpdate()).thenReturn(0);

		assertFalse(new JdbcHouseScriptRepository(dataSource).remove(1234, 2));
	}
}
