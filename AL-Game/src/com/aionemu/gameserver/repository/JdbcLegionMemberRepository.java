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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.team.legion.LegionMember;
import com.aionemu.gameserver.model.team.legion.LegionMemberEx;
import com.aionemu.gameserver.model.team.legion.LegionRank;
import com.aionemu.gameserver.services.LegionService;

/**
 * Reads and writes which legion each character belongs to, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcLegionMemberRepository extends JdbcRepositorySupport implements LegionMemberRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcLegionMemberRepository.class);

	private static final String SELECT_ENROLLED = "SELECT 1 FROM `legion_members` WHERE `player_id` = ? LIMIT 1";
	private static final String SELECT_ONE = "SELECT `legion_id`,`rank`,`nickname`,`selfintro`,`challenge_score`"
			+ " FROM `legion_members` WHERE `player_id` = ?";
	private static final String SELECT_DETAILED_BY_ID = "SELECT players.name, players.exp, players.player_class,"
			+ " players.last_online, players.world_id, legion_members.legion_id, legion_members.rank,"
			+ " legion_members.nickname, legion_members.selfintro FROM players, legion_members"
			+ " WHERE players.id = ? AND players.id = legion_members.player_id";
	private static final String SELECT_DETAILED_BY_NAME = "SELECT players.id, players.exp, players.player_class,"
			+ " players.last_online, players.world_id, legion_members.legion_id, legion_members.rank,"
			+ " legion_members.nickname, legion_members.selfintro FROM players, legion_members"
			+ " WHERE players.name = ? AND players.id = legion_members.player_id";
	private static final String SELECT_MEMBERS = "SELECT `player_id` FROM `legion_members` WHERE `legion_id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `legion_members` (`legion_id`,`player_id`,`rank`)"
			+ " VALUES (?,?,?)";
	private static final String UPDATE_ONE = "UPDATE `legion_members` SET `nickname` = ?, `rank` = ?,"
			+ " `selfintro` = ?, `challenge_score` = ? WHERE `player_id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `legion_members` WHERE `player_id` = ?";

	public JdbcLegionMemberRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public boolean isEnrolled(int playerId) {
		// One row at most, where the DAO counted every matching row.
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ENROLLED)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		} catch (SQLException e) {
			// Deliberately fails closed: a doubtful character is treated as already
			// enrolled so they are never put in two legions.
			log.error("Cannot tell whether character " + playerId + " is in a legion; treating them as enrolled.", e);
			return true;
		}
	}

	@Override
	public LegionMember load(int playerId) {
		if (playerId == 0) {
			return null;
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				// The DAO read the row without checking there was one and let the
				// resulting exception stand in for "this character has no legion".
				if (!rows.next()) {
					return null;
				}
				LegionMember member = new LegionMember(playerId);
				member.setRank(LegionRank.valueOf(rows.getString("rank")));
				member.setNickname(rows.getString("nickname"));
				member.setSelfIntro(rows.getString("selfintro"));
				member.setChallengeScore(rows.getInt("challenge_score"));
				member.setLegion(LegionService.getInstance().getLegion(rows.getInt("legion_id")));
				return member.getLegion() == null ? null : member;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the legion of character " + playerId + ".", e);
		}
	}

	@Override
	public LegionMemberEx loadDetailed(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_DETAILED_BY_ID)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return null;
				}
				LegionMemberEx member = new LegionMemberEx(playerId);
				member.setName(rows.getString("players.name"));
				return fill(member, rows);
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the legion of character " + playerId + ".", e);
		}
	}

	@Override
	public LegionMemberEx loadDetailed(String playerName) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_DETAILED_BY_NAME)) {
			statement.setString(1, playerName);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return null;
				}
				LegionMemberEx member = new LegionMemberEx(playerName);
				member.setObjectId(rows.getInt("players.id"));
				return fill(member, rows);
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the legion of " + playerName + ".", e);
		}
	}

	private static LegionMemberEx fill(LegionMemberEx member, ResultSet rows) throws SQLException {
		member.setExp(rows.getLong("players.exp"));
		member.setPlayerClass(PlayerClass.valueOf(rows.getString("players.player_class")));
		member.setLastOnline(rows.getTimestamp("players.last_online"));
		member.setWorldId(rows.getInt("players.world_id"));
		member.setRank(LegionRank.valueOf(rows.getString("legion_members.rank")));
		member.setNickname(rows.getString("legion_members.nickname"));
		member.setSelfIntro(rows.getString("legion_members.selfintro"));
		member.setLegion(LegionService.getInstance().getLegion(rows.getInt("legion_members.legion_id")));
		return member.getLegion() == null ? null : member;
	}

	@Override
	public List<Integer> loadMembers(int legionId) {
		// Empty rather than null, which the DAO answered for a legion whose last
		// member had just left.
		List<Integer> members = new ArrayList<Integer>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_MEMBERS)) {
			statement.setInt(1, legionId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					members.add(Integer.valueOf(rows.getInt("player_id")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the members of legion " + legionId + ".", e);
		}

		return members;
	}

	@Override
	public boolean add(LegionMember member) {
		if (member == null || member.getLegion() == null) {
			throw new IllegalArgumentException("Cannot enrol a character with no legion.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, member.getLegion().getLegionId());
			statement.setInt(2, member.getObjectId());
			statement.setString(3, member.getRank().toString());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to enrol character " + member.getObjectId() + " in legion "
					+ member.getLegion().getLegionId() + ".", e);
		}
	}

	@Override
	public boolean save(int playerId, LegionMember member) {
		if (member == null) {
			throw new IllegalArgumentException("Cannot store a null legion membership.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setString(1, member.getNickname());
			statement.setString(2, member.getRank().toString());
			statement.setString(3, member.getSelfIntro());
			statement.setInt(4, member.getChallengeScore());
			statement.setInt(5, playerId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to store the legion standing of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean remove(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, playerId);
			// The DAO caught the failure to bind this and ran the statement anyway.
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to take character " + playerId + " out of their legion.", e);
		}
	}
}
