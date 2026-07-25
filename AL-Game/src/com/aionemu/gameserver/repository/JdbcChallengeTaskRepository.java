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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.challenge.ChallengeQuest;
import com.aionemu.gameserver.model.challenge.ChallengeTask;
import com.aionemu.gameserver.model.templates.challenge.ChallengeType;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.templates.challenge.ChallengeQuestTemplate;

/**
 * Reads and writes the challenge tasks over JDBC.
 *
 * @author Oraion
 */
public final class JdbcChallengeTaskRepository extends JdbcRepositorySupport implements ChallengeTaskRepository {

	private static final String SELECT_ALL = "SELECT `task_id`,`quest_id`,`complete_count`,`complete_time` "
			+ "FROM `challenge_tasks` WHERE `owner_id` = ? AND `owner_type` = ?";

	/**
	 * Writes a quest whether the owner had it or not.
	 * <p>
	 * The DAO kept an insert and an update apart and chose between them from an
	 * in-memory flag, on a fresh connection each time.
	 */
	private static final String UPSERT_ONE = "INSERT INTO `challenge_tasks` "
			+ "(`task_id`,`quest_id`,`owner_id`,`owner_type`,`complete_count`,`complete_time`) VALUES (?,?,?,?,?,?) "
			+ "ON DUPLICATE KEY UPDATE `complete_count` = VALUES(`complete_count`), "
			+ "`complete_time` = VALUES(`complete_time`)";

	public JdbcChallengeTaskRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Map<Integer, ChallengeTask> findAll(int ownerId, ChallengeType type) {
		Map<Integer, ChallengeTask> tasks = new ConcurrentHashMap<Integer, ChallengeTask>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, ownerId);
			statement.setString(2, type.toString());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					collect(tasks, ownerId, rows);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the challenge tasks of " + type + " " + ownerId + ".", e);
		}
		return tasks;
	}

	@Override
	public void save(ChallengeTask task) {
		if (task == null) {
			throw new IllegalArgumentException("Cannot store a null challenge task.");
		}

		// One connection and one transaction for every quest that changed, where the
		// DAO took a connection per quest.
		inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(UPSERT_ONE)) {
				int queued = 0;
				for (ChallengeQuest quest : task.getQuests().values()) {
					if (quest.getPersistentState() != PersistentState.NEW
							&& quest.getPersistentState() != PersistentState.UPDATE_REQUIRED) {
						continue;
					}
					statement.setInt(1, task.getTaskId());
					statement.setInt(2, quest.getQuestId());
					statement.setInt(3, task.getOwnerId());
					statement.setString(4, task.getTemplate().getType().toString());
					statement.setInt(5, quest.getCompleteCount());
					statement.setTimestamp(6, task.getCompleteTime());
					statement.addBatch();
					queued++;
				}
				if (queued > 0) {
					statement.executeBatch();
				}
			}
			return null;
		}, "Failed to write the challenge task " + task.getTaskId() + ".");

		// Only once the whole batch has landed. The DAO marked each quest saved as it
		// went, so a failure partway left some claiming to be stored when they were
		// not.
		for (ChallengeQuest quest : task.getQuests().values()) {
			quest.setPersistentState(PersistentState.UPDATED);
		}
	}

	/** Adds one row to the task it belongs to, creating the task on first sight. */
	private static void collect(Map<Integer, ChallengeTask> tasks, int ownerId, ResultSet rows)
			throws SQLException {
		int taskId = rows.getInt("task_id");
		int questId = rows.getInt("quest_id");
		Timestamp completedAt = rows.getTimestamp("complete_time");

		ChallengeQuestTemplate template = DataManager.CHALLENGE_DATA.getQuestByQuestId(questId);
		ChallengeQuest quest = new ChallengeQuest(template, rows.getInt("complete_count"));
		quest.setPersistentState(PersistentState.UPDATED);

		ChallengeTask task = tasks.get(Integer.valueOf(taskId));
		if (task == null) {
			Map<Integer, ChallengeQuest> quests = new HashMap<Integer, ChallengeQuest>(2);
			quests.put(Integer.valueOf(quest.getQuestId()), quest);
			tasks.put(Integer.valueOf(taskId), new ChallengeTask(taskId, ownerId, quests, completedAt));
		} else {
			task.getQuests().put(Integer.valueOf(questId), quest);
		}
	}
}
