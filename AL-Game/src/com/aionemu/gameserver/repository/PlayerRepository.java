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
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.account.PlayerAccountData;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.model.team.legion.LegionJoinRequestState;

/**
 * Holds the characters themselves.
 *
 * @author Oraion
 */
public interface PlayerRepository {

	/** Answers when no character carries the name or id that was asked for. */
	int NO_CHARACTER = 0;

	/**
	 * Answers every character id already in use, so the id factory can reserve
	 * them.
	 *
	 * @return the ids, empty if nobody has a character
	 * @throws RepositoryException if they could not be read
	 */
	int[] findUsedIds();

	/**
	 * Answers whether a character name is taken. Answers that it is when the
	 * question cannot be settled, so a doubtful name is never handed out twice.
	 *
	 * @param name the name
	 * @return true if the name is taken, or could not be checked
	 */
	boolean isNameUsed(String name);

	/**
	 * Reads a character.
	 *
	 * @param playerId the character
	 * @return who they are, or null if there is no such character
	 * @throws RepositoryException if it could not be read
	 */
	PlayerCommonData load(int playerId);

	/**
	 * Reads a character by name, answering the live one if they are online.
	 *
	 * @param name the name
	 * @return who they are, or null if there is no such character
	 * @throws RepositoryException if it could not be read
	 */
	PlayerCommonData loadByName(String name);

	/**
	 * Reads the names of several characters at once.
	 *
	 * @param playerIds the characters
	 * @return their names, keyed by id
	 * @throws RepositoryException if they could not be read
	 */
	Map<Integer, String> findNames(Collection<Integer> playerIds);

	/**
	 * Answers a character's name.
	 *
	 * @param playerId the character
	 * @return the name, or null if there is no such character
	 * @throws RepositoryException if it could not be read
	 */
	String findName(int playerId);

	/**
	 * Answers which character carries a name.
	 *
	 * @param name the name
	 * @return the character, or {@link #NO_CHARACTER} if nobody carries it
	 * @throws RepositoryException if it could not be read
	 */
	int findIdByName(String name);

	/**
	 * Answers which account a character belongs to.
	 *
	 * @param name the character
	 * @return the account, or {@link #NO_CHARACTER} if there is no such character
	 * @throws RepositoryException if it could not be read
	 */
	int findAccountIdByName(String name);

	/**
	 * Answers which characters an account holds.
	 *
	 * @param accountId the account
	 * @return the character ids, empty if it holds none
	 * @throws RepositoryException if they could not be read
	 */
	List<Integer> findIdsOnAccount(int accountId);

	/**
	 * Counts the characters an account holds that are not awaiting deletion.
	 *
	 * @param accountId the account
	 * @return the count
	 * @throws RepositoryException if it could not be read
	 */
	int countOnAccount(int accountId);

	/**
	 * Counts the accounts that hold a character of a race past the level the
	 * faction ratio is measured from.
	 *
	 * @param race the race
	 * @return the count
	 * @throws RepositoryException if it could not be read
	 */
	int countAccountsForRace(Race race);

	/**
	 * Counts the characters recorded as online.
	 *
	 * @return the count
	 * @throws RepositoryException if it could not be read
	 */
	int countOnline();

	/**
	 * Answers which characters have not logged in for a while.
	 *
	 * @param daysOfInactivity how long they must have been away
	 * @param limit            how many to answer at most, or zero for all of them
	 * @return the character ids, empty if everybody has been around
	 * @throws RepositoryException if they could not be read
	 */
	List<Integer> findInactive(int daysOfInactivity, int limit);

	/**
	 * Creates a character.
	 *
	 * @param character   who they are
	 * @param accountId   the account
	 * @param accountName the account
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(PlayerCommonData character, int accountId, String accountName);

	/**
	 * Writes everything about a character that changes as they play.
	 *
	 * @param player the character
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(Player player);

	/**
	 * Writes only a character's name.
	 *
	 * @param character the character
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean saveName(PlayerCommonData character);

	/**
	 * Deletes a character.
	 *
	 * @param playerId the character
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be written
	 */
	boolean remove(int playerId);

	/**
	 * Reads when a character was created and when they are due to be deleted.
	 *
	 * @param account the account entry to fill
	 * @throws RepositoryException if it could not be read
	 */
	void loadCreationAndDeletion(PlayerAccountData account);

	/**
	 * Records when a character was created.
	 *
	 * @param playerId the character
	 * @param at       the moment
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setCreationTime(int playerId, Timestamp at);

	/**
	 * Records when a character is due to be deleted, or clears it.
	 *
	 * @param playerId the character
	 * @param at       the moment, or null to keep them
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setDeletionTime(int playerId, Timestamp at);

	/**
	 * Records when a character was last moved between servers.
	 *
	 * @param playerId the character
	 * @param at       the moment
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setLastTransferTime(int playerId, long at);

	/**
	 * Records whether a character is online.
	 *
	 * @param playerId the character
	 * @param online   true if they are
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setOnline(int playerId, boolean online);

	/**
	 * Records that every character is online, or that none is.
	 *
	 * @param online true if they all are
	 * @return the number of rows updated
	 * @throws RepositoryException if it could not be written
	 */
	int setAllOnline(boolean online);

	/**
	 * Records where a character stands on their request to join a legion.
	 *
	 * @param playerId the character
	 * @param state    where they stand
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setJoinRequestState(int playerId, LegionJoinRequestState state);

	/**
	 * Forgets a character's request to join a legion.
	 *
	 * @param playerId the character
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean clearJoinRequest(int playerId);

	/**
	 * Reads where a character stands on their request to join a legion, onto them.
	 *
	 * @param player the character
	 * @throws RepositoryException if it could not be read
	 */
	void loadJoinRequestState(Player player);
}
