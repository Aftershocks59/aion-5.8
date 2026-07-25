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
import com.aionemu.gameserver.model.cp.PlayerCPList;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Holds the creativity points a character has spent, slot by slot.
 *
 * @author Oraion
 */
public interface CreativityPointRepository {

	/**
	 * Reads the points a character has spent.
	 *
	 * @param playerId the character
	 * @return the slots, empty if the character has spent none
	 * @throws RepositoryException if they could not be read
	 */
	PlayerCPList load(int playerId);

	/**
	 * Writes the slots a character has changed and drops the ones they cleared.
	 *
	 * @param player the character
	 * @throws RepositoryException if they could not be written
	 */
	void save(Player player);
}
