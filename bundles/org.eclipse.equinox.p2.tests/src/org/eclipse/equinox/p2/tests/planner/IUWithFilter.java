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
package org.eclipse.equinox.p2.tests.planner;

import org.eclipse.equinox.internal.provisional.p2.metadata.Version;
import org.eclipse.equinox.internal.provisional.p2.metadata.VersionRange;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.internal.provisional.p2.director.IPlanner;
import org.eclipse.equinox.internal.provisional.p2.director.ProfileChangeRequest;
import org.eclipse.equinox.internal.provisional.p2.engine.IProfile;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class IUWithFilter extends AbstractProvisioningTest {
	IInstallableUnit a1;

	IPlanner planner;
	IProfile profile;

	protected void setUp() throws Exception {
		super.setUp();
		MetadataFactory.InstallableUnitDescription iud = new MetadataFactory.InstallableUnitDescription();
		iud.setId("A");
		iud.setVersion(new Version("1.0.0"));
		iud.setRequiredCapabilities(createRequiredCapabilities(IInstallableUnit.NAMESPACE_IU_ID, "A", new VersionRange("[1.0.0, 1.0.0]"), null));
		iud.setFilter("(invalid=true)");
		a1 = MetadataFactory.createInstallableUnit(iud);
		createTestMetdataRepository(new IInstallableUnit[] {a1});
		profile = createProfile(IUWithFilter.class.getName());
		planner = createPlanner();
	}

	public void testInstall() {
		ProfileChangeRequest req = new ProfileChangeRequest(profile);
		req.addInstallableUnits(new IInstallableUnit[] {a1});
		assertEquals(IStatus.ERROR, planner.getProvisioningPlan(req, null, null).getStatus().getSeverity());
	}
}