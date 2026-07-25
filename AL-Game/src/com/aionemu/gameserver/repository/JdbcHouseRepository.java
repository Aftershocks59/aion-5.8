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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.house.House;
import com.aionemu.gameserver.model.house.HouseStatus;
import com.aionemu.gameserver.model.templates.housing.Building;
import com.aionemu.gameserver.model.templates.housing.BuildingType;
import com.aionemu.gameserver.model.templates.housing.HouseAddress;
import com.aionemu.gameserver.model.templates.housing.HousingLand;

/**
 * Reads and writes the houses and studios the world has built, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcHouseRepository extends JdbcRepositorySupport implements HouseRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcHouseRepository.class);

	/** The two addresses that hold studios rather than houses. */
	private static final String STUDIO_ADDRESSES = "(2001, 3001)";

	private static final String SELECT_USED_IDS = "SELECT DISTINCT `id` FROM `houses`";
	private static final String SELECT_COLUMNS = "SELECT `id`,`address`,`building_id`,`player_id`,`acquire_time`,"
			+ "`settings`,`status`,`fee_paid`,`next_pay`,`sell_started`,`sign_notice` FROM `houses` WHERE `address` ";
	private static final String SELECT_HOUSES = SELECT_COLUMNS + "NOT IN " + STUDIO_ADDRESSES;
	private static final String SELECT_STUDIOS = SELECT_COLUMNS + "IN " + STUDIO_ADDRESSES;
	private static final String INSERT_ONE = "INSERT INTO `houses` (`id`,`address`,`building_id`,`player_id`,"
			+ "`acquire_time`,`settings`,`status`,`fee_paid`,`next_pay`,`sell_started`,`sign_notice`)"
			+ " VALUES (?,?,?,?,?,?,?,?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `houses` SET `building_id` = ?, `player_id` = ?,"
			+ " `acquire_time` = ?, `settings` = ?, `status` = ?, `fee_paid` = ?, `next_pay` = ?,"
			+ " `sell_started` = ?, `sign_notice` = ? WHERE `id` = ?";
	private static final String DELETE_FOR_PLAYER = "DELETE FROM `houses` WHERE `player_id` = ?";

	public JdbcHouseRepository(DataSource dataSource) {
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
			throw new RepositoryException("Failed to read the house ids already in use.", e);
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
	public Map<Integer, House> load(Collection<HousingLand> lands, boolean studios) {
		Map<Integer, HouseAddress> addresses = new HashMap<Integer, HouseAddress>();
		Map<Integer, List<Building>> buildings = new HashMap<Integer, List<Building>>();
		for (HousingLand land : lands) {
			for (HouseAddress address : land.getAddresses()) {
				addresses.put(Integer.valueOf(address.getId()), address);
				buildings.put(Integer.valueOf(address.getId()), land.getBuildings());
			}
		}

		Map<Integer, House> built = new LinkedHashMap<Integer, House>();
		Map<Integer, Integer> takenAddresses = new HashMap<Integer, Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(studios ? SELECT_STUDIOS : SELECT_HOUSES);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				House house = read(rows, addresses, buildings, takenAddresses);
				if (house == null) {
					continue;
				}
				int key = studios ? house.getOwnerId() : house.getAddress().getId();
				built.put(Integer.valueOf(key), house);
			}
		} catch (SQLException | IOException e) {
			throw new RepositoryException("Failed to read the houses.", e);
		}

		return built;
	}

	private House read(ResultSet rows, Map<Integer, HouseAddress> addresses, Map<Integer, List<Building>> buildings,
			Map<Integer, Integer> takenAddresses) throws SQLException, IOException {
		int houseId = rows.getInt("id");
		int addressId = rows.getInt("address");

		HouseAddress address = addresses.get(Integer.valueOf(addressId));
		if (address == null) {
			// A row naming an address the world does not have. The DAO dereferenced
			// this and lost every remaining house to the catch.
			log.warn("Ignoring house " + houseId + ": the world has no address " + addressId + ".");
			return null;
		}
		if (takenAddresses.containsKey(Integer.valueOf(addressId))) {
			log.warn("Duplicate house address " + addressId + "!");
			return null;
		}

		Building building = findBuilding(buildings.get(Integer.valueOf(addressId)), rows.getInt("building_id"));
		if (building == null) {
			log.warn("Ignoring house " + houseId + ": address " + addressId + " has no building "
					+ rows.getInt("building_id") + ".");
			return null;
		}

		House house = new House(houseId, building, address, 0);
		if (building.getType() == BuildingType.PERSONAL_FIELD) {
			takenAddresses.put(Integer.valueOf(addressId), Integer.valueOf(houseId));
		}

		house.setOwnerId(rows.getInt("player_id"));
		house.setAcquiredTime(rows.getTimestamp("acquire_time"));
		house.setPermissions(rows.getInt("settings"));
		house.setStatus(HouseStatus.valueOf(rows.getString("status")));
		house.setFeePaid(rows.getInt("fee_paid") != 0);
		house.setNextPay(rows.getTimestamp("next_pay"));
		house.setSellStarted(rows.getTimestamp("sell_started"));

		try (InputStream notice = rows.getBinaryStream("sign_notice")) {
			if (notice != null) {
				byte[] bytes = new byte[House.NOTICE_LENGTH];
				if (notice.read(bytes) > 0) {
					house.setSignNotice(bytes);
				}
			}
		}

		return house;
	}

	private static Building findBuilding(List<Building> candidates, int buildingId) {
		if (candidates == null) {
			return null;
		}
		for (Building building : candidates) {
			if (building.getId() == buildingId) {
				return building;
			}
		}
		return null;
	}

	@Override
	public void save(House house) {
		if (house == null) {
			throw new IllegalArgumentException("Cannot store a null house.");
		}

		boolean isNew = house.getPersistentState() == PersistentState.NEW;
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(isNew ? INSERT_ONE : UPDATE_ONE)) {
			int index = 1;
			if (isNew) {
				statement.setInt(index++, house.getObjectId());
				statement.setInt(index++, house.getAddress().getId());
			}
			statement.setInt(index++, house.getBuilding().getId());
			statement.setInt(index++, house.getOwnerId());
			setTimestamp(statement, index++, house.getAcquiredTime());
			statement.setInt(index++, house.getPermissions());
			statement.setString(index++, house.getStatus().toString());
			statement.setInt(index++, house.isFeePaid() ? 1 : 0);
			setTimestamp(statement, index++, house.getNextPay());
			setTimestamp(statement, index++, house.getSellStarted());

			byte[] notice = house.getSignNotice();
			if (notice.length == 0) {
				statement.setNull(index++, Types.BINARY);
			} else {
				statement.setBinaryStream(index++, new ByteArrayInputStream(notice));
			}
			if (!isNew) {
				statement.setInt(index, house.getObjectId());
			}

			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to store house " + house.getObjectId() + ".", e);
		}

		// Mark it built only once the write has landed.
		if (isNew) {
			house.setPersistentState(PersistentState.UPDATED);
		}
	}

	@Override
	public int removeFor(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_FOR_PLAYER)) {
			statement.setInt(1, playerId);
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to pull down the houses of character " + playerId + ".", e);
		}
	}

	private static void setTimestamp(PreparedStatement statement, int index, Timestamp value) throws SQLException {
		if (value == null) {
			statement.setNull(index, Types.TIMESTAMP);
		} else {
			statement.setTimestamp(index, value);
		}
	}
}
