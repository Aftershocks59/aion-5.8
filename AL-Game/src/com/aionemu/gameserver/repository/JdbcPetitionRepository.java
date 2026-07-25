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
import java.util.LinkedHashSet;
import java.util.Set;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.Petition;
import com.aionemu.gameserver.model.PetitionStatus;

/**
 * Reads and writes the petitions players have raised, over JDBC.
 *
 * @author Oraion
 */
public final class JdbcPetitionRepository extends JdbcRepositorySupport implements PetitionRepository {

	private static final String SELECT_NEXT_ID = "SELECT MAX(`id`) AS `highest` FROM `petitions`";
	private static final String SELECT_OPEN = "SELECT `id`,`player_id`,`type`,`title`,`message`,`add_data`,`status`"
			+ " FROM `petitions` WHERE `status` IN ('PENDING','IN_PROGRESS') ORDER BY `id` ASC";
	private static final String SELECT_ONE = "SELECT `id`,`player_id`,`type`,`title`,`message`,`add_data`,`status`"
			+ " FROM `petitions` WHERE `id` = ?";
	private static final String INSERT_ONE = "INSERT INTO `petitions`"
			+ " (`id`,`player_id`,`type`,`title`,`message`,`add_data`,`time`,`status`) VALUES (?,?,?,?,?,?,?,?)";
	private static final String DELETE_OPEN_FOR = "DELETE FROM `petitions` WHERE `player_id` = ?"
			+ " AND `status` IN ('PENDING','IN_PROGRESS')";
	private static final String MARK_REPLIED = "UPDATE `petitions` SET `status` = 'REPLIED' WHERE `id` = ?";

	public JdbcPetitionRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public int nextId() {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_NEXT_ID);
				ResultSet rows = statement.executeQuery()) {
			// An empty table answers one row carrying NULL, which reads as zero.
			return rows.next() ? rows.getInt("highest") + 1 : 1;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the next petition id.", e);
		}
	}

	@Override
	public Set<Petition> findOpen() {
		// Kept in the order the query asked for, so the oldest petition is answered
		// first. The DAO dropped them into a HashSet and lost that.
		Set<Petition> open = new LinkedHashSet<Petition>();

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_OPEN);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				open.add(read(rows));
			}
		} catch (SQLException e) {
			// The DAO answered null here, and its caller walked it straight into a
			// null pointer.
			throw new RepositoryException("Failed to read the open petitions.", e);
		}

		return open;
	}

	@Override
	public Petition findById(int petitionId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, petitionId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? read(rows) : null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read petition " + petitionId + ".", e);
		}
	}

	@Override
	public boolean add(Petition petition, long raisedAt) {
		if (petition == null) {
			throw new IllegalArgumentException("Cannot store a null petition.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setInt(1, petition.getPetitionId());
			statement.setInt(2, petition.getPlayerObjId());
			statement.setInt(3, petition.getPetitionType().getElementId());
			statement.setString(4, petition.getTitle());
			statement.setString(5, petition.getContentText());
			statement.setString(6, petition.getAdditionalData());
			statement.setLong(7, raisedAt);
			statement.setString(8, petition.getStatus().toString());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to store petition " + petition.getPetitionId() + ".", e);
		}
	}

	@Override
	public int removeOpenFor(int playerId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_OPEN_FOR)) {
			statement.setInt(1, playerId);
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to withdraw the petitions of character " + playerId + ".", e);
		}
	}

	@Override
	public boolean markReplied(int petitionId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(MARK_REPLIED)) {
			statement.setInt(1, petitionId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to record that petition " + petitionId + " was answered.", e);
		}
	}

	private static Petition read(ResultSet rows) throws SQLException {
		return new Petition(rows.getInt("id"), rows.getInt("player_id"), rows.getInt("type"), rows.getString("title"),
				rows.getString("message"), rows.getString("add_data"), status(rows.getString("status")).getElementId());
	}

	/** Anything the enum does not know reads as still waiting, as the DAO had it. */
	private static PetitionStatus status(String stored) {
		if (PetitionStatus.IN_PROGRESS.toString().equals(stored)) {
			return PetitionStatus.IN_PROGRESS;
		}
		return PetitionStatus.PENDING;
	}
}
