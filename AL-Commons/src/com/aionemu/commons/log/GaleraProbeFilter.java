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

import org.slf4j.Marker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Drops the one warning Flyway's Galera probe provokes on a standalone server.
 * <p>
 * Before touching the schema, Flyway asks whether it is talking to a Galera
 * cluster, because that would change how it takes its lock. It asks by reading
 * {@code @@WSREP_ON}, a variable a standalone MariaDB does not define, so the
 * server answers error 1193, the driver logs it, and Flyway carries on
 * perfectly happily. Three warnings per start, every start, describing nothing
 * wrong.
 * <p>
 * Silencing the driver's error logger outright would hide real SQL failures, so
 * this denies exactly that message from exactly that logger and stays out of the
 * way of everything else.
 *
 * @author Oraion
 */
public final class GaleraProbeFilter extends TurboFilter {

	/** Logger the MariaDB driver reports server error packets through. */
	static final String DRIVER_ERROR_LOGGER = "org.mariadb.jdbc.message.server.ErrorPacket";

	/** Names the Galera variable, which only a clustered server defines. */
	static final String GALERA_VARIABLE = "WSREP_ON";

	/** Matches the server's reply to a variable it does not know. */
	static final String UNKNOWN_VARIABLE = "Unknown system variable";

	@Override
	public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params,
			Throwable throwable) {
		if (logger == null || !DRIVER_ERROR_LOGGER.equals(logger.getName())) {
			return FilterReply.NEUTRAL;
		}

		// Look at the arguments as well as the pattern: the driver logs this through
		// a placeholder, so the variable name never appears in the format string.
		String message = describe(format, params);
		if (message.contains(UNKNOWN_VARIABLE) && message.contains(GALERA_VARIABLE)) {
			return FilterReply.DENY;
		}
		return FilterReply.NEUTRAL;
	}

	/** Joins the pattern and its arguments, so either can carry the text. */
	private static String describe(String format, Object[] params) {
		StringBuilder message = new StringBuilder(format == null ? "" : format);
		if (params != null) {
			for (Object param : params) {
				message.append(' ').append(param);
			}
		}
		return message.toString();
	}
}
