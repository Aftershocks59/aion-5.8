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
import com.aionemu.gameserver.model.gameobjects.player.title.Title;
import com.aionemu.gameserver.model.gameobjects.player.title.TitleList;

/**
 * Holds the titles a character has earned, and when the borrowed ones run out.
 *
 * @author Oraion
 */
public interface PlayerTitleRepository {

	/**
	 * Reads every title a character holds.
	 *
	 * @param playerId the character to read
	 * @return its titles, empty when it has none
	 * @throws RepositoryException if they could not be read
	 */
	TitleList findAll(int playerId);

	/**
	 * Grants a title to a character.
	 *
	 * @param playerId the character gaining it
	 * @param title    the title and when it expires
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(int playerId, Title title);

	/**
	 * Takes a title away from a character.
	 *
	 * @param playerId the character losing it
	 * @param titleId  the title to remove
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(int playerId, int titleId);
}
