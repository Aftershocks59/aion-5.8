/*
 * This file is part of Encom.
 *
 *  Encom is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Encom is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser Public License
 *  along with Encom.  If not, see <http://www.gnu.org/licenses/>.
 */
package mysql5;

import com.aionemu.commons.database.DB;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.database.IUStH;
import com.aionemu.gameserver.dao.MySQL5DAOUtils;
import com.aionemu.gameserver.dao.PlayerSettingsDAO;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author ATracer
 */
public class MySQL5PlayerSettingsDAO extends PlayerSettingsDAO {

	private static final Logger log = LoggerFactory.getLogger(MySQL5PlayerSettingsDAO.class);

	/**
	 * TODO 1) analyze possibility to zip settings 2) insert/update instead of replace 0 - uisettings 1 - shortcuts 2 -
	 * display 3 - deny
	 */
	@Override
	public void loadSettings(final Player player) {
		final int playerId = player.getObjectId();
		final PlayerSettings playerSettings = new PlayerSettings();
		Connection con = null;
		try {
			con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT * FROM player_settings WHERE player_id = ?");
			statement.setInt(1, playerId);
			ResultSet resultSet = statement.executeQuery();
			// The settings column is a blob for every type. The two numeric types are
			// written with setInt, so the driver stores their decimal text; read them
			// back the same way. Asking the driver for an int worked only because the
			// MySQL connector converted silently, where MariaDB rejects the blob.
			while (resultSet.next()) {
				int type = resultSet.getInt("settings_type");
				switch (type) {
					case 0:
						playerSettings.setUiSettings(resultSet.getBytes("settings"));
						break;
					case 1:
						playerSettings.setShortcuts(resultSet.getBytes("settings"));
						break;
					case 2:
						playerSettings.setHouseBuddies(resultSet.getBytes("settings"));
						break;
					case -1:
						playerSettings.setDisplay(readIntFromBlob(resultSet));
						break;
					case -2:
						playerSettings.setDeny(readIntFromBlob(resultSet));
						break;
				}
			}
			resultSet.close();
			statement.close();
		}
		catch (Exception e) {
			log.error("Could not restore PlayerSettings data for player " + playerId + " from DB: " + e.getMessage(), e);
		}
		finally {
			DatabaseFactory.close(con);
		}
		playerSettings.setPersistentState(PersistentState.UPDATED);
		player.setPlayerSettings(playerSettings);
	}

	@Override
	public void saveSettings(final Player player) {
		final int playerId = player.getObjectId();

		PlayerSettings playerSettings = player.getPlayerSettings();
		if (playerSettings.getPersistentState() == PersistentState.UPDATED)
			return;

		final byte[] uiSettings = playerSettings.getUiSettings();
		final byte[] shortcuts = playerSettings.getShortcuts();
		final byte[] houseBuddies = playerSettings.getHouseBuddies();
		final int display = playerSettings.getDisplay();
		final int deny = playerSettings.getDeny();

		if (uiSettings != null) {
			DB.insertUpdate("REPLACE INTO player_settings values (?, ?, ?)", new IUStH() {

				@Override
				public void handleInsertUpdate(PreparedStatement stmt) throws SQLException {
					stmt.setInt(1, playerId);
					stmt.setInt(2, 0);
					stmt.setBytes(3, uiSettings);
					stmt.execute();
				}
			});
		}

		if (shortcuts != null) {
			DB.insertUpdate("REPLACE INTO player_settings values (?, ?, ?)", new IUStH() {

				@Override
				public void handleInsertUpdate(PreparedStatement stmt) throws SQLException {
					stmt.setInt(1, playerId);
					stmt.setInt(2, 1);
					stmt.setBytes(3, shortcuts);
					stmt.execute();
				}
			});
		}
		
		if (houseBuddies != null) {
			DB.insertUpdate("REPLACE INTO player_settings values (?, ?, ?)", new IUStH() {

				@Override
				public void handleInsertUpdate(PreparedStatement stmt) throws SQLException {
					stmt.setInt(1, playerId);
					stmt.setInt(2, 2);
					stmt.setBytes(3, houseBuddies);
					stmt.execute();
				}
			});
		}

		DB.insertUpdate("REPLACE INTO player_settings values (?, ?, ?)", new IUStH() {

			@Override
			public void handleInsertUpdate(PreparedStatement stmt) throws SQLException {
				stmt.setInt(1, playerId);
				stmt.setInt(2, -1);
				stmt.setInt(3, display);
				stmt.execute();
			}
		});

		DB.insertUpdate("REPLACE INTO player_settings values (?, ?, ?)", new IUStH() {

			@Override
			public void handleInsertUpdate(PreparedStatement stmt) throws SQLException {
				stmt.setInt(1, playerId);
				stmt.setInt(2, -2);
				stmt.setInt(3, deny);
				stmt.execute();
			}
		});

	}

	@Override
	public boolean supports(String databaseName, int majorVersion, int minorVersion) {
		return MySQL5DAOUtils.supports(databaseName, majorVersion, minorVersion);
	}

	/**
	 * Reads the settings blob of the current row as an integer.
	 *
	 * @param resultSet row positioned on a numeric settings type
	 * @return the stored value, or 0 when the blob is absent or unreadable
	 * @throws SQLException if the column cannot be read
	 */
	private static int readIntFromBlob(ResultSet resultSet) throws SQLException {
		byte[] raw = resultSet.getBytes("settings");

		if (raw == null || raw.length == 0) {
			return 0;
		}

		try {
			return Integer.parseInt(new String(raw, StandardCharsets.US_ASCII).trim());
		} catch (NumberFormatException e) {
			log.warn("Ignored an unreadable numeric player setting: " + new String(raw, StandardCharsets.US_ASCII));
			return 0;
		}
	}
}
