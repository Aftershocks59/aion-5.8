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
import com.aionemu.gameserver.model.templates.survey.SurveyItem;

/**
 * Reads and marks the queued deliveries over JDBC.
 *
 * @author Oraion
 */
public final class JdbcSurveyRepository extends JdbcRepositorySupport implements SurveyRepository {

	/** Marks a row as not yet delivered. */
	private static final int PENDING = 0;

	/** Marks a row as delivered. */
	private static final int DELIVERED = 1;

	private static final String SELECT_PENDING = "SELECT `unique_id`,`owner_id`,`item_id`,`item_count`,"
			+ "`html_text`,`html_radio` FROM `surveys` WHERE `used` = ?";
	private static final String MARK_DELIVERED = "UPDATE `surveys` SET `used` = ?, `used_time` = NOW() WHERE `unique_id` = ?";

	public JdbcSurveyRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public List<SurveyItem> findPending() {
		List<SurveyItem> pending = new ArrayList<SurveyItem>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_PENDING)) {
			statement.setInt(1, PENDING);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					SurveyItem item = new SurveyItem();
					item.uniqueId = rows.getInt("unique_id");
					item.ownerId = rows.getInt("owner_id");
					item.itemId = rows.getInt("item_id");
					item.count = rows.getLong("item_count");
					item.html = rows.getString("html_text");
					item.radio = rows.getString("html_radio");
					pending.add(item);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the pending deliveries.", e);
		}
		return pending;
	}

	@Override
	public boolean markDelivered(int uniqueId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(MARK_DELIVERED)) {
			statement.setInt(1, DELIVERED);
			statement.setInt(2, uniqueId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to mark delivery " + uniqueId + " as done.", e);
		}
	}
}
