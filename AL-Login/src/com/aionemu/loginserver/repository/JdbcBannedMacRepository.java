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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.model.base.BannedMacEntry;

/**
 * Reads and writes the MAC bans over JDBC.
 * <p>
 * Takes its data source rather than reaching for a static one, so the mapping
 * and the failure handling can be exercised without a database.
 *
 * @author Oraion
 */
public final class JdbcBannedMacRepository implements BannedMacRepository {

	private static final String SELECT_ALL = "SELECT `address`,`time`,`details` FROM `banned_mac`";
	private static final String REPLACE_ONE = "REPLACE INTO `banned_mac` (`address`,`time`,`details`) VALUES (?,?,?)";
	private static final String DELETE_ONE = "DELETE FROM `banned_mac` WHERE `address` = ?";
	private static final String DELETE_EXPIRED = "DELETE FROM `banned_mac` WHERE `time` < CURRENT_DATE";

	private final DataSource dataSource;

	public JdbcBannedMacRepository(DataSource dataSource) {
		if (dataSource == null) {
			throw new IllegalArgumentException("A repository needs a data source.");
		}
		this.dataSource = dataSource;
	}

	@Override
	public Map<String, BannedMacEntry> findAll() {
		Map<String, BannedMacEntry> bans = new LinkedHashMap<String, BannedMacEntry>();
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				String address = rows.getString("address");
				bans.put(address,
						new BannedMacEntry(address, rows.getTimestamp("time"), rows.getString("details")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the banned MAC addresses.", e);
		}
		return bans;
	}

	@Override
	public boolean save(BannedMacEntry entry) {
		if (entry == null) {
			throw new IllegalArgumentException("Cannot store a null ban.");
		}

		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(REPLACE_ONE)) {
			statement.setString(1, entry.getMac());
			statement.setTimestamp(2, entry.getTime());
			statement.setString(3, entry.getDetails());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to ban the MAC address " + entry.getMac() + ".", e);
		}
	}

	@Override
	public boolean remove(String address) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setString(1, address);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to lift the ban on the MAC address " + address + ".", e);
		}
	}

	@Override
	public int removeExpired() {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(DELETE_EXPIRED)) {
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to drop the expired MAC bans.", e);
		}
	}
}
