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
import com.aionemu.gameserver.model.skill.linked_skill.PlayerEquippedStigmaList;

/**
 * Holds the stigmas a character wears.
 *
 * @author Oraion
 */
public interface EquippedStigmaRepository {

	/**
	 * Reads the stigmas a character wears.
	 *
	 * @param playerId the character
	 * @return the list, empty if the character wears none
	 * @throws RepositoryException if they could not be read
	 */
	PlayerEquippedStigmaList load(int playerId);

	/**
	 * Writes the stigmas a character has put on, taken off or changed. The entries
	 * are marked as saved only once the write has gone through.
	 *
	 * @param player the character
	 * @throws RepositoryException if they could not be written
	 */
	void save(Player player);
}
