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
import com.aionemu.gameserver.model.gameobjects.player.MinionCommonData;
import com.aionemu.gameserver.model.templates.minion.MinionDopingBag;

/**
 * Holds the minions each character keeps.
 *
 * @author Oraion
 */
public interface MinionRepository {

	/**
	 * Reads the minions a character keeps.
	 *
	 * @param playerId the character
	 * @return the minions, empty if they keep none
	 * @throws RepositoryException if they could not be read
	 */
	List<MinionCommonData> findAll(int playerId);

	/**
	 * Answers whether a character already has a minion under a given id.
	 *
	 * @param playerId      the character
	 * @param minionObjectId the id
	 * @return true if the id is taken
	 * @throws RepositoryException if it could not be read
	 */
	boolean isTaken(int playerId, int minionObjectId);

	/**
	 * Gives a character a minion.
	 *
	 * @param minion the minion
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(MinionCommonData minion);

	/**
	 * Takes a minion away.
	 *
	 * @param playerId       the character
	 * @param minionObjectId the minion
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be written
	 */
	boolean remove(int playerId, int minionObjectId);

	/**
	 * Reads a minion's birthday back onto it.
	 *
	 * @param minion the minion
	 * @throws RepositoryException if it could not be read
	 */
	void loadBirthday(MinionCommonData minion);

	/**
	 * Records the name a minion has been given.
	 *
	 * @param minion the minion
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean rename(MinionCommonData minion);

	/**
	 * Records how far a minion has grown.
	 *
	 * @param minion the minion
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setGrowthPoints(MinionCommonData minion);

	/**
	 * Records that a minion has grown into its next form, and starts its growth
	 * over.
	 *
	 * @param minion the minion
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean evolve(MinionCommonData minion);

	/**
	 * Locks or unlocks a minion.
	 *
	 * @param playerId       the character
	 * @param minionObjectId the minion
	 * @param locked         true to lock it
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setLocked(int playerId, int minionObjectId, boolean locked);

	/**
	 * Records what a minion carries in its bag.
	 *
	 * @param playerId       the character
	 * @param minionObjectId the minion
	 * @param bag            the bag
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean saveDopingBag(int playerId, int minionObjectId, MinionDopingBag bag);
}
