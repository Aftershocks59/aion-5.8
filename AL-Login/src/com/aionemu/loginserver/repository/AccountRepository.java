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
import com.aionemu.loginserver.model.Account;

/**
 * Stores the accounts the login server authenticates.
 * <p>
 * Every method reports a database failure rather than answering with a neutral
 * value. This is the table that decides who gets in, so a read that quietly
 * answers "no such account" during an outage is the difference between refusing
 * everyone and letting the wrong one through.
 *
 * @author Oraion
 */
public interface AccountRepository {

	/** Answers the id used when an account cannot be found. */
	int NO_ACCOUNT = -1;

	/**
	 * Finds an account by its login name.
	 *
	 * @param name the login name
	 * @return the account, or null when there is none
	 * @throws RepositoryException if it could not be read
	 */
	Account findByName(String name);

	/**
	 * Finds an account by its id.
	 *
	 * @param id the account id
	 * @return the account, or null when there is none
	 * @throws RepositoryException if it could not be read
	 */
	Account findById(int id);

	/**
	 * Finds the id behind a login name.
	 *
	 * @param name the login name
	 * @return the id, or {@link #NO_ACCOUNT} when there is none
	 * @throws RepositoryException if it could not be read
	 */
	int findIdByName(String name);

	/**
	 * Counts the registered accounts.
	 *
	 * @return how many exist
	 * @throws RepositoryException if they could not be counted
	 */
	int count();

	/**
	 * Stores a new account and fills in the id it was given.
	 *
	 * @param account the account to store
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(Account account);

	/**
	 * Writes back an existing account, found by its id.
	 *
	 * @param account the account to update
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean update(Account account);

	/**
	 * Records the last game server an account played on.
	 *
	 * @param accountId  the account
	 * @param lastServer the server it last joined
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean updateLastServer(int accountId, byte lastServer);

	/**
	 * Records the address an account last connected from.
	 *
	 * @param accountId the account
	 * @param ip        the address
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean updateLastIp(int accountId, String ip);

	/**
	 * Reads the address an account last connected from.
	 *
	 * @param accountId the account
	 * @return the address, or null when the account has none recorded
	 * @throws RepositoryException if it could not be read
	 */
	String findLastIp(int accountId);

	/**
	 * Records the machine an account last connected from.
	 *
	 * @param accountId the account
	 * @param mac       the hardware address
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean updateLastMac(int accountId, String mac);

	/**
	 * Restores the membership of an account whose subscription has run out.
	 *
	 * @param accountId the account
	 * @return true if a subscription had expired and was restored
	 * @throws RepositoryException if it could not be written
	 */
	boolean restoreExpiredMembership(int accountId);

	/**
	 * Deletes the accounts nobody has logged into for a while.
	 *
	 * @param daysOfInactivity how long an account may stay idle
	 * @return how many were deleted
	 * @throws RepositoryException if they could not be deleted
	 */
	int deleteInactive(int daysOfInactivity);
}
