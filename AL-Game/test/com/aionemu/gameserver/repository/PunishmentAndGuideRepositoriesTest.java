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
package com.aionemu.gameserver.repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
import com.aionemu.gameserver.model.account.CharacterBanInfo;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.PunishmentService.PunishmentType;

/**
 * Covers the punishments a character serves and the guide notes it receives.
 * <p>
 * The punishment table keeps seconds where the game keeps milliseconds, so every
 * duration is multiplied or divided by a thousand as it crosses. Getting that
 * wrong in either direction changes a sentence by a factor of a thousand, which
 * is what the tests below hold in place.
 *
 * @author Oraion
 */
class PunishmentAndGuideRepositoriesTest {

	private DataSource dataSource;
	private Connection connection;
	private PreparedStatement statement;
	private Player player;

	@BeforeEach
	void setUp() throws SQLException {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);
		player = mock(Player.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(player.getObjectId()).thenReturn(42);
	}

	@Test
	@DisplayName("Turns the stored seconds back into milliseconds on the way in")
	void restoresAPrisonSentence() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getLong("duration")).thenReturn(600L);
		when(statement.executeQuery()).thenReturn(rows);

		new JdbcPlayerPunishmentRepository(dataSource).load(player, PunishmentType.PRISON);

		verify(player).setPrisonTimer(600_000L);
	}

	@Test
	@DisplayName("Turns the milliseconds back into seconds on the way out")
	void storesAPrisonSentence() throws SQLException {
		when(player.getPrisonTimer()).thenReturn(600_000L);

		new JdbcPlayerPunishmentRepository(dataSource).save(player, PunishmentType.PRISON);

		verify(statement).setLong(1, 600L);
		verify(statement).setString(3, "PRISON");
	}

	@Test
	@DisplayName("Takes off the time already served from a gathering ban")
	void deductsTimeAlreadyServed() throws SQLException {
		when(player.getGatherableTimer()).thenReturn(600_000L);
		when(player.getStopGatherable()).thenReturn(System.currentTimeMillis() - 100_000L);

		new JdbcPlayerPunishmentRepository(dataSource).save(player, PunishmentType.GATHER);

		// Roughly five hundred seconds left; allow a second either way for the clock.
		verify(statement).setLong(org.mockito.ArgumentMatchers.eq(1),
				org.mockito.ArgumentMatchers.longThat(seconds -> seconds >= 499L && seconds <= 501L));
	}

	@Test
	@DisplayName("Answers nothing when a character is not banned")
	void answersNothingForAnUnbannedCharacter() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertNull(new JdbcPlayerPunishmentRepository(dataSource).findBan(42));
		verify(statement).setString(2, "CHARBAN");
	}

	@Test
	@DisplayName("Reads a ban with when it started and how long it lasts")
	void readsABan() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true);
		when(rows.getLong("start_time")).thenReturn(1_000L);
		when(rows.getLong("duration")).thenReturn(86_400L);
		when(rows.getString("reason")).thenReturn("cheating");
		when(statement.executeQuery()).thenReturn(rows);

		CharacterBanInfo ban = new JdbcPlayerPunishmentRepository(dataSource).findBan(42);

		assertNotNull(ban);
		assertEquals("cheating", ban.getReason());
	}

	@Test
	@DisplayName("Reports a punishment it could not read")
	void reportsAnUnreadablePunishment() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class,
				() -> new JdbcPlayerPunishmentRepository(dataSource).load(player, PunishmentType.PRISON));
		verify(connection).close();
	}

	@Test
	@DisplayName("Lists the guide ids already handed out")
	void listsUsedGuideIds() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(true, true, false);
		when(rows.getInt("guide_id")).thenReturn(11, 22);
		when(statement.executeQuery()).thenReturn(rows);

		// The id factory locks these at startup, so an empty answer would hand out an
		// id somebody already holds.
		assertArrayEquals(new int[] { 11, 22 }, new JdbcGuideRepository(dataSource).findUsedIds());
	}

	@Test
	@DisplayName("Reports guide ids it could not list rather than answering none")
	void reportsUnlistableGuideIds() throws SQLException {
		when(statement.executeQuery()).thenThrow(new SQLException("connection lost"));

		assertThrows(RepositoryException.class, () -> new JdbcGuideRepository(dataSource).findUsedIds());
	}

	@Test
	@DisplayName("Answers nothing for a guide note a character does not hold")
	void answersNothingForAMissingGuide() throws SQLException {
		ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenReturn(false);
		when(statement.executeQuery()).thenReturn(rows);

		assertNull(new JdbcGuideRepository(dataSource).find(42, 7));
	}
}
