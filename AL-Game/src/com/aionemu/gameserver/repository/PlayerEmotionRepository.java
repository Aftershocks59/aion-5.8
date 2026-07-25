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
import com.aionemu.gameserver.model.gameobjects.player.emotion.Emotion;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Holds the emotes a character has unlocked, and when the borrowed ones run out.
 *
 * @author Oraion
 */
public interface PlayerEmotionRepository {

	/**
	 * Reads a character's emotes and hands them the list.
	 *
	 * @param player the character entering the world
	 * @throws RepositoryException if they could not be read
	 */
	void load(Player player);

	/**
	 * Records an emote a character has just gained.
	 *
	 * @param player  the character gaining it
	 * @param emotion the emote and when it expires
	 * @throws RepositoryException if it could not be written
	 */
	void add(Player player, Emotion emotion);

	/**
	 * Takes an emote away from a character.
	 *
	 * @param playerId  the character losing it
	 * @param emotionId the emote to remove
	 * @throws RepositoryException if it could not be removed
	 */
	void remove(int playerId, int emotionId);
}
