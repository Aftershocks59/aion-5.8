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

/**
 * Carries one kind of cooldown between a player and the database.
 * <p>
 * Skills, items, crafting, portals and house objects each keep their own table
 * and their own row shape, but all five behave the same way: everything a player
 * holds is read on the way in and replaced wholesale on the way out.
 *
 * @author Oraion
 */
public interface PlayerCooldownRepository {

	/**
	 * Reads a player's cooldowns and applies them, skipping any that have run out.
	 *
	 * @param player the player entering the world
	 * @throws RepositoryException if they could not be read
	 */
	void load(Player player);

	/**
	 * Replaces everything stored for a player with what they hold now.
	 * <p>
	 * Deletes and re-inserts inside one transaction, so a failure leaves the
	 * player's stored cooldowns as they were rather than empty.
	 *
	 * @param player the player leaving, or being saved
	 * @throws RepositoryException if they could not be written
	 */
	void store(Player player);
}
