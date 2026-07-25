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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.configs.main.CacheConfig;
import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.PlayerInitialData;
import com.aionemu.gameserver.dataholders.PlayerInitialData.LocationData;
import com.aionemu.gameserver.model.Gender;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.account.PlayerAccountData;
import com.aionemu.gameserver.model.gameobjects.player.Mailbox;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.model.gameobjects.player.PlayerUpgradeArcade;
import com.aionemu.gameserver.model.team.legion.LegionJoinRequestState;
import com.aionemu.gameserver.world.MapRegion;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.WorldPosition;

/**
 * Reads and writes the characters themselves, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerRepository extends JdbcRepositorySupport implements PlayerRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcPlayerRepository.class);

	private static final String SELECT_USED_IDS = "SELECT `id` FROM `players`";
	private static final String SELECT_NAME_USED = "SELECT 1 FROM `players` WHERE `name` = ? LIMIT 1";
	private static final String SELECT_ONE = "SELECT * FROM `players` WHERE `id` = ?";
	private static final String SELECT_ID_BY_NAME = "SELECT `id` FROM `players` WHERE `name` = ?";
	private static final String SELECT_NAME_BY_ID = "SELECT `name` FROM `players` WHERE `id` = ?";
	private static final String SELECT_ACCOUNT_BY_NAME = "SELECT `account_id` FROM `players` WHERE `name` = ?";
	private static final String SELECT_ON_ACCOUNT = "SELECT `id` FROM `players` WHERE `account_id` = ?";
	private static final String SELECT_JOIN_STATE = "SELECT `join_state` FROM `players` WHERE `id` = ?";
	private static final String SELECT_CREATION_DELETION = "SELECT `creation_date`,`deletion_date` FROM `players`"
			+ " WHERE `id` = ?";
	private static final String COUNT_ON_ACCOUNT = "SELECT COUNT(*) AS `cnt` FROM `players` WHERE `account_id` = ?"
			+ " AND (`deletion_date` IS NULL OR `deletion_date` > CURRENT_TIMESTAMP)";
	private static final String COUNT_ACCOUNTS_FOR_RACE = "SELECT COUNT(DISTINCT `account_name`) AS `cnt`"
			+ " FROM `players` WHERE `race` = ? AND `exp` >= ?";
	private static final String COUNT_ONLINE = "SELECT COUNT(*) AS `cnt` FROM `players` WHERE `online` = 1";
	private static final String SELECT_INACTIVE = "SELECT `id` FROM `players`"
			+ " WHERE UNIX_TIMESTAMP(CURDATE()) - UNIX_TIMESTAMP(`last_online`) > ? * 24 * 60 * 60";

	private static final String INSERT_ONE = "INSERT INTO `players` (`id`,`name`,`account_id`,`account_name`,`x`,`y`,"
			+ "`z`,`heading`,`world_id`,`gender`,`race`,`player_class`,`quest_expands`,`npc_expands`,"
			+ "`warehouse_size`,`bonus_title_id`,`is_archdaeva`,`online`,`stamps`,`rewarded_pass`)"
			+ " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,0,1)";
	private static final String UPDATE_ONE = "UPDATE `players` SET `name` = ?, `exp` = ?, `recoverexp` = ?, `x` = ?,"
			+ " `y` = ?, `z` = ?, `heading` = ?, `world_id` = ?, `gender` = ?, `race` = ?, `player_class` = ?,"
			+ " `last_online` = ?, `quest_expands` = ?, `npc_expands` = ?, `advenced_stigma_slot_size` = ?,"
			+ " `warehouse_size` = ?, `note` = ?, `title_id` = ?, `bonus_title_id` = ?, `dp` = ?,"
			+ " `soul_sickness` = ?, `mailbox_letters` = ?, `reposte_energy` = ?, `mentor_flag_time` = ?,"
			+ " `world_owner` = ?, `stamps` = ?, `rewarded_pass` = ?, `last_stamp` = ?, `passport_time` = ?,"
			+ " `is_archdaeva` = ?, `aura_of_growth` = ?, `join_legion_id` = ?, `join_state` = ?,"
			+ " `berdin_star` = ?, `abyss_favor` = ?, `frenzy_points` = ?, `frenzy_count` = ?, `toc_floor` = ?,"
			+ " `minion_skill_points` = ?, `minion_function_time` = ? WHERE `id` = ?";
	private static final String UPDATE_NAME = "UPDATE `players` SET `name` = ? WHERE `id` = ?";
	private static final String UPDATE_CREATION = "UPDATE `players` SET `creation_date` = ? WHERE `id` = ?";
	private static final String UPDATE_DELETION = "UPDATE `players` SET `deletion_date` = ? WHERE `id` = ?";
	private static final String UPDATE_TRANSFER = "UPDATE `players` SET `last_transfer_time` = ? WHERE `id` = ?";
	private static final String UPDATE_ONLINE = "UPDATE `players` SET `online` = ? WHERE `id` = ?";
	private static final String UPDATE_ALL_ONLINE = "UPDATE `players` SET `online` = ?";
	private static final String UPDATE_JOIN_STATE = "UPDATE `players` SET `join_state` = ? WHERE `id` = ?";
	private static final String CLEAR_JOIN_REQUEST = "UPDATE `players` SET `join_legion_id` = 0,"
			+ " `join_state` = 'NONE' WHERE `id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `players` WHERE `id` = ?";

	private final Map<Integer, PlayerCommonData> byId = new ConcurrentHashMap<Integer, PlayerCommonData>();
	private final Map<String, PlayerCommonData> byName = new ConcurrentHashMap<String, PlayerCommonData>();

	public JdbcPlayerRepository(DataSource dataSource) {
		super(dataSource);
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
			throw new RepositoryException("Failed to read the character ids already in use.", e);
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
			log.error("Cannot tell whether the character name " + name + " is taken; treating it as taken.", e);
			return true;
		}
	}

	@Override
	public PlayerCommonData load(int playerId) {
		PlayerCommonData cached = byId.get(Integer.valueOf(playerId));
		if (cached != null) {
			return cached;
		}

		PlayerCommonData character = new PlayerCommonData(playerId);
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return null;
				}
				read(character, rows);
			}
		} catch (SQLException e) {
			// The DAO caught this and did nothing at all, not even a log line, so a
			// character whose read had failed simply looked as though they had never
			// existed.
			throw new RepositoryException("Failed to read character " + playerId + ".", e);
		}

		remember(character);
		return character;
	}

	private static void read(PlayerCommonData character, ResultSet rows) throws SQLException {
		character.setName(rows.getString("name"));
		// The class settles what the experience means, so it goes first.
		character.setPlayerClass(PlayerClass.valueOf(rows.getString("player_class")));
		character.setExp(rows.getLong("exp"), false);
		character.setRecoverableExp(rows.getLong("recoverexp"));
		character.setRace(Race.valueOf(rows.getString("race")));
		character.setGender(Gender.valueOf(rows.getString("gender")));
		character.setLastOnline(rows.getTimestamp("last_online"));
		character.setNote(rows.getString("note"));
		character.setQuestExpands(rows.getInt("quest_expands"));
		character.setNpcExpands(rows.getInt("npc_expands"));
		character.setAdvancedStigmaSlotSize(rows.getInt("advenced_stigma_slot_size"));
		character.setTitleId(rows.getInt("title_id"));
		character.setBonusTitleId(rows.getInt("bonus_title_id"));
		character.setWarehouseSize(rows.getInt("warehouse_size"));
		character.setOnline(rows.getBoolean("online"));
		character.setMailboxLetters(rows.getInt("mailbox_letters"));
		character.setDp(rows.getInt("dp"));
		character.setDeathCount(rows.getInt("soul_sickness"));
		character.setCurrentReposteEnergy(rows.getLong("reposte_energy"));

		character.setPosition(placeOf(character, rows));

		character.setWorldOwnerId(rows.getInt("world_owner"));
		character.setMentorFlagTime(rows.getInt("mentor_flag_time"));
		character.setLastTransferTime(rows.getLong("last_transfer_time"));
		character.setPassportStamps(rows.getInt("stamps"));
		character.setPassportReward(rows.getInt("rewarded_pass"));
		character.setLastStamp(rows.getTimestamp("last_stamp"));
		character.setPassportTime(rows.getInt("passport_time"));
		character.setArchDaeva(rows.getBoolean("is_archdaeva"));
		character.addAuraOfGrowth(rows.getLong("aura_of_growth"));
		character.setJoinRequestLegionId(rows.getInt("join_legion_id"));
		character.setJoinRequestState(LegionJoinRequestState.valueOf(rows.getString("join_state")));
		character.addBerdinStar(rows.getLong("berdin_star"));
		character.addAbyssFavor(rows.getLong("abyss_favor"));
		character.setFloor(rows.getInt("toc_floor"));
		character.setMinionSkillPoints(rows.getInt("minion_skill_points"));
		character.setMinionFunctionTime(rows.getTimestamp("minion_function_time"));

		// The DAO built one of these, filled it and never hung it on the character,
		// so the frenzy was written on every save and restored on none.
		PlayerUpgradeArcade arcade = new PlayerUpgradeArcade();
		arcade.setFrenzyPoints(rows.getInt("frenzy_points"));
		arcade.setFrenzyCount(rows.getInt("frenzy_count"));
		character.setUpgradeArcade(arcade);
	}

	/** Puts a character back where they logged out, or at their starting point. */
	private static WorldPosition placeOf(PlayerCommonData character, ResultSet rows) throws SQLException {
		float x = rows.getFloat("x");
		float y = rows.getFloat("y");
		float z = rows.getFloat("z");
		byte heading = rows.getByte("heading");
		int worldId = rows.getInt("world_id");

		MapRegion region = World.getInstance().getWorldMap(worldId).getMainWorldMapInstance().getRegion(x, y, z);
		PlayerInitialData initial = DataManager.PLAYER_INITIAL_DATA;
		if (region == null && initial != null) {
			LocationData start = initial.getSpawnLocation(character.getRace());
			x = start.getX();
			y = start.getY();
			z = start.getZ();
			heading = start.getHeading();
			worldId = start.getMapId();
		}

		return World.getInstance().createPosition(worldId, x, y, z, heading, 0);
	}

	@Override
	public PlayerCommonData loadByName(String name) {
		Player online = World.getInstance().findPlayer(name);
		if (online != null) {
			return online.getCommonData();
		}

		PlayerCommonData cached = byName.get(name.toLowerCase());
		if (cached != null) {
			return cached;
		}

		int playerId = findIdByName(name);
		return playerId == NO_CHARACTER ? null : load(playerId);
	}

	private void remember(PlayerCommonData character) {
		if (CacheConfig.CACHE_COMMONDATA) {
			byId.put(Integer.valueOf(character.getPlayerObjId()), character);
			byName.put(character.getName().toLowerCase(), character);
		}
	}

	@Override
	public Map<Integer, String> findNames(Collection<Integer> playerIds) {
		if (playerIds == null || playerIds.isEmpty()) {
			return Collections.emptyMap();
		}

		// One placeholder per id, where the DAO spliced them straight into the
		// statement.
		StringBuilder query = new StringBuilder("SELECT `id`,`name` FROM `players` WHERE `id` IN (");
		for (int i = 0; i < playerIds.size(); i++) {
			query.append(i == 0 ? "?" : ",?");
		}
		query.append(')');

		Map<Integer, String> names = new HashMap<Integer, String>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query.toString())) {
			int index = 1;
			for (Integer playerId : playerIds) {
				statement.setInt(index++, playerId.intValue());
			}
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					names.put(Integer.valueOf(rows.getInt("id")), rows.getString("name"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the names of " + playerIds.size() + " characters.", e);
		}

		return names;
	}

	@Override
	public String findName(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_NAME_BY_ID)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				// The DAO read the row without checking there was one.
				return rows.next() ? rows.getString("name") : null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the name of character " + playerId + ".", e);
		}
	}

	@Override
	public int findIdByName(String name) {
		return readInt(SELECT_ID_BY_NAME, "id", statement -> statement.setString(1, name),
				"the character called " + name);
	}

	@Override
	public int findAccountIdByName(String name) {
		return readInt(SELECT_ACCOUNT_BY_NAME, "account_id", statement -> statement.setString(1, name),
				"the account of " + name);
	}

	@Override
	public int countOnAccount(int accountId) {
		return readInt(COUNT_ON_ACCOUNT, "cnt", statement -> statement.setInt(1, accountId),
				"the characters on account " + accountId);
	}

	@Override
	public int countAccountsForRace(Race race) {
		long fromExp = DataManager.PLAYER_EXPERIENCE_TABLE
				.getStartExpForLevel(GSConfig.RATIO_MIN_REQUIRED_LEVEL);
		return readInt(COUNT_ACCOUNTS_FOR_RACE, "cnt", statement -> {
			statement.setString(1, race.name());
			statement.setLong(2, fromExp);
		}, "the accounts playing " + race);
	}

	@Override
	public int countOnline() {
		return readInt(COUNT_ONLINE, "cnt", statement -> {
			// Nothing to bind.
		}, "the characters online");
	}

	private int readInt(String query, String column, Binding binding, String description) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			binding.bind(statement);
			try (ResultSet rows = statement.executeQuery()) {
				// The DAO read the row without checking there was one, and answered
				// zero from a catch when there was not.
				return rows.next() ? rows.getInt(column) : NO_CHARACTER;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read " + description + ".", e);
		}
	}

	@Override
	public List<Integer> findIdsOnAccount(int accountId) {
		// Empty rather than null, which the DAO answered when the read failed.
		return readIds(SELECT_ON_ACCOUNT, statement -> statement.setInt(1, accountId),
				"the characters on account " + accountId);
	}

	@Override
	public List<Integer> findInactive(int daysOfInactivity, int limit) {
		String query = limit > 0 ? SELECT_INACTIVE + " LIMIT " + limit : SELECT_INACTIVE;
		return readIds(query, statement -> statement.setInt(1, daysOfInactivity),
				"the characters away for " + daysOfInactivity + " days");
	}

	private List<Integer> readIds(String query, Binding binding, String description) {
		List<Integer> ids = new ArrayList<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			binding.bind(statement);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					ids.add(Integer.valueOf(rows.getInt("id")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read " + description + ".", e);
		}

		return ids;
	}

	@Override
	public boolean add(PlayerCommonData character, int accountId, String accountName) {
		if (character == null) {
			throw new IllegalArgumentException("Cannot create a null character.");
		}

		boolean written = write(INSERT_ONE, "create character " + character.getPlayerObjId(), statement -> {
			statement.setInt(1, character.getPlayerObjId());
			statement.setString(2, character.getName());
			statement.setInt(3, accountId);
			statement.setString(4, accountName);
			statement.setFloat(5, character.getPosition().getX());
			statement.setFloat(6, character.getPosition().getY());
			statement.setFloat(7, character.getPosition().getZ());
			statement.setInt(8, character.getPosition().getHeading());
			statement.setInt(9, character.getPosition().getMapId());
			statement.setString(10, character.getGender().toString());
			statement.setString(11, character.getRace().toString());
			statement.setString(12, character.getPlayerClass().toString());
			statement.setInt(13, character.getQuestExpands());
			statement.setInt(14, character.getNpcExpands());
			statement.setInt(15, character.getWarehouseSize());
			statement.setInt(16, character.getBonusTitleId());
			statement.setBoolean(17, character.isArchDaeva());
		});

		if (written) {
			remember(character);
		}
		return written;
	}

	@Override
	public boolean save(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot store a null character.");
		}

		PlayerCommonData character = player.getCommonData();
		Mailbox mailbox = player.getMailbox();
		int letters = mailbox == null ? character.getMailboxLetters() : mailbox.size();

		boolean written = write(UPDATE_ONE, "store character " + player.getObjectId(), statement -> {
			statement.setString(1, player.getName());
			statement.setLong(2, character.getExp());
			statement.setLong(3, character.getExpRecoverable());
			statement.setFloat(4, player.getX());
			statement.setFloat(5, player.getY());
			statement.setFloat(6, player.getZ());
			statement.setInt(7, player.getHeading());
			statement.setInt(8, player.getWorldId());
			statement.setString(9, player.getGender().toString());
			statement.setString(10, player.getRace().toString());
			statement.setString(11, character.getPlayerClass().toString());
			statement.setTimestamp(12, character.getLastOnline());
			statement.setInt(13, player.getQuestExpands());
			statement.setInt(14, player.getNpcExpands());
			statement.setInt(15, character.getAdvancedStigmaSlotSize());
			statement.setInt(16, player.getWarehouseSize());
			statement.setString(17, character.getNote());
			statement.setInt(18, character.getTitleId());
			statement.setInt(19, character.getBonusTitleId());
			statement.setInt(20, character.getDp());
			statement.setInt(21, character.getDeathCount());
			statement.setInt(22, letters);
			statement.setLong(23, character.getCurrentReposteEnergy());
			statement.setInt(24, character.getMentorFlagTime());
			statement.setInt(25, player.getPosition().getWorldMapInstance().getOwnerId());
			statement.setInt(26, character.getPassportStamps());
			statement.setInt(27, character.getPassportReward());
			statement.setTimestamp(28, character.getLastStamp());
			statement.setInt(29, character.getPassportTime());
			statement.setBoolean(30, character.isArchDaeva());
			statement.setLong(31, character.getAuraOfGrowth());
			statement.setInt(32, character.getJoinRequestLegionId());
			statement.setString(33, character.getJoinRequestState().toString());
			statement.setLong(34, character.getBerdinStar());
			statement.setLong(35, character.getAbyssFavor());
			statement.setInt(36, player.getUpgradeArcade().getFrenzyPoints());
			statement.setInt(37, player.getUpgradeArcade().getFrenzyCount());
			statement.setInt(38, character.getFloor());
			statement.setInt(39, character.getMinionSkillPoints());
			statement.setTimestamp(40, character.getMinionFunctionTime());
			statement.setInt(41, player.getObjectId());
		});

		// Refresh the cache only for a character already in it, as it was.
		if (CacheConfig.CACHE_COMMONDATA && byId.containsKey(Integer.valueOf(player.getObjectId()))) {
			remember(character);
		}
		return written;
	}

	@Override
	public boolean saveName(PlayerCommonData character) {
		if (character == null) {
			throw new IllegalArgumentException("Cannot rename a null character.");
		}

		return write(UPDATE_NAME, "rename character " + character.getPlayerObjId(), statement -> {
			statement.setString(1, character.getName());
			statement.setInt(2, character.getPlayerObjId());
		});
	}

	@Override
	public boolean remove(int playerId) {
		PlayerCommonData forgotten = byId.remove(Integer.valueOf(playerId));
		if (forgotten != null) {
			byName.remove(forgotten.getName().toLowerCase());
		}

		// The DAO caught the failure to bind this and ran the statement anyway.
		return write(DELETE_ONE, "delete character " + playerId, statement -> statement.setInt(1, playerId));
	}

	@Override
	public void loadCreationAndDeletion(PlayerAccountData account) {
		if (account == null) {
			throw new IllegalArgumentException("Cannot read the dates of a null account entry.");
		}

		int playerId = account.getPlayerCommonData().getPlayerObjId();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_CREATION_DELETION)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) {
					account.setCreationDate(rows.getTimestamp("creation_date"));
					account.setDeletionDate(rows.getTimestamp("deletion_date"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the dates of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean setCreationTime(int playerId, Timestamp at) {
		return write(UPDATE_CREATION, "record when character " + playerId + " was created", statement -> {
			statement.setTimestamp(1, at);
			statement.setInt(2, playerId);
		});
	}

	@Override
	public boolean setDeletionTime(int playerId, Timestamp at) {
		return write(UPDATE_DELETION, "record when character " + playerId + " is due to go", statement -> {
			statement.setTimestamp(1, at);
			statement.setInt(2, playerId);
		});
	}

	@Override
	public boolean setLastTransferTime(int playerId, long at) {
		return write(UPDATE_TRANSFER, "record when character " + playerId + " was last moved", statement -> {
			statement.setLong(1, at);
			statement.setInt(2, playerId);
		});
	}

	@Override
	public boolean setOnline(int playerId, boolean online) {
		return write(UPDATE_ONLINE, "record whether character " + playerId + " is online", statement -> {
			statement.setBoolean(1, online);
			statement.setInt(2, playerId);
		});
	}

	@Override
	public int setAllOnline(boolean online) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ALL_ONLINE)) {
			statement.setBoolean(1, online);
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record every character as " + (online ? "online" : "offline")
					+ ".", e);
		}
	}

	@Override
	public boolean setJoinRequestState(int playerId, LegionJoinRequestState state) {
		return write(UPDATE_JOIN_STATE, "record the legion request of character " + playerId, statement -> {
			statement.setString(1, state.name());
			statement.setInt(2, playerId);
		});
	}

	@Override
	public boolean clearJoinRequest(int playerId) {
		// The DAO prepared this statement, bound its parameters and never ran it,
		// so a character's request to join a legion was never cleared.
		return write(CLEAR_JOIN_REQUEST, "forget the legion request of character " + playerId,
				statement -> statement.setInt(1, playerId));
	}

	@Override
	public void loadJoinRequestState(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot read the legion request of a null character.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_JOIN_STATE)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) {
					player.getCommonData()
							.setJoinRequestState(LegionJoinRequestState.valueOf(rows.getString("join_state")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the legion request of character " + player.getObjectId() + ".", e);
		}
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
