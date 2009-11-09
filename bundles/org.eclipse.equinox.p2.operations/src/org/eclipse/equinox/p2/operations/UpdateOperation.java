/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.equinox.p2.operations;

import java.util.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.equinox.internal.p2.operations.Messages;
import org.eclipse.equinox.internal.provisional.p2.director.PlannerHelper;
import org.eclipse.equinox.internal.provisional.p2.director.ProfileChangeRequest;
import org.eclipse.equinox.internal.provisional.p2.engine.IProfile;
import org.eclipse.equinox.internal.provisional.p2.engine.ProvisioningContext;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.Collector;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.InstallableUnitQuery;

/**
 * @since 2.0
 *
 */
public class UpdateOperation extends ProfileChangeOperation {

	private IInstallableUnit[] iusToUpdate;
	private List possibleUpdates = new ArrayList();
	private List defaultUpdates;

	public UpdateOperation(ProvisioningSession session, String profileId, String rootMarkerKey, ProvisioningContext context, IInstallableUnit[] toBeUpdated) {
		super(session, profileId, rootMarkerKey, context);
		this.iusToUpdate = toBeUpdated;
	}

	public void setDefaultUpdates(Update[] defaultUpdates) {
		this.defaultUpdates = Arrays.asList(defaultUpdates);
	}

	public Update[] getDefaultUpdates() {
		if (defaultUpdates == null)
			return new Update[0];
		return (Update[]) defaultUpdates.toArray(new Update[defaultUpdates.size()]);
	}

	public Update[] getPossibleUpdates() {
		if (possibleUpdates == null)
			return new Update[0];
		return (Update[]) possibleUpdates.toArray(new Update[possibleUpdates.size()]);
	}

	private Update[] updatesFor(IInstallableUnit iu, IProfile profile, IProgressMonitor monitor) {
		ArrayList updates;
		if (possibleUpdates == null) {
			IInstallableUnit[] replacements = session.getPlanner().updatesFor(iu, context, monitor);
			updates = new ArrayList(replacements.length);
			for (int i = 0; i < replacements.length; i++) {
				// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=273967
				// In the case of patches, it's possible that a patch is returned as an available update
				// even though it is already installed, because we are querying each IU for updates individually.
				// For now, we ignore any proposed update that is already installed.
				Collector alreadyInstalled = profile.query(new InstallableUnitQuery(replacements[i]), new Collector(), null);
				if (alreadyInstalled.isEmpty()) {
					Update update = new Update(iu, replacements[i]);
					possibleUpdates.add(update);
					updates.add(update);
				}
			}
		} else {
			// We've already looked them up in the planner, check the cache for those that are updating this iu
			updates = new ArrayList();
			Iterator iter = possibleUpdates.iterator();
			while (iter.hasNext()) {
				Update update = (Update) iter.next();
				if (update.toUpdate.equals(iu))
					updates.add(update);
			}
		}
		return (Update[]) updates.toArray(new Update[updates.size()]);

	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.p2.operations.ProfileChangeOperation#computeProfileChangeRequest(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void computeProfileChangeRequest(IProgressMonitor monitor) {
		// Here we create a profile change request by finding the latest version available for any replacement, unless
		// otherwise specified in the selections.
		// We have to consider the scenario where the only updates available are patches, in which case the original
		// IU should not be removed as part of the update.
		Set toBeUpdated = new HashSet();
		HashSet elementsToPlan = new HashSet();
		boolean selectionSpecified = false;
		IProfile profile = session.getProfile(profileId);
		if (profile == null)
			return;

		SubMonitor sub = SubMonitor.convert(monitor, Messages.UpdateOperation_ProfileChangeRequestProgress, 100 * iusToUpdate.length);
		for (int i = 0; i < iusToUpdate.length; i++) {
			SubMonitor iuMon = sub.newChild(100);
			Update[] updates = updatesFor(iusToUpdate[i], profile, iuMon);
			for (int j = 0; j < updates.length; j++) {
				toBeUpdated.add(iusToUpdate[i]);
				if (defaultUpdates != null && defaultUpdates.contains(updates[j])) {
					elementsToPlan.add(updates[j]);
					selectionSpecified = true;
				}

			}
			if (!selectionSpecified) {
				// If no selection was specified, we must figure out the latest version to apply.
				// The rules are that a true update will always win over a patch, but if only
				// patches are available, they should all be selected.
				// We first gather the latest versions of everything proposed.
				// Patches are keyed by their id because they are unique and should not be compared to
				// each other.  Updates are keyed by the IU they are updating so we can compare the
				// versions and select the latest one
				HashMap latestVersions = new HashMap();
				boolean foundUpdate = false;
				boolean foundPatch = false;
				for (int j = 0; j < updates.length; j++) {
					String key;
					if (Boolean.toString(true).equals(updates[j].replacement.getProperty(IInstallableUnit.PROP_TYPE_PATCH))) {
						foundPatch = true;
						key = updates[j].replacement.getId();
					} else {
						foundUpdate = true;
						key = updates[j].toUpdate.getId();
					}
					Update latestUpdate = (Update) latestVersions.get(key);
					IInstallableUnit latestIU = latestUpdate == null ? null : latestUpdate.replacement;
					if (latestIU == null || updates[j].replacement.getVersion().compareTo(latestIU.getVersion()) > 0)
						latestVersions.put(key, updates[j]);
				}
				// If there is a true update available, ignore any patches found
				// Patches are keyed by their own id
				if (foundPatch && foundUpdate) {
					Set keys = new HashSet();
					keys.addAll(latestVersions.keySet());
					Iterator keyIter = keys.iterator();
					while (keyIter.hasNext()) {
						String id = (String) keyIter.next();
						// Get rid of things keyed by a different id.  We've already made sure
						// that updates with a different id are keyed under the original id
						if (!id.equals(iusToUpdate[i].getId())) {
							latestVersions.remove(id);
						}
					}
				}
				elementsToPlan.addAll(latestVersions.values());
			}
			sub.worked(100);
		}

		if (toBeUpdated.size() <= 0 || elementsToPlan.isEmpty()) {
			sub.done();
			return;
		}

		request = ProfileChangeRequest.createByProfileId(profileId);
		Iterator iter = elementsToPlan.iterator();
		while (iter.hasNext()) {
			Update update = (Update) iter.next();
			IInstallableUnit theUpdate = update.replacement;
			if (defaultUpdates != null) {
				if (!defaultUpdates.contains(update))
					defaultUpdates.add(update);
			}
			request.addInstallableUnits(new IInstallableUnit[] {theUpdate});
			if (rootMarkerKey != null)
				request.setInstallableUnitProfileProperty(theUpdate, rootMarkerKey, Boolean.toString(true));
			if (Boolean.toString(true).equals(theUpdate.getProperty(IInstallableUnit.PROP_TYPE_PATCH))) {
				request.setInstallableUnitInclusionRules(theUpdate, PlannerHelper.createOptionalInclusionRule(theUpdate));
			} else {
				request.removeInstallableUnits(new IInstallableUnit[] {update.toUpdate});
			}

		}
		sub.done();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.p2.operations.ProfileChangeOperation#getProvisioningJobName()
	 */
	protected String getProvisioningJobName() {
		return Messages.UpdateOperation_UpdateJobName;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.p2.operations.ProfileChangeOperation#getResolveJobName()
	 */
	protected String getResolveJobName() {
		return Messages.UpdateOperation_ResolveJobName;
	}

}