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
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.MacroList;

/**
 * Reads and writes a character's macros over JDBC.
 * <p>
 * The slot column is called order, which is a reserved word, so every statement
 * here has to quote it.
 *
 * @author Oraion
 */
public final class JdbcPlayerMacroRepository extends JdbcRepositorySupport implements PlayerMacroRepository {

	private static final String SELECT_ALL = "SELECT `order`,`macro` FROM `player_macrosses` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_macrosses` (`player_id`,`order`,`macro`) VALUES (?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `player_macrosses` SET `macro` = ? WHERE `player_id` = ? AND `order` = ?";
	private static final String DELETE_ONE = "DELETE FROM `player_macrosses` WHERE `player_id` = ? AND `order` = ?";

	public JdbcPlayerMacroRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public MacroList findAll(int playerId) {
		Map<Integer, String> macros = new LinkedHashMap<Integer, String>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					macros.put(Integer.valueOf(rows.getInt("order")), rows.getString("macro"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the macros of character " + playerId + ".", e);
		}
		return new MacroList(macros);
	}

	@Override
	public void add(int playerId, int slot, String macro) {
		write(INSERT_ONE, playerId, slot, macro, "store");
	}

	@Override
	public void update(int playerId, int slot, String macro) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setString(1, macro);
			statement.setInt(2, playerId);
			statement.setInt(3, slot);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to replace the macro in slot " + slot + " of character " + playerId + ".", e);
		}
	}

	@Override
	public void remove(int playerId, int slot) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, slot);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to empty slot " + slot + " of character " + playerId + ".", e);
		}
	}

	/** Runs the insert, which binds the character and slot before the text. */
	private void write(String query, int playerId, int slot, String macro, String what) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, playerId);
			statement.setInt(2, slot);
			statement.setString(3, macro);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to " + what + " the macro in slot " + slot + " of character " + playerId + ".", e);
		}
	}
}
