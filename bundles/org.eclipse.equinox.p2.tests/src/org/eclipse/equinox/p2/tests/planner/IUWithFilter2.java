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
import org.eclipse.equinox.internal.provisional.p2.metadata.*;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class IUWithFilter2 extends AbstractProvisioningTest {
	IInstallableUnit a1, a2;
	IInstallableUnit b1;

	IPlanner planner;
	IProfile profile;

	protected void setUp() throws Exception {
		super.setUp();
		IRequiredCapability[] reqs = new IRequiredCapability[1];
		reqs[0] = MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, "B", new VersionRange("[1.0.0, 1.0.0]"), null, false, false);
		a1 = createIU("A", reqs);

		IRequiredCapability[] reqsA2 = new IRequiredCapability[1];
		reqsA2[0] = MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, "B", new VersionRange("[1.0.0, 1.0.0]"), null, true, false);
		a2 = createIU("A", reqsA2);

		b1 = createIU("B", new Version("1.0.0"), "(invalid=true)", NO_PROVIDES);

		createTestMetdataRepository(new IInstallableUnit[] {a1, a2, b1});
		profile = createProfile(IUWithFilter2.class.getName());
		planner = createPlanner();
	}

	public void testInstallA1() {
		ProfileChangeRequest req = new ProfileChangeRequest(profile);
		req.addInstallableUnits(new IInstallableUnit[] {a1});
		assertEquals(IStatus.ERROR, planner.getProvisioningPlan(req, null, null).getStatus().getSeverity());
	}

	public void testInstallA2() {
		ProfileChangeRequest req = new ProfileChangeRequest(profile);
		req.addInstallableUnits(new IInstallableUnit[] {a2});
		assertEquals(IStatus.OK, planner.getProvisioningPlan(req, null, null).getStatus().getSeverity());
	}
}