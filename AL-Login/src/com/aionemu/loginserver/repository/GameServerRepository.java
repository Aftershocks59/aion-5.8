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
import com.aionemu.loginserver.GameServerInfo;

/**
 * Lists the game servers allowed to register with this login server.
 *
 * @author Oraion
 */
public interface GameServerRepository {

	/**
	 * Reads every registered game server, keyed by its id.
	 * <p>
	 * Reporting a failure matters here: an empty list means no game server may
	 * connect, so a silent one would take the whole cluster offline with nothing
	 * to point at.
	 *
	 * @return the servers, empty when none are registered
	 * @throws RepositoryException if they could not be read
	 */
	Map<Byte, GameServerInfo> findAll();
}
