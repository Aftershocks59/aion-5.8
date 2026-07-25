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
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.templates.rewards.RewardEntryItem;

/**
 * Reads and writes what the web shop owes a character, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcWebRewardRepository extends JdbcRepositorySupport implements WebRewardRepository {

	private static final String SELECT_UNCLAIMED = "SELECT `unique`,`item_id`,`item_count` FROM `web_reward`"
			+ " WHERE `item_owner` = ? AND `rewarded` = 0";
	private static final String MARK_CLAIMED = "UPDATE `web_reward` SET `rewarded` = 1, `received` = NOW() WHERE `unique` = ?";
	private static final String MARK_UNCLAIMED = "UPDATE `web_reward` SET `rewarded` = 0 WHERE `unique` = ?";

	public JdbcWebRewardRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public List<RewardEntryItem> findUnclaimed(int playerId) {
		List<RewardEntryItem> owed = new ArrayList<RewardEntryItem>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_UNCLAIMED)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					owed.add(new RewardEntryItem(rows.getInt("unique"), rows.getInt("item_id"),
							rows.getLong("item_count")));
				}
			}
		} catch (SQLException e) {
			// The DAO swallowed this, so a character was told they were owed
			// nothing whenever the read failed.
			throw new RepositoryException("Failed to read what the web shop owes character " + playerId + ".", e);
		}

		return owed;
	}

	@Override
	public boolean markClaimed(int rewardId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(MARK_CLAIMED)) {
			statement.setInt(1, rewardId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record that web reward " + rewardId + " was handed over.", e);
		}
	}

	@Override
	public void markClaimed(List<Integer> rewardIds) {
		if (rewardIds == null || rewardIds.isEmpty()) {
			return;
		}

		// One transaction over one statement, where the DAO prepared a fresh
		// statement per row and could leave half the batch unmarked.
		inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(MARK_CLAIMED)) {
				for (Integer rewardId : rewardIds) {
					statement.setInt(1, rewardId.intValue());
					statement.addBatch();
				}
				statement.executeBatch();
			}
			return null;
		}, "Failed to record that " + rewardIds.size() + " web rewards were handed over.");
	}

	@Override
	public boolean markUnclaimed(int rewardId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(MARK_UNCLAIMED)) {
			statement.setInt(1, rewardId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to put web reward " + rewardId + " back on the pile.", e);
		}
	}
}
