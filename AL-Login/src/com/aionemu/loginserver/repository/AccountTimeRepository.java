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
import com.aionemu.loginserver.model.AccountTime;

/**
 * Holds when an account last played, for how long, and until when it is barred.
 *
 * @author Oraion
 */
public interface AccountTimeRepository {

	/**
	 * Reads the timings of one account.
	 * <p>
	 * Answers null only when the account has no row yet. A database failure is
	 * reported instead, because the DAO answered null for both and a caller reading
	 * a penalty end could not tell "never punished" from "could not ask".
	 *
	 * @param accountId the account to read
	 * @return its timings, or null when it has none recorded
	 * @throws RepositoryException if they could not be read
	 */
	AccountTime find(int accountId);

	/**
	 * Writes the timings of one account, replacing what it had.
	 *
	 * @param accountId   the account to write
	 * @param accountTime the timings to store
	 * @return true if a row was written
	 * @throws RepositoryException if they could not be written
	 */
	boolean save(int accountId, AccountTime accountTime);
}
