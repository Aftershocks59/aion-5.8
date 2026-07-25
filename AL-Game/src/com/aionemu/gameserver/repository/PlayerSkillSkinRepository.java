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
import com.aionemu.gameserver.model.skinskill.SkillSkin;
import com.aionemu.gameserver.model.skinskill.SkillSkinList;

/**
 * Holds the skill appearances a character owns, and which are switched on.
 *
 * @author Oraion
 */
public interface PlayerSkillSkinRepository {

	/**
	 * Reads every skin a character owns.
	 *
	 * @param playerId the character to read
	 * @return its skins, empty when it owns none
	 * @throws RepositoryException if they could not be read
	 */
	SkillSkinList findAll(int playerId);

	/**
	 * Records a skin a character has just gained.
	 *
	 * @param playerId the character gaining it
	 * @param skin     the skin and when it expires
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(int playerId, SkillSkin skin);

	/**
	 * Takes a skin away from a character.
	 *
	 * @param playerId the character losing it
	 * @param skinId   the skin to remove
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(int playerId, int skinId);

	/**
	 * Switches a skin on or off.
	 *
	 * @param playerId the character wearing it
	 * @param skinId   the skin to switch
	 * @param active   whether it should be worn
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setActive(int playerId, int skinId, boolean active);
}
