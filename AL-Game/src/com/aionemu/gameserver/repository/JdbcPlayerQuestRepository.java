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
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/**
 * Reads and writes where a character stands in every quest, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerQuestRepository extends JdbcRepositorySupport implements PlayerQuestRepository {

	private static final String SELECT_TAKEN = "SELECT `quest_id`,`status`,`quest_vars`,`complete_count`,`next_repeat_time`,`reward`,`complete_time`"
			+ " FROM `player_quests` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_quests`"
			+ " (`player_id`,`quest_id`,`status`,`quest_vars`,`complete_count`,`next_repeat_time`,`reward`,`complete_time`)"
			+ " VALUES (?,?,?,?,?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `player_quests` SET `status` = ?, `quest_vars` = ?, `complete_count` = ?,"
			+ " `next_repeat_time` = ?, `reward` = ?, `complete_time` = ? WHERE `player_id` = ? AND `quest_id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `player_quests` WHERE `player_id` = ? AND `quest_id` = ?";

	public JdbcPlayerQuestRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public QuestStateList load(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot read the quests of a null character.");
		}

		QuestStateList taken = new QuestStateList();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_TAKEN)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					int questId = rows.getInt("quest_id");
					int questVars = rows.getInt("quest_vars");
					int completeCount = rows.getInt("complete_count");
					Timestamp nextRepeatTime = rows.getTimestamp("next_repeat_time");
					int reward = rows.getInt("reward");
					if (rows.wasNull()) {
						reward = 0;
					}
					Timestamp completeTime = rows.getTimestamp("complete_time");
					QuestStatus status = QuestStatus.valueOf(rows.getString("status"));

					QuestState state = new QuestState(questId, status, questVars, completeCount, nextRepeatTime,
							Integer.valueOf(reward), completeTime);
					state.setPersistentState(PersistentState.UPDATED);
					taken.addQuest(questId, state);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the quests of character " + player.getObjectId() + ".", e);
		}

		return taken;
	}

	@Override
	public void save(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot store the quests of a null character.");
		}

		Collection<QuestState> known = player.getQuestStateList().getAllQuestState();
		if (known == null || known.isEmpty()) {
			return;
		}

		// Take a snapshot: the character keeps playing while this runs, and the
		// three passes below must agree on what they are writing.
		List<QuestState> pending = new ArrayList<QuestState>(known);
		int playerId = player.getObjectId();

		inTransaction(connection -> {
			// Abandoned quests go first, so a quest dropped and taken again in the
			// same breath ends up present rather than gone.
			deleteQuests(connection, pending, playerId);
			insertQuests(connection, pending, playerId);
			updateQuests(connection, pending, playerId);
			return null;
		}, "Failed to store the quests of character " + playerId + ".");

		// Mark them saved only now. The DAO did this after its catch, so quests
		// whose write had failed still looked saved and were never retried.
		for (QuestState state : pending) {
			state.setPersistentState(PersistentState.UPDATED);
		}
	}

	private void deleteQuests(Connection connection, List<QuestState> states, int playerId) throws SQLException {
		int queued = 0;
		try (PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			for (QuestState state : states) {
				if (!matches(state, PersistentState.DELETED)) {
					continue;
				}
				statement.setInt(1, playerId);
				statement.setInt(2, state.getQuestId());
				statement.addBatch();
				queued++;
			}
			if (queued > 0) {
				statement.executeBatch();
			}
		}
	}

	private void insertQuests(Connection connection, List<QuestState> states, int playerId) throws SQLException {
		int queued = 0;
		try (PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			for (QuestState state : states) {
				if (!matches(state, PersistentState.NEW)) {
					continue;
				}
				statement.setInt(1, playerId);
				statement.setInt(2, state.getQuestId());
				statement.setString(3, state.getStatus().toString());
				statement.setInt(4, state.getQuestVars().getQuestVars());
				statement.setInt(5, state.getCompleteCount());
				setTimestamp(statement, 6, state.getNextRepeatTime());
				setReward(statement, 7, state.getReward());
				setTimestamp(statement, 8, state.getCompleteTime());
				statement.addBatch();
				queued++;
			}
			if (queued > 0) {
				statement.executeBatch();
			}
		}
	}

	private void updateQuests(Connection connection, List<QuestState> states, int playerId) throws SQLException {
		int queued = 0;
		try (PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			for (QuestState state : states) {
				if (!matches(state, PersistentState.UPDATE_REQUIRED)) {
					continue;
				}
				statement.setString(1, state.getStatus().toString());
				statement.setInt(2, state.getQuestVars().getQuestVars());
				statement.setInt(3, state.getCompleteCount());
				setTimestamp(statement, 4, state.getNextRepeatTime());
				setReward(statement, 5, state.getReward());
				setTimestamp(statement, 6, state.getCompleteTime());
				statement.setInt(7, playerId);
				statement.setInt(8, state.getQuestId());
				statement.addBatch();
				queued++;
			}
			if (queued > 0) {
				statement.executeBatch();
			}
		}
	}

	private static boolean matches(QuestState state, PersistentState wanted) {
		return state != null && state.getPersistentState() == wanted;
	}

	private static void setTimestamp(PreparedStatement statement, int index, Timestamp value) throws SQLException {
		if (value == null) {
			statement.setNull(index, Types.TIMESTAMP);
		} else {
			statement.setTimestamp(index, value);
		}
	}

	private static void setReward(PreparedStatement statement, int index, Integer value) throws SQLException {
		if (value == null) {
			statement.setNull(index, Types.INTEGER);
		} else {
			statement.setInt(index, value.intValue());
		}
	}
}
