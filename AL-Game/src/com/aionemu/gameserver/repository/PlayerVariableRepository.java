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

import java.util.Map;

import com.aionemu.commons.database.RepositoryException;

/**
 * Holds the loose key and value pairs a character carries, the ones no column
 * was ever added for.
 *
 * @author Oraion
 */
public interface PlayerVariableRepository {

	/**
	 * Reads every variable a character holds.
	 *
	 * @param playerId the character to read
	 * @return its variables, empty when it has none
	 * @throws RepositoryException if they could not be read
	 */
	Map<String, Object> findAll(int playerId);

	/**
	 * Writes one variable, replacing whatever the key held.
	 *
	 * @param playerId the character to write
	 * @param key      the variable name
	 * @param value    what to store, written as text
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean set(int playerId, String key, Object value);

	/**
	 * Drops one variable.
	 *
	 * @param playerId the character to write
	 * @param key      the variable to drop
	 * @return true if a row was dropped
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(int playerId, String key);
}
