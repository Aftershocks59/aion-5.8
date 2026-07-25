/**
 * This file is part of Aion-Lightning <aion-lightning.org>.
 *
 *  Aion-Lightning is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Aion-Lightning is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details. *
 *  You should have received a copy of the GNU General Public License
 *  along with Aion-Lightning.
 *  If not, see <http://www.gnu.org/licenses/>.
 */


package com.aionemu.loginserver.utils;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Class with usefull methods to use with accounts
 *
 * @author SoulKeeper
 */
public class AccountUtils {

    /**
     * Logger :)
     */
    private static final Logger log = LoggerFactory.getLogger(AccountUtils.class);

    /**
     * Cost of the bcrypt work factor. Each increment doubles the time to hash and
     * to attack. 12 keeps a single check in the low tens of milliseconds on current
     * hardware, which is negligible next to a login round trip.
     */
    private static final int BCRYPT_COST = 12;

    /**
     * Hashes a password with bcrypt.
     * <p>
     * Replaces an unsalted SHA-1 digest. That construction was broken twice over:
     * SHA-1 is fast by design, so a commodity GPU walks billions of candidates per
     * second, and without a salt one precomputed table cracks every account at
     * once. bcrypt salts each hash and is deliberately slow.
     * <p>
     * The result embeds its own salt and cost, so two calls on the same password
     * return different strings. Never compare hashes with equals: use
     * {@link #matches(String, String)}.
     *
     * @param password password to hash
     * @return a 60 character bcrypt hash
     */
    public static String encodePassword(String password) {
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray());
    }

    /**
     * Checks a password against a stored hash.
     *
     * @param password  password as typed by the account holder
     * @param storedHash hash held in the database
     * @return true only when the password produced that hash
     */
    public static boolean matches(String password, String storedHash) {
        if (password == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }

        try {
            return BCrypt.verifyer().verify(password.getBytes(StandardCharsets.UTF_8),
                    storedHash.getBytes(StandardCharsets.UTF_8)).verified;
        } catch (IllegalArgumentException e) {
            // Reject rather than fail: accounts created before the move still hold a
            // SHA-1 digest, which is not a bcrypt hash. Their owners must have their
            // password reset.
            log.warn("Account holds a hash bcrypt cannot read; the password must be reset.");
            return false;
        }
    }
}
