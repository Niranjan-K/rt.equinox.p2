/******************************************************************************* 
* Copyright (c) 2009 EclipseSource and others. All rights reserved. This
* program and the accompanying materials are made available under the terms of
* the Eclipse Public License v1.0 which accompanies this distribution, and is
* available at http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*   EclipseSource - initial API and implementation
******************************************************************************/
package org.eclipse.equinox.p2.tests.updatesite;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.updatesite.CategoryXMLAction;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.Collector;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.IUPropertyQuery;
import org.eclipse.equinox.p2.publisher.*;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;
import org.eclipse.equinox.p2.tests.*;

/**
 *
 */
public class CategoryXMLActionTest extends AbstractProvisioningTest {

	private TestMetadataRepository metadataRepository;
	private IPublisherResult actionResult;
	private URI siteLocation;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		actionResult = new PublisherResult();
		PublisherInfo info = new PublisherInfo();
		metadataRepository = new TestMetadataRepository(new IInstallableUnit[0]);
		info.setMetadataRepository(metadataRepository);
		siteLocation = TestData.getFile("updatesite", "CategoryXMLActionTest/category.xml").toURI();
		FeaturesAction featuresAction = new FeaturesAction(new File[] {TestData.getFile("updatesite", "CategoryXMLActionTest")});
		featuresAction.perform(info, actionResult, new NullProgressMonitor());

		CategoryXMLAction action = new CategoryXMLAction(siteLocation, null);
		action.perform(info, actionResult, getMonitor());
	}

	public void testCategoryCreation() throws Exception {
		IUPropertyQuery categoryQuery = new IUPropertyQuery(IInstallableUnit.PROP_TYPE_CATEGORY, Boolean.TRUE.toString());
		Collection result = actionResult.query(categoryQuery, new Collector(), new NullProgressMonitor()).toCollection();
		assertEquals("1.0", 1, result.size());
		IInstallableUnit iu = (IInstallableUnit) result.iterator().next();
		assertEquals("1.1", "Test Category Label", iu.getProperty(IInstallableUnit.PROP_NAME));
	}

}