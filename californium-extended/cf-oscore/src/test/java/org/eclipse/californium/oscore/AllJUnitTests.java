/*******************************************************************************
 * Copyright (c) 2019 RISE SICS and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Tobias Andersson (RISE SICS)
 *    Rikard Höglund (RISE SICS)
 *    
 ******************************************************************************/
package org.eclipse.californium.oscore;

import org.eclipse.californium.oscore.group.CountersignAlgorithmsTest;
import org.eclipse.californium.oscore.group.GroupDecryptorTest;
import org.eclipse.californium.oscore.group.GroupEncryptorTest;
import org.eclipse.californium.oscore.group.GroupKeyDerivationTest;
import org.eclipse.californium.oscore.group.GroupModesTest;
import org.eclipse.californium.oscore.group.GroupModesTestAlt;
import org.eclipse.californium.oscore.group.GroupOSCoreServerClientTest;
import org.eclipse.californium.oscore.group.GroupOscoreCtxTest;
import org.eclipse.californium.oscore.group.KeyRemappingTest;
import org.eclipse.californium.oscore.group.SharedSecretCalculationTest;
import org.eclipse.californium.oscore.group.interop.OneKeyDecoderTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Class to launch all JUnit tests defined for OSCORE
 *
 */
@RunWith(Suite.class)
@SuiteClasses({ ByteIdTest.class, HashMapCtxDBTest.class, OptionJuggleTest.class, OSCoreCtxTest.class, OSCoreTest.class,
		OSSerializerTest.class, OSCoreServerClientTest.class, OSCoreObserveTest.class, EncryptorTest.class,
		DecryptorTest.class, EndpointContextInfoTest.class, ContextRederivationTest.class,
		OSCoreInnerBlockwiseTest.class, OSCoreOuterBlockwiseTest.class, OSCoreAlgorithmsTest.class,
		GroupOSCoreServerClientTest.class, KeyRemappingTest.class, SharedSecretCalculationTest.class,
		GroupEncryptorTest.class, GroupDecryptorTest.class, CountersignAlgorithmsTest.class, GroupModesTest.class,
		GroupModesTestAlt.class, GroupKeyDerivationTest.class, OneKeyDecoderTest.class, GroupOscoreCtxTest.class })
public class AllJUnitTests {

}
