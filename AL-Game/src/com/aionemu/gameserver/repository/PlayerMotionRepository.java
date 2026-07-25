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
import com.aionemu.gameserver.model.gameobjects.player.motion.Motion;

/**
 * Holds the movement styles a character has unlocked, and which one it wears.
 *
 * @author Oraion
 */
public interface PlayerMotionRepository {

	/**
	 * Reads a character's motions and hands them the list.
	 *
	 * @param player the character entering the world
	 * @throws RepositoryException if they could not be read
	 */
	void load(Player player);

	/**
	 * Records a motion a character has just gained.
	 *
	 * @param playerId the character gaining it
	 * @param motion   the motion and when it expires
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(int playerId, Motion motion);

	/**
	 * Records whether a motion is the one being worn.
	 *
	 * @param playerId the character wearing it
	 * @param motion   the motion whose state changed
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean update(int playerId, Motion motion);

	/**
	 * Takes a motion away from a character.
	 *
	 * @param playerId the character losing it
	 * @param motionId the motion to remove
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(int playerId, int motionId);
}
