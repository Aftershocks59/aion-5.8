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
package com.aionemu.commons.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Covers what the Galera probe filter hides and, more importantly, what it does
 * not.
 * <p>
 * A filter over a driver's error logger is one careless line away from hiding
 * every SQL failure the server would ever report, so the cases that must still
 * get through are pinned down here alongside the one that must not.
 *
 * @author Oraion
 */
class GaleraProbeFilterTest {

	private static final String PROBE_MESSAGE = "Error: 1193-HY000: Unknown system variable 'WSREP_ON'";

	private LoggerContext context;
	private GaleraProbeFilter filter;

	@BeforeEach
	void setUp() {
		context = new LoggerContext();
		filter = new GaleraProbeFilter();
	}

	private Logger logger(String name) {
		return context.getLogger(name);
	}

	private FilterReply decide(String loggerName, String message) {
		return filter.decide(null, logger(loggerName), Level.WARN, message, null, null);
	}

	@Test
	@DisplayName("Hides the Galera probe the driver reports")
	void hidesTheProbe() {
		assertEquals(FilterReply.DENY, decide(GaleraProbeFilter.DRIVER_ERROR_LOGGER, PROBE_MESSAGE));
	}

	@Test
	@DisplayName("Hides the probe when the driver logs it through a placeholder")
	void hidesTheProbeLoggedWithParameters() {
		FilterReply reply = filter.decide(null, logger(GaleraProbeFilter.DRIVER_ERROR_LOGGER), Level.WARN,
				"Error: {}-{}: {}", new Object[] { Integer.valueOf(1193), "HY000",
						"Unknown system variable 'WSREP_ON'" },
				null);

		assertEquals(FilterReply.DENY, reply);
	}

	@Test
	@DisplayName("Lets another parameterised driver error through")
	void keepsOtherParameterisedErrors() {
		FilterReply reply = filter.decide(null, logger(GaleraProbeFilter.DRIVER_ERROR_LOGGER), Level.WARN,
				"Error: {}-{}: {}", new Object[] { Integer.valueOf(1146), "42S02",
						"Table 'players' doesn't exist" },
				null);

		assertEquals(FilterReply.NEUTRAL, reply);
	}

	@Test
	@DisplayName("Lets every other driver error through")
	void keepsOtherDriverErrors() {
		assertEquals(FilterReply.NEUTRAL,
				decide(GaleraProbeFilter.DRIVER_ERROR_LOGGER, "Error: 1146-42S02: Table 'players' doesn't exist"));
	}

	@Test
	@DisplayName("Lets another unknown variable through")
	void keepsOtherUnknownVariables() {
		// Only the Galera probe is expected. Another missing variable is a real
		// mismatch between the code and the server, and must still be seen.
		assertEquals(FilterReply.NEUTRAL,
				decide(GaleraProbeFilter.DRIVER_ERROR_LOGGER, "Error: 1193-HY000: Unknown system variable 'FOO'"));
	}

	@Test
	@DisplayName("Leaves other loggers alone")
	void keepsOtherLoggers() {
		assertEquals(FilterReply.NEUTRAL, decide("com.aionemu.gameserver.GameServer", PROBE_MESSAGE));
	}

	@Test
	@DisplayName("Accepts a call with no message")
	void toleratesANullMessage() {
		assertEquals(FilterReply.NEUTRAL, decide(GaleraProbeFilter.DRIVER_ERROR_LOGGER, null));
	}

	@Test
	@DisplayName("Accepts a call with no logger")
	void toleratesANullLogger() {
		assertEquals(FilterReply.NEUTRAL, filter.decide(null, null, Level.WARN, PROBE_MESSAGE, null, null));
	}
}
