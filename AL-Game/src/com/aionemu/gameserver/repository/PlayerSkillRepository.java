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

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.skill.PlayerSkillList;

/**
 * Holds the skills a character knows.
 *
 * @author Oraion
 */
public interface PlayerSkillRepository {

	/** Answers when a skill skin carries no expiry. */
	int NO_EXPIRY = 0;

	/**
	 * Reads the skills a character knows.
	 *
	 * @param playerId the character
	 * @return the list, empty if the character knows none
	 * @throws RepositoryException if they could not be read
	 */
	PlayerSkillList load(int playerId);

	/**
	 * Writes the skills a character has learned, raised or forgotten. The entries
	 * are marked as saved only once the write has gone through.
	 *
	 * @param player the character
	 * @throws RepositoryException if they could not be written
	 */
	void save(Player player);

	/**
	 * Answers when a skill's skin was put on.
	 *
	 * @param playerId the character
	 * @param skillId  the skill
	 * @return the moment, or null if the character does not know the skill or its
	 *         skin was never put on
	 * @throws RepositoryException if it could not be read
	 */
	Timestamp findSkinActivatedAt(int playerId, int skillId);

	/**
	 * Answers how long a skill's skin lasts.
	 *
	 * @param playerId the character
	 * @param skillId  the skill
	 * @return the number of minutes, or {@link #NO_EXPIRY} if the character does
	 *         not know the skill
	 * @throws RepositoryException if it could not be read
	 */
	int findSkinExpiry(int playerId, int skillId);
}
