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

import java.util.Set;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.Announcement;

/**
 * Holds the messages the server repeats to everyone on a timer.
 *
 * @author Oraion
 */
public interface AnnouncementRepository {

	/**
	 * Reads every announcement.
	 *
	 * @return the announcements, empty when there are none
	 * @throws RepositoryException if they could not be read
	 */
	Set<Announcement> findAll();

	/**
	 * Adds an announcement to the rotation.
	 *
	 * @param announcement what to say, to whom and how often
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(Announcement announcement);

	/**
	 * Removes an announcement from the rotation.
	 *
	 * @param announcementId which one
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(int announcementId);
}
