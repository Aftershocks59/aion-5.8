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
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;

/**
 * Holds what a character looks like, down to the last slider.
 *
 * @author Oraion
 */
public interface PlayerAppearanceRepository {

	/**
	 * Reads a character's appearance.
	 *
	 * @param playerId the character to read
	 * @return its appearance, or a default one when it has no row
	 * @throws RepositoryException if it could not be read
	 */
	PlayerAppearance find(int playerId);

	/**
	 * Writes a character's appearance, replacing what it had.
	 *
	 * @param playerId   the character to write
	 * @param appearance what it looks like
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(int playerId, PlayerAppearance appearance);

	/**
	 * Writes what a character currently looks like.
	 * <p>
	 * The same convenience the DAO offered, so the three callers that hold a
	 * character rather than an id keep reading the way they did.
	 *
	 * @param player the character to write
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	default boolean save(Player player) {
		return save(player.getObjectId(), player.getPlayerAppearance());
	}
}
