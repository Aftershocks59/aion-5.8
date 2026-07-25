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
import com.aionemu.gameserver.model.gameobjects.player.MacroList;

/**
 * Holds the macros a character has written, each in its own slot.
 *
 * @author Oraion
 */
public interface PlayerMacroRepository {

	/**
	 * Reads every macro a character holds.
	 *
	 * @param playerId the character to read
	 * @return its macros, empty when it has none
	 * @throws RepositoryException if they could not be read
	 */
	MacroList findAll(int playerId);

	/**
	 * Stores a macro in a slot.
	 *
	 * @param playerId the character to write
	 * @param slot     where it goes
	 * @param macro    the macro itself
	 * @throws RepositoryException if it could not be written
	 */
	void add(int playerId, int slot, String macro);

	/**
	 * Replaces the macro in a slot.
	 *
	 * @param playerId the character to write
	 * @param slot     which one to replace
	 * @param macro    the macro itself
	 * @throws RepositoryException if it could not be written
	 */
	void update(int playerId, int slot, String macro);

	/**
	 * Empties a slot.
	 *
	 * @param playerId the character to write
	 * @param slot     which one to empty
	 * @throws RepositoryException if it could not be removed
	 */
	void remove(int playerId, int slot);
}
