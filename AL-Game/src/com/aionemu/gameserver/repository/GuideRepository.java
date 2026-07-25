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
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.guide.Guide;

/**
 * Holds the guide notes a character has been sent.
 *
 * @author Oraion
 */
public interface GuideRepository {

	/**
	 * Reads every note a character holds.
	 *
	 * @param playerId the character to read
	 * @return its notes, empty when it holds none
	 * @throws RepositoryException if they could not be read
	 */
	List<Guide> findAll(int playerId);

	/**
	 * Reads one note.
	 *
	 * @param playerId the character holding it
	 * @param guideId  which note
	 * @return the note, or null when the character has no such one
	 * @throws RepositoryException if it could not be read
	 */
	Guide find(int playerId, int guideId);

	/**
	 * Sends a note to a character.
	 *
	 * @param guideId the id reserved for it
	 * @param player  the character receiving it
	 * @param title   what it says
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(int guideId, Player player, String title);

	/**
	 * Removes a note.
	 *
	 * @param guideId the note to remove
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(int guideId);

	/**
	 * Lists every id already taken, so the id factory does not hand one out twice.
	 *
	 * @return the ids in use, empty when there are none
	 * @throws RepositoryException if they could not be read
	 */
	int[] findUsedIds();
}
