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

/**
 * Holds where a character stands with each of the npc factions.
 *
 * @author Oraion
 */
public interface PlayerNpcFactionRepository {

	/**
	 * Reads a character's standings and hands them the list.
	 *
	 * @param player the character entering the world
	 * @throws RepositoryException if they could not be read
	 */
	void load(Player player);

	/**
	 * Writes the standings that changed, leaving the rest alone.
	 *
	 * @param player the character to save
	 * @throws RepositoryException if they could not be written
	 */
	void store(Player player);
}
