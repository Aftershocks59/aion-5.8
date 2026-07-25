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
package com.aionemu.gameserver.taskmanager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers draining the FIFO task queue.
 * <p>
 * The drain loop used to read as {@code (task = removeFirst()) != null}, which
 * held only while the queue returned null when empty. On a java.util collection
 * removeFirst throws instead, so the last pass threw NoSuchElementException, and
 * a scheduled task that throws is cancelled for good: the manager stopped for
 * the lifetime of the process the first time its queue ran dry. It runs when a
 * player connects, so no server boot revealed it.
 *
 * @author Oraion
 */
class AbstractFIFOPeriodicTaskManagerTest {

	/** Records what it was asked to run, so order and count are observable. */
	private static class RecordingTaskManager extends AbstractFIFOPeriodicTaskManager<String> {

		private final List<String> executed = new ArrayList<String>();

		RecordingTaskManager() {
			super(1000);
		}

		@Override
		protected void callTask(String task) {
			executed.add(task);
		}

		@Override
		protected String getCalledMethodName() {
			return "callTask(String)";
		}
	}

	@Test
	@DisplayName("Runs on an empty queue without throwing")
	void drainsEmptyQueue() {
		RecordingTaskManager manager = new RecordingTaskManager();

		assertDoesNotThrow(manager::run);
		assertEquals(0, manager.executed.size());
	}

	@Test
	@DisplayName("Runs every queued task, in the order they arrived")
	void drainsInFifoOrder() {
		RecordingTaskManager manager = new RecordingTaskManager();
		manager.add("first");
		manager.add("second");
		manager.add("third");

		manager.run();

		assertIterableEquals(List.of("first", "second", "third"), manager.executed);
	}

	@Test
	@DisplayName("Leaves nothing behind, so a second run does nothing")
	void drainsCompletely() {
		RecordingTaskManager manager = new RecordingTaskManager();
		manager.add("only");

		manager.run();
		assertDoesNotThrow(manager::run);

		assertIterableEquals(List.of("only"), manager.executed);
	}

	@Test
	@DisplayName("Keeps draining after a task throws")
	void survivesAThrowingTask() {
		RecordingTaskManager manager = new RecordingTaskManager() {
			@Override
			protected void callTask(String task) {
				if ("boom".equals(task)) {
					throw new IllegalStateException("task failed");
				}
				super.callTask(task);
			}
		};
		manager.add("before");
		manager.add("boom");
		manager.add("after");

		assertDoesNotThrow(manager::run);

		assertIterableEquals(List.of("before", "after"), manager.executed);
	}

	@Test
	@DisplayName("Ignores a task added twice before a run, as a set does")
	void deduplicatesQueuedTasks() {
		RecordingTaskManager manager = new RecordingTaskManager();
		manager.add("same");
		manager.add("same");

		manager.run();

		assertIterableEquals(List.of("same"), manager.executed);
	}
}
