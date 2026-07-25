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
package com.aionemu.commons.database;

/**
 * Reports that a repository could not do what it was asked.
 * <p>
 * The DAO layer this replaces caught every SQLException, logged it and answered
 * with an empty result. A caller could not tell an empty table from a database
 * that never answered, which for anything guarding access means a failure reads
 * as permission granted. Carrying the failure lets each caller decide, and the
 * ones that genuinely want to carry on can still say so.
 *
 * @author Oraion
 */
public class RepositoryException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RepositoryException(String message, Throwable cause) {
		super(message, cause);
	}

	public RepositoryException(String message) {
		super(message);
	}
}
