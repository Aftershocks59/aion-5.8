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

import com.aionemu.commons.database.RepositoryException;

/**
 * Holds who is married to whom.
 *
 * @author Oraion
 */
public interface WeddingRepository {

	/** Answers the id used when a character has no partner. */
	int NO_PARTNER = 0;

	/**
	 * Finds who a character is married to.
	 *
	 * @param playerId the character to look up
	 * @return the partner, or {@link #NO_PARTNER} when it has none
	 * @throws RepositoryException if it could not be read
	 */
	int findPartner(int playerId);

	/**
	 * Records a marriage.
	 *
	 * @param firstId  one partner
	 * @param secondId the other
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean marry(int firstId, int secondId);

	/**
	 * Ends a marriage, whichever way round it was recorded.
	 *
	 * @param firstId  one partner
	 * @param secondId the other
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean divorce(int firstId, int secondId);
}
