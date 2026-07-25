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
import com.aionemu.gameserver.model.templates.rewards.RewardEntryItem;

/**
 * Holds the items the web shop owes a character.
 *
 * @author Oraion
 */
public interface WebRewardRepository {

	/**
	 * Reads what a character has bought and not yet been handed.
	 *
	 * @param playerId the character
	 * @return the items, empty if the character is owed nothing
	 * @throws RepositoryException if they could not be read
	 */
	List<RewardEntryItem> findUnclaimed(int playerId);

	/**
	 * Records that one item has been handed over, and when.
	 *
	 * @param rewardId the row
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean markClaimed(int rewardId);

	/**
	 * Records that several items have been handed over, and when.
	 *
	 * @param rewardIds the rows
	 * @throws RepositoryException if they could not be written
	 */
	void markClaimed(List<Integer> rewardIds);

	/**
	 * Puts one item back on the pile, after handing it over failed. The moment it
	 * was handed over is left as it stands, as the DAO did.
	 *
	 * @param rewardId the row
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean markUnclaimed(int rewardId);
}
