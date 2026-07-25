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
import java.util.LinkedHashSet;
import java.util.Set;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.veteranrewards.VeteranRewards;

/**
 * Reads and removes the queued veteran rewards over JDBC.
 *
 * @author Oraion
 */
public final class JdbcVeteranRewardRepository extends JdbcRepositorySupport
		implements VeteranRewardRepository {

	private static final String SELECT_ALL = "SELECT `id`,`player`,`type`,`item`,`count`,`kinah`,`sender`,`title`,`message` "
			+ "FROM `veteran_rewards` ORDER BY `id`";
	private static final String DELETE_ONE = "DELETE FROM `veteran_rewards` WHERE `id` = ?";

	public JdbcVeteranRewardRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Set<VeteranRewards> findAll() {
		Set<VeteranRewards> rewards = new LinkedHashSet<VeteranRewards>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				rewards.add(new VeteranRewards(rows.getInt("id"), rows.getString("player"), rows.getInt("type"),
						rows.getInt("item"), rows.getInt("count"), rows.getInt("kinah"), rows.getString("sender"),
						rows.getString("title"), rows.getString("message")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the queued veteran rewards.", e);
		}
		return rewards;
	}

	@Override
	public boolean remove(int rewardId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, rewardId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to remove veteran reward " + rewardId + ".", e);
		}
	}
}
