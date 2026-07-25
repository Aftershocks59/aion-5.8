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
import java.util.List;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.dorinerk_wardrobe.PlayerWardrobeEntry;
import com.aionemu.gameserver.model.dorinerk_wardrobe.PlayerWardrobeList;

/**
 * Reads and writes a character's stored looks over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerWardrobeRepository extends JdbcRepositorySupport
		implements PlayerWardrobeRepository {

	private static final String SELECT_ALL = "SELECT `item_id`,`slot`,`reskin_count` FROM `player_wardrobe` WHERE `player_id` = ?";
	private static final String UPSERT_ONE = "INSERT INTO `player_wardrobe` (`player_id`,`item_id`,`slot`,`reskin_count`) "
			+ "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE `item_id` = VALUES(`item_id`), `slot` = VALUES(`slot`)";
	private static final String DELETE_ONE = "DELETE FROM `player_wardrobe` WHERE `player_id` = ? AND `item_id` = ?";
	private static final String COUNT_ALL = "SELECT COUNT(*) FROM `player_wardrobe` WHERE `player_id` = ?";
	private static final String SELECT_ITEM = "SELECT `item_id` FROM `player_wardrobe` WHERE `player_id` = ? AND `slot` = ?";
	private static final String SELECT_RESKIN = "SELECT `reskin_count` FROM `player_wardrobe` WHERE `player_id` = ? AND `slot` = ?";
	private static final String UPDATE_RESKIN = "UPDATE `player_wardrobe` SET `reskin_count` = ? WHERE `player_id` = ? AND `slot` = ?";

	public JdbcPlayerWardrobeRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public PlayerWardrobeList findAll(Player player) {
		List<PlayerWardrobeEntry> entries = new ArrayList<PlayerWardrobeEntry>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					// The DAO read the slot into the restyle count as well, so every
					// entry came back claiming it had been restyled as many times as
					// its slot number.
					entries.add(new PlayerWardrobeEntry(rows.getInt("item_id"), rows.getInt("slot"),
							rows.getInt("reskin_count"), PersistentState.UPDATED));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the wardrobe of character " + player.getObjectId() + ".", e);
		}
		return new PlayerWardrobeList(entries);
	}

	@Override
	public boolean save(int playerId, int itemId, int slot, int reskinCount) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPSERT_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, itemId);
			statement.setInt(3, slot);
			statement.setInt(4, reskinCount);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to store item " + itemId + " in the wardrobe of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean remove(int playerId, int itemId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, itemId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to remove item " + itemId + " from the wardrobe of character " + playerId + ".", e);
		}
	}

	@Override
	public int count(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(COUNT_ALL)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getInt(1) : 0;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to count the wardrobe of character " + playerId + ".", e);
		}
	}

	@Override
	public int findItemInSlot(int playerId, int slot) {
		return readSlot(SELECT_ITEM, playerId, slot, "item");
	}

	@Override
	public int findReskinCount(int playerId, int slot) {
		return readSlot(SELECT_RESKIN, playerId, slot, "restyle count");
	}

	@Override
	public boolean setReskinCount(int playerId, int slot, int reskinCount) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_RESKIN)) {
			statement.setInt(1, reskinCount);
			statement.setInt(2, playerId);
			statement.setInt(3, slot);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record the restyle count of slot " + slot
					+ " for character " + playerId + ".", e);
		}
	}

	/**
	 * Reads one number out of one slot.
	 * <p>
	 * Answers zero for an empty slot. The DAO called next without checking it, so
	 * an empty slot threw and was caught by a catch that answered zero anyway.
	 */
	private int readSlot(String query, int playerId, int slot, String what) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, playerId);
			statement.setInt(2, slot);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getInt(1) : 0;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the " + what + " in slot " + slot
					+ " of character " + playerId + ".", e);
		}
	}
}
