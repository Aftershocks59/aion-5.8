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
import com.aionemu.gameserver.model.gameobjects.player.PlayerSweep;

/**
 * Holds where a character stands on the Shugo Sweep board.
 *
 * @author Oraion
 */
public interface ShugoSweepRepository {

	/**
	 * Applies the stored board to a character.
	 *
	 * @param player the character
	 * @throws RepositoryException if it could not be read
	 */
	void load(Player player);

	/**
	 * Gives a character a board to play on.
	 *
	 * @param playerId the character
	 * @param board    where they start
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(int playerId, PlayerSweep board);

	/**
	 * Writes a character's board back, if it has moved. The board is marked as
	 * saved only once the write has gone through.
	 *
	 * @param player the character
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(Player player);

	/**
	 * Writes a board for a character who may be offline.
	 *
	 * @param playerId the character
	 * @param board    where they now stand
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean saveBoard(int playerId, PlayerSweep board);
}
