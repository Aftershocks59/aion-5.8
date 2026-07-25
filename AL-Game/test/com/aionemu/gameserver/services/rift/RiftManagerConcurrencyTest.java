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
package com.aionemu.gameserver.services.rift;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.gameserver.model.gameobjects.Npc;

/**
 * Covers walking the spawned rifts while one of them closes.
 * <p>
 * The list is written when a rift spawns and when RVController despawns one, and
 * read by the announce task that builds SM_RIFT_ANNOUNCE. Those run on different
 * threads, so a plain list threw ConcurrentModificationException out of
 * RiftInformer and the players in that map were told nothing about the rifts.
 * The list RiftInformer built for its own answer had been made copy-on-write,
 * which cost a copy per rift and guarded nothing, since it never left the
 * caller.
 * <p>
 * These go through RiftManager.getSpawned on purpose: it hands back the live
 * list, so swapping the field back to a plain one fails them.
 *
 * @author Oraion
 */
class RiftManagerConcurrencyTest {

	private static final int WORLD = 110010000;
	private static final int OTHER_WORLD = 120010000;

	@BeforeEach
	@AfterEach
	void clearSpawnedRifts() {
		RiftManager.getSpawned().clear();
	}

	/** Builds a rift that only has to answer where it lives. */
	private static Npc riftIn(int worldId) {
		Npc npc = mock(Npc.class);
		when(npc.getWorldId()).thenReturn(worldId);
		return npc;
	}

	@Test
	@DisplayName("Walks the spawned rifts while one is removed")
	void toleratesRemovalWhileWalking() {
		List<Npc> spawned = RiftManager.getSpawned();
		Npc first = riftIn(WORLD);
		spawned.add(first);
		spawned.add(riftIn(WORLD));
		spawned.add(riftIn(WORLD));

		Iterator<Npc> walk = spawned.iterator();
		walk.next();
		// Close a rift halfway through, the way RVController does on despawn.
		spawned.remove(first);

		assertDoesNotThrow(() -> {
			while (walk.hasNext()) {
				walk.next();
			}
		});
	}

	@Test
	@DisplayName("Keeps only the rifts of the requested world")
	void filtersByWorld() {
		RiftManager.getSpawned().add(riftIn(WORLD));
		RiftManager.getSpawned().add(riftIn(OTHER_WORLD));
		RiftManager.getSpawned().add(riftIn(WORLD));

		assertEquals(2, RiftInformer.getSpawned(WORLD).size());
		assertEquals(1, RiftInformer.getSpawned(OTHER_WORLD).size());
	}

	@Test
	@DisplayName("Answers an empty list when no rift is up")
	void answersEmptyWhenNothingSpawned() {
		assertTrue(RiftInformer.getSpawned(WORLD).isEmpty());
	}

	@Test
	@DisplayName("Builds the world list while rifts open and close on another thread")
	void survivesConcurrentSpawnAndDespawn() throws InterruptedException {
		List<Npc> spawned = RiftManager.getSpawned();
		for (int i = 0; i < 50; i++) {
			spawned.add(riftIn(WORLD));
		}

		AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		CountDownLatch done = new CountDownLatch(1);

		Thread churn = new Thread(() -> {
			try {
				for (int i = 0; i < 500; i++) {
					Npc rift = riftIn(WORLD);
					spawned.add(rift);
					spawned.remove(rift);
				}
			} catch (Throwable t) {
				failure.compareAndSet(null, t);
			} finally {
				done.countDown();
			}
		});
		churn.start();

		try {
			for (int i = 0; i < 500; i++) {
				RiftInformer.getSpawned(WORLD);
			}
		} catch (Throwable t) {
			failure.compareAndSet(null, t);
		}

		assertTrue(done.await(30, TimeUnit.SECONDS), "The writing thread never finished.");
		churn.join();
		if (failure.get() != null) {
			throw new AssertionError("Walking the rifts raced with a rift closing.", failure.get());
		}
	}

	@Test
	@DisplayName("Hands back a list the caller may keep for itself")
	void answersAListTheCallerOwns() {
		RiftManager.getSpawned().add(riftIn(WORLD));

		List<Npc> answer = RiftInformer.getSpawned(WORLD);
		answer.clear();

		// Clearing the answer must not empty what RiftManager holds.
		assertEquals(1, RiftManager.getSpawned().size());
	}
}
