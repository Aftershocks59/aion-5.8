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
import java.util.Map;

import javax.sql.DataSource;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemCooldown;

/**
 * Stores how long a player's items stay on cooldown.
 *
 * @author Oraion
 */
public final class JdbcItemCooldownRepository extends AbstractCooldownRepository {

	/** Keeps a cooldown out of the database when it is nearly over, as the DAO did. */
	private static final long WORTH_STORING_MILLIS = 30_000L;

	public JdbcItemCooldownRepository(DataSource dataSource) {
		super(dataSource, "item");
	}

	@Override
	protected String selectQuery() {
		return "SELECT `delay_id`,`use_delay`,`reuse_time` FROM `item_cooldowns` WHERE `player_id` = ?";
	}

	@Override
	protected String deleteQuery() {
		return "DELETE FROM `item_cooldowns` WHERE `player_id` = ?";
	}

	@Override
	protected String insertQuery() {
		return "INSERT INTO `item_cooldowns` (`player_id`,`delay_id`,`use_delay`,`reuse_time`) VALUES (?,?,?,?)";
	}

	@Override
	protected void apply(Player player, ResultSet rows) throws SQLException {
		long now = System.currentTimeMillis();
		while (rows.next()) {
			long reuseTime = rows.getLong("reuse_time");
			if (reuseTime > now) {
				player.addItemCoolDown(rows.getInt("delay_id"), reuseTime, rows.getInt("use_delay"));
			}
		}
	}

	@Override
	protected void afterLoad(Player player) {
		// Tell the client about the effects the cooldowns just restored.
		player.getEffectController().broadCastEffects();
	}

	@Override
	protected int queue(Player player, PreparedStatement statement) throws SQLException {
		Map<Integer, ItemCooldown> cooldowns = player.getItemCoolDowns();
		if (cooldowns == null) {
			return 0;
		}

		long threshold = System.currentTimeMillis() + WORTH_STORING_MILLIS;
		int queued = 0;
		for (Map.Entry<Integer, ItemCooldown> cooldown : cooldowns.entrySet()) {
			ItemCooldown value = cooldown.getValue();
			if (value == null || value.getReuseTime() <= threshold) {
				continue;
			}
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, cooldown.getKey().intValue());
			statement.setInt(3, value.getUseDelay());
			statement.setLong(4, value.getReuseTime());
			statement.addBatch();
			queued++;
		}
		return queued;
	}
}
