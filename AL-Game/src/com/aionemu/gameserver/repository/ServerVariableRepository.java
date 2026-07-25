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

/**
 * Holds the numbers the server itself remembers between restarts, such as when
 * the world clock last ran and when the rankings were last refreshed.
 *
 * @author Oraion
 */
public interface ServerVariableRepository {

	/**
	 * Reads one server variable.
	 *
	 * @param key the variable to read
	 * @return its value, or zero when it has never been written
	 * @throws RepositoryException if it could not be read
	 */
	int find(String key);

	/**
	 * Writes one server variable, replacing what it held.
	 *
	 * @param key   the variable to write
	 * @param value what to store
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean set(String key, int value);
}
