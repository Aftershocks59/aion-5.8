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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Covers the behaviour every cooldown repository shares, through the skill one.
 * <p>
 * The DAOs deleted a player's rows on one connection and inserted the new ones
 * on another, so a failure in between left the player with no cooldowns at all
 * rather than the ones they had. Three of the five went further and borrowed a
 * fresh connection for every single row.
 *
 * @author Oraion
 */
class JdbcPlayerSkillCooldownRepositoryTest {

	private Connection connection;
	private PreparedStatement statement;
	private Player player;
	private JdbcPlayerSkillCooldownRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);
		player = mock(Player.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(player.getObjectId()).thenReturn(42);

		repository = new JdbcPlayerSkillCooldownRepository(dataSource);
	}

	@Test
	@DisplayName("Applies a cooldown that has time left")
	void appliesALiveCooldown() throws SQLException {
		long future = System.currentTimeMillis() + 600_000L;
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getLong("reuse_delay")).thenReturn(future);
		when(rows.getInt("cooldown_id")).thenReturn(1234);
		when(statement.executeQuery()).thenReturn(rows);

		repository.load(player);

		verify(player).setSkillCoolDown(1234, future);
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Drops a cooldown that has already run out")
	void dropsAnExpiredCooldown() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getLong("reuse_delay")).thenReturn(System.currentTimeMillis() - 1L);
		when(statement.executeQuery()).thenReturn(rows);

		repository.load(player);

		verify(player, never()).setSkillCoolDown(anyInt(), anyLong());
	}

	@Test
	@DisplayName("Reports a load that failed")
	void reportsAFailedLoad() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		RepositoryException failure = assertThrows(RepositoryException.class, () -> repository.load(player));

		verify(connection).close();
		org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("42"), failure.getMessage());
	}

	@Test
	@DisplayName("Deletes then inserts inside one transaction")
	void replacesEverythingAtomically() throws SQLException {
		Map<Integer, Long> cooldowns = new LinkedHashMap<Integer, Long>();
		cooldowns.put(Integer.valueOf(1), Long.valueOf(System.currentTimeMillis() + 600_000L));
		when(player.getSkillCoolDowns()).thenReturn(cooldowns);

		repository.store(player);

		InOrder order = inOrder(connection, statement);
		order.verify(connection).setAutoCommit(false);
		order.verify(connection).prepareStatement(contains("DELETE"));
		order.verify(connection).prepareStatement(contains("INSERT"));
		order.verify(statement).executeBatch();
		order.verify(connection).commit();
		order.verify(connection).setAutoCommit(true);
	}

	@Test
	@DisplayName("Uses one connection however many cooldowns there are")
	void usesOneConnectionForEveryRow() throws SQLException {
		Map<Integer, Long> cooldowns = new LinkedHashMap<Integer, Long>();
		long future = System.currentTimeMillis() + 600_000L;
		for (int i = 1; i <= 20; i++) {
			cooldowns.put(Integer.valueOf(i), Long.valueOf(future));
		}
		when(player.getSkillCoolDowns()).thenReturn(cooldowns);

		repository.store(player);

		// Three of the five DAOs opened a connection per row.
		verify(statement, times(20)).addBatch();
		verify(connection).close();
	}

	@Test
	@DisplayName("Leaves the stored cooldowns alone when the insert fails")
	void rollsBackAFailedStore() throws SQLException {
		Map<Integer, Long> cooldowns = new LinkedHashMap<Integer, Long>();
		cooldowns.put(Integer.valueOf(1), Long.valueOf(System.currentTimeMillis() + 600_000L));
		when(player.getSkillCoolDowns()).thenReturn(cooldowns);
		when(statement.executeBatch()).thenThrow(new SQLException("constraint violated"));

		assertThrows(RepositoryException.class, () -> repository.store(player));

		// Without the rollback the delete would have landed on its own.
		verify(connection).rollback();
		verify(connection, never()).commit();
	}

	@Test
	@DisplayName("Skips a cooldown too short to be worth storing")
	void skipsAShortCooldown() throws SQLException {
		Map<Integer, Long> cooldowns = new LinkedHashMap<Integer, Long>();
		cooldowns.put(Integer.valueOf(1), Long.valueOf(System.currentTimeMillis() + 5_000L));
		when(player.getSkillCoolDowns()).thenReturn(cooldowns);

		repository.store(player);

		verify(statement, never()).addBatch();
		verify(statement, never()).executeBatch();
	}

	@Test
	@DisplayName("Still clears the stored rows when the player holds none")
	void clearsWhenNothingIsHeld() throws SQLException {
		when(player.getSkillCoolDowns()).thenReturn(null);

		repository.store(player);

		verify(connection).prepareStatement(contains("DELETE"));
		verify(statement).setInt(eq(1), eq(42));
		verify(connection).commit();
	}

	@Test
	@DisplayName("Refuses to be built without a data source")
	void refusesANullDataSource() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcPlayerSkillCooldownRepository(null));
	}
}
