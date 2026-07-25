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

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.BlockList;
import com.aionemu.gameserver.model.gameobjects.player.FriendList;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;

/**
 * Holds who a character has befriended and who they have blocked.
 * <p>
 * Both lists name other characters, so reading either needs a way to look one
 * up. That lookup is handed in rather than reached for, because it still lives
 * on the player DAO and will move later.
 *
 * @author Oraion
 */
public interface PlayerSocialRepository {

	/** Finds another character by its object id, for the two lists to name. */
	@FunctionalInterface
	interface CharacterLookup {

		PlayerCommonData find(int objectId);
	}

	/**
	 * Reads a character's friends.
	 *
	 * @param player the character entering the world
	 * @param lookup how to name the friends it finds
	 * @return the list, empty when it has no friends
	 * @throws RepositoryException if it could not be read
	 */
	FriendList findFriends(Player player, CharacterLookup lookup);

	/**
	 * Befriends two characters, in both directions.
	 *
	 * @param playerId the character asking
	 * @param friendId the character accepting
	 * @return true if the friendship was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean addFriend(int playerId, int friendId);

	/**
	 * Ends a friendship, in both directions.
	 *
	 * @param playerId the character leaving
	 * @param friendId the character left
	 * @return true if the friendship was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean removeFriend(int playerId, int friendId);

	/**
	 * Writes the note a character keeps against a friend.
	 *
	 * @param playerId the character writing
	 * @param friendId the friend written about
	 * @param note     what it says
	 * @throws RepositoryException if it could not be written
	 */
	void setFriendNote(int playerId, int friendId, String note);

	/**
	 * Reads a character's block list.
	 *
	 * @param player the character entering the world
	 * @param lookup how to name the characters it has blocked
	 * @return the list, empty when it has blocked nobody
	 * @throws RepositoryException if it could not be read
	 */
	BlockList findBlocked(Player player, CharacterLookup lookup);

	/**
	 * Blocks another character.
	 *
	 * @param playerId  the character blocking
	 * @param blockedId the character blocked
	 * @param reason    why, as the player wrote it
	 * @return true if the block was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean block(int playerId, int blockedId, String reason);

	/**
	 * Unblocks another character.
	 *
	 * @param playerId  the character unblocking
	 * @param blockedId the character unblocked
	 * @return true if a block was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean unblock(int playerId, int blockedId);

	/**
	 * Rewrites why a character is blocked.
	 *
	 * @param playerId  the character blocking
	 * @param blockedId the character blocked
	 * @param reason    the new reason
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setBlockReason(int playerId, int blockedId, String reason);
}
