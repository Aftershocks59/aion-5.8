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
import com.aionemu.gameserver.model.house.HouseRegistry;

/**
 * Holds the furniture and decoration a character has placed in their house.
 *
 * @author Oraion
 */
public interface HouseRegistryRepository {

	/**
	 * Answers every object id already in use, so the id factory can reserve them.
	 *
	 * @return the ids, empty if nothing is placed
	 * @throws RepositoryException if they could not be read
	 */
	int[] findUsedIds();

	/**
	 * Fills a character's house registry from what they have placed.
	 *
	 * @param playerId the character
	 * @throws RepositoryException if it could not be read
	 */
	void load(int playerId);

	/**
	 * Writes what a character has placed, moved or taken away. The entries are
	 * marked as saved only once the write has gone through.
	 *
	 * @param registry the registry
	 * @param playerId the character
	 * @throws RepositoryException if it could not be written
	 */
	void save(HouseRegistry registry, int playerId);

	/**
	 * Takes everything but the decoration off the floor, leaving it in storage.
	 *
	 * @param playerId the character
	 * @return the number of objects taken up
	 * @throws RepositoryException if it could not be written
	 */
	int reset(int playerId);
}
