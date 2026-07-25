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

import java.util.Map;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.town.Town;

/**
 * Holds how far each town has been developed.
 *
 * @author Oraion
 */
public interface TownRepository {

	/**
	 * Reads every town belonging to one race.
	 *
	 * @param race whose towns to read
	 * @return the towns, keyed by id
	 * @throws RepositoryException if they could not be read
	 */
	Map<Integer, Town> findAll(Race race);

	/**
	 * Writes a town, whether or not it had a row.
	 *
	 * @param town the town to save
	 * @throws RepositoryException if it could not be written
	 */
	void save(Town town);
}
