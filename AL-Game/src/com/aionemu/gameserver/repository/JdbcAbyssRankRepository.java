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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.AbyssRankingResult;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.AbyssRank;
import com.aionemu.gameserver.utils.stats.AbyssRankEnum;

/**
 * Reads and writes where each character and legion stands in the abyss, over
 * JDBC.
 *
 * @author Oraion
 */
public final class JdbcAbyssRankRepository extends JdbcRepositorySupport implements AbyssRankRepository {

	/** The glory a character needs before they appear in the ranking at all. */
	private static final int RANKING_GLORY_FLOOR = 1243;

	private static final String COUNTERS = "`daily_ap`,`daily_gp`,`weekly_ap`,`weekly_gp`,`ap`,`gp`,`rank`,"
			+ "`top_ranking`,`daily_kill`,`weekly_kill`,`all_kill`,`max_rank`,`last_kill`,`last_ap`,`last_gp`,"
			+ "`last_update`";

	private static final String SELECT_ONE = "SELECT " + COUNTERS + " FROM `abyss_rank` WHERE `player_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `abyss_rank` (`player_id`," + COUNTERS + ")"
			+ " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `abyss_rank` SET `daily_ap` = ?, `daily_gp` = ?,"
			+ " `weekly_ap` = ?, `weekly_gp` = ?, `ap` = ?, `gp` = ?, `rank` = ?, `top_ranking` = ?,"
			+ " `daily_kill` = ?, `weekly_kill` = ?, `all_kill` = ?, `max_rank` = ?, `last_kill` = ?,"
			+ " `last_ap` = ?, `last_gp` = ?, `last_update` = ? WHERE `player_id` = ?";
	private static final String UPDATE_GRADE = "UPDATE `abyss_rank` SET `rank` = ?, `top_ranking` = ?"
			+ " WHERE `player_id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `abyss_rank` WHERE `player_id` = ?";

	private static final String SELECT_RANKED_PLAYERS = "SELECT abyss_rank.rank, abyss_rank.ap, abyss_rank.gp,"
			+ " abyss_rank.old_rank_pos, abyss_rank.rank_pos, players.name, legions.name AS legion_name,"
			+ " players.id, players.title_id, players.player_class, players.exp"
			+ " FROM abyss_rank INNER JOIN players ON abyss_rank.player_id = players.id"
			+ " LEFT JOIN legion_members ON legion_members.player_id = players.id"
			+ " LEFT JOIN legions ON legions.id = legion_members.legion_id"
			+ " WHERE players.race = ? AND abyss_rank.gp > " + RANKING_GLORY_FLOOR
			+ " ORDER BY abyss_rank.gp DESC LIMIT 300";

	// The member count travels with the row, where the DAO ran one extra query
	// per legion it had just listed.
	private static final String SELECT_RANKED_LEGIONS = "SELECT legions.id, legions.name,"
			+ " legions.contribution_points, legions.level AS lvl, legions.old_rank_pos, legions.rank_pos,"
			+ " (SELECT COUNT(*) FROM legion_members WHERE legion_members.legion_id = legions.id) AS members"
			+ " FROM legions, legion_members, players"
			+ " WHERE players.race = ? AND legion_members.rank = 'BRIGADE_GENERAL'"
			+ " AND legion_members.player_id = players.id AND legion_members.legion_id = legions.id"
			+ " AND legions.contribution_points > 0"
			+ " GROUP BY legions.id ORDER BY legions.contribution_points DESC LIMIT 50";

	private static final String SELECT_POINTS = "SELECT abyss_rank.player_id, abyss_rank.%1$s FROM abyss_rank, players"
			+ " WHERE abyss_rank.player_id = players.id AND players.race = ? AND abyss_rank.%1$s > ?"
			+ " ORDER BY abyss_rank.%1$s DESC";
	private static final String SELECT_POINTS_ACTIVE = "SELECT abyss_rank.player_id, abyss_rank.%1$s"
			+ " FROM abyss_rank, players WHERE abyss_rank.player_id = players.id AND players.race = ?"
			+ " AND abyss_rank.%1$s > ? AND UNIX_TIMESTAMP(CURDATE()) - UNIX_TIMESTAMP(players.last_online)"
			+ " <= ? * 24 * 60 * 60 ORDER BY abyss_rank.%1$s DESC";

	private static final String RANK_PLAYERS = "UPDATE abyss_rank SET abyss_rank.old_rank_pos = abyss_rank.rank_pos,"
			+ " abyss_rank.rank_pos = @a := @a + 1 WHERE player_id IN (SELECT id FROM players WHERE race = ?)"
			+ " ORDER BY gp DESC" + (GSConfig.ABYSSRANKING_SMALL_CACHE ? " LIMIT 500" : "");
	private static final String RANK_LEGIONS = "UPDATE legions SET legions.old_rank_pos = legions.rank_pos,"
			+ " legions.rank_pos = @a := @a + 1 WHERE id IN (SELECT legion_id FROM legion_members, players"
			+ " WHERE rank = 'BRIGADE_GENERAL' AND players.id = legion_members.player_id AND players.race = ?)"
			+ " ORDER BY legions.contribution_points DESC" + (GSConfig.ABYSSRANKING_SMALL_CACHE ? " LIMIT 75" : "");

	public JdbcAbyssRankRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public AbyssRank load(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					AbyssRank fresh = new AbyssRank(0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0,
							System.currentTimeMillis());
					fresh.setPersistentState(PersistentState.NEW);
					return fresh;
				}
				AbyssRank rank = new AbyssRank(rows.getInt("daily_ap"), rows.getInt("daily_gp"),
						rows.getInt("weekly_ap"), rows.getInt("weekly_gp"), rows.getInt("ap"), rows.getInt("gp"),
						rows.getInt("rank"), rows.getInt("top_ranking"), rows.getInt("daily_kill"),
						rows.getInt("weekly_kill"), rows.getInt("all_kill"), rows.getInt("max_rank"),
						rows.getInt("last_kill"), rows.getInt("last_ap"), rows.getInt("last_gp"),
						rows.getLong("last_update"));
				rank.setPersistentState(PersistentState.UPDATED);
				return rank;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the abyss standing of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean save(int playerId, AbyssRank rank) {
		if (rank == null) {
			throw new IllegalArgumentException("Cannot store a null abyss standing.");
		}

		PersistentState state = rank.getPersistentState();
		if (state != PersistentState.NEW && state != PersistentState.UPDATE_REQUIRED) {
			return false;
		}
		boolean isNew = state == PersistentState.NEW;

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(isNew ? INSERT_ONE : UPDATE_ONE)) {
			int index = 1;
			if (isNew) {
				statement.setInt(index++, playerId);
			}
			statement.setInt(index++, rank.getDailyAP());
			statement.setInt(index++, rank.getDailyGP());
			statement.setInt(index++, rank.getWeeklyAP());
			statement.setInt(index++, rank.getWeeklyGP());
			statement.setInt(index++, rank.getAp());
			statement.setInt(index++, rank.getGp());
			statement.setInt(index++, rank.getRank().getId());
			statement.setInt(index++, rank.getTopRanking());
			statement.setInt(index++, rank.getDailyKill());
			statement.setInt(index++, rank.getWeeklyKill());
			statement.setInt(index++, rank.getAllKill());
			statement.setInt(index++, rank.getMaxRank());
			statement.setInt(index++, rank.getLastKill());
			statement.setInt(index++, rank.getLastAP());
			statement.setInt(index++, rank.getLastGP());
			statement.setLong(index++, rank.getLastUpdate());
			if (!isNew) {
				statement.setInt(index, playerId);
			}

			boolean written = statement.executeUpdate() > 0;
			// Mark it saved only once the write has landed. The DAO did this
			// whatever happened, so a lost write was never retried.
			rank.setPersistentState(PersistentState.UPDATED);
			return written;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to store the abyss standing of character " + playerId + ".", e);
		}
	}

	@Override
	public List<AbyssRankingResult> findRankedPlayers(Race race) {
		List<AbyssRankingResult> ranking = new ArrayList<AbyssRankingResult>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_RANKED_PLAYERS)) {
			statement.setString(1, race.toString());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					PlayerClass playerClass = PlayerClass
							.getPlayerClassByString(rows.getString("players.player_class"));
					if (playerClass == null) {
						continue;
					}
					ranking.add(new AbyssRankingResult(rows.getString("players.name"), rows.getInt("abyss_rank.rank"),
							rows.getInt("players.id"), rows.getInt("abyss_rank.ap"), rows.getInt("abyss_rank.gp"),
							rows.getInt("players.title_id"), playerClass,
							DataManager.PLAYER_EXPERIENCE_TABLE.getLevelForExp(rows.getLong("players.exp")),
							rows.getString("legion_name"), rows.getInt("old_rank_pos"), rows.getInt("rank_pos")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the abyss ranking of the " + race + ".", e);
		}

		return ranking;
	}

	@Override
	public List<AbyssRankingResult> findRankedLegions(Race race) {
		List<AbyssRankingResult> ranking = new ArrayList<AbyssRankingResult>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_RANKED_LEGIONS)) {
			statement.setString(1, race.toString());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					ranking.add(new AbyssRankingResult(rows.getInt("legions.contribution_points"),
							rows.getString("legions.name"), rows.getInt("legions.id"), rows.getInt("lvl"),
							rows.getInt("members"), rows.getInt("old_rank_pos"), rows.getInt("rank_pos")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the legion ranking of the " + race + ".", e);
		}

		return ranking;
	}

	@Override
	public Map<Integer, Integer> findAbyssPoints(Race race, int lowerLimit, int maxOfflineDays) {
		return findPoints("ap", race, lowerLimit, maxOfflineDays);
	}

	@Override
	public Map<Integer, Integer> findGloryPoints(Race race, int lowerLimit, int maxOfflineDays) {
		return findPoints("gp", race, lowerLimit, maxOfflineDays);
	}

	/**
	 * The column name is spliced into the statement because a placeholder cannot
	 * stand for one. Both callers are in this file and pass a literal.
	 */
	private Map<Integer, Integer> findPoints(String column, Race race, int lowerLimit, int maxOfflineDays) {
		boolean activeOnly = maxOfflineDays > 0;
		String query = String.format(activeOnly ? SELECT_POINTS_ACTIVE : SELECT_POINTS, column);
		Map<Integer, Integer> points = new HashMap<Integer, Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setString(1, race.toString());
			statement.setInt(2, lowerLimit);
			if (activeOnly) {
				statement.setInt(3, maxOfflineDays);
			}
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					points.put(Integer.valueOf(rows.getInt("player_id")), Integer.valueOf(rows.getInt(column)));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the " + column + " of the " + race + ".", e);
		}

		return points;
	}

	@Override
	public boolean setGrade(int playerId, AbyssRankEnum grade) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_GRADE)) {
			statement.setInt(1, grade.getId());
			statement.setInt(2, grade.getQuota());
			statement.setInt(3, playerId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record the abyss grade of character " + playerId + ".", e);
		}
	}

	@Override
	public void updateRankPositions() {
		inTransaction(connection -> {
			// Run one ranking pass per race, resetting the row counter before each.
			// The reset and the query must share a connection, user variables being
			// per connection.
			for (String query : new String[] { RANK_PLAYERS, RANK_LEGIONS }) {
				for (Race race : new Race[] { Race.ELYOS, Race.ASMODIANS }) {
					try (Statement reset = connection.createStatement()) {
						reset.execute("SET @a := 0");
					}
					try (PreparedStatement statement = connection.prepareStatement(query)) {
						statement.setString(1, race.toString());
						statement.execute();
					}
				}
			}
			return null;
		}, "Failed to re-number the abyss ranking.");
	}

	@Override
	public int removeAll(Collection<Integer> playerIds) {
		if (playerIds == null || playerIds.isEmpty()) {
			return 0;
		}

		// One connection and one batch. The DAO opened a fresh connection inside
		// its loop, overwriting the statement each time, so only the last character
		// was struck and every earlier connection leaked.
		return inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
				for (Integer playerId : playerIds) {
					statement.setInt(1, playerId.intValue());
					statement.addBatch();
				}
				int removed = 0;
				for (int count : statement.executeBatch()) {
					if (count > 0) {
						removed += count;
					}
				}
				return Integer.valueOf(removed);
			}
		}, "Failed to strike " + playerIds.size() + " characters from the abyss ranking.").intValue();
	}
}
