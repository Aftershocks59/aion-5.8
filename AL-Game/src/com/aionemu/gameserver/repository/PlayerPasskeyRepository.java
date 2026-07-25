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
 * Holds the second passcode an account may set on its characters.
 * <p>
 * The two reads answer false when they cannot tell, which is deliberate: this
 * decides whether somebody gets past a lock, and a database failure must not
 * open it.
 *
 * @author Oraion
 */
public interface PlayerPasskeyRepository {

	/**
	 * Sets a passkey on an account that had none.
	 *
	 * @param accountId the account
	 * @param passkey   the passkey, already hashed by the caller
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean create(int accountId, String passkey);

	/**
	 * Replaces a passkey, but only for somebody who knows the old one.
	 *
	 * @param accountId  the account
	 * @param oldPasskey the passkey being replaced
	 * @param newPasskey the passkey replacing it
	 * @return true if the old one matched and the new one was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean replace(int accountId, String oldPasskey, String newPasskey);

	/**
	 * Replaces a passkey without knowing the old one, for an administrator.
	 *
	 * @param accountId  the account
	 * @param newPasskey the passkey replacing whatever was there
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean reset(int accountId, String newPasskey);

	/**
	 * Says whether a passkey is the one the account set.
	 *
	 * @param accountId the account
	 * @param passkey   the passkey offered
	 * @return true only when it matches; false when it does not, and when the
	 *         question could not be answered
	 */
	boolean matches(int accountId, String passkey);

	/**
	 * Says whether an account has set a passkey at all.
	 *
	 * @param accountId the account
	 * @return true only when it has; false when it has not, and when the question
	 *         could not be answered
	 */
	boolean exists(int accountId);
}
