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

import javax.sql.DataSource;

import com.aionemu.commons.database.DatabaseFactory;

/**
 * Hands out the login server's repositories, built once over the connection
 * pool.
 * <p>
 * This is not a step back towards the DAO lookup it replaces. That answered
 * DAOManager.getDAO(SomeDAO.class) with a cast from a map, so a caller named a
 * class and hoped; here every accessor is typed, the set is visible in one
 * place, and each repository still takes its data source by constructor, which
 * is what lets a test hand it a different one.
 * <p>
 * Built lazily because the pool is only open once the server has started.
 *
 * @author Oraion
 */
public final class LoginRepositories {

	private static LoginRepositories instance;

	private final AccountRepository accounts;
	private final AccountTimeRepository accountTimes;
	private final AccountPlayTimeRepository playTimes;
	private final BannedIpRepository bannedIps;
	private final BannedMacRepository bannedMacs;
	private final GameServerRepository gameServers;
	private final PlayerTransferRepository playerTransfers;
	private final PremiumRepository premium;
	private final ScheduledTaskRepository scheduledTasks;
	private final ServerStatsRepository serverStats;

	/**
	 * Builds every repository over one data source.
	 *
	 * @param dataSource the pool they all borrow from
	 */
	public LoginRepositories(DataSource dataSource) {
		accounts = new JdbcAccountRepository(dataSource);
		accountTimes = new JdbcAccountTimeRepository(dataSource);
		playTimes = new JdbcAccountPlayTimeRepository(dataSource);
		bannedIps = new JdbcBannedIpRepository(dataSource);
		bannedMacs = new JdbcBannedMacRepository(dataSource);
		gameServers = new JdbcGameServerRepository(dataSource);
		playerTransfers = new JdbcPlayerTransferRepository(dataSource);
		premium = new JdbcPremiumRepository(dataSource);
		scheduledTasks = new JdbcScheduledTaskRepository(dataSource);
		serverStats = new JdbcServerStatsRepository(dataSource);
	}

	/** Answers the shared set, building it over the pool on first use. */
	public static synchronized LoginRepositories getInstance() {
		if (instance == null) {
			instance = new LoginRepositories(DatabaseFactory.getDataSource());
		}
		return instance;
	}

	/** Installs a set built elsewhere, for tests. */
	static synchronized void setInstance(LoginRepositories replacement) {
		instance = replacement;
	}

	public static AccountRepository accounts() {
		return getInstance().accounts;
	}

	public static AccountTimeRepository accountTimes() {
		return getInstance().accountTimes;
	}

	public static AccountPlayTimeRepository playTimes() {
		return getInstance().playTimes;
	}

	public static BannedIpRepository bannedIps() {
		return getInstance().bannedIps;
	}

	public static BannedMacRepository bannedMacs() {
		return getInstance().bannedMacs;
	}

	public static GameServerRepository gameServers() {
		return getInstance().gameServers;
	}

	public static PlayerTransferRepository playerTransfers() {
		return getInstance().playerTransfers;
	}

	public static PremiumRepository premium() {
		return getInstance().premium;
	}

	public static ScheduledTaskRepository scheduledTasks() {
		return getInstance().scheduledTasks;
	}

	public static ServerStatsRepository serverStats() {
		return getInstance().serverStats;
	}
}
