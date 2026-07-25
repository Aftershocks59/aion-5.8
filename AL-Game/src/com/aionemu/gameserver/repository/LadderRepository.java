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
 * Holds where each character stands on the arena ladder.
 *
 * @author Oraion
 */
public interface LadderRepository {

	/** The rating a character carries before they have played a match. */
	int STARTING_RATING = 1000;

	/**
	 * Re-numbers every ranked character, best first, and records the rank each of
	 * them held a day ago.
	 *
	 * @throws RepositoryException if the ladder could not be re-numbered
	 */
	void updateRanks();

	/**
	 * Counts one more win for a character.
	 *
	 * @param playerId the character
	 * @throws RepositoryException if it could not be written
	 */
	void addWin(int playerId);

	/**
	 * Counts one more loss for a character.
	 *
	 * @param playerId the character
	 * @throws RepositoryException if it could not be written
	 */
	void addLoss(int playerId);

	/**
	 * Counts one more walkout for a character.
	 *
	 * @param playerId the character
	 * @throws RepositoryException if it could not be written
	 */
	void addLeave(int playerId);

	/**
	 * Moves a character's rating, up or down.
	 *
	 * @param playerId the character
	 * @param delta    how far to move it
	 * @throws RepositoryException if it could not be written
	 */
	void addRating(int playerId, int delta);

	/**
	 * Answers a character's rating.
	 *
	 * @param playerId the character
	 * @return the rating, or {@link #STARTING_RATING} if they have never played
	 * @throws RepositoryException if it could not be read
	 */
	int findRating(int playerId);

	/**
	 * Answers how many matches a character has walked out of.
	 *
	 * @param playerId the character
	 * @return the count, zero if they have never played
	 * @throws RepositoryException if it could not be read
	 */
	int findLeaves(int playerId);

	/**
	 * Sets how many matches a character has walked out of.
	 *
	 * @param playerId the character
	 * @param leaves   the count
	 * @throws RepositoryException if it could not be written
	 */
	void setLeaves(int playerId, int leaves);
}
