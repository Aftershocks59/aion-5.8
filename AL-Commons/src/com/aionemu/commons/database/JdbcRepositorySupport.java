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
package com.aionemu.commons.database;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * Holds what every JDBC repository needs: the pool it borrows from.
 * <p>
 * Taking the data source rather than reaching for a static one is what lets a
 * repository be exercised without a database. There are eighty-three of these to
 * write, so the check belongs in one place.
 *
 * @author Oraion
 */
public abstract class JdbcRepositorySupport {

	private final DataSource dataSource;

	protected JdbcRepositorySupport(DataSource dataSource) {
		if (dataSource == null) {
			throw new IllegalArgumentException("A repository needs a data source.");
		}
		this.dataSource = dataSource;
	}

	/**
	 * Borrows a connection from the pool.
	 * <p>
	 * Always close it, which means always inside a try-with-resources: a leaked
	 * connection drains the pool and strands the server hours later, far from the
	 * call that lost it.
	 *
	 * @return a pooled connection
	 * @throws SQLException if the pool cannot hand one out
	 */
	protected final Connection connection() throws SQLException {
		return dataSource.getConnection();
	}

	/** Work to run against one connection, allowed to fail the way JDBC does. */
	@FunctionalInterface
	public interface ConnectionWork<T> {

		T run(Connection connection) throws SQLException;
	}

	/**
	 * Runs work inside a transaction, committing it or rolling it all back.
	 * <p>
	 * Several DAO methods replaced a player's rows by deleting them and inserting
	 * the new ones on two separate connections. Nothing tied the two together, so a
	 * failure between them left the player with nothing where they had something.
	 * <p>
	 * Restores auto-commit before handing the connection back. The pool resets it
	 * too, but a connection returned mid-transaction is a trap for whoever borrows
	 * it next.
	 *
	 * @param work        what to do with the connection
	 * @param description what to say if it fails
	 * @return whatever the work returned
	 * @throws RepositoryException if the work failed, after rolling back
	 */
	protected final <T> T inTransaction(ConnectionWork<T> work, String description) {
		Connection connection = null;
		try {
			connection = connection();
			connection.setAutoCommit(false);
			try {
				T result = work.run(connection);
				connection.commit();
				return result;
			} catch (SQLException e) {
				connection.rollback();
				throw e;
			} finally {
				connection.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw new RepositoryException(description, e);
		} finally {
			closeQuietly(connection);
		}
	}

	/** Closes a connection, swallowing the failure that closing itself reports. */
	private static void closeQuietly(Connection connection) {
		if (connection == null) {
			return;
		}
		try {
			connection.close();
		} catch (SQLException ignored) {
			// Nothing useful is left to do: the work has already been settled.
		}
	}
}
