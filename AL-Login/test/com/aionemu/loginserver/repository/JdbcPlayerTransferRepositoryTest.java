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
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.service.ptransfer.PlayerTransferTask;

/**
 * Covers reading and writing the character transfers.
 * <p>
 * The DAO built its update by pasting a fragment into the middle of the
 * statement, producing three different texts depending on the status. The
 * timestamps now come from the status itself, through one statement.
 *
 * @author Oraion
 */
class JdbcPlayerTransferRepositoryTest {

	private Connection connection;
	private PreparedStatement statement;
	private JdbcPlayerTransferRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		DataSource dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);

		repository = new JdbcPlayerTransferRepository(dataSource);
	}

	@Test
	@DisplayName("Reads only the transfers nobody has started")
	void asksForWaitingTransfersOnly() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertTrue(repository.findPending().isEmpty());
		verify(statement).setByte(1, PlayerTransferTask.STATUS_WAIT);
	}

	@Test
	@DisplayName("Maps a pending transfer onto its task")
	void mapsATransfer() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, false);
		when(rows.getInt("id")).thenReturn(7);
		when(rows.getShort("source_server")).thenReturn((short) 1);
		when(rows.getShort("target_server")).thenReturn((short) 2);
		when(rows.getInt("source_account_id")).thenReturn(100);
		when(rows.getInt("target_account_id")).thenReturn(200);
		when(rows.getInt("player_id")).thenReturn(300);
		when(statement.executeQuery()).thenReturn(rows);

		List<PlayerTransferTask> pending = repository.findPending();

		assertEquals(1, pending.size());
		assertEquals(7, pending.get(0).id);
		assertEquals((byte) 1, pending.get(0).sourceServerId);
		assertEquals((byte) 2, pending.get(0).targetServerId);
		assertEquals(300, pending.get(0).playerId);
	}

	@Test
	@DisplayName("Stamps the start time when a transfer becomes active")
	void stampsTheStartOfAnActiveTransfer() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);
		PlayerTransferTask task = new PlayerTransferTask();
		task.id = 7;
		task.status = PlayerTransferTask.STATUS_ACTIVE;

		assertTrue(repository.save(task));

		verify(statement).setBoolean(3, true);
		verify(statement).setBoolean(4, false);
	}

	@Test
	@DisplayName("Stamps the end time when a transfer finishes or fails")
	void stampsTheEndOfAFinishedTransfer() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);
		PlayerTransferTask task = new PlayerTransferTask();
		task.id = 7;
		task.status = PlayerTransferTask.STATUS_ERROR;

		repository.save(task);

		verify(statement).setBoolean(3, false);
		verify(statement).setBoolean(4, true);
	}

	@Test
	@DisplayName("Stamps neither while a transfer is still waiting")
	void stampsNothingWhileWaiting() throws SQLException {
		when(statement.executeUpdate()).thenReturn(1);
		PlayerTransferTask task = new PlayerTransferTask();
		task.id = 7;
		task.status = PlayerTransferTask.STATUS_WAIT;

		repository.save(task);

		verify(statement).setBoolean(3, false);
		verify(statement).setBoolean(4, false);
	}

	@Test
	@DisplayName("Refuses to store a null transfer")
	void refusesANullTransfer() {
		assertThrows(IllegalArgumentException.class, () -> repository.save(null));
	}

	@Test
	@DisplayName("Reports a read that failed")
	void reportsAFailedRead() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> repository.findPending());
		verify(connection).close();
	}
}
