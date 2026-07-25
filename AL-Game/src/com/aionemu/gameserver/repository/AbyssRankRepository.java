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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.AbyssRankingResult;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.AbyssRank;
import com.aionemu.gameserver.utils.stats.AbyssRankEnum;

/**
 * Holds where each character and legion stands in the abyss.
 *
 * @author Oraion
 */
public interface AbyssRankRepository {

	/**
	 * Reads where a character stands, answering a fresh standing if they have
	 * never earned one.
	 *
	 * @param playerId the character
	 * @return their standing, never null
	 * @throws RepositoryException if it could not be read
	 */
	AbyssRank load(int playerId);

	/**
	 * Writes where a character now stands, creating their row if they have none.
	 *
	 * @param playerId the character
	 * @param rank     their standing
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(int playerId, AbyssRank rank);

	/**
	 * Reads the ranked characters of a race, best first.
	 *
	 * @param race the race
	 * @return the ranking, empty if nobody qualifies
	 * @throws RepositoryException if it could not be read
	 */
	List<AbyssRankingResult> findRankedPlayers(Race race);

	/**
	 * Reads the ranked legions of a race, best first.
	 *
	 * @param race the race
	 * @return the ranking, empty if no legion qualifies
	 * @throws RepositoryException if it could not be read
	 */
	List<AbyssRankingResult> findRankedLegions(Race race);

	/**
	 * Reads the abyss points of every character of a race above a floor.
	 *
	 * @param race            the race
	 * @param lowerLimit      the floor, exclusive
	 * @param maxOfflineDays  how long a character may have been away, or zero for
	 *                        any length of time
	 * @return the points, keyed by character
	 * @throws RepositoryException if they could not be read
	 */
	Map<Integer, Integer> findAbyssPoints(Race race, int lowerLimit, int maxOfflineDays);

	/**
	 * Reads the glory points of every character of a race above a floor.
	 *
	 * @param race           the race
	 * @param lowerLimit     the floor, exclusive
	 * @param maxOfflineDays how long a character may have been away, or zero for
	 *                       any length of time
	 * @return the points, keyed by character
	 * @throws RepositoryException if they could not be read
	 */
	Map<Integer, Integer> findGloryPoints(Race race, int lowerLimit, int maxOfflineDays);

	/**
	 * Records the grade a character has reached.
	 *
	 * @param playerId the character
	 * @param grade    the grade
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setGrade(int playerId, AbyssRankEnum grade);

	/**
	 * Re-numbers every character and legion of both races, best first.
	 *
	 * @throws RepositoryException if the ranking could not be re-numbered
	 */
	void updateRankPositions();

	/**
	 * Strikes characters from the abyss ranking.
	 *
	 * @param playerIds the characters
	 * @return the number of rows removed
	 * @throws RepositoryException if they could not be removed
	 */
	int removeAll(Collection<Integer> playerIds);
}
