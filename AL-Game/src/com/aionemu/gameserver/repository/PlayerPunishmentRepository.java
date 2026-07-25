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
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.account.CharacterBanInfo;
import com.aionemu.gameserver.services.PunishmentService.PunishmentType;

/**
 * Holds the punishments a character is serving: prison, a gathering ban, or a
 * ban on the character itself.
 *
 * @author Oraion
 */
public interface PlayerPunishmentRepository {

	/**
	 * Reads one kind of punishment and applies its remaining time.
	 *
	 * @param player the character entering the world
	 * @param type   which punishment to read
	 * @throws RepositoryException if it could not be read
	 */
	void load(Player player, PunishmentType type);

	/**
	 * Writes back how long a character still has to serve.
	 *
	 * @param player the character to save
	 * @param type   which punishment to write
	 * @throws RepositoryException if it could not be written
	 */
	void save(Player player, PunishmentType type);

	/**
	 * Starts a punishment, replacing any of the same kind.
	 *
	 * @param playerId the character being punished
	 * @param type     which punishment
	 * @param duration how long it lasts, in seconds
	 * @param reason   why, as recorded by whoever imposed it
	 * @throws RepositoryException if it could not be written
	 */
	void punish(int playerId, PunishmentType type, long duration, String reason);

	/**
	 * Starts a punishment, taking its length from the timer the character already
	 * carries.
	 * <p>
	 * The same convenience the DAO offered. A punishment of a kind that drives no
	 * timer is ignored, as it was.
	 *
	 * @param player the character being punished
	 * @param type   which punishment
	 * @param reason why, as recorded by whoever imposed it
	 * @throws RepositoryException if it could not be written
	 */
	default void punish(Player player, PunishmentType type, String reason) {
		if (type == PunishmentType.PRISON) {
			punish(player.getObjectId(), type, player.getPrisonTimer() / 1000L, reason);
		} else if (type == PunishmentType.GATHER) {
			punish(player.getObjectId(), type, player.getGatherableTimer() / 1000L, reason);
		}
	}

	/**
	 * Ends a punishment.
	 *
	 * @param playerId the character being released
	 * @param type     which punishment to lift
	 * @throws RepositoryException if it could not be removed
	 */
	void pardon(int playerId, PunishmentType type);

	/**
	 * Reads the ban standing against a character, if any.
	 *
	 * @param playerId the character to check
	 * @return the ban, or null when it has none
	 * @throws RepositoryException if it could not be read
	 */
	CharacterBanInfo findBan(int playerId);
}
