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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PortalCooldownItem;

/**
 * Stores how long a player must wait before entering an instance again, and how
 * many times they have been in.
 *
 * @author Oraion
 */
public final class JdbcPortalCooldownRepository extends AbstractCooldownRepository {

	public JdbcPortalCooldownRepository(DataSource dataSource) {
		super(dataSource, "portal");
	}

	@Override
	protected String selectQuery() {
		return "SELECT `world_id`,`reuse_time`,`entry_count` FROM `portal_cooldowns` WHERE `player_id` = ?";
	}

	@Override
	protected String deleteQuery() {
		return "DELETE FROM `portal_cooldowns` WHERE `player_id` = ?";
	}

	@Override
	protected String insertQuery() {
		return "INSERT INTO `portal_cooldowns` (`player_id`,`world_id`,`reuse_time`,`entry_count`) VALUES (?,?,?,?)";
	}

	@Override
	protected void apply(Player player, ResultSet rows) throws SQLException {
		Map<Integer, PortalCooldownItem> cooldowns = new LinkedHashMap<Integer, PortalCooldownItem>();
		long now = System.currentTimeMillis();
		while (rows.next()) {
			int worldId = rows.getInt("world_id");
			long reuseTime = rows.getLong("reuse_time");
			if (reuseTime > now) {
				cooldowns.put(Integer.valueOf(worldId),
						new PortalCooldownItem(worldId, rows.getInt("entry_count"), reuseTime));
			}
		}
		player.getPortalCooldownList().setPortalCoolDowns(cooldowns);
	}

	@Override
	protected int queue(Player player, PreparedStatement statement) throws SQLException {
		Map<Integer, PortalCooldownItem> cooldowns = player.getPortalCooldownList().getPortalCoolDowns();
		if (cooldowns == null) {
			return 0;
		}

		long now = System.currentTimeMillis();
		int queued = 0;
		for (Map.Entry<Integer, PortalCooldownItem> cooldown : cooldowns.entrySet()) {
			PortalCooldownItem value = cooldown.getValue();
			if (value == null || value.getCooldown() < now) {
				continue;
			}
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, cooldown.getKey().intValue());
			statement.setLong(3, value.getCooldown());
			statement.setInt(4, value.getEntryCount());
			statement.addBatch();
			queued++;
		}
		return queued;
	}
}
