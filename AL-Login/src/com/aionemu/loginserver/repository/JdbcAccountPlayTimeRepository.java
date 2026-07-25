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
import java.sql.SQLException;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;

/**
 * Records accumulated play time over JDBC.
 *
 * @author Oraion
 */
public final class JdbcAccountPlayTimeRepository extends JdbcRepositorySupport implements AccountPlayTimeRepository {

	/**
	 * Adds to the running total, inserting the row the first time.
	 * <p>
	 * The DAO this replaces pasted both numbers straight into the statement text.
	 * They were integers, so nothing could be smuggled through, but every call
	 * built a statement the database had never seen and could not reuse.
	 */
	private static final String ACCUMULATE = "INSERT INTO account_playtime (`account_id`,`accumulated_online`) VALUES (?,?) "
			+ "ON DUPLICATE KEY UPDATE `accumulated_online` = `accumulated_online` + ?";

	public JdbcAccountPlayTimeRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public boolean accumulate(int accountId, long seconds) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(ACCUMULATE)) {
			statement.setInt(1, accountId);
			statement.setLong(2, seconds);
			statement.setLong(3, seconds);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record the play time of account " + accountId + ".", e);
		}
	}
}
