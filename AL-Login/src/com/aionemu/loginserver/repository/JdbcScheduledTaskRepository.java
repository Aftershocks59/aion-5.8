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
package com.aionemu.loginserver.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.taskmanager.handler.TaskFromDBHandler;
import com.aionemu.loginserver.taskmanager.handler.TaskFromDBHandlerHolder;
import com.aionemu.loginserver.taskmanager.trigger.TaskFromDBTrigger;
import com.aionemu.loginserver.taskmanager.trigger.TaskFromDBTriggerHolder;

/**
 * Reads the configured scheduled tasks over JDBC.
 *
 * @author Oraion
 */
public final class JdbcScheduledTaskRepository extends JdbcRepositorySupport implements ScheduledTaskRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcScheduledTaskRepository.class);

	private static final String SELECT_ALL = "SELECT `id`,`trigger_type`,`task_type`,`exec_param`,`trigger_param` "
			+ "FROM `tasks` ORDER BY `id`";

	/** Separates the arguments an operator writes in a single column. */
	private static final String PARAMETER_SEPARATOR = " ";

	public JdbcScheduledTaskRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public List<TaskFromDBTrigger> findAll() {
		List<TaskFromDBTrigger> tasks = new ArrayList<TaskFromDBTrigger>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				TaskFromDBTrigger trigger = read(rows);
				if (trigger != null) {
					tasks.add(trigger);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the scheduled tasks.", e);
		}
		return tasks;
	}

	/**
	 * Builds one task from the row the cursor sits on.
	 *
	 * @return the task, or null when the row names something that cannot be built
	 */
	private static TaskFromDBTrigger read(ResultSet rows) throws SQLException {
		int id = rows.getInt("id");
		try {
			TaskFromDBTrigger trigger = TaskFromDBTriggerHolder.valueOf(rows.getString("trigger_type"))
					.getTriggerClass().getDeclaredConstructor().newInstance();
			TaskFromDBHandler handler = TaskFromDBHandlerHolder.valueOf(rows.getString("task_type"))
					.getTaskClass().getDeclaredConstructor().newInstance();

			handler.setTaskId(id);
			String executionParameters = rows.getString("exec_param");
			if (executionParameters != null) {
				handler.setParams(executionParameters.split(PARAMETER_SEPARATOR));
			}

			trigger.setHandlerToTrigger(handler);
			String triggerParameters = rows.getString("trigger_param");
			if (triggerParameters != null) {
				trigger.setParams(triggerParameters.split(PARAMETER_SEPARATOR));
			}
			return trigger;
		} catch (ReflectiveOperationException | IllegalArgumentException e) {
			// Skip this one rather than the rest: a task naming a handler that no
			// longer exists should not silently cancel every other schedule.
			log.error("Skipping the scheduled task " + id + ": it names a trigger or handler that cannot be built.", e);
			return null;
		}
	}
}
