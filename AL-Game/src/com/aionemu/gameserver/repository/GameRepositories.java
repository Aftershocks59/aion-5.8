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

import javax.sql.DataSource;

import com.aionemu.commons.database.DatabaseFactory;

/**
 * Hands out the game server's repositories, built once over the connection pool.
 * <p>
 * Every accessor is typed and the set is visible in one place, unlike the DAO
 * lookup it replaces, which answered a class with a cast from a map. Each
 * repository still takes its data source by constructor, which is what lets a
 * test hand it another one.
 * <p>
 * Built lazily, because the pool is only open once the server has started. This
 * grows as the remaining DAOs are converted.
 *
 * @author Oraion
 */
public final class GameRepositories {

	private static GameRepositories instance;

	private final PlayerCooldownRepository skillCooldowns;
	private final PlayerCooldownRepository itemCooldowns;
	private final PlayerCooldownRepository craftCooldowns;
	private final PlayerCooldownRepository portalCooldowns;
	private final PlayerCooldownRepository houseObjectCooldowns;

	/**
	 * Builds every repository over one data source.
	 *
	 * @param dataSource the pool they all borrow from
	 */
	public GameRepositories(DataSource dataSource) {
		skillCooldowns = new JdbcPlayerSkillCooldownRepository(dataSource);
		itemCooldowns = new JdbcItemCooldownRepository(dataSource);
		craftCooldowns = new JdbcCraftCooldownRepository(dataSource);
		portalCooldowns = new JdbcPortalCooldownRepository(dataSource);
		houseObjectCooldowns = new JdbcHouseObjectCooldownRepository(dataSource);
	}

	/** Answers the shared set, building it over the pool on first use. */
	public static synchronized GameRepositories getInstance() {
		if (instance == null) {
			instance = new GameRepositories(DatabaseFactory.getDataSource());
		}
		return instance;
	}

	public static PlayerCooldownRepository skillCooldowns() {
		return getInstance().skillCooldowns;
	}

	public static PlayerCooldownRepository itemCooldowns() {
		return getInstance().itemCooldowns;
	}

	public static PlayerCooldownRepository craftCooldowns() {
		return getInstance().craftCooldowns;
	}

	public static PlayerCooldownRepository portalCooldowns() {
		return getInstance().portalCooldowns;
	}

	public static PlayerCooldownRepository houseObjectCooldowns() {
		return getInstance().houseObjectCooldowns;
	}
}
