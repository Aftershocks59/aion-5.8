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

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerLunaShop;

/**
 * Reads and writes what each character has spent and claimed in the Luna shop,
 * over JDBC.
 *
 * @author Oraion
 */
public final class JdbcLunaShopRepository extends JdbcRepositorySupport implements LunaShopRepository {

	private static final String SELECT_ONE = "SELECT `free_under`,`free_munition`,`free_chest`,`luna_consume`,"
			+ "`luna_consume_count`,`wardrobe_slot`,`muni_keys`,`dice_count`,`is_golden_dice`"
			+ " FROM `player_luna_shop` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_luna_shop`"
			+ " (`player_id`,`free_under`,`free_munition`,`free_chest`,`luna_consume`,`luna_consume_count`,"
			+ "`wardrobe_slot`,`muni_keys`,`dice_count`,`is_golden_dice`) VALUES (?,?,?,?,?,?,?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `player_luna_shop` SET `free_under` = ?, `free_munition` = ?,"
			+ " `free_chest` = ?, `luna_consume` = ?, `luna_consume_count` = ?, `wardrobe_slot` = ?,"
			+ " `muni_keys` = ?, `dice_count` = ?, `is_golden_dice` = ? WHERE `player_id` = ?";

	public JdbcLunaShopRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot read a Luna shop record for a null character.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return;
				}
				PlayerLunaShop shop = new PlayerLunaShop(rows.getBoolean("free_under"),
						rows.getBoolean("free_munition"), rows.getBoolean("free_chest"));
				shop.setLunaConsumePoint(rows.getInt("luna_consume"));
				shop.setLunaConsumeCount(rows.getInt("luna_consume_count"));
				shop.setWardrobeSlot(rows.getInt("wardrobe_slot"));
				shop.setMuniKeys(rows.getInt("muni_keys"));
				shop.setLunaDiceCount(rows.getInt("dice_count"));
				shop.setLunaGoldenDice(rows.getBoolean("is_golden_dice"));
				shop.setPersistentState(PersistentState.UPDATED);
				player.setPlayerLunaShop(shop);
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the Luna shop record of character " + player.getObjectId() + ".", e);
		}
	}

	@Override
	public boolean add(int playerId, PlayerLunaShop shop) {
		if (shop == null) {
			throw new IllegalArgumentException("Cannot store a null Luna shop record.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, playerId);
			bindShop(statement, shop, 2);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to give character " + playerId + " a Luna shop record.", e);
		}
	}

	@Override
	public boolean save(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot store the Luna shop record of a null character.");
		}

		PlayerLunaShop shop = player.getPlayerLunaShop();
		if (shop == null) {
			return false;
		}
		PersistentState state = shop.getPersistentState();
		if (state != PersistentState.UPDATE_REQUIRED && state != PersistentState.NEW) {
			return false;
		}

		boolean written = saveShop(player.getObjectId(), shop);
		// Mark it saved only now. The DAO did this whatever happened, so a record
		// whose write had failed still looked saved and was never retried.
		shop.setPersistentState(PersistentState.UPDATED);
		return written;
	}

	@Override
	public boolean saveShop(int playerId, PlayerLunaShop shop) {
		if (shop == null) {
			throw new IllegalArgumentException("Cannot store a null Luna shop record.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			bindShop(statement, shop, 1);
			statement.setInt(10, playerId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to store the Luna shop record of character " + playerId + ".", e);
		}
	}

	private static void bindShop(PreparedStatement statement, PlayerLunaShop shop, int from) throws SQLException {
		statement.setBoolean(from, shop.isFreeUnderpath());
		statement.setBoolean(from + 1, shop.isFreeFactory());
		statement.setBoolean(from + 2, shop.isFreeChest());
		statement.setInt(from + 3, shop.getLunaConsumePoint());
		statement.setInt(from + 4, shop.getLunaConsumeCount());
		statement.setInt(from + 5, shop.getWardrobeSlot());
		statement.setInt(from + 6, shop.getMuniKeys());
		statement.setInt(from + 7, shop.getLunaDiceCount());
		statement.setBoolean(from + 8, shop.isLunaGoldenDice());
	}
}
