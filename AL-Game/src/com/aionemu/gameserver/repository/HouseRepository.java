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

import java.util.Collection;
import java.util.Map;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.house.House;
import com.aionemu.gameserver.model.templates.housing.HousingLand;

/**
 * Holds the houses and studios the world has built.
 *
 * @author Oraion
 */
public interface HouseRepository {

	/**
	 * Answers every house id already in use, so the id factory can reserve them.
	 *
	 * @return the ids, empty if nothing is built
	 * @throws RepositoryException if they could not be read
	 */
	int[] findUsedIds();


	/**
	 * Reads the houses, or the studios, standing on the given land.
	 *
	 * @param lands   the land the world knows
	 * @param studios true to read the studios rather than the houses
	 * @return the houses, keyed by address, or by owner for studios
	 * @throws RepositoryException if they could not be read
	 */
	Map<Integer, House> load(Collection<HousingLand> lands, boolean studios);

	/**
	 * Writes a house, creating it if it is new.
	 *
	 * @param house the house
	 * @throws RepositoryException if it could not be written
	 */
	void save(House house);

	/**
	 * Pulls down whatever a character owns.
	 *
	 * @param playerId the character
	 * @return the number of houses pulled down
	 * @throws RepositoryException if it could not be written
	 */
	int removeFor(int playerId);
}
