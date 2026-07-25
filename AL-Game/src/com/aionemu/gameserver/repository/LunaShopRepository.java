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
import com.aionemu.gameserver.model.gameobjects.player.PlayerLunaShop;

/**
 * Holds what each character has spent and claimed in the Luna shop.
 *
 * @author Oraion
 */
public interface LunaShopRepository {

	/**
	 * Applies the stored Luna shop state to a character.
	 *
	 * @param player the character
	 * @throws RepositoryException if it could not be read
	 */
	void load(Player player);

	/**
	 * Gives a character a Luna shop record.
	 *
	 * @param playerId the character
	 * @param shop     the state it starts in
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(int playerId, PlayerLunaShop shop);

	/**
	 * Writes a character's Luna shop state back, if it has changed. It is marked
	 * as saved only once the write has gone through.
	 *
	 * @param player the character
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(Player player);

	/**
	 * Writes a Luna shop state for a character who may be offline.
	 *
	 * @param playerId the character
	 * @param shop     the state to write
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean saveShop(int playerId, PlayerLunaShop shop);
}
