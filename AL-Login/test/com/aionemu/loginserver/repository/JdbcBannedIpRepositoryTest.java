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
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.model.BannedIP;

/**
 * Covers storing and reading the address bans.
 * <p>
 * The DAO read the generated id back into a local object it discarded, so the
 * caller's ban kept a null id. BannedIpController decides between storing and
 * updating on exactly that, so re-banning an address inserted a second row
 * instead of amending the first.
 *
 * @author Oraion
 */
class JdbcBannedIpRepositoryTest {

	private Connection connection;
	private PreparedStatement statement;
	private JdbcBannedIpRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(connection.prepareStatement(anyString(), anyInt())).thenReturn(statement);

		repository = new JdbcBannedIpRepository(dataSource);
	}

	/** Makes the insert succeed and hand back the given id. */
	private void stubGeneratedKey(int id) throws SQLException {
		ResultSet keys = mock(ResultSet.class);
		when(keys.next()).thenReturn(true);
		when(keys.getInt(1)).thenReturn(id);
		when(statement.executeUpdate()).thenReturn(1);
		when(statement.getGeneratedKeys()).thenReturn(keys);
	}

	@Test
	@DisplayName("Hands the stored ban the id the database assigned")
	void fillsInTheGeneratedId() throws SQLException {
		stubGeneratedKey(17);
		BannedIP ban = new BannedIP();
		ban.setMask("10.0.0.1");

		assertTrue(repository.save(ban));

		assertEquals(Integer.valueOf(17), ban.getId());
		verify(connection).prepareStatement(anyString(), org.mockito.ArgumentMatchers.eq(Statement.RETURN_GENERATED_KEYS));
	}

	@Test
	@DisplayName("Answers the stored ban when banning a mask")
	void bansAMask() throws SQLException {
		stubGeneratedKey(5);

		BannedIP ban = repository.ban("10.0.0.1", new Timestamp(1_000L));

		assertNotNull(ban);
		assertEquals("10.0.0.1", ban.getMask());
		assertEquals(Integer.valueOf(5), ban.getId());
	}

	@Test
	@DisplayName("Writes a typed null for a permanent ban")
	void writesATypedNullForAPermanentBan() throws SQLException {
		stubGeneratedKey(1);

		repository.ban("10.0.0.1", null);

		verify(statement).setNull(2, Types.TIMESTAMP);
	}

	@Test
	@DisplayName("Answers nothing when the ban was refused")
	void answersNothingWhenRefused() throws SQLException {
		when(statement.executeUpdate()).thenReturn(0);

		assertNull(repository.ban("10.0.0.1", null));
	}

	@Test
	@DisplayName("Reads every ban with its id and end date")
	void readsEveryBan() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("id")).thenReturn(1, 2);
		when(rows.getString("mask")).thenReturn("10.0.0.1", "10.0.0.2");
		when(rows.getTimestamp("time_end")).thenReturn(null);
		when(statement.executeQuery()).thenReturn(rows);

		Set<BannedIP> bans = repository.findAll();

		assertEquals(2, bans.size());
	}

	@Test
	@DisplayName("Reports a read that failed instead of lifting every ban")
	void reportsAFailedRead() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> repository.findAll());
		verify(connection).close();
	}

	@Test
	@DisplayName("Updates an existing ban by its id")
	void updatesById() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);
		BannedIP ban = new BannedIP();
		ban.setId(Integer.valueOf(17));
		ban.setMask("10.0.0.1");
		ban.setTimeEnd(new Timestamp(2_000L));

		assertTrue(repository.update(ban));

		verify(statement).setString(1, "10.0.0.1");
		verify(statement).setTimestamp(2, new Timestamp(2_000L));
		verify(statement).setInt(3, 17);
	}

	@Test
	@DisplayName("Says whether a ban was actually lifted")
	void reportsWhetherRemovalMatched() throws SQLException {
		when(statement.executeUpdate()).thenReturn(0);

		assertFalse(repository.remove("10.0.0.1"));
	}

	@Test
	@DisplayName("Leaves the permanent bans when dropping the expired ones")
	void keepsPermanentBans() throws SQLException {
		when(statement.executeUpdate()).thenReturn(3);

		assertEquals(3, repository.removeExpired());
		verify(connection).prepareStatement("DELETE FROM `banned_ip` "
				+ "WHERE `time_end` < CURRENT_TIMESTAMP AND `time_end` IS NOT NULL");
	}

	@Test
	@DisplayName("Refuses a null ban")
	void refusesANullBan() {
		assertThrows(IllegalArgumentException.class, () -> repository.save(null));
		assertThrows(IllegalArgumentException.class, () -> repository.update(null));
	}
}
