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

import java.util.List;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.team.legion.LegionMember;
import com.aionemu.gameserver.model.team.legion.LegionMemberEx;

/**
 * Holds which legion each character belongs to, and their standing in it.
 *
 * @author Oraion
 */
public interface LegionMemberRepository {

	/**
	 * Answers whether a character already belongs to a legion. Answers that they
	 * do when the question cannot be settled, so a doubtful character is never
	 * enrolled twice.
	 *
	 * @param playerId the character
	 * @return true if they belong to one, or it could not be checked
	 */
	boolean isEnrolled(int playerId);

	/**
	 * Reads a character's membership.
	 *
	 * @param playerId the character
	 * @return their membership, or null if they belong to no legion
	 * @throws RepositoryException if it could not be read
	 */
	LegionMember load(int playerId);

	/**
	 * Reads a character's membership along with who they are.
	 *
	 * @param playerId the character
	 * @return their membership, or null if they belong to no legion
	 * @throws RepositoryException if it could not be read
	 */
	LegionMemberEx loadDetailed(int playerId);

	/**
	 * Reads a character's membership along with who they are, by name.
	 *
	 * @param playerName the character
	 * @return their membership, or null if they belong to no legion
	 * @throws RepositoryException if it could not be read
	 */
	LegionMemberEx loadDetailed(String playerName);

	/**
	 * Reads who belongs to a legion.
	 *
	 * @param legionId the legion
	 * @return the character ids, empty if the legion stands alone
	 * @throws RepositoryException if they could not be read
	 */
	List<Integer> loadMembers(int legionId);

	/**
	 * Enrols a character in a legion.
	 *
	 * @param member the membership
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(LegionMember member);

	/**
	 * Writes a character's standing in their legion.
	 *
	 * @param playerId the character
	 * @param member   the membership
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(int playerId, LegionMember member);

	/**
	 * Takes a character out of their legion.
	 *
	 * @param playerId the character
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be written
	 */
	boolean remove(int playerId);
}
