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

/**
 * Stores how long a player's skills stay on cooldown.
 *
 * @author Oraion
 */
public final class JdbcPlayerSkillCooldownRepository extends AbstractCooldownRepository {

	/**
	 * Keeps a cooldown out of the database when it is nearly over.
	 * <p>
	 * Twenty-eight seconds, matching the DAO: a cooldown shorter than the time it
	 * takes to log back in is not worth a row.
	 */
	private static final long WORTH_STORING_MILLIS = 28_000L;

	public JdbcPlayerSkillCooldownRepository(DataSource dataSource) {
		super(dataSource, "skill");
	}

	@Override
	protected String selectQuery() {
		return "SELECT `cooldown_id`,`reuse_delay` FROM `player_cooldowns` WHERE `player_id` = ?";
	}

	@Override
	protected String deleteQuery() {
		return "DELETE FROM `player_cooldowns` WHERE `player_id` = ?";
	}

	@Override
	protected String insertQuery() {
		return "INSERT INTO `player_cooldowns` (`player_id`,`cooldown_id`,`reuse_delay`) VALUES (?,?,?)";
	}

	@Override
	protected void apply(Player player, ResultSet rows) throws SQLException {
		long now = System.currentTimeMillis();
		while (rows.next()) {
			long reuseDelay = rows.getLong("reuse_delay");
			if (reuseDelay > now) {
				player.setSkillCoolDown(rows.getInt("cooldown_id"), reuseDelay);
			}
		}
	}

	@Override
	protected int queue(Player player, PreparedStatement statement) throws SQLException {
		Map<Integer, Long> cooldowns = player.getSkillCoolDowns();
		if (cooldowns == null) {
			return 0;
		}

		long threshold = System.currentTimeMillis() + WORTH_STORING_MILLIS;
		int queued = 0;
		for (Map.Entry<Integer, Long> cooldown : cooldowns.entrySet()) {
			Long reuseDelay = cooldown.getValue();
			if (reuseDelay == null || reuseDelay.longValue() <= threshold) {
				continue;
			}
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, cooldown.getKey().intValue());
			statement.setLong(3, reuseDelay.longValue());
			statement.addBatch();
			queued++;
		}
		return queued;
	}
}
