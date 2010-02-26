/*******************************************************************************
 *  Copyright (c) 2005, 2008 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *      IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.generator;

import java.io.File;
import java.util.Map;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.publisher.Messages;
import org.eclipse.equinox.internal.p2.publisher.compatibility.GeneratorApplication;
import org.eclipse.equinox.internal.p2.updatesite.UpdateSitePublisherApplication;
import org.eclipse.equinox.internal.simpleconfigurator.utils.URIUtil;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAndBundlesPublisherApplication;
import org.eclipse.equinox.p2.publisher.eclipse.InstallPublisherApplication;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;
import org.eclipse.equinox.p2.tests.TestActivator;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;

public class GeneratorTests extends AbstractProvisioningTest {

	protected class TestGeneratorApplication extends GeneratorApplication {
		protected Object launchApplication(Map<String, Object> applicationMap) {
			try {
				String appId = (String) applicationMap.get(APP_ID);
				if (UPDATE_SITE_APPLICATION.equals(appId)) {
					UpdateSitePublisherApplication app = new UpdateSitePublisherApplication();
					return app.run((String[]) applicationMap.get(IApplicationContext.APPLICATION_ARGS));
				} else if (FEATURES_BUNDLES_APPLICATION.equals(appId)) {
					FeaturesAndBundlesPublisherApplication app = new FeaturesAndBundlesPublisherApplication();
					return app.run((String[]) applicationMap.get(IApplicationContext.APPLICATION_ARGS));
				} else if (INSTALL_APPLICATION.equals(appId)) {
					InstallPublisherApplication app = new InstallPublisherApplication();
					return app.run((String[]) applicationMap.get(IApplicationContext.APPLICATION_ARGS));
				}
			} catch (Exception e) {
				return e;
			}
			return null;
		}

		public Object go(String[] arguments) throws Exception {
			Object result = run(arguments);
			if (result instanceof Exception)
				throw (Exception) result;
			return result;
		}
	}

	public void test233240_artifactsDeleted() throws Exception {
		//this also covers 220494
		File rootFolder = getTestFolder("artifactsDeleted");

		//copy some bundles over 
		File plugins = new File(rootFolder, "plugins");
		plugins.mkdir();

		int limit = 3;
		for (int i = 0; i < limit; i++) {
			BundleContext context = TestActivator.getContext();
			File bundle = FileLocator.getBundleFile(context.getBundle(i));
			if (!bundle.isFile()) {
				//only jars please
				++limit;
				continue;
			}
			copy("1.0 Populating input bundles.", bundle, new File(plugins, bundle.getName()));
		}

		String[] arguments = new String[] {"-metadataRepository", rootFolder.toURL().toExternalForm().toString(), "-artifactRepository", rootFolder.toURL().toExternalForm().toString(), "-source", rootFolder.getAbsolutePath(), "-publishArtifacts", "-noDefaultIUs"};
		TestGeneratorApplication application = new TestGeneratorApplication();
		application.go(arguments);

		assertTrue("2.0 - initial artifact repo existance", new File(rootFolder, "artifacts.xml").exists());
		assertTrue("2.1 - initial artifact repo contents", plugins.listFiles().length > 0);

		//Taunt you one more time
		application = new TestGeneratorApplication();
		try {
			application.go(arguments);
			fail("3.0 - Expected Illegal Argument Exception not thrown.");
		} catch (IllegalArgumentException e) {
			assertTrue("3.0 - Expected Illegal Argument", e.getMessage().equals(NLS.bind(Messages.exception_artifactRepoNoAppendDestroysInput, rootFolder.toURI())));
		}

		assertTrue("3.1 - artifact repo existance", new File(rootFolder, "artifacts.xml").exists());

		//with -updateSite
		arguments = new String[] {"-metadataRepository", rootFolder.toURL().toExternalForm().toString(), "-artifactRepository", rootFolder.toURL().toExternalForm().toString(), "-updateSite", rootFolder.getAbsolutePath(), "-publishArtifacts", "-noDefaultIUs"};
		application.go(arguments);

		assertTrue("4.0 - artifact repo existance", new File(rootFolder, "artifacts.xml").exists());
		assertTrue("4.1 - artifact repo contents", plugins.listFiles().length > 0);

		assertEquals(new File(rootFolder, "plugins").list().length, 3);
		delete(rootFolder);
	}

	public void testBasicUpdateSite() throws Exception {
		File rootFolder = getTestFolder("basicUpdateSite");

		File updateSite = getTestData("1.0 finding update site", "testData/testRepos/updateSite");
		copy("2.0 copying update site", updateSite, rootFolder);

		new File(rootFolder, "content.xml").delete();
		new File(rootFolder, "artifacts.xml").delete();

		String[] arguments = {"-updateSite", rootFolder.getAbsolutePath(), "-site", new File(rootFolder, "site.xml").getAbsolutePath(), "-metadataRepository", URIUtil.toUnencodedString(rootFolder.toURI()), "-artifactRepository", URIUtil.toUnencodedString(rootFolder.toURI()), "-metadataRepositoryName", "Basic Metadata Test Site", "-artifactRepositoryName", "Basic Artifact Test Site", "-compress", "-reusePack200Files", "-noDefaultIUs"};

		TestGeneratorApplication app = new TestGeneratorApplication();
		app.go(arguments);

		assertTrue(new File(rootFolder, "artifacts.jar").exists());
		assertTrue(new File(rootFolder, "content.jar").exists());

		IMetadataRepository metadataRepo = loadMetadataRepository(rootFolder.toURI());
		assertEquals(metadataRepo.getName(), "Basic Metadata Test Site");

		File siteXml = new File(rootFolder, "site.xml");
		IInstallableUnit iu = getIU(metadataRepo, URIUtil.toUnencodedString(siteXml.toURI()) + ".More Examples");
		assertNotNull(iu);
		assertEquals(iu.getRequirements().size(), 3);
		assertEquals(iu.getProperty("org.eclipse.equinox.p2.type.category"), "true");
		assertEquals(iu.getProperty("org.eclipse.equinox.p2.name"), "More Fine Examples");

		iu = getIU(metadataRepo, URIUtil.toUnencodedString(siteXml.toURI()) + ".Examples");
		assertNotNull(iu);
		assertEquals(iu.getRequirements().size(), 5);
		assertEquals(iu.getProperty("org.eclipse.equinox.p2.type.category"), "true");
		assertEquals(iu.getProperty("org.eclipse.equinox.p2.name"), "Platform Examples");

		IArtifactRepository artifactRepo = loadArtifactRepository(rootFolder.toURI());
		assertEquals(artifactRepo.getName(), "Basic Artifact Test Site");
	}
}
