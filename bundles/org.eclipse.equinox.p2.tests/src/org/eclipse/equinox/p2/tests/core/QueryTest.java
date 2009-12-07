/*******************************************************************************
 *  Copyright (c) 2008, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.core;

import java.util.*;
import junit.framework.TestCase;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.*;

/**
 * Tests for the {@link org.eclipse.equinox.internal.provisional.p2.query.Query} class.
 */
public class QueryTest extends TestCase {

	static class AnyStringQuery extends MatchQuery {
		@Override
		public boolean isMatch(Object candidate) {
			return candidate instanceof String;
		}
	}

	static class PerformHookQuery extends AnyStringQuery {
		private boolean prepared = false;
		private boolean complete = false;

		public boolean areHooksExecutedProperly() {
			// Either they have both been called, or neither has been called
			return (prepared & complete) || (!prepared & !complete);
		}

		public boolean isComplete() {
			return this.complete;
		}

		public boolean isPrepared() {
			return this.prepared;
		}

		public void prePerform() {
			prepared = true;
		}

		public void postPerform() {
			if (!(prepared)) // Note:  is match might not be called if it can be determined it's not needed
				fail("prePerform not called");
			complete = true;
		}

		public boolean isMatch(Object candidate) {
			if (!prepared)
				fail("prePerform not called");
			if (!(candidate instanceof String))
				throw new RuntimeException("Exception intentionally thrown by test");
			return candidate instanceof String;
		}
	}

	/**
	 * A collector that only accepts the first element and then short-circuits.
	 */
	static class ShortCircuitCollector extends Collector {
		@Override
		public boolean accept(Object object) {
			super.accept(object);
			return false;
		}
	}

	/**
	 * Tests a simple perform where all items match.
	 */
	public void testPerformSimple() {
		List items = Arrays.asList("red", "green", "blue");
		Query query = new AnyStringQuery();
		Collector collector = new Collector();
		query.perform(items.iterator(), collector);
		Collection result = collector.toCollection();
		assertEquals("1.0", 3, result.size());
		assertTrue("1.1", result.contains("red"));
		assertTrue("1.2", result.contains("green"));
		assertTrue("1.3", result.contains("blue"));
	}

	/**
	 * Tests a perform where only some items match.
	 */
	public void testPerformSomeMatches() {
		List items = Arrays.asList(new Object(), "green", new Object());
		Query query = new AnyStringQuery();
		Collector collector = new Collector();
		query.perform(items.iterator(), collector);
		Collection result = collector.toCollection();
		assertEquals("1.0", 1, result.size());
		assertTrue("1.1", result.contains("green"));
	}

	public void testPerformHooks() {
		List items = Arrays.asList("red", "green", "blue");
		PerformHookQuery query = new PerformHookQuery();
		Collector collector = new Collector();
		assertFalse("1.0", query.isComplete());
		assertFalse("1.1", query.isPrepared());
		query.perform(items.iterator(), collector);
		assertTrue("1.2", query.isComplete());
		assertTrue("1.3", query.isPrepared());
		assertTrue("1.4", query.areHooksExecutedProperly());
	}

	public void testPropertyLookupMatchQuery() {
		Query query1 = new PropertyLookupQuery1();
		Object property = query1.getProperty("SomeProperty");
		assertEquals("1.0", "foo", property);
	}

	public void testPropertyLookupContextQuery() {
		Query query1 = new PropertyLookupQuery2();
		Object property = query1.getProperty("SomeOtherProperty");
		assertEquals("1.0", "bar", property);
	}

	public void testPropertyLookupInvalidProperty1() {
		Query query1 = new PropertyLookupQuery1();
		Object property = query1.getProperty("ThisProperty");
		assertEquals("1.0", null, property);
	}

	public void testPropertyLookupInvalidProperty2() {
		Query query1 = new PropertyLookupQuery1();
		Object property = query1.getProperty("SomeOtherProperty");
		assertEquals("1.0", null, property);
	}

	public void testIDLookup() {
		Query query1 = new PropertyLookupQuery1();
		Query query2 = new PropertyLookupQuery2();
		assertEquals("1.0", "org.eclipse.equinox.p2.tests.core.PropertyLookupQuery1", query1.getId());
		assertEquals("1.0", "org.eclipse.equinox.p2.tests.core.PropertyLookupQuery2", query2.getId());
	}

	public void testPerformHooksOnQueryFail() {
		List items = Arrays.asList("red", new Object());
		PerformHookQuery query = new PerformHookQuery();
		Collector collector = new Collector();
		assertFalse("1.0", query.isComplete());
		assertFalse("1.1", query.isPrepared());
		try {
			query.perform(items.iterator(), collector);
		} catch (RuntimeException e) {
			// expected
		}
		assertTrue("1.2", query.isComplete());
		assertTrue("1.3", query.isPrepared());
		assertTrue("1.4", query.areHooksExecutedProperly());
	}

	public void testPreAndPostCompoundANDQuery() {
		List items = Arrays.asList("red", "green", "blue");
		Collector collector = new Collector();
		PerformHookQuery query1 = new PerformHookQuery();
		PerformHookQuery query2 = new PerformHookQuery();
		CompoundQuery cQuery = CompoundQuery.createCompoundQuery(new Query[] {query1, query2}, true);
		assertFalse("1.0", query1.isComplete());
		assertFalse("1.1", query1.isPrepared());
		assertFalse("1.2", query2.isComplete());
		assertFalse("1.3", query2.isPrepared());
		cQuery.perform(items.iterator(), collector);
		assertTrue("1.4", query1.isComplete());
		assertTrue("1.5", query1.isPrepared());
		assertTrue("1.6", query2.isComplete());
		assertTrue("1.7", query2.isPrepared());
		assertTrue("1.8", query1.areHooksExecutedProperly());
		assertTrue("1.9", query2.areHooksExecutedProperly());
	}

	public void testPreAndPostCompoundOrQuery() {
		List items = Arrays.asList("red", "green", "blue");
		Collector collector = new Collector();
		PerformHookQuery query1 = new PerformHookQuery();
		PerformHookQuery query2 = new PerformHookQuery();
		CompoundQuery cQuery = CompoundQuery.createCompoundQuery(new Query[] {query1, query2}, false);
		assertFalse("1.0", query1.isComplete());
		assertFalse("1.1", query1.isPrepared());
		assertFalse("1.2", query2.isComplete());
		assertFalse("1.3", query2.isPrepared());
		cQuery.perform(items.iterator(), collector);
		assertTrue("1.4", query1.isComplete());
		assertTrue("1.5", query1.isPrepared());
		assertTrue("1.6", query2.isComplete());
		assertTrue("1.7", query2.isPrepared());
		assertTrue("1.8", query1.areHooksExecutedProperly());
		assertTrue("1.9", query2.areHooksExecutedProperly());
	}

	public void testPreAndPostCompositeQuery() {
		List items = Arrays.asList("red", "green", "blue");
		Collector collector = new Collector();
		PerformHookQuery query1 = new PerformHookQuery();
		PerformHookQuery query2 = new PerformHookQuery();
		CompositeQuery cQuery = new CompositeQuery(new Query[] {query1, query2});
		assertFalse("1.0", query1.isComplete());
		assertFalse("1.1", query1.isPrepared());
		assertFalse("1.2", query2.isComplete());
		assertFalse("1.3", query2.isPrepared());
		cQuery.perform(items.iterator(), collector);
		assertTrue("1.4", query1.isComplete());
		assertTrue("1.5", query1.isPrepared());
		assertTrue("1.6", query2.isComplete());
		assertTrue("1.7", query2.isPrepared());
		assertTrue("1.8", query1.areHooksExecutedProperly());
		assertTrue("1.9", query2.areHooksExecutedProperly());
	}

	public void testPreAndPostCompoundQueryFail() {
		List items = Arrays.asList("red", new Object());
		Collector collector = new Collector();
		PerformHookQuery query1 = new PerformHookQuery();
		PerformHookQuery query2 = new PerformHookQuery();
		CompoundQuery cQuery = CompoundQuery.createCompoundQuery(new Query[] {query1, query2}, true);
		assertFalse("1.0", query1.isComplete());
		assertFalse("1.1", query1.isPrepared());
		assertFalse("1.2", query2.isComplete());
		assertFalse("1.3", query2.isPrepared());
		try {
			cQuery.perform(items.iterator(), collector);
			fail("This query is expected to fail");
		} catch (RuntimeException e) {
			// expected
		}
		assertTrue("1.4", query1.isComplete());
		assertTrue("1.5", query1.isPrepared());
		assertTrue("1.6", query2.isComplete());
		assertTrue("1.7", query2.isPrepared());
		assertTrue("1.8", query1.areHooksExecutedProperly());
		assertTrue("1.9", query2.areHooksExecutedProperly());
	}

	public void testPreAndPostCompositeQueryFail() {
		List items = Arrays.asList("red", new Object());
		Collector collector = new Collector();
		PerformHookQuery query1 = new PerformHookQuery();
		PerformHookQuery query2 = new PerformHookQuery();
		CompositeQuery cQuery = new CompositeQuery(new Query[] {query1, query2});
		assertFalse("1.0", query1.isComplete());
		assertFalse("1.1", query1.isPrepared());
		assertFalse("1.2", query2.isComplete());
		assertFalse("1.3", query2.isPrepared());
		try {
			cQuery.perform(items.iterator(), collector);
			fail("This query is expected to fail");
		} catch (RuntimeException e) {
			// expected
		}
		assertTrue("1.4", query1.isComplete());
		assertTrue("1.5", query1.isPrepared());
		assertFalse("1.6", query2.isComplete()); // This should fail, the second query was never executed
		assertFalse("1.7", query2.isPrepared()); // This should fail, the second query was never executed
		assertTrue("1.8", query1.areHooksExecutedProperly());
		assertTrue("1.9", query2.areHooksExecutedProperly());
	}

	/**
	 * Tests a perform where the collector decides to short-circuit the query.
	 */
	public void testShortCircuit() {
		List items = Arrays.asList("red", "green", "blue");
		Query query = new AnyStringQuery();
		Collector collector = new ShortCircuitCollector();
		query.perform(items.iterator(), collector);
		Collection result = collector.toCollection();
		assertEquals("1.0", 1, result.size());
		assertTrue("1.1", result.contains("red"));
	}

}