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
import com.aionemu.gameserver.model.dorinerk_wardrobe.PlayerWardrobeList;

/**
 * Holds the looks a character has stored in its wardrobe, one per slot.
 *
 * @author Oraion
 */
public interface PlayerWardrobeRepository {

	/**
	 * Reads everything a character has stored.
	 *
	 * @param player the character to read
	 * @return its wardrobe, empty when it has stored nothing
	 * @throws RepositoryException if it could not be read
	 */
	PlayerWardrobeList findAll(Player player);

	/**
	 * Stores a look in a slot, replacing what the slot held.
	 *
	 * @param playerId    the character to write
	 * @param itemId      the item whose look is kept
	 * @param slot        where it goes
	 * @param reskinCount how many times it has been restyled
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(int playerId, int itemId, int slot, int reskinCount);

	/**
	 * Removes a stored look.
	 *
	 * @param playerId the character to write
	 * @param itemId   the item to forget
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(int playerId, int itemId);

	/**
	 * Counts what a character has stored.
	 *
	 * @param playerId the character to read
	 * @return how many looks it holds
	 * @throws RepositoryException if they could not be counted
	 */
	int count(int playerId);

	/**
	 * Reads which item a slot holds.
	 *
	 * @param playerId the character to read
	 * @param slot     the slot to look in
	 * @return the item, or zero when the slot is empty
	 * @throws RepositoryException if it could not be read
	 */
	int findItemInSlot(int playerId, int slot);

	/**
	 * Reads how many times a slot has been restyled.
	 *
	 * @param playerId the character to read
	 * @param slot     the slot to look in
	 * @return the count, or zero when the slot is empty
	 * @throws RepositoryException if it could not be read
	 */
	int findReskinCount(int playerId, int slot);

	/**
	 * Records how many times a slot has been restyled.
	 *
	 * @param playerId    the character to write
	 * @param slot        the slot restyled
	 * @param reskinCount the new count
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setReskinCount(int playerId, int slot, int reskinCount);
}
