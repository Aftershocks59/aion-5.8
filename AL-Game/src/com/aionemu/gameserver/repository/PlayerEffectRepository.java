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
 * Carries the effects a character is under across a logout.
 *
 * @author Oraion
 */
public interface PlayerEffectRepository {

	/**
	 * Restores the effects a character was under, and tells the client.
	 *
	 * @param player the character entering the world
	 * @throws RepositoryException if they could not be read
	 */
	void load(Player player);

	/**
	 * Replaces everything stored with the effects worth keeping.
	 * <p>
	 * Deletes and re-inserts inside one transaction, so a failure leaves the
	 * character's stored effects as they were rather than empty.
	 *
	 * @param player the character leaving
	 * @throws RepositoryException if they could not be written
	 */
	void store(Player player);
}
