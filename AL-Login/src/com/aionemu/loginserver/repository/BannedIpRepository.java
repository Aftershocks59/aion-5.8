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

import java.sql.Timestamp;
import java.util.Set;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.model.BannedIP;

/**
 * Stores the banned address masks.
 *
 * @author Oraion
 */
public interface BannedIpRepository {

	/**
	 * Reads every ban.
	 * <p>
	 * Reporting a failure matters: an empty set means nobody is banned, so a
	 * silent one lifts every ban until the next reload.
	 *
	 * @return the bans, empty when there are none
	 * @throws RepositoryException if they could not be read
	 */
	Set<BannedIP> findAll();

	/**
	 * Bans a mask until the given moment.
	 *
	 * @param mask   the address mask to ban
	 * @param expiry when the ban lifts, or null for a permanent one
	 * @return the stored ban, carrying the id it was given, or null if it was
	 *         refused
	 * @throws RepositoryException if it could not be written
	 */
	BannedIP ban(String mask, Timestamp expiry);

	/**
	 * Stores a new ban and fills in the id the database assigned it.
	 * <p>
	 * The id matters: callers decide between storing and updating by whether the
	 * ban has one.
	 *
	 * @param bannedIP the ban to store
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(BannedIP bannedIP);

	/**
	 * Writes back an existing ban, found by its id.
	 *
	 * @param bannedIP the ban to update
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean update(BannedIP bannedIP);

	/**
	 * Lifts the ban on a mask.
	 *
	 * @param mask the address mask to unban
	 * @return true if a ban was lifted
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(String mask);

	/**
	 * Drops every ban whose end has passed, leaving the permanent ones.
	 *
	 * @return how many were dropped
	 * @throws RepositoryException if they could not be removed
	 */
	int removeExpired();
}
