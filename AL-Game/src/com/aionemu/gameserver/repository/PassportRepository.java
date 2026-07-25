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

import java.sql.Timestamp;
import java.util.List;

import com.aionemu.commons.database.RepositoryException;

/**
 * Holds how far each account has got through its Atreian passports.
 *
 * @author Oraion
 */
public interface PassportRepository {

	/** Answers when an account has no row for the passport that was asked for. */
	int NO_STAMPS = 0;

	/**
	 * Reads which passports an account holds.
	 *
	 * @param accountId the account
	 * @return the passport ids, empty if the account holds none
	 * @throws RepositoryException if they could not be read
	 */
	List<Integer> findPassports(int accountId);

	/**
	 * Gives an account a passport.
	 *
	 * @param accountId  the account
	 * @param passportId the passport
	 * @param stamps     how many stamps it starts with
	 * @param lastStamp  when it was last stamped
	 * @return true if a row was written
	 * @throws RepositoryException if it could not be written
	 */
	boolean add(int accountId, int passportId, int stamps, Timestamp lastStamp);

	/**
	 * Writes an account's progress through a passport.
	 *
	 * @param accountId  the account
	 * @param passportId the passport
	 * @param stamps     how many stamps it now carries
	 * @param rewarded   whether the reward has been handed over
	 * @param lastStamp  when it was last stamped
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean update(int accountId, int passportId, int stamps, boolean rewarded, Timestamp lastStamp);

	/**
	 * Answers how many stamps an account has collected on a passport.
	 *
	 * @param accountId  the account
	 * @param passportId the passport
	 * @return the count, or {@link #NO_STAMPS} if the account has no such passport
	 * @throws RepositoryException if it could not be read
	 */
	int findStamps(int accountId, int passportId);

	/**
	 * Answers when an account last stamped a passport.
	 *
	 * @param accountId  the account
	 * @param passportId the passport
	 * @return the moment, or null if the account has no such passport or has never
	 *         stamped it
	 * @throws RepositoryException if it could not be read
	 */
	Timestamp findLastStamp(int accountId, int passportId);
}
