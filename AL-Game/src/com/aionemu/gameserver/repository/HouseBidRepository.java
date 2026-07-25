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

import java.sql.Timestamp;
import java.util.Set;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.house.PlayerHouseBid;

/**
 * Holds the bids standing on the houses up for auction.
 *
 * @author Oraion
 */
public interface HouseBidRepository {

	/**
	 * Reads every standing bid.
	 *
	 * @return the bids, empty if nobody has bid
	 * @throws RepositoryException if they could not be read
	 */
	Set<PlayerHouseBid> findAll();

	/**
	 * Records a bid.
	 *
	 * @param playerId the bidder, or zero for the opening price
	 * @param houseId  the house
	 * @param offer    the amount bid
	 * @param at       the moment it was bid
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(int playerId, int houseId, long offer, Timestamp at);

	/**
	 * Clears every bid on a house, once its auction is settled.
	 *
	 * @param houseId the house
	 * @return the number of bids cleared
	 * @throws RepositoryException if they could not be cleared
	 */
	int removeAll(int houseId);
}
