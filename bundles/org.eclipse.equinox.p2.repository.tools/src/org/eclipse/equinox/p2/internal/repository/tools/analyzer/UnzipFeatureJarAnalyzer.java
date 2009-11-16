/******************************************************************************* 
* Copyright (c) 2009 EclipseSource and others. All rights reserved. This
* program and the accompanying materials are made available under the terms of
* the Eclipse Public License v1.0 which accompanies this distribution, and is
* available at http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*   EclipseSource - initial API and implementation
******************************************************************************/
package org.eclipse.equinox.p2.internal.repository.tools.analyzer;

import org.eclipse.equinox.p2.repository.tools.analyzer.IUAnalyzer;

import org.eclipse.equinox.internal.provisional.p2.metadata.*;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepository;

/**
 * This service checks that each Feature Jar IU has the unzip touchpoint
 */
public class UnzipFeatureJarAnalyzer extends IUAnalyzer {

	public void analyzeIU(IInstallableUnit iu) {
		if (iu.getId().contains("feature.jar")) {
			ITouchpointData[] touchpointData = iu.getTouchpointData();
			if (touchpointData.length == 0) {
				error(iu, "[ERROR] No unzip touchpoint for: " + iu.getId());
			} else {
				boolean found = false;
				for (int i = 0; i < touchpointData.length; i++) {
					ITouchpointInstruction instruction = touchpointData[i].getInstruction("zipped");
					if (instruction.getBody().equals("true"))
						found = true;
				}
				if (!found) {
					error(iu, "[ERROR] No unzip touchpoint for: " + iu.getId());
				}
			}
		}

	}

	public void preAnalysis(IMetadataRepository repository) {
		// Do nothing
	}

}
