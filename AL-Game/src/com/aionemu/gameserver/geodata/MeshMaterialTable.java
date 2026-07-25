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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds what the collision meshes of one world are made of, and the objects
 * whose collision can change shape.
 * <p>
 * Three tables come first, each read as a block: one entry per material, then
 * two per group. What is inside an entry is not settled, so they are kept as
 * they were read. The shape-changing objects follow, and those are readable.
 *
 * @author Oraion
 */
public final class MeshMaterialTable {

	/** The file a world's materials are read from. */
	public static final String FILE_NAME = "MeshMaterial.Dat";

	/** How many bytes one material entry runs to. */
	public static final int MATERIAL_ENTRY_SIZE = 12;

	/** How many bytes one group entry runs to, in the first of the two group tables. */
	public static final int GROUP_ENTRY_SIZE = 8;

	private final GeoVersion version;
	private final int materialCount;
	private final byte[] materials;
	private final int groupCount;
	private final byte[] groups;
	private final byte[] groupFlags;
	private final List<FieldObject> fieldObjects;

	private MeshMaterialTable(GeoVersion version, int materialCount, byte[] materials, int groupCount, byte[] groups,
			byte[] groupFlags, List<FieldObject> fieldObjects) {
		this.version = version;
		this.materialCount = materialCount;
		this.materials = materials;
		this.groupCount = groupCount;
		this.groups = groups;
		this.groupFlags = groupFlags;
		this.fieldObjects = Collections.unmodifiableList(fieldObjects);
	}

	/**
	 * Reads the materials of one world.
	 *
	 * @param worldDirectory the world's geodata directory
	 * @return the materials, never null
	 * @throws IOException if the file is missing or does not read as expected
	 */
	public static MeshMaterialTable load(Path worldDirectory) throws IOException {
		Path file = worldDirectory.resolve(FILE_NAME);
		ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
		GeoVersion version = GeoVersion.readHeader(buffer);

		int materialCount = buffer.getInt();
		byte[] materials = read(buffer, materialCount, MATERIAL_ENTRY_SIZE, file, "materials");

		// A world with no material carries no table at all, and nothing after it.
		if (materialCount == 0) {
			return new MeshMaterialTable(version, 0, materials, 0, new byte[0], new byte[0],
					new ArrayList<FieldObject>());
		}

		int groupCount = buffer.getInt();
		byte[] groups = read(buffer, groupCount, GROUP_ENTRY_SIZE, file, "groups");
		byte[] groupFlags = read(buffer, groupCount, 1, file, "group flags");

		List<FieldObject> fieldObjects = readFieldObjects(buffer, file);
		if (buffer.hasRemaining()) {
			throw new IOException("Left " + buffer.remaining() + " bytes unread at the end of " + file + ".");
		}

		return new MeshMaterialTable(version, materialCount, materials, groupCount, groups, groupFlags, fieldObjects);
	}

	private static byte[] read(ByteBuffer buffer, int count, int entrySize, Path file, String what)
			throws IOException {
		long size = (long) count * entrySize;
		if (count < 0 || size > buffer.remaining()) {
			throw new IOException("Read a count of " + count + " " + what + " from " + file
					+ ", which the file is too short to hold.");
		}
		byte[] block = new byte[(int) size];
		buffer.get(block);
		return block;
	}

	private static List<FieldObject> readFieldObjects(ByteBuffer buffer, Path file) throws IOException {
		List<FieldObject> objects = new ArrayList<FieldObject>();
		if (!buffer.hasRemaining()) {
			return objects;
		}

		int count = buffer.getInt();
		if (count < 0) {
			throw new IOException("Read a negative shape-changing object count from " + file + ".");
		}

		for (int i = 0; i < count; i++) {
			int editorId = buffer.getInt();
			String className = readString(buffer, file);
			String name = readString(buffer, file);
			int payloadSize = buffer.getInt();
			if (payloadSize < 0 || payloadSize > buffer.remaining()) {
				throw new IOException("Read a payload of " + payloadSize + " bytes for object " + editorId + " in "
						+ file + ", which the file is too short to hold.");
			}
			byte[] payload = new byte[payloadSize];
			buffer.get(payload);
			objects.add(new FieldObject(editorId, className, name, payload));
		}

		return objects;
	}

	/** Reads one of the two names a record carries, up to its terminating zero. */
	private static String readString(ByteBuffer buffer, Path file) throws IOException {
		int start = buffer.position();
		while (buffer.hasRemaining()) {
			if (buffer.get() == 0) {
				int length = buffer.position() - start - 1;
				byte[] bytes = new byte[length];
				buffer.position(start);
				buffer.get(bytes);
				buffer.get();
				return new String(bytes, StandardCharsets.US_ASCII);
			}
		}
		throw new IOException("Ran off the end of " + file + " reading a name.");
	}

	public GeoVersion getVersion() {
		return version;
	}

	public int getMaterialCount() {
		return materialCount;
	}

	/** Answers the material table as it was read, {@value #MATERIAL_ENTRY_SIZE} bytes an entry. */
	public byte[] getMaterials() {
		return materials;
	}

	public int getGroupCount() {
		return groupCount;
	}

	/** Answers the first group table as it was read, {@value #GROUP_ENTRY_SIZE} bytes an entry. */
	public byte[] getGroups() {
		return groups;
	}

	/** Answers the second group table as it was read, one byte an entry. */
	public byte[] getGroupFlags() {
		return groupFlags;
	}

	/** Answers the objects whose collision can change shape, doors among them. */
	public List<FieldObject> getFieldObjects() {
		return fieldObjects;
	}
}
