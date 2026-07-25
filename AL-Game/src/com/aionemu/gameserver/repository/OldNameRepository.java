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
 * Remembers the names characters used to carry, so a freed name is not handed
 * straight to somebody else.
 *
 * @author Oraion
 */
public interface OldNameRepository {

	/**
	 * Says whether a name was worn before.
	 * <p>
	 * Answers true when it cannot tell. That is the DAO's choice and it is kept:
	 * refusing a rename that might have been allowed costs a retry, while allowing
	 * one that should have been refused hands out a name somebody may still be
	 * known by.
	 *
	 * @param name the name being claimed
	 * @return true if it was used before, or if the question could not be answered
	 */
	boolean wasUsed(String name);

	/**
	 * Records that a character changed name.
	 *
	 * @param playerId the character that was renamed
	 * @param oldName  the name it gave up
	 * @param newName  the name it took
	 * @throws RepositoryException if it could not be recorded
	 */
	void recordRename(int playerId, String oldName, String newName);
}
