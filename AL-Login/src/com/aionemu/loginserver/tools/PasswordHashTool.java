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
package com.aionemu.loginserver.tools;

import com.aionemu.loginserver.utils.AccountUtils;

/**
 * Prints the bcrypt hash of a password, for pasting into account_data.password.
 * <p>
 * Serves password resets and the migration off SHA-1: accounts created before
 * bcrypt hold a digest the server can no longer verify, and their owners cannot
 * log in until the stored hash is replaced.
 * <p>
 * Reads the password from the AION_PASSWORD environment variable rather than
 * from an argument, so it does not land in the shell history or in the process
 * list where any local user could read it.
 *
 * @author Oraion
 */
public final class PasswordHashTool {

	private PasswordHashTool() {
	}

	/**
	 * Prints the hash of the password held in AION_PASSWORD.
	 *
	 * @param args ignored
	 */
	public static void main(String[] args) {
		String password = System.getenv("AION_PASSWORD");

		if (password == null || password.isEmpty()) {
			System.err.println("Set AION_PASSWORD to the password you want to hash.");
			System.err.println("Example:");
			System.err.println("  AION_PASSWORD=secret ./gradlew :AL-Login:hashPassword");
			System.exit(1);
			return;
		}

		System.out.println(AccountUtils.encodePassword(password));
	}
}
