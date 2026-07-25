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
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.AbyssRank;
import com.aionemu.gameserver.model.gameobjects.player.ranking.GoldArenaRank;
import com.aionemu.gameserver.model.gameobjects.player.ranking.TowerOfChallengeRank;

/**
 * Covers the abyss ranking and the seasonal competitions.
 *
 * @author Oraion
 */
class RankingRepositoriesTest {

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
	@DisplayName("Answers a fresh abyss standing for a character who never earned one")
	void answersAFreshAbyssStanding() throws SQLException {
		noRows();

		AbyssRank rank = new JdbcAbyssRankRepository(dataSource).load(42);

		assertEquals(PersistentState.NEW, rank.getPersistentState());
	}

	@Test
	@DisplayName("Strikes every character in one batch on one connection")
	void strikesEveryCharacterAtOnce() throws SQLException {
		when(statement.executeBatch()).thenReturn(new int[] { 1, 1, 1 });

		// The DAO opened a fresh connection inside its loop, overwriting the
		// statement each time, so only the last character was struck.
		assertEquals(3, new JdbcAbyssRankRepository(dataSource)
				.removeAll(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3))));

		verify(dataSource, times(1)).getConnection();
		verify(statement).setInt(1, 1);
		verify(statement).setInt(1, 2);
		verify(statement).setInt(1, 3);
		verify(connection).commit();
	}

	@Test
	@DisplayName("Strikes nobody when the list is empty")
	void strikesNobodyForAnEmptyList() throws SQLException {
		assertEquals(0, new JdbcAbyssRankRepository(dataSource).removeAll(List.of()));

		verify(dataSource, never()).getConnection();
	}

	@Test
	@DisplayName("Writes nothing for an abyss standing that has not changed")
	void writesNothingForAnUnchangedAbyssStanding() throws SQLException {
		AbyssRank rank = new AbyssRank(0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0L);
		rank.setPersistentState(PersistentState.UPDATED);

		assertFalse(new JdbcAbyssRankRepository(dataSource).save(42, rank));
		verify(connection, never()).prepareStatement(anyString());
	}

	@Test
	@DisplayName("Refuses a null abyss standing")
	void refusesANullAbyssStanding() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcAbyssRankRepository(dataSource).save(42, null));
	}

	@Test
	@DisplayName("Reports an abyss standing it could not read")
	void reportsAnUnreadableAbyssStanding() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcAbyssRankRepository(dataSource).load(42));
		verify(connection).close();
	}

	@Test
	@DisplayName("Keeps a gold arena position match across a reload")
	void keepsTheGoldArenaPositionMatch() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getInt("rank")).thenReturn(4);
		when(rows.getInt("position_match")).thenReturn(9);
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO read this one standing with a hard-coded zero where the position
		// match belonged, so it came back lost on every reload.
		GoldArenaRank rank = new JdbcSeasonRankingRepository(dataSource).loadGoldArena(42, 1);

		assertEquals(4, rank.getRank());
		assertEquals(9, rank.getPossitionMatch());
	}

	@Test
	@DisplayName("Reads a tower standing back in the order it was written")
	void readsTheTowerStandingBackInOrder() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getInt("rank")).thenReturn(3);
		when(rows.getInt("last_rank")).thenReturn(2);
		when(rows.getInt("points")).thenReturn(180);
		when(rows.getInt("last_points")).thenReturn(200);
		when(rows.getInt("high_points")).thenReturn(150);
		when(rows.getInt("low_points")).thenReturn(7);
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO wrote the current time into points and read points back as the
		// low rank, so the four numbers rotated on every round trip.
		TowerOfChallengeRank rank = new JdbcSeasonRankingRepository(dataSource).loadTower(42, 2);

		assertEquals(3, rank.getRank());
		assertEquals(2, rank.getBestRank());
		assertEquals(180, rank.getCurrentTime());
		assertEquals(200, rank.getLastTime());
		assertEquals(150, rank.getBestTime());
		assertEquals(7, rank.getLowRank());
	}

	@Test
	@DisplayName("Writes a tower standing in the order it is read back")
	void writesTheTowerStandingInOrder() throws SQLException {
		TowerOfChallengeRank rank = new TowerOfChallengeRank(3, 2, 7, 180, 200, 150);
		rank.setPersistentState(PersistentState.UPDATE_REQUIRED);
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcSeasonRankingRepository(dataSource).saveTower(42, rank));

		verify(statement).setInt(1, 3);
		verify(statement).setInt(2, 2);
		verify(statement).setInt(3, 180);
		verify(statement).setInt(4, 200);
		verify(statement).setInt(5, 150);
		verify(statement).setInt(6, 7);
	}

	@Test
	@DisplayName("Answers a fresh tower standing for a character who never competed")
	void answersAFreshTowerStanding() throws SQLException {
		noRows();

		assertEquals(PersistentState.NEW,
				new JdbcSeasonRankingRepository(dataSource).loadTower(42, 2).getPersistentState());
	}

	@Test
	@DisplayName("Writes nothing for a tower standing that has not changed")
	void writesNothingForAnUnchangedTowerStanding() throws SQLException {
		TowerOfChallengeRank rank = new TowerOfChallengeRank(0, 0, 0, 0, 0, 0);
		rank.setPersistentState(PersistentState.UPDATED);

		assertFalse(new JdbcSeasonRankingRepository(dataSource).saveTower(42, rank));
		verify(connection, never()).prepareStatement(anyString());
	}

	@Test
	@DisplayName("Refuses a null tower standing")
	void refusesANullTowerStanding() {
		assertThrows(IllegalArgumentException.class,
				() -> new JdbcSeasonRankingRepository(dataSource).saveTower(42, null));
	}

	@Test
	@DisplayName("Reports a leaderboard it could not read")
	void reportsAnUnreadableLeaderboard() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcSeasonRankingRepository(dataSource).findLeaderboard(2));
		verify(connection).close();
	}
}
