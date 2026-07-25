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
 * Holds how much free-to-play time a character has left.
 *
 * @author Oraion
 */
public interface F2pRepository {

	/**
	 * Reads a character's remaining time and hands it over.
	 *
	 * @param player the character entering the world
	 * @throws RepositoryException if it could not be read
	 */
	void load(Player player);

	/**
	 * Records a character's remaining time, whether or not it had a row.
	 *
	 * @param playerId the character to write
	 * @param time     how much time it has left
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(int playerId, int time);

	/**
	 * Forgets a character's remaining time.
	 *
	 * @param playerId the character to write
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(int playerId);
}
