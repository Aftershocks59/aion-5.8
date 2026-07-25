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

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.skinskill.SkillSkin;
import com.aionemu.gameserver.model.skinskill.SkillSkinList;

/**
 * Reads and writes a character's skill appearances over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerSkillSkinRepository extends JdbcRepositorySupport
		implements PlayerSkillSkinRepository {

	private static final String SELECT_ALL = "SELECT `skin_id`,`remaining`,`active` FROM `player_skill_skins` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_skill_skins` (`player_id`,`skin_id`,`remaining`,`active`) VALUES (?,?,?,?)";
	private static final String DELETE_ONE = "DELETE FROM `player_skill_skins` WHERE `player_id` = ? AND `skin_id` = ?";

	/**
	 * Switches a skin on or off.
	 * <p>
	 * The DAO kept two statements that differed only in the literal they set,
	 * which is a parameter.
	 */
	private static final String SET_ACTIVE = "UPDATE `player_skill_skins` SET `active` = ? WHERE `player_id` = ? AND `skin_id` = ?";

	public JdbcPlayerSkillSkinRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public SkillSkinList findAll(int playerId) {
		SkillSkinList skins = new SkillSkinList();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					skins.addEntry(rows.getInt("skin_id"), rows.getInt("remaining"), rows.getInt("active"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the skill skins of character " + playerId + ".", e);
		}
		return skins;
	}

	@Override
	public boolean add(int playerId, SkillSkin skin) {
		if (skin == null) {
			throw new IllegalArgumentException("Cannot store a null skill skin.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, skin.getId());
			statement.setInt(3, skin.getExpireTime());
			statement.setInt(4, skin.getIsActive());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to grant skill skin " + skin.getId() + " to character " + playerId + ".", e);
		}
	}

	@Override
	public boolean remove(int playerId, int skinId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, skinId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to take skill skin " + skinId + " from character " + playerId + ".", e);
		}
	}

	@Override
	public boolean setActive(int playerId, int skinId, boolean active) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SET_ACTIVE)) {
			statement.setInt(1, active ? 1 : 0);
			statement.setInt(2, playerId);
			statement.setInt(3, skinId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to switch skill skin " + skinId + " of character " + playerId + ".", e);
		}
	}
}
