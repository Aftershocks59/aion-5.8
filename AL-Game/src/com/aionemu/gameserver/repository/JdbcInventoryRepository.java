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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Equipment;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.PlayerStorage;
import com.aionemu.gameserver.model.items.storage.Storage;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.services.item.ItemService;

/**
 * Reads and writes everything a character, an account or a legion has stored
 * away, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcInventoryRepository extends JdbcRepositorySupport implements InventoryRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcInventoryRepository.class);

	/** The storage id a character's worn equipment sits under. */
	private static final int WORN_STORAGE = 0;

	private static final String COLUMNS = "`item_unique_id`,`item_id`,`item_count`,`item_color`,`color_expires`,"
			+ "`item_creator`,`expire_time`,`activation_count`,`is_equiped`,`is_soul_bound`,`slot`,`enchant`,"
			+ "`enchant_bonus`,`item_skin`,`fusioned_item`,`optional_socket`,`optional_fusion_socket`,`charge`,"
			+ "`rnd_bonus`,`rnd_count`,`wrappable_count`,`is_packed`,`tempering_level`,`is_topped`,"
			+ "`strengthen_skill`,`skin_skill`,`luna_reskin`,`reduction_level`,`is_seal`,`isEnhance`,"
			+ "`enhanceSkillId`,`enhanceSkillEnchant`";

	private static final String SELECT_BY_EQUIPPED = "SELECT " + COLUMNS + " FROM `inventory`"
			+ " WHERE `item_owner` = ? AND `item_location` = ? AND `is_equiped` = ?";
	private static final String SELECT_IN_STORAGE = "SELECT " + COLUMNS + " FROM `inventory`"
			+ " WHERE `item_owner` = ? AND `item_location` = ?";
	private static final String SELECT_USED_IDS = "SELECT `item_unique_id` FROM `inventory`";
	private static final String SELECT_ACCOUNT = "SELECT `account_id` FROM `players` WHERE `id` = ?";
	private static final String SELECT_LEGION = "SELECT `legion_id` FROM `legion_members` WHERE `player_id` = ?";

	private static final String INSERT_ONE = "INSERT INTO `inventory` (`item_unique_id`,`item_id`,`item_count`,"
			+ "`item_color`,`color_expires`,`item_creator`,`expire_time`,`activation_count`,`item_owner`,"
			+ "`is_equiped`,`is_soul_bound`,`slot`,`item_location`,`enchant`,`enchant_bonus`,`item_skin`,"
			+ "`fusioned_item`,`optional_socket`,`optional_fusion_socket`,`charge`,`rnd_bonus`,`rnd_count`,"
			+ "`wrappable_count`,`is_packed`,`tempering_level`,`is_topped`,`strengthen_skill`,`skin_skill`,"
			+ "`luna_reskin`,`reduction_level`,`is_seal`,`isEnhance`,`enhanceSkillId`,`enhanceSkillEnchant`)"
			+ " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `inventory` SET `item_count` = ?, `item_color` = ?,"
			+ " `color_expires` = ?, `item_creator` = ?, `expire_time` = ?, `activation_count` = ?,"
			+ " `item_owner` = ?, `is_equiped` = ?, `is_soul_bound` = ?, `slot` = ?, `item_location` = ?,"
			+ " `enchant` = ?, `enchant_bonus` = ?, `item_skin` = ?, `fusioned_item` = ?, `optional_socket` = ?,"
			+ " `optional_fusion_socket` = ?, `charge` = ?, `rnd_bonus` = ?, `rnd_count` = ?,"
			+ " `wrappable_count` = ?, `is_packed` = ?, `tempering_level` = ?, `is_topped` = ?,"
			+ " `strengthen_skill` = ?, `skin_skill` = ?, `luna_reskin` = ?, `reduction_level` = ?,"
			+ " `is_seal` = ?, `isEnhance` = ?, `enhanceSkillId` = ?, `enhanceSkillEnchant` = ?"
			+ " WHERE `item_unique_id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `inventory` WHERE `item_unique_id` = ?";
	// The legion warehouse is deliberately left alone: legions own their ids
	// separately from the characters in them.
	private static final String DELETE_FOR_PLAYER = "DELETE FROM `inventory`"
			+ " WHERE `item_owner` = ? AND `item_location` <> 2";
	private static final String DELETE_ACCOUNT_WAREHOUSE = "DELETE FROM `inventory`"
			+ " WHERE `item_owner` = ? AND `item_location` = 2";

	public JdbcInventoryRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public int[] findUsedIds() {
		List<Integer> used = new ArrayList<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_USED_IDS);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				used.add(Integer.valueOf(rows.getInt("item_unique_id")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the item ids already in use.", e);
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
	public Storage loadStorage(int playerId, StorageType storageType) {
		Storage storage = new PlayerStorage(storageType);

		for (Item item : readItems(SELECT_BY_EQUIPPED, ownerOf(playerId, storageType), storageType.getId(),
				Integer.valueOf(0))) {
			item.setPersistentState(PersistentState.UPDATED);
			if (item.getItemTemplate() == null) {
				log.error("Character " + playerId + " holds item " + item.getObjectId()
						+ ", which no template describes.");
				continue;
			}
			storage.onLoadHandler(item);
		}

		return storage;
	}

	@Override
	public List<Item> loadStorageItems(int playerId, StorageType storageType) {
		return readItems(SELECT_IN_STORAGE, ownerOf(playerId, storageType), storageType.getId(), null);
	}

	@Override
	public Equipment loadEquipment(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot read the equipment of a null character.");
		}

		Equipment equipment = new Equipment(player);
		for (Item item : loadEquipment(player.getObjectId())) {
			item.setPersistentState(PersistentState.UPDATED);
			equipment.onLoadHandler(item);
		}
		return equipment;
	}

	@Override
	public List<Item> loadEquipment(int playerId) {
		return readItems(SELECT_BY_EQUIPPED, playerId, WORN_STORAGE, Integer.valueOf(1));
	}

	/** The account warehouse hangs off the account, not the character. */
	private int ownerOf(int playerId, StorageType storageType) {
		return storageType == StorageType.ACCOUNT_WAREHOUSE ? findAccountId(playerId) : playerId;
	}

	private List<Item> readItems(String query, int owner, int storage, Integer equipped) {
		List<Item> items = new ArrayList<Item>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, owner);
			statement.setInt(2, storage);
			if (equipped != null) {
				statement.setInt(3, equipped.intValue());
			}
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					items.add(read(rows, storage));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read storage " + storage + " of owner " + owner + ".", e);
		}

		return items;
	}

	private static Item read(ResultSet rows, int storage) throws SQLException {
		return new Item(rows.getInt("item_unique_id"), rows.getInt("item_id"), rows.getLong("item_count"),
				rows.getInt("item_color"), rows.getInt("color_expires"), rows.getString("item_creator"),
				rows.getInt("expire_time"), rows.getInt("activation_count"), rows.getInt("is_equiped") == 1,
				rows.getInt("is_soul_bound") == 1, rows.getLong("slot"), storage, rows.getInt("enchant"),
				rows.getInt("enchant_bonus"), rows.getInt("item_skin"), rows.getInt("fusioned_item"),
				rows.getInt("optional_socket"), rows.getInt("optional_fusion_socket"), rows.getInt("charge"),
				Integer.valueOf(rows.getInt("rnd_bonus")), rows.getInt("rnd_count"), rows.getInt("wrappable_count"),
				rows.getInt("is_packed") == 1, rows.getInt("tempering_level"), rows.getInt("is_topped") == 1,
				rows.getInt("strengthen_skill"), rows.getInt("skin_skill"), rows.getInt("luna_reskin") == 1,
				rows.getInt("reduction_level"), rows.getInt("is_seal"), rows.getBoolean("isEnhance"),
				rows.getInt("enhanceSkillId"), rows.getInt("enhanceSkillEnchant"));
	}

	private int findAccountId(int playerId) {
		return readId(SELECT_ACCOUNT, "account_id", playerId, "the account of character " + playerId);
	}

	private int findLegionId(int playerId) {
		return readId(SELECT_LEGION, "legion_id", playerId, "the legion of character " + playerId);
	}

	private int readId(String query, String column, int playerId, String description) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getInt(column) : 0;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read " + description + ".", e);
		}
	}

	@Override
	public boolean save(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot store the items of a null character.");
		}

		Integer accountId = player.getPlayerAccount() == null ? null
				: Integer.valueOf(player.getPlayerAccount().getId());
		Integer legionId = player.getLegion() == null ? null : Integer.valueOf(player.getLegion().getLegionId());
		return save(player.getDirtyItemsToUpdate(), Integer.valueOf(player.getObjectId()), accountId, legionId);
	}

	@Override
	public boolean save(Item item, Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot store an item for a null character.");
		}

		Integer accountId = player.getPlayerAccount() == null ? null
				: Integer.valueOf(player.getPlayerAccount().getId());
		Integer legionId = player.getLegion() == null ? null : Integer.valueOf(player.getLegion().getLegionId());
		return save(Collections.singletonList(item), Integer.valueOf(player.getObjectId()), accountId, legionId);
	}

	@Override
	public boolean save(Item item, int playerId) {
		return save(Collections.singletonList(item), playerId);
	}

	@Override
	public boolean save(List<Item> items, int playerId) {
		if (items == null || items.isEmpty()) {
			return true;
		}

		Integer accountId = null;
		Integer legionId = null;
		for (Item item : items) {
			if (item == null) {
				continue;
			}
			if (accountId == null && item.getItemLocation() == StorageType.ACCOUNT_WAREHOUSE.getId()) {
				accountId = Integer.valueOf(findAccountId(playerId));
			}
			if (legionId == null && item.getItemLocation() == StorageType.LEGION_WAREHOUSE.getId()) {
				int found = findLegionId(playerId);
				if (found > 0) {
					legionId = Integer.valueOf(found);
				}
			}
		}

		return save(items, Integer.valueOf(playerId), accountId, legionId);
	}

	@Override
	public boolean save(List<Item> items, Integer playerId, Integer accountId, Integer legionId) {
		if (items == null || items.isEmpty()) {
			return true;
		}

		List<Item> pending = new ArrayList<Item>(items);
		List<Item> removed = matching(pending, PersistentState.DELETED);

		inTransaction(connection -> {
			// Take away first, so an item destroyed and re-created under the same id
			// in one breath ends up present rather than gone.
			deleteItems(connection, removed);
			writeItems(connection, INSERT_ONE, matching(pending, PersistentState.NEW), playerId, accountId, legionId);
			writeItems(connection, UPDATE_ONE, matching(pending, PersistentState.UPDATE_REQUIRED), playerId,
					accountId, legionId);
			return null;
		}, "Failed to store " + pending.size() + " items of character " + playerId + ".");

		// Only once the write has landed: mark them saved and hand the ids back.
		// The DAO marked them whatever happened, and its insert pass had even had
		// its error log commented out, so a lost item looked stored and was never
		// written again.
		for (Item item : pending) {
			item.setPersistentState(PersistentState.UPDATED);
		}
		if (!removed.isEmpty()) {
			ItemService.releaseItemIds(removed);
		}
		return true;
	}

	private static List<Item> matching(List<Item> items, PersistentState wanted) {
		List<Item> found = new ArrayList<Item>();
		for (Item item : items) {
			if (item != null && item.getPersistentState() == wanted) {
				found.add(item);
			}
		}
		return found;
	}

	private void deleteItems(Connection connection, List<Item> items) throws SQLException {
		if (items.isEmpty()) {
			return;
		}

		try (PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			for (Item item : items) {
				statement.setInt(1, item.getObjectId());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private void writeItems(Connection connection, String query, List<Item> items, Integer playerId,
			Integer accountId, Integer legionId) throws SQLException {
		if (items.isEmpty()) {
			return;
		}
		boolean isNew = INSERT_ONE.equals(query);

		try (PreparedStatement statement = connection.prepareStatement(query)) {
			for (Item item : items) {
				int index = 1;
				if (isNew) {
					statement.setInt(index++, item.getObjectId());
					statement.setInt(index++, item.getItemTemplate().getTemplateId());
				}
				statement.setLong(index++, item.getItemCount());
				statement.setInt(index++, item.getItemColor());
				statement.setInt(index++, item.getColorExpireTime());
				statement.setString(index++, item.getItemCreator());
				statement.setInt(index++, item.getExpireTime());
				statement.setInt(index++, item.getActivationCount());
				statement.setInt(index++, ownerOf(item, playerId, accountId, legionId));
				statement.setBoolean(index++, item.isEquipped());
				statement.setInt(index++, item.isSoulBound() ? 1 : 0);
				statement.setLong(index++, item.getEquipmentSlot());
				statement.setInt(index++, item.getItemLocation());
				statement.setInt(index++, item.getEnchantLevel());
				statement.setInt(index++, item.getEnchantBonus());
				statement.setInt(index++, item.getItemSkinTemplate().getTemplateId());
				statement.setInt(index++, item.getFusionedItemId());
				statement.setInt(index++, item.getOptionalSocket());
				statement.setInt(index++, item.getOptionalFusionSocket());
				statement.setInt(index++, item.getChargePoints());
				statement.setInt(index++, item.getBonusNumber());
				statement.setInt(index++, item.getRandomCount());
				statement.setInt(index++, item.getWrappableCount());
				statement.setBoolean(index++, item.isPacked());
				statement.setInt(index++, item.getAuthorize());
				statement.setBoolean(index++, item.isAmplified());
				statement.setInt(index++, item.getAmplificationSkill());
				statement.setInt(index++, item.getItemSkinSkill());
				statement.setBoolean(index++, item.isLunaReskin());
				statement.setInt(index++, item.getReductionLevel());
				statement.setInt(index++, item.getUnSeal());
				statement.setBoolean(index++, item.isEnhance());
				statement.setInt(index++, item.getEnhanceSkillId());
				statement.setInt(index++, item.getEnhanceEnchantLevel());
				if (!isNew) {
					statement.setInt(index, item.getObjectId());
				}
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	/**
	 * Answers whose row an item belongs to. The DAO unboxed the account id
	 * straight, so an account warehouse item saved for a character with no account
	 * threw where it should have fallen back.
	 */
	private static int ownerOf(Item item, Integer playerId, Integer accountId, Integer legionId) {
		if (item.getItemLocation() == StorageType.ACCOUNT_WAREHOUSE.getId() && accountId != null) {
			return accountId.intValue();
		}
		if (item.getItemLocation() == StorageType.LEGION_WAREHOUSE.getId() && legionId != null) {
			return legionId.intValue();
		}
		if (playerId == null) {
			throw new RepositoryException("Item " + item.getObjectId() + " sits in storage "
					+ item.getItemLocation() + ", which no owner was given for.");
		}
		return playerId.intValue();
	}

	@Override
	public int removeFor(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_FOR_PLAYER)) {
			statement.setInt(1, playerId);
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to clear the items of character " + playerId + ".", e);
		}
	}

	@Override
	public int removeAccountWarehouse(int accountId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ACCOUNT_WAREHOUSE)) {
			statement.setInt(1, accountId);
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to clear the warehouse of account " + accountId + ".", e);
		}
	}
}
