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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.aionemu.gameserver.model.gameobjects.player.CraftCooldownList;
import com.aionemu.gameserver.model.gameobjects.player.HouseObjectCooldownList;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PortalCooldownItem;
import com.aionemu.gameserver.model.gameobjects.player.PortalCooldownList;
import com.aionemu.gameserver.model.items.ItemCooldown;

/**
 * Covers what each cooldown repository reads and where it writes.
 * <p>
 * The behaviour they share is settled in
 * {@link JdbcPlayerSkillCooldownRepositoryTest}. What is left per repository is
 * its table, its columns and how a row becomes something the player holds, which
 * is exactly what a careless rename would break in silence.
 *
 * @author Oraion
 */
class CooldownRepositoryMappingTest {

	private static final long FUTURE = System.currentTimeMillis() + 600_000L;

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
	@DisplayName("Reads the item cooldowns with their use delay")
	void mapsAnItemCooldown() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getLong("reuse_time")).thenReturn(FUTURE);
		when(rows.getInt("delay_id")).thenReturn(7);
		when(rows.getInt("use_delay")).thenReturn(3);
		when(statement.executeQuery()).thenReturn(rows);
		when(player.getEffectController())
				.thenReturn(mock(com.aionemu.gameserver.controllers.effect.PlayerEffectController.class));

		new JdbcItemCooldownRepository(dataSource).load(player);

		verify(player).addItemCoolDown(7, FUTURE, 3);
		verify(connection).prepareStatement(
				"SELECT `delay_id`,`use_delay`,`reuse_time` FROM `item_cooldowns` WHERE `player_id` = ?");
	}

	@Test
	@DisplayName("Queues the item cooldowns with their use delay")
	void writesAnItemCooldown() throws SQLException {
		Map<Integer, ItemCooldown> held = new java.util.LinkedHashMap<Integer, ItemCooldown>();
		ItemCooldown cooldown = mock(ItemCooldown.class);
		when(cooldown.getReuseTime()).thenReturn(FUTURE);
		when(cooldown.getUseDelay()).thenReturn(3);
		held.put(Integer.valueOf(7), cooldown);
		when(player.getItemCoolDowns()).thenReturn(held);

		new JdbcItemCooldownRepository(dataSource).store(player);

		verify(statement).setInt(2, 7);
		verify(statement).setInt(3, 3);
		verify(statement).setLong(4, FUTURE);
	}

	@Test
	@DisplayName("Hands the craft cooldowns to the player as one map")
	void mapsACraftCooldown() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getLong("reuse_time")).thenReturn(FUTURE);
		when(rows.getInt("delay_id")).thenReturn(11);
		when(statement.executeQuery()).thenReturn(rows);
		CraftCooldownList list = mock(CraftCooldownList.class);
		when(player.getCraftCooldownList()).thenReturn(list);

		new JdbcCraftCooldownRepository(dataSource).load(player);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<Integer, Long>> captured = ArgumentCaptor.forClass(Map.class);
		verify(list).setCraftCoolDowns(captured.capture());
		assertEquals(1, captured.getValue().size());
		assertEquals(Long.valueOf(FUTURE), captured.getValue().get(Integer.valueOf(11)));
	}

	@Test
	@DisplayName("Drops a craft cooldown with under a second left")
	void dropsANearlyFinishedCraftCooldown() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getLong("reuse_time")).thenReturn(System.currentTimeMillis() + 500L);
		when(statement.executeQuery()).thenReturn(rows);
		CraftCooldownList list = mock(CraftCooldownList.class);
		when(player.getCraftCooldownList()).thenReturn(list);

		new JdbcCraftCooldownRepository(dataSource).load(player);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<Integer, Long>> captured = ArgumentCaptor.forClass(Map.class);
		verify(list).setCraftCoolDowns(captured.capture());
		assertTrue(captured.getValue().isEmpty());
	}

	@Test
	@DisplayName("Hands the house object cooldowns to the player as one map")
	void mapsAHouseObjectCooldown() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getLong("reuse_time")).thenReturn(FUTURE);
		when(rows.getInt("object_id")).thenReturn(21);
		when(statement.executeQuery()).thenReturn(rows);
		HouseObjectCooldownList list = mock(HouseObjectCooldownList.class);
		when(player.getHouseObjectCooldownList()).thenReturn(list);

		new JdbcHouseObjectCooldownRepository(dataSource).load(player);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<Integer, Long>> captured = ArgumentCaptor.forClass(Map.class);
		verify(list).setHouseObjectCooldowns(captured.capture());
		assertEquals(Long.valueOf(FUTURE), captured.getValue().get(Integer.valueOf(21)));
	}

	@Test
	@DisplayName("Keeps the entry count with a portal cooldown")
	void mapsAPortalCooldown() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("world_id")).thenReturn(300100000);
		when(rows.getLong("reuse_time")).thenReturn(FUTURE);
		when(rows.getInt("entry_count")).thenReturn(2);
		when(statement.executeQuery()).thenReturn(rows);
		PortalCooldownList list = mock(PortalCooldownList.class);
		when(player.getPortalCooldownList()).thenReturn(list);

		new JdbcPortalCooldownRepository(dataSource).load(player);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<Integer, PortalCooldownItem>> captured = ArgumentCaptor.forClass(Map.class);
		verify(list).setPortalCoolDowns(captured.capture());
		PortalCooldownItem stored = captured.getValue().get(Integer.valueOf(300100000));
		assertEquals(2, stored.getEntryCount());
		assertEquals(FUTURE, stored.getCooldown());
	}

	@Test
	@DisplayName("Writes each kind to its own table")
	void writesToItsOwnTable() throws SQLException {
		when(player.getSkillCoolDowns()).thenReturn(null);
		new JdbcPlayerSkillCooldownRepository(dataSource).store(player);
		verify(connection).prepareStatement("DELETE FROM `player_cooldowns` WHERE `player_id` = ?");

		when(player.getItemCoolDowns()).thenReturn(null);
		new JdbcItemCooldownRepository(dataSource).store(player);
		verify(connection).prepareStatement("DELETE FROM `item_cooldowns` WHERE `player_id` = ?");

		CraftCooldownList crafts = mock(CraftCooldownList.class);
		when(player.getCraftCooldownList()).thenReturn(crafts);
		new JdbcCraftCooldownRepository(dataSource).store(player);
		verify(connection).prepareStatement("DELETE FROM `craft_cooldowns` WHERE `player_id` = ?");

		PortalCooldownList portals = mock(PortalCooldownList.class);
		when(player.getPortalCooldownList()).thenReturn(portals);
		new JdbcPortalCooldownRepository(dataSource).store(player);
		verify(connection).prepareStatement("DELETE FROM `portal_cooldowns` WHERE `player_id` = ?");

		HouseObjectCooldownList houseObjects = mock(HouseObjectCooldownList.class);
		when(player.getHouseObjectCooldownList()).thenReturn(houseObjects);
		new JdbcHouseObjectCooldownRepository(dataSource).store(player);
		verify(connection).prepareStatement("DELETE FROM `house_object_cooldowns` WHERE `player_id` = ?");
	}
}
