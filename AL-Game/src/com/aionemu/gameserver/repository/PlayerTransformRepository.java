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
 * Holds the shape a character is transformed into across sessions.
 *
 * @author Oraion
 */
public interface PlayerTransformRepository {

	/**
	 * Applies the stored transformation to a character.
	 *
	 * @param player the character logging in
	 * @throws RepositoryException if it could not be read
	 */
	void load(Player player);

	/**
	 * Records the shape a character has taken.
	 *
	 * @param playerId the character
	 * @param panelId  the transformation panel
	 * @param itemId   the item that granted it
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(int playerId, int panelId, int itemId);

	/**
	 * Forgets the shape a character had taken.
	 *
	 * @param playerId the character
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be written
	 */
	boolean remove(int playerId);
}
