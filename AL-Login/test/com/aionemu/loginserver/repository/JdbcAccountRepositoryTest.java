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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.model.Account;

/**
 * Covers reading and writing the accounts.
 * <p>
 * Two things the DAO got wrong are pinned down here. Its update declared a
 * result, ran the statement without assigning it, and compared the untouched
 * zero, so it always answered false. And every read swallowed its failure and
 * answered null, which on the table that decides who may log in reads as "no
 * such account" during an outage.
 *
 * @author Oraion
 */
class JdbcAccountRepositoryTest {

	private Connection connection;
	private PreparedStatement statement;
	private JdbcAccountRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(connection.prepareStatement(anyString(), anyInt())).thenReturn(statement);

		repository = new JdbcAccountRepository(dataSource);
	}

	/** Answers one fully populated account row. */
	private ResultSet oneAccountRow() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("id")).thenReturn(42);
		when(rows.getString("name")).thenReturn("test");
		when(rows.getString("password")).thenReturn("$2a$12$hash");
		when(rows.getByte("access_level")).thenReturn((byte) 3);
		when(rows.getByte("membership")).thenReturn((byte) 1);
		when(rows.getByte("activated")).thenReturn((byte) 1);
		when(rows.getByte("last_server")).thenReturn((byte) 1);
		when(rows.getString("last_ip")).thenReturn("127.0.0.1");
		when(rows.getString("last_mac")).thenReturn("00-11-22-33-44-55");
		when(rows.getString("ip_force")).thenReturn(null);
		when(rows.getByte("return_account")).thenReturn((byte) 0);
		when(rows.getTimestamp("return_end")).thenReturn(new Timestamp(0L));
		return rows;
	}

	@Test
	@DisplayName("Maps every column of an account found by name")
	void mapsAnAccountFoundByName() throws SQLException {
		ResultSet rows = oneAccountRow();
		when(statement.executeQuery()).thenReturn(rows);

		Account account = repository.findByName("test");

		assertNotNull(account);
		assertEquals(42, account.getId());
		assertEquals("test", account.getName());
		assertEquals("$2a$12$hash", account.getPasswordHash());
		assertEquals((byte) 3, account.getAccessLevel());
		assertEquals("00-11-22-33-44-55", account.getLastMac());
		verify(statement).setString(1, "test");
	}

	@Test
	@DisplayName("Finds an account by its id")
	void findsAnAccountById() throws SQLException {
		ResultSet rows = oneAccountRow();
		when(statement.executeQuery()).thenReturn(rows);

		assertNotNull(repository.findById(42));
		verify(statement).setInt(1, 42);
	}

	@Test
	@DisplayName("Answers nothing when no account matches")
	void answersNothingWhenAbsent() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertNull(repository.findByName("ghost"));
		assertEquals(AccountRepository.NO_ACCOUNT, repository.findIdByName("ghost"));
		assertNull(repository.findLastIp(42));
	}

	@Test
	@DisplayName("Reports a lookup that failed rather than answering nothing")
	void reportsAFailedLookup() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		RepositoryException failure = assertThrows(RepositoryException.class, () -> repository.findByName("test"));

		assertTrue(failure.getMessage().contains("test"), failure.getMessage());
	}

	@Test
	@DisplayName("Says whether an update landed")
	void reportsWhetherTheUpdateLanded() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);
		Account account = new Account();
		account.setId(42);
		account.setName("test");

		// The DAO answered false here even on success.
		assertTrue(repository.update(account));
		verify(statement).setInt(11, 42);
	}

	@Test
	@DisplayName("Says when an update matched nothing")
	void reportsAnUpdateThatMatchedNothing() throws SQLException {
		when(statement.executeUpdate()).thenReturn(0);
		Account account = new Account();
		account.setId(42);
		account.setName("test");

		assertFalse(repository.update(account));
	}

	@Test
	@DisplayName("Hands a stored account the id the database assigned")
	void fillsInTheGeneratedId() throws SQLException {
		ResultSet keys = mock(ResultSet.class);
		when(keys.next()).thenReturn(true);
		when(keys.getInt(1)).thenReturn(77);
		when(statement.executeUpdate()).thenReturn(1);
		when(statement.getGeneratedKeys()).thenReturn(keys);

		Account account = new Account();
		account.setName("newcomer");

		assertTrue(repository.save(account));
		assertEquals(77, account.getId());
	}

	@Test
	@DisplayName("Counts the registered accounts")
	void countsAccounts() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getInt(1)).thenReturn(1234);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(1234, repository.count());
	}

	@Test
	@DisplayName("Records the last server, address and machine")
	void recordsTheLastConnection() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);

		assertTrue(repository.updateLastServer(42, (byte) 2));
		assertTrue(repository.updateLastIp(42, "10.0.0.1"));
		assertTrue(repository.updateLastMac(42, "00-11-22-33-44-55"));

		verify(statement).setByte(1, (byte) 2);
		verify(statement).setString(1, "10.0.0.1");
		verify(statement).setString(1, "00-11-22-33-44-55");
	}

	@Test
	@DisplayName("Restores a membership only once it has expired")
	void restoresAnExpiredMembership() throws SQLException {
		when(statement.executeUpdate()).thenReturn(0);

		assertFalse(repository.restoreExpiredMembership(42));
		verify(connection).prepareStatement("UPDATE `account_data` SET `membership` = `old_membership`, "
				+ "`expire` = NULL WHERE `id` = ? AND `expire` < CURRENT_TIMESTAMP");
	}

	@Test
	@DisplayName("Counts the inactive accounts it deleted")
	void countsDeletedInactiveAccounts() throws SQLException {
		when(statement.executeUpdate()).thenReturn(9);

		assertEquals(9, repository.deleteInactive(90));
		verify(statement).setInt(1, 90);
	}

	@Test
	@DisplayName("Refuses a null account")
	void refusesANullAccount() {
		assertThrows(IllegalArgumentException.class, () -> repository.save(null));
		assertThrows(IllegalArgumentException.class, () -> repository.update(null));
	}

	@Test
	@DisplayName("Closes the connection even when a query throws")
	void releasesItsResourcesOnFailure() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> repository.count());

		verify(connection).close();
		verify(statement).close();
	}
}
