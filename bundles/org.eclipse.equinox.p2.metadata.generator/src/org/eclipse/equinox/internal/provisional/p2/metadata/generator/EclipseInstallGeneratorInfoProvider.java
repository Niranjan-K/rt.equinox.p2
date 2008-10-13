/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.provisional.p2.metadata.generator;

import java.io.*;
import java.net.*;
import java.util.*;
import org.eclipse.equinox.internal.frameworkadmin.equinox.EquinoxFwConfigFileParser;
import org.eclipse.equinox.internal.frameworkadmin.equinox.EquinoxManipulatorImpl;
import org.eclipse.equinox.internal.frameworkadmin.utils.Utils;
import org.eclipse.equinox.internal.p2.core.helpers.ServiceHelper;
import org.eclipse.equinox.internal.p2.metadata.generator.Activator;
import org.eclipse.equinox.internal.p2.metadata.generator.Messages;
import org.eclipse.equinox.internal.provisional.frameworkadmin.*;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.IArtifactRepository;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepository;
import org.eclipse.osgi.service.environment.EnvironmentInfo;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.*;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * @deprecated The function in this class has been refactored into more focused locations
 * such as IPublisherAction and IPublishingAdvice classes.  See the individual method deprecations
 * for more information on where the code has moved.
 */
public class EclipseInstallGeneratorInfoProvider implements IGeneratorInfo {
	private final static String FILTER_OBJECTCLASS = "(" + Constants.OBJECTCLASS + "=" + FrameworkAdmin.class.getName() + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	private final static String filterFwName = "(" + FrameworkAdmin.SERVICE_PROP_KEY_FW_NAME + "=Equinox)"; //$NON-NLS-1$ //$NON-NLS-2$
	//String filterFwVersion = "(" + FrameworkAdmin.SERVICE_PROP_KEY_FW_VERSION + "=" + props.getProperty("equinox.fw.version") + ")";
	private final static String filterLauncherName = "(" + FrameworkAdmin.SERVICE_PROP_KEY_LAUNCHER_NAME + "=Eclipse.exe)"; //$NON-NLS-1$ //$NON-NLS-2$
	//String filterLauncherVersion = "(" + FrameworkAdmin.SERVICE_PROP_KEY_LAUNCHER_VERSION + "=" + props.getProperty("equinox.launcher.version") + ")";
	private final static String frameworkAdminFillter = "(&" + FILTER_OBJECTCLASS + filterFwName + filterLauncherName + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	private static final String ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR = "org.eclipse.equinox.simpleconfigurator"; //$NON-NLS-1$
	private static final String ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR_CONFIGURL = "org.eclipse.equinox.simpleconfigurator.configUrl"; //$NON-NLS-1$
	private static final String ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR_MANIPULATOR = "org.eclipse.equinox.simpleconfigurator.manipulator"; //$NON-NLS-1$
	private static final String ORG_ECLIPSE_EQUINOX_FRAMEWORKADMIN_EQUINOX = "org.eclipse.equinox.frameworkadmin.equinox"; //$NON-NLS-1$
	private static final String ORG_ECLIPSE_EQUINOX_P2_RECONCILER_DROPINS = "org.eclipse.equinox.p2.reconciler.dropins"; //$NON-NLS-1$

	private static final String PARAMETER_BASEURL = "org.eclipse.equinox.simpleconfigurator.baseUrl"; //$NON-NLS-1$

	/*
	 * 	TODO: Temporary for determining whether eclipse installs
	 * 		  in a profile should support backward compatibility
	 * 		  with update manager.
	 */
	private static final String UPDATE_COMPATIBILITY = "eclipse.p2.update.compatibility"; //$NON-NLS-1$

	private String os;

	/**
	 * Returns a default name for the executable.
	 * @param providedOS The operating system to return the executable for. If null,
	 * the operating system is determined from the current runtime environment.
	 */
	public static String getDefaultExecutableName(String providedOS) {
		String theOS = providedOS;
		if (theOS == null) {
			EnvironmentInfo info = (EnvironmentInfo) ServiceHelper.getService(Activator.getContext(), EnvironmentInfo.class.getName());
			theOS = info.getOS();
		}
		if (theOS.equalsIgnoreCase("win32")) //$NON-NLS-1$
			return "eclipse.exe"; //$NON-NLS-1$
		if (theOS.equalsIgnoreCase("macosx")) //$NON-NLS-1$
			return "Eclipse.app"; //$NON-NLS-1$
		//FIXME Is this a reasonable default for all non-Windows platforms?
		return "eclipse"; //$NON-NLS-1$
	}

	private boolean addDefaultIUs = true;

	private boolean append = false;
	private IArtifactRepository artifactRepository;
	private File baseLocation;
	private File[] bundleLocations;
	private File configLocation;
	private ArrayList defaultIUs;
	private List otherIUs;
	private File executableLocation;
	private File featuresLocation;
	private String flavor;
	private ServiceTracker frameworkAdminTracker;
	private Manipulator manipulator;
	private String[][] mappingRules;
	private IMetadataRepository metadataRepository;
	private boolean publishArtifactRepo = false;
	private boolean publishArtifacts = false;
	private boolean updateCompatibility = Boolean.valueOf(System.getProperty(UPDATE_COMPATIBILITY, "false")).booleanValue(); //$NON-NLS-1$
	private String rootId;
	private String rootVersion;
	private String productFile = null;
	private String launcherConfig;
	private String versionAdvice;

	private URI siteLocation;

	private boolean reuseExistingPack200Files = false;

	public EclipseInstallGeneratorInfoProvider() {
		super();
	}

	public boolean addDefaultIUs() {
		return addDefaultIUs;
	}

	public boolean append() {
		return append;
	}

	/** 
	 * @deprecated moved to DefaultCUsAction
	 */
	protected GeneratorBundleInfo createDefaultConfigurationBundleInfo() {
		GeneratorBundleInfo result = new GeneratorBundleInfo();
		result.setSymbolicName("defaultConfigure"); //$NON-NLS-1$
		result.setVersion("1.0.0"); //$NON-NLS-1$
		result.setStartLevel(4);
		// These should just be in the install section now
		//		result.setSpecialConfigCommands("installBundle(bundle:${artifact});");
		return result;
	}

	/** 
	 * @deprecated moved to DefaultCUsAction
	 */
	protected GeneratorBundleInfo createDefaultUnconfigurationBundleInfo() {
		GeneratorBundleInfo result = new GeneratorBundleInfo();
		result.setSymbolicName("defaultUnconfigure"); //$NON-NLS-1$
		result.setVersion("1.0.0"); //$NON-NLS-1$
		// These should just be in the uninstall section now
		//		result.setSpecialConfigCommands("uninstallBundle(bundle:${artifact});");
		return result;
	}

	/**
	 * Obtains the framework manipulator instance. Throws an exception
	 * if it could not be created.
	 * @deprecated see DataLoader
	 */
	private void createFrameworkManipulator() {
		FrameworkAdmin admin = getFrameworkAdmin();
		if (admin == null)
			throw new RuntimeException("Framework admin service not found"); //$NON-NLS-1$
		manipulator = admin.getManipulator();
		if (manipulator == null)
			throw new RuntimeException("Framework manipulator not found"); //$NON-NLS-1$
	}

	/** 
	 * @deprecated moved to EquinoxLauncherData
	 */
	public static GeneratorBundleInfo createLauncher() {
		GeneratorBundleInfo result = new GeneratorBundleInfo();
		result.setSymbolicName("org.eclipse.equinox.launcher"); //$NON-NLS-1$
		result.setVersion("0.0.0"); //$NON-NLS-1$
		//result.setSpecialConfigCommands("manipulator.addProgramArgument('-startup'); manipulator.addProgramArgument(artifact);");
		result.setSpecialConfigCommands("addProgramArg(programArg:-startup);addProgramArg(programArg:@artifact);"); //$NON-NLS-1$
		result.setSpecialUnconfigCommands("removeProgramArg(programArg:-startup);removeProgramArg(programArg:@artifact);"); //$NON-NLS-1$
		return result;
	}

	/** 
	 * @deprecated moved to EquinoxLauncherCUAction
	 */
	private Collection createLauncherBundleInfo(Set ius) {
		Collection result = new HashSet();
		Collection launchers = getIUs(ius, "org.eclipse.equinox.launcher."); //$NON-NLS-1$
		for (Iterator iterator = launchers.iterator(); iterator.hasNext();) {
			IInstallableUnit object = (IInstallableUnit) iterator.next();
			if (object.getId().endsWith(".source")) //$NON-NLS-1$
				continue;
			GeneratorBundleInfo temp = new GeneratorBundleInfo();
			temp.setSymbolicName(object.getId());
			temp.setVersion(object.getVersion().toString());
			temp.setSpecialConfigCommands("addProgramArg(programArg:--launcher.library);addProgramArg(programArg:@artifact);"); //$NON-NLS-1$
			temp.setSpecialUnconfigCommands("removeProgramArg(programArg:--launcher.library);removeProgramArg(programArg:@artifact);"); //$NON-NLS-1$
			result.add(temp);
		}
		return result;
	}

	/**
	 * @deprecated moved to EclipseInstallAction (perhaps it will be somewhere more general...)
	 */
	private GeneratorBundleInfo createSimpleConfiguratorBundleInfo() {
		GeneratorBundleInfo result = new GeneratorBundleInfo();
		result.setSymbolicName(ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR);
		result.setVersion("0.0.0"); //$NON-NLS-1$
		result.setStartLevel(1);
		result.setMarkedAsStarted(true);
		return result;
	}

	/**
	 * @deprecated moved to EclipseInstallAction (perhaps it will be somewhere more general...)
	 */
	private GeneratorBundleInfo createDropinsReconcilerBundleInfo() {
		GeneratorBundleInfo result = new GeneratorBundleInfo();
		result.setSymbolicName(ORG_ECLIPSE_EQUINOX_P2_RECONCILER_DROPINS);
		result.setVersion("0.0.0"); //$NON-NLS-1$
		result.setMarkedAsStarted(true);
		result.setSpecialConfigCommands("mkdir(path:${installFolder}/dropins)"); //$NON-NLS-1$
		result.setSpecialUnconfigCommands("rmdir(path:${installFolder}/dropins)"); //$NON-NLS-1$
		return result;
	}

	/**
	 * @deprecated moved to BundlesAction
	 */
	private void expandBundleLocations() {
		if (bundleLocations == null) {
			bundleLocations = new File[] {};
			return;
		}
		ArrayList result = new ArrayList();
		for (int i = 0; i < bundleLocations.length; i++) {
			File location = bundleLocations[i];
			if (location.isDirectory()) {
				File[] list = location.listFiles();
				for (int j = 0; j < list.length; j++)
					result.add(list[j]);
			} else {
				result.add(location);
			}
		}
		bundleLocations = (File[]) result.toArray(new File[result.size()]);
	}

	public IArtifactRepository getArtifactRepository() {
		return artifactRepository;
	}

	public File getBaseLocation() {
		return baseLocation;
	}

	public File[] getBundleLocations() {
		return bundleLocations;
	}

	public ConfigData getConfigData() {
		return manipulator == null ? null : manipulator.getConfigData();
	}

	/**
	 * @deprecated moved to DataLoader
	 */
	public ConfigData loadConfigData(File location) {
		if (manipulator == null)
			return null;

		EquinoxFwConfigFileParser parser = new EquinoxFwConfigFileParser(Activator.getContext());
		try {
			parser.readFwConfig(manipulator, location);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		ConfigData data = manipulator.getConfigData();
		String value = data.getFwIndependentProp(ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR_CONFIGURL);
		if (value != null) {
			try {
				//config.ini uses simpleconfigurator, read the bundles.info and replace the bundle infos
				BundleInfo[] bundleInfos = readConfiguration(new URL(value));
				data.setBundles(bundleInfos);
			} catch (MalformedURLException e1) {
				// ignore
			} catch (IOException e1) {
				// ignore
			}

			try {
				data.setFwIndependentProp(ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR_CONFIGURL, EquinoxManipulatorImpl.makeRelative(value, configLocation.toURL()));
			} catch (MalformedURLException e) {
				//ignore
			}
		}

		return data;
	}

	/**
	 * @deprecated moved to DataLoader
	 */
	public static BundleInfo[] readConfiguration(URL url) throws IOException {
		List bundles = new ArrayList();
		try {
			// System.out.println("readConfiguration(URL url):url()=" + url);
			// URL configFileUrl = getConfigFileUrl();
			// URL configFileUrl = Utils.getUrl("file",null,
			// inputFile.getAbsolutePath());
			BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
			// BufferedReader r = new BufferedReader(new FileReader(inputFile));

			String line;
			try {
				URL baseUrl = new URL(url, "./"); //$NON-NLS-1$
				while ((line = r.readLine()) != null) {
					if (line.startsWith("#")) //$NON-NLS-1$
						continue;
					line = line.trim();// symbolicName,version,location,startlevel,expectedState
					if (line.length() == 0)
						continue;

					// (expectedState is an integer).
					if (line.startsWith(PARAMETER_BASEURL + "=")) { //$NON-NLS-1$
						String baseUrlSt = line.substring((PARAMETER_BASEURL + "=").length()); //$NON-NLS-1$
						if (!baseUrlSt.endsWith("/")) //$NON-NLS-1$
							baseUrlSt += "/"; //$NON-NLS-1$
						baseUrl = new URL(url, baseUrlSt);
						continue;
					}
					StringTokenizer tok = new StringTokenizer(line, ",", true); //$NON-NLS-1$
					String symbolicName = tok.nextToken();
					if (symbolicName.equals(",")) //$NON-NLS-1$
						symbolicName = null;
					else
						tok.nextToken(); // ,

					String version = tok.nextToken();
					if (version.equals(",")) //$NON-NLS-1$
						version = null;
					else
						tok.nextToken(); // ,

					String urlSt = tok.nextToken();
					if (urlSt.equals(",")) { //$NON-NLS-1$
						if (symbolicName != null && version != null)
							urlSt = symbolicName + "_" + version + ".jar"; //$NON-NLS-1$ //$NON-NLS-2$
						else
							urlSt = null;
					} else
						tok.nextToken(); // ,
					try {
						new URL(urlSt);
					} catch (MalformedURLException e) {
						urlSt = Utils.getUrlInFull(urlSt, baseUrl).toExternalForm();
					}

					int sl = Integer.parseInt(tok.nextToken().trim());
					tok.nextToken(); // ,
					boolean markedAsStarted = Boolean.valueOf(tok.nextToken()).booleanValue();

					BundleInfo bInfo = new BundleInfo(symbolicName, version, urlSt, sl, markedAsStarted);
					bundles.add(bInfo);
				}
			} finally {
				try {
					r.close();
				} catch (IOException ex) {
					// ignore
				}
			}
		} catch (MalformedURLException e) {
			// TODO log something
		}
		return (BundleInfo[]) bundles.toArray(new BundleInfo[bundles.size()]);
	}

	public File getConfigurationLocation() {
		return configLocation;
	}

	/**
	 * @deprecated logic moved to EclipseInstallAction (and related actions)
	 */
	public ArrayList getDefaultIUs(Set ius) {
		if (defaultIUs != null)
			return defaultIUs;
		defaultIUs = new ArrayList(5);
		if (addDefaultIUs) {
			defaultIUs.addAll(createLauncherBundleInfo(ius));
			defaultIUs.add(createLauncher());
			defaultIUs.add(createSimpleConfiguratorBundleInfo());
			defaultIUs.add(createDropinsReconcilerBundleInfo());
			//			defaultIUs.add(createDefaultConfigurationBundleInfo());
			//			defaultIUs.add(createDefaultUnconfigurationBundleInfo());
		}
		return defaultIUs;
	}

	// TODO: This is kind of ugly. It's purpose is to allow us to craft CUs that we know about and need for our build
	// We should try to replace this with something more generic prior to release
	/**
	 * @deprecated this has been replaced with RootIUAdvice and related things
	 */
	public Collection getOtherIUs() {
		if (otherIUs != null)
			return otherIUs;
		otherIUs = new ArrayList();
		otherIUs.add(createDropinsReconcilerBundleInfo());
		return otherIUs;
	}

	public File getExecutableLocation() {
		return executableLocation;
	}

	public File getFeaturesLocation() {
		return featuresLocation;
	}

	public String getFlavor() {
		//use 'tooling' as default flavor since we are not actively using flavors yet
		return flavor == null ? "tooling" : flavor; //$NON-NLS-1$
	}

	/**
	 * @deprecated moved to DataLoader
	 */
	private FrameworkAdmin getFrameworkAdmin() {
		if (frameworkAdminTracker == null) {
			try {
				Filter filter = Activator.getContext().createFilter(frameworkAdminFillter);
				frameworkAdminTracker = new ServiceTracker(Activator.getContext(), filter, null);
				frameworkAdminTracker.open();
			} catch (InvalidSyntaxException e) {
				// never happens
				e.printStackTrace();
			}
		}
		//		try {
		//			frameworkAdminTracker.waitForService(500);
		//		} catch (InterruptedException e) {
		//			// ignore
		//		}

		FrameworkAdmin admin = (FrameworkAdmin) frameworkAdminTracker.getService();
		if (admin == null) {
			startBundle(ORG_ECLIPSE_EQUINOX_FRAMEWORKADMIN_EQUINOX);
			startBundle(ORG_ECLIPSE_EQUINOX_SIMPLECONFIGURATOR_MANIPULATOR);
			admin = (FrameworkAdmin) frameworkAdminTracker.getService();
		}
		return admin;
	}

	public boolean getIsUpdateCompatible() {
		return updateCompatibility;
	}

	private Collection getIUs(Set ius, String prefix) {
		Set result = new HashSet();
		for (Iterator iterator = ius.iterator(); iterator.hasNext();) {
			IInstallableUnit tmp = (IInstallableUnit) iterator.next();
			if (tmp.getId().startsWith(prefix))
				result.add(tmp);
		}
		return result;
	}

	public File getJRELocation() {
		//assume JRE is relative to install location
		if (executableLocation == null)
			return null;
		return new File(executableLocation.getParentFile(), "jre"); //$NON-NLS-1$
	}

	public String getLauncherConfig() {
		return launcherConfig;
	}

	public LauncherData getLauncherData() {
		return manipulator == null ? null : manipulator.getLauncherData();
	}

	public String[][] getMappingRules() {
		return mappingRules;
	}

	public IMetadataRepository getMetadataRepository() {
		return metadataRepository;
	}

	public String getRootId() {
		return rootId;
	}

	public String getRootVersion() {
		if (rootVersion == null || rootVersion.length() == 0)
			return "0.0.0"; //$NON-NLS-1$
		return rootVersion;
	}

	public String getProductFile() {
		return productFile;
	}

	public URI getSiteLocation() {
		return siteLocation;
	}

	public void initialize(File base) {
		// if the various locations are set in self, use them.  Otherwise compute defaults
		File[] bundles = bundleLocations == null ? new File[] {new File(base, "plugins")} : bundleLocations; //$NON-NLS-1$
		File features = featuresLocation == null ? new File(base, "features") : featuresLocation; //$NON-NLS-1$
		File executable = executableLocation == null ? new File(base, getDefaultExecutableName(os)) : executableLocation;
		File configuration = configLocation == null ? new File(base, "configuration") : configLocation; //$NON-NLS-1$

		initialize(base, configuration, executable, bundles, features);
	}

	public void initialize(File base, File config, File executable, File[] bundles, File features) {
		if (base == null || !base.exists())
			throw new RuntimeException(NLS.bind(Messages.exception_sourceDirectoryInvalid, base == null ? "null" : base.getAbsolutePath())); //$NON-NLS-1$
		this.baseLocation = base;
		if (config == null || config.exists())
			this.configLocation = config;
		if (executable == null || executable.exists())
			this.executableLocation = executable;
		if (bundles != null)
			bundleLocations = bundles;
		if (features != null)
			featuresLocation = features;
		expandBundleLocations();

		// if the config or exe are not set then we cannot be generating any data related to the config so 
		// don't bother setting up the manipulator.  In fact, the manipulator will likely be invalid without
		// these locations.
		if (configLocation == null || executableLocation == null)
			return;

		createFrameworkManipulator();

		LauncherData launcherData = manipulator.getLauncherData();
		launcherData.setFwPersistentDataLocation(configLocation, true);
		launcherData.setLauncher(executableLocation);
		try {
			manipulator.load();
		} catch (IllegalStateException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		} catch (FrameworkAdminRuntimeException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
	}

	public boolean publishArtifactRepository() {
		return publishArtifactRepo;
	}

	public boolean publishArtifacts() {
		return publishArtifacts;
	}

	public boolean reuseExistingPack200Files() {
		return reuseExistingPack200Files;
	}

	public void reuseExistingPack200Files(boolean publishPack) {
		reuseExistingPack200Files = publishPack;
	}

	public void setAddDefaultIUs(boolean value) {
		addDefaultIUs = value;
	}

	public void setAppend(boolean value) {
		append = value;
	}

	public void setArtifactRepository(IArtifactRepository value) {
		artifactRepository = value;
	}

	public void setExecutableLocation(String value) {
		executableLocation = new File(value);
	}

	public void setFlavor(String value) {
		flavor = value;
	}

	public void setIsUpdateCompatible(boolean isCompatible) {
		this.updateCompatibility = isCompatible;
	}

	public void setLauncherConfig(String value) {
		launcherConfig = value;
	}

	public void setMappingRules(String[][] value) {
		mappingRules = value;
	}

	public void setMetadataRepository(IMetadataRepository value) {
		metadataRepository = value;
	}

	public void setOS(String os) {
		this.os = os;
	}

	public void setPublishArtifactRepository(boolean value) {
		publishArtifactRepo = value;
	}

	public void setPublishArtifacts(boolean value) {
		publishArtifacts = value;
	}

	public void setRootId(String value) {
		rootId = value;
	}

	public void setRootVersion(String value) {
		rootVersion = value;
	}

	public void setProductFile(String file) {
		productFile = file;
	}

	/**
	 * Sets the location of site.xml if applicable.
	 */
	public void setSiteLocation(URI location) {
		this.siteLocation = location;
	}

	private boolean startBundle(String bundleId) {
		PackageAdmin packageAdmin = (PackageAdmin) ServiceHelper.getService(Activator.getContext(), PackageAdmin.class.getName());
		if (packageAdmin == null)
			return false;

		Bundle[] bundles = packageAdmin.getBundles(bundleId, null);
		if (bundles != null && bundles.length > 0) {
			for (int i = 0; i < bundles.length; i++) {
				try {
					if ((bundles[0].getState() & Bundle.RESOLVED) > 0) {
						bundles[0].start();
						return true;
					}
				} catch (BundleException e) {
					// failed, try next bundle
				}
			}
		}
		return false;
	}

	public String getVersionAdvice() {
		return versionAdvice;
	}

	public void setVersionAdvice(String advice) {
		this.versionAdvice = advice;
	}
}
