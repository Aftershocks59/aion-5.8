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

import java.util.List;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.taskmanager.trigger.TaskFromDBTrigger;

/**
 * Holds the scheduled tasks the operator configured in the database.
 *
 * @author Oraion
 */
public interface ScheduledTaskRepository {

	/**
	 * Reads every configured task, in the order they were declared.
	 * <p>
	 * A row naming a trigger or a handler that cannot be built is reported and
	 * skipped: one bad row should cost its own task, not every other one.
	 *
	 * @return the tasks, empty when none are configured
	 * @throws RepositoryException if they could not be read
	 */
	List<TaskFromDBTrigger> findAll();
}
