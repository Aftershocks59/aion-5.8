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

import java.sql.Timestamp;
import java.util.List;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.event_window.PlayerEventWindowList;

/**
 * Holds how far each account has got through the timed event windows.
 *
 * @author Oraion
 */
public interface EventWindowRepository {

	/**
	 * Reads the event windows an account has open.
	 *
	 * @param accountId the account
	 * @return the windows, empty if it has none
	 * @throws RepositoryException if they could not be read
	 */
	PlayerEventWindowList load(int accountId);

	/**
	 * Reads which event windows an account has open.
	 *
	 * @param accountId the account
	 * @return the event ids, empty if it has none
	 * @throws RepositoryException if they could not be read
	 */
	List<Integer> findEventIds(int accountId);

	/**
	 * Opens an event window for an account.
	 *
	 * @param accountId the account
	 * @param eventId   the event
	 * @param lastStamp when it was opened
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(int accountId, int eventId, Timestamp lastStamp);

	/**
	 * Writes an account's progress through an event window, opening it if it is
	 * not open yet.
	 *
	 * @param accountId the account
	 * @param eventId   the event
	 * @param lastStamp when it was last touched
	 * @param elapsed   how long the account has spent on it
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(int accountId, int eventId, Timestamp lastStamp, int elapsed);

	/**
	 * Closes an event window.
	 *
	 * @param accountId the account
	 * @param eventId   the event
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be written
	 */
	boolean remove(int accountId, int eventId);

	/**
	 * Answers when an account last touched an event window.
	 *
	 * @param accountId the account
	 * @param eventId   the event
	 * @return the moment, or null if the window is not open
	 * @throws RepositoryException if it could not be read
	 */
	Timestamp findLastStamp(int accountId, int eventId);

	/**
	 * Answers how long an account has spent on an event window.
	 *
	 * @param accountId the account
	 * @param eventId   the event
	 * @return the count, zero if the window is not open
	 * @throws RepositoryException if it could not be read
	 */
	int findElapsed(int accountId, int eventId);

	/**
	 * Records how long an account has spent on an event window.
	 *
	 * @param accountId the account
	 * @param eventId   the event
	 * @param elapsed   the count
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setElapsed(int accountId, int eventId, int elapsed);

	/**
	 * Answers how many rewards an account has taken from an event window.
	 *
	 * @param accountId the account
	 * @param eventId   the event
	 * @return the count, zero if the window is not open
	 * @throws RepositoryException if it could not be read
	 */
	int findRewardCount(int accountId, int eventId);

	/**
	 * Records how many rewards an account has taken from an event window, and
	 * starts its clock over.
	 *
	 * @param accountId the account
	 * @param eventId   the event
	 * @param count     the count
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setRewardCount(int accountId, int eventId, int count);
}
