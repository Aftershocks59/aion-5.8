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
import com.aionemu.gameserver.model.gameobjects.player.PlayerScripts;

/**
 * Holds the decorating scripts saved against a house.
 *
 * @author Oraion
 */
public interface HouseScriptRepository {

	/**
	 * Reads the scripts saved against a house.
	 *
	 * @param houseId the house
	 * @return the scripts, empty if the house has none
	 * @throws RepositoryException if they could not be read
	 */
	PlayerScripts load(int houseId);

	/**
	 * Saves a new script in a slot.
	 *
	 * @param houseId  the house
	 * @param position the slot
	 * @param script   the script, which may be null
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(int houseId, int position, String script);

	/**
	 * Replaces the script in a slot.
	 *
	 * @param houseId  the house
	 * @param position the slot
	 * @param script   the script, which may be null
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean update(int houseId, int position, String script);

	/**
	 * Clears a slot.
	 *
	 * @param houseId  the house
	 * @param position the slot
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be written
	 */
	boolean remove(int houseId, int position);
}
