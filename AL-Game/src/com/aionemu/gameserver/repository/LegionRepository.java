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
import java.util.TreeMap;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.team.legion.Legion;
import com.aionemu.gameserver.model.team.legion.LegionEmblem;
import com.aionemu.gameserver.model.team.legion.LegionHistory;
import com.aionemu.gameserver.model.team.legion.LegionJoinRequest;
import com.aionemu.gameserver.model.team.legion.LegionWarehouse;

/**
 * Holds the legions, their emblems, their history and their warehouse.
 *
 * @author Oraion
 */
public interface LegionRepository {

	/**
	 * Answers every legion id already in use, so the id factory can reserve them.
	 *
	 * @return the ids, empty if no legion stands
	 * @throws RepositoryException if they could not be read
	 */
	int[] findUsedIds();

	/**
	 * Answers whether a legion name is taken. Answers that it is when the question
	 * cannot be settled, so a doubtful name is never handed out twice.
	 *
	 * @param name the name
	 * @return true if the name is taken, or could not be checked
	 */
	boolean isNameUsed(String name);

	/**
	 * Reads a legion by id.
	 *
	 * @param legionId the legion
	 * @return the legion, or null if there is no such legion
	 * @throws RepositoryException if it could not be read
	 */
	Legion load(int legionId);

	/**
	 * Reads a legion by name.
	 *
	 * @param legionName the legion
	 * @return the legion, or null if there is no such legion
	 * @throws RepositoryException if it could not be read
	 */
	Legion load(String legionName);

	/**
	 * Answers which legions hold territory.
	 *
	 * @return the legion ids, empty if none does
	 * @throws RepositoryException if they could not be read
	 */
	Collection<Integer> findWithTerritory();

	/**
	 * Founds a legion.
	 *
	 * @param legion the legion
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(Legion legion);

	/**
	 * Writes a legion and the candidacies standing against it.
	 *
	 * @param legion the legion
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean save(Legion legion);

	/**
	 * Writes only a legion's notice board and joining terms.
	 *
	 * @param legion the legion
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean saveDescription(Legion legion);

	/**
	 * Disbands a legion and releases whatever it held.
	 *
	 * @param legionId the legion
	 * @throws RepositoryException if it could not be written
	 */
	void remove(int legionId);

	/**
	 * Reads the seven most recent notices of a legion, oldest first.
	 *
	 * @param legionId the legion
	 * @return the notices, empty if the board is bare
	 * @throws RepositoryException if they could not be read
	 */
	TreeMap<Timestamp, String> loadNotices(int legionId);

	/**
	 * Pins a notice on a legion's board.
	 *
	 * @param legionId the legion
	 * @param at       when it was written
	 * @param message  the notice
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean addNotice(int legionId, Timestamp at, String message);

	/**
	 * Takes a notice off a legion's board.
	 *
	 * @param legionId the legion
	 * @param at       when it was written
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be written
	 */
	boolean removeNotice(int legionId, Timestamp at);

	/**
	 * Reads a legion's emblem.
	 *
	 * @param legionId the legion
	 * @return the emblem, blank if the legion has none
	 * @throws RepositoryException if it could not be read
	 */
	LegionEmblem loadEmblem(int legionId);

	/**
	 * Writes a legion's emblem, creating it if the legion had none. A custom
	 * emblem with no artwork is ignored, as it was.
	 *
	 * @param legionId the legion
	 * @param emblem   the emblem
	 * @throws RepositoryException if it could not be written
	 */
	void saveEmblem(int legionId, LegionEmblem emblem);

	/**
	 * Reads a legion's warehouse.
	 *
	 * @param legion the legion
	 * @return the warehouse, empty if nothing is in it
	 * @throws RepositoryException if it could not be read
	 */
	LegionWarehouse loadWarehouse(Legion legion);

	/**
	 * Reads a legion's history onto it, oldest first.
	 *
	 * @param legion the legion
	 * @throws RepositoryException if it could not be read
	 */
	void loadHistory(Legion legion);

	/**
	 * Records one entry in a legion's history.
	 *
	 * @param legionId the legion
	 * @param entry    the entry
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean addHistory(int legionId, LegionHistory entry);

	/**
	 * Reads the candidacies standing against a legion, oldest first.
	 *
	 * @param legionId the legion
	 * @return the candidacies, empty if nobody has asked to join
	 * @throws RepositoryException if they could not be read
	 */
	List<LegionJoinRequest> loadJoinRequests(int legionId);

	/**
	 * Writes a candidacy, replacing whatever the same character had asked before.
	 *
	 * @param request the candidacy
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean saveJoinRequest(LegionJoinRequest request);

	/**
	 * Withdraws a candidacy.
	 *
	 * @param legionId the legion
	 * @param playerId the character
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be written
	 */
	boolean removeJoinRequest(int legionId, int playerId);
}
