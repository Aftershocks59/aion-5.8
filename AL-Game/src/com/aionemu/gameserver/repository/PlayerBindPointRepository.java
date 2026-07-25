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
 * Holds where a character comes back to life.
 *
 * @author Oraion
 */
public interface PlayerBindPointRepository {

	/**
	 * Reads a character's bind point and hands it over.
	 *
	 * @param player the character entering the world
	 * @throws RepositoryException if it could not be read
	 */
	void load(Player player);

	/**
	 * Writes a character's bind point when it has moved.
	 *
	 * @param player the character to save
	 * @return true if a row was written, false when nothing had changed
	 * @throws RepositoryException if it could not be written
	 */
	boolean store(Player player);
}
