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
 * Holds how many of each event item a character has taken today.
 *
 * @author Oraion
 */
public interface EventItemRepository {

	/**
	 * Reads a character's daily counts and applies them.
	 *
	 * @param player the character entering the world
	 * @throws RepositoryException if they could not be read
	 */
	void load(Player player);

	/**
	 * Replaces a character's daily counts with what it holds now.
	 * <p>
	 * Deletes and re-inserts inside one transaction, so a failure leaves the
	 * stored counts as they were rather than empty.
	 *
	 * @param player the character to save
	 * @throws RepositoryException if they could not be written
	 */
	void store(Player player);

	/**
	 * Forgets one event item for everybody, which is what ending an event does.
	 *
	 * @param itemId the item to forget
	 * @return how many rows were dropped
	 * @throws RepositoryException if they could not be removed
	 */
	int removeItem(int itemId);
}
