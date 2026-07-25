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
import com.aionemu.gameserver.model.gameobjects.player.MinionCommonData;
import com.aionemu.gameserver.model.templates.minion.MinionDopingBag;

/**
 * Reads and writes the minions each character keeps, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcMinionRepository extends JdbcRepositorySupport implements MinionRepository {

	/** Shorter than "0,0," and there is nothing in the bag worth reading. */
	private static final int EMPTY_BAG_LENGTH = 6;

	private static final String SELECT_KEPT = "SELECT `object_id`,`minion_id`,`name`,`grade`,`level`,`growthpoints`,"
			+ "`birthday`,`is_locked`,`buff_bag` FROM `player_minions` WHERE `player_id` = ?";
	private static final String SELECT_TAKEN = "SELECT 1 FROM `player_minions`"
			+ " WHERE `player_id` = ? AND `object_id` = ? LIMIT 1";
	private static final String SELECT_BIRTHDAY = "SELECT `birthday` FROM `player_minions`"
			+ " WHERE `player_id` = ? AND `object_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_minions`"
			+ " (`player_id`,`object_id`,`minion_id`,`name`,`grade`,`level`) VALUES (?,?,?,?,?,?)";
	private static final String DELETE_ONE = "DELETE FROM `player_minions` WHERE `player_id` = ? AND `object_id` = ?";
	private static final String UPDATE_NAME = "UPDATE `player_minions` SET `name` = ?"
			+ " WHERE `player_id` = ? AND `object_id` = ?";
	private static final String UPDATE_GROWTH = "UPDATE `player_minions` SET `growthpoints` = ?"
			+ " WHERE `player_id` = ? AND `object_id` = ?";
	private static final String UPDATE_EVOLUTION = "UPDATE `player_minions` SET `minion_id` = ?, `growthpoints` = 0,"
			+ " `level` = ? WHERE `player_id` = ? AND `object_id` = ?";
	private static final String UPDATE_LOCK = "UPDATE `player_minions` SET `is_locked` = ?"
			+ " WHERE `player_id` = ? AND `object_id` = ?";
	private static final String UPDATE_DOPING = "UPDATE `player_minions` SET `buff_bag` = ?"
			+ " WHERE `player_id` = ? AND `object_id` = ?";

	public JdbcMinionRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public List<MinionCommonData> findAll(int playerId) {
		List<MinionCommonData> kept = new ArrayList<MinionCommonData>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_KEPT)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					kept.add(read(rows, playerId));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the minions of character " + playerId + ".", e);
		}

		return kept;
	}

	private static MinionCommonData read(ResultSet rows, int playerId) throws SQLException {
		MinionCommonData minion = new MinionCommonData(rows.getInt("minion_id"), playerId, rows.getString("name"),
				rows.getString("grade"), rows.getInt("level"), rows.getInt("growthpoints"));
		minion.setObjectId(rows.getInt("object_id"));
		minion.setBirthday(rows.getTimestamp("birthday"));
		minion.setLock(rows.getInt("is_locked") == 1);

		if (minion.getDopingBag() != null) {
			String bag = rows.getString("buff_bag");
			if (bag != null && bag.length() > EMPTY_BAG_LENGTH) {
				String[] itemIds = bag.split(",");
				for (int slot = 0; slot < itemIds.length; slot++) {
					minion.getDopingBag().setItem(Integer.parseInt(itemIds[slot]), slot);
				}
			}
		}

		return minion;
	}

	@Override
	public boolean isTaken(int playerId, int minionObjectId) {
		// One row at most, where the DAO read every minion the character keeps and
		// walked them in Java. Its caller does this in a loop to find a free id.
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_TAKEN)) {
			statement.setInt(1, playerId);
			statement.setInt(2, minionObjectId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to check minion id " + minionObjectId + " of character " + playerId
					+ ".", e);
		}
	}

	@Override
	public boolean add(MinionCommonData minion) {
		if (minion == null) {
			throw new IllegalArgumentException("Cannot store a null minion.");
		}

		return write(INSERT_ONE, "store minion " + minion.getMinionId(), statement -> {
			statement.setInt(1, minion.getMasterObjectId());
			statement.setInt(2, minion.getObjectId());
			statement.setInt(3, minion.getMinionId());
			statement.setString(4, minion.getName());
			statement.setString(5, minion.getMinionGrade());
			statement.setInt(6, minion.getMinionLevel());
		});
	}

	@Override
	public boolean remove(int playerId, int minionObjectId) {
		return write(DELETE_ONE, "take minion " + minionObjectId + " away from character " + playerId, statement -> {
			statement.setInt(1, playerId);
			statement.setInt(2, minionObjectId);
		});
	}

	@Override
	public void loadBirthday(MinionCommonData minion) {
		if (minion == null) {
			throw new IllegalArgumentException("Cannot read the birthday of a null minion.");
		}

		// This reads, despite the name the DAO gave it.
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_BIRTHDAY)) {
			statement.setInt(1, minion.getMasterObjectId());
			statement.setInt(2, minion.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) {
					minion.setBirthday(rows.getTimestamp("birthday"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the birthday of minion " + minion.getObjectId() + ".", e);
		}
	}

	@Override
	public boolean rename(MinionCommonData minion) {
		if (minion == null) {
			throw new IllegalArgumentException("Cannot rename a null minion.");
		}

		return write(UPDATE_NAME, "rename minion " + minion.getObjectId(), statement -> {
			statement.setString(1, minion.getName());
			statement.setInt(2, minion.getMasterObjectId());
			statement.setInt(3, minion.getObjectId());
		});
	}

	@Override
	public boolean setGrowthPoints(MinionCommonData minion) {
		if (minion == null) {
			throw new IllegalArgumentException("Cannot store the growth of a null minion.");
		}

		return write(UPDATE_GROWTH, "record the growth of minion " + minion.getObjectId(), statement -> {
			statement.setInt(1, minion.getMinionGrowthPoint());
			statement.setInt(2, minion.getMasterObjectId());
			statement.setInt(3, minion.getObjectId());
		});
	}

	@Override
	public boolean evolve(MinionCommonData minion) {
		if (minion == null) {
			throw new IllegalArgumentException("Cannot grow a null minion.");
		}

		return write(UPDATE_EVOLUTION, "grow minion " + minion.getObjectId(), statement -> {
			statement.setInt(1, minion.getMinionId());
			statement.setInt(2, minion.getMinionLevel());
			statement.setInt(3, minion.getMasterObjectId());
			statement.setInt(4, minion.getObjectId());
		});
	}

	@Override
	public boolean setLocked(int playerId, int minionObjectId, boolean locked) {
		return write(UPDATE_LOCK, "lock minion " + minionObjectId, statement -> {
			statement.setInt(1, locked ? 1 : 0);
			statement.setInt(2, playerId);
			statement.setInt(3, minionObjectId);
		});
	}

	@Override
	public boolean saveDopingBag(int playerId, int minionObjectId, MinionDopingBag bag) {
		if (bag == null) {
			throw new IllegalArgumentException("Cannot store a null minion bag.");
		}

		return write(UPDATE_DOPING, "record what minion " + minionObjectId + " carries", statement -> {
			statement.setString(1, describe(bag));
			statement.setInt(2, playerId);
			statement.setInt(3, minionObjectId);
		});
	}

	/** The bag travels as a comma-separated list: food, drink, then the scrolls. */
	private static String describe(MinionDopingBag bag) {
		StringBuilder itemIds = new StringBuilder();
		itemIds.append(bag.getFoodItem()).append(',').append(bag.getDrinkItem());
		for (int itemId : bag.getScrollsUsed()) {
			itemIds.append(',').append(itemId);
		}
		return itemIds.toString();
	}

	@FunctionalInterface
	private interface Binding {
		void bind(PreparedStatement statement) throws SQLException;
	}

	private boolean write(String query, String description, Binding binding) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			binding.bind(statement);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to " + description + ".", e);
		}
	}
}
