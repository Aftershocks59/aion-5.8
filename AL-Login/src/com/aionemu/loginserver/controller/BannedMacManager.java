/**
 * This file is part of Aion-Lightning <aion-lightning.org>.
 *
 *  Aion-Lightning is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Aion-Lightning is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details. *
 *  You should have received a copy of the GNU General Public License
 *  along with Aion-Lightning.
 *  If not, see <http://www.gnu.org/licenses/>.
 */


package com.aionemu.loginserver.controller;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.loginserver.model.base.BannedMacEntry;
import com.aionemu.loginserver.repository.BannedMacRepository;
import com.aionemu.loginserver.repository.JdbcBannedMacRepository;

/**
 * Keeps the banned MAC addresses in memory, backed by the repository.
 *
 * @author KID
 */
public class BannedMacManager {

	private static BannedMacManager manager;

	private final BannedMacRepository repository;
	private final Map<String, BannedMacEntry> bannedList;

	/**
	 * Builds a manager over the given store.
	 * <p>
	 * Takes the repository rather than looking one up, so the ban logic can be
	 * exercised without a database.
	 *
	 * @param repository where the bans live
	 */
	public BannedMacManager(BannedMacRepository repository) {
		this.repository = repository;
		this.bannedList = new LinkedHashMap<String, BannedMacEntry>(repository.findAll());
	}

	/**
	 * Answers the shared manager, building it on first use.
	 * <p>
	 * Built lazily because it reads the bans straight away, which needs the
	 * connection pool to be open.
	 */
	public static synchronized BannedMacManager getInstance() {
		if (manager == null) {
			manager = new BannedMacManager(new JdbcBannedMacRepository(DatabaseFactory.getDataSource()));
		}
		return manager;
	}

	/**
	 * Lifts the ban on an address.
	 *
	 * @param address the address to unban
	 * @param details why it was lifted, kept for the caller's audit trail
	 */
	public void unban(String address, String details) {
		if (bannedList.remove(address) != null) {
			repository.remove(address);
		}
	}

	/**
	 * Bans an address until the given moment.
	 *
	 * @param address the address to ban
	 * @param time    when the ban ends
	 * @param details why it was banned
	 */
	public void ban(String address, long time, String details) {
		BannedMacEntry entry = new BannedMacEntry(address, new Timestamp(time), details);
		bannedList.put(address, entry);
		repository.save(entry);
	}

	public final Map<String, BannedMacEntry> getMap() {
		return bannedList;
	}
}
