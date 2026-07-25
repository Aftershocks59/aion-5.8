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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.account.CharacterBanInfo;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.PunishmentService.PunishmentType;

/**
 * Reads and writes the punishments a character is serving, over JDBC.
 * <p>
 * The table keeps seconds where the game keeps milliseconds, which is why every
 * duration crosses this boundary multiplied or divided by a thousand.
 *
 * @author Oraion
 */
public final class JdbcPlayerPunishmentRepository extends JdbcRepositorySupport
		implements PlayerPunishmentRepository {

	private static final long MILLIS_PER_SECOND = 1_000L;

	private static final String SELECT_ONE = "SELECT `start_time`,`duration`,`reason` FROM `player_punishments` "
			+ "WHERE `player_id` = ? AND `punishment_type` = ?";
	private static final String UPDATE_DURATION = "UPDATE `player_punishments` SET `duration` = ? "
			+ "WHERE `player_id` = ? AND `punishment_type` = ?";
	private static final String REPLACE_ONE = "REPLACE INTO `player_punishments` "
			+ "(`player_id`,`punishment_type`,`start_time`,`duration`,`reason`) VALUES (?,?,?,?,?)";
	private static final String DELETE_ONE = "DELETE FROM `player_punishments` "
			+ "WHERE `player_id` = ? AND `punishment_type` = ?";

	public JdbcPlayerPunishmentRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player, PunishmentType type) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, player.getObjectId());
			statement.setString(2, type.toString());
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) {
					apply(player, type, rows.getLong("duration") * MILLIS_PER_SECOND);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the " + type + " punishment of character "
					+ player.getObjectId() + ".", e);
		}
	}

	@Override
	public void save(Player player, PunishmentType type) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_DURATION)) {
			statement.setLong(1, remainingSeconds(player, type));
			statement.setInt(2, player.getObjectId());
			statement.setString(3, type.toString());
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to write the " + type + " punishment of character "
					+ player.getObjectId() + ".", e);
		}
	}

	@Override
	public void punish(int playerId, PunishmentType type, long duration, String reason) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(REPLACE_ONE)) {
			statement.setInt(1, playerId);
			statement.setString(2, type.toString());
			statement.setLong(3, System.currentTimeMillis() / MILLIS_PER_SECOND);
			statement.setLong(4, duration);
			statement.setString(5, reason);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to impose a " + type + " punishment on character " + playerId + ".", e);
		}
	}

	@Override
	public void pardon(int playerId, PunishmentType type) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			statement.setString(2, type.toString());
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to lift the " + type + " punishment of character " + playerId + ".", e);
		}
	}

	@Override
	public CharacterBanInfo findBan(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, playerId);
			statement.setString(2, PunishmentType.CHARBAN.toString());
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? new CharacterBanInfo(playerId, rows.getLong("start_time"),
						rows.getLong("duration"), rows.getString("reason")) : null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the ban on character " + playerId + ".", e);
		}
	}

	/** Applies a stored duration to whichever timer the punishment drives. */
	private static void apply(Player player, PunishmentType type, long durationMillis) {
		if (type == PunishmentType.PRISON) {
			player.setPrisonTimer(durationMillis);
		} else if (type == PunishmentType.GATHER) {
			player.setGatherableTimer(durationMillis);
		}
	}

	/**
	 * Answers how much of a punishment is left, in the seconds the table keeps.
	 * <p>
	 * The gathering ban counts down while the character is out of the world, so
	 * what is stored is what remains after the time already spent.
	 */
	private static long remainingSeconds(Player player, PunishmentType type) {
		if (type == PunishmentType.PRISON) {
			return player.getPrisonTimer() / MILLIS_PER_SECOND;
		}
		if (type == PunishmentType.GATHER) {
			long served = System.currentTimeMillis() - player.getStopGatherable();
			return (player.getGatherableTimer() - served) / MILLIS_PER_SECOND;
		}
		return 0L;
	}
}
