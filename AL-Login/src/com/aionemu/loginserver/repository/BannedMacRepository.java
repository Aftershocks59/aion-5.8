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

import java.util.Map;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.model.base.BannedMacEntry;

/**
 * Stores the banned MAC addresses.
 * <p>
 * Every method reports a database failure rather than answering with an empty
 * result: a ban list that silently comes back empty lets in exactly the clients
 * it exists to keep out.
 *
 * @author Oraion
 */
public interface BannedMacRepository {

	/**
	 * Reads every ban, keyed by address.
	 *
	 * @return the bans, empty when there are none
	 * @throws RepositoryException if they could not be read
	 */
	Map<String, BannedMacEntry> findAll();

	/**
	 * Writes a ban, replacing any the address already had.
	 *
	 * @param entry the ban to store
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(BannedMacEntry entry);

	/**
	 * Lifts the ban on an address.
	 *
	 * @param address the address to unban
	 * @return true if a ban was lifted, false if it had none
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(String address);

	/**
	 * Drops every ban whose end date has passed.
	 *
	 * @return how many were dropped
	 * @throws RepositoryException if they could not be removed
	 */
	int removeExpired();
}
