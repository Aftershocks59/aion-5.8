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
}
