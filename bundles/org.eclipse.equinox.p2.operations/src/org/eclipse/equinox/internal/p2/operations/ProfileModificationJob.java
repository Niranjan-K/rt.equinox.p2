/*******************************************************************************
 *  Copyright (c) 2007, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.operations;

import org.eclipse.equinox.p2.operations.ProvisioningJob;
import org.eclipse.equinox.p2.operations.ProvisioningSession;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.director.ProvisioningPlan;
import org.eclipse.equinox.internal.provisional.p2.engine.*;

/**
 * Class representing a provisioning profile plan
 * 
 * @since 2.0
 */
public class ProfileModificationJob extends ProvisioningJob {

	ProvisioningPlan plan;
	String profileId;
	PhaseSet phaseSet;
	boolean isUser = true;
	ProvisioningContext provisioningContext;
	int restartPolicy = ProvisioningJob.RESTART_OR_APPLY;
	private String taskName;

	public ProfileModificationJob(String label, ProvisioningSession session, String profileId, ProvisioningPlan plan, ProvisioningContext context) {
		this(label, session, profileId, plan, context, null, true);
	}

	public ProfileModificationJob(String label, ProvisioningSession session, String profileId, ProvisioningPlan plan, ProvisioningContext context, PhaseSet set, boolean isUser) {
		super(label, session);
		this.plan = plan;
		this.profileId = profileId;
		this.provisioningContext = context;
		this.isUser = isUser;
		if (set == null)
			phaseSet = new DefaultPhaseSet();
		else
			phaseSet = set;
	}

	public String getProfileId() {
		return profileId;
	}

	protected IProfile getProfile() {
		return getSession().getProfile(profileId);

	}

	public void runModal(IProgressMonitor monitor) throws ProvisionException {
		String task = taskName;
		if (task == null)
			task = getName();
		monitor.beginTask(task, 1000);
		try {
			getSession().performProvisioningPlan(plan, phaseSet, provisioningContext, new SubProgressMonitor(monitor, 1000));
		} finally {
			monitor.done();
		}
	}

	public boolean runInBackground() {
		return true;
	}

	/**
	 * Sets the top level task name for progress when running this operation.
	 * @param label
	 */
	public void setTaskName(String label) {
		this.taskName = label;
	}

	public int getRestartPolicy() {
		return restartPolicy;
	}

	public void setRestartPolicy(int policy) {
		restartPolicy = policy;
	}
}