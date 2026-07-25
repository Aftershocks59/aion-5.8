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
import com.aionemu.gameserver.model.Announcement;

/**
 * Reads and writes the repeating announcements over JDBC.
 *
 * @author Oraion
 */
public final class JdbcAnnouncementRepository extends JdbcRepositorySupport implements AnnouncementRepository {

	private static final String SELECT_ALL = "SELECT `id`,`announce`,`faction`,`type`,`delay` "
			+ "FROM `announcements` ORDER BY `id`";
	private static final String INSERT_ONE = "INSERT INTO `announcements` (`announce`,`faction`,`type`,`delay`) VALUES (?,?,?,?)";
	private static final String DELETE_ONE = "DELETE FROM `announcements` WHERE `id` = ?";

	public JdbcAnnouncementRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Set<Announcement> findAll() {
		// Keep the reading order: the DAO asked for it and then dropped it into a
		// HashSet, so the rotation came out in whatever order the hashes fell.
		Set<Announcement> announcements = new LinkedHashSet<Announcement>();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				announcements.add(new Announcement(rows.getInt("id"), rows.getString("announce"),
						rows.getString("faction"), rows.getString("type"), rows.getInt("delay")));
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the announcements.", e);
		}
		return announcements;
	}

	@Override
	public boolean add(Announcement announcement) {
		if (announcement == null) {
			throw new IllegalArgumentException("Cannot store a null announcement.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ONE)) {
			statement.setString(1, announcement.getAnnounce());
			statement.setString(2, announcement.getFaction());
			statement.setString(3, announcement.getType());
			statement.setInt(4, announcement.getDelay());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to add an announcement.", e);
		}
	}

	@Override
	public boolean remove(int announcementId) {
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
			statement.setInt(1, announcementId);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to remove announcement " + announcementId + ".", e);
		}
	}
}
