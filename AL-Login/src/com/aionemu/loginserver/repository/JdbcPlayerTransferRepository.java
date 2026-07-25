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
package com.aionemu.loginserver.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.service.ptransfer.PlayerTransferTask;

/**
 * Reads and writes the character transfers over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerTransferRepository extends JdbcRepositorySupport implements PlayerTransferRepository {

	private static final String SELECT_PENDING = "SELECT `id`,`source_server`,`target_server`,`source_account_id`,"
			+ "`target_account_id`,`player_id` FROM `player_transfers` WHERE `status` = ?";

	/**
	 * Updates a transfer, stamping the moment that matches its new state.
	 * <p>
	 * The DAO built this by pasting a fragment into the middle of the statement,
	 * which produced three different statement texts. Setting both columns from the
	 * status keeps one statement and leaves the untouched one alone.
	 */
	private static final String UPDATE_ONE = "UPDATE `player_transfers` SET `status` = ?, `comment` = ?, "
			+ "`time_performed` = CASE WHEN ? THEN NOW() ELSE `time_performed` END, "
			+ "`time_done` = CASE WHEN ? THEN NOW() ELSE `time_done` END WHERE `id` = ?";

	public JdbcPlayerTransferRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public List<PlayerTransferTask> findPending() {
		List<PlayerTransferTask> pending = new ArrayList<PlayerTransferTask>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_PENDING)) {
			statement.setByte(1, PlayerTransferTask.STATUS_WAIT);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					pending.add(read(rows));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the pending character transfers.", e);
		}
		return pending;
	}

	@Override
	public boolean save(PlayerTransferTask task) {
		if (task == null) {
			throw new IllegalArgumentException("Cannot store a null transfer.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setByte(1, task.status);
			statement.setString(2, task.comment);
			statement.setBoolean(3, task.status == PlayerTransferTask.STATUS_ACTIVE);
			statement.setBoolean(4, task.status == PlayerTransferTask.STATUS_DONE
					|| task.status == PlayerTransferTask.STATUS_ERROR);
			statement.setInt(5, task.id);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to update the character transfer " + task.id + ".", e);
		}
	}

	/** Builds one transfer from the row the cursor sits on. */
	private static PlayerTransferTask read(ResultSet rows) throws SQLException {
		PlayerTransferTask task = new PlayerTransferTask();
		task.id = rows.getInt("id");
		task.sourceServerId = (byte) rows.getShort("source_server");
		task.targetServerId = (byte) rows.getShort("target_server");
		task.sourceAccountId = rows.getInt("source_account_id");
		task.targetAccountId = rows.getInt("target_account_id");
		task.playerId = rows.getInt("player_id");
		return task;
	}
}
