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
import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.house.PlayerHouseBid;

/**
 * Reads and writes the bids standing on the houses up for auction, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcHouseBidRepository extends JdbcRepositorySupport implements HouseBidRepository {

	private static final String SELECT_ALL = "SELECT `player_id`,`house_id`,`bid`,`bid_time` FROM `house_bids`"
			+ " ORDER BY `house_id`, `bid`";
	private static final String INSERT_ONE = "INSERT INTO `house_bids` (`player_id`,`house_id`,`bid`,`bid_time`) VALUES (?,?,?,?)";
	private static final String DELETE_FOR_HOUSE = "DELETE FROM `house_bids` WHERE `house_id` = ?";

	public JdbcHouseBidRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Set<PlayerHouseBid> findAll() {
		// Kept in the order the database answered, so the auction reads the same
		// way twice running.
		Set<PlayerHouseBid> bids = new LinkedHashSet<PlayerHouseBid>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				bids.add(new PlayerHouseBid(rows.getInt("player_id"), rows.getInt("house_id"), rows.getLong("bid"),
						rows.getTimestamp("bid_time")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the standing house bids.", e);
		}

		return bids;
	}

	@Override
	public boolean add(int playerId, int houseId, long offer, Timestamp at) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, playerId);
			statement.setInt(2, houseId);
			statement.setLong(3, offer);
			statement.setTimestamp(4, at);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException(
					"Failed to record the bid of character " + playerId + " on house " + houseId + ".", e);
		}
	}

	@Override
	public int removeAll(int houseId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_FOR_HOUSE)) {
			statement.setInt(1, houseId);
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to clear the bids on house " + houseId + ".", e);
		}
	}
}
