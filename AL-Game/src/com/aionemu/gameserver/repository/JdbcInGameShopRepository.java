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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.ingameshop.IGItem;

/**
 * Reads and writes what the in-game shop has on its shelves, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcInGameShopRepository extends JdbcRepositorySupport implements InGameShopRepository {

	/**
	 * The client only renders sub-categories from here up; the lower ones are
	 * bookkeeping rows the shop never shows.
	 */
	private static final byte FIRST_VISIBLE_SUB_CATEGORY = 3;

	private static final String SELECT_ALL = "SELECT `object_id`,`item_id`,`item_count`,`item_price`,`category`,"
			+ "`sub_category`,`list`,`sales_ranking`,`item_type`,`gift`,`title_description`,`description`"
			+ " FROM `ingameshop` WHERE `sub_category` >= " + FIRST_VISIBLE_SUB_CATEGORY;
	private static final String INSERT_ONE = "INSERT INTO `ingameshop` (`object_id`,`item_id`,`item_count`,"
			+ "`item_price`,`category`,`sub_category`,`list`,`sales_ranking`,`item_type`,`gift`,"
			+ "`title_description`,`description`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String DELETE_ONE = "DELETE FROM `ingameshop` WHERE `item_id` = ? AND `category` = ?"
			+ " AND `sub_category` = ? AND `list` = ?";
	private static final String UPDATE_SALES = "UPDATE `ingameshop` SET `sales_ranking` = ? WHERE `object_id` = ?";

	public JdbcInGameShopRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Map<Byte, List<IGItem>> findAll() {
		Map<Byte, List<IGItem>> shelves = new LinkedHashMap<Byte, List<IGItem>>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				// The DAO read every row and skipped the hidden ones in Java; the
				// database can leave them out itself.
				Byte category = Byte.valueOf(rows.getByte("category"));
				List<IGItem> shelf = shelves.get(category);
				if (shelf == null) {
					shelf = new ArrayList<IGItem>();
					shelves.put(category, shelf);
				}
				shelf.add(new IGItem(rows.getInt("object_id"), rows.getInt("item_id"), rows.getLong("item_count"),
						rows.getLong("item_price"), rows.getByte("category"), rows.getByte("sub_category"),
						rows.getInt("list"), rows.getInt("sales_ranking"), rows.getByte("item_type"),
						rows.getByte("gift"), rows.getString("title_description"), rows.getString("description")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the in-game shop.", e);
		}

		return shelves;
	}

	@Override
	public boolean add(IGItem item) {
		if (item == null) {
			throw new IllegalArgumentException("Cannot put a null item on a shop shelf.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, item.getObjectId());
			statement.setInt(2, item.getItemId());
			statement.setLong(3, item.getItemCount());
			statement.setLong(4, item.getItemPrice());
			statement.setByte(5, item.getCategory());
			statement.setByte(6, item.getSubCategory());
			statement.setInt(7, item.getList());
			statement.setInt(8, item.getSalesRanking());
			statement.setByte(9, item.getItemType());
			statement.setByte(10, item.getGift());
			statement.setString(11, item.getTitleDescription());
			statement.setString(12, item.getItemDescription());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to put item " + item.getItemId() + " on a shop shelf.", e);
		}
	}

	@Override
	public int remove(int itemId, byte category, byte subCategory, int list) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, itemId);
			statement.setByte(2, category);
			statement.setByte(3, subCategory);
			statement.setInt(4, list);
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to take item " + itemId + " off a shop shelf.", e);
		}
	}

	@Override
	public boolean setSales(int objectId, int sales) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_SALES)) {
			statement.setInt(1, sales);
			statement.setInt(2, objectId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record the sales of shop entry " + objectId + ".", e);
		}
	}
}
