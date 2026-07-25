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
import java.util.List;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.PetCommonData;
import com.aionemu.gameserver.model.templates.pet.PetDopingBag;
import com.aionemu.gameserver.services.toypet.PetHungryLevel;

/**
 * Reads and writes the pets each character keeps, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPetRepository extends JdbcRepositorySupport implements PetRepository {

	private static final String SELECT_KEPT = "SELECT `pet_id`,`decoration`,`name`,`birthday`,`despawn_time`,"
			+ "`expire_time`,`hungry_level`,`feed_progress`,`reuse_time`,`dopings`,`mood_started`,`counter`,"
			+ "`mood_cd_started`,`gift_cd_started` FROM `player_pets` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_pets`"
			+ " (`player_id`,`pet_id`,`decoration`,`name`,`despawn_time`,`expire_time`) VALUES (?,?,?,?,?,?)";
	private static final String DELETE_ONE = "DELETE FROM `player_pets` WHERE `player_id` = ? AND `pet_id` = ?";
	private static final String UPDATE_NAME = "UPDATE `player_pets` SET `name` = ? WHERE `player_id` = ? AND `pet_id` = ?";
	private static final String UPDATE_REUSE = "UPDATE `player_pets` SET `reuse_time` = ?"
			+ " WHERE `player_id` = ? AND `pet_id` = ?";
	private static final String UPDATE_FEEDING = "UPDATE `player_pets` SET `hungry_level` = ?, `feed_progress` = ?,"
			+ " `reuse_time` = ? WHERE `player_id` = ? AND `pet_id` = ?";
	private static final String UPDATE_MOOD = "UPDATE `player_pets` SET `mood_started` = ?, `counter` = ?,"
			+ " `mood_cd_started` = ?, `gift_cd_started` = ?, `despawn_time` = ? WHERE `player_id` = ? AND `pet_id` = ?";
	private static final String UPDATE_DOPING = "UPDATE `player_pets` SET `dopings` = ?"
			+ " WHERE `player_id` = ? AND `pet_id` = ?";

	public JdbcPetRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public List<PetCommonData> findAll(int playerId) {
		List<PetCommonData> kept = new ArrayList<PetCommonData>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_KEPT)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					kept.add(read(rows, playerId));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the pets of character " + playerId + ".", e);
		}

		return kept;
	}

	private static PetCommonData read(ResultSet rows, int playerId) throws SQLException {
		PetCommonData pet = new PetCommonData(rows.getInt("pet_id"), playerId, rows.getInt("expire_time"));
		pet.setName(rows.getString("name"));
		pet.setDecoration(rows.getInt("decoration"));

		if (pet.getFeedProgress() != null) {
			pet.getFeedProgress().setHungryLevel(PetHungryLevel.fromId(rows.getInt("hungry_level")));
			pet.getFeedProgress().setData(rows.getInt("feed_progress"));
			pet.setCurentTime(rows.getLong("reuse_time"));
		}
		if (pet.getDopingBag() != null) {
			String dopings = rows.getString("dopings");
			if (dopings != null) {
				String[] itemIds = dopings.split(",");
				for (int slot = 0; slot < itemIds.length; slot++) {
					pet.getDopingBag().setItem(Integer.parseInt(itemIds[slot]), slot);
				}
			}
		}

		pet.setBirthday(rows.getTimestamp("birthday"));
		if (pet.getTime() != 0) {
			pet.setIsFeedingTime(false);
			pet.setReFoodTime(pet.getTime());
		}
		pet.setStartMoodTime(rows.getLong("mood_started"));
		pet.setShuggleCounter(rows.getInt("counter"));
		pet.setMoodCdStarted(rows.getLong("mood_cd_started"));
		pet.setGiftCdStarted(rows.getLong("gift_cd_started"));

		// A pet that has never been sent away counts as available now. The DAO
		// wrapped this read in a catch that swallowed everything.
		Timestamp despawn = rows.getTimestamp("despawn_time");
		pet.setDespawnTime(despawn == null ? new Timestamp(System.currentTimeMillis()) : despawn);

		return pet;
	}

	@Override
	public boolean add(PetCommonData pet) {
		if (pet == null) {
			throw new IllegalArgumentException("Cannot store a null pet.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, pet.getMasterObjectId());
			statement.setInt(2, pet.getPetId());
			statement.setInt(3, pet.getDecoration());
			statement.setString(4, pet.getName());
			statement.setTimestamp(5, pet.getDespawnTime());
			statement.setInt(6, pet.getExpireTime());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to store pet " + pet.getPetId() + ".", e);
		}
	}

	@Override
	public boolean remove(int playerId, int petId) {
		return write(DELETE_ONE, "take pet " + petId + " away from character " + playerId,
				statement -> {
					statement.setInt(1, playerId);
					statement.setInt(2, petId);
				});
	}

	@Override
	public boolean rename(PetCommonData pet) {
		if (pet == null) {
			throw new IllegalArgumentException("Cannot rename a null pet.");
		}

		return write(UPDATE_NAME, "rename pet " + pet.getPetId(), statement -> {
			statement.setString(1, pet.getName());
			statement.setInt(2, pet.getMasterObjectId());
			statement.setInt(3, pet.getPetId());
		});
	}

	@Override
	public boolean setReuseTime(int playerId, int petId, long reuseAt) {
		return write(UPDATE_REUSE, "record when pet " + petId + " may next be summoned", statement -> {
			statement.setLong(1, reuseAt);
			statement.setInt(2, playerId);
			statement.setInt(3, petId);
		});
	}

	@Override
	public boolean saveFeeding(int playerId, int petId, int hungryLevel, int feedProgress, long reuseAt) {
		return write(UPDATE_FEEDING, "record how well fed pet " + petId + " is", statement -> {
			statement.setInt(1, hungryLevel);
			statement.setInt(2, feedProgress);
			statement.setLong(3, reuseAt);
			statement.setInt(4, playerId);
			statement.setInt(5, petId);
		});
	}

	@Override
	public boolean saveMood(PetCommonData pet) {
		if (pet == null) {
			throw new IllegalArgumentException("Cannot store the mood of a null pet.");
		}

		return write(UPDATE_MOOD, "record the mood of pet " + pet.getPetId(), statement -> {
			statement.setLong(1, pet.getMoodStartTime());
			statement.setInt(2, pet.getShuggleCounter());
			statement.setLong(3, pet.getMoodCdStarted());
			statement.setLong(4, pet.getGiftCdStarted());
			statement.setTimestamp(5, pet.getDespawnTime());
			statement.setInt(6, pet.getMasterObjectId());
			statement.setInt(7, pet.getPetId());
		});
	}

	@Override
	public boolean saveDopingBag(int playerId, int petId, PetDopingBag bag) {
		if (bag == null) {
			throw new IllegalArgumentException("Cannot store a null pet bag.");
		}

		return write(UPDATE_DOPING, "record what pet " + petId + " carries", statement -> {
			statement.setString(1, describe(bag));
			statement.setInt(2, playerId);
			statement.setInt(3, petId);
		});
	}

	/** The bag travels as a comma-separated list: food, drink, then the scrolls. */
	private static String describe(PetDopingBag bag) {
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
