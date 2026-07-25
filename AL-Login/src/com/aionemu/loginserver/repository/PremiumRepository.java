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

import com.aionemu.commons.database.RepositoryException;

/**
 * Holds the two currencies an account carries outside the game world.
 *
 * @author Oraion
 */
public interface PremiumRepository {

	/**
	 * Reads the toll balance and claims one pending reward into it.
	 * <p>
	 * This both reads and writes, which its name does not admit. It is kept that
	 * way on purpose: the balance it returns travels to the game server and only
	 * comes back through {@code CM_ACCOUNT_TOLL_INFO}, so changing when a reward is
	 * claimed would change where currency can be lost. See the note on the
	 * implementation.
	 *
	 * @param accountId the account to read
	 * @return the balance, including the reward just claimed
	 * @throws RepositoryException if it could not be read
	 */
	long claimAndGetPoints(int accountId);

	/**
	 * Reads the luna balance.
	 *
	 * @param accountId the account to read
	 * @return its balance, zero when it has no row
	 * @throws RepositoryException if it could not be read
	 */
	long getLuna(int accountId);

	/**
	 * Sets the toll balance to what is left after a purchase.
	 *
	 * @param accountId the account to charge
	 * @param points    the balance before the purchase
	 * @param required  what the purchase costs
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean spendPoints(int accountId, long points, long required);

	/**
	 * Sets the luna balance.
	 *
	 * @param accountId the account to write
	 * @param luna      the balance to store
	 * @return true if a row was updated
	 * @throws RepositoryException if it could not be written
	 */
	boolean setLuna(int accountId, long luna);
}
