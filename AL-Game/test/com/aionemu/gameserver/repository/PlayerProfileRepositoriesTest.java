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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.BindPointPosition;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;

/**
 * Covers what a character carries about itself: how it looks, where it revives,
 * who it knows, what it has earned and what it can craft.
 * <p>
 * The appearance is the one worth watching. Sixty-two sliders, and the DAO spelt
 * the column list, the question marks and the sixty-two binds out three separate
 * times in three places that had to agree. Here they come from one list, and
 * what a round trip has to preserve is settled below.
 *
 * @author Oraion
 */
class PlayerProfileRepositoriesTest {

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

	@Test
	@DisplayName("Asks for every appearance column it binds")
	void appearanceColumnsMatchTheBinds() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		new JdbcPlayerAppearanceRepository(dataSource).save(42, new PlayerAppearance());

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(connection).prepareStatement(sql.capture());
		// The character, sixty-one sliders and the height, plus the table name.
		long columns = sql.getValue().chars().filter(c -> c == (char) 96).count() / 2 - 1;
		long placeholders = sql.getValue().chars().filter(c -> c == '?').count();
		assertEquals(63, columns);
		assertEquals(columns, placeholders);
	}

	@Test
	@DisplayName("Binds the character first and the height last")
	void appearanceBindsInOrder() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);
		PlayerAppearance appearance = new PlayerAppearance();
		appearance.setVoice(7);
		appearance.setHeight(1.25f);

		new JdbcPlayerAppearanceRepository(dataSource).save(42, appearance);

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 7);
		verify(statement).setFloat(63, 1.25f);
	}

	@Test
	@DisplayName("Reads an appearance back with its sliders")
	void readsAnAppearance() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getInt("voice")).thenReturn(7);
		when(rows.getInt("nose")).thenReturn(3);
		when(rows.getFloat("height")).thenReturn(1.25f);
		when(statement.executeQuery()).thenReturn(rows);

		PlayerAppearance appearance = new JdbcPlayerAppearanceRepository(dataSource).find(42);

		assertEquals(7, appearance.getVoice());
		assertEquals(3, appearance.getNose());
		assertEquals(1.25f, appearance.getHeight());
	}

	@Test
	@DisplayName("Refuses a null appearance")
	void refusesANullAppearance() {
		assertThrows(IllegalArgumentException.class,
				() -> new JdbcPlayerAppearanceRepository(dataSource).save(42, null));
	}

	@Test
	@DisplayName("Leaves the bind point alone when it has not moved")
	void skipsAnUnchangedBindPoint() throws SQLException {
		BindPointPosition bindPoint = new BindPointPosition(110010000, 1f, 2f, 3f, (byte) 0);
		bindPoint.setPersistentState(PersistentState.UPDATED);
		when(player.getBindPoint()).thenReturn(bindPoint);

		assertFalse(new JdbcPlayerBindPointRepository(dataSource).store(player));
		verify(connection, times(0)).prepareStatement(anyString());
	}

	@Test
	@DisplayName("Writes a bind point that moved, then marks it saved")
	void writesAMovedBindPoint() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);
		BindPointPosition bindPoint = new BindPointPosition(110010000, 1f, 2f, 3f, (byte) 4);
		bindPoint.setPersistentState(PersistentState.UPDATE_REQUIRED);
		when(player.getBindPoint()).thenReturn(bindPoint);

		assertTrue(new JdbcPlayerBindPointRepository(dataSource).store(player));

		verify(statement).setInt(2, 110010000);
		verify(statement).setByte(6, (byte) 4);
		assertEquals(PersistentState.UPDATED, bindPoint.getPersistentState());
	}

	@Test
	@DisplayName("Records a friendship in both directions")
	void recordsAFriendshipBothWays() throws SQLException {
		when(statement.executeBatch()).thenReturn(new int[] { 1, 1 });

		assertTrue(new JdbcPlayerSocialRepository(dataSource).addFriend(1, 2));

		verify(statement, times(2)).addBatch();
		// Both rows in one transaction: a half-recorded friendship shows on one side.
		verify(connection).commit();
	}

	@Test
	@DisplayName("Rolls a friendship back when only one side lands")
	void rollsBackAHalfFriendship() throws SQLException {
		when(statement.executeBatch()).thenThrow(new SQLException("constraint violated"));

		assertThrows(RepositoryException.class, () -> new JdbcPlayerSocialRepository(dataSource).addFriend(1, 2));

		verify(connection).rollback();
	}

	@Test
	@DisplayName("Skips a friend whose character no longer exists")
	void skipsAMissingFriend() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("friend")).thenReturn(99);
		when(statement.executeQuery()).thenReturn(rows);
		when(player.getName()).thenReturn("Maeron");

		// One dangling row must not cost the whole list, nor throw.
		assertEquals(0, new JdbcPlayerSocialRepository(dataSource)
				.findFriends(player, objectId -> null).getSize());
	}

	@Test
	@DisplayName("Names a blocked character through the lookup it was given")
	void namesABlockedCharacter() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("blocked_player")).thenReturn(99);
		when(rows.getString("reason")).thenReturn("spam");
		when(statement.executeQuery()).thenReturn(rows);
		PlayerCommonData blocked = mock(PlayerCommonData.class);

		assertEquals(1, new JdbcPlayerSocialRepository(dataSource)
				.findBlocked(player, objectId -> blocked).getSize());
	}

	@Test
	@DisplayName("Reads the recipes a character knows")
	void readsRecipes() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("recipe_id")).thenReturn(11, 22);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(2, new JdbcPlayerRecipeRepository(dataSource).findAll(42).getRecipeList().size());
	}

	@Test
	@DisplayName("Reads the titles a character holds from its own table")
	void readsTitles() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(0, new JdbcPlayerTitleRepository(dataSource).findAll(42).size());
		verify(statement).setInt(1, 42);
		verify(connection).prepareStatement(
				"SELECT `title_id`,`remaining` FROM `player_titles` WHERE `player_id` = ?");
	}
	@Test
	@DisplayName("Reports a profile it could not read")
	void reportsUnreadableProfile() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcPlayerRecipeRepository(dataSource).findAll(42));
		verify(connection).close();
	}

	@Test
	@DisplayName("Quotes the tables it writes")
	void quotesItsTables() throws SQLException {
		new JdbcPlayerTitleRepository(dataSource).remove(42, 5);

		verify(connection).prepareStatement(contains("`player_titles`"));
	}
}
