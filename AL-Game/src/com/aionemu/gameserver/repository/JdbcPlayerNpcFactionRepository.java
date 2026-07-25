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
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.npcFaction.ENpcFactionQuestState;
import com.aionemu.gameserver.model.gameobjects.player.npcFaction.NpcFaction;
import com.aionemu.gameserver.model.gameobjects.player.npcFaction.NpcFactions;

/**
 * Reads and writes a character's npc faction standings over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerNpcFactionRepository extends JdbcRepositorySupport
		implements PlayerNpcFactionRepository {

	private static final String SELECT_ALL = "SELECT `faction_id`,`active`,`time`,`state`,`quest_id` "
			+ "FROM `player_npc_factions` WHERE `player_id` = ?";

	/**
	 * Writes a standing whether the character had one or not.
	 * <p>
	 * The DAO kept an insert and an update apart and chose between them from an
	 * in-memory flag. The table is keyed on the character and the faction, so one
	 * statement covers both and a stale flag can no longer lose a write.
	 */
	private static final String UPSERT_ONE = "INSERT INTO `player_npc_factions` "
			+ "(`player_id`,`faction_id`,`active`,`time`,`state`,`quest_id`) VALUES (?,?,?,?,?,?) "
			+ "ON DUPLICATE KEY UPDATE `active` = VALUES(`active`), `time` = VALUES(`time`), "
			+ "`state` = VALUES(`state`), `quest_id` = VALUES(`quest_id`)";

	public JdbcPlayerNpcFactionRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		NpcFactions factions = new NpcFactions(player);
		player.setNpcFactions(factions);

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					NpcFaction faction = new NpcFaction(rows.getInt("faction_id"), rows.getInt("time"),
							rows.getBoolean("active"), ENpcFactionQuestState.valueOf(rows.getString("state")),
							rows.getInt("quest_id"));
					faction.setPersistentState(PersistentState.UPDATED);
					factions.addNpcFaction(faction);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the npc factions of character " + player.getObjectId() + ".", e);
		}
	}

	@Override
	public void store(Player player) {
		// One connection and one transaction for every faction that changed, where
		// the DAO borrowed a connection per faction.
		inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(UPSERT_ONE)) {
				int queued = 0;
				for (NpcFaction faction : player.getNpcFactions().getNpcFactions()) {
					if (faction.getPersistentState() != PersistentState.NEW
							&& faction.getPersistentState() != PersistentState.UPDATE_REQUIRED) {
						continue;
					}
					statement.setInt(1, player.getObjectId());
					statement.setInt(2, faction.getId());
					statement.setBoolean(3, faction.isActive());
					statement.setInt(4, faction.getTime());
					statement.setString(5, faction.getState().name());
					statement.setInt(6, faction.getQuestId());
					statement.addBatch();
					queued++;
				}
				if (queued > 0) {
					statement.executeBatch();
				}
			}
			return null;
		}, "Failed to write the npc factions of character " + player.getObjectId() + ".");
	}
}
