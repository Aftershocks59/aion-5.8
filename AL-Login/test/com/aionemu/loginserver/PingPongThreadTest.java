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
package com.aionemu.loginserver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.loginserver.configs.SvStatsConfig;
import com.aionemu.loginserver.network.gameserver.GsConnection;

/**
 * Covers what happens when a game server drops off.
 *
 * @author Oraion
 */
class PingPongThreadTest {

	private boolean publishing;

	@BeforeEach
	void setUp() {
		publishing = SvStatsConfig.SVSTATS_ENABLE;
		SvStatsConfig.SVSTATS_ENABLE = true;
	}

	@AfterEach
	void tearDown() {
		SvStatsConfig.SVSTATS_ENABLE = publishing;
	}

	@Test
	@DisplayName("Lets a game server that never authenticated drop off quietly")
	void letsAnUnauthenticatedServerDropOff() {
		GsConnection connection = mock(GsConnection.class);
		when(connection.getGameServerInfo()).thenReturn(null);
		PingPongThread pingPong = new PingPongThread(connection);

		// A game server that drops before it authenticates has no server info, and
		// this read it straight through, so every such disconnection threw.
		assertDoesNotThrow(pingPong::closeMe);
		assertFalse(pingPong.uptime);
	}

	@Test
	@DisplayName("Stops pinging even when nothing is published")
	void stopsPingingWithoutPublishing() {
		SvStatsConfig.SVSTATS_ENABLE = false;
		GsConnection connection = mock(GsConnection.class);
		PingPongThread pingPong = new PingPongThread(connection);

		pingPong.closeMe();

		assertFalse(pingPong.uptime);
	}
}
