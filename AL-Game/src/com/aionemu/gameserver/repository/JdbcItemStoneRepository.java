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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.configs.main.EnchantsConfig;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.items.GodStone;
import com.aionemu.gameserver.model.items.IdianStone;
import com.aionemu.gameserver.model.items.ItemStone;
import com.aionemu.gameserver.model.items.ItemStone.ItemStoneType;
import com.aionemu.gameserver.model.items.ManaStone;

/**
 * Reads and writes the stones socketed into weapons and armour, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcItemStoneRepository extends JdbcRepositorySupport implements ItemStoneRepository {

	private static final String SELECT_FOR_ITEM = "SELECT `item_id`,`slot`,`category`,`polishNumber`,`polishCharge`"
			+ " FROM `item_stones` WHERE `item_unique_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `item_stones`"
			+ " (`item_unique_id`,`item_id`,`slot`,`category`,`polishNumber`,`polishCharge`) VALUES (?,?,?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `item_stones` SET `item_id` = ?, `slot` = ?,"
			+ " `polishNumber` = ?, `polishCharge` = ? WHERE `item_unique_id` = ? AND `category` = ?";
	private static final String DELETE_ONE = "DELETE FROM `item_stones`"
			+ " WHERE `item_unique_id` = ? AND `slot` = ? AND `category` = ?";

	public JdbcItemStoneRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Collection<Item> items) {
		if (items == null || items.isEmpty()) {
			return;
		}

		// Sockets that no longer fit the item, gathered while reading and cleared
		// afterwards. The DAO deleted them through a second statement opened while
		// its own result set was still walking the same connection.
		List<int[]> overflowing = new ArrayList<int[]>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_FOR_ITEM)) {
			for (Item item : items) {
				if (item == null || !canHoldStones(item)) {
					continue;
				}
				statement.setInt(1, item.getObjectId());
				try (ResultSet rows = statement.executeQuery()) {
					while (rows.next()) {
						socket(item, rows, overflowing);
					}
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the stones of " + items.size() + " items.", e);
		}

		if (!overflowing.isEmpty() && EnchantsConfig.CLEAN_STONE) {
			clearOverflowing(overflowing);
		}
	}

	private static boolean canHoldStones(Item item) {
		return item.getItemTemplate().isArmor() || item.getItemTemplate().isWeapon();
	}

	private static void socket(Item item, ResultSet rows, List<int[]> overflowing) throws SQLException {
		int itemId = rows.getInt("item_id");
		int slot = rows.getInt("slot");
		int category = rows.getInt("category");

		if (category == ItemStoneType.MANASTONE.ordinal()) {
			if (item.getSockets(false) <= item.getItemStonesSize()) {
				overflowing.add(new int[] { item.getObjectId(), slot, category });
				return;
			}
			item.getItemStones().add(new ManaStone(item.getObjectId(), itemId, slot, PersistentState.UPDATED));
		} else if (category == ItemStoneType.GODSTONE.ordinal()) {
			item.setGodStone(new GodStone(item.getObjectId(), itemId, PersistentState.UPDATED));
		} else if (category == ItemStoneType.FUSIONSTONE.ordinal()) {
			if (item.getSockets(true) <= item.getFusionStonesSize()) {
				overflowing.add(new int[] { item.getObjectId(), slot, category });
				return;
			}
			item.getFusionStones().add(new ManaStone(item.getObjectId(), itemId, slot, PersistentState.UPDATED));
		} else if (category == ItemStoneType.IDIANSTONE.ordinal()) {
			item.setIdianStone(new IdianStone(itemId, PersistentState.UPDATE_REQUIRED, item,
					rows.getInt("polishNumber"), rows.getInt("polishCharge")));
		}
	}

	private void clearOverflowing(List<int[]> overflowing) {
		inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
				for (int[] stone : overflowing) {
					statement.setInt(1, stone[0]);
					statement.setInt(2, stone[1]);
					statement.setInt(3, stone[2]);
					statement.addBatch();
				}
				statement.executeBatch();
			}
			return null;
		}, "Failed to clear " + overflowing.size() + " stones that no longer fit their item.");
	}

	@Override
	public void save(List<Item> items) {
		if (items == null || items.isEmpty()) {
			return;
		}

		Set<ManaStone> manaStones = new LinkedHashSet<ManaStone>();
		Set<ManaStone> fusionStones = new LinkedHashSet<ManaStone>();
		Set<GodStone> godStones = new LinkedHashSet<GodStone>();
		Set<IdianStone> idianStones = new LinkedHashSet<IdianStone>();

		for (Item item : items) {
			if (item == null) {
				continue;
			}
			if (item.hasManaStones()) {
				manaStones.addAll(item.getItemStones());
			}
			if (item.hasFusionStones()) {
				fusionStones.addAll(item.getFusionStones());
			}
			if (item.getGodStone() != null) {
				godStones.add(item.getGodStone());
			}
			if (item.getIdianStone() != null) {
				idianStones.add(item.getIdianStone());
			}
		}

		write(manaStones, ItemStoneType.MANASTONE);
		write(fusionStones, ItemStoneType.FUSIONSTONE);
		write(godStones, ItemStoneType.GODSTONE);
		write(idianStones, ItemStoneType.IDIANSTONE);
	}

	@Override
	public void saveManaStones(Set<ManaStone> manaStones) {
		write(manaStones, ItemStoneType.MANASTONE);
	}

	@Override
	public void saveFusionStones(Set<ManaStone> fusionStones) {
		write(fusionStones, ItemStoneType.FUSIONSTONE);
	}

	@Override
	public void saveIdianStone(IdianStone idianStone) {
		if (idianStone == null) {
			throw new IllegalArgumentException("Cannot store a null idian stone.");
		}
		write(Collections.singleton(idianStone), ItemStoneType.IDIANSTONE);
	}

	private void write(Set<? extends ItemStone> stones, ItemStoneType type) {
		if (stones == null || stones.isEmpty()) {
			return;
		}

		// Take out first, so a stone pulled and pushed back into the same slot in
		// one breath ends up socketed rather than gone.
		inTransaction(connection -> {
			batch(connection, DELETE_ONE, stones, PersistentState.DELETED, type);
			batch(connection, INSERT_ONE, stones, PersistentState.NEW, type);
			batch(connection, UPDATE_ONE, stones, PersistentState.UPDATE_REQUIRED, type);
			return null;
		}, "Failed to store " + stones.size() + " " + type + " stones.");

		// Mark them saved only now. The DAO did this whatever happened, so a stone
		// whose write had failed still looked saved and was never retried.
		for (ItemStone stone : stones) {
			stone.setPersistentState(PersistentState.UPDATED);
		}
	}

	private void batch(Connection connection, String query, Set<? extends ItemStone> stones, PersistentState wanted,
			ItemStoneType type) throws SQLException {
		int queued = 0;
		try (PreparedStatement statement = connection.prepareStatement(query)) {
			for (ItemStone stone : stones) {
				if (stone == null || stone.getPersistentState() != wanted) {
					continue;
				}
				bind(statement, query, stone, type);
				// Queued, not run twice. The DAO called execute() and addBatch() on
				// the same statement, so every delete went out a second time.
				statement.addBatch();
				queued++;
			}
			if (queued > 0) {
				statement.executeBatch();
			}
		}
	}

	private static void bind(PreparedStatement statement, String query, ItemStone stone, ItemStoneType type)
			throws SQLException {
		int polishNumber = 0;
		int polishCharge = 0;
		if (stone instanceof IdianStone) {
			polishNumber = ((IdianStone) stone).getPolishNumber();
			polishCharge = ((IdianStone) stone).getPolishCharge();
		}

		if (DELETE_ONE.equals(query)) {
			statement.setInt(1, stone.getItemObjId());
			statement.setInt(2, stone.getSlot());
			statement.setInt(3, type.ordinal());
		} else if (UPDATE_ONE.equals(query)) {
			statement.setInt(1, stone.getItemId());
			statement.setInt(2, stone.getSlot());
			statement.setInt(3, polishNumber);
			statement.setInt(4, polishCharge);
			statement.setInt(5, stone.getItemObjId());
			statement.setInt(6, type.ordinal());
		} else {
			statement.setInt(1, stone.getItemObjId());
			statement.setInt(2, stone.getItemId());
			statement.setInt(3, stone.getSlot());
			statement.setInt(4, type.ordinal());
			statement.setInt(5, polishNumber);
			statement.setInt(6, polishCharge);
		}
	}
}
