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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.PlayerScripts;

/**
 * Reads and writes the decorating scripts saved against a house, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcHouseScriptRepository extends JdbcRepositorySupport implements HouseScriptRepository {

	private static final String SELECT_SAVED = "SELECT `index`,`script` FROM `house_scripts` WHERE `house_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `house_scripts` (`house_id`,`index`,`script`) VALUES (?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `house_scripts` SET `script` = ? WHERE `house_id` = ? AND `index` = ?";
	private static final String DELETE_ONE = "DELETE FROM `house_scripts` WHERE `house_id` = ? AND `index` = ?";

	public JdbcHouseScriptRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public PlayerScripts load(int houseId) {
		PlayerScripts scripts = new PlayerScripts(houseId);

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_SAVED)) {
			statement.setInt(1, houseId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					scripts.addScript(rows.getInt("index"), rows.getString("script"));
				}
			}
		} catch (SQLException e) {
			// The DAO caught this and did nothing at all, not even a log line, so a
			// house came back empty and the next save overwrote what was there.
			throw new RepositoryException("Failed to read the scripts of house " + houseId + ".", e);
		}

		return scripts;
	}

	@Override
	public boolean add(int houseId, int position, String script) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, houseId);
			statement.setInt(2, position);
			setScript(statement, 3, script);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to save script " + position + " of house " + houseId + ".", e);
		}
	}

	@Override
	public boolean update(int houseId, int position, String script) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			setScript(statement, 1, script);
			statement.setInt(2, houseId);
			statement.setInt(3, position);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to replace script " + position + " of house " + houseId + ".", e);
		}
	}

	@Override
	public boolean remove(int houseId, int position) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, houseId);
			statement.setInt(2, position);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to clear script " + position + " of house " + houseId + ".", e);
		}
	}

	private static void setScript(PreparedStatement statement, int index, String script) throws SQLException {
		if (script == null) {
			statement.setNull(index, Types.LONGNVARCHAR);
		} else {
			statement.setString(index, script);
		}
	}
}
