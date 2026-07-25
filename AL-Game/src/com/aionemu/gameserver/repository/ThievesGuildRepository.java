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
import com.aionemu.gameserver.services.events.thievesguildservice.ThievesStatusList;

/**
 * Holds where each character stands in the thieves guild.
 *
 * @author Oraion
 */
public interface ThievesGuildRepository {

	/**
	 * Reads where a character stands.
	 *
	 * @param playerId the character
	 * @return their standing, or null if they have never joined
	 * @throws RepositoryException if it could not be read
	 */
	ThievesStatusList load(int playerId);

	/**
	 * Enrols a character.
	 *
	 * @param standing where they start
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(ThievesStatusList standing);

	/**
	 * Writes where a character now stands.
	 *
	 * @param standing their standing
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(ThievesStatusList standing);
}
