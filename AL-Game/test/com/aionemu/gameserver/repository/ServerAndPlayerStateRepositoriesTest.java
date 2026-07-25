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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerSettings;
import com.aionemu.gameserver.model.stats.container.PlayerLifeStats;

/**
 * Covers the server's own variables and the per-character state that hangs off a
 * login: life stats and settings.
 * <p>
 * Two DAO habits are settled here. Reading a server variable parsed the stored
 * text inside a catch for SQLException only, so a hand-edited row escaped as an
 * unchecked throw from whatever asked for the time. And saving settings wrote
 * five rows on five connections, so a failure partway left a character with some
 * settings from this session and some from the last.
 *
 * @author Oraion
 */
class ServerAndPlayerStateRepositoriesTest {

	private DataSource dataSource;
	private Connection connection;
	private PreparedStatement statement;
	private Player player;

	@BeforeEach
	void setUp() throws SQLException {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);
		player = mock(Player.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(player.getObjectId()).thenReturn(42);
	}

	private void stubValue(String value) throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getString("value")).thenReturn(value);
		when(statement.executeQuery()).thenReturn(rows);
	}

	@Test
	@DisplayName("Reads a server variable as a number")
	void readsAServerVariable() throws SQLException {
		stubValue("1234");

		assertEquals(1234, new JdbcServerVariableRepository(dataSource).find("time"));
		verify(statement).setString(1, "time");
	}

	@Test
	@DisplayName("Answers zero for a variable never written")
	void answersZeroForAMissingVariable() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(0, new JdbcServerVariableRepository(dataSource).find("time"));
	}

	@Test
	@DisplayName("Answers zero for a variable that is not a number")
	void survivesAHandEditedVariable() throws SQLException {
		stubValue("yesterday");

		// The DAO let NumberFormatException escape: it caught SQLException only.
		assertEquals(0, new JdbcServerVariableRepository(dataSource).find("time"));
	}

	@Test
	@DisplayName("Reports a server variable it could not read")
	void reportsAnUnreadableVariable() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcServerVariableRepository(dataSource).find("time"));
	}

	@Test
	@DisplayName("Restores the life stats a character logged out with")
	void restoresLifeStats() throws SQLException {
		PlayerLifeStats lifeStats = mock(PlayerLifeStats.class);
		when(player.getLifeStats()).thenReturn(lifeStats);
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getInt("hp")).thenReturn(500);
		when(rows.getInt("mp")).thenReturn(300);
		when(rows.getInt("fp")).thenReturn(100);
		when(statement.executeQuery()).thenReturn(rows);

		new JdbcPlayerLifeStatRepository(dataSource).load(player);

		verify(lifeStats).setCurrentHp(500);
		verify(lifeStats).setCurrentMp(300);
		verify(lifeStats).setCurrentFp(100);
	}

	@Test
	@DisplayName("Creates the row for a character that has never been saved")
	void createsTheRowOnFirstLoad() throws SQLException {
		PlayerLifeStats lifeStats = mock(PlayerLifeStats.class);
		when(player.getLifeStats()).thenReturn(lifeStats);
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		new JdbcPlayerLifeStatRepository(dataSource).load(player);

		verify(connection).prepareStatement(contains("ON DUPLICATE KEY UPDATE"));
	}

	@Test
	@DisplayName("Writes the life stats whether the row existed or not")
	void writesLifeStatsEitherWay() throws SQLException {
		PlayerLifeStats lifeStats = mock(PlayerLifeStats.class);
		when(lifeStats.getCurrentHp()).thenReturn(500);
		when(player.getLifeStats()).thenReturn(lifeStats);

		new JdbcPlayerLifeStatRepository(dataSource).save(player);

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 500);
	}

	@Test
	@DisplayName("Leaves settings alone when nothing changed")
	void skipsUnchangedSettings() throws SQLException {
		PlayerSettings settings = new PlayerSettings();
		settings.setPersistentState(PersistentState.UPDATED);
		when(player.getPlayerSettings()).thenReturn(settings);

		new JdbcPlayerSettingsRepository(dataSource).save(player);

		verify(connection, never()).prepareStatement(anyString());
	}

	@Test
	@DisplayName("Writes every settings row in one transaction")
	void writesSettingsAtomically() throws SQLException {
		PlayerSettings settings = new PlayerSettings();
		settings.setPersistentState(PersistentState.UPDATE_REQUIRED);
		settings.setUiSettings(new byte[] { 1 });
		when(player.getPlayerSettings()).thenReturn(settings);

		new JdbcPlayerSettingsRepository(dataSource).save(player);

		// The DAO used one connection per row, so a failure split a character's
		// settings across two sessions.
		verify(connection).setAutoCommit(false);
		verify(statement).executeBatch();
		verify(connection).commit();
	}

	@Test
	@DisplayName("Skips a settings kind the character never set")
	void skipsAnUnsetSettingsKind() throws SQLException {
		PlayerSettings settings = new PlayerSettings();
		settings.setPersistentState(PersistentState.UPDATE_REQUIRED);
		when(player.getPlayerSettings()).thenReturn(settings);

		new JdbcPlayerSettingsRepository(dataSource).save(player);

		// Three layouts are null; only the two flag words are queued.
		verify(statement, times(2)).addBatch();
	}

	@Test
	@DisplayName("Reads a flag word back out of the blob column")
	void readsAFlagWordFromABlob() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("settings_type")).thenReturn(-1);
		when(rows.getBytes("settings")).thenReturn("7".getBytes(StandardCharsets.US_ASCII));
		when(statement.executeQuery()).thenReturn(rows);

		new JdbcPlayerSettingsRepository(dataSource).load(player);

		verify(player).setPlayerSettings(org.mockito.ArgumentMatchers.argThat(s -> s.getDisplay() == 7));
	}

	@Test
	@DisplayName("Survives a flag word that is not a number")
	void survivesAnUnreadableFlagWord() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("settings_type")).thenReturn(-2);
		when(rows.getBytes("settings")).thenReturn("nonsense".getBytes(StandardCharsets.US_ASCII));
		when(statement.executeQuery()).thenReturn(rows);

		new JdbcPlayerSettingsRepository(dataSource).load(player);

		verify(player).setPlayerSettings(org.mockito.ArgumentMatchers.argThat(s -> s.getDeny() == 0));
	}

	@Test
	@DisplayName("Reports settings it could not read")
	void reportsUnreadableSettings() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		RepositoryException failure = assertThrows(RepositoryException.class,
				() -> new JdbcPlayerSettingsRepository(dataSource).load(player));

		assertTrue(failure.getMessage().contains("42"), failure.getMessage());
	}
}
