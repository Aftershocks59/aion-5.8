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
package com.aionemu.gameserver.geodata;

/**
 * Names one object whose collision can change shape, a door above all.
 * <p>
 * The world's materials file carries these after its tables. The original
 * loader matches a record to a live object on the editor id and the class name
 * together, and reads only that record's payload.
 *
 * @author Oraion
 */
public final class FieldObject {

	/**
	 * How long a record's payload runs. Every one of the 9658 records shipped
	 * across the 213 worlds carries exactly this many bytes, four little-endian
	 * ints in the pattern {@code [a, b, a, b]} where {@code a < b}.
	 */
	public static final int PAYLOAD_SIZE = 16;

	private final int editorId;
	private final String className;
	private final String name;
	private final byte[] payload;

	FieldObject(int editorId, String className, String name, byte[] payload) {
		this.editorId = editorId;
		this.className = className;
		this.name = name;
		this.payload = payload;
	}

	/** Answers the id the map editor gave this object. */
	public int getEditorId() {
		return editorId;
	}

	/** Answers the object's class, such as Door, AbyssDoor or PlaceableObject. */
	public String getClassName() {
		return className;
	}

	/**
	 * Answers the name the editor gave this object, such as AbyssDoor12.
	 * <p>
	 * The original loader reads this and throws it away; it is kept because it
	 * is the only thing that tells one door from another by eye.
	 */
	public String getName() {
		return name;
	}

	/** Answers the record's payload, whose meaning is not yet settled. */
	public byte[] getPayload() {
		return payload;
	}

	@Override
	public String toString() {
		return className + " " + name + " (editor id " + editorId + ")";
	}
}
