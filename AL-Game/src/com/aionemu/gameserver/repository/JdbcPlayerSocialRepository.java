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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.BlockList;
import com.aionemu.gameserver.model.gameobjects.player.BlockedPlayer;
import com.aionemu.gameserver.model.gameobjects.player.Friend;
import com.aionemu.gameserver.model.gameobjects.player.FriendList;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;

/**
 * Reads and writes a character's friends and blocks over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPlayerSocialRepository extends JdbcRepositorySupport implements PlayerSocialRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcPlayerSocialRepository.class);

	private static final String SELECT_FRIENDS = "SELECT `friend` FROM `friends` WHERE `player` = ?";
	private static final String INSERT_FRIEND = "INSERT INTO `friends` (`player`,`friend`) VALUES (?,?)";
	private static final String DELETE_FRIEND = "DELETE FROM `friends` WHERE `player` = ? AND `friend` = ?";
	private static final String UPDATE_NOTE = "UPDATE `friends` SET `note` = ? WHERE `player` = ? AND `friend` = ?";

	private static final String SELECT_BLOCKED = "SELECT `blocked_player`,`reason` FROM `blocks` WHERE `player` = ?";
	private static final String INSERT_BLOCK = "INSERT INTO `blocks` (`player`,`blocked_player`,`reason`) VALUES (?,?,?)";
	private static final String DELETE_BLOCK = "DELETE FROM `blocks` WHERE `player` = ? AND `blocked_player` = ?";
	private static final String UPDATE_REASON = "UPDATE `blocks` SET `reason` = ? WHERE `player` = ? AND `blocked_player` = ?";

	public JdbcPlayerSocialRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public FriendList findFriends(Player player, CharacterLookup lookup) {
		List<Friend> friends = new ArrayList<Friend>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_FRIENDS)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					int friendId = rows.getInt("friend");
					PlayerCommonData friend = lookup.find(friendId);
					if (friend == null) {
						log.error("The friend list of " + player.getName() + " names character " + friendId
								+ ", which no longer exists.");
						continue;
					}
					friends.add(new Friend(friend));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the friends of character " + player.getObjectId() + ".", e);
		}
		return new FriendList(player, friends);
	}

	@Override
	public boolean addFriend(int playerId, int friendId) {
		// Both directions in one transaction: the DAO batched them on one statement
		// but nothing rolled the first back if the second failed, so a friendship
		// could end up recorded on one side only.
		return inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(INSERT_FRIEND)) {
				bindPair(statement, playerId, friendId);
				int[] written = statement.executeBatch();
				return Boolean.valueOf(written.length == 2);
			}
		}, "Failed to record the friendship between " + playerId + " and " + friendId + ".").booleanValue();
	}

	@Override
	public boolean removeFriend(int playerId, int friendId) {
		return inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(DELETE_FRIEND)) {
				bindPair(statement, playerId, friendId);
				int[] removed = statement.executeBatch();
				return Boolean.valueOf(removed.length == 2);
			}
		}, "Failed to end the friendship between " + playerId + " and " + friendId + ".").booleanValue();
	}

	@Override
	public void setFriendNote(int playerId, int friendId, String note) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_NOTE)) {
			statement.setString(1, note);
			statement.setInt(2, playerId);
			statement.setInt(3, friendId);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to write the note character " + playerId + " keeps on " + friendId + ".", e);
		}
	}

	@Override
	public BlockList findBlocked(Player player, CharacterLookup lookup) {
		Map<Integer, BlockedPlayer> blocked = new LinkedHashMap<Integer, BlockedPlayer>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_BLOCKED)) {
			statement.setInt(1, player.getObjectId());
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					int blockedId = rows.getInt("blocked_player");
					PlayerCommonData other = lookup.find(blockedId);
					if (other == null) {
						log.error("The block list of " + player.getName() + " names character " + blockedId
								+ ", which no longer exists.");
						continue;
					}
					blocked.put(Integer.valueOf(blockedId),
							new BlockedPlayer(other, rows.getString("reason")));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to read the block list of character " + player.getObjectId() + ".", e);
		}
		return new BlockList(blocked);
	}

	@Override
	public boolean block(int playerId, int blockedId, String reason) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_BLOCK)) {
			statement.setInt(1, playerId);
			statement.setInt(2, blockedId);
			statement.setString(3, reason);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to let character " + playerId + " block " + blockedId + ".", e);
		}
	}

	@Override
	public boolean unblock(int playerId, int blockedId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_BLOCK)) {
			statement.setInt(1, playerId);
			statement.setInt(2, blockedId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to let character " + playerId + " unblock " + blockedId + ".", e);
		}
	}

	@Override
	public boolean setBlockReason(int playerId, int blockedId, String reason) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_REASON)) {
			statement.setString(1, reason);
			statement.setInt(2, playerId);
			statement.setInt(3, blockedId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to rewrite why character " + playerId + " blocked " + blockedId + ".", e);
		}
	}

	/** Queues the same pair of characters both ways round. */
	private static void bindPair(PreparedStatement statement, int first, int second) throws SQLException {
		statement.setInt(1, first);
		statement.setInt(2, second);
		statement.addBatch();
		statement.setInt(1, second);
		statement.setInt(2, first);
		statement.addBatch();
	}
}
