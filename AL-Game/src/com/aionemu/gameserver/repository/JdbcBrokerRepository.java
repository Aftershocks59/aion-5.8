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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.broker.BrokerRace;
import com.aionemu.gameserver.model.gameobjects.BrokerItem;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.items.storage.StorageType;

/**
 * Reads and writes what is up for sale at the broker, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcBrokerRepository extends JdbcRepositorySupport implements BrokerRepository {

	private static final String SELECT_ALL = "SELECT `item_pointer`,`item_id`,`item_count`,`item_creator`,`seller`,"
			+ "`seller_id`,`price`,`broker_race`,`expire_time`,`settle_time`,`is_sold`,`is_settled`,`is_splitsell`"
			+ " FROM `broker`";
	private static final String SELECT_ON_SALE = "SELECT 1 FROM `broker`"
			+ " WHERE `item_pointer` = ? AND `is_sold` = 0 LIMIT 1";
	private static final String INSERT_ONE = "INSERT INTO `broker` (`item_pointer`,`item_id`,`item_count`,"
			+ "`item_creator`,`seller`,`price`,`broker_race`,`expire_time`,`settle_time`,`seller_id`,`is_sold`,"
			+ "`is_settled`,`is_splitsell`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String UPDATE_SETTLEMENT = "UPDATE `broker` SET `is_sold` = ?, `is_settled` = 1,"
			+ " `settle_time` = ? WHERE `item_pointer` = ? AND `expire_time` = ? AND `seller_id` = ?"
			+ " AND `is_settled` = 0";
	private static final String UPDATE_LISTING = "UPDATE `broker` SET `item_count` = ?, `price` = ?, `is_sold` = ?,"
			+ " `is_settled` = ?, `settle_time` = ?, `is_splitsell` = ? WHERE `item_pointer` = ?"
			+ " AND `expire_time` = ? AND `seller_id` = ? AND `is_settled` = 0";
	private static final String DELETE_ONE = "DELETE FROM `broker`"
			+ " WHERE `item_pointer` = ? AND `seller_id` = ? AND `expire_time` = ?";

	private final InventoryRepository inventories;
	private final ItemStoneRepository itemStones;

	public JdbcBrokerRepository(DataSource dataSource, InventoryRepository inventories,
			ItemStoneRepository itemStones) {
		super(dataSource);
		this.inventories = inventories;
		this.itemStones = itemStones;
	}

	@Override
	public List<BrokerItem> findAll() {
		// The items travel through the inventory repository, where the DAO had its
		// own copy of the thirty-odd column reader.
		List<Item> onSale = inventories.loadStorageItems(StorageType.BROKER);
		itemStones.load(onSale);

		Map<Integer, Item> byId = new HashMap<Integer, Item>();
		for (Item item : onSale) {
			byId.put(Integer.valueOf(item.getObjectId()), item);
		}

		List<BrokerItem> listings = new ArrayList<BrokerItem>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				int itemPointer = rows.getInt("item_pointer");
				boolean isSold = rows.getInt("is_sold") == 1;
				// A sold listing no longer carries its item; the buyer has it. The
				// DAO walked the whole list looking for it, once per listing.
				Item item = isSold ? null : byId.get(Integer.valueOf(itemPointer));

				listings.add(new BrokerItem(item, rows.getInt("item_id"), itemPointer, rows.getLong("item_count"),
						rows.getString("item_creator"), rows.getLong("price"), rows.getString("seller"),
						rows.getInt("seller_id"), BrokerRace.valueOf(rows.getString("broker_race")), isSold,
						rows.getInt("is_settled") == 1, rows.getTimestamp("expire_time"),
						rows.getTimestamp("settle_time"), rows.getInt("is_splitsell") == 1));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read what is for sale at the broker.", e);
		}

		return listings;
	}

	@Override
	public boolean isStillOnSale(int itemUniqueId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ON_SALE)) {
			statement.setInt(1, itemUniqueId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to check whether item " + itemUniqueId + " is still on sale.", e);
		}
	}

	@Override
	public boolean save(BrokerItem listing) {
		if (listing == null) {
			throw new IllegalArgumentException("Cannot store a null broker listing.");
		}

		boolean written;
		switch (listing.getPersistentState()) {
			case NEW:
				written = insert(listing);
				if (written && listing.getItem() != null) {
					inventories.save(listing.getItem(), listing.getSellerId());
				}
				break;
			case DELETED:
				written = write(DELETE_ONE, listing, statement -> {
					statement.setInt(1, listing.getItemUniqueId());
					statement.setInt(2, listing.getSellerId());
					statement.setTimestamp(3, listing.getExpireTime());
				});
				break;
			case UPDATE_ITEM_BROKER:
				written = write(UPDATE_LISTING, listing, statement -> {
					statement.setLong(1, listing.getItemCount());
					statement.setLong(2, listing.getPrice());
					statement.setBoolean(3, listing.isSold());
					statement.setBoolean(4, listing.isSettled());
					statement.setTimestamp(5, listing.getSettleTime());
					statement.setBoolean(6, listing.isSplitSell());
					statement.setInt(7, listing.getItemUniqueId());
					statement.setTimestamp(8, listing.getExpireTime());
					statement.setInt(9, listing.getSellerId());
				});
				break;
			case UPDATE_REQUIRED:
				written = write(UPDATE_SETTLEMENT, listing, statement -> {
					statement.setBoolean(1, listing.isSold());
					statement.setTimestamp(2, listing.getSettleTime());
					statement.setInt(3, listing.getItemUniqueId());
					statement.setTimestamp(4, listing.getExpireTime());
					statement.setInt(5, listing.getSellerId());
				});
				break;
			default:
				return false;
		}

		if (written) {
			listing.setPersistentState(PersistentState.UPDATED);
		}
		return written;
	}

	private boolean insert(BrokerItem listing) {
		return write(INSERT_ONE, listing, statement -> {
			statement.setInt(1, listing.getItemUniqueId());
			statement.setInt(2, listing.getItemId());
			statement.setLong(3, listing.getItemCount());
			statement.setString(4, listing.getItemCreator());
			statement.setString(5, listing.getSeller());
			statement.setLong(6, listing.getPrice());
			statement.setString(7, String.valueOf(listing.getItemBrokerRace()));
			statement.setTimestamp(8, listing.getExpireTime());
			statement.setTimestamp(9, listing.getSettleTime());
			statement.setInt(10, listing.getSellerId());
			statement.setBoolean(11, listing.isSold());
			statement.setBoolean(12, listing.isSettled());
			statement.setBoolean(13, listing.isSplitSell());
		});
	}

	@FunctionalInterface
	private interface Binding {
		void bind(PreparedStatement statement) throws SQLException;
	}

	private boolean write(String query, BrokerItem listing, Binding binding) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			binding.bind(statement);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to store broker listing " + listing.getItemUniqueId() + ".", e);
		}
	}
}
