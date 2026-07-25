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
import com.aionemu.gameserver.model.templates.survey.SurveyItem;

/**
 * Holds the items an operator has queued for delivery to characters.
 *
 * @author Oraion
 */
public interface SurveyRepository {

	/**
	 * Reads everything not yet delivered.
	 *
	 * @return the pending deliveries, empty when there are none
	 * @throws RepositoryException if they could not be read
	 */
	List<SurveyItem> findPending();

	/**
	 * Marks one delivery as done, stamping when.
	 *
	 * @param uniqueId which delivery
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean markDelivered(int uniqueId);
}
