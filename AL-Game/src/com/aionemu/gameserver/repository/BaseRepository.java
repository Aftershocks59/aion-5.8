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
import com.aionemu.gameserver.model.base.BaseLocation;

/**
 * Holds who owns each base.
 *
 * @author Oraion
 */
public interface BaseRepository {

	/**
	 * Applies the stored owners to the bases the world knows about, and creates a
	 * row for any that has none yet.
	 *
	 * @param locations the bases, keyed by id
	 * @throws RepositoryException if they could not be read or created
	 */
	void load(Map<Integer, BaseLocation> locations);

	/**
	 * Records who owns a base now.
	 *
	 * @param location the base that changed hands
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(BaseLocation location);
}
