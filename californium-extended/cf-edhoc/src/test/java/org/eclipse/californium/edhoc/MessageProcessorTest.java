package org.eclipse.californium.edhoc;

import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Set;

import org.eclipse.californium.cose.AlgorithmID;
import org.eclipse.californium.cose.CoseException;
import org.eclipse.californium.cose.KeyKeys;
import org.eclipse.californium.cose.OneKey;
import org.eclipse.californium.elements.util.Bytes;
import org.eclipse.californium.oscore.HashMapCtxDB;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.upokecenter.cbor.CBORObject;

import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import net.i2p.crypto.eddsa.Utils;

public class MessageProcessorTest {

	/**
	 * Tests identification of EDHOC messages. Based on messages from the EDHOC test vectors.
	 * 
	 */
	@Test
	public void testMessageType() {
		
		// Note: the actual EDHOC message 1 starts with 0x00. The byte 0xf5 (CBOR simple value True) is prepended,
		//       in order to pass the check against what returned by the EDHOC engine, to be sent as a CoAP request payload.
		byte[] message1 = Utils
				.hexToBytes("f500005820e31ec15ee8039427dfc4727ef17e2e0e69c54437f3c5828019ef0a6388c125520e");
		
		byte[] message2 = Utils.hexToBytes(
				"5870e1739096c5c9582c1298918166d69548c78f7497b258c0856aa2019893a39425690bdd9b15885138490d3b8ac735e2ad7912d58d0e3995f2b54e8e63e90bc3c42620308c10508d0f40c8f48f87a404cfc78fb522db588a12f3d8e76436fc26a81daeb735c34feb1f7254bda2b7d014f332");
		
		// Note: the actual EDHOC message 3 starts with 0x58. The byte 0x32 (CBOR encoding for -19) is prepended as C_R,
		//       in order to pass the check against what returned by the EDHOC engine, to be sent as a CoAP request payload. 
		byte[] message3 = Utils.hexToBytes(
				"3258584c53ed22c45fb00cad889b4c06f2a26cf49154cb8bdf4eee44e2b50221ab1f029d3d3e0523ddf9d7610c376c728a1e901692f1da0782a3472ff6eb1bb6810c6f686879c9a5594f8f170ca5a2b5bf05a74f42cdd9c854e01e");

		HashMap<CBORObject, EdhocSession> edhocSessions = new HashMap<CBORObject, EdhocSession>();
		
		Provider EdDSA = new EdDSASecurityProvider();
		Security.insertProviderAt(EdDSA, 1);

		// Set the application profile
		// - Supported authentication methods
		// - Use of message_4 as expected to be sent by the Responder
		// - Use of EDHOC for keying OSCORE
		// - Supporting for the EDHOC+OSCORE request
		// - Method for converting from OSCORE Recipient/Sender ID to EDHOC Connection Identifier
		//
		Set<Integer> authMethods = new HashSet<Integer>();
		for (int i = 0; i <= Constants.EDHOC_AUTH_METHOD_3; i++ )
			authMethods.add(i);
		boolean useMessage4 = false;
		boolean usedForOSCORE = true;
		boolean supportCombinedRequest = false;
		AppProfile appProfile = new AppProfile(authMethods, useMessage4, usedForOSCORE, supportCombinedRequest);
		
		int method = 0;
		
		List<Integer> supportedCipherSuites = new ArrayList<Integer>();
		supportedCipherSuites.add(0);
		
		/* Initiator information*/

		// Connection Identifier of the Initiator
		byte[] connectionIdentifierInitiator = new byte[] {(byte) 0x0e};
		
		// The identity key of the Initiator
		byte[] privateIdentityKeyBytesInit = Utils.hexToBytes("366a5859a4cd65cfaeaf0566c9fc7e1a93306fdec17763e05813a70f21ff59db");
		byte[] publicIdentityKeyBytesInit = Utils.hexToBytes("ec2c2eb6cdd95782a8cd0b2e9c44270774dcbd31bfbe2313ce80132e8a261c04");
		OneKey identityKeyInit = SharedSecretCalculation.buildEd25519OneKey(privateIdentityKeyBytesInit, publicIdentityKeyBytesInit);
		
		// The x509 certificate of the Initiator
		byte[] serializedCertInit = Utils.hexToBytes("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F404142434445464748494A4B4C4D4E4F505152535455565758595A5B5C5D5E5F606162636465666768696A6B6C6D6E6F707172737475767778797A7B7C7D7E7F808182838485868788");
		
		// CRED_I, as serialization of a CBOR byte string wrapping the serialized certificate
		byte[] credI = CBORObject.FromObject(serializedCertInit).EncodeToBytes();
		
		// ID_CRED_I for the identity key of the initiator, built from the x509 certificate using x5t
		CBORObject idCredI = Util.buildIdCredX5t(serializedCertInit);

		HashMap<Integer, HashMap<Integer, OneKey>> keyPairsI = new HashMap<Integer, HashMap<Integer, OneKey>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> credsI = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> idCredsI = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		
		keyPairsI.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, OneKey>());
		credsI.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		idCredsI.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		
	    keyPairsI.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
	    		  put(Integer.valueOf(Constants.CURVE_Ed25519), identityKeyInit);
	    credsI.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
	    	      put(Integer.valueOf(Constants.CURVE_Ed25519), CBORObject.FromObject(credI));
	    idCredsI.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
	    		  put(Integer.valueOf(Constants.CURVE_Ed25519), idCredI);

		// Create the session for the Initiator (with only the minimal set of information required for this test)
		boolean initiator = true;
		KissEDP edp = new KissEDP();
		HashMapCtxDB db = new HashMapCtxDB();
		EdhocSession sessionInitiator = new EdhocSession(initiator, true, method, connectionIdentifierInitiator,
														 keyPairsI, idCredsI, credsI, supportedCipherSuites,
														 appProfile, edp, db);
		
		edhocSessions.put(CBORObject.FromObject(connectionIdentifierInitiator), sessionInitiator);

		
		/* Responder information*/
		
		// Connection Identifier of the Responder
		byte[] connectionIdentifierResponder = new byte[] {(byte) 0x32};
		
		// The identity key of the Responder
		byte[] privateIdentityKeyBytesResp = Utils.hexToBytes("bc4d4f9882612233b402db75e6c4cf3032a70a0d2e3ee6d01b11ddde5f419cfc");
		byte[] publicIdentityKeyBytesResp = Utils.hexToBytes("27eef2b08a6f496faedaa6c7f9ec6ae3b9d52424580d52e49da6935edf53cdc5");
		OneKey identityKeyResp = SharedSecretCalculation.buildEd25519OneKey(privateIdentityKeyBytesResp, publicIdentityKeyBytesResp);
		
		// The x509 certificate of the Responder
		byte[] serializedCertResp = Utils.hexToBytes("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F404142434445464748494A4B4C4D4E4F505152535455565758595A5B5C5D5E5F606162636465666768696A6B6C6D6E");
		
		// CRED_R, as serialization of a CBOR byte string wrapping the serialized certificate
		byte[] credR = CBORObject.FromObject(serializedCertResp).EncodeToBytes();
		
		// ID_CRED_R for the identity key of the Responder, built from the x509 certificate using x5t
		CBORObject idCredR = Util.buildIdCredX5t(serializedCertResp);

		HashMap<Integer, HashMap<Integer, OneKey>> keyPairsR = new HashMap<Integer, HashMap<Integer, OneKey>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> credsR = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> idCredsR = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		
		keyPairsR.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, OneKey>());
		credsR.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		idCredsR.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		
	    keyPairsR.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
	    		  put(Integer.valueOf(Constants.CURVE_Ed25519), identityKeyResp);
	    credsR.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
	    	      put(Integer.valueOf(Constants.CURVE_Ed25519), CBORObject.FromObject(credR));
	    idCredsR.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
	    		  put(Integer.valueOf(Constants.CURVE_Ed25519), idCredR);
		
		// Create the session for the Responder (with only the minimal set of information required for this test)
		initiator = false;
		KissEDP edp2 = new KissEDP();
		HashMapCtxDB db2 = new HashMapCtxDB();
		EdhocSession sessionResponder = new EdhocSession(initiator, true, method, connectionIdentifierResponder,
														 keyPairsR, idCredsR, credsR, supportedCipherSuites,
														 appProfile, edp2, db2);
		
		edhocSessions.put(CBORObject.FromObject(connectionIdentifierResponder), sessionResponder);
		
		
		// Test from the point of view of the Initiator as Client
		Assert.assertEquals(Constants.EDHOC_MESSAGE_1, MessageProcessor.messageType(
								message1, true, edhocSessions, connectionIdentifierInitiator));
		sessionInitiator.setCurrentStep(Constants.EDHOC_SENT_M1);
		Assert.assertEquals(Constants.EDHOC_MESSAGE_2, MessageProcessor.messageType(
								message2, false, edhocSessions, connectionIdentifierInitiator));
		sessionInitiator.setCurrentStep(Constants.EDHOC_AFTER_M3);
		Assert.assertEquals(Constants.EDHOC_MESSAGE_3, MessageProcessor.messageType(
								message3, true, edhocSessions, connectionIdentifierInitiator));

		
		// Test from the point of view of the Responder as Server
		Assert.assertEquals(Constants.EDHOC_MESSAGE_1, MessageProcessor.messageType(
								message1, true, edhocSessions, null));
		sessionResponder.setCurrentStep(Constants.EDHOC_AFTER_M2);
		Assert.assertEquals(Constants.EDHOC_MESSAGE_2, MessageProcessor.messageType(
								message2, false, edhocSessions, connectionIdentifierResponder));
		sessionResponder.setCurrentStep(Constants.EDHOC_SENT_M2);
		Assert.assertEquals(Constants.EDHOC_MESSAGE_3, MessageProcessor.messageType(
								message3, true, edhocSessions, null));
		
		
		// Error message is not from test vectors
		CBORObject cX = CBORObject.FromObject(new byte[] { (byte) 0x59, (byte) 0xe9 });
		CBORObject errMsg = CBORObject.FromObject("Something went wrong");
		CBORObject suitesR = CBORObject.FromObject(1);
		List<CBORObject> errorMessageList;
		
		// Test for an EDHOC error message as an incoming/outgoing response
		errorMessageList = new ArrayList<CBORObject>();
		errorMessageList.add(CBORObject.FromObject(Constants.ERR_CODE_UNSPECIFIED_ERROR));
		errorMessageList.add(errMsg);
		byte[] errorMessage = Util.buildCBORSequence(errorMessageList);
		Assert.assertEquals(Constants.EDHOC_ERROR_MESSAGE, MessageProcessor.messageType(
				            	errorMessage, false, edhocSessions, connectionIdentifierInitiator));
		errorMessageList = new ArrayList<CBORObject>();
		errorMessageList.add(CBORObject.FromObject(Constants.ERR_CODE_WRONG_SELECTED_CIPHER_SUITE));
		errorMessageList.add(suitesR);
		errorMessage = Util.buildCBORSequence(errorMessageList);
		Assert.assertEquals(Constants.EDHOC_ERROR_MESSAGE, MessageProcessor.messageType(
				            	errorMessage, false, edhocSessions, connectionIdentifierInitiator));
		
		// Test for an EDHOC error message as an incoming/outgoing request
		errorMessageList = new ArrayList<CBORObject>();
		errorMessageList.add(cX);
		errorMessageList.add(CBORObject.FromObject(Constants.ERR_CODE_UNSPECIFIED_ERROR));
		errorMessageList.add(errMsg);
		errorMessage = Util.buildCBORSequence(errorMessageList);
		Assert.assertEquals(Constants.EDHOC_ERROR_MESSAGE, MessageProcessor.messageType(
				            	errorMessage, true, edhocSessions, connectionIdentifierInitiator));
		errorMessageList = new ArrayList<CBORObject>();
		errorMessageList.add(cX);
		errorMessageList.add(CBORObject.FromObject(Constants.ERR_CODE_WRONG_SELECTED_CIPHER_SUITE));
		errorMessageList.add(suitesR);
		errorMessage = Util.buildCBORSequence(errorMessageList);
		Assert.assertEquals(Constants.EDHOC_ERROR_MESSAGE, MessageProcessor.messageType(
				            	errorMessage, true, edhocSessions, connectionIdentifierInitiator));
		
	}

	
	/**
	 * Test writing of message 1, for authentication with signatures, with x.509 certificates identified by 'x5t'
	 * 
	 * See: upcoming draft-ietf-lake-traces-01
	 */
	@Test
	public void testWriteMessage1Ciphersuite0Method0() {
		
		// Insert EdDSA security provider
		final Provider EdDSA = new EdDSASecurityProvider();
		Security.insertProviderAt(EdDSA, 1);
		
		boolean initiator = true;
		int method = 0;
		
		
		// Connection Identifier of the Initiator
		byte[] connectionIdentifierInitiator = new byte[] {(byte) 0x2d};
		
		List<Integer> cipherSuites = new ArrayList<Integer>();
		cipherSuites.add(0);
		
		// The identity key of the Initiator
		byte[] privateIdentityKeyBytes = Utils.hexToBytes("4c5b25878f507c6b9dae68fbd4fd3ff997533db0af00b25d324ea28e6c213bc8");
		byte[] publicIdentityKeyBytes = Utils.hexToBytes("ed06a8ae61a829ba5fa54525c9d07f48dd44a302f43e0f23d8cc20b73085141e");
		OneKey identityKey = SharedSecretCalculation.buildEd25519OneKey(privateIdentityKeyBytes, publicIdentityKeyBytes);
		
		CBORObject[] ead1 = null;
		
		// Just for method compatibility; it is not used for EDHOC Message 1
		// The x509 certificate of the Initiator
		byte[] serializedCert = Utils.hexToBytes("3081EE3081A1A003020102020462319EA0300506032B6570301D311B301906035504030C124544484F4320526F6F742045643235353139301E170D3232303331363038323430305A170D3239313233313233303030305A30223120301E06035504030C174544484F4320496E69746961746F722045643235353139302A300506032B6570032100ED06A8AE61A829BA5FA54525C9D07F48DD44A302F43E0F23D8CC20B73085141E300506032B6570034100521241D8B3A770996BCFC9B9EAD4E7E0A1C0DB353A3BDF2910B39275AE48B756015981850D27DB6734E37F67212267DD05EEFF27B9E7A813FA574B72A00B430B");
		
		// CRED_I, as serialization of a CBOR byte string wrapping the serialized certificate
		byte[] cred = CBORObject.FromObject(serializedCert).EncodeToBytes();
		
		CBORObject idCred = Util.buildIdCredX5t(cred);		

		
		// Set the application profile
		// - Supported authentication methods
		// - Use of message_4 as expected to be sent by the Responder
		// - Use of EDHOC for keying OSCORE
		// - Supporting for the EDHOC+OSCORE request
		// - Method for converting from OSCORE Recipient/Sender ID to EDHOC Connection Identifier
		//
		Set<Integer> authMethods = new HashSet<Integer>();
		for (int i = 0; i <= Constants.EDHOC_AUTH_METHOD_3; i++ )
			authMethods.add(i);
		boolean useMessage4 = false;
		boolean usedForOSCORE = true;
		boolean supportCombinedRequest = false;
		AppProfile appProfile = new AppProfile(authMethods, useMessage4, usedForOSCORE, supportCombinedRequest);
		
		// Specify the processor of External Authorization Data
		KissEDP edp = new KissEDP();
		
		// Specify the database of OSCORE Security Contexts
		HashMapCtxDB db = new HashMapCtxDB();
		
		HashMap<Integer, HashMap<Integer, OneKey>> keyPairs = new HashMap<Integer, HashMap<Integer, OneKey>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> creds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> idCreds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		
		keyPairs.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, OneKey>());
		creds.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		idCreds.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		
		keyPairs.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
				 put(Integer.valueOf(Constants.CURVE_Ed25519), identityKey);
		creds.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
			     put(Integer.valueOf(Constants.CURVE_Ed25519), CBORObject.FromObject(cred));
		idCreds.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
				 put(Integer.valueOf(Constants.CURVE_Ed25519), idCred);
	    
		EdhocSession session = new EdhocSession(initiator, true, method, connectionIdentifierInitiator, keyPairs,
				                                idCreds, creds, cipherSuites, appProfile, edp, db);

		// Force a specific ephemeral key
		byte[] privateEkeyBytes = Utils.hexToBytes("892ec28e5cb6669108470539500b705e60d008d347c5817ee9f3327c8a87bb03");
		byte[] publicEkeyBytes = Utils.hexToBytes("31f82c7b5b9cbbf0f194d913cc12ef1532d328ef32632a4881a1c0701e237f04");
		OneKey ek = SharedSecretCalculation.buildCurve25519OneKey(privateEkeyBytes, publicEkeyBytes);
		session.setEphemeralKey(ek);

		// Now write EDHOC message 1
		byte[] message1 = MessageProcessor.writeMessage1(session, ead1);

		// Compare with the expected value from the test vectors
		
		// Note: the actual EDHOC message 1 starts with 0x00. The byte 0xf5 (CBOR simple value True) is prepended,
		//       in order to pass the check against what returned by the EDHOC engine, to be sent as a CoAP request payload.
		byte[] expectedMessage1 = Utils
				.hexToBytes("f50000582031f82c7b5b9cbbf0f194d913cc12ef1532d328ef32632a4881a1c0701e237f042d");

		
		// v-14 TO BE ENABLED
		Assert.assertArrayEquals(expectedMessage1, message1);
		
	}
	
	
	/**
	 * Test writing of message 2, for authentication with signatures, with x.509 certificates identified by 'x5t'
	 * 
	 * See: upcoming draft-ietf-lake-traces-01
	 */
	@Test
	public void testWriteMessage2Ciphersuite0Method0() {

		// Insert EdDSA security provider
		final Provider EdDSA = new EdDSASecurityProvider();
		Security.insertProviderAt(EdDSA, 1);
		
		boolean initiator = false;
		int method = 0;
		CBORObject[] ead2 = null;
		
		
		/* Responder information*/

		// Connection Identifier of the Responder
		byte[] connectionIdentifierResponder = new byte[] {(byte) 0x18};
		
		List<Integer> supportedCipherSuites = new ArrayList<Integer>();
		supportedCipherSuites.add(0);

		
		// The x509 certificate of the Responder
		byte[] serializedCert = Utils.hexToBytes("3081EE3081A1A003020102020462319EC4300506032B6570301D311B301906035504030C124544484F4320526F6F742045643235353139301E170D3232303331363038323433365A170D3239313233313233303030305A30223120301E06035504030C174544484F4320526573706F6E6465722045643235353139302A300506032B6570032100A1DB47B95184854AD12A0C1A354E418AACE33AA0F2C662C00B3AC55DE92F9359300506032B6570034100B723BC01EAB0928E8B2B6C98DE19CC3823D46E7D6987B032478FECFAF14537A1AF14CC8BE829C6B73044101837EB4ABC949565D86DCE51CFAE52AB82C152CB02");
		
		// CRED_R, as serialization of a CBOR byte string wrapping the serialized certificate
		byte[] credR = CBORObject.FromObject(serializedCert).EncodeToBytes();
		
		// ID_CRED_R for the identity key of the Responder, built from the x509 certificate using x5t
		CBORObject idCredR = Util.buildIdCredX5t(serializedCert);
		
		
		// TODO Extract the public key from the certificate
		/*
		ByteArrayInputStream inputStream = new ByteArrayInputStream(credR);
		try {
			System.out.println((Utils.bytesToHex(inputStream.readAllBytes())));
		} catch (IOException e) {
			fail("Error when printing the input bytes: " + e.getMessage());
			return;
		}
		
		CertificateFactory certFactory;
		X509Certificate cert;
		try {
			certFactory = CertificateFactory.getInstance("X.509");
		} catch (CertificateException e) {
			fail("Error when initializing the Certificate Factory: " + e.getMessage());
			return;
		}
		try {
			cert = (X509Certificate)certFactory.generateCertificate(inputStream);
		} catch (CertificateException e) {
			fail("Error when decoding the x509 certificate: " + e.getMessage());
			return;
		}
		if (cert == null) {
			fail("Decoded a null certificate");
			return;
		}
		PublicKey pk = cert.getPublicKey();
		
		OneKey publicKey;
		try {
			publicKey = new OneKey(pk, null);
		} catch (CoseException e) {
			fail("Error when rebuilding the COSE key from : " + e.getMessage());
			return;
		}
		byte[] publicPart = publicKey.AsCBOR().get(KeyKeys.OKP_X.AsCBOR()).GetByteString();
		identityKey = SharedSecretCalculation.buildEd25519OneKey(privateIdentityKeyBytes, publicPart);
		*/
		
		
		// The identity key of the Responder
		byte[] privateIdentityKeyBytes = Utils.hexToBytes("ef140ff900b0ab03f0c08d879cbbd4b31ea71e6e7ee7ffcb7e7955777a332799");
		byte[] publicIdentityKeyBytes = Utils.hexToBytes("a1db47b95184854ad12a0c1a354e418aace33aa0f2c662c00b3ac55de92f9359");
		OneKey identityKey = SharedSecretCalculation.buildEd25519OneKey(privateIdentityKeyBytes, publicIdentityKeyBytes);
		
		// The ephemeral key of the Responder
		byte[] privateEphemeralKeyBytes = Utils.hexToBytes("e69c23fbf81bc435942446837fe827bf206c8fa10a39db47449e5a813421e1e8");
		byte[] publicEphemeralKeyBytes = Utils.hexToBytes("dc88d2d51da5ed67fc4616356bc8ca74ef9ebe8b387e623a360ba480b9b29d1c");
		OneKey ephemeralKey = SharedSecretCalculation.buildCurve25519OneKey(privateEphemeralKeyBytes, publicEphemeralKeyBytes);

		
		/* Initiator information*/
		
		// Connection Identifier of the Initiator
		byte[] connectionIdentifierInitiator = new byte[] {(byte) 0x2d};

		// The ephemeral key of the Initiator
		byte[] peerEphemeralPublicKeyBytes = Utils.hexToBytes("31f82c7b5b9cbbf0f194d913cc12ef1532d328ef32632a4881a1c0701e237f04");
		OneKey peerEphemeralPublicKey = SharedSecretCalculation.buildCurve25519OneKey(null, peerEphemeralPublicKeyBytes);
		
		
		/* Set up the session to use */
		
		// Set the application profile
		// - Supported authentication methods
		// - Use of message_4 as expected to be sent by the Responder
		// - Use of EDHOC for keying OSCORE
		// - Supporting for the EDHOC+OSCORE request
		// - Method for converting from OSCORE Recipient/Sender ID to EDHOC Connection Identifier
		//
		Set<Integer> authMethods = new HashSet<Integer>();
		for (int i = 0; i <= Constants.EDHOC_AUTH_METHOD_3; i++ )
			authMethods.add(i);
		boolean useMessage4 = false;
		boolean usedForOSCORE = true;
		boolean supportCombinedRequest = false;
		AppProfile appProfile = new AppProfile(authMethods, useMessage4, usedForOSCORE, supportCombinedRequest);
		
		// Specify the processor of External Authorization Data
		KissEDP edp = new KissEDP();
		
		// Specify the database of OSCORE Security Contexts
		HashMapCtxDB db = new HashMapCtxDB();
		
		HashMap<Integer, HashMap<Integer, OneKey>> keyPairs = new HashMap<Integer, HashMap<Integer, OneKey>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> creds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> idCreds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		
		keyPairs.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, OneKey>());
		creds.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		idCreds.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		
		keyPairs.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
				 put(Integer.valueOf(Constants.CURVE_Ed25519), identityKey);
		creds.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
			     put(Integer.valueOf(Constants.CURVE_Ed25519), CBORObject.FromObject(credR));
		idCreds.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
				 put(Integer.valueOf(Constants.CURVE_Ed25519), idCredR);
	    
		// Create the session
		EdhocSession session = new EdhocSession(initiator, true, method, connectionIdentifierResponder, keyPairs,
												idCreds, creds, supportedCipherSuites, appProfile, edp, db);

		// Set the ephemeral keys, i.e. G_X for the initiator, as well as Y and G_Y for the Responder
		session.setEphemeralKey(ephemeralKey);
		session.setPeerEphemeralPublicKey(peerEphemeralPublicKey);

		// Set the selected cipher suite
		session.setSelectedCiphersuite(0);
		
		// Set the asymmetric key pair, CRED and ID_CRED of the Initiator to use in this session
    	session.setAuthenticationCredential();
		
		// Set the Connection Identifier of the peer
		session.setPeerConnectionId(connectionIdentifierInitiator);
		
		// Store the EDHOC Message 1
		// Note: this is the actual EDHOC message 1, so it does not include the byte 0xf5 (True) prepended on the wire
		byte[] message1 = Utils.hexToBytes("0000582031f82c7b5b9cbbf0f194d913cc12ef1532d328ef32632a4881a1c0701e237f042d");
		session.setHashMessage1(message1);
		
		
		// Now write EDHOC message 2
		byte[] message2 = MessageProcessor.writeMessage2(session, ead2);

		// Compare with the expected value from the test vectors
		
		byte[] expectedMessage2 = Utils
				.hexToBytes("5870dc88d2d51da5ed67fc4616356bc8ca74ef9ebe8b387e623a360ba480b9b29d1c9b2f43ba6ee11c4e6c005c0a3e4cb5ddcbf41c938eaa3e0f9f4f9d76fe80c07cf8b09b2861331f7ce118404a354b89fc8651915eb227cd06c074601783f9706a1bf442fb5902c5c12f6e298631898b3d4118");

		Assert.assertArrayEquals(expectedMessage2, message2);
		
	}
	
	
	/**
	 * Test writing of message 3, for authentication with signatures, with x.509 certificates identified by 'x5t'
	 * Test the derivation of OSCORE Master Secret and Master Salt
	 * Test EDHOC-KeyUpdate and a second derivation of OSCORE Master Secret and Master Salt 
	 * 
	 * See: upcoming draft-ietf-lake-traces-01
	 */
	@Test
	public void testWriteMessage3Ciphersuite0Method0() {

		// Insert EdDSA security provider
		final Provider EdDSA = new EdDSASecurityProvider();
		Security.insertProviderAt(EdDSA, 1);
		
		boolean initiator = true;
		int method = 0;
		CBORObject[] ead3 = null;
		
		
		/* Initiator information*/

		// Connection Identifier of the Initiator
		byte[] connectionIdentifierInitiator = new byte[] {(byte) 0x2d};
		
		List<Integer> supportedCipherSuites = new ArrayList<Integer>();
		supportedCipherSuites.add(0);
		
		
		// The x509 certificate of the Initiator
		byte[] serializedCert = Utils.hexToBytes("3081EE3081A1A003020102020462319EA0300506032B6570301D311B301906035504030C124544484F4320526F6F742045643235353139301E170D3232303331363038323430305A170D3239313233313233303030305A30223120301E06035504030C174544484F4320496E69746961746F722045643235353139302A300506032B6570032100ED06A8AE61A829BA5FA54525C9D07F48DD44A302F43E0F23D8CC20B73085141E300506032B6570034100521241D8B3A770996BCFC9B9EAD4E7E0A1C0DB353A3BDF2910B39275AE48B756015981850D27DB6734E37F67212267DD05EEFF27B9E7A813FA574B72A00B430B");
		
		// CRED_I, as serialization of a CBOR byte string wrapping the serialized certificate
		byte[] credI = CBORObject.FromObject(serializedCert).EncodeToBytes();
		
		// ID_CRED_I for the identity key of the Initiator, built from the x509 certificate using x5t
		CBORObject idCredI = Util.buildIdCredX5t(serializedCert);
		
		// The identity key of the Initiator
		byte[] privateIdentityKeyBytes = Utils.hexToBytes("4c5b25878f507c6b9dae68fbd4fd3ff997533db0af00b25d324ea28e6c213bc8");
		byte[] publicIdentityKeyBytes = Utils.hexToBytes("ed06a8ae61a829ba5fa54525c9d07f48dd44a302f43e0f23d8cc20b73085141e");
		OneKey identityKey = SharedSecretCalculation.buildEd25519OneKey(privateIdentityKeyBytes, publicIdentityKeyBytes);
		
		// The ephemeral key of the Initiator
		byte[] privateEphemeralKeyBytes = Utils.hexToBytes("892ec28e5cb6669108470539500b705e60d008d347c5817ee9f3327c8a87bb03");
		byte[] publicEphemeralKeyBytes = Utils.hexToBytes("31f82c7b5b9cbbf0f194d913cc12ef1532d328ef32632a4881a1c0701e237f04");
		OneKey ephemeralKey = SharedSecretCalculation.buildCurve25519OneKey(privateEphemeralKeyBytes, publicEphemeralKeyBytes);
		
		
		/* Responder information*/

		// Connection Identifier of the Responder
		byte[] connectionIdentifierResponder = new byte[] {(byte) 0x18};
		
		// The ephemeral key of the Responder
		byte[] peerEphemeralPublicKeyBytes = Utils.hexToBytes("dc88d2d51da5ed67fc4616356bc8ca74ef9ebe8b387e623a360ba480b9b29d1c");
		OneKey peerEphemeralPublicKey = SharedSecretCalculation.buildCurve25519OneKey(null, peerEphemeralPublicKeyBytes);

		
		/* Status from after receiving EDHOC Message 2 */
		byte[] th2 = Utils.hexToBytes("3ab11700841fce193c323911edb317b046dcf24b9950fd624884f7f57cd98b07");
		byte[] prk3e2m = Utils.hexToBytes("c576105d95b4d8c18e8f655f546880a854f2da106ce5a3a02d8b3ede7baabca6");
		
		
		
		/* Set up the session to use */
		
		// Set the application profile
		// - Supported authentication methods
		// - Use of message_4 as expected to be sent by the Responder
		// - Use of EDHOC for keying OSCORE
		// - Supporting for the EDHOC+OSCORE request
		// - Method for converting from OSCORE Recipient/Sender ID to EDHOC Connection Identifier
		//
		Set<Integer> authMethods = new HashSet<Integer>();
		for (int i = 0; i <= Constants.EDHOC_AUTH_METHOD_3; i++ )
			authMethods.add(i);
		boolean useMessage4 = false;
		boolean usedForOSCORE = true;
		boolean supportCombinedRequest = false;
		AppProfile appProfile = new AppProfile(authMethods, useMessage4, usedForOSCORE, supportCombinedRequest);
		
		// Specify the processor of External Authorization Data
		KissEDP edp = new KissEDP();
		
		// Specify the database of OSCORE Security Contexts
		HashMapCtxDB db = new HashMapCtxDB();
		
		HashMap<Integer, HashMap<Integer, OneKey>> keyPairs = new HashMap<Integer, HashMap<Integer, OneKey>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> creds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> idCreds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		
		keyPairs.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, OneKey>());
		creds.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		idCreds.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		
		keyPairs.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
				 put(Integer.valueOf(Constants.CURVE_Ed25519), identityKey);
		creds.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
				 put(Integer.valueOf(Constants.CURVE_Ed25519), CBORObject.FromObject(credI));
		idCreds.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
				 put(Integer.valueOf(Constants.CURVE_Ed25519), idCredI);
		
		// Create the session
		EdhocSession session = new EdhocSession(initiator, true, method, connectionIdentifierInitiator, keyPairs,
												idCreds, creds, supportedCipherSuites, appProfile, edp, db);

		// Set the ephemeral keys, i.e. X and G_X for the initiator, as well as G_Y for the Responder
		session.setEphemeralKey(ephemeralKey);
		session.setPeerEphemeralPublicKey(peerEphemeralPublicKey);

		// Set the selected cipher suite
		session.setSelectedCiphersuite(0);
		
		// Set the asymmetric key pair, CRED and ID_CRED of the Initiator to use in this session
		session.setAuthenticationCredential();
		
		// Set the Connection Identifier of the peer
		session.setPeerConnectionId(connectionIdentifierResponder);
		
		// Set TH_2 from the previous protocol step
		session.setTH2(th2);
		
		// v-14
		// Set PLAINTEXT_2 from the previous protocol step
		byte[] plaintext2 = Utils.hexToBytes("a11822822e4879f2a41b510c1f9b5840f373a7203efa7df0738c360ee080171aca67fcb175f26d785d095b55eb1484655c39e03a3f5f9bdd87ef8c5f2ec3dfe6fbba49b7b4625b126f27de301767270c");
		session.setPlaintext2(plaintext2);
		
		// Set PRK_3e2m from the previous protocol step
		session.setPRK3e2m(prk3e2m);
		
		
		// Now write EDHOC message 3
		byte[] message3 = MessageProcessor.writeMessage3(session, ead3);

		// Compare with the expected value from the test vectors
		// Note: the actual EDHOC message 3 starts with 0x58. The bytes 0x1718 (CBOR encoding for h'18') is prepended as C_R,
		//       in order to pass the check against what returned by the EDHOC engine, to be sent as a CoAP request payload. 
		byte[] expectedMessage3 = Utils.hexToBytes("411858582fc19556f67c929a9734789ecec60aaf8f50324fc31bd07801d57cec00d3bf1ea4dbafac342fe1686cbcea0cec4502959805b153819e274638cc23bbc4f4e39477f8a8fff5caaf5e32d4ca4e7c748d2b519d21b8d7577248");

		Assert.assertArrayEquals(expectedMessage3, message3);
		
		
        /* Invoke the EDHOC-Exporter to produce OSCORE input material */
		
        byte[] masterSecret = EdhocSession.getMasterSecretOSCORE(session);
        byte[] masterSalt = EdhocSession.getMasterSaltOSCORE(session);
        
		// Compare with the expected value from the test vectors
		
		byte[] expectedMasterSecret = Utils.hexToBytes("d6dd09b137359f0ad215dd021962c05c");
		byte[] expectedMasterSalt = Utils.hexToBytes("67ecd7d5bb494617");
       
       	Util.nicePrint("OSCORE Master Secret", masterSecret);
        Util.nicePrint("OSCORE Master Salt", masterSalt);
		
        Assert.assertArrayEquals(expectedMasterSecret, masterSecret);
        Assert.assertArrayEquals(expectedMasterSalt, masterSalt);
       
        
        /* Invoke EDHOC-KeyUpdate to updated the EDHOC key material */
        
        byte[] nonce = Utils.hexToBytes("d6be169602b8bceaa01158fdb820890c");
       
        try {
			session.edhocKeyUpdate(CBORObject.FromObject(nonce));
		} catch (InvalidKeyException e) {
			Assert.fail("Error while running EDHOC-KeyUpdate(): " + e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			Assert.fail("Error while running EDHOC-KeyUpdate(): " + e.getMessage());
		}
        
        System.out.println("Completed EDHOC-KeyUpdate()\n");
        
        // Following the key update, generate new OSCORE Master Secret and Master Salt
        masterSecret = EdhocSession.getMasterSecretOSCORE(session);
        masterSalt = EdhocSession.getMasterSaltOSCORE(session);
        
        // Compare with the expected value from the test vectors

       	Util.nicePrint("OSCORE Master Secret", masterSecret);
        Util.nicePrint("OSCORE Master Salt", masterSalt);
        
		expectedMasterSecret = Utils.hexToBytes("66ecb0db0a9e496f67c0b55554796e3e");
		expectedMasterSalt = Utils.hexToBytes("18f57dc34ed54917");
        
        Assert.assertArrayEquals(expectedMasterSecret, masterSecret);
        Assert.assertArrayEquals(expectedMasterSalt, masterSalt);
        
	}
	
	
	/**
	 * Test writing of message 4, for authentication with signatures, with x.509 certificates identified by 'x5t'
	 * 
	 * See: upcoming draft-ietf-lake-traces-01
	 */
	@Test
	public void testWriteMessage4Ciphersuite0Method0() {

		// Insert EdDSA security provider
		final Provider EdDSA = new EdDSASecurityProvider();
		Security.insertProviderAt(EdDSA, 1);
		
		boolean initiator = false;
		int method = 0;
		CBORObject[] ead4 = null;
		
		
		/* Responder information*/

		// Connection Identifier of the Responder
		byte[] connectionIdentifierResponder = new byte[] {(byte) 0x18};
		
		List<Integer> supportedCipherSuites = new ArrayList<Integer>();
		supportedCipherSuites.add(0);
		
		
		// The x509 certificate of the Responder
		byte[] serializedCert = Utils.hexToBytes("3081EE3081A1A003020102020462319EC4300506032B6570301D311B301906035504030C124544484F4320526F6F742045643235353139301E170D3232303331363038323433365A170D3239313233313233303030305A30223120301E06035504030C174544484F4320526573706F6E6465722045643235353139302A300506032B6570032100A1DB47B95184854AD12A0C1A354E418AACE33AA0F2C662C00B3AC55DE92F9359300506032B6570034100B723BC01EAB0928E8B2B6C98DE19CC3823D46E7D6987B032478FECFAF14537A1AF14CC8BE829C6B73044101837EB4ABC949565D86DCE51CFAE52AB82C152CB02");
		
		// CRED_R, as serialization of a CBOR byte string wrapping the serialized certificate
		byte[] credR = CBORObject.FromObject(serializedCert).EncodeToBytes();
		
		// ID_CRED_R for the identity key of the Responder, built from the x509 certificate using x5t
		CBORObject idCredR = Util.buildIdCredX5t(serializedCert);
		
		// The identity key of the Responder
		byte[] privateIdentityKeyBytes = Utils.hexToBytes("ef140ff900b0ab03f0c08d879cbbd4b31ea71e6e7ee7ffcb7e7955777a332799");
		byte[] publicIdentityKeyBytes = Utils.hexToBytes("a1db47b95184854ad12a0c1a354e418aace33aa0f2c662c00b3ac55de92f9359");
		OneKey identityKey = SharedSecretCalculation.buildEd25519OneKey(privateIdentityKeyBytes, publicIdentityKeyBytes);
		
		// The ephemeral key of the Responder
		byte[] privateEphemeralKeyBytes = Utils.hexToBytes("e69c23fbf81bc435942446837fe827bf206c8fa10a39db47449e5a813421e1e8");
		byte[] publicEphemeralKeyBytes = Utils.hexToBytes("dc88d2d51da5ed67fc4616356bc8ca74ef9ebe8b387e623a360ba480b9b29d1c");
		OneKey ephemeralKey = SharedSecretCalculation.buildCurve25519OneKey(privateEphemeralKeyBytes, publicEphemeralKeyBytes);

		
		/* Initiator information*/
		
		// Connection Identifier of the Initiator
		byte[] connectionIdentifierInitiator = new byte[] {(byte) 0x2d};

		// The ephemeral key of the Initiator
		byte[] peerEphemeralPublicKeyBytes = Utils.hexToBytes("31f82c7b5b9cbbf0f194d913cc12ef1532d328ef32632a4881a1c0701e237f04");
		OneKey peerEphemeralPublicKey = SharedSecretCalculation.buildCurve25519OneKey(null, peerEphemeralPublicKeyBytes);
		
		
		/* Set up the session to use */
		
		// Set the application profile
		// - Supported authentication methods
		// - Use of message_4 as expected to be sent by the Responder
		// - Use of EDHOC for keying OSCORE
		// - Supporting for the EDHOC+OSCORE request
		// - Method for converting from OSCORE Recipient/Sender ID to EDHOC Connection Identifier
		//
		Set<Integer> authMethods = new HashSet<Integer>();
		for (int i = 0; i <= Constants.EDHOC_AUTH_METHOD_3; i++ )
			authMethods.add(i);
		boolean useMessage4 = false;
		boolean usedForOSCORE = true;
		boolean supportCombinedRequest = false;
		AppProfile appProfile = new AppProfile(authMethods, useMessage4, usedForOSCORE, supportCombinedRequest);
		
		// Specify the processor of External Authorization Data
		KissEDP edp = new KissEDP();
		
		// Specify the database of OSCORE Security Contexts
		HashMapCtxDB db = new HashMapCtxDB();
		
		HashMap<Integer, HashMap<Integer, OneKey>> keyPairs = new HashMap<Integer, HashMap<Integer, OneKey>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> creds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> idCreds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		
		keyPairs.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, OneKey>());
		creds.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		idCreds.put(Integer.valueOf(Constants.SIGNATURE_KEY), new HashMap<Integer, CBORObject>());
		
		keyPairs.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
				 put(Integer.valueOf(Constants.CURVE_Ed25519), identityKey);
		creds.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
			     put(Integer.valueOf(Constants.CURVE_Ed25519), CBORObject.FromObject(credR));
		idCreds.get(Integer.valueOf(Constants.SIGNATURE_KEY)).
				 put(Integer.valueOf(Constants.CURVE_Ed25519), idCredR);
		
		// Create the session
		EdhocSession session = new EdhocSession(initiator, true, method, connectionIdentifierResponder, keyPairs,
												idCreds, creds, supportedCipherSuites, appProfile, edp, db);

		session.setCurrentStep(Constants.EDHOC_AFTER_M3);
		
		// Set the ephemeral keys, i.e. G_X for the initiator, as well as Y and G_Y for the Responder
		session.setEphemeralKey(ephemeralKey);
		session.setPeerEphemeralPublicKey(peerEphemeralPublicKey);

		// Set the selected cipher suite
		session.setSelectedCiphersuite(0);
		
		// Set the asymmetric key pair, CRED and ID_CRED of the Initiator to use in this session
		session.setAuthenticationCredential();
		
		// Set the Connection Identifier of the peer
		session.setPeerConnectionId(connectionIdentifierInitiator);
		
		// Store PRK_4e3m computed from the previous protocol step
		byte[] prk4e3m = Utils.hexToBytes("c576105d95b4d8c18e8f655f546880a854f2da106ce5a3a02d8b3ede7baabca6");
		session.setPRK4e3m(prk4e3m);
		
		// Store TH_4 computed from the previous protocol step
		byte[] th4 = Utils.hexToBytes("ddccc14e32330d2da93d134b0c571e2e22a28e6208135e7cda45231d850dcec3");
		session.setTH4(th4);
		
		// Now write EDHOC message 4
		byte[] message4 = MessageProcessor.writeMessage4(session, ead4);

		// Compare with the expected value from the test vectors

		byte[] expectedMessage4 = Utils.hexToBytes("48523b0282a13c8923");
		
		Assert.assertArrayEquals(expectedMessage4, message4);
		
	}
	
	
	/**
	 * Test writing of message 1, for static-static authentication with MACs, with CCS identified by 'kid'
	 * 
	 * See: upcoming draft-ietf-lake-traces-01
	 */
	@Test
	public void testWriteMessage1Ciphersuite2Method3() {
		
		boolean initiator = true;
		int method = 3;
		
		// Connection Identifier of the Initiator
		byte[] connectionIdentifierInitiator = new byte[] {(byte) 0x37};
		
		List<Integer> supportedCipherSuites = new ArrayList<Integer>();
		supportedCipherSuites.add(6);
		supportedCipherSuites.add(2);

		List<Integer> cipherSuitesPeer = new ArrayList<Integer>();
		cipherSuitesPeer.add(2);
		
		OneKey identityKey = Util.generateKeyPair(KeyKeys.EC2_P256.AsInt32());
		CBORObject[] ead1 = null;
		
		// Just for method compatibility; it is not used for EDHOC Message 1
		
		// ID_CRED_I for the identity key of the Initiator
		byte[] idCredKid = new byte[] {(byte) 0x2b};
		CBORObject idCred = Util.buildIdCredKid(idCredKid);
		
		// CRED_I for the identity key of the Initiator
		byte[] cred = Utils.hexToBytes("A2027734322D35302D33312D46462D45462D33372D33322D333908A101A5010202412B2001215820AC75E9ECE3E50BFC8ED60399889522405C47BF16DF96660A41298CB4307F7EB62258206E5DE611388A4B8A8211334AC7D37ECB52A387D257E6DB3C2A93DF21FF3AFFC8");
		
				
		// Set the application profile
		// - Supported authentication methods
		// - Use of message_4 as expected to be sent by the Responder
		// - Use of EDHOC for keying OSCORE
		// - Supporting for the EDHOC+OSCORE request
		// - Method for converting from OSCORE Recipient/Sender ID to EDHOC Connection Identifier
		//
		Set<Integer> authMethods = new HashSet<Integer>();
		for (int i = 0; i <= Constants.EDHOC_AUTH_METHOD_3; i++ )
			authMethods.add(i);
		boolean useMessage4 = false;
		boolean usedForOSCORE = true;
		boolean supportCombinedRequest = false;
		AppProfile appProfile = new AppProfile(authMethods, useMessage4, usedForOSCORE, supportCombinedRequest);
		
		// Specify the processor of External Authorization Data
		KissEDP edp = new KissEDP();
		
		// Specify the database of OSCORE Security Contexts
		HashMapCtxDB db = new HashMapCtxDB();
		
		HashMap<Integer, HashMap<Integer, OneKey>> keyPairs = new HashMap<Integer, HashMap<Integer, OneKey>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> creds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> idCreds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		
		keyPairs.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, OneKey>());
		creds.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, CBORObject>());
		idCreds.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, CBORObject>());
		
		keyPairs.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), identityKey);
		creds.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), CBORObject.FromObject(cred));
		idCreds.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), idCred);
		
		EdhocSession session = new EdhocSession(initiator, true, method, connectionIdentifierInitiator, keyPairs,
				                                idCreds, creds, supportedCipherSuites, appProfile, edp, db);

		// Force the early knowledge of cipher suites supported by the other peer
		session.setPeerSupportedCipherSuites(cipherSuitesPeer);
		
		// Force a specific ephemeral key
		byte[] privateEkeyBytes = Utils.hexToBytes("368ec1f69aeb659ba37d5a8d45b21bdc0299dceaa8ef235f3ca42ce3530f9525");
		byte[] publicEkeyBytesX = Utils.hexToBytes("8af6f430ebe18d34184017a9a11bf511c8dff8f834730b96c1b7c8dbca2fc3b6");
		byte[] publicEkeyBytesY = Utils.hexToBytes("51e8af6c6edb781601ad1d9c5fa8bf7aa15716c7c06a5d038503c614ff80c9b3");
		OneKey ek = SharedSecretCalculation.buildEcdsa256OneKey(privateEkeyBytes, publicEkeyBytesX, publicEkeyBytesY);
		session.setEphemeralKey(ek);

		// Now write EDHOC message 1
		byte[] message1 = MessageProcessor.writeMessage1(session, ead1);

		// Compare with the expected value from the test vectors

		// Note: the actual EDHOC message 1 starts with 0x03. The byte 0xf5 (CBOR simple value True) is prepended,
		//       in order to pass the check against what returned by the EDHOC engine, to be sent as a CoAP request payload. 
		byte[] expectedMessage1 = Utils
				.hexToBytes("f50382060258208af6f430ebe18d34184017a9a11bf511c8dff8f834730b96c1b7c8dbca2fc3b637");
		
		Assert.assertArrayEquals(expectedMessage1, message1);
	}
	
	
	/**
	 * Test writing of message 2, for static-static authentication with MACs, with CCS identified by 'kid'
	 * 
	 * See: upcoming draft-ietf-lake-traces-01
	 */
	@Test
	public void testWriteMessage2Ciphersuite2Method3() {

		boolean initiator = false;
		int method = 3;
		CBORObject[] ead2 = null;
		
		/* Responder information*/

		// Connection Identifier of the Responder
		byte[] connectionIdentifierResponder = new byte[] {(byte) 0x27};
		
		List<Integer> supportedCipherSuites = new ArrayList<Integer>();
		supportedCipherSuites.add(2);
		
		// The identity key of the Responder
		byte[] privateIdentityKeyBytes = Utils.hexToBytes("72cc4761dbd4c78f758931aa589d348d1ef874a7e303ede2f140dcf3e6aa4aac");
		byte[] publicIdentityKeyBytesX = Utils.hexToBytes("bbc34960526ea4d32e940cad2a234148ddc21791a12afbcbac93622046dd44f0");
		byte[] publicIdentityKeyBytesY = Utils.hexToBytes("4519e257236b2a0ce2023f0931f1f386ca7afda64fcde0108c224c51eabf6072");
		
		OneKey identityKey = SharedSecretCalculation.buildEcdsa256OneKey(privateIdentityKeyBytes, publicIdentityKeyBytesX, publicIdentityKeyBytesY);
		
		// ID_CRED_R for the identity key of the Responder
		byte[] idCredKid = {(byte) 0x32};
		CBORObject idCredR = Util.buildIdCredKid(idCredKid);
		
		// CRED_R for the identity key of the Responder
		byte[] credR = Utils.hexToBytes("A2026B6578616D706C652E65647508A101A501020241322001215820BBC34960526EA4D32E940CAD2A234148DDC21791A12AFBCBAC93622046DD44F02258204519E257236B2A0CE2023F0931F1F386CA7AFDA64FCDE0108C224C51EABF6072");
		
				
		// The ephemeral key of the Responder
		byte[] privateEphemeralKeyBytes = Utils.hexToBytes("e2f4126777205e853b437d6eaca1e1f753cdcc3e2c69fa884b0a1a640977e418");
		byte[] publicEphemeralKeyBytesX = Utils.hexToBytes("419701d7f00a26c2dc587a36dd752549f33763c893422c8ea0f955a13a4ff5d5");
		byte[] publicEphemeralKeyBytesY = Utils.hexToBytes("5e4f0dd8a3da0baa16b9d3ad56a0c1860a940af85914915e25019b402417e99d");
		OneKey ephemeralKey = SharedSecretCalculation.buildEcdsa256OneKey(privateEphemeralKeyBytes,
																		  publicEphemeralKeyBytesX,
																		  publicEphemeralKeyBytesY);

		/* Initiator information*/
		
		// Connection Identifier of the Initiator
		byte[] connectionIdentifierInitiator = new byte[] {(byte) 0x37};

		// The ephemeral key of the Initiator
		byte[] publicPeerEphemeralKeyBytesX = Utils.hexToBytes("8af6f430ebe18d34184017a9a11bf511c8dff8f834730b96c1b7c8dbca2fc3b6");
		byte[] publicPeerEphemeralKeyBytesY = Utils.hexToBytes("51e8af6c6edb781601ad1d9c5fa8bf7aa15716c7c06a5d038503c614ff80c9b3");
		OneKey peerEphemeralPublicKey = SharedSecretCalculation.buildEcdsa256OneKey(null,
																					publicPeerEphemeralKeyBytesX,
																					publicPeerEphemeralKeyBytesY);
				
		/* Set up the session to use */
		
		// Set the application profile
		// - Supported authentication methods
		// - Use of message_4 as expected to be sent by the Responder
		// - Use of EDHOC for keying OSCORE
		// - Supporting for the EDHOC+OSCORE request
		// - Method for converting from OSCORE Recipient/Sender ID to EDHOC Connection Identifier
		//
		Set<Integer> authMethods = new HashSet<Integer>();
		for (int i = 0; i <= Constants.EDHOC_AUTH_METHOD_3; i++ )
			authMethods.add(i);
		boolean useMessage4 = false;
		boolean usedForOSCORE = true;
		boolean supportCombinedRequest = false;
		AppProfile appProfile = new AppProfile(authMethods, useMessage4, usedForOSCORE, supportCombinedRequest);
		
		// Specify the processor of External Authorization Data
		KissEDP edp = new KissEDP();
		
		// Specify the database of OSCORE Security Contexts
		HashMapCtxDB db = new HashMapCtxDB();
		
		HashMap<Integer, HashMap<Integer, OneKey>> keyPairs = new HashMap<Integer, HashMap<Integer, OneKey>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> creds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> idCreds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		
		keyPairs.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, OneKey>());
		creds.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, CBORObject>());
		idCreds.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, CBORObject>());
		
		keyPairs.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), identityKey);
		creds.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), CBORObject.FromObject(credR));
		idCreds.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), idCredR);
		
		// Create the session
		EdhocSession session = new EdhocSession(initiator, true, method, connectionIdentifierResponder, keyPairs,
												idCreds, creds, supportedCipherSuites, appProfile, edp, db);

		// Set the ephemeral keys, i.e. G_X for the initiator, as well as Y and G_Y for the Responder
		session.setEphemeralKey(ephemeralKey);
		session.setPeerEphemeralPublicKey(peerEphemeralPublicKey);

		// Set the selected cipher suite
		session.setSelectedCiphersuite(2);
		
		// Set the asymmetric key pair, CRED and ID_CRED of the Initiator to use in this session
		session.setAuthenticationCredential();
		
		// Set the Connection Identifier of the peer
		session.setPeerConnectionId(connectionIdentifierInitiator);
		
		// Store the EDHOC Message 1
		// Note: this is the actual EDHOC message 1, so it does not include the byte 0xf5 (True) prepended on the wire
		byte[] message1 = Utils.hexToBytes("0382060258208af6f430ebe18d34184017a9a11bf511c8dff8f834730b96c1b7c8dbca2fc3b637");
		session.setHashMessage1(message1);
		
		
		// Now write EDHOC message 2
		byte[] message2 = MessageProcessor.writeMessage2(session, ead2);

		// Compare with the expected value from the test vectors
		
		byte[] expectedMessage2 = Utils
				.hexToBytes("582a419701d7f00a26c2dc587a36dd752549f33763c893422c8ea0f955a13a4ff5d58b8fec6b1f0580c5043927");
		
		Assert.assertArrayEquals(expectedMessage2, message2);
		
	}
	
	
	/**
	 * Test writing of message 3, for static-static authentication with MACs, with CCS identified by 'kid'
	 * Test the derivation of OSCORE Master Secret and Master Salt
	 * Test EDHOC-KeyUpdate and a second derivation of OSCORE Master Secret and Master Salt 
	 * 
	 * See: upcoming draft-ietf-lake-traces-01
	 */
	@Test
	public void testWriteMessage3Ciphersuite2Method3() {

		boolean initiator = true;
		int method = 3;
		CBORObject[] ead3 = null;
		
		/* Initiator information*/

		// Connection Identifier of the Initiator
		byte[] connectionIdentifierInitiator = new byte[] {(byte) 0x37};
		
		List<Integer> supportedCipherSuites = new ArrayList<Integer>();
		supportedCipherSuites.add(6);
		supportedCipherSuites.add(2);
		
		// The identity key of the Initiator
		byte[] privateIdentityKeyBytes = Utils.hexToBytes("fb13adeb6518cee5f88417660841142e830a81fe334380a953406a1305e8706b");
		byte[] publicIdentityKeyBytesX = Utils.hexToBytes("ac75e9ece3e50bfc8ed60399889522405c47bf16df96660a41298cb4307f7eb6");
		byte[] publicIdentityKeyBytesY = Utils.hexToBytes("6e5de611388a4b8a8211334ac7d37ecb52a387d257e6db3c2a93df21ff3affc8");
		OneKey identityKey = SharedSecretCalculation.buildEcdsa256OneKey(privateIdentityKeyBytes, publicIdentityKeyBytesX, publicIdentityKeyBytesY);
		
		// ID_CRED_I for the identity key of the Initiator
		byte[] idCredKid = {(byte) 0x2b};
		CBORObject idCredI = Util.buildIdCredKid(idCredKid);
		
		// CRED_I for the identity key of the Initiator
		byte[] credI = Utils.hexToBytes("A2027734322D35302D33312D46462D45462D33372D33322D333908A101A5010202412B2001215820AC75E9ECE3E50BFC8ED60399889522405C47BF16DF96660A41298CB4307F7EB62258206E5DE611388A4B8A8211334AC7D37ECB52A387D257E6DB3C2A93DF21FF3AFFC8");
		
		// The ephemeral key of the Initiator
		byte[] privateEphemeralKeyBytes = Utils.hexToBytes("368ec1f69aeb659ba37d5a8d45b21bdc0299dceaa8ef235f3ca42ce3530f9525");
		byte[] publicEphemeralKeyBytesX = Utils.hexToBytes("8af6f430ebe18d34184017a9a11bf511c8dff8f834730b96c1b7c8dbca2fc3b6");
		byte[] publicEphemeralKeyBytesY = Utils.hexToBytes("51e8af6c6edb781601ad1d9c5fa8bf7aa15716c7c06a5d038503c614ff80c9b3");
		OneKey ephemeralKey = SharedSecretCalculation.buildEcdsa256OneKey(privateEphemeralKeyBytes,
																		  publicEphemeralKeyBytesX,
																		  publicEphemeralKeyBytesY);
		
		
		/* Responder information*/

		// Connection Identifier of the Responder
		byte[] connectionIdentifierResponder = new byte[] {(byte) 0x27};
		
		// The ephemeral key of the Responder
		byte[] peerEphemeralPublicKeyBytesX = Utils.hexToBytes("419701d7f00a26c2dc587a36dd752549f33763c893422c8ea0f955a13a4ff5d5");
		byte[] peerEphemeralPublicKeyBytesY = Utils.hexToBytes("5e4f0dd8a3da0baa16b9d3ad56a0c1860a940af85914915e25019b402417e99d");
		OneKey peerEphemeralPublicKey = SharedSecretCalculation.buildEcdsa256OneKey(null,
																					peerEphemeralPublicKeyBytesX,
																					peerEphemeralPublicKeyBytesY);

		
		/* Status from after receiving EDHOC Message 2 */
		
		byte[] th2 = Utils.hexToBytes("9d2af3a3d3fc06aea8110f14ba12ad0b4fb7e5cdf59c7df1cf2dfe9c2024439c");
		
		byte[] prk3e2m = Utils.hexToBytes("7e230e62b909ca7492367aaa8a229f6306c5ac67482184b33362d28d177a56e9");
		
		
		/* Set up the session to use */
		
		// Set the application profile
		// - Supported authentication methods
		// - Use of message_4 as expected to be sent by the Responder
		// - Use of EDHOC for keying OSCORE
		// - Supporting for the EDHOC+OSCORE request
		// - Method for converting from OSCORE Recipient/Sender ID to EDHOC Connection Identifier
		//
		Set<Integer> authMethods = new HashSet<Integer>();
		for (int i = 0; i <= Constants.EDHOC_AUTH_METHOD_3; i++ )
			authMethods.add(i);
		boolean useMessage4 = false;
		boolean usedForOSCORE = true;
		boolean supportCombinedRequest = false;
		AppProfile appProfile = new AppProfile(authMethods, useMessage4, usedForOSCORE, supportCombinedRequest);
		
		// Specify the processor of External Authorization Data
		KissEDP edp = new KissEDP();
		
		// Specify the database of OSCORE Security Contexts
		HashMapCtxDB db = new HashMapCtxDB();
		
		HashMap<Integer, HashMap<Integer, OneKey>> keyPairs = new HashMap<Integer, HashMap<Integer, OneKey>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> creds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> idCreds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		
		keyPairs.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, OneKey>());
		creds.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, CBORObject>());
		idCreds.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, CBORObject>());
		
		keyPairs.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), identityKey);
		creds.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), CBORObject.FromObject(credI));
		idCreds.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), idCredI);
		
		// Create the session
		EdhocSession session = new EdhocSession(initiator, true, method, connectionIdentifierInitiator, keyPairs,
												idCreds, creds, supportedCipherSuites, appProfile, edp, db);

		// Set the ephemeral keys, i.e. X and G_X for the initiator, as well as G_Y for the Responder
		session.setEphemeralKey(ephemeralKey);
		session.setPeerEphemeralPublicKey(peerEphemeralPublicKey);

		// Set the selected cipher suite
		session.setSelectedCiphersuite(2);
		
		// Set the asymmetric key pair, CRED and ID_CRED of the Initiator to use in this session
		session.setAuthenticationCredential();
		
		// Set the Connection Identifier of the peer
		session.setPeerConnectionId(connectionIdentifierResponder);
		
		// Set TH_2 from the previous protocol step
		session.setTH2(th2);
		
		// v-14
		// Set PLAINTEXT_2 from the previous protocol step
		byte[] plaintext2 = Utils.hexToBytes("3248ad01bc30c6911176");
		session.setPlaintext2(plaintext2);
		
		// Set PRK_3e2m from the previous protocol step
		session.setPRK3e2m(prk3e2m);
		
		
		// Now write EDHOC message 3
		byte[] message3 = MessageProcessor.writeMessage3(session, ead3);

		// Compare with the expected value from the test vectors
		// Note: the actual EDHOC message 3 starts with 0x52. The byte 0x27 (CBOR encoding for -8) is prepended as C_R,
		//       in order to pass the check against what returned by the EDHOC engine, to be sent as a CoAP request payload.
		
		byte[] expectedMessage3 = Utils.hexToBytes("2752c25c8420036764462f57357986616c8d21b0");

		Assert.assertArrayEquals(expectedMessage3, message3);
		
		
        /* Invoke EDHOC-Exporter to produce OSCORE input material */
		
        byte[] masterSecret = EdhocSession.getMasterSecretOSCORE(session);
        byte[] masterSalt = EdhocSession.getMasterSaltOSCORE(session);
        
		// Compare with the expected value from the test vectors
        
		byte[] expectedMasterSecret = Utils.hexToBytes("f3fb2149fd08c18a40938ae5703bfb38");
		byte[] expectedMasterSalt = Utils.hexToBytes("25549b7255cafa1d");

       	Util.nicePrint("OSCORE Master Secret", masterSecret);
        Util.nicePrint("OSCORE Master Salt", masterSalt);
		
        Assert.assertArrayEquals(expectedMasterSecret, masterSecret);
        Assert.assertArrayEquals(expectedMasterSalt, masterSalt);
		
        
        /* Invoke EDHOC-KeyUpdate to updated the EDHOC key material */
        
        byte[] nonce = Utils.hexToBytes("a01158fdb820890cd6be169602b8bcea");
       
        try {
			session.edhocKeyUpdate(CBORObject.FromObject(nonce));
		} catch (InvalidKeyException e) {
			Assert.fail("Error while running EDHOC-KeyUpdate(): " + e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			Assert.fail("Error while running EDHOC-KeyUpdate(): " + e.getMessage());
		}
        
        System.out.println("Completed EDHOC-KeyUpdate()\n");
        
        // Following the key update, generate new OSCORE Master Secret and Master Salt
        masterSecret = EdhocSession.getMasterSecretOSCORE(session);
        masterSalt = EdhocSession.getMasterSaltOSCORE(session);
        
        // Compare with the expected value from the test vectors
        
		expectedMasterSecret = Utils.hexToBytes("5d96c2fb5c1ad12e324a59f4a4cde25b");
		expectedMasterSalt = Utils.hexToBytes("3aef12c42700cb60");

       	Util.nicePrint("OSCORE Master Secret", masterSecret);
        Util.nicePrint("OSCORE Master Salt", masterSalt);
		
        Assert.assertArrayEquals(expectedMasterSecret, masterSecret);
        Assert.assertArrayEquals(expectedMasterSalt, masterSalt);
        
	}
	
	
	/**
	 * Test writing of message 4, for static-static authentication with MACs, with CCS identified by 'kid'
	 * 
	 * See: upcoming draft-ietf-lake-traces-01
	 */
	@Test
	public void testWriteMessage4Ciphersuite2Method3() {

		boolean initiator = false;
		int method = 3;
		CBORObject[] ead4 = null;
		
		/* Responder information*/

		// Connection Identifier of the Responder
		byte[] connectionIdentifierResponder = new byte[] {(byte) 0x27};
		
		List<Integer> supportedCipherSuites = new ArrayList<Integer>();
		supportedCipherSuites.add(2);
		
		// The identity key of the Responder
		byte[] privateIdentityKeyBytes = Utils.hexToBytes("72cc4761dbd4c78f758931aa589d348d1ef874a7e303ede2f140dcf3e6aa4aac");
		byte[] publicIdentityKeyBytesX = Utils.hexToBytes("bbc34960526ea4d32e940cad2a234148ddc21791a12afbcbac93622046dd44f0");
		byte[] publicIdentityKeyBytesY = Utils.hexToBytes("4519e257236b2a0ce2023f0931f1f386ca7afda64fcde0108c224c51eabf6072");
		
		OneKey identityKey = SharedSecretCalculation.buildEcdsa256OneKey(privateIdentityKeyBytes, publicIdentityKeyBytesX, publicIdentityKeyBytesY);
		
		// ID_CRED_R for the identity key of the Responder
		byte[] idCredKid = new byte[] {(byte) 0x32};
		CBORObject idCredR = Util.buildIdCredKid(idCredKid);
		
		// CRED_R for the identity key of the Responder
		byte[] credR = Utils.hexToBytes("A2026B6578616D706C652E65647508A101A501020241322001215820BBC34960526EA4D32E940CAD2A234148DDC21791A12AFBCBAC93622046DD44F02258204519E257236B2A0CE2023F0931F1F386CA7AFDA64FCDE0108C224C51EABF6072");
		
		
		// The ephemeral key of the Responder
		byte[] privateEphemeralKeyBytes = Utils.hexToBytes("e2f4126777205e853b437d6eaca1e1f753cdcc3e2c69fa884b0a1a640977e418");
		byte[] publicEphemeralKeyBytesX = Utils.hexToBytes("419701d7f00a26c2dc587a36dd752549f33763c893422c8ea0f955a13a4ff5d5");
		byte[] publicEphemeralKeyBytesY = Utils.hexToBytes("5e4f0dd8a3da0baa16b9d3ad56a0c1860a940af85914915e25019b402417e99d");
		OneKey ephemeralKey = SharedSecretCalculation.buildEcdsa256OneKey(privateEphemeralKeyBytes,
																		  publicEphemeralKeyBytesX,
																		  publicEphemeralKeyBytesY);

		
		/* Initiator information*/
		
		// Connection Identifier of the Initiator
		byte[] connectionIdentifierInitiator = new byte[] {(byte) 0x37};

		// The ephemeral key of the Initiator
		byte[] publicPeerEphemeralKeyBytesX = Utils.hexToBytes("8af6f430ebe18d34184017a9a11bf511c8dff8f834730b96c1b7c8dbca2fc3b6");
		byte[] publicPeerEphemeralKeyBytesY = Utils.hexToBytes("51e8af6c6edb781601ad1d9c5fa8bf7aa15716c7c06a5d038503c614ff80c9b3");
		OneKey peerEphemeralPublicKey = SharedSecretCalculation.buildEcdsa256OneKey(null,
																					publicPeerEphemeralKeyBytesX,
																					publicPeerEphemeralKeyBytesY);
		
		
		/* Set up the session to use */
		
		// Set the application profile
		// - Supported authentication methods
		// - Use of message_4 as expected to be sent by the Responder
		// - Use of EDHOC for keying OSCORE
		// - Supporting for the EDHOC+OSCORE request
		// - Method for converting from OSCORE Recipient/Sender ID to EDHOC Connection Identifier
		//
		Set<Integer> authMethods = new HashSet<Integer>();
		for (int i = 0; i <= Constants.EDHOC_AUTH_METHOD_3; i++ )
			authMethods.add(i);
		boolean useMessage4 = false;
		boolean usedForOSCORE = true;
		boolean supportCombinedRequest = false;
		AppProfile appProfile = new AppProfile(authMethods, useMessage4, usedForOSCORE, supportCombinedRequest);
		
		// Specify the processor of External Authorization Data
		KissEDP edp = new KissEDP();
		
		// Specify the database of OSCORE Security Contexts
		HashMapCtxDB db = new HashMapCtxDB();
		
		HashMap<Integer, HashMap<Integer, OneKey>> keyPairs = new HashMap<Integer, HashMap<Integer, OneKey>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> creds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		HashMap<Integer, HashMap<Integer, CBORObject>> idCreds = new HashMap<Integer, HashMap<Integer, CBORObject>>();
		
		keyPairs.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, OneKey>());
		creds.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, CBORObject>());
		idCreds.put(Integer.valueOf(Constants.ECDH_KEY), new HashMap<Integer, CBORObject>());
		
		keyPairs.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), identityKey);
		creds.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), CBORObject.FromObject(credR));
		idCreds.get(Integer.valueOf(Constants.ECDH_KEY)).
				 put(Integer.valueOf(Constants.CURVE_P256), idCredR);
		
		// Create the session
		EdhocSession session = new EdhocSession(initiator, true, method, connectionIdentifierResponder, keyPairs,
												idCreds, creds, supportedCipherSuites, appProfile, edp, db);

		session.setCurrentStep(Constants.EDHOC_AFTER_M3);
		
		// Set the ephemeral keys, i.e. G_X for the initiator, as well as Y and G_Y for the Responder
		session.setEphemeralKey(ephemeralKey);
		session.setPeerEphemeralPublicKey(peerEphemeralPublicKey);

		// Set the selected cipher suite
		session.setSelectedCiphersuite(2);
		
		// Set the Connection Identifier of the peer
		session.setPeerConnectionId(connectionIdentifierInitiator);
		
		
		// Store PRK_4e3m computed from the previous protocol step
		byte[] prk4e3m = Utils.hexToBytes("9eda8cd755ae3b80b47e8ddbb8d7c5fe2b62b462e4bcba2c6c8ea36ee5fb604d");
		session.setPRK4e3m(prk4e3m);
		
		// Store TH_4 computed from the previous protocol step
		byte[] th4 = Utils.hexToBytes("a4097a6b9e39f7d3dc4f8af2c4a8645b373d7af586f415df626e16b6ac2755d3");
		session.setTH4(th4);
		
		// Now write EDHOC message 4
		byte[] message4 = MessageProcessor.writeMessage4(session, ead4);

		// Compare with the expected value from the test vectors

		byte[] expectedMessage4 = Utils.hexToBytes("48ddf977df1cac7fc3");
		
		Assert.assertArrayEquals(expectedMessage4, message4);
		
	}
		
}
