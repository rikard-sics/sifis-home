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
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.cose.AlgorithmID;
import org.eclipse.californium.cose.CoseException;
import org.eclipse.californium.cose.KeyKeys;
import org.eclipse.californium.cose.OneKey;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.oscore.HashMapCtxDB;
import org.eclipse.californium.oscore.InstallCryptoProviders;
import org.eclipse.californium.oscore.OSCoreCoapStackFactory;
import org.eclipse.californium.oscore.OSCoreCtx;
import org.eclipse.californium.oscore.OSCoreCtxDB;
import org.eclipse.californium.oscore.OSException;
import org.eclipse.californium.oscore.Utility;
import org.eclipse.californium.oscore.group.GroupCtx;
import org.eclipse.californium.oscore.group.MultiKey;
import org.junit.Assert;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;

import se.sics.ace.AceException;
import se.sics.ace.Constants;
import se.sics.ace.client.GetToken;
import se.sics.ace.coap.client.OSCOREProfileRequests;
import se.sics.ace.coap.client.OSCOREProfileRequestsGroupOSCORE;
import se.sics.ace.oscore.GroupOSCOREInputMaterialObject;
import se.sics.ace.oscore.GroupOSCOREInputMaterialObjectParameters;
import se.sics.prototype.support.AceUtil;
import se.sics.prototype.support.KeyStorage;
import se.sics.prototype.support.Util;

/**
 * A stand-alone application for Client->AS followed by Client->GM
 * communication using the OSCORE profile.
 * 
 * First the client will request a Token from the AS,
 * it will then post it to the GM and then proceed with
 * the Group Joining procedure.
 * 
 * @author Rikard Höglund
 *
 */
public class OscoreAsRsClient {

	/* Information:
	 Clients: Server1, Server2, Server3, Server4, Server5, Server6, Client1, Client2
	 Groups: GroupA (aaaaaa570000), GroupB (bbbbbb570000)
	 */
	
	//Sets the default GM port to use
	private static int GM_PORT = CoAP.DEFAULT_COAP_PORT + 100;
	//Sets the default GM hostname/IP to use
	private static String GM_HOST = "localhost";
	
	//Sets the default AS port to use
	private static int AS_PORT = CoAP.DEFAULT_COAP_PORT;
	//Sets the default AS hostname/IP to use
	private static String AS_HOST = "localhost";
	
	//Multicast IP for Group A
	static final InetAddress groupA_multicastIP = new InetSocketAddress("224.0.1.191", 0).getAddress();
	
	//Multicast IP for Group B
	static final InetAddress groupB_multicastIP = new InetSocketAddress("224.0.1.192", 0).getAddress();
	
    static HashMapCtxDB db = new HashMapCtxDB();
    
    private final static int MAX_UNFRAGMENTED_SIZE = 4096;

    // Each set of the list refers to a different size of Recipient IDs.
    // The element with index 0 includes as elements Recipient IDs with size 1 byte.
    private static List<Set<Integer>> usedRecipientIds = new ArrayList<Set<Integer>>();

    private static final String rootGroupMembershipResource = "ace-group";

    // Uncomment to set EDDSA with curve Ed25519 for countersignatures
    private static int signKeyCurve = KeyKeys.OKP_Ed25519.AsInt32();
    // Uncomment to set curve X25519 for pairwise key derivation
    private static int ecdhKeyCurve = KeyKeys.OKP_X25519.AsInt32();
    
	/**
	 * Main method for Token request followed by Group joining
	 * 
	 * @throws CoseException 
	 */
	public static void main(String[] args) throws CoseException, URISyntaxException {
		
		// install needed cryptography providers
		try {
			org.eclipse.californium.oscore.InstallCryptoProviders.installProvider();
		} catch (Exception e) {
			System.err.println("Failed to install cryptography providers.");
			e.printStackTrace();
		}
		
		//Set member name, AS and GM to use from command line arguments
		String memberName = "Client1";
		for(int i = 0 ; i < args.length ; i += 2) {
			if(args[i].equals("-name")) {
				memberName = args[i + 1];
			} else if(args[i].equals("-gm")) {
				GM_HOST = new URI(args[i + 1]).getHost();
				GM_PORT = new URI(args[i + 1]).getPort();
			} else if(args[i].equals("-as")) {
				AS_HOST = new URI(args[i + 1]).getHost();
				AS_PORT = new URI(args[i + 1]).getPort();
			}
		}
		
		//Explicitly enable the OSCORE Stack
		if(CoapEndpoint.isDefaultCoapStackFactorySet() == false) {
			OSCoreCoapStackFactory.useAsDefault(db);
		}
		
        for (int i = 0; i < 4; i++) {
            // Empty sets of assigned Sender IDs; one set for each possible Sender ID size in bytes.
            // The set with index 0 refers to Sender IDs with size 1 byte
            usedRecipientIds.add(new HashSet<Integer>());
        }

		//Set group to join based on the member name
		String group = "";
		InetAddress multicastIP = null;
		switch(memberName) {
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

		//Set public/private key to use in the group
		String publicPrivateKey;
		publicPrivateKey = KeyStorage.publicPrivateKeys.get(memberName);
		
		//Set key (OSCORE master secret) to use towards AS
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
		
		//Request Token from AS
		Response responseFromAS = null;
		try {
			responseFromAS = requestToken(memberName, group, keyToAS);
		} catch (Exception e) {
			System.err.print("Token request procedure failed: ");
			e.printStackTrace();
		}
		
		printPause(memberName, "Will now post Token to Group Manager and perform group joining");
		
//		///////////////
//    	// EDDSA (Ed25519)
//    	CBORObject rpkData = null;
//    	CBORObject x = null;
//    	CBORObject d = null;
//    	OneKey C1keyPair = null;
//		String c1X_EDDSA = "069E912B83963ACC5941B63546867DEC106E5B9051F2EE14F3BC5CC961ACD43A";
//		String c1D_EDDSA = privKeyClient;
//    	if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
//            rpkData = CBORObject.NewMap();
//            rpkData.Add(KeyKeys.KeyType.AsCBOR(), KeyKeys.KeyType_OKP);
//            rpkData.Add(KeyKeys.Algorithm.AsCBOR(), AlgorithmID.EDDSA.AsCBOR());
//            rpkData.Add(KeyKeys.OKP_Curve.AsCBOR(), KeyKeys.OKP_Ed25519);
//            x = CBORObject.FromObject(hexStringToByteArray(c1X_EDDSA));
//            d = CBORObject.FromObject(hexStringToByteArray(c1D_EDDSA));
//            rpkData.Add(KeyKeys.OKP_X.AsCBOR(), x);
//            rpkData.Add(KeyKeys.OKP_D.AsCBOR(), d);
//            C1keyPair = new OneKey(rpkData);
//    	}
		
		
		// Get OneKey representation of this member's public/private key
		OneKey cKeyPair = new MultiKey(KeyStorage.memberCcs.get(memberName), KeyStorage.memberPrivateKeys.get(memberName)).getCoseKey();
    	// Get byte array of this member's CCS
		byte[] memberCcs = KeyStorage.memberCcs.get(memberName);
		
    	// Post Token to GM and perform Group joining
    	GroupCtx derivedCtx = null;
		try {
			derivedCtx = testSuccessGroupOSCOREMultipleRoles(memberName, group, GM_HOST, GM_PORT, db, cKeyPair,
					responseFromAS, memberCcs);
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
//		//Post Token to GM and perform Group joining
//		GroupCtx derivedCtx = null;
//        try {
//        	derivedCtx = postTokenAndJoinOld(memberName, group, publicPrivateKey, responseFromAS);
//        } catch (IllegalStateException | InvalidCipherTextException | CoseException | AceException | OSException
//                | ConnectorException | IOException e) {
//            System.err.print("Join procedure failed: ");
//            e.printStackTrace();
//        }
        
        //Now start the Group OSCORE Client or Server application with the derived context
        try {
	        if(memberName.equals("Client1") || memberName.equals("Client2")) {
	        	GroupOscoreClient.start(derivedCtx, multicastIP);
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
	 * @param memberName
	 * @param group
	 * @param keyToAS
	 * @return the CoAP response from the AS
	 * @throws Exception
	 */
	public static Response requestToken(String memberName, String group, byte[] keyToAS) throws Exception {
		
		/* Configure parameters */
		
		String clientID = memberName;
		String groupName = group;		
        byte[] key128 = keyToAS; //KeyStorage.memberAsKeys.get(memberName);// {'a', 'b', 'c', 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        String tokenURI = "coap://" + AS_HOST + ":" + AS_PORT + "/token";
        
        /* Set byte string scope */
        
        // Map<Short, CBORObject> params = new HashMap<>();
        // params.put(Constants.GRANT_TYPE, Token.clientCredentials);

        CBORObject cborArrayScope = CBORObject.NewArray();
        CBORObject cborArrayEntry = CBORObject.NewArray();
        cborArrayEntry.Add(groupName);

        int myRoles = 0;
        myRoles = AceUtil.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_REQUESTER);
        myRoles = AceUtil.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_RESPONDER);
        cborArrayEntry.Add(myRoles);

        cborArrayScope.Add(cborArrayEntry);
        byte[] byteStringScope = cborArrayScope.EncodeToBytes();
		
        /* Perform Token request */
        
        System.out.println("Performing Token request to AS.");
        System.out.println("AS Token resource is at: " + tokenURI);
        
		CBORObject params = GetToken.getClientCredentialsRequest(
                CBORObject.FromObject("rs2"),
                CBORObject.FromObject(byteStringScope), null);
        
        /*
         * OSCoreCtx ctx = new OSCoreCtx(key128, true, null, clientID.getBytes(Constants.charset),
         * "AS".getBytes(Constants.charset), null, null, null, null);
         */

        byte[] senderId = KeyStorage.aceSenderIds.get(clientID);
        byte[] recipientId = KeyStorage.aceSenderIds.get("AS");
        OSCoreCtx ctx = new OSCoreCtx(key128, true, null, senderId, recipientId,
                null, null, null, null, MAX_UNFRAGMENTED_SIZE);
        
        Response response = OSCOREProfileRequestsGroupOSCORE.getToken(
				tokenURI, params, ctx, db);
        
        System.out.println("DB content: " + db.getContext(new byte[] { 0x00 }, null));

        /* Parse and print response */
        
        System.out.println("Response from AS: " + response.getPayloadString());
        CBORObject res = CBORObject.DecodeFromBytes(response.getPayload());
        //Map<Short, CBORObject> map = Constants.getParams(res);
        //System.out.println(map);

        System.out.println("Received response from AS to Token request: " + res.toString());
        
        //Fix the structure of the response from the AS as the first element should be an array (FIXME?)
		// CBORObject first = res.get(CBORObject.FromObject(1));
		// CBORObject firstAsCBORArray =
		// CBORObject.DecodeFromBytes(first.GetByteString());
		// // System.out.println(firstAsCBORArray.toString());
		// res.Remove(CBORObject.FromObject(1));
		// res.Add(CBORObject.FromObject(1), firstAsCBORArray);
		// System.out.println("Fixed response from AS to Token request: " +
		// res.toString());
		// response.setPayload(res.EncodeToBytes());
        
        db.purge(); // FIXME: Remove?
        return response;
	}
	
	/**
	 * Post to Authz-Info, then perform join request using multiple roles.
     * Uses the ACE OSCORE Profile.
     *  
	 * @param groupName
	 * @param rsAddr
	 * @param portNumberRSnosec
	 * @param ctxDB
	 * @param C1keyPair
	 * @param responseFromAS
	 * @throws Exception
	 */
	public static GroupCtx testSuccessGroupOSCOREMultipleRoles(String memberName, String groupName, String rsAddr,
			int portNumberRSnosec, OSCoreCtxDB ctxDB, OneKey cKeyPair,
			Response responseFromAS, byte[] clientCcsBytes) throws Exception {

    	boolean askForSignInfo = true;
    	boolean askForEcdhInfo = true;
        boolean askForPubKeys = true;
        boolean providePublicKey = true;
        
        Map<Short, CBORObject> params = new HashMap<>(); 
        
        // Create the scope
        CBORObject cborArrayScope = CBORObject.NewArray();
        CBORObject cborArrayEntry = CBORObject.NewArray();
        
        cborArrayEntry.Add(groupName);
        
    	int myRoles = 0;
    	myRoles = AceUtil.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_REQUESTER);
    	myRoles = AceUtil.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_RESPONDER);
    	cborArrayEntry.Add(myRoles);
    	
        cborArrayScope.Add(cborArrayEntry);
        byte[] byteStringScope = cborArrayScope.EncodeToBytes();
        
//        params.put(Constants.SCOPE, CBORObject.FromObject(byteStringScope));
//        params.put(Constants.AUD, CBORObject.FromObject("aud2"));
//        params.put(Constants.CTI, CBORObject.FromObject(
//                   "token4JoinMultipleRoles".getBytes(Constants.charset))); //Need different CTI
//        params.put(Constants.ISS, CBORObject.FromObject("TestAS"));

//        CBORObject osc = CBORObject.NewMap();        
//        osc.Add(Constants.OS_MS, keyCnf);
//        osc.Add(Constants.OS_ID, AceUtil.intToBytes(0));
        
//        CBORObject cnf = CBORObject.NewMap();
//        cnf.Add(Constants.OSCORE_Input_Material, osc);
//        params.put(Constants.CNF, cnf);
//        CWT token = new CWT(params);
//        CBORObject payload = CBORObject.NewMap();
//        payload.Add(Constants.ACCESS_TOKEN, token.encode(ctx1));
//        payload.Add(Constants.CNF, cnf);
//        Response asRes = new Response(CoAP.ResponseCode.CREATED);
//        asRes.setPayload(payload.EncodeToBytes());
//        
//        Response rsRes = OSCOREProfileRequestsGroupOSCORE.postToken(
//                		 "coap://" + rsAddr + ":" + portNumberRSnosec + "/authz-info",
//                		 asRes, askForSignInfo, askForEcdhInfo, ctxDB, usedRecipientIds);
        
        Response rsRes = OSCOREProfileRequestsGroupOSCORE.postToken(
       		 "coap://" + rsAddr + ":" + portNumberRSnosec + "/authz-info",
       		responseFromAS, askForSignInfo, askForEcdhInfo, ctxDB, usedRecipientIds);
        
        printResponseFromRS(rsRes);
        
        //Check that the OSCORE context has been created:
        Assert.assertNotNull(ctxDB.getContext(
                "coap://" + rsAddr + ":" + portNumberRSnosec + "/" + rootGroupMembershipResource + "/" + groupName));
        
        CBORObject rsPayload = CBORObject.DecodeFromBytes(rsRes.getPayload());
        // Sanity checks already occurred in OSCOREProfileRequestsGroupOSCORE.postToken()
        
        // Nonce from the GM, to use together with a local nonce to prove possession of the private key
        byte[] gm_nonce = rsPayload.get(CBORObject.FromObject(Constants.KDCCHALLENGE)).GetByteString();
        
        CBORObject signInfo = null;
        CBORObject ecdhInfo = null;
        
        // Group OSCORE specific values for the countersignature
        CBORObject signAlgExpected = null;
        CBORObject signParamsExpected = CBORObject.NewArray();
        CBORObject signKeyParamsExpected = CBORObject.NewArray();

        // ECDSA_256
        if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
            signAlgExpected = AlgorithmID.ECDSA_256.AsCBOR();
            
            // The algorithm capabilities
            signParamsExpected.Add(KeyKeys.KeyType_EC2); // Key Type
            
            // The key type capabilities
            signKeyParamsExpected.Add(KeyKeys.KeyType_EC2); // Key Type
            signKeyParamsExpected.Add(KeyKeys.EC2_P256); // Curve
        }

        // EDDSA (Ed25519)
        if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
            signAlgExpected = AlgorithmID.EDDSA.AsCBOR();
            
            // The algorithm capabilities
            signParamsExpected.Add(KeyKeys.KeyType_OKP); // Key Type
            
            // The key type capabilities
            signKeyParamsExpected.Add(KeyKeys.KeyType_OKP); // Key Type
            signKeyParamsExpected.Add(KeyKeys.OKP_Ed25519); // Curve
        }
        
        
        // Group OSCORE specific values for the pairwise key derivation
        CBORObject ecdhAlgExpected = AlgorithmID.ECDH_SS_HKDF_256.AsCBOR();
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
        
        CBORObject pubKeyEncExpected = CBORObject.FromObject(Constants.COSE_HEADER_PARAM_CCS);
                
        
        // DEBUG: START SET OF ASSERTIONS
        /*
        if (askForSignInfo) {
        	Assert.assertEquals(true, rsPayload.ContainsKey(CBORObject.FromObject(Constants.SIGN_INFO)));
            Assert.assertEquals(CBORType.Array, rsPayload.get(CBORObject.FromObject(Constants.SIGN_INFO)).getType());
            signInfo = CBORObject.NewArray();
        	signInfo = rsPayload.get(CBORObject.FromObject(Constants.SIGN_INFO));
        	
	    	CBORObject signInfoExpected = CBORObject.NewArray();
	    	CBORObject signInfoEntry = CBORObject.NewArray();
	    	
	    	signInfoEntry.Add(CBORObject.FromObject(groupName));
	    	
	    	if (signAlgExpected == null)
	    		signInfoEntry.Add(CBORObject.Null);
	    	else
	    		signInfoEntry.Add(signAlgExpected);
	    	
	    	if (signParamsExpected == null)
	    		signInfoEntry.Add(CBORObject.Null);
	    	else
	    		signInfoEntry.Add(signParamsExpected);
	    	
	    	if (signKeyParamsExpected == null)
	    		signInfoEntry.Add(CBORObject.Null);
	    	else
	    		signInfoEntry.Add(signKeyParamsExpected);
        	
        	if (pubKeyEncExpected == null)
        		signInfoEntry.Add(CBORObject.Null);
        	else
        		signInfoEntry.Add(pubKeyEncExpected);
	    	
	        signInfoExpected.Add(signInfoEntry);

        	Assert.assertEquals(signInfoExpected, signInfo);
        }
        
        if (askForEcdhInfo) {
		    Assert.assertEquals(true, rsPayload.ContainsKey(CBORObject.FromObject(Constants.ECDH_INFO)));
		    
		    if (rsPayload.ContainsKey(CBORObject.FromObject(Constants.ECDH_INFO))) {
		    
		        Assert.assertEquals(CBORType.Array, rsPayload.get(CBORObject.FromObject(Constants.ECDH_INFO)).getType());
		        ecdhInfo = CBORObject.NewArray();
		        ecdhInfo = rsPayload.get(CBORObject.FromObject(Constants.ECDH_INFO));
		        
		        CBORObject ecdhInfoExpected = CBORObject.NewArray();
		        CBORObject ecdhInfoEntry = CBORObject.NewArray();
		        
		        ecdhInfoEntry.Add(CBORObject.FromObject(groupName));
		        
		        if (ecdhAlgExpected == null)
		            ecdhInfoEntry.Add(CBORObject.Null);
		        else
		            ecdhInfoEntry.Add(ecdhAlgExpected);
		        
		        if (ecdhParamsExpected == null)
		            ecdhInfoEntry.Add(CBORObject.Null);
		        else
		            ecdhInfoEntry.Add(ecdhParamsExpected);
		        
		        if (ecdhKeyParamsExpected == null)
		            ecdhInfoEntry.Add(CBORObject.Null);
		        else
		            ecdhInfoEntry.Add(ecdhKeyParamsExpected);
		        
		        if (pubKeyEncExpected == null)
		            ecdhInfoEntry.Add(CBORObject.Null);
		        else
		            ecdhInfoEntry.Add(pubKeyEncExpected);
		        
		        ecdhInfoExpected.Add(ecdhInfoEntry);
		
		        Assert.assertEquals(ecdhInfo, ecdhInfoExpected);
		        
		    }
		}
        */
        // DEBUG: END SET OF ASSERTIONS
        
        
        // Now proceed with the Join request
        
        CoapClient c = OSCOREProfileRequests.getClient(new InetSocketAddress(
        		"coap://" + rsAddr + ":" + portNumberRSnosec + "/" +
        		rootGroupMembershipResource + "/" + groupName, portNumberRSnosec),
        		ctxDB);
        
        System.out.println("Performing Join request using OSCORE to GM at " + "coap://localhost/feedca570000");
       
        CBORObject requestPayload = CBORObject.NewMap();
       
        cborArrayScope = CBORObject.NewArray();
        cborArrayScope.Add(groupName);
        
    	myRoles = 0;
    	myRoles = AceUtil.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_REQUESTER);
    	myRoles = AceUtil.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_RESPONDER);
    	cborArrayScope.Add(myRoles);
        
        byteStringScope = cborArrayScope.EncodeToBytes();
        requestPayload.Add(Constants.SCOPE, CBORObject.FromObject(byteStringScope));
       
        if (askForPubKeys) {
           
            CBORObject getPubKeys = CBORObject.NewArray();
            
            getPubKeys.Add(CBORObject.True); // This must be true
            
            getPubKeys.Add(CBORObject.NewArray());
            // The following is required to retrieve the public keys of both the already present group members
            myRoles = 0;
            myRoles = AceUtil.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_REQUESTER);
            getPubKeys.get(1).Add(myRoles);            
            myRoles = AceUtil.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_REQUESTER);
        	myRoles = AceUtil.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_RESPONDER);
        	getPubKeys.get(1).Add(myRoles);
            
            getPubKeys.Add(CBORObject.NewArray()); // This must be empty
            
            requestPayload.Add(Constants.GET_PUB_KEYS, getPubKeys);
           
        }
       
       byte[] encodedPublicKey = null;
       if (providePublicKey) {
           
	    	// This should never happen, if the Group Manager has provided 'kdc_challenge' in the Token POST response,
	       	// or the joining node has computed N_S differently (e.g. through a TLS exporter)
	       	if (gm_nonce == null)
	       		Assert.fail("Error: the component N_S of the PoP evidence challence is null");
            
            
            encodedPublicKey = null;

            /*
            // Build the public key according to the format used in the group
            // Note: most likely, the result will NOT follow the required deterministic
            //       encoding in byte lexicographic order, and it has to be adjusted offline
            OneKey publicKey = C1keyPair.PublicKey();
	        switch (pubKeyEncExpected.AsInt32()) {
	            case Constants.COSE_HEADER_PARAM_CCS:
	                // Build a CCS including the public key
	                encodedPublicKey = Util.oneKeyToCCS(publicKey, "");
	                break;
	            case Constants.COSE_HEADER_PARAM_CWT:
	                // Build a CWT including the public key
	                // TODO
	                break;
	            case Constants.COSE_HEADER_PARAM_X5CHAIN:
	                // Build/retrieve the certificate including the public key
	                // TODO
	                break;
	        }
	        */
	
	        switch (pubKeyEncExpected.AsInt32()) {
		        case Constants.COSE_HEADER_PARAM_CCS:
		            // A CCS including the public key
		            if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
		            	System.out.println("Needs further configuration");
		                encodedPublicKey = hexStringToByteArray("A2026008A101A5010203262001215820E8F9A8D5850A533CDA24B9FA8A1EE293F6A0E1E81E1E560A64FF134D65F7ECEC225820164A6D5D4B97F56D1F60A12811D55DE7A055EBAC6164C9EF9302CBCBFF1F0ABE");
		            }
		            if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
		                encodedPublicKey = clientCcsBytes;
		            }
		            break;
		        case Constants.COSE_HEADER_PARAM_CWT:
		            // A CWT including the public key
		            // TODO
		            encodedPublicKey = null;
		            break;
		        case Constants.COSE_HEADER_PARAM_X5CHAIN:
		            // A certificate including the public key
		            // TODO
		            encodedPublicKey = null;
		            break;
			default:
				System.err.println("Error: pubKeyEncExpected set incorrectly.");
				break;
	        }
	
	        requestPayload.Add(Constants.CLIENT_CRED, CBORObject.FromObject(encodedPublicKey));
        	
            
        	// Add the nonce for PoP of the Client's private key
            byte[] cnonce = new byte[8];
            new SecureRandom().nextBytes(cnonce);
            requestPayload.Add(Constants.CNONCE, cnonce);
            
            // Add the signature computed over (scope | rsnonce | cnonce), using the Client's private key
            int offset = 0;
            PrivateKey privKey = cKeyPair.AsPrivateKey();
            
            byte[] serializedScopeCBOR = CBORObject.FromObject(byteStringScope).EncodeToBytes();
            byte[] serializedGMNonceCBOR = CBORObject.FromObject(gm_nonce).EncodeToBytes();
            byte[] serializedCNonceCBOR = CBORObject.FromObject(cnonce).EncodeToBytes();
       	    byte [] dataToSign = new byte [serializedScopeCBOR.length + serializedGMNonceCBOR.length + serializedCNonceCBOR.length];
       	    System.arraycopy(serializedScopeCBOR, 0, dataToSign, offset, serializedScopeCBOR.length);
       	    offset += serializedScopeCBOR.length;
       	    System.arraycopy(serializedGMNonceCBOR, 0, dataToSign, offset, serializedGMNonceCBOR.length);
       	    offset += serializedGMNonceCBOR.length;
       	    System.arraycopy(serializedCNonceCBOR, 0, dataToSign, offset, serializedCNonceCBOR.length);
                   	   
       	    byte[] clientSignature = AceUtil.computeSignature(signKeyCurve, privKey, dataToSign);
            
            if (clientSignature != null)
            	requestPayload.Add(Constants.CLIENT_CRED_VERIFY, clientSignature);
        	else
        		Assert.fail("Computed signature is empty");
           
        }
       
        Request joinReq = new Request(Code.POST, Type.CON);
        joinReq.getOptions().setOscore(new byte[0]);
        joinReq.setPayload(requestPayload.EncodeToBytes());
        joinReq.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);
       
        //Submit the request
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
        int pubKeyEnc;
       
        
        // DEBUG: START SET OF ASSERTIONS
        /*
        Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.GKTY)));
        Assert.assertEquals(CBORType.Integer, joinResponse.get(CBORObject.FromObject(Constants.GKTY)).getType());
        // Assume that "Group_OSCORE_Input_Material object" is registered with value 0 in the "ACE Groupcomm Key" Registry of draft-ietf-ace-key-groupcomm
        Assert.assertEquals(Constants.GROUP_OSCORE_INPUT_MATERIAL_OBJECT, joinResponse.get(CBORObject.FromObject(Constants.GKTY)).AsInt32());
       
        Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.KEY)));
        Assert.assertEquals(CBORType.Map, joinResponse.get(CBORObject.FromObject(Constants.KEY)).getType());
       
        CBORObject myMap = joinResponse.get(CBORObject.FromObject(Constants.KEY));
       
        // Sanity check
        Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(OSCOREInputMaterialObjectParameters.ms)));
        Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.group_SenderID)));
        Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(OSCOREInputMaterialObjectParameters.hkdf)));
        Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.sign_enc_alg)));
        Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(OSCOREInputMaterialObjectParameters.salt)));
        Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(OSCOREInputMaterialObjectParameters.contextId)));
        Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.sign_alg)));
        Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ecdh_alg)));
        
        if (signKeyCurve == KeyKeys.EC2_P256.AsInt32() || signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
            Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.sign_params)));
        }
        if (ecdhKeyCurve == KeyKeys.EC2_P256.AsInt32() || ecdhKeyCurve == KeyKeys.OKP_X25519.AsInt32()) {
            Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ecdh_params)));
        }

       
        // Check the presence, type and value of the public key encoding
        Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.pub_key_enc)));
        Assert.assertEquals(CBORType.Integer, myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.pub_key_enc)).getType());        
        Assert.assertEquals(CBORObject.FromObject(Constants.COSE_HEADER_PARAM_CCS), myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.pub_key_enc)));
       
        Assert.assertEquals(true, myMap.ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.pub_key_enc)));
        Assert.assertEquals(CBORType.Integer, myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.pub_key_enc)).getType());
        pubKeyEnc = myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.pub_key_enc)).AsInt32();
        
        final byte[] masterSecret = { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                                      (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
                                      (byte) 0x09, (byte) 0x0A, (byte) 0x0B, (byte) 0x0C,
                                      (byte) 0x0D, (byte) 0x0E, (byte) 0x0F, (byte) 0x10 };
        final byte[] masterSalt =   { (byte) 0x9e, (byte) 0x7c, (byte) 0xa9, (byte) 0x22,
                                      (byte) 0x23, (byte) 0x78, (byte) 0x63, (byte) 0x40 };
        final byte[] senderId = new byte[] { (byte) 0x25 };
        final byte[] groupId = new byte[] { (byte) 0xfe, (byte) 0xed, (byte) 0xca, (byte) 0x57, (byte) 0xf0, (byte) 0x5c };

        final AlgorithmID hkdf = AlgorithmID.HKDF_HMAC_SHA_256;
       
        
        final AlgorithmID signEncAlg = AlgorithmID.AES_CCM_16_64_128;
        AlgorithmID signAlg = null;
        CBORObject signAlgCapabilities = CBORObject.NewArray();
        CBORObject signKeyCapabilities = CBORObject.NewArray();
        CBORObject signParams = CBORObject.NewArray();

        // ECDSA_256
        if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
        signAlg = AlgorithmID.ECDSA_256;
        signAlgCapabilities.Add(KeyKeys.KeyType_EC2); // Key Type
        signKeyCapabilities.Add(KeyKeys.KeyType_EC2); // Key Type
        signKeyCapabilities.Add(KeyKeys.EC2_P256); // Curve
        }
            
        // EDDSA (Ed25519)
        if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
        signAlg = AlgorithmID.EDDSA;
        signAlgCapabilities.Add(KeyKeys.KeyType_OKP); // Key Type
        signKeyCapabilities.Add(KeyKeys.KeyType_OKP); // Key Type
        signKeyCapabilities.Add(KeyKeys.OKP_Ed25519); // Curve
        }
            
        signParams.Add(signAlgCapabilities);
        signParams.Add(signKeyCapabilities);


        final AlgorithmID ecdhAlg = AlgorithmID.ECDH_SS_HKDF_256;
        CBORObject ecdhAlgCapabilities = CBORObject.NewArray();
        CBORObject ecdhKeyCapabilities = CBORObject.NewArray();
        CBORObject ecdhParams = CBORObject.NewArray();

        // ECDSA_256
        if (ecdhKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
        ecdhAlgCapabilities.Add(KeyKeys.KeyType_EC2); // Key Type
        ecdhKeyCapabilities.Add(KeyKeys.KeyType_EC2); // Key Type
        ecdhKeyCapabilities.Add(KeyKeys.EC2_P256); // Curve
        }
            
        // X25519
        if (ecdhKeyCurve == KeyKeys.OKP_X25519.AsInt32()) {
        ecdhAlgCapabilities.Add(KeyKeys.KeyType_OKP); // Key Type
        ecdhKeyCapabilities.Add(KeyKeys.KeyType_OKP); // Key Type
        ecdhKeyCapabilities.Add(KeyKeys.OKP_X25519); // Curve
        }
            
        ecdhParams.Add(ecdhAlgCapabilities);
        ecdhParams.Add(ecdhKeyCapabilities);
        

        Assert.assertArrayEquals(masterSecret, myMap.get(CBORObject.FromObject(OSCOREInputMaterialObjectParameters.ms)).GetByteString());
        Assert.assertArrayEquals(senderId, myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.group_SenderID)).GetByteString());
       
        Assert.assertEquals(hkdf.AsCBOR(), myMap.get(CBORObject.FromObject(OSCOREInputMaterialObjectParameters.hkdf)));
        Assert.assertEquals(signEncAlg.AsCBOR(), myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.sign_enc_alg)));
        Assert.assertArrayEquals(masterSalt, myMap.get(CBORObject.FromObject(OSCOREInputMaterialObjectParameters.salt)).GetByteString());
        Assert.assertArrayEquals(groupId, myMap.get(CBORObject.FromObject(OSCOREInputMaterialObjectParameters.contextId)).GetByteString());
        Assert.assertNotNull(signAlg);
        Assert.assertEquals(signAlg.AsCBOR(), myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.sign_alg)));
        Assert.assertNotNull(ecdhAlg);
        Assert.assertEquals(ecdhAlg.AsCBOR(), myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ecdh_alg)));
        
        // Add default values for missing parameters
        if (myMap.ContainsKey(CBORObject.FromObject(OSCOREInputMaterialObjectParameters.hkdf)) == false)
            myMap.Add(OSCOREInputMaterialObjectParameters.hkdf, AlgorithmID.HKDF_HMAC_SHA_256);
        if (myMap.ContainsKey(CBORObject.FromObject(OSCOREInputMaterialObjectParameters.salt)) == false)
            myMap.Add(OSCOREInputMaterialObjectParameters.salt, CBORObject.FromObject(new byte[0]));
       
        Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.NUM)));
        Assert.assertEquals(CBORType.Integer, joinResponse.get(CBORObject.FromObject(Constants.NUM)).getType());
        // This assumes that the Group Manager did not rekeyed the group upon previous nodes' joining
        Assert.assertEquals(0, joinResponse.get(CBORObject.FromObject(Constants.NUM)).AsInt32());
        
        Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.ACE_GROUPCOMM_PROFILE)));
        Assert.assertEquals(CBORType.Integer, joinResponse.get(CBORObject.FromObject(Constants.ACE_GROUPCOMM_PROFILE)).getType());
        // Assume that "coap_group_oscore" is registered with value 0 in the "ACE Groupcomm Profile" Registry of draft-ietf-ace-key-groupcomm
        Assert.assertEquals(Constants.COAP_GROUP_OSCORE_APP, joinResponse.get(CBORObject.FromObject(Constants.ACE_GROUPCOMM_PROFILE)).AsInt32());
       
        Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.EXP)));
        Assert.assertEquals(CBORType.Integer, joinResponse.get(CBORObject.FromObject(Constants.EXP)).getType());
        Assert.assertEquals(1000000, joinResponse.get(CBORObject.FromObject(Constants.EXP)).AsInt32());
       
        if (myMap.ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.sign_params))) {
            Assert.assertEquals(CBORType.Array, myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.sign_params)).getType());
            Assert.assertEquals(CBORObject.FromObject(signParams), myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.sign_params)));
        }
        if (myMap.ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ecdh_params))) {
            Assert.assertEquals(CBORType.Array, myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ecdh_params)).getType());
            Assert.assertEquals(CBORObject.FromObject(ecdhParams), myMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ecdh_params)));
        }
        
        CBORObject pubKeysArray = null;
        if (askForPubKeys) {
            Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.PUB_KEYS)));
            Assert.assertEquals(CBORType.Array, joinResponse.get(CBORObject.FromObject(Constants.PUB_KEYS)).getType());
           
            pubKeysArray = joinResponse.get(CBORObject.FromObject(Constants.PUB_KEYS));
            Assert.assertEquals(2, pubKeysArray.size());
           
            Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.PEER_IDENTIFIERS)));
            Assert.assertEquals(CBORType.Array, joinResponse.get(CBORObject.FromObject(Constants.PEER_IDENTIFIERS)).getType());
            Assert.assertEquals(2, joinResponse.get(CBORObject.FromObject(Constants.PEER_IDENTIFIERS)).size());
            
            byte[] peerSenderId;
            OneKey peerPublicKey;
            byte[] peerSenderIdFromResponse;
           
            OneKey peerPublicKeyRetrieved = null;
            CBORObject peerPublicKeyRetrievedEncoded;
            
            peerSenderId = new byte[] { (byte) 0x77 };
            peerSenderIdFromResponse = joinResponse.
                    get(CBORObject.FromObject(Constants.PEER_IDENTIFIERS)).get(0).GetByteString();
            peerPublicKey = C3pubKey;
            Assert.assertArrayEquals(peerSenderId, peerSenderIdFromResponse);
           
            peerPublicKeyRetrievedEncoded = pubKeysArray.get(0);
	            if (peerPublicKeyRetrievedEncoded.getType() != CBORType.ByteString) {
	        	Assert.fail("Elements of the parameter 'pub_keys' must be CBOR byte strings");
	        }
	        byte[] peerPublicKeyRetrievedBytes = peerPublicKeyRetrievedEncoded.GetByteString();
	        switch (pubKeyEnc) {
	            case Constants.COSE_HEADER_PARAM_CCS:
	            	CBORObject ccs = CBORObject.DecodeFromBytes(peerPublicKeyRetrievedBytes);
	                if (ccs.getType() == CBORType.Map) {
	                	// Retrieve the public key from the CCS
	                    peerPublicKeyRetrieved = Util.ccsToOneKey(ccs);
	                }
	                else {
	                    Assert.fail("Invalid format of public key");
	                }
	                break;
	            case Constants.COSE_HEADER_PARAM_CWT:
	            	CBORObject cwt = CBORObject.DecodeFromBytes(peerPublicKeyRetrievedBytes);
	                if (cwt.getType() == CBORType.Array) {
	                    // Retrieve the public key from the CWT
	                    // TODO
	                }
	                else {
	                    Assert.fail("Invalid format of public key");
	                }
	                break;
	            case Constants.COSE_HEADER_PARAM_X5CHAIN:
	                // Retrieve the public key from the certificate
	                // TODO
	                break;
	            default:
	                Assert.fail("Invalid format of public key");
	        }
	        if (peerPublicKeyRetrieved == null)
	            Assert.fail("Invalid format of public key");
            
            // ECDSA_256
            if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
                Assert.assertEquals(KeyKeys.KeyType_EC2, peerPublicKeyRetrieved.get(KeyKeys.KeyType.AsCBOR()));
                Assert.assertEquals(KeyKeys.EC2_P256, peerPublicKeyRetrieved.get(KeyKeys.EC2_Curve.AsCBOR()));
                Assert.assertEquals(peerPublicKey.get(KeyKeys.EC2_X.AsCBOR()), peerPublicKeyRetrieved.get(KeyKeys.EC2_X.AsCBOR()));
                Assert.assertEquals(peerPublicKey.get(KeyKeys.EC2_Y.AsCBOR()), peerPublicKeyRetrieved.get(KeyKeys.EC2_Y.AsCBOR()));
            }
           
            // EDDSA (Ed25519)
            if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
                Assert.assertEquals(KeyKeys.KeyType_OKP, peerPublicKeyRetrieved.get(KeyKeys.KeyType.AsCBOR()));
                Assert.assertEquals(KeyKeys.OKP_Ed25519, peerPublicKeyRetrieved.get(KeyKeys.OKP_Curve.AsCBOR()));
                Assert.assertEquals(peerPublicKey.get(KeyKeys.OKP_Curve.AsCBOR()), peerPublicKeyRetrieved.get(KeyKeys.OKP_Curve.AsCBOR()));
                Assert.assertEquals(peerPublicKey.get(KeyKeys.OKP_X.AsCBOR()), peerPublicKeyRetrieved.get(KeyKeys.OKP_X.AsCBOR()));
            }
            
            peerSenderId = new byte[] { (byte) 0x52 };
            peerSenderIdFromResponse = joinResponse.
                    get(CBORObject.FromObject(Constants.PEER_IDENTIFIERS)).get(1).GetByteString();
            peerPublicKey = C2pubKey;
            Assert.assertArrayEquals(peerSenderId, peerSenderIdFromResponse);
           
            peerPublicKeyRetrievedEncoded = pubKeysArray.get(1);
	            if (peerPublicKeyRetrievedEncoded.getType() != CBORType.ByteString) {
	        	Assert.fail("Elements of the parameter 'pub_keys' must be CBOR byte strings");
	        }
	        peerPublicKeyRetrievedBytes = peerPublicKeyRetrievedEncoded.GetByteString();
	        switch (pubKeyEnc) {
	            case Constants.COSE_HEADER_PARAM_CCS:
	            	CBORObject ccs = CBORObject.DecodeFromBytes(peerPublicKeyRetrievedBytes);
	                if (ccs.getType() == CBORType.Map) {
	                	// Retrieve the public key from the CCS
	                    peerPublicKeyRetrieved = Util.ccsToOneKey(ccs);
	                }
	                else {
	                    Assert.fail("Invalid format of public key");
	                }
	                break;
	            case Constants.COSE_HEADER_PARAM_CWT:
	            	CBORObject cwt = CBORObject.DecodeFromBytes(peerPublicKeyRetrievedBytes);
	                if (cwt.getType() == CBORType.Array) {
	                    // Retrieve the public key from the CWT
	                    // TODO
	                }
	                else {
	                    Assert.fail("Invalid format of public key");
	                }
	                break;
	            case Constants.COSE_HEADER_PARAM_X5CHAIN:
	                // Retrieve the public key from the certificate
	                // TODO
	                break;
	            default:
	                Assert.fail("Invalid format of public key");
	        }
	        if (peerPublicKeyRetrieved == null)
	            Assert.fail("Invalid format of public key");
            
            // ECDSA_256
            if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
                Assert.assertEquals(KeyKeys.KeyType_EC2, peerPublicKeyRetrieved.get(KeyKeys.KeyType.AsCBOR()));
                Assert.assertEquals(KeyKeys.EC2_P256, peerPublicKeyRetrieved.get(KeyKeys.EC2_Curve.AsCBOR()));
                Assert.assertEquals(peerPublicKey.get(KeyKeys.EC2_X.AsCBOR()), peerPublicKeyRetrieved.get(KeyKeys.EC2_X.AsCBOR()));
                Assert.assertEquals(peerPublicKey.get(KeyKeys.EC2_Y.AsCBOR()), peerPublicKeyRetrieved.get(KeyKeys.EC2_Y.AsCBOR()));
            }
           
            // EDDSA (Ed25519)
            if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
                Assert.assertEquals(KeyKeys.KeyType_OKP, peerPublicKeyRetrieved.get(KeyKeys.KeyType.AsCBOR()));
                Assert.assertEquals(KeyKeys.OKP_Ed25519, peerPublicKeyRetrieved.get(KeyKeys.OKP_Curve.AsCBOR()));
                Assert.assertEquals(peerPublicKey.get(KeyKeys.OKP_Curve.AsCBOR()), peerPublicKeyRetrieved.get(KeyKeys.OKP_Curve.AsCBOR()));
                Assert.assertEquals(peerPublicKey.get(KeyKeys.OKP_X.AsCBOR()), peerPublicKeyRetrieved.get(KeyKeys.OKP_X.AsCBOR()));
            }

            
			Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.PEER_ROLES)));
            Assert.assertEquals(CBORType.Array, joinResponse.get(CBORObject.FromObject(Constants.PEER_ROLES)).getType());
            Assert.assertEquals(2, joinResponse.get(CBORObject.FromObject(Constants.PEER_ROLES)).size());
            
            int expectedRoles = 0;
            expectedRoles = Util.addGroupOSCORERole(expectedRoles, Constants.GROUP_OSCORE_REQUESTER);
            expectedRoles = Util.addGroupOSCORERole(expectedRoles, Constants.GROUP_OSCORE_RESPONDER);
            Assert.assertEquals(expectedRoles, joinResponse.get(CBORObject.FromObject(Constants.PEER_ROLES)).get(0).AsInt32());
            expectedRoles = 0;
            expectedRoles = Util.addGroupOSCORERole(expectedRoles, Constants.GROUP_OSCORE_REQUESTER);
            Assert.assertEquals(expectedRoles, joinResponse.get(CBORObject.FromObject(Constants.PEER_ROLES)).get(1).AsInt32());
           
        }
        else {
            Assert.assertEquals(false, joinResponse.ContainsKey(CBORObject.FromObject(Constants.PUB_KEYS)));
            Assert.assertEquals(false, joinResponse.ContainsKey(CBORObject.FromObject(Constants.PEER_ROLES)));
        }
        
		Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.GROUP_POLICIES)));
        Assert.assertEquals(3600, joinResponse.get(CBORObject.FromObject(Constants.GROUP_POLICIES)).get(CBORObject.FromObject(Constants.POLICY_KEY_CHECK_INTERVAL)).AsInt32());
        Assert.assertEquals(0, joinResponse.get(CBORObject.FromObject(Constants.GROUP_POLICIES)).get(CBORObject.FromObject(Constants.POLICY_EXP_DELTA)).AsInt32());
        */
        // DEBUG: END SET OF ASSERTIONS
        
        
        
	    // Check the proof-of-possession evidence over kdc_nonce, using the GM's public key
        Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.KDC_NONCE)));
        Assert.assertEquals(CBORType.ByteString, joinResponse.get(CBORObject.FromObject(Constants.KDC_NONCE)).getType());
        Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.KDC_CRED)));
        Assert.assertEquals(CBORType.ByteString, joinResponse.get(CBORObject.FromObject(Constants.KDC_CRED)).getType());
        Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.KDC_CRED_VERIFY)));
		Assert.assertEquals(CBORType.ByteString, joinResponse.get(CBORObject.FromObject(Constants.KDC_CRED_VERIFY)).getType());
        
	    Assert.assertEquals(true, joinResponse.ContainsKey(CBORObject.FromObject(Constants.KEY)));
        Assert.assertEquals(CBORType.Map, joinResponse.get(CBORObject.FromObject(Constants.KEY)).getType());
	    Assert.assertEquals(true, joinResponse.get(CBORObject.FromObject(Constants.KEY)).
	    		            ContainsKey(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.pub_key_enc)));
        Assert.assertEquals(CBORType.Map, joinResponse.get(CBORObject.FromObject(Constants.KEY)).getType());
        Assert.assertEquals(CBORType.Integer, joinResponse.get(CBORObject.FromObject(Constants.KEY)).
        					get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.pub_key_enc)).getType());
        pubKeyEnc = joinResponse.get(CBORObject.FromObject(Constants.KEY)).
							get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.pub_key_enc)).AsInt32();
	    
        OneKey gmPublicKeyRetrieved = null;
        byte[] kdcCredBytes = joinResponse.get(CBORObject.FromObject(Constants.KDC_CRED)).GetByteString();
        switch (pubKeyEnc) {
            case Constants.COSE_HEADER_PARAM_CCS:
                CBORObject ccs = CBORObject.DecodeFromBytes(kdcCredBytes);
                if (ccs.getType() == CBORType.Map) {
                    // Retrieve the public key from the CCS
                    gmPublicKeyRetrieved = AceUtil.ccsToOneKey(ccs);
                }
                else {
                    Assert.fail("Invalid format of Group Manager public key");
                }
                break;
            case Constants.COSE_HEADER_PARAM_CWT:
                CBORObject cwt = CBORObject.DecodeFromBytes(kdcCredBytes);
                if (cwt.getType() == CBORType.Array) {
                    // Retrieve the public key from the CWT
                    // TODO
                }
                else {
                    Assert.fail("Invalid format of Group Manager public key");
                }
                break;
            case Constants.COSE_HEADER_PARAM_X5CHAIN:
                // Retrieve the public key from the certificate
                // TODO
                break;
            default:
                Assert.fail("Invalid format of Group Manager public key");
        }
        if (gmPublicKeyRetrieved == null)
        	Assert.fail("Invalid format of Group Manager public key");
        
        PublicKey gmPublicKey = gmPublicKeyRetrieved.AsPublicKey();
	
	    byte[] gmNonce = joinResponse.get(CBORObject.FromObject(Constants.KDC_NONCE)).GetByteString();
		
	    CBORObject gmPopEvidence = joinResponse.get(CBORObject.FromObject(Constants.KDC_CRED_VERIFY));
	    byte[] rawGmPopEvidence = gmPopEvidence.GetByteString();
        
        // Invalid Client's PoP signature
        if (!AceUtil.verifySignature(signKeyCurve, gmPublicKey, gmNonce, rawGmPopEvidence)) {
        	Assert.fail("Invalid GM's PoP evidence");
        }
        
        // Final join response parsing and Group Context generation
        
        // Print the join response
        Util.printJoinResponse(joinResponse);
        
        // Pause if this is for server1
        if(!memberName.toLowerCase().contains("server1")) {
        	System.out.println("Has now joined the OSCORE group.");
        } else {
        	printPause(memberName, "Has now joined the OSCORE group.");
        }
        
        MultiKey clientKey = new MultiKey(encodedPublicKey, cKeyPair.get(KeyKeys.OKP_D).GetByteString());
        GroupCtx groupOscoreCtx = Util.generateGroupOSCOREContext(joinResponse, clientKey);

        return groupOscoreCtx;
    }
	
	/**
	 * Post to Authz-Info, then perform join request using multiple roles.
     * Uses the ACE OSCORE Profile.
	 * 
	 * @deprecated
	 * @param memberName
	 * @param group
	 * @param publicPrivateKey
	 * @throws IllegalStateException
	 * @throws InvalidCipherTextException
	 * @throws CoseException
	 * @throws AceException
	 * @throws OSException
	 * @throws ConnectorException
	 * @throws IOException
	 */
	public static GroupCtx postTokenAndJoinOld(String memberName, String group, String publicPrivateKey,
			Response responseFromAS) throws IllegalStateException, InvalidCipherTextException, CoseException,
			AceException, OSException, ConnectorException, IOException {

        /* Configure parameters for the join request */

        boolean askForSignInfo = true;
        boolean askForPubKeys = true;
        boolean providePublicKey = true;

        // Generate private and public key to be used in the OSCORE group by the joining client (EDDSA)
        InstallCryptoProviders.installProvider();
        String groupKeyPair = publicPrivateKey;// = InstallCryptoProviders.getCounterSignKey(); //"pQMnAQEgBiFYIAaekSuDljrMWUG2NUaGfewQbluQUfLuFPO8XMlhrNQ6I1ggZHFNQaJAth2NgjUCcXqwiMn0r2/JhEVT5K1MQsxzUjk=";

        // Set EDDSA with curve Ed25519 for countersignatures
        int countersignKeyCurve = KeyKeys.OKP_Ed25519.AsInt32();

//        // The cnf key used in these tests
//        byte[] keyCnf = {'a', 'b', 'c', 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
//
//        //The AS <-> RS key used in these tests
//        byte[] keyASRS = {'c', 'b', 'c', 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        
        String groupName = group; //"bbbbbb570000";
//        String audience = "rs2";
//        String asName = "AS";
//        String clientID = memberName; //"clientA";
//        String cti = "token4JoinMultipleRolesDeriv" + clientID;

        String gmBaseURI = "coap://" + GM_HOST + ":" + GM_PORT + "/";
        String authzInfoURI = gmBaseURI + "authz-info";
        String joinResourceURI = gmBaseURI + rootGroupMembershipResource + "/" + groupName;

        System.out.println("Performing Token post to GM followed by Join request.");
        System.out.println("GM join resource is at: " + joinResourceURI);

        /* Prepare ACE Token generated by the client */

//        //Generate a token (update to get Token from AS here instead)
//        COSEparams coseP = new COSEparams(MessageTag.Encrypt0, AlgorithmID.AES_CCM_16_128_128, AlgorithmID.Direct);
//        CwtCryptoCtx ctx = CwtCryptoCtx.encrypt0(keyASRS, coseP.getAlg().AsCBOR());
//        Map<Short, CBORObject> params = new HashMap<>(); 
//
        //Create a byte string scope for use later
        CBORObject cborArrayScope = CBORObject.NewArray();
        CBORObject cborArrayEntry = CBORObject.NewArray();
        cborArrayEntry.Add(groupName);

        int myRoles = 0;
        myRoles = se.sics.ace.Util.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_REQUESTER);
        myRoles = se.sics.ace.Util.addGroupOSCORERole(myRoles, Constants.GROUP_OSCORE_RESPONDER);
        cborArrayEntry.Add(myRoles);

        cborArrayScope.Add(cborArrayEntry);
        byte[] byteStringScope = cborArrayScope.EncodeToBytes();

//        params.put(Constants.SCOPE, CBORObject.FromObject(byteStringScope));
//        params.put(Constants.AUD, CBORObject.FromObject(audience));
//        params.put(Constants.CTI, CBORObject.FromObject(cti.getBytes(Constants.charset))); //Need different CTIs
//        params.put(Constants.ISS, CBORObject.FromObject(asName));
//
//        CBORObject osc = CBORObject.NewMap();
//        byte[] clientId = clientID.getBytes(Constants.charset); //Need different client IDs
//        osc.Add(Constants.OS_CLIENTID, clientId);
//        osc.Add(Constants.OS_MS, keyCnf);
//        byte[] serverId = audience.getBytes(Constants.charset);
//        osc.Add(Constants.OS_SERVERID, serverId);
//
//        CBORObject cnf = CBORObject.NewMap();
//        cnf.Add(Constants.OSCORE_Security_Context, osc);
//        params.put(Constants.CNF, cnf);
//        CWT token = new CWT(params);
//        CBORObject payload = CBORObject.NewMap();
//        payload.Add(Constants.ACCESS_TOKEN, token.encode(ctx));
//        payload.Add(Constants.CNF, cnf);
//        Response asRes = new Response(CoAP.ResponseCode.CREATED);
//        asRes.setPayload(payload.EncodeToBytes());

        /* Post Token to GM */

        CBORObject res = CBORObject.DecodeFromBytes(responseFromAS.getPayload());
        System.out.println("Performing Token request to GM. Response from AS was: " + res.toString());

        boolean askForEcdhInfo = true;
        Response rsRes = OSCOREProfileRequestsGroupOSCORE.postToken(authzInfoURI, responseFromAS, askForSignInfo,
                askForEcdhInfo, db, usedRecipientIds);
        /* Check response from GM to Token post */

        assert(rsRes.getCode().equals(CoAP.ResponseCode.CREATED));
        //Check that the OSCORE context has been created:
        Assert.assertNotNull(db.getContext(joinResourceURI));

        CBORObject rsPayload = CBORObject.DecodeFromBytes(rsRes.getPayload());

        System.out.println("Receved response from GM to Token post: " + rsPayload.toString());

        // Sanity checks already occurred in OSCOREProfileRequestsGroupOSCORE.postToken()

        // Nonce from the GM, to be signed together with a local nonce to prove PoP of the private key
        byte[] gm_sign_nonce = rsPayload.get(CBORObject.FromObject(Constants.KDCCHALLENGE)).GetByteString();

        @SuppressWarnings("unused")
        CBORObject signInfo = null;
        @SuppressWarnings("unused")
        CBORObject pubKeyEnc = null;

        if (askForSignInfo) {
            signInfo = rsPayload.get(CBORObject.FromObject(Constants.SIGN_INFO));
        }

        /* Now proceed to build join request to GM */

        CoapClient c = OSCOREProfileRequestsGroupOSCORE
                .getClient(new InetSocketAddress(joinResourceURI, GM_PORT),
                db);

        CBORObject requestPayload = CBORObject.NewMap();

        requestPayload.Add(Constants.SCOPE, CBORObject.FromObject(byteStringScope));

        if (askForPubKeys) {
            CBORObject getPubKeys = CBORObject.NewArray();
            requestPayload.Add(Constants.GET_PUB_KEYS, getPubKeys);
        }

        if (providePublicKey) {

            // For the time being, the client's public key can be only a COSE Key
            OneKey publicKey = new OneKey(CBORObject.DecodeFromBytes(Base64.getDecoder().decode(groupKeyPair))).PublicKey();

            requestPayload.Add(Constants.CLIENT_CRED, publicKey.AsCBOR().EncodeToBytes());

            // Add the nonce for PoP of the Client's private key
            byte[] cnonce = new byte[8];
            new SecureRandom().nextBytes(cnonce);
            requestPayload.Add(Constants.CNONCE, cnonce);

            // Add the signature computed over (rsnonce | cnonce), using the Client's private key
            PrivateKey privKey = (new OneKey(CBORObject.DecodeFromBytes(Base64.getDecoder().decode(groupKeyPair)))).AsPrivateKey();
            byte [] dataToSign = new byte [gm_sign_nonce.length + cnonce.length];
            System.arraycopy(gm_sign_nonce, 0, dataToSign, 0, gm_sign_nonce.length);
            System.arraycopy(cnonce, 0, dataToSign, gm_sign_nonce.length, cnonce.length);

            byte[] clientSignature = Util.computeSignature(privKey, dataToSign, countersignKeyCurve);

            if (clientSignature != null)
                requestPayload.Add(Constants.CLIENT_CRED_VERIFY, clientSignature);
            else
                Assert.fail("Computed signature is empty");

        }

        OSCoreCtx tmp = db.getContext(gmBaseURI);
        System.out.println("Client: Installing Security Context with Recipient ID: " + tmp.getRecipientIdString()
                + " Sender ID: " + tmp.getSenderIdString()
                + " ID Context: " + Utility.arrayToString(tmp.getIdContext()) + "\r\n");
        Request joinReq = new Request(Code.POST, Type.CON);
        joinReq.getOptions().setOscore(new byte[0]);
        joinReq.setPayload(requestPayload.EncodeToBytes());
        joinReq.getOptions().setContentFormat(MediaTypeRegistry.APPLICATION_CBOR);

        /* Send to join request to GM */

        System.out.println("Performing Join request to GM: " + requestPayload.toString());
        CoapResponse r2 = c.advanced(joinReq);

        /* Parse response to Join request from GM */

        System.out.println("Response from GM to Join request: " + r2.getResponseText());
        byte[] responsePayload = r2.getPayload();
        CBORObject joinResponse = CBORObject.DecodeFromBytes(responsePayload);

        CBORObject keyMap = joinResponse.get(CBORObject.FromObject(Constants.KEY));

        //The following two lines are useful for generating the Group OSCORE context
		Map<Short, CBORObject> contextParams = new HashMap<>(
				GroupOSCOREInputMaterialObjectParameters.getParams(keyMap));
		GroupOSCOREInputMaterialObject contextObject = new GroupOSCOREInputMaterialObject(contextParams);

        System.out.println("Received response from GM to Join request: " + joinResponse.toString());

        /* Parse the Join response in detail */

        Util.printJoinResponse(joinResponse);
        
        if(!memberName.toLowerCase().contains("server1")) {
        	System.out.println("Has now joined the OSCORE group.");
        } else {
        	printPause(memberName, "Has now joined the OSCORE group.");
        }
        
        /* Generate a Group OSCORE security context from the Join response */

        CBORObject coseKeySetArray = CBORObject.NewArray();
        if(joinResponse.ContainsKey(CBORObject.FromObject(Constants.PUB_KEYS))) {
        	byte[] coseKeySetByte = joinResponse.get(CBORObject.FromObject(Constants.PUB_KEYS)).GetByteString();
        	coseKeySetArray = CBORObject.DecodeFromBytes(coseKeySetByte);
        }
        
		GroupCtx groupOscoreCtx = Util.generateGroupOSCOREContextOld(contextObject, coseKeySetArray, groupKeyPair);

        System.out.println();
        //System.out.println("Generated Group OSCORE Context:");
        Utility.printContextInfo(groupOscoreCtx);

        return groupOscoreCtx;
    }
    
    /**
     * Simple method for "press enter to continue" functionality
     */
    static void printPause(String memberName, String message) {
    	
    	//Only print for Server1
    	if(!memberName.toLowerCase().equals("server1")) {
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
    
    private static void printMapPayload(CBORObject obj) throws Exception {
        if (obj != null) {
        	System.out.println("*** Map Payload *** ");
        	System.out.println(obj);
        } else {
        	System.out.println("*** The payload argument is null!");
        }
    }
    
    private static void printResponseFromRS(Response res) throws Exception {
        if (res != null) {
        	System.out.println("*** Response from the RS *** ");
            System.out.print(res.getCode().codeClass + ".0" + res.getCode().codeDetail);
            System.out.println(" " + res.getCode().name());

            if (res.getPayload() != null) {
            	
            	if (res.getOptions().getContentFormat() == Constants.APPLICATION_ACE_CBOR ||
            		res.getOptions().getContentFormat() == Constants.APPLICATION_ACE_GROUPCOMM_CBOR) {
		        	CBORObject resCBOR = CBORObject.DecodeFromBytes(res.getPayload());
		            System.out.println(resCBOR.toString());
            	}
            	else {
		            System.out.println(new String(res.getPayload()));
            	}
            }
        } else {
        	System.out.println("*** The response from the RS is null!");
            System.out.print("No response received");
        }
    }
    
 // ---

 	/**
 	 * @param str the hex string
 	 * @return the byte array
 	 * @str the hexadecimal string to be converted into a byte array
 	 * 
 	 *      Return the byte array representation of the original string
 	 */
 	public static byte[] hexStringToByteArray(final String str) {
 		int len = str.length();
 		byte[] data = new byte[len / 2];

 		// Big-endian
 		for (int i = 0; i < len; i += 2) {
 			data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
 			data[i / 2] = (byte) (data[i / 2] & 0xFF);
 		}

 		// Little-endian
 		/*
 		 * for (int i = 0; i < len; i += 2) { data[i / 2] = (byte)
 		 * ((Character.digit(str.charAt(len - 2 - i), 16) << 4) +
 		 * Character.digit(str.charAt(len - 1 - i), 16)); data[i / 2] = (byte)
 		 * (data[i / 2] & 0xFF); }
 		 */

 		return data;

 	}
}
