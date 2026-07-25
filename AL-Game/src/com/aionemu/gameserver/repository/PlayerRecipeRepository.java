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
import com.aionemu.gameserver.model.gameobjects.player.RecipeList;

/**
 * Holds the crafting recipes a character has learnt.
 *
 * @author Oraion
 */
public interface PlayerRecipeRepository {

	/**
	 * Reads every recipe a character knows.
	 *
	 * @param playerId the character to read
	 * @return its recipes, empty when it knows none
	 * @throws RepositoryException if they could not be read
	 */
	RecipeList findAll(int playerId);

	/**
	 * Teaches a recipe to a character.
	 *
	 * @param playerId the character learning
	 * @param recipeId the recipe learnt
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(int playerId, int recipeId);

	/**
	 * Makes a character forget a recipe.
	 *
	 * @param playerId the character forgetting
	 * @param recipeId the recipe forgotten
	 * @return true if a row was removed
	 * @throws RepositoryException if it could not be removed
	 */
	boolean remove(int playerId, int recipeId);
}
