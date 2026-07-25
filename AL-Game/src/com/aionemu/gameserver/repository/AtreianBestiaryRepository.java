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
import com.aionemu.gameserver.model.atreian_bestiary.PlayerABList;

/**
 * Holds what each character has hunted for the Atreian bestiary.
 *
 * @author Oraion
 */
public interface AtreianBestiaryRepository {

	/** Answers when a character has no entry for the beast that was asked for. */
	int NOT_HUNTED = 0;

	/**
	 * Reads what a character has hunted.
	 *
	 * @param playerId the character
	 * @return the entries, empty if the character has hunted nothing
	 * @throws RepositoryException if they could not be read
	 */
	PlayerABList load(int playerId);

	/**
	 * Writes one bestiary entry.
	 *
	 * @param playerId  the character
	 * @param beastId   the beast
	 * @param killCount how many have been killed
	 * @param level     the entry's level
	 * @param rewarded  whether the reward has been claimed
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(int playerId, int beastId, int killCount, int level, int rewarded);

	/**
	 * Clears one bestiary entry.
	 *
	 * @param playerId the character
	 * @param beastId  the beast
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be written
	 */
	boolean remove(int playerId, int beastId);

	/**
	 * Answers how many of a beast a character has killed.
	 *
	 * @param playerId the character
	 * @param beastId  the beast
	 * @return the count, or {@link #NOT_HUNTED} if there is no entry
	 * @throws RepositoryException if it could not be read
	 */
	int findKillCount(int playerId, int beastId);

	/**
	 * Answers what level a character's bestiary entry has reached.
	 *
	 * @param playerId the character
	 * @param beastId  the beast
	 * @return the level, or {@link #NOT_HUNTED} if there is no entry
	 * @throws RepositoryException if it could not be read
	 */
	int findLevel(int playerId, int beastId);

	/**
	 * Answers whether a character has claimed a bestiary reward.
	 *
	 * @param playerId the character
	 * @param beastId  the beast
	 * @return the stored flag, or {@link #NOT_HUNTED} if there is no entry
	 * @throws RepositoryException if it could not be read
	 */
	int findClaimedReward(int playerId, int beastId);
}
