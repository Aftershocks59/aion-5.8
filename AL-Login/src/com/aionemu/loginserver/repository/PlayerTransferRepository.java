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

import java.util.List;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.service.ptransfer.PlayerTransferTask;

/**
 * Holds the requests to move a character between game servers.
 *
 * @author Oraion
 */
public interface PlayerTransferRepository {

	/**
	 * Reads the transfers nobody has started yet.
	 *
	 * @return the pending transfers, empty when there are none
	 * @throws RepositoryException if they could not be read
	 */
	List<PlayerTransferTask> findPending();

	/**
	 * Writes back the state of a transfer, stamping when it moved.
	 *
	 * @param task the transfer to update
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(PlayerTransferTask task);
}
