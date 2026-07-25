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
package com.aionemu.loginserver.repository;

import com.aionemu.commons.database.RepositoryException;

/**
 * Records how long an account has been online.
 *
 * @author Oraion
 */
public interface AccountPlayTimeRepository {

	/**
	 * Adds a session to the time an account has accumulated.
	 *
	 * @param accountId the account that played
	 * @param seconds   how long this session lasted
	 * @return true if the total was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean accumulate(int accountId, long seconds);
}
