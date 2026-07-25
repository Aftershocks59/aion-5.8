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

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;

/**
 * Holds where a character stands in every quest they have taken.
 *
 * @author Oraion
 */
public interface PlayerQuestRepository {

	/**
	 * Reads the quests a character has taken.
	 *
	 * @param player the character
	 * @return the quest states, empty if the character has taken none
	 * @throws RepositoryException if they could not be read
	 */
	QuestStateList load(Player player);

	/**
	 * Writes the quests a character has started, advanced or abandoned. The quest
	 * states are marked as saved only once the write has gone through.
	 *
	 * @param player the character
	 * @throws RepositoryException if they could not be written
	 */
	void save(Player player);
}
