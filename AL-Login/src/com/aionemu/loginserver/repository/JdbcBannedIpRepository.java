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
package com.aionemu.loginserver.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.loginserver.model.BannedIP;

/**
 * Reads and writes the banned address masks over JDBC.
 *
 * @author Oraion
 */
public final class JdbcBannedIpRepository extends JdbcRepositorySupport implements BannedIpRepository {

	private static final String SELECT_ALL = "SELECT `id`,`mask`,`time_end` FROM `banned_ip`";
	private static final String INSERT_ONE = "INSERT INTO `banned_ip` (`mask`,`time_end`) VALUES (?,?)";
	private static final String UPDATE_ONE = "UPDATE `banned_ip` SET `mask` = ?, `time_end` = ? WHERE `id` = ?";
	private static final String DELETE_ONE = "DELETE FROM `banned_ip` WHERE `mask` = ?";
	private static final String DELETE_EXPIRED = "DELETE FROM `banned_ip` "
			+ "WHERE `time_end` < CURRENT_TIMESTAMP AND `time_end` IS NOT NULL";

	public JdbcBannedIpRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Set<BannedIP> findAll() {
		Set<BannedIP> bans = new LinkedHashSet<BannedIP>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				BannedIP ban = new BannedIP();
				ban.setId(Integer.valueOf(rows.getInt("id")));
				ban.setMask(rows.getString("mask"));
				ban.setTimeEnd(rows.getTimestamp("time_end"));
				bans.add(ban);
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the banned addresses.", e);
		}
		return bans;
	}

	@Override
	public BannedIP ban(String mask, Timestamp expiry) {
		BannedIP ban = new BannedIP();
		ban.setMask(mask);
		ban.setTimeEnd(expiry);
		return save(ban) ? ban : null;
	}

	/**
	 * Stores a ban and hands it the id the database assigned.
	 * <p>
	 * The DAO this replaces read the id back into a local object it then threw
	 * away, so the caller's ban kept a null id. Callers choose between storing and
	 * updating on exactly that, so a ban stored twice was stored twice rather than
	 * updated. Asking for the generated key settles it in one round trip.
	 */
	@Override
	public boolean save(BannedIP bannedIP) {
		if (bannedIP == null) {
			throw new IllegalArgumentException("Cannot store a null ban.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE,
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, bannedIP.getMask());
			setExpiry(statement, 2, bannedIP.getTimeEnd());
			if (statement.executeUpdate() == 0) {
				return false;
			}

			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (keys.next()) {
					bannedIP.setId(Integer.valueOf(keys.getInt(1)));
				}
			}
			return true;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to ban the address mask " + bannedIP.getMask() + ".", e);
		}
	}

	@Override
	public boolean update(BannedIP bannedIP) {
		if (bannedIP == null) {
			throw new IllegalArgumentException("Cannot update a null ban.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(UPDATE_ONE)) {
			statement.setString(1, bannedIP.getMask());
			setExpiry(statement, 2, bannedIP.getTimeEnd());
			statement.setInt(3, bannedIP.getId().intValue());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to update the ban on " + bannedIP.getMask() + ".", e);
		}
	}

	@Override
	public boolean remove(String mask) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setString(1, mask);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to lift the ban on " + mask + ".", e);
		}
	}

	@Override
	public int removeExpired() {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_EXPIRED)) {
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Failed to drop the expired address bans.", e);
		}
	}

	/** Writes the end date, or a null of the right type for a permanent ban. */
	private static void setExpiry(PreparedStatement statement, int index, Timestamp expiry) throws SQLException {
		if (expiry == null) {
			statement.setNull(index, Types.TIMESTAMP);
		} else {
			statement.setTimestamp(index, expiry);
		}
	}
}
