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
package com.aionemu.gameserver.dataholders.loadingutils;

import java.util.List;
import java.util.Set;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Map;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.dataholders.StaticData;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.SerializerFactory;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.CollectionSerializer;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.serializers.MapSerializer;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;

import com.aionemu.commons.taskmanager.AbstractLockManager;

import gnu.trove.impl.hash.THash;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TShortObjectHashMap;


/**
 * Stores the parsed static data graph as a binary snapshot, and restores it
 * without going through XML.
 * <p>
 * Parsing the aggregate XML costs roughly half of the server start time. The
 * snapshot is written once, right after a successful parse, and reused on every
 * later start whose source XML files are unchanged. {@link XmlMerger} owns the
 * change detection: it compares the CRC of every source file against the
 * metadata it recorded, so touching any file under data/static_data invalidates
 * the snapshot.
 *
 * @author Oraion
 */
public final class BinaryStaticDataCache {

	private static final Logger log = LoggerFactory.getLogger(BinaryStaticDataCache.class);

	private static final File CACHE_FILE = new File("./cache/static_data.bin");

	/**
	 * Identifies the layout of the serialised graph. Bump it whenever the shape of
	 * the data holder classes changes, so an old snapshot is discarded instead of
	 * being read back into incompatible classes.
	 */
	private static final int FORMAT_VERSION = 9;

	/** Sizes the streaming buffers. The graph is large, so read and write in bulk. */
	private static final int BUFFER_SIZE = 1 << 20;

	private BinaryStaticDataCache() {
	}

	/**
	 * Returns the snapshot file, whether or not it exists.
	 *
	 * @return the snapshot location
	 */
	public static File getFile() {
		return CACHE_FILE;
	}

	/**
	 * Reports whether a snapshot is present and can be considered for loading.
	 *
	 * @return true when the snapshot file exists and is not empty
	 */
	public static boolean exists() {
		return CACHE_FILE.isFile() && CACHE_FILE.length() > 0;
	}

	/**
	 * Returns the instant the snapshot was written, used as the reference point for
	 * change detection against the source XML files.
	 *
	 * @return last modification time in milliseconds, or -1 when absent
	 */
	public static long lastModified() {
		return exists() ? CACHE_FILE.lastModified() : -1L;
	}

	/**
	 * Reads the snapshot back into a live object graph.
	 *
	 * @return the restored data, or null when the snapshot is absent, truncated or
	 *         written by an incompatible version
	 */
	public static StaticData load() {
		if (!exists()) {
			return null;
		}

		long start = System.currentTimeMillis();
		try (InputStream in = Files.newInputStream(CACHE_FILE.toPath());
				Input input = new Input(in, BUFFER_SIZE)) {

			int version = input.readInt();
			if (version != FORMAT_VERSION) {
				log.info("Static data snapshot has format {}, expected {}. Rebuilding from XML.", version,
						FORMAT_VERSION);
				return null;
			}

			StaticData data = newKryo().readObject(input, StaticData.class);
			log.info("Restored static data snapshot in {} ms ({} MB).", System.currentTimeMillis() - start,
					CACHE_FILE.length() / (1024 * 1024));
			return data;
		} catch (Exception e) {
			// A corrupt or stale snapshot must never stop the server: fall back to XML.
			log.warn("Could not read the static data snapshot, rebuilding from XML.", e);
			discard();
			return null;
		}
	}

	/**
	 * Writes the parsed graph so the next start can skip XML entirely.
	 * <p>
	 * A failure here is logged and swallowed: the server already holds the data it
	 * needs, and the only cost is a slower next start.
	 *
	 * @param data the freshly parsed graph
	 */
	public static void save(StaticData data) {
		if (data == null) {
			return;
		}

		long start = System.currentTimeMillis();
		File parent = CACHE_FILE.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}

		// Write to a temporary file and move it into place, so an interrupted run
		// never leaves a half-written snapshot behind.
		File temp = new File(CACHE_FILE.getPath() + ".tmp");
		try {
			try (OutputStream out = Files.newOutputStream(temp.toPath());
					Output output = new Output(out, BUFFER_SIZE)) {
				output.writeInt(FORMAT_VERSION);
				newKryo().writeObject(output, data);
			}

			Files.deleteIfExists(CACHE_FILE.toPath());
			Files.move(temp.toPath(), CACHE_FILE.toPath());
			log.info("Wrote static data snapshot in {} ms ({} MB).", System.currentTimeMillis() - start,
					CACHE_FILE.length() / (1024 * 1024));
		} catch (Exception e) {
			log.warn("Could not write the static data snapshot.", e);
			try {
				Files.deleteIfExists(temp.toPath());
			} catch (IOException ignored) {
				// Nothing useful to do if even the cleanup fails.
			}
		}
	}

	/** Removes the snapshot, forcing the next start to rebuild it from XML. */
	public static void discard() {
		try {
			Files.deleteIfExists(CACHE_FILE.toPath());
		} catch (IOException e) {
			log.warn("Could not delete the static data snapshot.", e);
		}
	}

	/**
	 * Builds a Kryo instance configured for this graph.
	 *
	 * @return a ready to use serialiser
	 */
	private static Kryo newKryo() {
		Kryo kryo = new Kryo();

		// The data holders are plain JAXB beans with no Kryo annotations, and the
		// graph is not known ahead of time, so resolve classes by name.
		kryo.setRegistrationRequired(false);

		// Templates are shared between holders, most visibly item and NPC templates
		// referenced from several indexes. Tracking references preserves that sharing
		// instead of duplicating every shared object.
		kryo.setReferences(true);

		// Several data holders declare no no-argument constructor. Objenesis allocates
		// them without calling one.
		kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));

		// Copy Trove containers through their public API. Their backing arrays cannot
		// be serialised field by field: Trove marks empty and removed slots with
		// sentinel objects it compares by identity, and a round trip creates fresh
		// instances of those sentinels. The map then disagrees with its own entry
		// count, which surfaces far away as an ArrayIndexOutOfBoundsException.
		kryo.addDefaultSerializer(THash.class, new TroveSerializerFactory());
		kryo.addDefaultSerializer(TIntArrayList.class, new TIntArrayListSerializer());

		// JAXB maps xs:date and xs:dateTime onto XMLGregorianCalendar, whose only
		// implementation lives inside the java.xml module. That module opens nothing
		// to the unnamed module, so reflecting over its fields throws. Carry the
		// lexical form instead, which is the value the XML held in the first place.
		kryo.addDefaultSerializer(XMLGregorianCalendar.class, new XmlGregorianCalendarSerializer());

		// Spawn indexes hold AbstractMap.SimpleEntry pairs. Its key field is private
		// final inside java.base, which opens nothing to the unnamed module, so go
		// through the public accessors rather than reflection.
		kryo.addDefaultSerializer(SimpleEntry.class, new SimpleEntrySerializer());

		// Spawn groups extend AbstractLockManager, which carries a read-write lock and
		// its two views. Locks are runtime state, not data, and java.base refuses
		// reflection over them. Skip the three fields and let the instance install a
		// fresh, self-consistent set on the way back in.
		kryo.addDefaultSerializer(AbstractLockManager.class, new LockManagerSerializerFactory());

		// Zone shapes keep their geometry in a GeneralPath, whose coordinate array is
		// a private transient field of java.awt.geom.Path2D. Reflection over it is
		// refused, and being transient it would be skipped anyway, which would restore
		// every zone polygon empty. Walk the path through its public iterator instead.
		kryo.addDefaultSerializer(Path2D.class, new Path2DSerializer());

		return kryo;
	}

	/**
	 * Serialises a {@link Path2D} as its sequence of segments.
	 */
	private static final class Path2DSerializer extends Serializer<Path2D> {

		/** Holds the segment kind followed by the six coordinates it may carry. */
		private static final int RECORD_LENGTH = 7;

		@Override
		public void write(Kryo kryo, Output output, Path2D path) {
			output.writeInt(path.getWindingRule());

			// Count is unknown up front, so buffer the segments before writing them.
			java.util.List<float[]> segments = new java.util.ArrayList<float[]>();
			float[] coordinates = new float[6];
			for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
				float[] record = new float[RECORD_LENGTH];
				record[0] = it.currentSegment(coordinates);
				System.arraycopy(coordinates, 0, record, 1, 6);
				segments.add(record);
			}

			output.writeInt(segments.size(), true);
			for (float[] record : segments) {
				output.writeFloats(record, 0, RECORD_LENGTH);
			}
		}

		@Override
		public Path2D read(Kryo kryo, Input input, Class<? extends Path2D> type) {
			GeneralPath path = new GeneralPath(input.readInt());
			int count = input.readInt(true);

			for (int i = 0; i < count; i++) {
				float[] r = input.readFloats(RECORD_LENGTH);
				switch ((int) r[0]) {
					case PathIterator.SEG_MOVETO:
						path.moveTo(r[1], r[2]);
						break;
					case PathIterator.SEG_LINETO:
						path.lineTo(r[1], r[2]);
						break;
					case PathIterator.SEG_QUADTO:
						path.quadTo(r[1], r[2], r[3], r[4]);
						break;
					case PathIterator.SEG_CUBICTO:
						path.curveTo(r[1], r[2], r[3], r[4], r[5], r[6]);
						break;
					case PathIterator.SEG_CLOSE:
						path.closePath();
						break;
					default:
						throw new IllegalStateException("Unknown path segment type " + (int) r[0]);
				}
			}
			return path;
		}
	}

	/**
	 * Picks the right serialiser for each Trove hash container, and refuses the ones
	 * nothing covers.
	 * <p>
	 * Failing loudly matters here: the fallback would write only the non-transient
	 * fields, restoring an empty container without any error, and the server would
	 * run on silently missing data.
	 */
	private static final class TroveSerializerFactory implements SerializerFactory<Serializer> {

		@Override
		public Serializer newSerializer(Kryo kryo, Class type) {
			if (TIntObjectHashMap.class.isAssignableFrom(type)) {
				return new TIntObjectHashMapSerializer();
			}
			if (TShortObjectHashMap.class.isAssignableFrom(type)) {
				return new TShortObjectHashMapSerializer();
			}
			if (TIntIntHashMap.class.isAssignableFrom(type)) {
				return new TIntIntHashMapSerializer();
			}
			if (Map.class.isAssignableFrom(type)) {
				return new MapSerializer<Map<?, ?>>();
			}
			if (Collection.class.isAssignableFrom(type)) {
				return new CollectionSerializer<Collection<?>>();
			}
			throw new IllegalStateException("No snapshot serialiser for Trove type " + type.getName()
					+ ". Add one rather than letting it round trip empty.");
		}

		@Override
		public boolean isSupported(Class type) {
			return THash.class.isAssignableFrom(type);
		}
	}

	/** Copies a {@link TIntObjectHashMap} entry by entry. */
	private static final class TIntObjectHashMapSerializer extends Serializer<TIntObjectHashMap<Object>> {

		@Override
		public void write(Kryo kryo, Output output, TIntObjectHashMap<Object> map) {
			int[] keys = map.keys();
			output.writeInt(keys.length, true);
			for (int key : keys) {
				output.writeInt(key);
				kryo.writeClassAndObject(output, map.get(key));
			}
		}

		@Override
		public TIntObjectHashMap<Object> read(Kryo kryo, Input input,
				Class<? extends TIntObjectHashMap<Object>> type) {
			int count = input.readInt(true);
			TIntObjectHashMap<Object> map = new TIntObjectHashMap<Object>(Math.max(count, 1));
			// Register before reading values: an entry may point back at this map.
			kryo.reference(map);
			for (int i = 0; i < count; i++) {
				int key = input.readInt();
				map.put(key, kryo.readClassAndObject(input));
			}
			return map;
		}
	}

	/** Copies a {@link TShortObjectHashMap} entry by entry. */
	private static final class TShortObjectHashMapSerializer extends Serializer<TShortObjectHashMap<Object>> {

		@Override
		public void write(Kryo kryo, Output output, TShortObjectHashMap<Object> map) {
			short[] keys = map.keys();
			output.writeInt(keys.length, true);
			for (short key : keys) {
				output.writeShort(key);
				kryo.writeClassAndObject(output, map.get(key));
			}
		}

		@Override
		public TShortObjectHashMap<Object> read(Kryo kryo, Input input,
				Class<? extends TShortObjectHashMap<Object>> type) {
			int count = input.readInt(true);
			TShortObjectHashMap<Object> map = new TShortObjectHashMap<Object>(Math.max(count, 1));
			kryo.reference(map);
			for (int i = 0; i < count; i++) {
				short key = input.readShort();
				map.put(key, kryo.readClassAndObject(input));
			}
			return map;
		}
	}

	/** Copies a {@link TIntIntHashMap} entry by entry. */
	private static final class TIntIntHashMapSerializer extends Serializer<TIntIntHashMap> {

		@Override
		public void write(Kryo kryo, Output output, TIntIntHashMap map) {
			int[] keys = map.keys();
			output.writeInt(keys.length, true);
			for (int key : keys) {
				output.writeInt(key);
				output.writeInt(map.get(key));
			}
		}

		@Override
		public TIntIntHashMap read(Kryo kryo, Input input, Class<? extends TIntIntHashMap> type) {
			int count = input.readInt(true);
			TIntIntHashMap map = new TIntIntHashMap(Math.max(count, 1));
			kryo.reference(map);
			for (int i = 0; i < count; i++) {
				map.put(input.readInt(), input.readInt());
			}
			return map;
		}
	}

	/** Copies a {@link TIntArrayList} through its primitive array. */
	private static final class TIntArrayListSerializer extends Serializer<TIntArrayList> {

		@Override
		public void write(Kryo kryo, Output output, TIntArrayList list) {
			int[] values = list.toArray();
			output.writeInt(values.length, true);
			output.writeInts(values, 0, values.length);
		}

		@Override
		public TIntArrayList read(Kryo kryo, Input input, Class<? extends TIntArrayList> type) {
			int count = input.readInt(true);
			return new TIntArrayList(input.readInts(count));
		}
	}

	/** Produces a {@link LockManagerSerializer} for each lock manager subclass. */
	private static final class LockManagerSerializerFactory implements SerializerFactory<LockManagerSerializer> {

		@Override
		public LockManagerSerializer newSerializer(Kryo kryo, Class type) {
			return new LockManagerSerializer(kryo, type);
		}

		@Override
		public boolean isSupported(Class type) {
			return AbstractLockManager.class.isAssignableFrom(type);
		}
	}

	/**
	 * Serialises a lock manager without its locks, then restores them.
	 */
	private static final class LockManagerSerializer extends FieldSerializer<AbstractLockManager> {

		LockManagerSerializer(Kryo kryo, Class type) {
			super(kryo, type);
			removeField("lock");
			removeField("writeLock");
			removeField("readLock");
		}

		@Override
		public AbstractLockManager read(Kryo kryo, Input input, Class<? extends AbstractLockManager> type) {
			AbstractLockManager result = super.read(kryo, input, type);
			// Objenesis allocated the instance without running a constructor, so the
			// lock fields are null until this call.
			result.resetLocks();
			return result;
		}
	}

	/**
	 * Serialises a {@link SimpleEntry} through its public key and value accessors.
	 */
	private static final class SimpleEntrySerializer extends Serializer<SimpleEntry<Object, Object>> {

		@Override
		public void write(Kryo kryo, Output output, SimpleEntry<Object, Object> entry) {
			kryo.writeClassAndObject(output, entry.getKey());
			kryo.writeClassAndObject(output, entry.getValue());
		}

		@Override
		public SimpleEntry<Object, Object> read(Kryo kryo, Input input,
				Class<? extends SimpleEntry<Object, Object>> type) {
			Object key = kryo.readClassAndObject(input);
			Object value = kryo.readClassAndObject(input);
			return new SimpleEntry<Object, Object>(key, value);
		}
	}

	/**
	 * Serialises an {@link XMLGregorianCalendar} through its lexical representation.
	 */
	private static final class XmlGregorianCalendarSerializer extends Serializer<XMLGregorianCalendar> {

		private final DatatypeFactory factory;

		XmlGregorianCalendarSerializer() {
			try {
				factory = DatatypeFactory.newInstance();
			} catch (DatatypeConfigurationException e) {
				throw new IllegalStateException("Could not create a DatatypeFactory", e);
			}
		}

		@Override
		public void write(Kryo kryo, Output output, XMLGregorianCalendar calendar) {
			output.writeString(calendar.toXMLFormat());
		}

		@Override
		public XMLGregorianCalendar read(Kryo kryo, Input input, Class<? extends XMLGregorianCalendar> type) {
			return factory.newXMLGregorianCalendar(input.readString());
		}
	}
}
