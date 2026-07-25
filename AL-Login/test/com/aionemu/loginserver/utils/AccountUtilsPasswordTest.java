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
package com.aionemu.loginserver.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers account password hashing.
 * <p>
 * The previous scheme was an unsalted SHA-1 digest, which a commodity GPU walks
 * at billions of candidates per second and which one precomputed table cracks
 * across every account at once.
 *
 * @author Oraion
 */
class AccountUtilsPasswordTest {

	@Test
	@DisplayName("Accepts the password it hashed")
	void acceptsCorrectPassword() {
		String hash = AccountUtils.encodePassword("correct horse battery staple");

		assertTrue(AccountUtils.matches("correct horse battery staple", hash));
	}

	@Test
	@DisplayName("Rejects a wrong password")
	void rejectsWrongPassword() {
		String hash = AccountUtils.encodePassword("correct horse battery staple");

		assertFalse(AccountUtils.matches("Correct horse battery staple", hash));
	}

	@Test
	@DisplayName("Salts each hash, so the same password never yields the same string")
	void saltsEveryHash() {
		String first = AccountUtils.encodePassword("same password");
		String second = AccountUtils.encodePassword("same password");

		assertNotEquals(first, second, "Two hashes of one password must differ");
		assertTrue(AccountUtils.matches("same password", first));
		assertTrue(AccountUtils.matches("same password", second));
	}

	@Test
	@DisplayName("Produces a 60 character hash, which the password column holds")
	void producesHashThatFitsTheColumn() {
		// account_data.password is varchar(65).
		assertEquals(60, AccountUtils.encodePassword("any password").length());
	}

	@Test
	@DisplayName("Rejects a legacy SHA-1 digest instead of throwing")
	void rejectsLegacyDigest() {
		// Base64 of a SHA-1 digest, as accounts created before the move still hold.
		String legacy = "qUqP5cyxm6YcTAhz05Hph5gvu9M=";

		assertFalse(AccountUtils.matches("test", legacy));
	}

	@Test
	@DisplayName("Rejects a null or empty stored hash")
	void rejectsMissingStoredHash() {
		assertFalse(AccountUtils.matches("password", null));
		assertFalse(AccountUtils.matches("password", ""));
	}

	@Test
	@DisplayName("Rejects a null password")
	void rejectsNullPassword() {
		String hash = AccountUtils.encodePassword("password");

		assertFalse(AccountUtils.matches(null, hash));
	}

	@Test
	@DisplayName("Handles non-ASCII and long passwords")
	void handlesNonAsciiPassword() {
		String password = "mot de passe accentue: eaiou 12345678";
		String hash = AccountUtils.encodePassword(password);

		assertTrue(AccountUtils.matches(password, hash));
		assertFalse(AccountUtils.matches(password + "x", hash));
	}
}
