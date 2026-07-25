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
import com.aionemu.gameserver.model.gameobjects.player.ranking.Arena6V6Ranking;
import com.aionemu.gameserver.model.gameobjects.player.ranking.ArenaOfTenacityRank;
import com.aionemu.gameserver.model.gameobjects.player.ranking.GoldArenaRank;
import com.aionemu.gameserver.model.gameobjects.player.ranking.TowerOfChallengeRank;
import com.aionemu.gameserver.model.ranking.SeasonRankingResult;

/**
 * Holds where each character stands in the seasonal competitions.
 *
 * @author Oraion
 */
public interface SeasonRankingRepository {

	/**
	 * Reads the leaderboard of one competition, best first.
	 *
	 * @param tableId the competition
	 * @return the leaderboard, empty if nobody has scored
	 * @throws RepositoryException if it could not be read
	 */
	List<SeasonRankingResult> findLeaderboard(int tableId);

	/**
	 * Reads where a character stands in the gold arena, answering a fresh
	 * standing if they have never competed.
	 *
	 * @param playerId the character
	 * @param tableId  the competition
	 * @return their standing, never null
	 * @throws RepositoryException if it could not be read
	 */
	GoldArenaRank loadGoldArena(int playerId, int tableId);

	/**
	 * Reads where a character stands in the arena of tenacity, answering a fresh
	 * standing if they have never competed.
	 *
	 * @param playerId the character
	 * @param tableId  the competition
	 * @return their standing, never null
	 * @throws RepositoryException if it could not be read
	 */
	ArenaOfTenacityRank loadTenacity(int playerId, int tableId);

	/**
	 * Reads where a character stands in the tower of challenge, answering a fresh
	 * standing if they have never competed.
	 *
	 * @param playerId the character
	 * @param tableId  the competition
	 * @return their standing, never null
	 * @throws RepositoryException if it could not be read
	 */
	TowerOfChallengeRank loadTower(int playerId, int tableId);

	/**
	 * Reads where a character stands in the six versus six arena, answering a
	 * fresh standing if they have never competed.
	 *
	 * @param playerId the character
	 * @param tableId  the competition
	 * @return their standing, never null
	 * @throws RepositoryException if it could not be read
	 */
	Arena6V6Ranking load6v6(int playerId, int tableId);

	/**
	 * Writes where a character now stands in the tower of challenge, creating
	 * their row if they have none.
	 *
	 * @param playerId the character
	 * @param rank     their standing
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean saveTower(int playerId, TowerOfChallengeRank rank);
}
