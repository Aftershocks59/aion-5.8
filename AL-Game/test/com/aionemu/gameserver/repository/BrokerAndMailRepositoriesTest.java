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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.aionemu.gameserver.model.gameobjects.BrokerItem;
import com.aionemu.gameserver.model.gameobjects.Letter;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.model.items.storage.StorageType;

/**
 * Covers the broker listings and the mailboxes.
 *
 * @author Oraion
 */
class BrokerAndMailRepositoriesTest {

	private DataSource dataSource;
	private Connection connection;
	private PreparedStatement statement;
	private InventoryRepository inventories;
	private ItemStoneRepository itemStones;

	@BeforeEach
	void setUp() throws SQLException {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);
		inventories = mock(InventoryRepository.class);
		itemStones = mock(ItemStoneRepository.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(inventories.loadStorageItems(any(StorageType.class))).thenReturn(List.of());
		when(inventories.loadStorageItems(org.mockito.ArgumentMatchers.anyInt(), any(StorageType.class)))
				.thenReturn(List.of());
	}

	private void noRows() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);
	}

	private JdbcBrokerRepository brokers() {
		return new JdbcBrokerRepository(dataSource, inventories, itemStones);
	}

	private JdbcMailRepository mails() {
		return new JdbcMailRepository(dataSource, inventories, itemStones);
	}

	@Test
	@DisplayName("Reads the broker items through the inventory repository")
	void readsBrokerItemsThroughTheInventory() throws SQLException {
		noRows();

		assertEquals(0, brokers().findAll().size());

		// The DAO had its own copy of the thirty-odd column reader.
		verify(inventories).loadStorageItems(StorageType.BROKER);
		verify(itemStones).load(List.of());
	}

	@Test
	@DisplayName("Asks the database whether one item is still on sale")
	void asksWhetherAnItemIsOnSale() throws SQLException {
		noRows();

		assertFalse(brokers().isStillOnSale(700001));
		verify(statement).setInt(1, 700001);
	}

	@Test
	@DisplayName("Leaves a broker listing pending when its write failed")
	void leavesAFailedListingPending() throws SQLException {
		BrokerItem listing = mock(BrokerItem.class);
		when(listing.getPersistentState()).thenReturn(PersistentState.DELETED);
		when(listing.getItemUniqueId()).thenReturn(700001);
		when(statement.executeUpdate()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> brokers().save(listing));

		verify(listing, never()).setPersistentState(PersistentState.UPDATED);
	}

	@Test
	@DisplayName("Writes nothing for a broker listing that has not changed")
	void writesNothingForAnUnchangedListing() throws SQLException {
		BrokerItem listing = mock(BrokerItem.class);
		when(listing.getPersistentState()).thenReturn(PersistentState.UPDATED);

		assertFalse(brokers().save(listing));
		verify(connection, never()).prepareStatement(anyString());
	}

	@Test
	@DisplayName("Refuses a null broker listing")
	void refusesANullListing() {
		assertThrows(IllegalArgumentException.class, () -> brokers().save(null));
	}

	@Test
	@DisplayName("Asks the database for one unread letter rather than a hundred")
	void asksForOneUnreadLetter() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(statement.executeQuery()).thenReturn(rows);

		// The DAO read a hundred letters and walked them looking for an unread one,
		// leaking its result set the moment it found one.
		assertTrue(mails().hasUnread(42));

		verify(statement).setInt(1, 42);
		verify(rows, never()).getInt("unread");
	}

	@Test
	@DisplayName("Answers no unread post for a character with none")
	void answersNoUnreadPost() throws SQLException {
		noRows();

		assertFalse(mails().hasUnread(42));
	}

	@Test
	@DisplayName("Walks the used letter ids forward")
	void walksTheLetterIds() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("mail_unique_id")).thenReturn(800001, 800002);
		when(statement.executeQuery()).thenReturn(rows);

		assertArrayEquals(new int[] { 800001, 800002 }, mails().findUsedIds());
		verify(rows, never()).last();
	}

	@Test
	@DisplayName("Reads the attachments through the inventory repository")
	void readsAttachmentsThroughTheInventory() throws SQLException {
		com.aionemu.gameserver.model.gameobjects.player.Player player = mock(
				com.aionemu.gameserver.model.gameobjects.player.Player.class);
		when(player.getObjectId()).thenReturn(42);
		noRows();

		assertNull(mails().load(player).getLetterFromMailbox(1));

		verify(inventories).loadStorageItems(42, StorageType.MAILBOX);
		// Nothing to socket, so the stones are left alone entirely.
		verify(itemStones, never()).load(any());
	}

	@Test
	@DisplayName("Leaves a letter pending when its write failed")
	void leavesAFailedLetterPending() throws SQLException {
		Letter letter = mock(Letter.class);
		when(letter.getLetterPersistentState()).thenReturn(PersistentState.UPDATE_REQUIRED);
		when(letter.getLetterType()).thenReturn(com.aionemu.gameserver.model.gameobjects.LetterType.NORMAL);
		when(statement.executeUpdate()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class,
				() -> mails().save(new Timestamp(1_700_000_000_000L), letter));

		// The DAO did this whatever happened, so a lost letter was never written
		// again.
		verify(letter, never()).setPersistState(PersistentState.UPDATED);
	}

	@Test
	@DisplayName("Throws a letter away")
	void throwsALetterAway() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(mails().remove(800001));
		verify(statement).setInt(1, 800001);
	}

	@Test
	@DisplayName("Records how much post an offline character is holding")
	void recordsTheOfflineCounter() throws SQLException {
		PlayerCommonData recipient = mock(PlayerCommonData.class);
		when(recipient.getMailboxLetters()).thenReturn(3);
		when(recipient.getName()).thenReturn("Someone");
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(mails().setOfflineCounter(recipient));

		verify(statement).setInt(1, 3);
		verify(statement).setString(2, "Someone");
	}

	@Test
	@DisplayName("Refuses a null letter")
	void refusesANullLetter() {
		assertThrows(IllegalArgumentException.class, () -> mails().save(new Timestamp(0L), null));
	}
}
