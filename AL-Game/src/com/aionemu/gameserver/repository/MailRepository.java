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

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.Letter;
import com.aionemu.gameserver.model.gameobjects.player.Mailbox;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;

/**
 * Holds the letters waiting in each character's mailbox.
 *
 * @author Oraion
 */
public interface MailRepository {

	/**
	 * Answers every letter id already in use, so the id factory can reserve them.
	 *
	 * @return the ids, empty if nobody has post
	 * @throws RepositoryException if they could not be read
	 */
	int[] findUsedIds();

	/**
	 * Reads a character's mailbox, with each letter carrying its attachment.
	 *
	 * @param player the character
	 * @return their mailbox, empty if they have no post
	 * @throws RepositoryException if it could not be read
	 */
	Mailbox load(Player player);

	/**
	 * Answers whether a character has a letter they have not opened.
	 *
	 * @param playerId the character
	 * @return true if any letter is unread
	 * @throws RepositoryException if it could not be read
	 */
	boolean hasUnread(int playerId);

	/**
	 * Writes every letter in a character's mailbox that has changed.
	 *
	 * @param player the character
	 * @throws RepositoryException if they could not be written
	 */
	void save(Player player);

	/**
	 * Writes one letter, according to what has happened to it. The letter is
	 * marked as saved only once the write has gone through.
	 *
	 * @param at     the moment it was sent or last touched
	 * @param letter the letter
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(Timestamp at, Letter letter);

	/**
	 * Throws a letter away.
	 *
	 * @param letterId the letter
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be written
	 */
	boolean remove(int letterId);

	/**
	 * Records how much post an offline character is holding.
	 *
	 * @param recipient the character
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setOfflineCounter(PlayerCommonData recipient);
}
