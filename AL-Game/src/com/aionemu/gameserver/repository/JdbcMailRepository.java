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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Letter;
import com.aionemu.gameserver.model.gameobjects.LetterType;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Mailbox;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.model.items.storage.StorageType;

/**
 * Reads and writes the letters waiting in each character's mailbox, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcMailRepository extends JdbcRepositorySupport implements MailRepository {

	/** How much post the client is shown at once. */
	private static final int MAILBOX_PAGE = 100;

	private static final String SELECT_USED_IDS = "SELECT `mail_unique_id` FROM `mail`";
	private static final String SELECT_FOR_PLAYER = "SELECT `mail_unique_id`,`mail_recipient_id`,`sender_name`,"
			+ "`mail_title`,`mail_message`,`unread`,`attached_item_id`,`attached_kinah_count`,`attached_ap_count`,"
			+ "`express`,`recieved_time` FROM `mail` WHERE `mail_recipient_id` = ?"
			+ " ORDER BY `recieved_time` LIMIT " + MAILBOX_PAGE;
	private static final String SELECT_UNREAD = "SELECT 1 FROM `mail` WHERE `mail_recipient_id` = ? AND `unread` = 1"
			+ " LIMIT 1";
	private static final String INSERT_ONE = "INSERT INTO `mail` (`mail_unique_id`,`mail_recipient_id`,`sender_name`,"
			+ "`mail_title`,`mail_message`,`unread`,`attached_item_id`,`attached_kinah_count`,`express`,"
			+ "`recieved_time`,`attached_ap_count`) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `mail` SET `unread` = ?, `attached_item_id` = ?,"
			+ " `attached_kinah_count` = ?, `express` = ?, `recieved_time` = ?, `attached_ap_count` = ?"
			+ " WHERE `mail_unique_id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `mail` WHERE `mail_unique_id` = ?";
	private static final String UPDATE_OFFLINE_COUNTER = "UPDATE `players` SET `mailbox_letters` = ?"
			+ " WHERE `name` = ?";

	private final InventoryRepository inventories;
	private final ItemStoneRepository itemStones;

	public JdbcMailRepository(DataSource dataSource, InventoryRepository inventories,
			ItemStoneRepository itemStones) {
		super(dataSource);
		this.inventories = inventories;
		this.itemStones = itemStones;
	}

	@Override
	public int[] findUsedIds() {
		List<Integer> used = new ArrayList<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_USED_IDS);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				used.add(Integer.valueOf(rows.getInt("mail_unique_id")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the letter ids already in use.", e);
		}

		// A plain forward walk, where the DAO asked for a scrollable cursor only to
		// count the rows first.
		int[] ids = new int[used.size()];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = used.get(i).intValue();
		}
		return ids;
	}

	@Override
	public Mailbox load(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot read the mailbox of a null character.");
		}

		int playerId = player.getObjectId();
		Mailbox mailbox = new Mailbox(player);

		// The attachments travel through the inventory repository, where the DAO
		// had its own copy of the thirty-odd column reader.
		Map<Integer, Item> attachments = new HashMap<Integer, Item>();
		for (Item item : inventories.loadStorageItems(playerId, StorageType.MAILBOX)) {
			attachments.put(Integer.valueOf(item.getObjectId()), item);
		}

		List<Item> needingStones = new ArrayList<Item>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_FOR_PLAYER)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					int attachedItemId = rows.getInt("attached_item_id");
					Item attached = attachedItemId == 0 ? null : attachments.get(Integer.valueOf(attachedItemId));
					if (attached != null && (attached.getItemTemplate().isArmor()
							|| attached.getItemTemplate().isWeapon())) {
						needingStones.add(attached);
					}

					Letter letter = new Letter(rows.getInt("mail_unique_id"), rows.getInt("mail_recipient_id"),
							attached, rows.getLong("attached_kinah_count"), rows.getLong("attached_ap_count"),
							rows.getString("mail_title"), rows.getString("mail_message"),
							rows.getString("sender_name"), rows.getTimestamp("recieved_time"),
							rows.getInt("unread") == 1, LetterType.getLetterTypeById(rows.getInt("express")));
					letter.setPersistState(PersistentState.UPDATED);
					mailbox.putLetterToMailbox(letter);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the mailbox of character " + playerId + ".", e);
		}

		// One pass for every attachment that can hold a stone, where the DAO asked
		// for one letter's worth at a time from inside its own result set.
		if (!needingStones.isEmpty()) {
			itemStones.load(needingStones);
		}

		return mailbox;
	}

	@Override
	public boolean hasUnread(int playerId) {
		// One row at most, where the DAO read a hundred letters and walked them
		// looking for an unread one, leaking its result set the moment it found it.
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_UNREAD)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to check the post of character " + playerId + ".", e);
		}
	}

	@Override
	public void save(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot store the mailbox of a null character.");
		}

		Mailbox mailbox = player.getMailbox();
		if (mailbox == null) {
			return;
		}

		Collection<Letter> letters = mailbox.getLetters();
		if (letters == null) {
			return;
		}
		for (Letter letter : new ArrayList<Letter>(letters)) {
			save(letter.getTimeStamp(), letter);
		}
	}

	@Override
	public boolean save(Timestamp at, Letter letter) {
		if (letter == null) {
			throw new IllegalArgumentException("Cannot store a null letter.");
		}

		int attachedItemId = letter.getAttachedItem() == null ? 0 : letter.getAttachedItem().getObjectId();
		boolean written;

		switch (letter.getLetterPersistentState()) {
			case NEW:
				written = write(INSERT_ONE, letter, statement -> {
					statement.setInt(1, letter.getObjectId());
					statement.setInt(2, letter.getRecipientId());
					statement.setString(3, letter.getSenderName());
					statement.setString(4, letter.getTitle());
					statement.setString(5, letter.getMessage());
					statement.setBoolean(6, letter.isUnread());
					statement.setInt(7, attachedItemId);
					statement.setLong(8, letter.getAttachedKinah());
					statement.setInt(9, letter.getLetterType().getId());
					statement.setTimestamp(10, at);
					statement.setLong(11, letter.getAttachedAp());
				});
				break;
			case UPDATE_REQUIRED:
				written = write(UPDATE_ONE, letter, statement -> {
					statement.setBoolean(1, letter.isUnread());
					statement.setInt(2, attachedItemId);
					statement.setLong(3, letter.getAttachedKinah());
					statement.setInt(4, letter.getLetterType().getId());
					statement.setTimestamp(5, at);
					statement.setLong(6, letter.getAttachedAp());
					statement.setInt(7, letter.getObjectId());
				});
				break;
			default:
				return false;
		}

		if (written) {
			// Mark it saved only once the write has landed. The DAO did this
			// whatever happened, so a lost letter was never written again.
			letter.setPersistState(PersistentState.UPDATED);
		}
		return written;
	}

	@Override
	public boolean remove(int letterId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, letterId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to throw letter " + letterId + " away.", e);
		}
	}

	@Override
	public boolean setOfflineCounter(PlayerCommonData recipient) {
		if (recipient == null) {
			throw new IllegalArgumentException("Cannot record the post of a null character.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_OFFLINE_COUNTER)) {
			statement.setInt(1, recipient.getMailboxLetters());
			statement.setString(2, recipient.getName());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to record the post waiting for " + recipient.getName() + ".", e);
		}
	}

	@FunctionalInterface
	private interface Binding {
		void bind(PreparedStatement statement) throws SQLException;
	}

	private boolean write(String query, Letter letter, Binding binding) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			binding.bind(statement);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to store letter " + letter.getObjectId() + ".", e);
		}
	}
}
