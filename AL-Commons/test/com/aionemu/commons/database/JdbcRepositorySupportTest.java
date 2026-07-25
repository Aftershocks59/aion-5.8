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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Covers the transaction the repositories share.
 * <p>
 * Several DAO methods replaced a player's rows by deleting them on one
 * connection and inserting the new ones on another. Nothing tied the two
 * together, so a failure in between left the player holding nothing where they
 * had something. What this settles is that the work either lands whole or not at
 * all, and that the connection goes back to the pool usable.
 *
 * @author Oraion
 */
class JdbcRepositorySupportTest {

	/** Exposes the protected helper so it can be exercised directly. */
	private static final class TestRepository extends JdbcRepositorySupport {

		TestRepository(DataSource dataSource) {
			super(dataSource);
		}

		<T> T run(ConnectionWork<T> work) {
			return inTransaction(work, "The test work failed.");
		}
	}

	private Connection connection;
	private TestRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		when(dataSource.getConnection()).thenReturn(connection);
		repository = new TestRepository(dataSource);
	}

	@Test
	@DisplayName("Commits work that succeeded")
	void commitsOnSuccess() throws SQLException {
		assertEquals("done", repository.run(c -> "done"));

		InOrder order = inOrder(connection);
		order.verify(connection).setAutoCommit(false);
		order.verify(connection).commit();
		order.verify(connection).setAutoCommit(true);
		order.verify(connection).close();
		verify(connection, never()).rollback();
	}

	@Test
	@DisplayName("Rolls everything back when the work fails")
	void rollsBackOnFailure() throws SQLException {
		assertThrows(RepositoryException.class, () -> repository.run(c -> {
			throw new SQLException("constraint violated");
		}));

		verify(connection).rollback();
		verify(connection, never()).commit();
	}

	@Test
	@DisplayName("Hands the connection back usable after a rollback")
	void restoresAutoCommitAfterFailure() throws SQLException {
		assertThrows(RepositoryException.class, () -> repository.run(c -> {
			throw new SQLException("constraint violated");
		}));

		// A connection returned mid-transaction traps whoever borrows it next.
		verify(connection).setAutoCommit(true);
		verify(connection).close();
	}

	@Test
	@DisplayName("Carries the reason the work failed")
	void carriesTheDescription() {
		RepositoryException failure = assertThrows(RepositoryException.class, () -> repository.run(c -> {
			throw new SQLException("constraint violated");
		}));

		assertEquals("The test work failed.", failure.getMessage());
		assertEquals("constraint violated", failure.getCause().getMessage());
	}

	@Test
	@DisplayName("Closes the connection even when closing itself fails")
	void survivesAFailingClose() throws SQLException {
		org.mockito.Mockito.doThrow(new SQLException("already gone")).when(connection).close();

		// The work settled; a failure to hand the connection back must not surface
		// as the operation having failed.
		assertEquals("done", repository.run(c -> "done"));
	}

	@Test
	@DisplayName("Refuses to be built without a data source")
	void refusesANullDataSource() {
		assertThrows(IllegalArgumentException.class, () -> new TestRepository(null));
	}
}
