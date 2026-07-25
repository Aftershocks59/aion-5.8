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

import java.util.List;
import java.util.Map;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.ingameshop.IGItem;

/**
 * Holds what the in-game shop has on its shelves.
 *
 * @author Oraion
 */
public interface InGameShopRepository {

	/**
	 * Reads the shelves, keyed by category and kept in the order the database
	 * answered.
	 *
	 * @return the shelves, empty if the shop is bare
	 * @throws RepositoryException if they could not be read
	 */
	Map<Byte, List<IGItem>> findAll();

	/**
	 * Puts an item on a shelf.
	 *
	 * @param item the item
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(IGItem item);

	/**
	 * Takes an item off a shelf.
	 *
	 * @param itemId      the item
	 * @param category    its category, or -1 for every category
	 * @param subCategory its sub-category, or -1 for every sub-category
	 * @param list        its position, or -1 for every position
	 * @return the number of rows removed
	 * @throws RepositoryException if it could not be written
	 */
	int remove(int itemId, byte category, byte subCategory, int list);

	/**
	 * Records how many times an item has sold.
	 *
	 * @param objectId the shelf entry
	 * @param sales    the new count
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setSales(int objectId, int sales);
}
