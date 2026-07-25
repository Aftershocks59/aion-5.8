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

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.GameServerInfo;

/**
 * Reads the registered game servers over JDBC.
 *
 * @author Oraion
 */
public final class JdbcGameServerRepository extends JdbcRepositorySupport implements GameServerRepository {

	/** Names the columns rather than selecting everything, so a schema change shows here. */
	private static final String SELECT_ALL = "SELECT `id`,`mask`,`password` FROM `gameservers`";

	public JdbcGameServerRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Map<Byte, GameServerInfo> findAll() {
		Map<Byte, GameServerInfo> servers = new LinkedHashMap<Byte, GameServerInfo>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				byte id = rows.getByte("id");
				servers.put(Byte.valueOf(id),
						new GameServerInfo(id, rows.getString("mask"), rows.getString("password")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the registered game servers.", e);
		}
		return servers;
	}
}
