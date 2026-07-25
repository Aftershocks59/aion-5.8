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
import com.aionemu.gameserver.model.gameobjects.player.PetCommonData;
import com.aionemu.gameserver.model.templates.pet.PetDopingBag;

/**
 * Holds the pets each character keeps.
 *
 * @author Oraion
 */
public interface PetRepository {

	/**
	 * Reads the pets a character keeps.
	 *
	 * @param playerId the character
	 * @return the pets, empty if they keep none
	 * @throws RepositoryException if they could not be read
	 */
	List<PetCommonData> findAll(int playerId);

	/**
	 * Gives a character a pet.
	 *
	 * @param pet the pet
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(PetCommonData pet);

	/**
	 * Takes a pet away.
	 *
	 * @param playerId the character
	 * @param petId    the pet
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be written
	 */
	boolean remove(int playerId, int petId);

	/**
	 * Records the name a pet has been given.
	 *
	 * @param pet the pet
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean rename(PetCommonData pet);

	/**
	 * Records when a pet may next be summoned.
	 *
	 * @param playerId the character
	 * @param petId    the pet
	 * @param reuseAt  the moment
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setReuseTime(int playerId, int petId, long reuseAt);

	/**
	 * Records how well fed a pet is.
	 *
	 * @param playerId     the character
	 * @param petId        the pet
	 * @param hungryLevel  how hungry it is
	 * @param feedProgress how far through its meal it is
	 * @param reuseAt      when it may next be fed
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean saveFeeding(int playerId, int petId, int hungryLevel, int feedProgress, long reuseAt);

	/**
	 * Records a pet's mood.
	 *
	 * @param pet the pet
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean saveMood(PetCommonData pet);

	/**
	 * Records what a pet carries in its bag.
	 *
	 * @param playerId the character
	 * @param petId    the pet
	 * @param bag      the bag
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean saveDopingBag(int playerId, int petId, PetDopingBag bag);
}
