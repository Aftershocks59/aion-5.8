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
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.AionObject;
import com.aionemu.gameserver.model.gameobjects.HouseDecoration;
import com.aionemu.gameserver.model.gameobjects.HouseObject;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.house.House;
import com.aionemu.gameserver.model.house.HouseRegistry;
import com.aionemu.gameserver.model.templates.housing.HouseType;
import com.aionemu.gameserver.model.templates.housing.PartType;
import com.aionemu.gameserver.services.HousingService;
import com.aionemu.gameserver.services.item.HouseObjectFactory;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.world.World;
import com.google.common.collect.Collections2;

/**
 * Reads and writes what a character has placed in their house, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcHouseRegistryRepository extends JdbcRepositorySupport implements HouseRegistryRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcHouseRegistryRepository.class);

	private static final String SELECT_USED_IDS = "SELECT `item_unique_id` FROM `player_registered_items`"
			+ " WHERE `item_unique_id` <> 0";
	private static final String SELECT_PLACED = "SELECT `expire_time`,`color`,`color_expires`,`owner_use_count`,"
			+ "`visitor_use_count`,`x`,`y`,`z`,`h`,`area`,`floor`,`item_unique_id`,`item_id`"
			+ " FROM `player_registered_items` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_registered_items`"
			+ " (`expire_time`,`color`,`color_expires`,`owner_use_count`,`visitor_use_count`,`x`,`y`,`z`,`h`,"
			+ "`area`,`floor`,`player_id`,`item_unique_id`,`item_id`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `player_registered_items` SET `expire_time` = ?, `color` = ?,"
			+ " `color_expires` = ?, `owner_use_count` = ?, `visitor_use_count` = ?, `x` = ?, `y` = ?, `z` = ?,"
			+ " `h` = ?, `area` = ?, `floor` = ? WHERE `player_id` = ? AND `item_unique_id` = ? AND `item_id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `player_registered_items` WHERE `item_unique_id` = ?";
	private static final String RESET_PLACED = "UPDATE `player_registered_items` SET `x` = 0, `y` = 0, `z` = 0,"
			+ " `h` = 0, `area` = 'NONE' WHERE `player_id` = ? AND `area` <> 'DECOR'";

	/** The area a row carries when it is a wall or floor covering rather than furniture. */
	private static final String DECORATION_AREA = "DECOR";

	public JdbcHouseRegistryRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public int[] findUsedIds() {
		List<Integer> used = new ArrayList<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_USED_IDS);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				used.add(Integer.valueOf(rows.getInt(1)));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the ids already placed in houses.", e);
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
	public void load(int playerId) {
		House house = HousingService.getInstance().getPlayerStudio(playerId);
		if (house == null) {
			house = HousingService.getInstance()
					.getHouseByAddress(HousingService.getInstance().getPlayerAddress(playerId));
		}
		if (house == null) {
			// A character with neither a studio nor an address has nothing to fill.
			// The DAO dereferenced this and reported it as a failed read.
			return;
		}

		HouseRegistry registry = house.getRegistry();
		Map<PartType, List<HouseDecoration>> inUse = new HashMap<PartType, List<HouseDecoration>>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_PLACED)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					if (DECORATION_AREA.equals(rows.getString("area"))) {
						readDecoration(rows, house, registry, inUse);
					} else {
						HouseObject<?> placed = readObject(rows, house, registry);
						registry.putObject(placed);
						placed.setPersistentState(PersistentState.UPDATED);
					}
				}
			}
		} catch (SQLException | IllegalAccessException e) {
			throw new RepositoryException("Failed to read what character " + playerId + " placed in their house.", e);
		}

		applyDecoration(registry, house, inUse);
		registry.setPersistentState(PersistentState.UPDATED);
	}

	private void readDecoration(ResultSet rows, House house, HouseRegistry registry,
			Map<PartType, List<HouseDecoration>> inUse) throws SQLException {
		HouseDecoration decoration = new HouseDecoration(rows.getInt("item_unique_id"), rows.getInt("item_id"),
				rows.getByte("floor"));
		decoration.setUsed(rows.getInt("owner_use_count") > 0);
		registry.putCustomPart(decoration);

		if (decoration.isUsed()) {
			if (house.getHouseType() != HouseType.PALACE && decoration.getFloor() > 0) {
				decoration.setFloor(0);
			}
			List<HouseDecoration> forType = inUse.get(decoration.getTemplate().getType());
			if (forType == null) {
				forType = new ArrayList<HouseDecoration>();
				inUse.put(decoration.getTemplate().getType(), forType);
			}
			forType.add(decoration);
		}
		decoration.setPersistentState(PersistentState.UPDATED);
	}

	private HouseObject<?> readObject(ResultSet rows, House house, HouseRegistry registry)
			throws SQLException, IllegalAccessException {
		int itemUniqueId = rows.getInt("item_unique_id");
		VisibleObject seen = World.getInstance().findVisibleObject(itemUniqueId);
		HouseObject<?> placed;

		if (seen != null) {
			if (!(seen instanceof HouseObject<?>)) {
				throw new IllegalAccessException("Someone stole my house object id : " + itemUniqueId);
			}
			placed = (HouseObject<?>) seen;
		} else {
			placed = registry.getObjectByObjId(itemUniqueId);
			if (placed == null) {
				placed = HouseObjectFactory.createNew(house, itemUniqueId, rows.getInt("item_id"));
			}
		}

		placed.setOwnerUsedCount(rows.getInt("owner_use_count"));
		placed.setVisitorUsedCount(rows.getInt("visitor_use_count"));
		placed.setX(rows.getFloat("x"));
		placed.setY(rows.getFloat("y"));
		placed.setZ(rows.getFloat("z"));
		placed.setHeading((byte) rows.getInt("h"));
		placed.setColor(rows.getInt("color"));
		placed.setColorExpireEnd(rows.getInt("color_expires"));
		if (placed.getObjectTemplate().getUseDays() > 0) {
			placed.setExpireTime(rows.getInt("expire_time"));
		}
		return placed;
	}

	/** Hangs what the character chose, and the house's own covering everywhere else. */
	private void applyDecoration(HouseRegistry registry, House house, Map<PartType, List<HouseDecoration>> inUse) {
		for (PartType partType : PartType.values()) {
			List<HouseDecoration> chosen = inUse.get(partType);
			if (chosen != null) {
				for (HouseDecoration decoration : chosen) {
					registry.setPartInUse(decoration, decoration.getFloor());
				}
				continue;
			}

			int floors = 1;
			if (house.getHouseType() == HouseType.PALACE
					&& (partType == PartType.INFLOOR_ANY || partType == PartType.INWALL_ANY)) {
				floors = 6;
			}
			for (int floor = 0; floor < floors; floor++) {
				HouseDecoration fallback = registry.getDefaultPartByType(partType, floor);
				if (fallback != null) {
					registry.setPartInUse(fallback, floor);
				}
			}
		}
	}

	@Override
	public void save(HouseRegistry registry, int playerId) {
		if (registry == null) {
			throw new IllegalArgumentException("Cannot store a null house registry.");
		}

		List<HouseObject<?>> objects = new ArrayList<HouseObject<?>>(registry.getObjects());
		List<HouseDecoration> parts = new ArrayList<HouseDecoration>(registry.getAllParts());

		inTransaction(connection -> {
			// Take away first, so an object moved out and back in the same breath
			// ends up placed rather than gone.
			deleteObjects(connection, objects);
			deleteParts(connection, parts);
			writeObjects(connection, objects, playerId, PersistentState.UPDATE_REQUIRED, UPDATE_ONE);
			writeParts(connection, parts, playerId, PersistentState.UPDATE_REQUIRED, UPDATE_ONE);
			writeObjects(connection, objects, playerId, PersistentState.NEW, INSERT_ONE);
			writeParts(connection, parts, playerId, PersistentState.NEW, INSERT_ONE);
			return null;
		}, "Failed to store what character " + playerId + " placed in their house.");

		// Only once the write has landed: hand the ids back and mark the rest saved.
		// The DAO did this after its catch, so a failed write still looked saved and
		// its ids were released while the rows were still there.
		Collection<HouseObject<?>> removedObjects = pending(objects, PersistentState.DELETED);
		if (!removedObjects.isEmpty()) {
			IDFactory.getInstance()
					.releaseIds(Collections2.transform(removedObjects, AionObject.OBJECT_TO_ID_TRANSFORMER));
		}
		for (HouseDecoration part : pendingParts(parts, PersistentState.DELETED)) {
			if (part.getObjectId() != 0) {
				IDFactory.getInstance().releaseId(part.getObjectId());
			}
		}

		for (HouseObject<?> object : objects) {
			if (object.getPersistentState() == PersistentState.DELETED) {
				registry.discardObject(object.getObjectId());
			} else {
				object.setPersistentState(PersistentState.UPDATED);
			}
		}
		for (HouseDecoration part : parts) {
			if (part.getPersistentState() == PersistentState.DELETED) {
				registry.discardPart(part);
			} else {
				part.setPersistentState(PersistentState.UPDATED);
			}
		}
		registry.setPersistentState(PersistentState.UPDATED);
	}

	private void writeObjects(Connection connection, List<HouseObject<?>> objects, int playerId,
			PersistentState wanted, String query) throws SQLException {
		Collection<HouseObject<?>> writing = pending(objects, wanted);
		if (writing.isEmpty()) {
			return;
		}

		try (PreparedStatement statement = connection.prepareStatement(query)) {
			for (HouseObject<?> object : writing) {
				if (object.getExpireTime() > 0) {
					statement.setInt(1, object.getExpireTime());
				} else {
					statement.setNull(1, Types.INTEGER);
				}
				if (object.getColor() == null) {
					statement.setNull(2, Types.INTEGER);
				} else {
					statement.setInt(2, object.getColor());
				}
				statement.setInt(3, object.getColorExpireEnd());
				statement.setInt(4, object.getOwnerUsedCount());
				statement.setInt(5, object.getVisitorUsedCount());
				statement.setFloat(6, object.getX());
				statement.setFloat(7, object.getY());
				statement.setFloat(8, object.getZ());
				statement.setInt(9, object.getHeading());
				boolean placed = object.getX() > 0 || object.getY() > 0 || object.getZ() > 0;
				statement.setString(10, placed ? object.getPlaceArea().toString() : "NONE");
				statement.setByte(11, (byte) 0);
				statement.setInt(12, playerId);
				statement.setInt(13, object.getObjectId());
				statement.setInt(14, object.getObjectTemplate().getTemplateId());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private void writeParts(Connection connection, List<HouseDecoration> parts, int playerId, PersistentState wanted,
			String query) throws SQLException {
		Collection<HouseDecoration> writing = pendingParts(parts, wanted);
		if (writing.isEmpty()) {
			return;
		}

		try (PreparedStatement statement = connection.prepareStatement(query)) {
			for (HouseDecoration part : writing) {
				statement.setNull(1, Types.INTEGER);
				statement.setNull(2, Types.INTEGER);
				statement.setInt(3, 0);
				statement.setInt(4, part.isUsed() ? 1 : 0);
				statement.setInt(5, 0);
				statement.setFloat(6, 0);
				statement.setFloat(7, 0);
				statement.setFloat(8, 0);
				statement.setInt(9, 0);
				statement.setString(10, DECORATION_AREA);
				statement.setByte(11, part.getFloor());
				statement.setInt(12, playerId);
				statement.setInt(13, part.getObjectId());
				statement.setInt(14, part.getTemplate().getId());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private void deleteObjects(Connection connection, List<HouseObject<?>> objects) throws SQLException {
		Collection<HouseObject<?>> removing = pending(objects, PersistentState.DELETED);
		if (removing.isEmpty()) {
			return;
		}

		try (PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			for (HouseObject<?> object : removing) {
				statement.setInt(1, object.getObjectId());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private void deleteParts(Connection connection, List<HouseDecoration> parts) throws SQLException {
		Collection<HouseDecoration> removing = pendingParts(parts, PersistentState.DELETED);
		if (removing.isEmpty()) {
			return;
		}

		try (PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			for (HouseDecoration part : removing) {
				statement.setInt(1, part.getObjectId());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	/**
	 * The two kinds of entry answer their state separately rather than through a
	 * shared type, so each gets its own filter.
	 */
	private static List<HouseObject<?>> pending(List<HouseObject<?>> objects, PersistentState wanted) {
		List<HouseObject<?>> matching = new ArrayList<HouseObject<?>>();
		for (HouseObject<?> object : objects) {
			if (object != null && object.getPersistentState() == wanted) {
				matching.add(object);
			}
		}
		return matching;
	}

	private static List<HouseDecoration> pendingParts(List<HouseDecoration> parts, PersistentState wanted) {
		List<HouseDecoration> matching = new ArrayList<HouseDecoration>();
		for (HouseDecoration part : parts) {
			if (part != null && part.getPersistentState() == wanted) {
				matching.add(part);
			}
		}
		return matching;
	}

	@Override
	public int reset(int playerId) {
		log.info("Taking up everything character " + playerId + " placed in their house.");

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(RESET_PLACED)) {
			statement.setInt(1, playerId);
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to take up what character " + playerId + " placed in their house.", e);
		}
	}
}
