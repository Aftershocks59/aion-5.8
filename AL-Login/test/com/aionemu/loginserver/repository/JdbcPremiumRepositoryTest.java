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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;

/**
 * Covers the premium balances.
 * <p>
 * Two behaviours are pinned down here because they were carried across on
 * purpose rather than corrected: only one pending reward is claimed per call,
 * and the claimed points are never added to account_data. Both belong to a
 * currency protocol that spans the login and game servers, so a test saying what
 * happens today is what a later decision to change it will be measured against.
 *
 * @author Oraion
 */
class JdbcPremiumRepositoryTest {

	private Connection connection;
	private PreparedStatement statement;
	private JdbcPremiumRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);

		repository = new JdbcPremiumRepository(dataSource);
	}

	/** Answers the toll, then no pending reward. */
	private void stubBalanceOnlyWith(long toll) throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getLong("toll")).thenReturn(toll);
		when(statement.executeQuery()).thenReturn(rows);
	}

	@Test
	@DisplayName("Answers the stored balance when nothing is pending")
	void readsTheStoredBalance() throws SQLException {
		stubBalanceOnlyWith(500L);

		assertEquals(500L, repository.claimAndGetPoints(42));
	}

	@Test
	@DisplayName("Answers zero when the account has no row")
	void answersZeroWhenAbsent() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(0L, repository.claimAndGetPoints(42));
	}

	@Test
	@DisplayName("Adds a pending reward and marks it received")
	void claimsOnePendingReward() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getLong("toll")).thenReturn(500L);
		when(rows.getInt("uniqId")).thenReturn(9);
		when(rows.getLong("points")).thenReturn(250L);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(750L, repository.claimAndGetPoints(42));

		verify(connection).prepareStatement(contains("SET `rewarded` = 1"));
		verify(statement).setInt(1, 9);
	}

	@Test
	@DisplayName("Leaves the stored balance untouched while claiming")
	void neverWritesTheClaimedTotalBack() throws SQLException {
		stubBalanceOnlyWith(500L);

		repository.claimAndGetPoints(42);

		// Preserved from the DAO: the balance travels to the game server and is only
		// written down when it comes back. Changing that is a protocol decision.
		verify(connection, never()).prepareStatement("UPDATE `account_data` SET `toll` = ? WHERE `id` = ?");
	}

	@Test
	@DisplayName("Subtracts the cost when points are spent")
	void spendsPoints() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(repository.spendPoints(42, 500L, 200L));

		verify(statement).setLong(1, 300L);
		verify(statement).setInt(2, 42);
	}

	@Test
	@DisplayName("Sets the luna balance as given")
	void setsLuna() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(repository.setLuna(42, 99L));

		verify(statement).setLong(1, 99L);
		verify(statement).setInt(2, 42);
	}

	@Test
	@DisplayName("Reports a balance that could not be read")
	void reportsAFailedRead() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		RepositoryException failure = assertThrows(RepositoryException.class, () -> repository.getLuna(42));

		assertTrue(failure.getMessage().contains("42"), failure.getMessage());
	}

	@Test
	@DisplayName("Reports a balance that could not be written")
	void reportsAFailedWrite() throws SQLException {
		when(statement.executeUpdate()).thenThrow(new SQLException("table is read only"));

		assertThrows(RepositoryException.class, () -> repository.spendPoints(42, 1L, 0L));
		verify(connection).close();
	}

	@Test
	@DisplayName("Uses one connection for the whole claim")
	void usesOneConnection() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getLong("toll")).thenReturn(0L);
		when(rows.getInt("uniqId")).thenReturn(9);
		when(rows.getLong("points")).thenReturn(1L);
		when(statement.executeQuery()).thenReturn(rows);

		repository.claimAndGetPoints(42);

		// The DAO borrowed three connections for these three statements.
		verify(connection).close();
	}
}
