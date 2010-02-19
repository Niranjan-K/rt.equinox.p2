/*******************************************************************************
 * Copyright (c) 2009 Cloudsmith Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Cloudsmith Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.ql;

import java.io.File;
import java.net.URI;
import java.util.*;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.director.QueryableArray;
import org.eclipse.equinox.internal.p2.metadata.IRequiredCapability;
import org.eclipse.equinox.internal.provisional.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.metadata.expression.*;
import org.eclipse.equinox.p2.metadata.expression.IContextExpression;
import org.eclipse.equinox.p2.metadata.query.InstallableUnitQuery;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherResult;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.ql.*;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.MatchQuery;
import org.eclipse.equinox.p2.repository.artifact.*;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;
import org.eclipse.equinox.p2.tests.TestActivator;
import org.osgi.framework.Filter;

public class EvaluatorTest extends AbstractProvisioningTest {
	private static final IExpressionParser parser = ExpressionUtil.getParser();
	private static final IExpressionFactory factory = ExpressionUtil.getFactory();

	public void testArguments() throws Exception {
		IExpression expr = parser.parse("'a' == $0 && 'b' == $1 && 'c' == $2");
		assertEquals(Boolean.TRUE, expr.evaluate(factory.createContext("a", "b", "c")));
	}

	public void testAnonymousMember() throws Exception {
		IExpression expr = parser.parse("$0.class == $1");
		assertEquals(Boolean.TRUE, expr.evaluate(factory.createContext("a", String.class)));
	}

	public void testInstanceOf() throws Exception {
		// Explicit instanceof when rhs is a class
		IExpression expr = parser.parse("$0 ~= $1");
		assertEquals(Boolean.TRUE, expr.evaluate(factory.createContext(new Integer(4), Number.class)));
	}

	public void testArray() throws Exception {
		IExpression expr = parser.parse("['a', 'b', 'c'].exists(x | x == 'b') && ['a', 'b', 'c'].all(x | 'd' > x)");
		IEvaluationContext ctx = factory.createContext();
		assertEquals(Boolean.TRUE, expr.evaluate(ctx));
		expr = parser.parse("['d', 'e', 'f'].exists(x | ['a', 'b', 'c'].exists(y | x > y))");
		assertEquals(Boolean.TRUE, expr.evaluate(ctx));
		expr = parser.parse("[['d', 'e', 'f'], ['h', 'i', 'j']].exists(x | x.all(y | ['a', 'b', 'c'].all(z | y > z)))");
		assertEquals(Boolean.TRUE, expr.evaluate(ctx));
		expr = parser.parse("[['d', 'e', 'f'], ['h', '3', 'j']].exists(x | x.all(y | ['a', 'b', 'c'].exists(z | y > z)))");
		assertEquals(Boolean.TRUE, expr.evaluate(ctx));
		expr = parser.parse("[['d', 'e', 'f'], ['h', 'i', 'j']].all(x | x.all(y | ['a', 'b', 'c'].all(z | y > z)))");
		assertEquals(Boolean.TRUE, expr.evaluate(ctx));
		expr = parser.parse("[['d', 'e', 'f'], ['h', '3', 'j']].all(x | x.all(y | ['a', 'b', 'c'].all(z | y > z)))");
		assertEquals(Boolean.FALSE, expr.evaluate(ctx)); // 3 < 'b'
	}

	public void testLatest() throws Exception {
		IMetadataRepository repo = getMDR("/testData/metadataRepo/multipleversions1");
		IQueryResult result = repo.query(new QLContextQuery(IInstallableUnit.class, "latest(x | x.id == $0)", "test.bundle"), new NullProgressMonitor());
		assertTrue(queryResultSize(result) == 1);
	}

	public void testRange() throws Exception {
		IMetadataRepository repo = getMDR("/testData/metadataRepo/multipleversions1");
		IQueryResult result = repo.query(new QLMatchQuery(IInstallableUnit.class, "version ~= $0", new VersionRange("2.0.0")), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 2);
	}

	public void testProperty() throws Exception {
		IMetadataRepository repo = getMDR("/testData/metadataRepo/multipleversions1");

		IQueryResult result = repo.query(new QLMatchQuery(IInstallableUnit.class, "properties.exists(p | boolean(p.value))"), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 3);

		result = repo.query(new QLMatchQuery(IInstallableUnit.class, "boolean(properties['org.eclipse.equinox.p2.type.group'])"), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 3);

		Filter filter = TestActivator.context.createFilter("(org.eclipse.equinox.p2.type.group=true)");
		result = repo.query(new QLMatchQuery(IInstallableUnit.class, "properties ~= $0", filter), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 3);
	}

	public void testToString() throws Exception {
		String exprString = "select(x | x.id == $0 && (x.version == $1 || x.version == $2)).traverse(set(), _, {requirementsCache, parent | select(" + //
				"parent.requiredCapabilities.unique(requirementsCache).select(rc | rc.filter == null || $2 ~= filter(rc.filter)), _, " + //
				"{rcs, child | rcs.exists(rc | child ~= rc)})}).limit(10)";

		IContextExpression expr = factory.contextExpression(parser.parseQuery(exprString));
		System.out.println(expr.toString());
		assertEquals(exprString, expr.toString());
	}

	public void testSomeAPI() throws Exception {
		// Create some expressions. Note the use of identifiers instead of
		// indexes for the parameters

		IExpression item = factory.variable("item");
		IExpression cmp1 = factory.equals(factory.member(item, "id"), factory.indexedParameter(0));
		IExpression cmp2 = factory.equals(factory.at(factory.member(item, "properties"), factory.indexedParameter(1)), factory.indexedParameter(2));

		IExpression lambda = factory.lambda(item, factory.and(cmp1, cmp2));
		IExpression latest = ((IQLFactory) factory).latest(((IQLFactory) factory).select(factory.variable("everything"), lambda));

		// Create the query
		IContextExpression<IInstallableUnit> e3 = factory.contextExpression(latest, "test.bundle", "org.eclipse.equinox.p2.type.group", "true");
		IMetadataRepository repo = getMDR("/testData/metadataRepo/multipleversions1");
		IQueryResult result = repo.query(new QLContextQuery(IInstallableUnit.class, e3), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 1);
	}

	public void testPatch() throws Exception {
		IRequiredCapability[][] applicability = new IRequiredCapability[2][2];
		applicability[0][0] = MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, "javax.wsdl", null, null, false, false);
		applicability[0][1] = MetadataFactory.createRequiredCapability("org.eclipse.equinox.p2.eclipse.type", "bundle", null, null, false, false);
		applicability[1][0] = MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, "tooling.source.default", null, null, false, false);
		applicability[1][1] = MetadataFactory.createRequiredCapability("org.eclipse.equinox.p2.flavor", "tooling", null, null, false, false);

		IMetadataRepository repo = getMDR("/testData/metadataRepo/wsdlTestRepo");
		IQueryResult result = repo.query(new QLMatchQuery(IInstallableUnit.class, "$0.exists(rcs | rcs.all(rc | this ~= rc))", (Object) applicability), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 3);
	}

	public void testPattern() throws Exception {
		IProvidedCapability pc = MetadataFactory.createProvidedCapability("org.eclipse.equinox.p2.eclipse.type", "source", null);
		IMetadataRepository repo = getMDR("/testData/metadataRepo/wsdlTestRepo");
		IQueryResult result = repo.query(new QLMatchQuery(IInstallableUnit.class, "id ~= /tooling.*.default/", pc), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 3);
	}

	public void testLimit() throws Exception {
		IMetadataRepository repo = getMDR("/testData/metadataRepo/wsdlTestRepo");
		IQueryResult result = repo.query(new QLContextQuery(IInstallableUnit.class, "select(x | x.id ~= /tooling.*/).limit(1)"), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 1);

		result = repo.query(new QLContextQuery(IInstallableUnit.class, "select(x | x.id ~= /tooling.*/).limit($0)", new Integer(2)), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 2);
	}

	public void testNot() throws Exception {
		IMetadataRepository repo = getMDR("/testData/metadataRepo/wsdlTestRepo");
		IQueryResult result = repo.query(new QLMatchQuery(IInstallableUnit.class, "!(id ~= /tooling.*/)"), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 4);
	}

	public void testArtifactQuery() throws Exception {
		URI artifactRepo = getTestData("1.1", "/testData/artifactRepo/simple").toURI();

		IArtifactRepositoryManager artifactManager = getArtifactRepositoryManager();
		assertNotNull(artifactManager);

		IArtifactRepository repo = artifactManager.loadRepository(artifactRepo, new NullProgressMonitor());
		IQueryResult result = repo.query(new QLMatchQuery(IArtifactKey.class, "classifier ~= /*/"), new NullProgressMonitor());
		assertTrue(queryResultSize(result) > 1);
		Iterator itor = result.iterator();
		while (itor.hasNext())
			assertTrue(itor.next() instanceof IArtifactKey);

		result = repo.descriptorQueryable().query(new QLMatchQuery(IArtifactDescriptor.class, "artifactKey.classifier ~= /*/"), new NullProgressMonitor());
		assertTrue(queryResultSize(result) > 1);
		itor = result.iterator();
		while (itor.hasNext())
			assertTrue(itor.next() instanceof IArtifactDescriptor);
	}

	public void testClassConstructor() throws Exception {
		IMetadataRepository repo = getMDR("/testData/metadataRepo/wsdlTestRepo");
		IQueryResult result = repo.query(new QLContextQuery(IInstallableUnit.class, //
				"select(x | x ~= class('org.eclipse.equinox.p2.metadata.IInstallableUnitFragment'))"), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 4);
		repo = getMDR("/testData/galileoM7");
	}

	public void testTraverseWithoutIndex() throws Exception {
		IMetadataRepository repo = getMDR("/testData/galileoM7");
		IQueryResult result = repo.query(new QLContextQuery(IInstallableUnit.class, //
				"select(x | x.id == $0 && x.version == $1).traverse(parent | select(" + //
						"child | parent.requiredCapabilities.exists(rc | child ~= rc)))", //
				"org.eclipse.sdk.feature.group", Version.create("3.5.0.v20090423-7Q7bA7DPR-wM38__Q4iRsmx9z0KOjbpx3AbyvXd-Uq7J2")), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 463);
	}

	public void testTraverseWithIndex() throws Exception {
		IMetadataRepository repo = getMDR("/testData/galileoM7");
		IQueryResult result = repo.query(//
				new QLContextQuery(IInstallableUnit.class, "" + //
						"select(x | x.id == $0 && x.version == $1).traverse(capabilityIndex(everything), _, { index, parent |" + //
						"index.satisfiesAny(parent.requiredCapabilities)})", //
						"org.eclipse.sdk.feature.group",//
						Version.create("3.5.0.v20090423-7Q7bA7DPR-wM38__Q4iRsmx9z0KOjbpx3AbyvXd-Uq7J2")),//
				new NullProgressMonitor());
		assertEquals(queryResultSize(result), 463);
	}

	public void testTraverseWithIndexAndFilter() throws Exception {
		// Add some filtering of requirements
		Map env = new Hashtable();
		env.put("osgi.os", "linux");
		env.put("osgi.ws", "gtk");
		env.put("osgi.arch", "x86");

		IContextExpression<IInstallableUnit> expr = factory.contextExpression(parser.parseQuery("" + //
				"select(x | x.id == $0 && x.version == $1).traverse(capabilityIndex(everything), _, { index, parent |" + //
				"index.satisfiesAny(parent.requiredCapabilities.select(rc | rc.filter == null || $2 ~= rc.filter))})"), "org.eclipse.sdk.feature.group", Version.create("3.5.0.v20090423-7Q7bA7DPR-wM38__Q4iRsmx9z0KOjbpx3AbyvXd-Uq7J2"), env);

		QLContextQuery query = new QLContextQuery(IInstallableUnit.class, expr);
		IMetadataRepository repo = getMDR("/testData/galileoM7");
		IQueryResult result = repo.query(query, new NullProgressMonitor());
		assertEquals(queryResultSize(result), 411);
	}

	public void testCommonRequirements() throws Exception {
		// Add some filtering of requirements

		IMetadataRepository repo = getMDR("/testData/galileoM7");
		QLContextQuery indexQuery = new QLContextQuery(IInstallableUnit.class, "capabilityIndex(everything)");
		Object index = indexQuery.query(QL.newQueryContext(repo));

		Map env = new Hashtable();
		env.put("osgi.os", "linux");
		env.put("osgi.ws", "gtk");
		env.put("osgi.arch", "x86");

		IContextExpression<IInstallableUnit> expr = factory.contextExpression(parser.parseQuery("" + //
				"select(x | x.id == $0 && x.version == $1).traverse(parent |" + //
				"$5.satisfiesAny(parent.requiredCapabilities.select(rc | rc.filter == null || $4 ~= rc.filter))).intersect(" + //
				"select(x | x.id == $2 && x.version == $3).traverse(parent |" + //
				"$5.satisfiesAny(parent.requiredCapabilities.select(rc | rc.filter == null || $4 ~= rc.filter))))"), //
				"org.eclipse.pde.feature.group", //
				Version.create("3.5.0.v20090123-7Z7YF8NFE-z0VXhWU26Hu8gY"), //
				"org.eclipse.gmf.feature.group", //
				Version.create("1.1.1.v20090114-0940-7d8B0FXwkKwFanGNHeHHq8ymBgZ"), //
				env,//
				index);

		QLContextQuery query = new QLContextQuery(IInstallableUnit.class, expr);
		IQueryResult result = repo.query(query, new NullProgressMonitor());
		assertEquals(queryResultSize(result), 184);
	}

	public void testMatchQueryInjectionInPredicate() throws Exception {
		IMetadataRepository repo = getMDR("/testData/galileoM7");
		IMatchExpression expr = factory.matchExpression(parser.parse("iquery($0) || iquery($1)"), new MatchQuery() {
			@Override
			public boolean isMatch(Object candidate) {
				return "true".equals(((IInstallableUnit) candidate).getProperty("org.eclipse.equinox.p2.type.category"));
			}
		}, new MatchQuery() {
			@Override
			public boolean isMatch(Object candidate) {
				return "true".equals(((IInstallableUnit) candidate).getProperty("org.eclipse.equinox.p2.type.group"));
			}
		});
		IQueryResult result = repo.query(new QLMatchQuery(IInstallableUnit.class, expr), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 497);
	}

	static class MyObject {
		String id;
		Map properties = new HashMap();

		public MyObject(String id, String key, String value) {
			this.id = id;
			this.properties.put(key, value);
		}

		public String getId() {
			return this.id;
		}

		public Map getProperties() {
			return this.properties;
		}
	}

	public void testRootVariableSerialization() throws Exception {
		List items = new ArrayList();

		items.add(new MyObject("ian bull", "foo", "true"));

		// Create some expressions. Note the use of identifiers instead of
		// indexes for the parameters
		IExpression item = factory.variable("item");

		IExpression cmp1 = factory.equals(factory.member(item, "id"), factory.indexedParameter(0));

		IExpression everything = factory.variable("everything");
		IExpression lambda = factory.lambda(item, cmp1);

		IContextExpression e3 = factory.contextExpression(((IQLFactory) factory).select(everything, lambda));

		IContextExpression<Object> contextExpression = factory.contextExpression(parser.parseQuery(e3.toString()), "ian bull");
		QLContextQuery qlContextQuery = new QLContextQuery(IInstallableUnit.class, contextExpression);
		System.out.println(e3);

		IQueryResult queryResult = qlContextQuery.perform(items.iterator());
		Iterator iterator = queryResult.iterator();
		while (iterator.hasNext()) {
			System.out.println(iterator.next());
		}

	}

	public void testMatchQueryInjectionInContext() throws Exception {
		IMetadataRepository repo = getMDR("/testData/galileoM7");
		IContextExpression<IInstallableUnit> expr = factory.contextExpression(parser.parseQuery("select(x | iquery($0, x) || iquery($1, x)).latest()"), new MatchQuery() {
			@Override
			public boolean isMatch(Object candidate) {
				return "true".equals(((IInstallableUnit) candidate).getProperty("org.eclipse.equinox.p2.type.category"));
			}
		}, new MatchQuery() {
			@Override
			public boolean isMatch(Object candidate) {
				return "true".equals(((IInstallableUnit) candidate).getProperty("org.eclipse.equinox.p2.type.group"));
			}
		});
		IQueryResult result = repo.query(new QLContextQuery(IInstallableUnit.class, expr), new NullProgressMonitor());
		assertEquals(queryResultSize(result), 497);
	}

	public void testTranslations() {
		File foo_fragment = new File(TestActivator.getTestDataFolder(), "FragmentPublisherTest/foo.fragment");//$NON-NLS-1$
		File foo = new File(TestActivator.getTestDataFolder(), "FragmentPublisherTest/foo");//$NON-NLS-1$
		BundlesAction bundlesAction = new BundlesAction(new File[] {foo_fragment});
		PublisherInfo info = new PublisherInfo();
		PublisherResult results = new PublisherResult();

		bundlesAction.perform(info, results, new NullProgressMonitor());
		Collection ius = results.getIUs(null, null);
		assertEquals("1.0", 1, ius.size());

		info = new PublisherInfo();
		results = new PublisherResult();
		bundlesAction = new BundlesAction(new File[] {foo});
		bundlesAction.perform(info, results, new NullProgressMonitor());

		bundlesAction = new BundlesAction(new File[] {foo_fragment});
		bundlesAction.perform(info, results, new NullProgressMonitor());
		ius = results.getIUs(null, null);
		assertEquals("2.0", 3, ius.size());
		QueryableArray queryableArray = new QueryableArray((IInstallableUnit[]) ius.toArray(new IInstallableUnit[ius.size()]));
		IQueryResult result = queryableArray.query(new InstallableUnitQuery("foo"), null);
		assertEquals("2.1", 1, queryResultSize(result));

		QLMatchQuery lq = new QLMatchQuery(IInstallableUnit.class, "translations['org.eclipse.equinox.p2.name'] ~= /German*/");
		lq.setLocale(Locale.GERMAN);
		Iterator itr = queryableArray.query(lq, new NullProgressMonitor()).iterator();
		assertTrue(itr.hasNext());
		assertEquals("2.8", "foo", ((IInstallableUnit) itr.next()).getId());
		assertFalse(itr.hasNext());
	}

	private IMetadataRepository getMDR(String uri) throws Exception {
		URI metadataRepo = getTestData("1.1", uri).toURI();

		IMetadataRepositoryManager metadataManager = (IMetadataRepositoryManager) getAgent().getService(IMetadataRepositoryManager.SERVICE_NAME);
		assertNotNull(metadataManager);

		return metadataManager.loadRepository(metadataRepo, new NullProgressMonitor());
	}
}
