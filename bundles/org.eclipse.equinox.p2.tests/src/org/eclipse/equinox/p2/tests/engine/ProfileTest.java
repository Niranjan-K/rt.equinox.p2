/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.engine;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.eclipse.equinox.internal.p2.engine.ProfileParser;
import org.eclipse.equinox.internal.p2.engine.ProfileWriter;
import org.eclipse.equinox.p2.engine.Profile;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;
import org.eclipse.equinox.p2.tests.TestActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.xml.sax.*;

/**
 * Simple test of the engine API.
 */
public class ProfileTest extends AbstractProvisioningTest {
	public ProfileTest(String name) {
		super(name);
	}

	public ProfileTest() {
		super("");
	}

	public void testNullProfile() {
		try {
			createProfile(null);
		} catch (IllegalArgumentException expected) {
			return;
		}
		fail();
	}

	public void testEmptyProfile() {
		try {
			createProfile("");
		} catch (IllegalArgumentException expected) {
			return;
		}
		fail();
	}

	public void testNestedProfileStructure() {
		Profile parent = createProfile("parent");
		Profile child = createProfile("child", parent);
		assertTrue("Parentless profile should be a root.", parent.isRootProfile());
		assertFalse("Child profile should not be a root.", child.isRootProfile());
		assertTrue("Parent should be parent of child", child.getParentProfile() == parent);
		assertTrue("Parent should have one child.", parent.getSubProfileIds().length == 1);
		assertTrue("Child should have no children.", child.getSubProfileIds().length == 0);

		Profile grandchild = createProfile("grand", child);
		assertFalse("Grandchild profile should not be a root.", grandchild.isRootProfile());
		assertTrue("Parent should have one child.", parent.getSubProfileIds().length == 1);
		assertTrue("Child should have one child.", child.getSubProfileIds().length == 1);
		assertTrue("Grandparent of grandchild should be parent of child.", grandchild.getParentProfile().getParentProfile() == parent);
	}

	/*	The test profile has the following structure and properties where
	 *  	id{x,y}  indicates a profile with id "id" and properties defined
	 *				 with keys "x" and "y"
	 *
	 *                                    grandchild00{foo}
	 *                                   /
	 *                                  /
	 *                      child0{foo} | --- grandchild01{}
	 *                     /             \
	 *					  /               \
	 *                   /                 grandchild01{bar}
	 *	parent{foo,bar} |				   
	 *                   \            grandchild10{foo}
	 *                    \          /
	 *                     child1{} |
	 *								 \
	 *                                grandchild11{}
	 *
	 */
	private static String parentId = "parent";
	private static String child0Id = "child0";
	private static String grandchild00Id = "grand00";
	private static String grandchild01Id = "grand01";
	private static String grandchild02Id = "grand02";
	private static String child1Id = "child1";
	private static String grandchild10Id = "grand10";
	private static String grandchild11Id = "grand11";

	private static String key = "org.eclipse.p2.foo";
	private static String parentValue = "parent";
	private static String child0Value = "child0";
	private static String grandchild00Value = "grandchild00";
	private static String grandchild02Value = "grandchild02";
	private static String grandchild10Value = "grandchild10";
	private static String otherKey = "org.eclipse.p2.bar";
	private static String otherValue = "other";

	// Create the profiles and test get after set
	// for associated properties.
	private Profile[] createTestProfiles() {

		Map properties = new HashMap();

		properties.put(key, parentValue);
		properties.put(otherKey, otherValue);
		Profile parent = createProfile(parentId, null, properties);
		properties.clear();
		assertTrue(parentValue.equals(parent.getProperty(key)));
		assertTrue(otherValue.equals(parent.getProperty(otherKey)));

		properties.put(key, child0Value);
		Profile child0 = createProfile(child0Id, parent, properties);
		properties.clear();
		assertTrue(child0Value.equals(child0.getProperty(key)));

		Profile child1 = createProfile(child1Id, parent, properties);
		// no value in child1

		properties.put(key, grandchild00Value);
		Profile grandchild00 = createProfile(grandchild00Id, child0, properties);
		properties.clear();
		assertTrue(grandchild00Value.equals(grandchild00.getProperty(key)));

		Profile grandchild01 = createProfile(grandchild01Id, child0);
		// no value in grandchild01

		properties.put(otherKey, grandchild02Value);
		Profile grandchild02 = createProfile(grandchild02Id, child0, properties);
		properties.clear();
		assertTrue(grandchild02Value.equals(grandchild02.getProperty(otherKey)));

		properties.put(key, grandchild10Value);
		Profile grandchild10 = createProfile(grandchild10Id, child1, properties);
		properties.clear();
		assertTrue(grandchild10Value.equals(grandchild10.getProperty(key)));

		Profile grandchild11 = createProfile(grandchild11Id, child1);
		// no value in grandchild11

		parent = getProfile(parentId);
		child0 = getProfile(child0Id);
		child1 = getProfile(child1Id);
		grandchild00 = getProfile(grandchild00Id);
		grandchild01 = getProfile(grandchild01Id);
		grandchild02 = getProfile(grandchild02Id);
		grandchild10 = getProfile(grandchild10Id);
		grandchild11 = getProfile(grandchild11Id);

		Profile[] profiles = {parent, child0, child1, grandchild00, grandchild01, grandchild02, grandchild10, grandchild11};
		return profiles;
	}

	public void testNestedProfileProperties() {
		validateProfiles(createTestProfiles());
	}

	public void validateProfiles(Profile[] profiles) {
		Profile parent = profiles[0];
		Profile child0 = profiles[1];
		Profile child1 = profiles[2];
		Profile grandchild00 = profiles[3];
		Profile grandchild01 = profiles[4];
		Profile grandchild02 = profiles[5];
		Profile grandchild10 = profiles[6];
		Profile grandchild11 = profiles[7];

		assertTrue(parentId.equals(parent.getProfileId()));
		assertTrue("Profile should have 3 local properties", parent.getLocalProperties().size() == 3);
		assertTrue(parentValue.equals(parent.getProperty(key)));
		assertTrue(otherValue.equals(parent.getProperty(otherKey)));
		assertTrue("Parent should have 2 children.", parent.getSubProfileIds().length == 2);

		assertTrue(child0Id.equals(child0.getProfileId()));
		assertTrue("First Child should have 1 local properties.", child0.getLocalProperties().size() == 1);
		assertTrue(child0Value.equals(child0.getProperty(key)));
		assertTrue(otherValue.equals(child0.getProperty(otherKey)));
		assertTrue("First Child should have 3 children.", child0.getSubProfileIds().length == 3);

		assertTrue(child1Id.equals(child1.getProfileId()));
		assertTrue("Second Child should have 0 local properties.", child1.getLocalProperties().size() == 0);
		assertTrue(parentValue.equals(child1.getProperty(key)));
		assertTrue(otherValue.equals(child1.getProperty(otherKey)));
		assertTrue("Second Child should have 2 children.", child1.getSubProfileIds().length == 2);

		assertTrue(grandchild00Id.equals(grandchild00.getProfileId()));
		assertTrue("First Grandchild of first Child should have 1 property.", grandchild00.getLocalProperties().size() == 1);
		assertTrue(grandchild00Value.equals(grandchild00.getProperty(key)));
		assertTrue(otherValue.equals(grandchild00.getProperty(otherKey)));

		assertTrue(grandchild01Id.equals(grandchild01.getProfileId()));
		assertTrue("Second Grandchild of first Child should have 0 properties.", grandchild01.getLocalProperties().size() == 0);
		assertTrue(child0Value.equals(grandchild01.getProperty(key)));
		assertTrue(otherValue.equals(grandchild01.getProperty(otherKey)));

		assertTrue(grandchild02Id.equals(grandchild02.getProfileId()));
		assertTrue("Third Grandchild of first Child should have 1 property.", grandchild02.getLocalProperties().size() == 1);
		assertTrue(child0Value.equals(grandchild02.getProperty(key)));
		assertTrue(grandchild02Value.equals(grandchild02.getProperty(otherKey)));

		assertTrue(grandchild10Id.equals(grandchild10.getProfileId()));
		assertTrue("First Grandchild of second Child should have 1 property.", grandchild10.getLocalProperties().size() == 1);
		assertTrue(grandchild10Value.equals(grandchild10.getProperty(key)));
		assertTrue(otherValue.equals(grandchild10.getProperty(otherKey)));

		assertTrue(grandchild11Id.equals(grandchild11.getProfileId()));
		assertTrue("Second Grandchild of second Child should have 0 properties.", grandchild11.getLocalProperties().size() == 0);
		assertTrue(parentValue.equals(grandchild11.getProperty(key)));
		assertTrue(otherValue.equals(grandchild11.getProperty(otherKey)));
	}

	private static String PROFILE_TEST_TARGET = "profileTest";
	private static Version PROFILE_TEST_VERSION = new Version("0.0.1");

	private static String PROFILE_TEST_ELEMENT = "test";

	class ProfileStringWriter extends ProfileWriter {

		public ProfileStringWriter(ByteArrayOutputStream stream) throws IOException {
			super(stream, new ProcessingInstruction[] {ProcessingInstruction.makeClassVersionInstruction(PROFILE_TEST_TARGET, Profile.class, PROFILE_TEST_VERSION)});
		}

		public void writeTest(Profile[] profiles) {
			start(PROFILE_TEST_ELEMENT);
			writeProfiles(profiles);
			end(PROFILE_TEST_ELEMENT);
		}
	}

	class ProfileStringParser extends ProfileParser {

		private Profile[] profiles = null;

		public ProfileStringParser(BundleContext context, String bundleId) {
			super(context, bundleId);
		}

		public void parse(String profileString) throws IOException {
			this.status = null;
			try {
				getParser();
				TestHandler2 testHandler = new TestHandler2();
				xmlReader.setContentHandler(new ProfileDocHandler(PROFILE_TEST_ELEMENT, testHandler));
				xmlReader.parse(new InputSource(new StringReader(profileString)));
				if (isValidXML()) {
					profiles = testHandler.profiles;
				}
			} catch (SAXException e) {
				throw new IOException(e.getMessage());
			} catch (ParserConfigurationException e) {
				fail();
			}
		}

		private final class ProfileDocHandler extends DocHandler {

			public ProfileDocHandler(String rootName, RootHandler rootHandler) {
				super(rootName, rootHandler);
			}

			public void processingInstruction(String target, String data) throws SAXException {
				if (PROFILE_TEST_TARGET.equals(target)) {
					String clazz = extractPIClass(data);
					try {
						if (!Class.forName(clazz).equals(Profile.class)) {
							throw new SAXException("Wrong class '" + clazz + "' in processing instruction"); //$NON-NLS-1$//$NON-NLS-2$
						}
					} catch (ClassNotFoundException e) {
						throw new SAXException("Profile class '" + clazz + "' not found"); //$NON-NLS-1$//$NON-NLS-2$
					}

					Version profileTestVersion = extractPIVersion(target, data);
					if (!PROFILE_TEST_VERSION.equals(profileTestVersion)) {
						throw new SAXException("Bad profile test version.");
					}
				}
			}
		}

		private final class TestHandler2 extends RootHandler {

			private ProfilesHandler profilesHandler;
			Profile[] profiles;

			protected void handleRootAttributes(Attributes attributes) {
			}

			public void startElement(String name, Attributes attributes) throws SAXException {
				if (PROFILES_ELEMENT.equals(name)) {
					if (profilesHandler == null) {
						profilesHandler = new ProfilesHandler(this, attributes);
					} else {
						duplicateElement(this, name, attributes);
					}
				} else {
					invalidElement(name, attributes);
				}
			}

			protected void finished() {
				if (isValidXML()) {
					if (profilesHandler != null) {
						profiles = profilesHandler.getProfiles();
					}
				}
			}

		}

		//		private final class TestHandler extends RootHandler {
		//
		//			private ProfileHandler profileHandler = null;
		//
		//			private Profile profile = null;
		//			private Map singleton = new HashMap(1);
		//
		//			public TestHandler() {
		//				super();
		//			}
		//
		//			public Profile getProfile() {
		//				return profile;
		//			}
		//
		//			protected void handleRootAttributes(Attributes attributes) {
		//				String[] values = parseAttributes(attributes, noAttributes, noAttributes);
		//			}
		//
		//			public void startElement(String name, Attributes attributes) {
		//				if (PROFILE_ELEMENT.equals(name)) {
		//					if (profileHandler == null) {
		//						profileHandler = new ProfileHandler(this, attributes, singleton);
		//					} else {
		//						duplicateElement(this, name, attributes);
		//					}
		//				} else {
		//					invalidElement(name, attributes);
		//				}
		//			}
		//
		//			protected void finished() {
		//				if (isValidXML()) {
		//					if (profileHandler != null && singleton.size() == 1) {
		//						profile = new Profile(profileHandler.getProfileId(), null, profileHandler.getProperties());
		//					}
		//				}
		//			}
		//		}

		protected String getErrorMessage() {
			return "Error parsing profile string";
		}

		protected Object getRootObject() {
			Map result = new HashMap();
			for (int i = 0; i < profiles.length; i++) {
				result.put(profiles[i].getProfileId(), profiles[i]);
			}
			return result;
		}
	}

	public void testProfilePersistence() throws IOException {
		Profile[] testProfiles = createTestProfiles();
		ByteArrayOutputStream output0 = new ByteArrayOutputStream(1492);
		ProfileStringWriter writer0 = new ProfileStringWriter(output0);
		writer0.writeTest(testProfiles);
		String profileText0 = output0.toString();
		output0.close();

		ProfileStringParser parser = new ProfileStringParser(TestActivator.context, TestActivator.PI_PROV_TESTS);
		parser.parse(profileText0);
		assertTrue("Error parsing test profile: " + parser.getStatus().getMessage(), parser.getStatus().isOK());
		Map profileMap = (Map) parser.getRootObject();
		Profile parent = (Profile) profileMap.get(parentId);
		Profile child0 = (Profile) profileMap.get(child0Id);
		Profile child1 = (Profile) profileMap.get(child1Id);
		Profile grandchild00 = (Profile) profileMap.get(grandchild00Id);
		Profile grandchild01 = (Profile) profileMap.get(grandchild01Id);
		Profile grandchild02 = (Profile) profileMap.get(grandchild02Id);
		Profile grandchild10 = (Profile) profileMap.get(grandchild10Id);
		Profile grandchild11 = (Profile) profileMap.get(grandchild11Id);
		Profile[] profiles = {parent, child0, child1, grandchild00, grandchild01, grandchild02, grandchild10, grandchild11};
		validateProfiles(profiles);
		ByteArrayOutputStream output1 = new ByteArrayOutputStream(1492);
		ProfileStringWriter writer = new ProfileStringWriter(output1);

		writer.writeTest(profiles);
		String profileText1 = output1.toString();
		output1.close();
		assertTrue("Profile write after read after write produced different XML", profileText1.equals(profileText0));
	}

}
