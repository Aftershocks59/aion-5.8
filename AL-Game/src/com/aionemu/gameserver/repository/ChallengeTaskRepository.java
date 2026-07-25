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

import java.util.Map;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.challenge.ChallengeTask;
import com.aionemu.gameserver.model.templates.challenge.ChallengeType;

/**
 * Holds the challenge tasks a character or a legion is working through.
 *
 * @author Oraion
 */
public interface ChallengeTaskRepository {

	/**
	 * Reads every task an owner holds, keyed by task.
	 *
	 * @param ownerId the character or legion
	 * @param type    which of the two it is
	 * @return the tasks, empty when it holds none
	 * @throws RepositoryException if they could not be read
	 */
	Map<Integer, ChallengeTask> findAll(int ownerId, ChallengeType type);

	/**
	 * Writes the quests of a task that changed, leaving the rest alone.
	 *
	 * @param task the task to save
	 * @throws RepositoryException if it could not be written
	 */
	void save(ChallengeTask task);
}
