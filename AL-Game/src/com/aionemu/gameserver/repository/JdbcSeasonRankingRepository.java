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
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.ranking.Arena6V6Ranking;
import com.aionemu.gameserver.model.gameobjects.player.ranking.ArenaOfTenacityRank;
import com.aionemu.gameserver.model.gameobjects.player.ranking.GoldArenaRank;
import com.aionemu.gameserver.model.gameobjects.player.ranking.TowerOfChallengeRank;
import com.aionemu.gameserver.model.ranking.SeasonRankingResult;
import com.aionemu.gameserver.model.ranking.SeasonRankingEnum;

/**
 * Reads and writes where each character stands in the seasonal competitions,
 * over JDBC.
 *
 * @author Oraion
 */
public final class JdbcSeasonRankingRepository extends JdbcRepositorySupport implements SeasonRankingRepository {

	private static final String SELECT_LEADERBOARD = "SELECT competition_ranking.rank, competition_ranking.last_rank,"
			+ " competition_ranking.points, players.name, players.id, players.player_class, players.race"
			+ " FROM competition_ranking INNER JOIN players ON competition_ranking.player_id = players.id"
			+ " WHERE competition_ranking.table_id = ? AND competition_ranking.points > 0"
			+ " ORDER BY competition_ranking.points DESC LIMIT 300";
	private static final String SELECT_STANDING = "SELECT `rank`,`last_rank`,`points`,`last_points`,`high_points`,"
			+ "`low_points`,`position_match` FROM `competition_ranking` WHERE `player_id` = ? AND `table_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `competition_ranking` (`player_id`,`table_id`,`rank`,"
			+ "`last_rank`,`points`,`last_points`,`high_points`,`low_points`,`position_match`)"
			+ " VALUES (?,?,?,?,?,?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `competition_ranking` SET `rank` = ?, `last_rank` = ?,"
			+ " `points` = ?, `last_points` = ?, `high_points` = ?, `low_points` = ?, `position_match` = ?"
			+ " WHERE `player_id` = ? AND `table_id` = ?";

	public JdbcSeasonRankingRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public List<SeasonRankingResult> findLeaderboard(int tableId) {
		List<SeasonRankingResult> leaderboard = new ArrayList<SeasonRankingResult>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_LEADERBOARD)) {
			statement.setInt(1, tableId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					PlayerClass playerClass = PlayerClass
							.getPlayerClassByString(rows.getString("players.player_class"));
					if (playerClass == null) {
						continue;
					}
					leaderboard.add(new SeasonRankingResult(rows.getString("players.name"),
							rows.getInt("competition_ranking.last_rank"), rows.getInt("competition_ranking.rank"),
							rows.getInt("competition_ranking.points"), playerClass,
							Race.getRaceByString(rows.getString("players.race")).getRaceId(),
							rows.getInt("players.id")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the leaderboard of competition " + tableId + ".", e);
		}

		return leaderboard;
	}

	/**
	 * Every standing carries the same seven numbers; only their names differ per
	 * competition, so one read serves all four.
	 */
	private int[] readStanding(int playerId, int tableId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_STANDING)) {
			statement.setInt(1, playerId);
			statement.setInt(2, tableId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return null;
				}
				return new int[] { rows.getInt("rank"), rows.getInt("last_rank"), rows.getInt("points"),
						rows.getInt("last_points"), rows.getInt("high_points"), rows.getInt("low_points"),
						rows.getInt("position_match") };
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the standing of character " + playerId
					+ " in competition " + tableId + ".", e);
		}
	}

	@Override
	public GoldArenaRank loadGoldArena(int playerId, int tableId) {
		int[] standing = readStanding(playerId, tableId);
		if (standing == null) {
			GoldArenaRank fresh = new GoldArenaRank(0, 0, 0, 0, 0, 0, 0);
			fresh.setPersistentState(PersistentState.NEW);
			return fresh;
		}
		// The DAO read this one standing with a hard-coded zero where the position
		// match belonged, so it came back lost on every reload.
		GoldArenaRank rank = new GoldArenaRank(standing[0], standing[1], standing[2], standing[3], standing[4],
				standing[5], standing[6]);
		rank.setPersistentState(PersistentState.UPDATED);
		return rank;
	}

	@Override
	public ArenaOfTenacityRank loadTenacity(int playerId, int tableId) {
		int[] standing = readStanding(playerId, tableId);
		if (standing == null) {
			ArenaOfTenacityRank fresh = new ArenaOfTenacityRank(0, 0, 0, 0, 0, 0, 0);
			fresh.setPersistentState(PersistentState.NEW);
			return fresh;
		}
		ArenaOfTenacityRank rank = new ArenaOfTenacityRank(standing[0], standing[1], standing[2], standing[3],
				standing[4], standing[5], standing[6]);
		rank.setPersistentState(PersistentState.UPDATED);
		return rank;
	}

	@Override
	public Arena6V6Ranking load6v6(int playerId, int tableId) {
		int[] standing = readStanding(playerId, tableId);
		if (standing == null) {
			Arena6V6Ranking fresh = new Arena6V6Ranking(0, 0, 0, 0, 0, 0, 0);
			fresh.setPersistentState(PersistentState.NEW);
			return fresh;
		}
		Arena6V6Ranking rank = new Arena6V6Ranking(standing[0], standing[1], standing[2], standing[3], standing[4],
				standing[5], standing[6]);
		rank.setPersistentState(PersistentState.UPDATED);
		return rank;
	}

	@Override
	public TowerOfChallengeRank loadTower(int playerId, int tableId) {
		int[] standing = readStanding(playerId, tableId);
		if (standing == null) {
			TowerOfChallengeRank fresh = new TowerOfChallengeRank(0, 0, 0, 0, 0, 0);
			fresh.setPersistentState(PersistentState.NEW);
			return fresh;
		}
		// The tower counts times where the arenas count points. The DAO wrote them
		// in one order and read them back in another, so a saved current time came
		// back as the low rank, the last time as the current time, and so on round
		// the four. The read now mirrors the write.
		TowerOfChallengeRank rank = new TowerOfChallengeRank(standing[0], standing[1], standing[5], standing[2],
				standing[3], standing[4]);
		rank.setPersistentState(PersistentState.UPDATED);
		return rank;
	}

	@Override
	public boolean saveTower(int playerId, TowerOfChallengeRank rank) {
		if (rank == null) {
			throw new IllegalArgumentException("Cannot store a null tower standing.");
		}

		return writeStanding(playerId, SeasonRankingEnum.TOWER_OF_CHALLENGE.getId(), rank.getPersistentState(),
				new int[] { rank.getRank(), rank.getBestRank(), rank.getCurrentTime(), rank.getLastTime(),
						rank.getBestTime(), rank.getLowRank(), 0 },
				rank::setPersistentState);
	}

	@FunctionalInterface
	private interface StateSink {
		void accept(PersistentState state);
	}

	/**
	 * The four competitions wrote the same nine columns through four copies of the
	 * same pair of statements; one write serves them all.
	 */
	private boolean writeStanding(int playerId, int tableId, PersistentState state, int[] standing,
			StateSink markSaved) {
		if (state != PersistentState.NEW && state != PersistentState.UPDATE_REQUIRED) {
			return false;
		}
		boolean isNew = state == PersistentState.NEW;

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(isNew ? INSERT_ONE : UPDATE_ONE)) {
			int index = 1;
			if (isNew) {
				statement.setInt(index++, playerId);
				statement.setInt(index++, tableId);
			}
			for (int value : standing) {
				statement.setInt(index++, value);
			}
			if (!isNew) {
				statement.setInt(index++, playerId);
				statement.setInt(index, tableId);
			}

			boolean written = statement.executeUpdate() > 0;
			// Mark it saved only once the write has landed. The DAO did this
			// whatever happened, so a lost write was never retried.
			markSaved.accept(PersistentState.UPDATED);
			return written;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to store the standing of character " + playerId
					+ " in competition " + tableId + ".", e);
		}
	}
}
