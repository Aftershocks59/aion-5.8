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
 * Publishes what each game server is doing, for whatever reads the svstats
 * table.
 *
 * @author Oraion
 */
public interface ServerStatsRepository {

	/**
	 * Records a server that is up, with how many players it holds.
	 *
	 * @param serverId the server being described
	 * @param status   the status code to publish
	 * @param current  players connected right now
	 * @param maximum  players it accepts
	 * @throws RepositoryException if it could not be written
	 */
	void publishOnline(int serverId, int status, int current, int maximum);

	/**
	 * Records a server that is down, leaving its capacity untouched.
	 *
	 * @param serverId the server being described
	 * @param status   the status code to publish
	 * @param current  players connected, normally none
	 * @throws RepositoryException if it could not be written
	 */
	void publishOffline(int serverId, int status, int current);

	/**
	 * Records every server as down, which is what a shutdown does.
	 *
	 * @param status  the status code to publish
	 * @param current players connected, normally none
	 * @throws RepositoryException if it could not be written
	 */
	void publishAllOffline(int status, int current);
}
