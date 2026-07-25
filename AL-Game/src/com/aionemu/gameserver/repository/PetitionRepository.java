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
import com.aionemu.gameserver.model.Petition;

/**
 * Holds the petitions players have raised.
 *
 * @author Oraion
 */
public interface PetitionRepository {

	/**
	 * Answers the id the next petition should carry.
	 *
	 * @return one past the highest id in use
	 * @throws RepositoryException if it could not be read
	 */
	int nextId();

	/**
	 * Reads the petitions still waiting on an answer, oldest first.
	 *
	 * @return the petitions, empty if none are waiting
	 * @throws RepositoryException if they could not be read
	 */
	Set<Petition> findOpen();

	/**
	 * Reads one petition.
	 *
	 * @param petitionId the petition
	 * @return the petition, or null if there is no such petition
	 * @throws RepositoryException if it could not be read
	 */
	Petition findById(int petitionId);

	/**
	 * Records a new petition.
	 *
	 * @param petition the petition
	 * @param raisedAt the moment it was raised, in seconds
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(Petition petition, long raisedAt);

	/**
	 * Withdraws whatever a character is still waiting on an answer for.
	 *
	 * @param playerId the character
	 * @return the number of petitions withdrawn
	 * @throws RepositoryException if they could not be withdrawn
	 */
	int removeOpenFor(int playerId);

	/**
	 * Records that a petition has been answered.
	 *
	 * @param petitionId the petition
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean markReplied(int petitionId);
}
