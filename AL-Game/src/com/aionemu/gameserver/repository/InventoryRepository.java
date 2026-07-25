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
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Equipment;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.Storage;
import com.aionemu.gameserver.model.items.storage.StorageType;

/**
 * Holds everything a character, an account or a legion has stored away.
 *
 * @author Oraion
 */
public interface InventoryRepository {

	/**
	 * Answers every item id already in use, so the id factory can reserve them.
	 *
	 * @return the ids, empty if nothing is stored
	 * @throws RepositoryException if they could not be read
	 */
	int[] findUsedIds();

	/**
	 * Reads one storage, ready to be handed to its owner.
	 *
	 * @param playerId    the character, whose account is used for the account
	 *                    warehouse
	 * @param storageType which storage
	 * @return the storage, empty if nothing is in it
	 * @throws RepositoryException if it could not be read
	 */
	Storage loadStorage(int playerId, StorageType storageType);

	/**
	 * Reads the items in one storage, without building the storage itself.
	 *
	 * @param playerId    the character, whose account is used for the account
	 *                    warehouse
	 * @param storageType which storage
	 * @return the items, empty if nothing is in it
	 * @throws RepositoryException if they could not be read
	 */
	List<Item> loadStorageItems(int playerId, StorageType storageType);

	/**
	 * Reads what a character is wearing, ready to be handed to them.
	 *
	 * @param player the character
	 * @return their equipment, empty if they wear nothing
	 * @throws RepositoryException if it could not be read
	 */
	Equipment loadEquipment(Player player);

	/**
	 * Reads the items a character is wearing.
	 *
	 * @param playerId the character
	 * @return the items, empty if they wear nothing
	 * @throws RepositoryException if they could not be read
	 */
	List<Item> loadEquipment(int playerId);

	/**
	 * Writes everything a character has changed since their last save.
	 *
	 * @param player the character
	 * @return true if every pass wrote what it was given
	 * @throws RepositoryException if the write failed
	 */
	boolean save(Player player);

	/**
	 * Writes one item belonging to a character.
	 *
	 * @param item   the item
	 * @param player the character
	 * @return true if the write went through
	 * @throws RepositoryException if it failed
	 */
	boolean save(Item item, Player player);

	/**
	 * Writes one item belonging to a character who may be offline.
	 *
	 * @param item     the item
	 * @param playerId the character
	 * @return true if the write went through
	 * @throws RepositoryException if it failed
	 */
	boolean save(Item item, int playerId);

	/**
	 * Writes items belonging to a character who may be offline, looking up the
	 * account and the legion only when an item calls for one.
	 *
	 * @param items    the items
	 * @param playerId the character
	 * @return true if every pass wrote what it was given
	 * @throws RepositoryException if the write failed
	 */
	boolean save(List<Item> items, int playerId);

	/**
	 * Writes items, each row going to whichever owner its storage calls for.
	 *
	 * @param items     the items
	 * @param playerId  the character, or null for legion-only writes
	 * @param accountId the account, or null if no item sits in the account
	 *                  warehouse
	 * @param legionId  the legion, or null if no item sits in the legion warehouse
	 * @return true if every pass wrote what it was given
	 * @throws RepositoryException if the write failed
	 */
	boolean save(List<Item> items, Integer playerId, Integer accountId, Integer legionId);

	/**
	 * Clears everything a character owns, bar the account warehouse.
	 *
	 * @param playerId the character
	 * @return the number of items cleared
	 * @throws RepositoryException if they could not be cleared
	 */
	int removeFor(int playerId);

	/**
	 * Clears an account warehouse.
	 *
	 * @param accountId the account
	 * @return the number of items cleared
	 * @throws RepositoryException if they could not be cleared
	 */
	int removeAccountWarehouse(int accountId);
}
