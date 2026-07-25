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

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerSettings;

/**
 * Reads and writes a character's settings over JDBC.
 * <p>
 * One row per kind, distinguished by settings_type. The three that hold layouts
 * are blobs; the two that hold flags are numbers written into the same blob
 * column, which is why reading them takes a detour through text.
 *
 * @author Oraion
 */
public final class JdbcPlayerSettingsRepository extends JdbcRepositorySupport implements PlayerSettingsRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcPlayerSettingsRepository.class);

	private static final int UI_SETTINGS = 0;
	private static final int SHORTCUTS = 1;
	private static final int HOUSE_BUDDIES = 2;
	private static final int DISPLAY = -1;
	private static final int DENY = -2;

	private static final String SELECT_ALL = "SELECT `settings_type`,`settings` FROM `player_settings` WHERE `player_id` = ?";
	private static final String REPLACE_ONE = "REPLACE INTO `player_settings` (`player_id`,`settings_type`,`settings`) VALUES (?,?,?)";

	public JdbcPlayerSettingsRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		PlayerSettings settings = new PlayerSettings();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					apply(settings, rows.getInt("settings_type"), rows.getBytes("settings"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the settings of character " + player.getObjectId() + ".", e);
		}

		settings.setPersistentState(PersistentState.UPDATED);
		player.setPlayerSettings(settings);
	}

	@Override
	public void save(Player player) {
		PlayerSettings settings = player.getPlayerSettings();
		if (settings.getPersistentState() == PersistentState.UPDATED) {
			return;
		}

		// One connection and one transaction for the five rows. The DAO borrowed a
		// connection per row, so a failure partway left the character with some
		// settings from this session and some from the last.
		inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(REPLACE_ONE)) {
				writeBlob(statement, player.getObjectId(), UI_SETTINGS, settings.getUiSettings());
				writeBlob(statement, player.getObjectId(), SHORTCUTS, settings.getShortcuts());
				writeBlob(statement, player.getObjectId(), HOUSE_BUDDIES, settings.getHouseBuddies());
				writeNumber(statement, player.getObjectId(), DISPLAY, settings.getDisplay());
				writeNumber(statement, player.getObjectId(), DENY, settings.getDeny());
				statement.executeBatch();
			}
			return null;
		}, "Failed to write the settings of character " + player.getObjectId() + ".");
	}

	/** Puts one stored row where it belongs. */
	private static void apply(PlayerSettings settings, int type, byte[] raw) {
		switch (type) {
			case UI_SETTINGS:
				settings.setUiSettings(raw);
				break;
			case SHORTCUTS:
				settings.setShortcuts(raw);
				break;
			case HOUSE_BUDDIES:
				settings.setHouseBuddies(raw);
				break;
			case DISPLAY:
				settings.setDisplay(readNumber(raw));
				break;
			case DENY:
				settings.setDeny(readNumber(raw));
				break;
			default:
				log.warn("Ignored a player setting of unknown type " + type + ".");
				break;
		}
	}

	/** Queues a layout, skipping a kind the character has never set. */
	private static void writeBlob(PreparedStatement statement, int playerId, int type, byte[] value)
			throws SQLException {
		if (value == null) {
			return;
		}
		statement.setInt(1, playerId);
		statement.setInt(2, type);
		statement.setBytes(3, value);
		statement.addBatch();
	}

	/** Queues a flag word, which shares the blob column with the layouts. */
	private static void writeNumber(PreparedStatement statement, int playerId, int type, int value)
			throws SQLException {
		statement.setInt(1, playerId);
		statement.setInt(2, type);
		statement.setInt(3, value);
		statement.addBatch();
	}

	/**
	 * Reads a number back out of the blob column.
	 * <p>
	 * The driver hands these back as bytes and refuses to convert them, so the
	 * digits have to be decoded and parsed.
	 */
	private static int readNumber(byte[] raw) {
		if (raw == null || raw.length == 0) {
			return 0;
		}
		String text = new String(raw, StandardCharsets.US_ASCII).trim();
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			log.warn("Ignored an unreadable numeric player setting: " + text);
			return 0;
		}
	}
}
