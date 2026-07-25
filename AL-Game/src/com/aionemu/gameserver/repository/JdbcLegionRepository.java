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
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.team.legion.Legion;
import com.aionemu.gameserver.model.team.legion.LegionEmblem;
import com.aionemu.gameserver.model.team.legion.LegionEmblemType;
import com.aionemu.gameserver.model.team.legion.LegionHistory;
import com.aionemu.gameserver.model.team.legion.LegionHistoryType;
import com.aionemu.gameserver.model.team.legion.LegionJoinRequest;
import com.aionemu.gameserver.model.team.legion.LegionTerritory;
import com.aionemu.gameserver.model.team.legion.LegionWarehouse;

/**
 * Reads and writes the legions, their emblems, their history and their
 * warehouse, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcLegionRepository extends JdbcRepositorySupport implements LegionRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcLegionRepository.class);

	/** How many notices a legion's board shows. */
	private static final int NOTICE_BOARD_SIZE = 7;

	private static final String LEGION_COLUMNS = "`id`,`name`,`level`,`contribution_points`,`deputy_permission`,"
			+ "`centurion_permission`,`legionary_permission`,`volunteer_permission`,`disband_time`,`description`,"
			+ "`joinType`,`minJoinLevel`,`territory`";

	private static final String SELECT_USED_IDS = "SELECT `id` FROM `legions`";
	private static final String SELECT_NAME_USED = "SELECT 1 FROM `legions` WHERE `name` = ? LIMIT 1";
	private static final String SELECT_BY_ID = "SELECT " + LEGION_COLUMNS + " FROM `legions` WHERE `id` = ?";
	private static final String SELECT_BY_NAME = "SELECT " + LEGION_COLUMNS + " FROM `legions` WHERE `name` = ?";
	private static final String SELECT_WITH_TERRITORY = "SELECT `id` FROM `legions` WHERE `territory` > 0";
	private static final String INSERT_ONE = "INSERT INTO `legions` (`id`,`name`) VALUES (?,?)";
	private static final String UPDATE_ONE = "UPDATE `legions` SET `name` = ?, `level` = ?,"
			+ " `contribution_points` = ?, `deputy_permission` = ?, `centurion_permission` = ?,"
			+ " `legionary_permission` = ?, `volunteer_permission` = ?, `disband_time` = ?, `description` = ?,"
			+ " `joinType` = ?, `minJoinLevel` = ?, `territory` = ? WHERE `id` = ?";
	private static final String UPDATE_DESCRIPTION = "UPDATE `legions` SET `description` = ?, `joinType` = ?,"
			+ " `minJoinLevel` = ? WHERE `id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `legions` WHERE `id` = ?";
	private static final String CLEAR_SIEGE_HOLDINGS = "UPDATE `siege_locations` SET `legion_id` = 0"
			+ " WHERE `legion_id` = ?";

	private static final String SELECT_NOTICES = "SELECT `announcement`,`date` FROM `legion_announcement_list`"
			+ " WHERE `legion_id` = ? ORDER BY `date` ASC LIMIT " + NOTICE_BOARD_SIZE;
	private static final String INSERT_NOTICE = "INSERT INTO `legion_announcement_list`"
			+ " (`legion_id`,`announcement`,`date`) VALUES (?,?,?)";
	private static final String DELETE_NOTICE = "DELETE FROM `legion_announcement_list`"
			+ " WHERE `legion_id` = ? AND `date` = ?";

	private static final String SELECT_EMBLEM = "SELECT `emblem_id`,`color_r`,`color_g`,`color_b`,`emblem_type`,"
			+ "`emblem_data` FROM `legion_emblems` WHERE `legion_id` = ?";
	private static final String UPSERT_EMBLEM = "INSERT INTO `legion_emblems`"
			+ " (`legion_id`,`emblem_id`,`color_r`,`color_g`,`color_b`,`emblem_type`,`emblem_data`)"
			+ " VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE `emblem_id` = VALUES(`emblem_id`),"
			+ " `color_r` = VALUES(`color_r`), `color_g` = VALUES(`color_g`), `color_b` = VALUES(`color_b`),"
			+ " `emblem_type` = VALUES(`emblem_type`), `emblem_data` = VALUES(`emblem_data`)";

	private static final String SELECT_HISTORY = "SELECT `date`,`history_type`,`name`,`tab_id`,`description`"
			+ " FROM `legion_history` WHERE `legion_id` = ? ORDER BY `date` ASC";
	private static final String INSERT_HISTORY = "INSERT INTO `legion_history`"
			+ " (`legion_id`,`date`,`history_type`,`name`,`tab_id`,`description`) VALUES (?,?,?,?,?,?)";

	private static final String SELECT_JOIN_REQUESTS = "SELECT `legionId`,`playerId`,`playerName`,`playerClassId`,"
			+ "`playerRaceId`,`playerLevel`,`playerGenderId`,`joinRequestMsg`,`date` FROM `legion_join_requests`"
			+ " WHERE `legionId` = ? ORDER BY `date` ASC";
	private static final String UPSERT_JOIN_REQUEST = "INSERT INTO `legion_join_requests` (`legionId`,`playerId`,"
			+ "`playerName`,`playerClassId`,`playerRaceId`,`playerLevel`,`playerGenderId`,`joinRequestMsg`,`date`)"
			+ " VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE `playerName` = VALUES(`playerName`),"
			+ " `playerClassId` = VALUES(`playerClassId`), `playerRaceId` = VALUES(`playerRaceId`),"
			+ " `playerLevel` = VALUES(`playerLevel`), `playerGenderId` = VALUES(`playerGenderId`),"
			+ " `joinRequestMsg` = VALUES(`joinRequestMsg`), `date` = VALUES(`date`)";
	private static final String DELETE_JOIN_REQUEST = "DELETE FROM `legion_join_requests`"
			+ " WHERE `legionId` = ? AND `playerId` = ?";

	private final InventoryRepository inventories;

	public JdbcLegionRepository(DataSource dataSource, InventoryRepository inventories) {
		super(dataSource);
		this.inventories = inventories;
	}

	@Override
	public int[] findUsedIds() {
		List<Integer> used = new ArrayList<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_USED_IDS);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				used.add(Integer.valueOf(rows.getInt("id")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the legion ids already in use.", e);
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
	public boolean isNameUsed(String name) {
		// One row at most, where the DAO counted every matching row.
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_NAME_USED)) {
			statement.setString(1, name);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		} catch (SQLException e) {
			// Deliberately fails closed: a doubtful name is treated as taken so it
			// is never handed out twice.
			log.error("Cannot tell whether the legion name " + name + " is taken; treating it as taken.", e);
			return true;
		}
	}

	@Override
	public Legion load(int legionId) {
		return read(SELECT_BY_ID, statement -> statement.setInt(1, legionId), "legion " + legionId);
	}

	@Override
	public Legion load(String legionName) {
		return read(SELECT_BY_NAME, statement -> statement.setString(1, legionName), "the legion " + legionName);
	}

	private Legion read(String query, Binding binding, String description) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			binding.bind(statement);
			try (ResultSet rows = statement.executeQuery()) {
				// The DAO decided a legion was missing by comparing its name against
				// the empty literal by reference.
				if (!rows.next()) {
					return null;
				}

				Legion legion = new Legion();
				legion.setLegionId(rows.getInt("id"));
				legion.setLegionName(rows.getString("name"));
				legion.setLegionLevel(rows.getInt("level"));
				legion.addContributionPoints(rows.getLong("contribution_points"));

				int territoryId = rows.getInt("territory");
				LegionTerritory territory = new LegionTerritory(territoryId);
				if (territoryId > 0) {
					territory.setLegionId(legion.getLegionId());
					territory.setLegionName(legion.getLegionName());
				}
				legion.setTerritory(territory);

				legion.setLegionPermissions(rows.getShort("deputy_permission"), rows.getShort("centurion_permission"),
						rows.getShort("legionary_permission"), rows.getShort("volunteer_permission"));
				legion.setDescription(rows.getString("description"));
				legion.setJoinType(rows.getInt("joinType"));
				legion.setMinJoinLevel(rows.getInt("minJoinLevel"));
				legion.setDisbandTime(rows.getInt("disband_time"));

				// Read after the row is done with, where the DAO ran this query from
				// inside its own open result set.
				rows.close();
				for (LegionJoinRequest request : loadJoinRequests(legion.getLegionId())) {
					legion.addJoinRequest(request);
				}
				return legion;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read " + description + ".", e);
		}
	}

	@Override
	public Collection<Integer> findWithTerritory() {
		Collection<Integer> legionIds = new ArrayList<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_WITH_TERRITORY);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				legionIds.add(Integer.valueOf(rows.getInt("id")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read which legions hold territory.", e);
		}

		return legionIds;
	}

	@Override
	public boolean add(Legion legion) {
		if (legion == null) {
			throw new IllegalArgumentException("Cannot found a null legion.");
		}

		return write(INSERT_ONE, "found legion " + legion.getLegionId(), statement -> {
			statement.setInt(1, legion.getLegionId());
			statement.setString(2, legion.getLegionName());
		});
	}

	@Override
	public boolean save(Legion legion) {
		if (legion == null) {
			throw new IllegalArgumentException("Cannot store a null legion.");
		}

		boolean written = write(UPDATE_ONE, "store legion " + legion.getLegionId(), statement -> {
			statement.setString(1, legion.getLegionName());
			statement.setInt(2, legion.getLegionLevel());
			statement.setLong(3, legion.getContributionPoints());
			statement.setInt(4, legion.getDeputyPermission());
			statement.setInt(5, legion.getCenturionPermission());
			statement.setInt(6, legion.getLegionaryPermission());
			statement.setInt(7, legion.getVolunteerPermission());
			statement.setInt(8, legion.getDisbandTime());
			statement.setString(9, legion.getLegionDescription());
			statement.setInt(10, legion.getLegionJoinType());
			statement.setInt(11, legion.getMinLevel());
			statement.setInt(12, legion.getTerritory() == null ? 0 : Math.max(0, legion.getTerritory().getId()));
			statement.setInt(13, legion.getLegionId());
		});

		// After the legion itself, not from inside the statement that writes it.
		for (LegionJoinRequest request : legion.getJoinRequestMap().values()) {
			saveJoinRequest(request);
		}
		return written;
	}

	@Override
	public boolean saveDescription(Legion legion) {
		if (legion == null) {
			throw new IllegalArgumentException("Cannot store the notice board of a null legion.");
		}

		return write(UPDATE_DESCRIPTION, "store the notice board of legion " + legion.getLegionId(), statement -> {
			statement.setString(1, legion.getLegionDescription());
			statement.setInt(2, legion.getLegionJoinType());
			statement.setInt(3, legion.getMinLevel());
			statement.setInt(4, legion.getLegionId());
		});
	}

	@Override
	public void remove(int legionId) {
		// Both writes in one transaction: a legion that is gone must not be left
		// holding a fortress.
		inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
				statement.setInt(1, legionId);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement(CLEAR_SIEGE_HOLDINGS)) {
				statement.setInt(1, legionId);
				statement.executeUpdate();
			}
			return null;
		}, "Failed to disband legion " + legionId + ".");
	}

	@Override
	public TreeMap<Timestamp, String> loadNotices(int legionId) {
		TreeMap<Timestamp, String> notices = new TreeMap<Timestamp, String>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_NOTICES)) {
			statement.setInt(1, legionId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					notices.put(rows.getTimestamp("date"), rows.getString("announcement"));
				}
			}
		} catch (SQLException e) {
			// Empty rather than null, which the DAO answered on failure.
			throw new RepositoryException("Failed to read the notices of legion " + legionId + ".", e);
		}

		return notices;
	}

	@Override
	public boolean addNotice(int legionId, Timestamp at, String message) {
		return write(INSERT_NOTICE, "pin a notice on legion " + legionId, statement -> {
			statement.setInt(1, legionId);
			statement.setString(2, message);
			statement.setTimestamp(3, at);
		});
	}

	@Override
	public boolean removeNotice(int legionId, Timestamp at) {
		return write(DELETE_NOTICE, "take a notice off legion " + legionId, statement -> {
			statement.setInt(1, legionId);
			statement.setTimestamp(2, at);
		});
	}

	@Override
	public LegionEmblem loadEmblem(int legionId) {
		LegionEmblem emblem = new LegionEmblem();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_EMBLEM)) {
			statement.setInt(1, legionId);
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) {
					emblem.setEmblem(rows.getInt("emblem_id"), rows.getInt("color_r"), rows.getInt("color_g"),
							rows.getInt("color_b"), LegionEmblemType.valueOf(rows.getString("emblem_type")),
							rows.getBytes("emblem_data"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the emblem of legion " + legionId + ".", e);
		}

		emblem.setPersistentState(PersistentState.UPDATED);
		return emblem;
	}

	@Override
	public void saveEmblem(int legionId, LegionEmblem emblem) {
		if (emblem == null) {
			throw new IllegalArgumentException("Cannot store a null emblem.");
		}
		// A custom emblem with no artwork behind it is not worth a row.
		if (LegionEmblemType.CUSTOM == emblem.getEmblemType() && emblem.getCustomEmblemData() == null) {
			return;
		}

		// One upsert, where the DAO asked whether the legion already had an emblem
		// and then chose between an insert and an update.
		boolean written = write(UPSERT_EMBLEM, "store the emblem of legion " + legionId, statement -> {
			statement.setInt(1, legionId);
			statement.setInt(2, emblem.getEmblemId());
			statement.setInt(3, emblem.getColor_r());
			statement.setInt(4, emblem.getColor_g());
			statement.setInt(5, emblem.getColor_b());
			statement.setString(6, emblem.getEmblemType().toString());
			statement.setBytes(7, emblem.getCustomEmblemData());
		});

		if (written) {
			emblem.setPersistentState(PersistentState.UPDATED);
		}
	}

	@Override
	public LegionWarehouse loadWarehouse(Legion legion) {
		if (legion == null) {
			throw new IllegalArgumentException("Cannot read the warehouse of a null legion.");
		}

		// The items travel through the inventory repository, where the DAO had its
		// own copy of the thirty-odd column reader.
		LegionWarehouse warehouse = new LegionWarehouse(legion);
		for (Item item : inventories.loadStorageItems(legion.getLegionId(), StorageType.LEGION_WAREHOUSE)) {
			item.setPersistentState(PersistentState.UPDATED);
			warehouse.onLoadHandler(item);
		}
		return warehouse;
	}

	@Override
	public void loadHistory(Legion legion) {
		if (legion == null) {
			throw new IllegalArgumentException("Cannot read the history of a null legion.");
		}

		Collection<LegionHistory> history = legion.getLegionHistory();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_HISTORY)) {
			statement.setInt(1, legion.getLegionId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					history.add(new LegionHistory(LegionHistoryType.valueOf(rows.getString("history_type")),
							rows.getString("name"), rows.getTimestamp("date"), rows.getInt("tab_id"),
							rows.getString("description")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the history of legion " + legion.getLegionId() + ".", e);
		}
	}

	@Override
	public boolean addHistory(int legionId, LegionHistory entry) {
		if (entry == null) {
			throw new IllegalArgumentException("Cannot store a null history entry.");
		}

		return write(INSERT_HISTORY, "record the history of legion " + legionId, statement -> {
			statement.setInt(1, legionId);
			statement.setTimestamp(2, entry.getTime());
			statement.setString(3, entry.getLegionHistoryType().toString());
			statement.setString(4, entry.getName());
			statement.setInt(5, entry.getTabId());
			statement.setString(6, entry.getDescription());
		});
	}

	@Override
	public List<LegionJoinRequest> loadJoinRequests(int legionId) {
		List<LegionJoinRequest> requests = new ArrayList<LegionJoinRequest>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_JOIN_REQUESTS)) {
			statement.setInt(1, legionId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					LegionJoinRequest request = new LegionJoinRequest();
					request.setLegionId(rows.getInt("legionId"));
					request.setPlayerId(rows.getInt("playerId"));
					request.setPlayerName(rows.getString("playerName"));
					request.setPlayerClass(rows.getInt("playerClassId"));
					request.setRace(rows.getInt("playerRaceId"));
					request.setLevel(rows.getInt("playerLevel"));
					request.setGenderId(rows.getInt("playerGenderId"));
					// The DAO wrote this and never read it back, so every candidacy
					// came back with no message on it.
					request.setMsg(rows.getString("joinRequestMsg"));
					request.setDate(rows.getTimestamp("date"));
					requests.add(request);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the candidacies for legion " + legionId + ".", e);
		}

		return requests;
	}

	@Override
	public boolean saveJoinRequest(LegionJoinRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Cannot store a null candidacy.");
		}

		// One upsert. The DAO only ever inserted, and the table keys on the legion
		// and the character, so re-storing a legion threw a duplicate key error
		// that nothing reported and no candidacy was ever updated.
		return write(UPSERT_JOIN_REQUEST, "store the candidacy of character " + request.getPlayerId(), statement -> {
			statement.setInt(1, request.getLegionId());
			statement.setInt(2, request.getPlayerId());
			statement.setString(3, request.getPlayerName());
			statement.setInt(4, request.getPlayerClass());
			statement.setInt(5, request.getRace());
			statement.setInt(6, request.getLevel());
			statement.setInt(7, request.getGenderId());
			statement.setString(8, request.getMsg());
			statement.setTimestamp(9, request.getDate());
		});
	}

	@Override
	public boolean removeJoinRequest(int legionId, int playerId) {
		return write(DELETE_JOIN_REQUEST, "withdraw the candidacy of character " + playerId, statement -> {
			statement.setInt(1, legionId);
			statement.setInt(2, playerId);
		});
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
