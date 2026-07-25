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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.ItemData;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.base.BaseLocation;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.skill.linked_skill.EquippedStigmasEntry;
import com.aionemu.gameserver.model.skill.linked_skill.PlayerEquippedStigmaList;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;

/**
 * Covers the bases, the transformations, the equipped stigmas and the quests.
 *
 * @author Oraion
 */
class PlayerProgressRepositoriesTest {

	private DataSource dataSource;
	private Connection connection;
	private PreparedStatement statement;

	private ItemData itemData;

	@BeforeEach
	void setUp() throws SQLException {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);

		// A stigma answers its name from the static data rather than from the
		// column it was read out of, so the repository needs that data present.
		itemData = DataManager.ITEM_DATA;
		ItemTemplate template = mock(ItemTemplate.class);
		when(template.getName()).thenReturn("Stigma");
		ItemData items = mock(ItemData.class);
		when(items.getItemTemplate(140000001)).thenReturn(template);
		DataManager.ITEM_DATA = items;
	}

	@AfterEach
	void tearDown() {
		DataManager.ITEM_DATA = itemData;
	}

	private ResultSet emptyRows() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);
		return rows;
	}

	@Test
	@DisplayName("Ignores a stored base the world does not have")
	void ignoresAnUnknownBase() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("id")).thenReturn(9999);
		when(rows.getString("race")).thenReturn("ELYOS");
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO dereferenced the missing base and lost every remaining row to
		// its catch.
		new JdbcBaseRepository(dataSource).load(new LinkedHashMap<Integer, BaseLocation>());

		verify(connection).commit();
	}

	@Test
	@DisplayName("Creates a row for a base that has none")
	void createsMissingBaseRows() throws SQLException {
		emptyRows();

		BaseLocation location = mock(BaseLocation.class);
		when(location.getId()).thenReturn(21);
		Map<Integer, BaseLocation> world = new LinkedHashMap<Integer, BaseLocation>();
		world.put(Integer.valueOf(21), location);

		new JdbcBaseRepository(dataSource).load(world);

		verify(statement).setInt(1, 21);
		verify(statement).setString(2, Race.NPC.toString());
		verify(statement).executeBatch();
	}

	@Test
	@DisplayName("Reports bases it could not read")
	void reportsUnreadableBases() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class,
				() -> new JdbcBaseRepository(dataSource).load(new LinkedHashMap<Integer, BaseLocation>()));
	}

	@Test
	@DisplayName("Refuses a null base")
	void refusesANullBase() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcBaseRepository(dataSource).save(null));
	}

	@Test
	@DisplayName("Forgets the transformation of a character")
	void forgetsATransformation() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcPlayerTransformRepository(dataSource).remove(42));
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Answers false when a character had no transformation to forget")
	void answersFalseWithoutATransformation() throws SQLException {
		when(statement.executeUpdate()).thenReturn(0);

		assertFalse(new JdbcPlayerTransformRepository(dataSource).remove(42));
	}

	@Test
	@DisplayName("Records the shape a character has taken")
	void recordsATransformation() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(new JdbcPlayerTransformRepository(dataSource).save(42, 7, 188000001));

		verify(statement).setInt(1, 42);
		verify(statement).setInt(2, 7);
		verify(statement).setInt(3, 188000001);
	}

	@Test
	@DisplayName("Reports a transformation it could not write")
	void reportsAnUnwritableTransformation() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcPlayerTransformRepository(dataSource).save(42, 7, 1));
		verify(connection).close();
	}

	@Test
	@DisplayName("Refuses to read a transformation for a null character")
	void refusesANullTransformedCharacter() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcPlayerTransformRepository(dataSource).load(null));
	}

	@Test
	@DisplayName("Reads the stigmas a character wears as already saved")
	void readsWornStigmas() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("item_id")).thenReturn(140000001, 140000002);
		when(rows.getString("item_name")).thenReturn("first", "second");
		when(statement.executeQuery()).thenReturn(rows);

		PlayerEquippedStigmaList worn = new JdbcEquippedStigmaRepository(dataSource).load(42);

		EquippedStigmasEntry[] entries = worn.getAllItems();
		assertEquals(2, entries.length);
		for (EquippedStigmasEntry entry : entries) {
			assertEquals(PersistentState.UPDATED, entry.getPersistentState());
		}
	}

	@Test
	@DisplayName("Reports stigmas it could not read")
	void reportsUnreadableStigmas() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcEquippedStigmaRepository(dataSource).load(42));
		verify(connection).close();
	}

	@Test
	@DisplayName("Binds a changed stigma in the order the statement declares")
	void bindsTheStigmaUpdateInOrder() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		EquippedStigmasEntry changed = new EquippedStigmasEntry(140000001, "Stigma", PersistentState.UPDATE_REQUIRED);
		when(player.getEquipedStigmaList()).thenReturn(listOf(changed));

		new JdbcEquippedStigmaRepository(dataSource).save(player);

		// The DAO bound the character id into item_name and filtered on the name as
		// if it were the character id, so no update ever matched a row.
		verify(statement).setInt(1, 140000001);
		verify(statement).setString(2, "Stigma");
		verify(statement).setInt(3, 42);
		verify(statement).executeBatch();
	}

	@Test
	@DisplayName("Leaves a stigma pending when its write failed")
	void leavesAFailedStigmaPending() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		EquippedStigmasEntry pending = new EquippedStigmasEntry(140000001, "Stigma", PersistentState.NEW);
		when(player.getEquipedStigmaList()).thenReturn(listOf(pending));
		when(statement.executeBatch()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcEquippedStigmaRepository(dataSource).save(player));

		// The DAO marked them saved in a finally, so a write that never landed
		// still looked like it had and was never retried.
		assertEquals(PersistentState.NEW, pending.getPersistentState());
		verify(connection).rollback();
	}

	@Test
	@DisplayName("Writes nothing when a character wears no stigma")
	void writesNothingWithoutStigmas() throws SQLException {
		Player player = mock(Player.class);
		when(player.getEquipedStigmaList()).thenReturn(listOf());

		new JdbcEquippedStigmaRepository(dataSource).save(player);

		verify(connection, never()).prepareStatement(anyString());
	}

	@Test
	@DisplayName("Refuses to store the stigmas of a null character")
	void refusesANullStigmaCharacter() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcEquippedStigmaRepository(dataSource).save(null));
	}

	@Test
	@DisplayName("Refuses to read the quests of a null character")
	void refusesANullQuestCharacter() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcPlayerQuestRepository(dataSource).load(null));
	}

	@Test
	@DisplayName("Reports quests it could not read")
	void reportsUnreadableQuests() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcPlayerQuestRepository(dataSource).load(player));
		verify(connection).close();
	}

	@Test
	@DisplayName("Asks for the quests of the character it was given")
	void asksForTheRightQuests() throws SQLException {
		Player player = mock(Player.class);
		when(player.getObjectId()).thenReturn(42);
		emptyRows();

		assertEquals(0, new JdbcPlayerQuestRepository(dataSource).load(player).getAllQuestState().size());
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Refuses to store the quests of a null character")
	void refusesANullQuestSave() {
		assertThrows(IllegalArgumentException.class, () -> new JdbcPlayerQuestRepository(dataSource).save(null));
	}

	private static PlayerEquippedStigmaList listOf(EquippedStigmasEntry... entries) {
		return new PlayerEquippedStigmaList(List.of(entries));
	}
}
