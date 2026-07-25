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
import com.aionemu.gameserver.model.tasks.TaskFromDB;

/**
 * Reads and writes the scheduled tasks, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcScheduledTaskRepository extends JdbcRepositorySupport implements ScheduledTaskRepository {

	private static final String SELECT_ALL = "SELECT `id`,`task_type`,`trigger_type`,`trigger_param`,`exec_param`,`last_activation`"
			+ " FROM `tasks` ORDER BY `id`";

	/** The schema carries no per-task delay; only the fixed-in-time trigger is used. */
	private static final int NO_DELAY = 0;
	private static final String UPDATE_ACTIVATION = "UPDATE `tasks` SET `last_activation` = ? WHERE `id` = ?";

	public JdbcScheduledTaskRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public List<TaskFromDB> findAll() {
		List<TaskFromDB> tasks = new ArrayList<TaskFromDB>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				// The DAO named six columns the table does not have, so every read
				// threw and the server started with no scheduled task at all.
				tasks.add(new TaskFromDB(rows.getInt("id"), rows.getString("task_type"),
						rows.getString("trigger_type"), rows.getTimestamp("last_activation"),
						rows.getString("trigger_param"), NO_DELAY, rows.getString("exec_param")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the scheduled tasks.", e);
		}

		return tasks;
	}

	@Override
	public boolean markActivated(int id, long at) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ACTIVATION)) {
			statement.setTimestamp(1, new Timestamp(at));
			statement.setInt(2, id);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record that task " + id + " ran.", e);
		}
	}
}
