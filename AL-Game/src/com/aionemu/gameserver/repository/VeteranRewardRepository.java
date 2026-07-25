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

import java.util.Set;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.veteranrewards.VeteranRewards;

/**
 * Holds the rewards queued for long-standing accounts.
 *
 * @author Oraion
 */
public interface VeteranRewardRepository {

	/**
	 * Reads every reward still waiting to be handed out.
	 *
	 * @return the rewards, empty when there are none
	 * @throws RepositoryException if they could not be read
	 */
	Set<VeteranRewards> findAll();

	/**
	 * Removes a reward once it has been handed out.
	 *
	 * @param rewardId which reward
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(int rewardId);
}
