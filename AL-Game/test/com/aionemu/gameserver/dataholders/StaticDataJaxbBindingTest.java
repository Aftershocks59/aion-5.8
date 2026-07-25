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
package com.aionemu.gameserver.dataholders;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the JAXB binding of the whole static data graph.
 * <p>
 * Building the context walks every annotated field of every data holder, so a
 * field whose type stops being bindable fails here in under a second. Without
 * this the same mistake only surfaces after a full server start, as a bare
 * "1 counts of IllegalAnnotationExceptions" with no indication of the culprit,
 * followed much later by a NullPointerException on a null data graph.
 *
 * @author Oraion
 */
class StaticDataJaxbBindingTest {

	@Test
	@DisplayName("JAXB context builds for the whole static data graph")
	void jaxbContextBuildsForStaticData() {
		try {
			assertNotNull(JAXBContext.newInstance(StaticData.class),
					"JAXBContext.newInstance returned null for StaticData");
		} catch (JAXBException e) {
			// Report every offending annotation, not just the count: the default
			// message says how many problems exist without naming any of them.
			fail("Static data is no longer bindable by JAXB:\n" + describe(e));
		}
	}

	/**
	 * Flattens an exception chain into a readable report.
	 *
	 * @param throwable failure to describe
	 * @return one line per cause, deepest last
	 */
	private static String describe(Throwable throwable) {
		StringBuilder report = new StringBuilder();
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			report.append(current.getClass().getName()).append(": ").append(current.getMessage()).append('\n');
			appendBindingErrors(current, report);
			if (current.getCause() == current) {
				break;
			}
		}
		return report.toString();
	}

	/**
	 * Appends the individual binding errors an exception may carry.
	 * <p>
	 * IllegalAnnotationsException names no culprit in its message, it only counts
	 * them; the detail sits behind a getErrors() method. Reach it reflectively so
	 * the test keeps compiling against the JAXB API rather than a specific runtime.
	 *
	 * @param throwable exception to inspect
	 * @param report    report being built
	 */
	private static void appendBindingErrors(Throwable throwable, StringBuilder report) {
		try {
			Object errors = throwable.getClass().getMethod("getErrors").invoke(throwable);
			if (errors instanceof Iterable) {
				for (Object error : (Iterable<?>) errors) {
					report.append("  -> ").append(error).append('\n');
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// The exception simply does not expose binding errors; the chain is enough.
		}
	}
}
