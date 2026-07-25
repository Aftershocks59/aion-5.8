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

import java.util.List;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.tasks.TaskFromDB;

/**
 * Holds the tasks the server runs on a schedule.
 *
 * @author Oraion
 */
public interface ScheduledTaskRepository {

	/**
	 * Reads every scheduled task, in id order.
	 *
	 * @return the tasks, empty if none are configured
	 * @throws RepositoryException if they could not be read
	 */
	List<TaskFromDB> findAll();

	/**
	 * Records that a task has just run.
	 *
	 * @param id the task
	 * @param at the moment it ran
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean markActivated(int id, long at);
}
