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

/**
 * Stores how long the objects in a player's house stay on cooldown.
 *
 * @author Oraion
 */
public final class JdbcHouseObjectCooldownRepository extends AbstractCooldownRepository {

	/** Drops a cooldown with under a second left, as the DAO's division did. */
	private static final long MINIMUM_REMAINING_MILLIS = 1_000L;

	public JdbcHouseObjectCooldownRepository(DataSource dataSource) {
		super(dataSource, "house object");
	}

	@Override
	protected String selectQuery() {
		return "SELECT `object_id`,`reuse_time` FROM `house_object_cooldowns` WHERE `player_id` = ?";
	}

	@Override
	protected String deleteQuery() {
		return "DELETE FROM `house_object_cooldowns` WHERE `player_id` = ?";
	}

	@Override
	protected String insertQuery() {
		return "INSERT INTO `house_object_cooldowns` (`player_id`,`object_id`,`reuse_time`) VALUES (?,?,?)";
	}

	@Override
	protected void apply(Player player, ResultSet rows) throws SQLException {
		Map<Integer, Long> cooldowns = new LinkedHashMap<Integer, Long>();
		long now = System.currentTimeMillis();
		while (rows.next()) {
			long reuseTime = rows.getLong("reuse_time");
			if (reuseTime - now >= MINIMUM_REMAINING_MILLIS) {
				cooldowns.put(Integer.valueOf(rows.getInt("object_id")), Long.valueOf(reuseTime));
			}
		}
		player.getHouseObjectCooldownList().setHouseObjectCooldowns(cooldowns);
	}

	@Override
	protected int queue(Player player, PreparedStatement statement) throws SQLException {
		Map<Integer, Long> cooldowns = player.getHouseObjectCooldownList().getHouseObjectCooldowns();
		if (cooldowns == null) {
			return 0;
		}

		long now = System.currentTimeMillis();
		int queued = 0;
		for (Map.Entry<Integer, Long> cooldown : cooldowns.entrySet()) {
			Long reuseTime = cooldown.getValue();
			if (reuseTime == null || reuseTime.longValue() < now) {
				continue;
			}
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, cooldown.getKey().intValue());
			statement.setLong(3, reuseTime.longValue());
			statement.addBatch();
			queued++;
		}
		return queued;
	}
}
