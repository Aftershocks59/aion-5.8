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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.items.IdianStone;
import com.aionemu.gameserver.model.items.ManaStone;

/**
 * Holds the stones socketed into weapons and armour.
 *
 * @author Oraion
 */
public interface ItemStoneRepository {

	/**
	 * Sockets the stored stones back into the items that carry them. Items that
	 * cannot hold a stone are skipped.
	 *
	 * @param items the items
	 * @throws RepositoryException if the stones could not be read
	 */
	void load(Collection<Item> items);

	/**
	 * Writes every stone the given items carry.
	 *
	 * @param items the items
	 * @throws RepositoryException if the stones could not be written
	 */
	void save(List<Item> items);

	/**
	 * Writes mana stones.
	 *
	 * @param manaStones the stones
	 * @throws RepositoryException if they could not be written
	 */
	void saveManaStones(Set<ManaStone> manaStones);

	/**
	 * Writes fusion stones.
	 *
	 * @param fusionStones the stones
	 * @throws RepositoryException if they could not be written
	 */
	void saveFusionStones(Set<ManaStone> fusionStones);

	/**
	 * Writes one idian stone.
	 *
	 * @param idianStone the stone
	 * @throws RepositoryException if it could not be written
	 */
	void saveIdianStone(IdianStone idianStone);
}
