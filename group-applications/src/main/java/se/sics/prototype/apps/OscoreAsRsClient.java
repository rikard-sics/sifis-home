/*******************************************************************************
 * Copyright (c) 2023, RISE AB
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR 
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT 
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY 
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
package se.sics.prototype.apps;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.cose.KeyKeys;
import org.eclipse.californium.cose.OneKey;
import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.oscore.HashMapCtxDB;
import org.eclipse.californium.oscore.OSCoreCoapStackFactory;
import org.eclipse.californium.oscore.OSCoreCtx;
import org.eclipse.californium.oscore.OSCoreCtxDB;
import org.eclipse.californium.oscore.group.GroupCtx;
import org.eclipse.californium.oscore.group.MultiKey;
import org.junit.Assert;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;

import se.sics.ace.Constants;
import se.sics.ace.Util;
import se.sics.ace.client.GetToken;
import se.sics.ace.coap.client.OSCOREProfileRequests;
import se.sics.ace.coap.client.OSCOREProfileRequestsGroupOSCORE;
import se.sics.ace.oscore.GroupOSCOREInputMaterialObjectParameters;
import se.sics.prototype.support.KeyStorage;
import se.sics.prototype.support.Tools;

/**
 * A stand-alone application for Client->AS followed by Client->GM communication
 * using the OSCORE profile.
 * 
 * First the client will request a Token from the AS, it will then post it to
 * the GM and then proceed with the Group Joining procedure.
 * 
 * After the joining communication in the group commences using the Group OSCORE
 * client and server applications.
 * 
 * @author Rikard Höglund
 *
 */
public class OscoreAsRsClient {

	/*
	 * Information: Clients: Server1, Server2, Server3, Server4, Server5,
	 * Server6, Client1, Client2 Groups: GroupA (aaaaaa570000), GroupB
	 * (bbbbbb570000)
	 */

	// Sets the default GM port to use
	private static int GM_PORT = CoAP.DEFAULT_COAP_PORT + 100;
	// Sets the default GM hostname/IP to use
	private static String GM_HOST = "localhost";

	// Sets the default AS port to use
	private static int AS_PORT = CoAP.DEFAULT_COAP_PORT;
	// Sets the default AS hostname/IP to use
	private static String AS_HOST = "localhost";

	// Multicast IP for Group A
	static final InetAddress groupA_multicastIP = new InetSocketAddress("224.0.1.191", 0).getAddress();

	// Multicast IP for Group B
	static final InetAddress groupB_multicastIP = new InetSocketAddress("224.0.1.192", 0).getAddress();

	// Default URI for DHT WebSocket connection. Can be changed using command
	// line arguments.
	private static String dhtWebsocketUri = "ws://localhost:3000/ws";

	static HashMapCtxDB db = new HashMapCtxDB();

	private final static int MAX_UNFRAGMENTED_SIZE = 4096;

	// Each set of the list refers to a different size of Recipient IDs.
	// The element with index 0 includes as elements Recipient IDs with size 1
	// byte.
	private static List<Set<Integer>> usedRecipientIds = new ArrayList<Set<Integer>>();

	private static final String rootGroupMembershipResource = "ace-group";

	// Uncomment to set EDDSA with curve Ed25519 for countersignatures
	private static int signKeyCurve = KeyKeys.OKP_Ed25519.AsInt32();
	// Uncomment to set curve X25519 for pairwise key derivation
	private static int ecdhKeyCurve = KeyKeys.OKP_X25519.AsInt32();

	/**
	 * Main method for Token request followed by Group joining
	 * 
	 * @param args input command line arguments
	 * 
	 * @throws URISyntaxException on failure to parse command line arguments
	 */
	public static void main(String[] args) throws URISyntaxException {

		System.out.println("Starting Group peer (OSCORE Server/Client) that joins groups.: OscoreAsRsClient...");

		// install needed cryptography providers
		try {
			org.eclipse.californium.oscore.InstallCryptoProviders.installProvider();
		} catch (Exception e) {
			System.err.println("Failed to install cryptography providers.");
			e.printStackTrace();
		}

		// Usage of DHT for controlling the client
		boolean useDht = false;

		// Set member name, AS and GM to use from command line arguments
		String memberName = "Client1";
		int delay = 0;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-name")) {
				memberName = args[i + 1];
				i++;
			} else if (args[i].equals("-gm")) {
				GM_HOST = new URI(args[i + 1]).getHost();
				GM_PORT = new URI(args[i + 1]).getPort();
				i++;
			} else if (args[i].equals("-as")) {
				AS_HOST = new URI(args[i + 1]).getHost();
				AS_PORT = new URI(args[i + 1]).getPort();
				i++;
			} else if (args[i].toLowerCase().endsWith("-dht") || args[i].toLowerCase().endsWith("-usedht")) {
				useDht = true;

				// Check if a WebSocket URI for the DHT is also indicated
				URI parsed = null;
				try {
					parsed = new URI(args[i + 1]);
				} catch (URISyntaxException | ArrayIndexOutOfBoundsException e) {
					// No URI indicated
				}
				if (parsed != null) {
					dhtWebsocketUri = parsed.toString();
					i++;
				}

			} else if (args[i].toLowerCase().endsWith("-delay")) {
				delay = Integer.parseInt(args[i + 1]);
				i++;
			} else if (args[i].toLowerCase().endsWith("-help")) {
				printHelp();
				System.exit(0);
			}
		}

		// Delay before starting
		try {
			Thread.sleep(delay * 1000);
		} catch (InterruptedException e) {
			System.err.println("Failed to sleep before starting");
			e.printStackTrace();
		}

		// Explicitly enable the OSCORE Stack
		if (CoapEndpoint.isDefaultCoapStackFactorySet() == false) {
			OSCoreCoapStackFactory.useAsDefault(db);
		}

		// Wait for Authorization Server to become available
		boolean asAvailable = false;
		do {
			String asUri = "coap://" + AS_HOST + ":" + AS_PORT + "/token";
			System.out.println("Attempting to reach AS at: " + asUri + " ...");

			try {
				Thread.sleep(5000);
				CoapClient checker = new CoapClient(asUri);
				asAvailable = checker.ping();
			} catch (InterruptedException e) {
				System.err.println("Failed to sleep when waiting for AS.");
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				System.err.println("AS hostname not available. Retrying...");
			}
		} while (!asAvailable);
		System.out.println("AS is available. Proceeding to request Token from AS.");

		// Build empty sets of assigned Sender IDs; one set for each possible
		for (int i = 0; i < 4; i++) {
			// Sender ID size in bytes.
			// The set with index 0 refers to Sender IDs with size 1 byte
			usedRecipientIds.add(new HashSet<Integer>());
		}

		// Set group to join based on the member name
		String group = "";
		InetAddress multicastIP = null;
		switch (memberName) {
		case "Client1":
		case "Server1":
		case "Server2":
		case "Server3":
			group = "aaaaaa570000";
			multicastIP = groupA_multicastIP;
			break;
		case "Client2":
		case "Server4":
		case "Server5":
		case "Server6":
			group = "bbbbbb570000";
			multicastIP = groupB_multicastIP;
			break;
		default:
			System.err.println("Error: Invalid member name specified!");
			System.exit(1);
			break;
		}

		// Set public/private key to use in the group
		String publicPrivateKey;
		publicPrivateKey = CBORObject.DecodeFromBytes(KeyStorage.memberCcs.get(memberName)).toString();

		// Set key (OSCORE master secret) to use towards AS
		byte[] keyToAS;
		keyToAS = KeyStorage.memberAsKeys.get(memberName);

		System.out.println("Configured with parameters:");
		System.out.println("\tAS: " + AS_HOST + ":" + AS_PORT);
		System.out.println("\tGM: " + GM_HOST + ":" + GM_PORT);
		System.out.println("\tMember name: " + memberName);
		System.out.println("\tGroup: " + group);
		System.out.println("\tGroup Key: " + publicPrivateKey);
		System.out.println("\tKey to AS: " + StringUtil.byteArray2Hex(keyToAS));

		printPause(memberName, "Will now request Token from AS");

		// Request Token from AS
		Response responseFromAS = null;
		try {
			responseFromAS = requestToken(memberName, group, keyToAS);
		} catch (Exception e) {
			System.err.print("Token request procedure failed: ");
			e.printStackTrace();
		}

		printPause(memberName, "Will now post Token to Group Manager and perform group joining");

		// Wait for Group Manager to become available
		boolean gmAvailable = false;
		do {
			String gmUri = "coap://" + GM_HOST + ":" + GM_PORT + "/authz-info";
			System.out.println("Attempting to reach GM at: " + gmUri + " ...");

			try {
				Thread.sleep(5000);
				CoapClient checker = new CoapClient(gmUri);
				gmAvailable = checker.ping();
			} catch (InterruptedException e) {
				System.err.println("Failed to sleep when waiting for GM.");
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				System.err.println("GM hostname not available. Retrying...");
			}
		} while (!gmAvailable);
		System.out.println("GM is available. Proceeding to post Token to GM.");

		// ///////////////
		// // EDDSA (Ed25519)
		// CBORObject rpkData = null;
		// CBORObject x = null;
		// CBORObject d = null;
		// OneKey C1keyPair = null;
		// String c1X_EDDSA =
		// "069E912B83963ACC5941B63546867DEC106E5B9051F2EE14F3BC5CC961ACD43A";
		// String c1D_EDDSA = privKeyClient;
		// if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
		// rpkData = CBORObject.NewMap();
		// rpkData.Add(KeyKeys.KeyType.AsCBOR(), KeyKeys.KeyType_OKP);
		// rpkData.Add(KeyKeys.Algorithm.AsCBOR(), AlgorithmID.EDDSA.AsCBOR());
		// rpkData.Add(KeyKeys.OKP_Curve.AsCBOR(), KeyKeys.OKP_Ed25519);
		// x = CBORObject.FromObject(StringUtil.hex2ByteArray(c1X_EDDSA));
		// d = CBORObject.FromObject(StringUtil.hex2ByteArray(c1D_EDDSA));
		// rpkData.Add(KeyKeys.OKP_X.AsCBOR(), x);
		// rpkData.Add(KeyKeys.OKP_D.AsCBOR(), d);
		// C1keyPair = new OneKey(rpkData);
		// }

		// Get OneKey representation of this member's public/private key
		OneKey cKeyPair = new MultiKey(KeyStorage.memberCcs.get(memberName),
				KeyStorage.memberPrivateKeys.get(memberName)).getCoseKey();
		// Get byte array of this member's CCS
		byte[] memberCcs = KeyStorage.memberCcs.get(memberName);

		// Post Token to GM and perform Group joining
		GroupCtx derivedCtx = null;
		try {
			derivedCtx = testSuccessGroupOSCOREMultipleRoles(memberName, group, GM_HOST, GM_PORT, db, cKeyPair,
					responseFromAS, memberCcs);
		} catch (Exception e1) {
			System.err.println("Failed Token post and Joining");
			e1.printStackTrace();
		}

		// Now start the Group OSCORE Client or Server application with the
		// derived context
		try {
			if (memberName.equals("Client1") || memberName.equals("Client2")) {
				GroupOscoreClient.start(derivedCtx, multicastIP, memberName, useDht, dhtWebsocketUri);
			} else {
				GroupOscoreServer.start(derivedCtx, multicastIP);
			}
		} catch (Exception e) {
			System.err.print("Starting Group OSCORE applications: ");
			e.printStackTrace();
		}
	}

	/**
	 * Request a Token from the AS.
	 * 
	 * @param memberName name of client/server peer
	 * @param group to request token for
	 * @param keyToAS key shared with the AS
	 * @return the CoAP response from the AS
	 * 
	 * @throws Exception on failure
	 */
	public static Response requestToken(String memberName, String group, byte[] keyToAS) throws Exception {

		/* Configure parameters */

		String clientID = memberName;
		String groupName = group;
		byte[] key128 = keyToAS; // KeyStorage.memberAsKeys.get(memberName);//
									// {'a', 'b', 'c', 4, 5, 6, 7, 8, 9, 10, 11,
									// 12, 13, 14, 15, 16};
		String tokenURI = "coap://" + AS_HOST + ":" + AS_PORT + "/token";

		/* Set byte string scope */

		// Map<Short, CBORObject> params = new HashMap<>();
		// params.put(Constants.GRANT_TYPE, Token.clientCredentials);

		CBORObject cborArrayScope = CBORObject.NewArray();
		CBORObject cborArrayEntry = CBORObject.NewArray();
		cborArrayEntry.Add(groupName);

		int myRoles = 0;
		myRoles = Util.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_REQUESTER);
		myRoles = Util.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_RESPONDER);
		cborArrayEntry.Add(myRoles);

		cborArrayScope.Add(cborArrayEntry);
		byte[] byteStringScope = cborArrayScope.EncodeToBytes();

		/* Perform Token request */

		System.out.println("Performing Token request to AS.");
		System.out.println("AS Token resource is at: " + tokenURI);

		CBORObject params = GetToken.getClientCredentialsRequest(CBORObject.FromObject("rs2"),
				CBORObject.FromObject(byteStringScope), null);

		/*
		 * OSCoreCtx ctx = new OSCoreCtx(key128, true, null,
		 * clientID.getBytes(Constants.charset),
		 * "AS".getBytes(Constants.charset), null, null, null, null);
		 */

		byte[] senderId = KeyStorage.aceSenderIds.get(clientID);
		byte[] recipientId = KeyStorage.aceSenderIds.get("AS");
		OSCoreCtx ctx = new OSCoreCtx(key128, true, null, senderId, recipientId, null, null, null, null,
				MAX_UNFRAGMENTED_SIZE, true);

		Response response = OSCOREProfileRequestsGroupOSCORE.getToken(tokenURI, params, ctx, db);

		System.out.println("DB content: " + db.getContext(new byte[] { 0x00 }, null));

		/* Parse and print response */

		System.out.println("Response from AS: " + response.getPayloadString());
		CBORObject res = CBORObject.DecodeFromBytes(response.getPayload());
		// Map<Short, CBORObject> map = Constants.getParams(res);
		// System.out.println(map);

		System.out.println("Received response from AS to Token request: " + res.toString());

		db.purge(); // FIXME: Remove?
		return response;
	}

	/**
	 * Post to Authz-Info, then perform join request using multiple roles. Uses
	 * the ACE OSCORE Profile.
	 * 
	 * @param memberName name of client/server peer
	 * @param groupName name of group to join
	 * @param rsAddr address of the Resource Server
	 * @param portNumberRSnosec port number for RS no sec. communication
	 * @param ctxDB OSCORE context database
	 * @param cKeyPair key pair for joining peer
	 * @param responseFromAS response message previously received from AS
	 * @param clientCcsBytes joining peer's CCS as bytes
	 * 
	 * @return the generated Group OSCORE context after joining
	 * @throws Exception on failure
	 */
	public static GroupCtx testSuccessGroupOSCOREMultipleRoles(String memberName, String groupName, String rsAddr,
			int portNumberRSnosec, OSCoreCtxDB ctxDB, OneKey cKeyPair, Response responseFromAS, byte[] clientCcsBytes)
			throws Exception {

		boolean askForSignInfo = true;
		boolean askForEcdhInfo = true;
		boolean askForAuthCreds = true;
		boolean provideAuthCreds = true;

		// Create the scope
		CBORObject cborArrayScope = CBORObject.NewArray();
		CBORObject cborArrayEntry = CBORObject.NewArray();

		cborArrayEntry.Add(groupName);

		int myRoles = 0;
		myRoles = Util.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_REQUESTER);
		myRoles = Util.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_RESPONDER);
		cborArrayEntry.Add(myRoles);

		cborArrayScope.Add(cborArrayEntry);
		byte[] byteStringScope = cborArrayScope.EncodeToBytes();

		Response rsRes = OSCOREProfileRequestsGroupOSCORE.postToken(
				"coap://" + rsAddr + ":" + portNumberRSnosec + "/authz-info", responseFromAS, askForSignInfo,
				askForEcdhInfo, ctxDB, usedRecipientIds);

		printResponseFromRS(rsRes);

		// Check that the OSCORE context has been created:
		Assert.assertNotNull(ctxDB.getContext(
				"coap://" + rsAddr + ":" + portNumberRSnosec + "/" + rootGroupMembershipResource + "/" + groupName));

		CBORObject rsPayload = CBORObject.DecodeFromBytes(rsRes.getPayload());
		// Sanity checks already occurred in
		// OSCOREProfileRequestsGroupOSCORE.postToken()

		// Nonce from the GM, to use together with a local nonce to prove
		// possession of the private key
		byte[] gm_nonce = rsPayload.get(CBORObject.FromObject(Constants.KDCCHALLENGE)).GetByteString();

		// Group OSCORE specific values for the countersignature
		CBORObject signParamsExpected = CBORObject.NewArray();
		CBORObject signKeyParamsExpected = CBORObject.NewArray();

		// ECDSA_256
		if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {

			// The algorithm capabilities
			signParamsExpected.Add(KeyKeys.KeyType_EC2); // Key Type

			// The key type capabilities
			signKeyParamsExpected.Add(KeyKeys.KeyType_EC2); // Key Type
			signKeyParamsExpected.Add(KeyKeys.EC2_P256); // Curve
		}

		// EDDSA (Ed25519)
		if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {

			// The algorithm capabilities
			signParamsExpected.Add(KeyKeys.KeyType_OKP); // Key Type

			// The key type capabilities
			signKeyParamsExpected.Add(KeyKeys.KeyType_OKP); // Key Type
			signKeyParamsExpected.Add(KeyKeys.OKP_Ed25519); // Curve
		}

		// Group OSCORE specific values for the pairwise key derivation
		CBORObject ecdhParamsExpected = CBORObject.NewArray();
		CBORObject ecdhKeyParamsExpected = CBORObject.NewArray();

		// P-256
		if (ecdhKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
			// The algorithm capabilities
			ecdhParamsExpected.Add(KeyKeys.KeyType_EC2); // Key Type

			// The key type capabilities
			ecdhKeyParamsExpected.Add(KeyKeys.KeyType_EC2); // Key Type
			ecdhKeyParamsExpected.Add(KeyKeys.EC2_P256); // Curve
		}

		// X25519
		if (ecdhKeyCurve == KeyKeys.OKP_X25519.AsInt32()) {
			// The algorithm capabilities
			ecdhParamsExpected.Add(KeyKeys.KeyType_OKP); // Key Type

			// The key type capabilities
			ecdhKeyParamsExpected.Add(KeyKeys.KeyType_OKP); // Key Type
			ecdhKeyParamsExpected.Add(KeyKeys.OKP_X25519); // Curve
		}

		CBORObject credFmtExpected = CBORObject.FromObject(Constants.COSE_HEADER_PARAM_CCS);

		// Now proceed with the Join request

		CoapClient c = OSCOREProfileRequests.getClient(new InetSocketAddress(
				"coap://" + rsAddr + ":" + portNumberRSnosec + "/" + rootGroupMembershipResource + "/" + groupName,
				portNumberRSnosec), ctxDB);

		System.out.println("Performing Join request using OSCORE to GM.");

		CBORObject requestPayload = CBORObject.NewMap();

		cborArrayScope = CBORObject.NewArray();
		cborArrayScope.Add(groupName);

		myRoles = 0;
		myRoles = Util.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_REQUESTER);
		myRoles = Util.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_RESPONDER);
		cborArrayScope.Add(myRoles);

		byteStringScope = cborArrayScope.EncodeToBytes();
		requestPayload.Add(Constants.SCOPE, CBORObject.FromObject(byteStringScope));

		if (askForAuthCreds) {

			CBORObject getCreds = CBORObject.NewArray();

			getCreds.Add(CBORObject.True); // This must be true

			getCreds.Add(CBORObject.NewArray());
			// The following is required to retrieve the authentication
			// credentials of both the already present group members
			myRoles = 0;
			myRoles = Util.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_REQUESTER);
			getCreds.get(1).Add(myRoles);
			myRoles = Util.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_REQUESTER);
			myRoles = Util.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_RESPONDER);
			getCreds.get(1).Add(myRoles);

			getCreds.Add(CBORObject.NewArray()); // This must be empty

			requestPayload.Add(Constants.GET_CREDS, getCreds);

		}

		byte[] authCred = null;
		if (provideAuthCreds) {

			// This should never happen, if the Group Manager has provided
			// 'kdc_challenge' in the Token POST response,
			// or the joining node has computed N_S differently (e.g. through a
			// TLS exporter)
			if (gm_nonce == null) {
				Assert.fail("Error: the component N_S of the PoP evidence challence is null");
			}

			/*
			 * // Build the public key according to the format used in the group
			 * // Note: most likely, the result will NOT follow the required
			 * deterministic // encoding in byte lexicographic order, and it has
			 * to be adjusted offline OneKey publicKey = C1keyPair.PublicKey();
			 * switch (credFmtExpected.AsInt32()) { case
			 * Constants.COSE_HEADER_PARAM_CCS: // Build a CCS including the
			 * public key encodedPublicKey = Util.oneKeyToCCS(publicKey, "");
			 * break; case Constants.COSE_HEADER_PARAM_CWT: // Build a CWT
			 * including the public key // Constants.COSE_HEADER_PARAM_X5CHAIN:
			 * // Build/retrieve the certificate including the public key // }
			 */

			switch (credFmtExpected.AsInt32()) {
			case Constants.COSE_HEADER_PARAM_CCS:
				// A CCS including the public key
				if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
					System.out.println("Needs further configuration");
					authCred = StringUtil.hex2ByteArray(
							"A2026008A101A5010203262001215820E8F9A8D5850A533CDA24B9FA8A1EE293F6A0E1E81E1E560A64FF134D65F7ECEC225820164A6D5D4B97F56D1F60A12811D55DE7A055EBAC6164C9EF9302CBCBFF1F0ABE");
				}
				if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
					authCred = clientCcsBytes;
				}
				break;
			case Constants.COSE_HEADER_PARAM_CWT:
				// A CWT including the public key
				// TODO
				break;
			case Constants.COSE_HEADER_PARAM_X5CHAIN:
				// A certificate including the public key
				// TODO
				break;
			default:
				System.err.println("Error: credFmtExpected set incorrectly.");
				break;
			}

			requestPayload.Add(Constants.CLIENT_CRED, CBORObject.FromObject(authCred));

			// Add the nonce for PoP of the Client's private key
			byte[] cnonce = new byte[8];
			new SecureRandom().nextBytes(cnonce);
			requestPayload.Add(Constants.CNONCE, cnonce);

			// Add the signature computed over (scope | rsnonce | cnonce), using
			// the Client's private key
			int offset = 0;
			PrivateKey privKey = cKeyPair.AsPrivateKey();

			byte[] serializedScopeCBOR = CBORObject.FromObject(byteStringScope).EncodeToBytes();
			byte[] serializedGMNonceCBOR = CBORObject.FromObject(gm_nonce).EncodeToBytes();
			byte[] serializedCNonceCBOR = CBORObject.FromObject(cnonce).EncodeToBytes();
			byte[] dataToSign = new byte[serializedScopeCBOR.length + serializedGMNonceCBOR.length
					+ serializedCNonceCBOR.length];
			System.arraycopy(serializedScopeCBOR, 0, dataToSign, offset, serializedScopeCBOR.length);
			offset += serializedScopeCBOR.length;
			System.arraycopy(serializedGMNonceCBOR, 0, dataToSign, offset, serializedGMNonceCBOR.length);
			offset += serializedGMNonceCBOR.length;
			System.arraycopy(serializedCNonceCBOR, 0, dataToSign, offset, serializedCNonceCBOR.length);

			byte[] clientSignature = Util.computeSignature(signKeyCurve, privKey, dataToSign);

			if (clientSignature != null)
				requestPayload.Add(Constants.CLIENT_CRED_VERIFY, clientSignature);
			else
				Assert.fail("Computed signature is empty");

		}

		Request joinReq = new Request(Code.POST, Type.CON);
		joinReq.getOptions().setOscore(new byte[0]);
		joinReq.setPayload(requestPayload.EncodeToBytes());
		joinReq.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);

		// Submit the request
		System.out.println("");
		System.out.println("Sent Join request to GM: " + requestPayload.toString());
		printMapPayload(requestPayload);

		CoapResponse r2 = c.advanced(joinReq);

		if (r2.getOptions().getLocationPath().size() != 0) {
			System.out.print("Location-Path: ");
			System.out.println(r2.getOptions().getLocationPathString());
		}

		printResponseFromRS(r2.advanced());

		Assert.assertEquals("CREATED", r2.getCode().name());

		byte[] responsePayload = r2.getPayload();
		CBORObject joinResponse = CBORObject.DecodeFromBytes(responsePayload);

		Assert.assertEquals(CBORType.Map, joinResponse.getType());
		int credFmt;

		// Check the proof-of-possession evidence over kdc_nonce, using the GM's
		// public key
		Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.KDC_NONCE)));
		Assert.assertEquals(CBORType.ByteString,
				joinResponse.get(CBORObject.FromObject(Constants.KDC_NONCE)).getType());
		Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.KDC_CRED)));
		Assert.assertEquals(CBORType.ByteString, joinResponse.get(CBORObject.FromObject(Constants.KDC_CRED)).getType());
		Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.KDC_CRED_VERIFY)));
		Assert.assertEquals(CBORType.ByteString,
				joinResponse.get(CBORObject.FromObject(Constants.KDC_CRED_VERIFY)).getType());

		Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.KEY)));
		Assert.assertEquals(CBORType.Map, joinResponse.get(CBORObject.FromObject(Constants.KEY)).getType());
		Assert.assertEquals(true, joinResponse.get(CBORObject.FromObject(Constants.KEY))
				.ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.cred_fmt)));
		Assert.assertEquals(CBORType.Map, joinResponse.get(CBORObject.FromObject(Constants.KEY)).getType());
		Assert.assertEquals(CBORType.Integer, joinResponse.get(CBORObject.FromObject(Constants.KEY))
				.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.cred_fmt)).getType());
		credFmt = joinResponse.get(CBORObject.FromObject(Constants.KEY))
				.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.cred_fmt)).AsInt32();

		OneKey gmPublicKeyRetrieved = null;
		byte[] kdcCredBytes = joinResponse.get(CBORObject.FromObject(Constants.KDC_CRED)).GetByteString();
		switch (credFmt) {
		case Constants.COSE_HEADER_PARAM_CCS:
			CBORObject ccs = CBORObject.DecodeFromBytes(kdcCredBytes);
			if (ccs.getType() == CBORType.Map) {
				// Retrieve the public key from the CCS
				gmPublicKeyRetrieved = Util.ccsToOneKey(ccs);
			} else {
				Assert.fail("Invalid format of Group Manager authentication credential");
			}
			break;
		case Constants.COSE_HEADER_PARAM_CWT:
			CBORObject cwt = CBORObject.DecodeFromBytes(kdcCredBytes);
			if (cwt.getType() == CBORType.Array) {
				// Retrieve the public key from the CWT
				// TODO
			} else {
				Assert.fail("Invalid format of Group Manager authentication credential");
			}
			break;
		case Constants.COSE_HEADER_PARAM_X5CHAIN:
			// Retrieve the public key from the certificate
			// TODO
			break;
		default:
			Assert.fail("Invalid format of Group Manager authentication credential");
		}
		if (gmPublicKeyRetrieved == null) {
			Assert.fail("Invalid format of Group Manager authentication credential");
			return null;
		}

		PublicKey gmPublicKey = gmPublicKeyRetrieved.AsPublicKey();

		byte[] gmNonce = joinResponse.get(CBORObject.FromObject(Constants.KDC_NONCE)).GetByteString();

		CBORObject gmPopEvidence = joinResponse.get(CBORObject.FromObject(Constants.KDC_CRED_VERIFY));
		byte[] rawGmPopEvidence = gmPopEvidence.GetByteString();

		// Invalid Client's PoP signature
		if (!Util.verifySignature(signKeyCurve, gmPublicKey, gmNonce, rawGmPopEvidence)) {
			Assert.fail("Invalid GM's PoP evidence");
		}

		// Final join response parsing and Group Context generation

		// Print the join response
		Tools.printJoinResponse(joinResponse);

		// Pause if this is for server7
		if (!memberName.toLowerCase().contains("server7")) {
			System.out.println("Has now joined the OSCORE group.");
		} else {
			printPause(memberName, "Has now joined the OSCORE group.");
		}

		MultiKey clientKey = new MultiKey(authCred, cKeyPair.get(KeyKeys.OKP_D).GetByteString());
		GroupCtx groupOscoreCtx = Tools.generateGroupOSCOREContext(joinResponse, clientKey);

		return groupOscoreCtx;
	}

	/**
	 * Simple method for "press enter to continue" functionality
	 */
	static void printPause(String memberName, String message) {

		// Only print for Server7
		if (!memberName.toLowerCase().equals("server7")) {
			return;
		}

		System.out.println("===");
		System.out.println(message);
		System.out.println("Press ENTER to continue");
		System.out.println("===");
		try {
			@SuppressWarnings("unused")
			int read = System.in.read(new byte[2]);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Printing methods

	private static void printMapPayload(CBORObject obj) {
		if (obj != null) {
			System.out.println("*** Map Payload *** ");
			System.out.println(obj);
		} else {
			System.out.println("*** The payload argument is null!");
		}
	}

	private static void printResponseFromRS(Response res) {
		if (res != null) {
			System.out.println("*** Response from the RS *** ");
			System.out.print(res.getCode().codeClass + ".0" + res.getCode().codeDetail);
			System.out.println(" " + res.getCode().name());

			if (res.getPayload() != null) {

				if (res.getOptions().getContentFormat() == Constants.APPLICATION_ACE_CBOR
						|| res.getOptions().getContentFormat() == Constants.APPLICATION_ACE_GROUPCOMM_CBOR) {
					CBORObject resCBOR = CBORObject.DecodeFromBytes(res.getPayload());
					System.out.println(resCBOR.toString());
				} else {
					System.out.println(new String(res.getPayload()));
				}
			}
		} else {
			System.out.println("*** The response from the RS is null!");
			System.out.print("No response received");
		}
	}

	/**
	 * Print help message with valid command line arguments
	 */
	private static void printHelp() {
		System.out.println("Usage: [ -name Name ] [ -gm URI ] [ -as URI ] [-delay Seconds ] [ -dht {URI} ] [ -help ]");

		System.out.println("Options:");

		System.out.print("-name");
		System.out.println("\t Name/Role of this peer");

		System.out.print("-gm");
		System.out.println("\t Group Manager base URI");

		System.out.print("-as");
		System.out.println("\t Authorization Server base URI");

		System.out.print("-delay");
		System.out.println("\t Delay in seconds before starting");

		System.out.print("-dht");
		System.out.println("\t Use DHT: Optionally specify its WebSocket URI");

		System.out.print("-help");
		System.out.println("\t Print help");
	}

}
