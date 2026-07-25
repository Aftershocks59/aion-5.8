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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.GameServerInfo;

/**
 * Covers reading the registered game servers.
 * <p>
 * An empty list means no game server may register, so the DAO answering empty on
 * a database failure would take the cluster offline with nothing to point at.
 *
 * @author Oraion
 */
class JdbcGameServerRepositoryTest {

	private Connection connection;
	private PreparedStatement statement;
	private JdbcGameServerRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);

		repository = new JdbcGameServerRepository(dataSource);
	}

	@Test
	@DisplayName("Reads every server, keyed by id")
	void readsEveryServer() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getByte("id")).thenReturn((byte) 1, (byte) 2);
		when(rows.getString("mask")).thenReturn("127.0.0.1", "10.0.0.0/8");
		when(rows.getString("password")).thenReturn("secret", "other");
		when(statement.executeQuery()).thenReturn(rows);

		Map<Byte, GameServerInfo> servers = repository.findAll();

		assertEquals(2, servers.size());
		assertTrue(servers.containsKey(Byte.valueOf((byte) 1)));
		assertTrue(servers.containsKey(Byte.valueOf((byte) 2)));
	}

	@Test
	@DisplayName("Answers an empty list when none are registered")
	void readsAnEmptyTable() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertTrue(repository.findAll().isEmpty());
	}

	@Test
	@DisplayName("Reports a read that failed instead of answering empty")
	void reportsAFailedRead() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> repository.findAll());
	}

	@Test
	@DisplayName("Closes the connection even when the query throws")
	void releasesItsResourcesOnFailure() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> repository.findAll());

		verify(connection).close();
		verify(statement).close();
	}

	@Test
	@DisplayName("Names the columns it reads")
	void namesItsColumns() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		repository.findAll();

		// SELECT * would silently pick up a column added later and change the row
		// shape under the mapping.
		verify(connection).prepareStatement("SELECT `id`,`mask`,`password` FROM `gameservers`");
	}
}
