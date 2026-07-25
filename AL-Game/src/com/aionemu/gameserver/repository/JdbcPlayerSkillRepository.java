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
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.skill.PlayerSkillEntry;
import com.aionemu.gameserver.model.skill.PlayerSkillList;

/**
 * Reads and writes the skills a character knows, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerSkillRepository extends JdbcRepositorySupport implements PlayerSkillRepository {

	private static final String SELECT_KNOWN = "SELECT `skill_id`,`skill_level`,`skin_id`,`skin_active_date`,`skin_expire_time`,`skin_activated`"
			+ " FROM `player_skills` WHERE `player_id` = ?";
	private static final String SELECT_SKIN_ACTIVATED_AT = "SELECT `skin_active_date` FROM `player_skills`"
			+ " WHERE `player_id` = ? AND `skill_id` = ?";
	private static final String SELECT_SKIN_EXPIRY = "SELECT `skin_expire_time` FROM `player_skills`"
			+ " WHERE `player_id` = ? AND `skill_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_skills` (`player_id`,`skill_id`,`skill_level`) VALUES (?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `player_skills` SET `skill_level` = ?, `skin_id` = ?,"
			+ " `skin_active_date` = ?, `skin_expire_time` = ?, `skin_activated` = ?"
			+ " WHERE `player_id` = ? AND `skill_id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `player_skills` WHERE `player_id` = ? AND `skill_id` = ?";

	public JdbcPlayerSkillRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public PlayerSkillList load(int playerId) {
		List<PlayerSkillEntry> known = new ArrayList<PlayerSkillEntry>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_KNOWN)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					known.add(new PlayerSkillEntry(rows.getInt("skill_id"), false, false, rows.getInt("skill_level"),
							rows.getInt("skin_id"), rows.getTimestamp("skin_active_date"),
							rows.getInt("skin_expire_time"), rows.getBoolean("skin_activated"),
							PersistentState.UPDATED));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the skills of character " + playerId + ".", e);
		}

		return new PlayerSkillList(known);
	}

	@Override
	public void save(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot store the skills of a null character.");
		}

		List<PlayerSkillEntry> pending = new ArrayList<PlayerSkillEntry>();
		Collections.addAll(pending, player.getSkillList().getAllSkills());
		Collections.addAll(pending, player.getSkillList().getDeletedSkills());
		if (pending.isEmpty()) {
			return;
		}

		int playerId = player.getObjectId();
		inTransaction(connection -> {
			// Forget first, so a skill dropped and learned again in the same breath
			// ends up known rather than gone.
			deleteSkills(connection, pending, playerId);
			insertSkills(connection, pending, playerId);
			updateSkills(connection, pending, playerId);
			return null;
		}, "Failed to store the skills of character " + playerId + ".");

		// Mark them saved only now. The DAO did this whatever happened, so a skill
		// whose write had failed still looked saved and was never retried.
		for (PlayerSkillEntry entry : pending) {
			entry.setPersistentState(PersistentState.UPDATED);
		}
	}

	private void deleteSkills(Connection connection, List<PlayerSkillEntry> entries, int playerId) throws SQLException {
		int queued = 0;
		try (PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			for (PlayerSkillEntry entry : entries) {
				if (!matches(entry, PersistentState.DELETED)) {
					continue;
				}
				statement.setInt(1, playerId);
				statement.setInt(2, entry.getSkillId());
				statement.addBatch();
				queued++;
			}
			if (queued > 0) {
				statement.executeBatch();
			}
		}
	}

	private void insertSkills(Connection connection, List<PlayerSkillEntry> entries, int playerId) throws SQLException {
		int queued = 0;
		try (PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			for (PlayerSkillEntry entry : entries) {
				if (!matches(entry, PersistentState.NEW)) {
					continue;
				}
				statement.setInt(1, playerId);
				statement.setInt(2, entry.getSkillId());
				statement.setInt(3, entry.getSkillLevel());
				statement.addBatch();
				queued++;
			}
			if (queued > 0) {
				statement.executeBatch();
			}
		}
	}

	private void updateSkills(Connection connection, List<PlayerSkillEntry> entries, int playerId) throws SQLException {
		// The level and the skin travel together in one statement, where the DAO
		// ran the same filter twice and wrote each skill's row two times over.
		int queued = 0;
		try (PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			for (PlayerSkillEntry entry : entries) {
				if (!matches(entry, PersistentState.UPDATE_REQUIRED)) {
					continue;
				}
				statement.setInt(1, entry.getSkillLevel());
				statement.setInt(2, entry.getSkinId());
				statement.setTimestamp(3, entry.getSkinActiveTime());
				statement.setInt(4, entry.getSkinExpireTime());
				statement.setBoolean(5, entry.isActivated());
				statement.setInt(6, playerId);
				statement.setInt(7, entry.getSkillId());
				statement.addBatch();
				queued++;
			}
			if (queued > 0) {
				statement.executeBatch();
			}
		}
	}

	private static boolean matches(PlayerSkillEntry entry, PersistentState wanted) {
		return entry != null && entry.getPersistentState() == wanted;
	}

	@Override
	public Timestamp findSkinActivatedAt(int playerId, int skillId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_SKIN_ACTIVATED_AT)) {
			statement.setInt(1, playerId);
			statement.setInt(2, skillId);
			try (ResultSet rows = statement.executeQuery()) {
				// The DAO read the row without checking there was one, and answered
				// null from a catch when there was not.
				return rows.next() ? rows.getTimestamp("skin_active_date") : null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read when the skin of skill " + skillId + " of character "
					+ playerId + " was put on.", e);
		}
	}

	@Override
	public int findSkinExpiry(int playerId, int skillId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_SKIN_EXPIRY)) {
			statement.setInt(1, playerId);
			statement.setInt(2, skillId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getInt("skin_expire_time") : NO_EXPIRY;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read how long the skin of skill " + skillId + " of character "
					+ playerId + " lasts.", e);
		}
	}
}
