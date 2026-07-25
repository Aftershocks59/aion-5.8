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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.sql.Timestamp;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerSweep;
import com.aionemu.gameserver.model.skill.PlayerSkillEntry;
import com.aionemu.gameserver.model.skill.PlayerSkillList;
import com.aionemu.gameserver.model.tasks.TaskFromDB;

/**
 * Covers the scheduled tasks, the creativity points, the Shugo Sweep boards and
 * the skills.
 *
 * @author Oraion
 */
class SkillAndTaskRepositoriesTest {

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
	@DisplayName("Reads the scheduled tasks")
	void readsTheScheduledTasks() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("id")).thenReturn(3);
		when(rows.getString("task_type")).thenReturn("SHUTDOWN");
		when(rows.getString("trigger_type")).thenReturn("FIXED_IN_TIME");
		when(rows.getString("trigger_param")).thenReturn("12:00:00");
		when(rows.getString("exec_param")).thenReturn("60 hello");
		when(statement.executeQuery()).thenReturn(rows);

		List<TaskFromDB> tasks = new JdbcScheduledTaskRepository(dataSource).findAll();

		// The DAO named six columns the table does not have, so every read threw
		// and the server started with no scheduled task at all.
		assertEquals(1, tasks.size());
		assertEquals(3, tasks.get(0).getId());
		assertEquals("SHUTDOWN", tasks.get(0).getName());
		assertEquals("FIXED_IN_TIME", tasks.get(0).getType());
		assertEquals("12:00:00", tasks.get(0).getStartTime());
		assertEquals(2, tasks.get(0).getParams().length);
	}

	@Test
	@DisplayName("Records the moment a task ran")
	void recordsWhenATaskRan() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcScheduledTaskRepository(dataSource).markActivated(3, 1_700_000_000_000L));

		verify(statement).setTimestamp(1, new Timestamp(1_700_000_000_000L));
		verify(statement).setInt(2, 3);
	}

	@Test
	@DisplayName("Reports scheduled tasks it could not read")
	void reportsUnreadableTasks() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcScheduledTaskRepository(dataSource).findAll());
		verify(connection).close();
	}

	@Test
	@DisplayName("Reads the creativity points as already saved")
	void readsTheCreativityPoints() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("slot")).thenReturn(1, 2);
		when(rows.getInt("point")).thenReturn(40, 55);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(2, new JdbcCreativityPointRepository(dataSource).load(42).size());
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Reports creativity points it could not read")
	void reportsUnreadableCreativityPoints() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcCreativityPointRepository(dataSource).load(42));
		verify(connection).close();
	}

	@Test
	@DisplayName("Refuses to store the creativity points of a null character")
	void refusesANullCreativityCharacter() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcCreativityPointRepository(dataSource).save(null));
	}

	@Test
	@DisplayName("Leaves a character without a board alone")
	void leavesAnUnknownBoardAlone() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		noRows();

		new JdbcShugoSweepRepository(dataSource).load(player);

		verify(player, never()).setPlayerShugoSweep(null);
	}

	@Test
	@DisplayName("Writes a Shugo Sweep board that has moved")
	void writesAMovedBoard() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		PlayerSweep board = new PlayerSweep(4, 2, 1, 0, 0, 3);
		board.setPersistentState(PersistentState.UPDATE_REQUIRED);
		when(player.getPlayerShugoSweep()).thenReturn(board);
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcShugoSweepRepository(dataSource).save(player));

		verify(statement).setInt(2, 4);
		verify(statement).setInt(7, 42);
		assertEquals(PersistentState.UPDATED, board.getPersistentState());
	}

	@Test
	@DisplayName("Writes nothing for a Shugo Sweep board that has not moved")
	void writesNothingForAnUnchangedBoard() throws SQLException {
		Player player = mock(Player.class);
		PlayerSweep board = new PlayerSweep(4, 2, 1, 0, 0, 3);
		board.setPersistentState(PersistentState.UPDATED);
		when(player.getPlayerShugoSweep()).thenReturn(board);

		assertFalse(new JdbcShugoSweepRepository(dataSource).save(player));
		verify(connection, never()).prepareStatement(anyString());
	}

	@Test
	@DisplayName("Leaves a Shugo Sweep board pending when its write failed")
	void leavesAFailedBoardPending() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		PlayerSweep board = new PlayerSweep(4, 2, 1, 0, 0, 3);
		board.setPersistentState(PersistentState.UPDATE_REQUIRED);
		when(player.getPlayerShugoSweep()).thenReturn(board);
		when(statement.executeUpdate()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcShugoSweepRepository(dataSource).save(player));

		// The DAO marked it saved whatever happened, so a board whose write had
		// failed was never retried. A board that has never been written keeps its
		// NEW state rather than taking the one it was handed.
		assertNotEquals(PersistentState.UPDATED, board.getPersistentState());
	}

	@Test
	@DisplayName("Reads the skills a character knows as already saved")
	void readsKnownSkills() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("skill_id")).thenReturn(30001, 30002);
		when(rows.getInt("skill_level")).thenReturn(1, 2);
		when(statement.executeQuery()).thenReturn(rows);

		PlayerSkillList known = new JdbcPlayerSkillRepository(dataSource).load(42);

		assertEquals(2, known.size());
		for (PlayerSkillEntry entry : known.getAllSkills()) {
			assertEquals(PersistentState.UPDATED, entry.getPersistentState());
		}
	}

	@Test
	@DisplayName("Writes a changed skill's level and skin in one statement")
	void writesLevelAndSkinTogether() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		PlayerSkillEntry changed = new PlayerSkillEntry(30001, false, false, 5, 990001, null, 60, true,
				PersistentState.UPDATE_REQUIRED);
		when(player.getSkillList()).thenReturn(listOf(changed));

		new JdbcPlayerSkillRepository(dataSource).save(player);

		// The DAO ran the same filter twice and wrote each changed skill's row two
		// times over.
		verify(statement).setInt(1, 5);
		verify(statement).setInt(2, 990001);
		verify(statement).setInt(6, 42);
		verify(statement).setInt(7, 30001);
		verify(statement).executeBatch();
	}

	@Test
	@DisplayName("Leaves a skill pending when its write failed")
	void leavesAFailedSkillPending() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		PlayerSkillEntry pending = new PlayerSkillEntry(30001, false, false, 1, 0, null, 0, false,
				PersistentState.NEW);
		when(player.getSkillList()).thenReturn(listOf(pending));
		when(statement.executeBatch()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcPlayerSkillRepository(dataSource).save(player));

		assertEquals(PersistentState.NEW, pending.getPersistentState());
		verify(connection).rollback();
	}

	@Test
	@DisplayName("Answers nothing about a skin the character does not have")
	void answersNothingAboutAMissingSkin() throws SQLException {
		noRows();

		// The DAO read the row without checking there was one and answered from a
		// catch.
		assertNull(new JdbcPlayerSkillRepository(dataSource).findSkinActivatedAt(42, 30001));
	}

	@Test
	@DisplayName("Answers no expiry for a skin the character does not have")
	void answersNoExpiryForAMissingSkin() throws SQLException {
		noRows();

		assertEquals(PlayerSkillRepository.NO_EXPIRY,
				new JdbcPlayerSkillRepository(dataSource).findSkinExpiry(42, 30001));
	}

	@Test
	@DisplayName("Refuses to store the skills of a null character")
	void refusesANullSkillCharacter() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcPlayerSkillRepository(dataSource).save(null));
	}

	private static PlayerSkillList listOf(PlayerSkillEntry... entries) {
		return new PlayerSkillList(List.of(entries));
	}
}
