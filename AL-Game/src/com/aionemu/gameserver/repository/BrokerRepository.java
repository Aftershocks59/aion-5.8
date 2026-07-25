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

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.BrokerItem;

/**
 * Holds what is up for sale at the broker.
 *
 * @author Oraion
 */
public interface BrokerRepository {

	/**
	 * Reads everything at the broker, with the unsold listings carrying the item
	 * they are selling.
	 *
	 * @return the listings, empty if nothing is for sale
	 * @throws RepositoryException if they could not be read
	 */
	List<BrokerItem> findAll();

	/**
	 * Answers whether an item is still on sale and unsold.
	 *
	 * @param itemUniqueId the item
	 * @return true if it is still there to buy
	 * @throws RepositoryException if it could not be read
	 */
	boolean isStillOnSale(int itemUniqueId);

	/**
	 * Writes a listing, according to what has happened to it. A listing is marked
	 * as saved only once the write has gone through.
	 *
	 * @param listing the listing
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(BrokerItem listing);
}
