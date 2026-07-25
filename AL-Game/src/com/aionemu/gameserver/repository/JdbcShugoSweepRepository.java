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
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerSweep;

/**
 * Reads and writes the Shugo Sweep boards, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcShugoSweepRepository extends JdbcRepositorySupport implements ShugoSweepRepository {

	private static final String SELECT_ONE = "SELECT `free_dice`,`sweep_step`,`board_id`,`golden_dice`,`sweep_reset`,`completed_steps`"
			+ " FROM `player_shugo_sweep` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `player_shugo_sweep`"
			+ " (`player_id`,`free_dice`,`sweep_step`,`board_id`,`golden_dice`,`sweep_reset`,`completed_steps`)"
			+ " VALUES (?,?,?,?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `player_shugo_sweep` SET `free_dice` = ?, `sweep_step` = ?,"
			+ " `board_id` = ?, `golden_dice` = ?, `sweep_reset` = ?, `completed_steps` = ? WHERE `player_id` = ?";

	public JdbcShugoSweepRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void load(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot read a Shugo Sweep board for a null character.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return;
				}
				PlayerSweep board = new PlayerSweep(rows.getInt("sweep_step"), rows.getInt("free_dice"),
						rows.getInt("board_id"), rows.getInt("golden_dice"), rows.getInt("sweep_reset"),
						rows.getInt("completed_steps"));
				board.setPersistentState(PersistentState.UPDATED);
				player.setPlayerShugoSweep(board);
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the Shugo Sweep board of character " + player.getObjectId() + ".", e);
		}
	}

	@Override
	public boolean add(int playerId, PlayerSweep board) {
		if (board == null) {
			throw new IllegalArgumentException("Cannot store a null Shugo Sweep board.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, board.getFreeDice());
			statement.setInt(3, board.getStep());
			statement.setInt(4, board.getBoardId());
			statement.setInt(5, board.getGoldenDice());
			statement.setInt(6, board.getResetBoard());
			statement.setInt(7, board.getCompletedSteps());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to give character " + playerId + " a Shugo Sweep board.", e);
		}
	}

	@Override
	public boolean save(Player player) {
		if (player == null) {
			throw new IllegalArgumentException("Cannot store the Shugo Sweep board of a null character.");
		}

		PlayerSweep board = player.getPlayerShugoSweep();
		if (board == null) {
			return false;
		}
		PersistentState state = board.getPersistentState();
		if (state != PersistentState.UPDATE_REQUIRED && state != PersistentState.NEW) {
			return false;
		}

		boolean written = saveBoard(player.getObjectId(), board);
		// Mark it saved only now. The DAO did this whatever happened, so a board
		// whose write had failed still looked saved and was never retried.
		board.setPersistentState(PersistentState.UPDATED);
		return written;
	}

	@Override
	public boolean saveBoard(int playerId, PlayerSweep board) {
		if (board == null) {
			throw new IllegalArgumentException("Cannot store a null Shugo Sweep board.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setInt(1, board.getFreeDice());
			statement.setInt(2, board.getStep());
			statement.setInt(3, board.getBoardId());
			statement.setInt(4, board.getGoldenDice());
			statement.setInt(5, board.getResetBoard());
			statement.setInt(6, board.getCompletedSteps());
			statement.setInt(7, playerId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to store the Shugo Sweep board of character " + playerId + ".", e);
		}
	}
}
