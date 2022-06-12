/*******************************************************************************
 * Copyright (c) 2019, RISE AB
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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.log4j.BasicConfigurator;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.oscore.OSCoreCoapStackFactory;
import org.junit.Assert;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;

import org.eclipse.californium.cose.AlgorithmID;
import org.eclipse.californium.cose.CoseException;
import org.eclipse.californium.cose.KeyKeys;
import org.eclipse.californium.cose.MessageTag;
import org.eclipse.californium.cose.OneKey;
import se.sics.ace.AceException;
import se.sics.ace.COSEparams;
import se.sics.ace.Constants;
import se.sics.ace.Util;
import se.sics.ace.coap.CoapReq;
import se.sics.ace.coap.rs.CoapAuthzInfo;
import se.sics.ace.coap.rs.CoapDeliverer;
import se.sics.ace.coap.rs.oscoreProfile.OscoreCtxDbSingleton;
import se.sics.ace.cwt.CWT;
import se.sics.ace.cwt.CwtCryptoCtx;
import se.sics.ace.examples.KissTime;
import se.sics.ace.examples.LocalMessage;
import se.sics.ace.oscore.GroupInfo;
import se.sics.ace.oscore.GroupOSCOREInputMaterialObjectParameters;
import se.sics.ace.oscore.OSCOREInputMaterialObjectParameters;
import se.sics.ace.oscore.rs.GroupOSCOREJoinValidator;
import se.sics.ace.oscore.rs.OscoreAuthzInfoGroupOSCORE;
import se.sics.ace.rs.AsRequestCreationHints;
import se.sics.ace.rs.TokenRepository;
import se.sics.prototype.support.KeyStorage;

/**
 * Server for testing the DTLSProfileDeliverer class. 
 * 
 * The Junit tests are in TestDtlspClientGroupOSCORE, 
 * which will automatically start this server.
 * 
 * @author Marco Tiloca
 *
 */
public class OscoreRsServer {

	// For old tests - PSK to encrypt the token (used for both audiences rs1 and rs2)
    private static byte[] key128_token = {(byte)0xa1, (byte)0xa2, (byte)0xa3, 0x04, 
            0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
            0x10};
    
    private final static String rootGroupMembershipResource = "ace-group";
    
 // Up to 4 bytes, same for all the OSCORE Group of the Group Manager
	private final static int groupIdPrefixSize = 4;
	
	// Initial part of the node name for monitors, since they do not have a Sender ID
	private final static String prefixMonitorNames = "M";
	
	// For non-monitor members, separator between the two components of the node name
	private final static String nodeNameSeparator = "-";
	
	private static Set<Integer> validRoleCombinations = new HashSet<Integer>();
	
	static Map<String, GroupInfo> activeGroups = new HashMap<>();
	
	private static int portNumberNoSec = CoAP.DEFAULT_COAP_PORT + 100;
	
	// Sender ID 0x52 for an already present group member
	private static final byte[] idClient2 = new byte[] { (byte) 0x52 };
	
	// Sender ID 0x77 for an already present group member
	private static final byte[] idClient3 = new byte[] { (byte) 0x77 };
	
	static String testpath = "temp-";
	
	static Random rand;
	
    /**
     * Definition of the Hello-World Resource
     */
    public static class HelloWorldResource extends CoapResource {
        
        /**
         * Constructor
         */
        public HelloWorldResource() {
            
            // set resource identifier
            super("helloWorld");
            
            // set display name
            getAttributes().setTitle("Hello-World Resource");
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            
            // respond to the request
            exchange.respond("Hello World!");
        }
    }
    
    /**
     * Definition of the Temp Resource
     */
    public static class TempResource extends CoapResource {
        
        /**
         * Constructor
         */
        public TempResource() {
            
            // set resource identifier
            super("temp");
            
            // set display name
            getAttributes().setTitle("Temp Resource");
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            
            // respond to the request
            exchange.respond("19.0 C");
        }
    }
    
    /**
     * Definition of the root group-membership resource for Group OSCORE
     * 
     * Children of this resource are the group-membership resources
     */
    public static class GroupOSCORERootMembershipResource extends CoapResource {
        
        /**
         * Constructor
         * @param resId  the resource identifier
         */
        public GroupOSCORERootMembershipResource(String resId) {
            
            // set resource identifier
            super(resId);
            
            // set display name
            getAttributes().setTitle("Group OSCORE Group-Membership Resource " + resId);
        }
        
        @Override
        public void handleFETCH(CoapExchange exchange) {
        	System.out.println("FETCH request reached the GM");
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen,
            	// due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
            
        	byte[] requestPayload = exchange.getRequestPayload();
        	
        	if(requestPayload == null) {
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
        						 "A payload must be present");
        		return;
        	}
        	
        	CBORObject requestCBOR = CBORObject.DecodeFromBytes(requestPayload);
			
        	// The payload of the request must be a CBOR Map
        	if (!requestCBOR.getType().equals(CBORType.Map)) {
				exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
								 "Invalid payload format");
	    		return;
        	}
        	
        	// The CBOR Map must include exactly one element, i.e. 'gid'
        	if ((requestCBOR.size() != 1) || (!requestCBOR.ContainsKey(Constants.GID))) {
				exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
								 "Invalid payload format");
	    		return;
        	}
        	
        	// The 'gid' element must be a CBOR array, with at least one element
        	if (requestCBOR.get(Constants.GID).getType() != CBORType.Array ||
        		requestCBOR.get(Constants.GID).size() == 0) {
				exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
								 "Invalid payload format");
	    		return;
        	}
        	
        	// Each element of 'gid' element must be a CBOR byte string
        	for (int i = 0 ; i < requestCBOR.get(Constants.GID).size(); i++) {
	        	if (requestCBOR.get(Constants.GID).get(i).getType() != CBORType.ByteString) {
					exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
									 "Invalid payload format");
		    		return;
	        	}
        	}
        	    		
    		List<CBORObject> inputGroupIds = new ArrayList<CBORObject> ();
    		for (int i = 0; i < requestCBOR.get(Constants.GID).size(); i++) {
    			inputGroupIds.add(requestCBOR.get(Constants.GID).get(i));
    		}
    		
    		List<String> preliminaryGroupNames = new ArrayList<String>();
    		List<CBORObject> finalGroupNames = new ArrayList<CBORObject>();
    		List<CBORObject> finalGroupIds = new ArrayList<CBORObject>();
    		List<CBORObject> finalGroupURIs = new ArrayList<CBORObject>();
    		
    		// Navigate the list of existing OSCORE groups
        	for (String groupName : activeGroups.keySet()) {
        		
        		GroupInfo myGroup = activeGroups.get(groupName);
        		byte[] storedGid = myGroup.getGroupId();
        		
        		// Navigate the list of Group IDs specified in the request
        		for (int i = 0; i < inputGroupIds.size(); i ++) {
        			byte[] inputGid = inputGroupIds.get(i).GetByteString();
        			
        			// A match is found with the examined OSCORE group
        			if (Arrays.equals(storedGid, inputGid)) {
            			// Store the used Group Name for future inspection
        				preliminaryGroupNames.add(groupName);
        				// No need to further consider this Group ID value
        				inputGroupIds.remove(i);
        				break;
        			}
        		}
        		
        		if (inputGroupIds.isEmpty())
        			break;
        		
        	}
    		
        	// Selects only names of groups where the requesting client is
        	// a current member or is authorized to have any role about
        	for (String groupName : preliminaryGroupNames) {
        		
        		GroupInfo targetedGroup = activeGroups.get(groupName);
        		
            	if (!targetedGroup.isGroupMember(subject)) {
            		
            		// The requester is not a current group member.
            		//
            		// This is still fine, as long as at least one Access Token allows
            		// the requesting client to have any role with respect to the group
            		
            		if (getRolesFromToken(subject, groupName) == null) {
            	    	// No Access Token allows the requesting client node to have
            	    	// to have any role with respect to the group
            			
            			// Move to considering the next group
            			continue;
            		}
                	
            	}
            	
            	finalGroupNames.add(CBORObject.FromObject(groupName));
            	byte[] gid = targetedGroup.getGroupId();
            	finalGroupIds.add(CBORObject.FromObject(gid));
            	finalGroupURIs.add(CBORObject.FromObject(this.getURI() + "/" + groupName));
            	
        	}
        	
            
            // Respond to the Group Name and URI Retrieval Request
            
        	byte[] responsePayload = null;
        	Response coapResponse = new Response(CoAP.ResponseCode.CONTENT);
        	
        	// The response is an empty CBOR byte string
        	if (finalGroupNames.size() == 0) {
        		byte[] emptyArray = new byte[0];
        		responsePayload = CBORObject.FromObject(emptyArray).EncodeToBytes();
        	}
        	// The response is a CBOR may including three CBOR arrays
        	else {
        		CBORObject myResponse = CBORObject.NewMap();

        		CBORObject gnameArray = CBORObject.NewArray();
        		CBORObject gidArray = CBORObject.NewArray();
        		CBORObject guriArray = CBORObject.NewArray();
        		
        		for (int i = 0; i < finalGroupNames.size(); i++) {
        			gnameArray.Add(finalGroupNames.get(i));
        			gidArray.Add(finalGroupIds.get(i));
        			guriArray.Add(finalGroupURIs.get(i));
        		}
        		
        		myResponse.Add(Constants.GID, gidArray);
        		myResponse.Add(Constants.GNAME, gnameArray);
        		myResponse.Add(Constants.GURI, guriArray);
        		
        		responsePayload = myResponse.EncodeToBytes();
            	coapResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);
        	} 
            
        	coapResponse.setPayload(responsePayload);

        	exchange.respond(coapResponse);
        	
        }
        
    }
    
    /**
     * Definition of the group-membership resource for Group OSCORE
     */
    public static class GroupOSCOREJoinResource extends CoapResource {
        
    	private Set<Integer> validRoleCombinations = new HashSet<Integer>();
    	
        /**
         * Constructor
         * @param resId  the resource identifier
         */
        public GroupOSCOREJoinResource(String resId) {
            
            // set resource identifier
            super(resId);
            
            // set display name
            getAttributes().setTitle("Group OSCORE Group-Membership Resource " + resId);
            
            // Set the valid combinations of roles in a Joining Request
            // Combinations are expressed with the AIF specific data model AIF-OSCORE-GROUPCOMM
            validRoleCombinations.add(1 << Constants.GROUP_OSCORE_REQUESTER); // Requester (2)
            validRoleCombinations.add(1 << Constants.GROUP_OSCORE_RESPONDER); // Responder (4)
            validRoleCombinations.add(1 << Constants.GROUP_OSCORE_MONITOR); // Monitor (8)
            validRoleCombinations.add((1 << Constants.GROUP_OSCORE_REQUESTER) +
            		                  (1 << Constants.GROUP_OSCORE_RESPONDER)); // Requester+Responder (6)

        }
        
        @Override
        public void handleGET(CoapExchange exchange) {
        	System.out.println("GET request reached the GM");
        	
        	// Retrieve the entry for the target group, using the last path segment
        	// of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getName());
        	
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	String groupName = targetedGroup.getGroupName();
        	
        	// This should never happen if active groups are maintained properly
  	  		if (!groupName.equals(this.getName())) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
  				return;
  			}  
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen, due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
        	
        	if (!targetedGroup.isGroupMember(subject)) {	
        		// The requester is not a current group member.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to group members");
        		return;
        	}
                        	
        	// Respond to the Key Distribution Request
            
        	CBORObject myResponse = CBORObject.NewMap();
        	
        	// Key Type Value assigned to the Group_OSCORE_Input_Material object.
        	myResponse.Add(Constants.GKTY, CBORObject.FromObject(Constants.GROUP_OSCORE_INPUT_MATERIAL_OBJECT));
        	
        	// This map is filled as the Group_OSCORE_Input_Material object,
        	// as defined in draft-ace-key-groupcomm-oscore
        	CBORObject myMap = CBORObject.NewMap();
        	
        	// Fill the 'key' parameter
        	// Note that no Sender ID is included
        	myMap.Add(OSCOREInputMaterialObjectParameters.hkdf, targetedGroup.getHkdf().AsCBOR());
        	myMap.Add(OSCOREInputMaterialObjectParameters.salt, targetedGroup.getMasterSalt());
        	myMap.Add(OSCOREInputMaterialObjectParameters.ms, targetedGroup.getMasterSecret());
        	myMap.Add(OSCOREInputMaterialObjectParameters.contextId, targetedGroup.getGroupId());
        	myMap.Add(GroupOSCOREInputMaterialObjectParameters.pub_key_enc, targetedGroup.getPubKeyEnc());
        	if (targetedGroup.getMode() != Constants.GROUP_OSCORE_PAIRWISE_MODE_ONLY) {
        	    // The group mode is used
        	    myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_enc_alg, targetedGroup.getSignEncAlg().AsCBOR());
        	    myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_alg, targetedGroup.getSignAlg().AsCBOR());
        	    if (targetedGroup.getSignParams().size() != 0)
        	        myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_params, targetedGroup.getSignParams());
        	}
        	if (targetedGroup.getMode() != Constants.GROUP_OSCORE_GROUP_MODE_ONLY) {
        	    // The pairwise mode is used
        	    myMap.Add(OSCOREInputMaterialObjectParameters.alg, targetedGroup.getAlg().AsCBOR());
        	    myMap.Add(GroupOSCOREInputMaterialObjectParameters.ecdh_alg, targetedGroup.getEcdhAlg().AsCBOR());
        	    if (targetedGroup.getEcdhParams().size() != 0)
        	        myMap.Add(GroupOSCOREInputMaterialObjectParameters.ecdh_params, targetedGroup.getEcdhParams());
        	}
        	myResponse.Add(Constants.KEY, myMap);
        	
        	// The current version of the symmetric keying material
        	myResponse.Add(Constants.NUM, CBORObject.FromObject(targetedGroup.getVersion()));
        	
        	// CBOR Value assigned to the coap_group_oscore profile.
        	myResponse.Add(Constants.ACE_GROUPCOMM_PROFILE, CBORObject.FromObject(Constants.COAP_GROUP_OSCORE_APP));
        	
        	// Expiration time in seconds, after which the OSCORE Security Context
        	// derived from the 'k' parameter is not valid anymore.
        	myResponse.Add(Constants.EXP, CBORObject.FromObject(1000000));

        	byte[] responsePayload = myResponse.EncodeToBytes();
        	
        	Response coapResponse = new Response(CoAP.ResponseCode.CONTENT);
        	coapResponse.setPayload(responsePayload);
        	coapResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);

        	exchange.respond(coapResponse);

        }

        @Override
        public void handlePOST(CoapExchange exchange) {
        	
        	Set<String> roles = new HashSet<>();
        	boolean providePublicKeys = false;
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen, due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
            
            String rsNonceString = TokenRepository.getInstance().getRsnonce(subject);
                        
            if(rsNonceString == null) {
            	// Return an error response, with a new nonce for PoP
            	// of the Client's private key in the next Join Request
        	    CBORObject responseMap = CBORObject.NewMap();
                byte[] rsnonce = new byte[8];
                new SecureRandom().nextBytes(rsnonce);
                responseMap.Add(Constants.KDCCHALLENGE, rsnonce);
                TokenRepository.getInstance().setRsnonce(subject, Base64.getEncoder().encodeToString(rsnonce));
                byte[] responsePayload = responseMap.EncodeToBytes();
            	exchange.respond(CoAP.ResponseCode.BAD_REQUEST, responsePayload, Constants.APPLICATION_ACE_CBOR);
            	return;
            }
            
            byte[] rsnonce = Base64.getDecoder().decode(rsNonceString);
            
        	byte[] requestPayload = exchange.getRequestPayload();
        	
        	if(requestPayload == null) {
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "A payload must be present");
        		return;
        	}
        	
        	CBORObject joinRequest = CBORObject.DecodeFromBytes(requestPayload);
            
			CBORObject errorResponseMap = CBORObject.NewMap();
        	
        	// Prepare the 'sign_info' and 'ecdh_info' parameter,
			// to possibly return it in a 4.00 (Bad Request) response        	
    		CBORObject signInfo = CBORObject.NewArray();
        	CBORObject ecdhInfo = CBORObject.NewArray();
				
        	// Retrieve the entry for the target group, using the last path segment
        	// of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getName());
			
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	if (!targetedGroup.getStatus()) {
        		// The group is currently inactive and no new members are admitted
        		exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
        						 "The OSCORE group is currently not active");
            	return;
        	}
        	
        	// The group mode is used
        	if (targetedGroup.getMode() != Constants.GROUP_OSCORE_PAIRWISE_MODE_ONLY) {
				CBORObject signInfoEntry = CBORObject.NewArray();
				signInfoEntry.Add(CBORObject.FromObject(targetedGroup.getGroupName())); // 'id' element
				signInfoEntry.Add(targetedGroup.getSignAlg().AsCBOR()); // 'sign_alg' element
				
				// 'sign_parameters' element (The algorithm capabilities)
		    	CBORObject arrayElem = targetedGroup.getSignParams().get(0);
		    	if (arrayElem == null)
		    		signInfoEntry.Add(CBORObject.Null);
		    	else
		    		signInfoEntry.Add(arrayElem);
		    	
		    	// 'sign_key_parameters' element (The key type capabilities)
		    	arrayElem = targetedGroup.getSignParams().get(1);
		    	if (arrayElem == null)
		    		signInfoEntry.Add(CBORObject.Null);
		    	else
		    		signInfoEntry.Add(arrayElem);
		    	
		    	// 'pub_key_enc' element
		    	signInfoEntry.Add(targetedGroup.getPubKeyEnc());
			    signInfo.Add(signInfoEntry);
			    errorResponseMap.Add(Constants.SIGN_INFO, signInfo);
        	}
        	
        	// The pairwise mode is used
        	if (targetedGroup.getMode() != Constants.GROUP_OSCORE_GROUP_MODE_ONLY) {
				CBORObject ecdhInfoEntry = CBORObject.NewArray();
				ecdhInfoEntry.Add(CBORObject.FromObject(targetedGroup.getGroupName())); // 'id' element
				ecdhInfoEntry.Add(targetedGroup.getEcdhAlg().AsCBOR()); // 'ecdh_alg' element
				
				// 'ecdh_parameters' element (The algorithm capabilities)
		    	CBORObject arrayElem = targetedGroup.getEcdhParams().get(0);
		    	if (arrayElem == null)
		    		ecdhInfoEntry.Add(CBORObject.Null);
		    	else
		    		ecdhInfoEntry.Add(arrayElem);
		    	
		    	// 'ecdh_key_parameters' element (The key type capabilities)
		    	arrayElem = targetedGroup.getEcdhParams().get(1);
		    	if (arrayElem == null)
		    		ecdhInfoEntry.Add(CBORObject.Null);
		    	else
		    		ecdhInfoEntry.Add(arrayElem);
		    	
		    	// 'pub_key_enc' element
		    	ecdhInfoEntry.Add(targetedGroup.getPubKeyEnc());
			    ecdhInfo.Add(ecdhInfoEntry);
			    errorResponseMap.Add(Constants.ECDH_INFO, ecdhInfo);
        	}
        	
        	// The payload of the join request must be a CBOR Map
        	if (!joinRequest.getType().equals(CBORType.Map)) {
        		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
        					 	 Constants.APPLICATION_ACE_CBOR);
        		return;
        	}
        		
        	// More steps follow:
        	//
        	// Retrieve 'scope' from the map; check the GroupID against the name of the resource, just for consistency.
        	//
        	// Retrieve the role(s) to possibly reduce the set of material to provide to the joining node.
        	//
        	// Any other check is performed through the method canAccess() of the TokenRepository, which is
        	// in turn invoked by the deliverRequest() method of CoapDeliverer, upon getting the join request.
        	// The actual checks of legitimate access are performed by scopeMatchResource() and scopeMatch()
        	// of the GroupOSCOREJoinValidator used as Scope/Audience Validator.
        	
        	// Retrieve scope
        	CBORObject scope = joinRequest.get(CBORObject.FromObject(Constants.SCOPE));
        	
        	// Scope must be included for joining OSCORE groups
        	if (scope == null) {
        		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
        						 Constants.APPLICATION_ACE_CBOR);
        		return;
        	}

        	// Scope must be wrapped in a binary string for joining OSCORE groups
        	if (!scope.getType().equals(CBORType.ByteString)) {
        		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
        						 Constants.APPLICATION_ACE_CBOR);
        		return;
            }
        	
        	byte[] rawScope = scope.GetByteString();
        	CBORObject cborScope = CBORObject.DecodeFromBytes(rawScope);
        	
        	// Invalid scope format for joining OSCORE groups
        	if (!cborScope.getType().equals(CBORType.Array)) {
        		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
        						 Constants.APPLICATION_ACE_CBOR);
        		return;
            }
        	
        	// Invalid scope format for joining OSCORE groups
        	if (cborScope.size() != 2) {
        		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
        						 Constants.APPLICATION_ACE_CBOR);
        		return;
            }
        	
        	// Retrieve the name of the OSCORE group
        	String groupName;
      	  	CBORObject scopeElement = cborScope.get(0);
      	  	if (scopeElement.getType().equals(CBORType.TextString)) {
      	  	groupName = scopeElement.AsString();

      	  		// The group name in 'scope' is not pertinent for this group-membership resource
          	  	if (!groupName.equals(this.getName())) {
            		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
	  				exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
	  								 Constants.APPLICATION_ACE_CBOR);
	  				return;
	  			}      	  		
      	  	}
      	  	// Invalid scope format for joining OSCORE groups
      	  	else {
        		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
      	  		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
      	  						 Constants.APPLICATION_ACE_CBOR);
        		return;
      	  	}
      	  	
      	  	// Retrieve the role or list of roles
      	  	scopeElement = cborScope.get(1);
      	  	
      	  	int roleSet = 0;
      	  	
          	// NEW VERSION USING the AIF-BASED ENCODING AS SINGLE INTEGER
        	if (scopeElement.getType().equals(CBORType.Integer)) {
        		roleSet = scopeElement.AsInt32();
        		
        		// Invalid format of roles
        		if (roleSet < 0) {
            		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
      	  			exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
      	  							 Constants.APPLICATION_ACE_CBOR);
	        		return;
        		}
        		// Invalid combination of roles
        		if(!validRoleCombinations.contains(roleSet)) {
            		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
  					exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
  									 Constants.APPLICATION_ACE_CBOR);
  					return;
        		}
        		Set<Integer> roleIdSet = new HashSet<Integer>();
        		try {
            		roleIdSet = Util.getGroupOSCORERoles(roleSet);
        		}
        		catch(AceException e) {
        			System.err.println(e.getMessage());
        		}
        		short[] roleIdArray = new short[roleIdSet.size()];
        		int index = 0;
        		for (Integer elem : roleIdSet)
        		    roleIdArray[index++] = elem.shortValue(); 
        		for (int i=0; i<roleIdArray.length; i++) {
        			short roleIdentifier = roleIdArray[i];
        			// Silently ignore unrecognized roles
        			if (roleIdentifier < Constants.GROUP_OSCORE_ROLES.length)
        				roles.add(Constants.GROUP_OSCORE_ROLES[roleIdentifier]);
        		}
        	}
      	  	
        	// Invalid format of roles
      	  	else {
        		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
      	  		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
      	  						 Constants.APPLICATION_ACE_CBOR);
        		return;
      	  	}
        	
        	// Check that the indicated roles for this group are actually allowed by the Access Token 
        	boolean allowed = false;
        	int[] roleSetToken = getRolesFromToken(subject, groupName);
        	if (roleSetToken == null) {
        		exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
        						 "Error when retrieving allowed roles from Access Tokens");
        		return;
        	}
        	else {
        		for (int index = 0; index < roleSetToken.length; index++) {
            		if ((roleSet & roleSetToken[index]) == roleSet) {
            			// 'scope' in at least one Access Token admits all the roles indicated
            			// for this group in the Joining Request
            			allowed = true;
            			break;
            		}
        		}	
        	}
        	
        	if (!allowed) {
        		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN, errorResponsePayload,
        						 Constants.APPLICATION_ACE_CBOR);
        		return;
        	}
        	
        	// Retrieve 'get_pub_keys'
        	// If present, this parameter must be a CBOR array or the CBOR simple value Null
        	CBORObject getPubKeys = joinRequest.get(CBORObject.FromObject((Constants.GET_PUB_KEYS)));
        	if (getPubKeys != null) {
        		
        		// Invalid format of 'get_pub_keys'
        		if (!getPubKeys.getType().equals(CBORType.Array) && !getPubKeys.equals(CBORObject.Null)) {
            		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
        			exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
        							 Constants.APPLICATION_ACE_CBOR);
            		return;
        		}
        		
        		// Invalid format of 'get_pub_keys'
        		if (getPubKeys.getType().equals(CBORType.Array)) {
	        		if ( getPubKeys.size() != 3 ||
	        	        !getPubKeys.get(0).getType().equals(CBORType.Boolean) ||
	        	         getPubKeys.get(0).AsBoolean() != true ||
	        			!getPubKeys.get(1).getType().equals(CBORType.Array) ||
	        			!getPubKeys.get(2).getType().equals(CBORType.Array) || 
	        			 getPubKeys.get(2).size() != 0) {
	            		
	            		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
	        			exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
	        							 Constants.APPLICATION_ACE_CBOR);
	            		return;
	            		
	        		}
        		}
        		
        		// Invalid format of 'get_pub_keys'
        		if (getPubKeys.getType().equals(CBORType.Array)) {
	    			for (int i = 0; i < getPubKeys.get(1).size(); i++) {
	    				// Possible elements of the first array have to be all integers and
	    				// express a valid combination of roles encoded in the AIF data model
	    				if (!getPubKeys.get(1).get(i).getType().equals(CBORType.Integer) ||
	    					!validRoleCombinations.contains(getPubKeys.get(1).get(i).AsInt32())) {
	    					
	                		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
	            			exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
	            							 Constants.APPLICATION_ACE_CBOR);
	                		return;
	    					
	    				}
	    			}
        		}
        		
        		providePublicKeys = true;
        		
        	}
        	
        	// Retrieve the entry for the target group, using the group name
        	GroupInfo myGroup = activeGroups.get(groupName);
        	
        	String nodeName = null;
        	byte[] senderId = null;
        	int signKeyCurve = 0;
        	
        	// Identify reserved Sender IDs for Client1 and Client2
			byte[] sidClient1 = KeyStorage.clientIds.get("Client1").getBytes();
			byte[] sidClient2 = KeyStorage.clientIds.get("Client2").getBytes();
			// If this is Client1 or Client2 joining (check public key), give them a specific Sender ID
			byte[] clientPubKey = joinRequest.get(CBORObject.FromObject(Constants.CLIENT_CRED)).GetByteString();
			if(Arrays.equals(clientPubKey, KeyStorage.memberCcs.get("Client1"))) {
            	senderId = sidClient1;
            	myGroup.allocateSenderId(senderId);
            } else if (Arrays.equals(clientPubKey, KeyStorage.memberCcs.get("Client2"))) {
            	senderId = sidClient2;
            	myGroup.allocateSenderId(senderId);
            }
        	// Assign a Sender ID to the joining node, unless it is a monitor
            else if (roleSet != (1 << Constants.GROUP_OSCORE_MONITOR)) {
        		
        		senderId = new byte[1];
        		while(Arrays.equals(senderId, new byte[] { 0x25 }) || Arrays.equals(senderId, new byte[] { 0x77 }) ||
        				Arrays.equals(senderId, new byte[] { 0x52 })) {
        			rand.nextBytes(senderId);
        		}
            	while(myGroup.allocateSenderId(senderId) == false || Arrays.equals(senderId, sidClient1) || Arrays.equals(senderId, sidClient2)) {
            		rand.nextBytes(senderId);
            	}
            	
            	myGroup.allocateSenderId(senderId);
        	}
        	
        	nodeName = myGroup.allocateNodeName(senderId);
        	
        	if (nodeName == null) {
        		exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
        						 "Error when assigning a node name");
        		return;
        	}
        	
        	// Retrieve 'client_cred'
        	CBORObject clientCred = joinRequest.get(CBORObject.FromObject(Constants.CLIENT_CRED));
        	
        	if (clientCred == null && (roleSet != (1 << Constants.GROUP_OSCORE_MONITOR))) {
        		
        		// TODO: check if the Group Manager already owns this client's public key
        		
        	}
        	if (clientCred == null && (roleSet != (1 << Constants.GROUP_OSCORE_MONITOR))) {
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
        						 "A public key was neither provided nor found as already stored");
        		return;
        	}
        	// Process the public key of the joining node
        	else if (roleSet != (1 << Constants.GROUP_OSCORE_MONITOR)) {
        		
        		OneKey publicKey = null;
        		boolean valid = false;
        		
        		if (clientCred.getType() != CBORType.ByteString) {
        		    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
        		                     "The parameter 'client_cred' must be a CBOR byte string");
        		    return;
        		}
        		
        		byte[] clientCredBytes = clientCred.GetByteString();
        		switch(myGroup.getPubKeyEnc()) {
        		    case Constants.COSE_HEADER_PARAM_CCS:
        		        CBORObject ccs = CBORObject.DecodeFromBytes(clientCredBytes);
        		        if (ccs.getType() == CBORType.Map) {
        		            // Retrieve the public key from the CCS
        		            publicKey = Util.ccsToOneKey(ccs);
        		            valid = true;
        		        }
        		        else {
        		            Assert.fail("Invalid format of public key");
        		        }
        		        break;
        		    case Constants.COSE_HEADER_PARAM_CWT:
        		        CBORObject cwt = CBORObject.DecodeFromBytes(clientCredBytes);
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
        		        if (clientCred.getType() == CBORType.ByteString) {
        		            // TODO
        		        }
        		        else {
        		            Assert.fail("Invalid format of public key");
        		        }
        		        break;
        		    default:
        		        Assert.fail("Invalid format of public key");
        		}
        		if (publicKey == null ||  valid == false) {
            		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
            		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
            						 Constants.APPLICATION_ACE_CBOR);
            		return;
        		}
        		        		
        		// Sanity check on the type of public key

        		if (myGroup.getSignAlg().equals(AlgorithmID.ECDSA_256) ||
            		    myGroup.getSignAlg().equals(AlgorithmID.ECDSA_384) ||
            		    myGroup.getSignAlg().equals(AlgorithmID.ECDSA_512)) {
            			
        				// Invalid public key format
            			if (!publicKey.get(KeyKeys.KeyType).
            					equals(myGroup.getSignParams().get(0).get(0)) || // alg capability: key type
                       		!publicKey.get(KeyKeys.KeyType).
                       			equals(myGroup.getSignParams().get(1).get(0)) || // key capability: key type
                       		!publicKey.get(KeyKeys.EC2_Curve).
                       			equals(myGroup.getSignParams().get(1).get(1)))   // key capability: curve
            			{ 

                    			myGroup.deallocateSenderId(senderId);

                        		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
                        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
                        						 Constants.APPLICATION_ACE_CBOR);
                    			return;
                            		
                    	}

            		}
            		
            		if (myGroup.getSignAlg().equals(AlgorithmID.EDDSA)) {
            			
            			// Invalid public key format
            			if (!publicKey.get(KeyKeys.KeyType).
            						equals(myGroup.getSignParams().get(0).get(0)) || // alg capability: key type
                   			!publicKey.get(KeyKeys.KeyType).
                   					equals(myGroup.getSignParams().get(1).get(0)) || // key capability: key type
                   			!publicKey.get(KeyKeys.OKP_Curve).
                   					equals(myGroup.getSignParams().get(1).get(1)))   // key capability: curve
            			{

                        			myGroup.deallocateSenderId(senderId);

                            		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
                            		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
                            						 Constants.APPLICATION_ACE_CBOR);
                        			return;
                        		
                		}
            				
            		}
        		
        		// Retrieve the proof-of-possession nonce and signature from the Client
        		CBORObject cnonce = joinRequest.get(CBORObject.FromObject(Constants.CNONCE));
            	
        		// A client nonce must be included for proof-of-possession
            	if (cnonce == null) {
            		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
            		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
            						 Constants.APPLICATION_ACE_CBOR);
            		return;
            	}

            	// The client nonce must be wrapped in a binary string
            	if (!cnonce.getType().equals(CBORType.ByteString)) {
            		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
            		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
            						 Constants.APPLICATION_ACE_CBOR);
            		return;
                }
        		
        		// Check the proof-of-possession evidence over (scope | rsnonce | cnonce), using the Client's public key
            	CBORObject clientPopEvidence = joinRequest.get(CBORObject.FromObject(Constants.CLIENT_CRED_VERIFY));
            	
            	// A client PoP evidence must be included
            	if (clientPopEvidence == null) {
            		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
            		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
            						 Constants.APPLICATION_ACE_CBOR);
            		return;
            	}

            	// The client PoP evidence must be wrapped in a binary string
            	if (!clientPopEvidence.getType().equals(CBORType.ByteString)) {
            		byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
            		exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
            						 Constants.APPLICATION_ACE_CBOR);
            		return;
                }
            	
            	byte[] rawClientPopEvidence = clientPopEvidence.GetByteString();
        		
            	PublicKey pubKey = null;
                try {
					pubKey = publicKey.AsPublicKey();
				} catch (CoseException e) {
					System.out.println(e.getMessage());
					exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
									 "Failed to use the Client's public key to verify the PoP signature");
            		return;
				}
                if (pubKey == null) {
                	exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
                					 "Failed to use the Client's public key to verify the PoP signature");
            		return;
                }
                
                int offset = 0;

                byte[] serializedScopeCBOR = CBORObject.FromObject(scope).EncodeToBytes();
                byte[] serializedGMNonceCBOR = CBORObject.FromObject(rsnonce).EncodeToBytes();
                byte[] serializedCNonceCBOR = cnonce.EncodeToBytes();
                byte[] popInput = new byte [serializedScopeCBOR.length +
                                            serializedGMNonceCBOR.length +
                                            serializedCNonceCBOR.length];
                System.arraycopy(serializedScopeCBOR, 0, popInput, offset, serializedScopeCBOR.length);
                offset += serializedScopeCBOR.length;
                System.arraycopy(serializedGMNonceCBOR, 0, popInput, offset, serializedGMNonceCBOR.length);
                offset += serializedGMNonceCBOR.length;
                System.arraycopy(serializedCNonceCBOR, 0, popInput, offset, serializedCNonceCBOR.length);

                // The group mode is used. The PoP evidence is a signature
                if (targetedGroup.getMode() != Constants.GROUP_OSCORE_PAIRWISE_MODE_ONLY) {
                    
                    if (publicKey.get(KeyKeys.KeyType).equals(org.eclipse.californium.cose.KeyKeys.KeyType_EC2))
                        signKeyCurve = publicKey.get(KeyKeys.EC2_Curve).AsInt32();
                    else if (publicKey.get(KeyKeys.KeyType).equals(org.eclipse.californium.cose.KeyKeys.KeyType_OKP))
                        signKeyCurve = publicKey.get(KeyKeys.OKP_Curve).AsInt32();

                    // This should never happen, due to the previous sanity checks
                    if (signKeyCurve == 0) {
                        exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
                        				 "Error when setting up the signature verification");
                        return;
                    }

                    // Invalid Client's PoP signature
                    if (!Util.verifySignature(signKeyCurve, pubKey, popInput, rawClientPopEvidence)) {
                    	byte[] errorResponsePayload = errorResponseMap.EncodeToBytes();
                    	exchange.respond(CoAP.ResponseCode.BAD_REQUEST, errorResponsePayload,
                    					 Constants.APPLICATION_ACE_CBOR);
                        return;
                    }
                }
                // Only the pairwise mode is used. The PoP evidence is a MAC
                else {
                    // TODO
                }
            	
                if (!myGroup.storePublicKey(senderId, clientCred)) {
        			myGroup.deallocateSenderId(senderId);
					exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
									 "Error when storing the public key");
            		return;
        			
        		}
        		
        	}
        	
        	if (myGroup.isGroupMember(subject) == true) {
        		// This node is re-joining the group without having left
        		
            	String oldNodeName = myGroup.getGroupMemberName(subject);
            	
            	Resource staleResource = getChild("nodes").getChild(oldNodeName);
        		this.getChild("nodes").getChild(oldNodeName).delete(staleResource);
        		
        		myGroup.removeGroupMemberBySubject(subject);
        		
        	}
        	
        	if (!myGroup.addGroupMember(senderId, nodeName, roleSet, subject)) {
        		// The joining node is not a monitor; its node name is its Sender ID encoded as a String
    			if (senderId != null) {
    				myGroup.deallocateSenderId(senderId);
    			}
    			// The joining node is a monitor; it got a node name but not a Sender ID
    			else {
    				myGroup.deallocateNodeName(nodeName);
    			}
    			myGroup.deleteBirthGid(nodeName);
    			exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
    							 "Error when adding the new group member");
        		return;
        	}
        	
        	// Create and add the sub-resource associated to the new group member
        	try {
        		valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" +
        													 groupName + "/nodes/" + nodeName));
        		valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" +
        													 groupName + "/nodes/" + nodeName + "/pub-key"));
    		}
    		catch(AceException e) {
    			myGroup.removeGroupMemberBySubject(subject);
    			
    			// The joining node is not a monitor
    			if (senderId != null) {
	    			myGroup.deallocateSenderId(senderId);
	    			myGroup.deletePublicKey(senderId);
    			}
    			
				exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
								 "Error when creating the node sub-resource");
        		return;
    		}
        	Set<Short> actions = new HashSet<>();
        	actions.add(Constants.GET);
        	actions.add(Constants.PUT);
        	actions.add(Constants.DELETE);
        	myScopes.get(rootGroupMembershipResource + "/" + groupName)
        	        .put(rootGroupMembershipResource + "/" + groupName + "/nodes/" + nodeName, actions);
        	Resource nodeCoAPResource = new GroupOSCORESubResourceNodename(nodeName);
        	this.getChild("nodes").add(nodeCoAPResource);
        	
        	actions = new HashSet<>();
        	actions.add(Constants.POST);
        	myScopes.get(rootGroupMembershipResource + "/" + groupName)
	                .put(rootGroupMembershipResource + "/" + groupName + "/nodes/" + nodeName + "/pub-key", actions);
        	nodeCoAPResource = new GroupOSCORESubResourceNodenamePubKey("pub-key");
        	this.getChild("nodes").getChild(nodeName).add(nodeCoAPResource);
        	
        	        	
            // Respond to the Join Request
            
        	CBORObject joinResponse = CBORObject.NewMap();
        	
        	// Key Type Value assigned to the Group_OSCORE_Input_Material object.
        	joinResponse.Add(Constants.GKTY, CBORObject.FromObject(Constants.GROUP_OSCORE_INPUT_MATERIAL_OBJECT));
        	
        	// This map is filled as the Group_OSCORE_Input_Material object, as defined in draft-ace-key-groupcomm-oscore
        	CBORObject myMap = CBORObject.NewMap();
        	
        	// Fill the 'key' parameter
        	if (senderId != null) {
    			// The joining node is not a monitor
        		myMap.Add(GroupOSCOREInputMaterialObjectParameters.group_SenderID, senderId);
        	}
        	myMap.Add(OSCOREInputMaterialObjectParameters.hkdf, targetedGroup.getHkdf().AsCBOR());
        	myMap.Add(OSCOREInputMaterialObjectParameters.salt, targetedGroup.getMasterSalt());
        	myMap.Add(OSCOREInputMaterialObjectParameters.ms, targetedGroup.getMasterSecret());
        	myMap.Add(OSCOREInputMaterialObjectParameters.contextId, targetedGroup.getGroupId());
        	myMap.Add(GroupOSCOREInputMaterialObjectParameters.pub_key_enc, targetedGroup.getPubKeyEnc());
        	if (targetedGroup.getMode() != Constants.GROUP_OSCORE_PAIRWISE_MODE_ONLY) {
        	    // The group mode is used
        	    myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_enc_alg, targetedGroup.getSignEncAlg().AsCBOR());
        	    myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_alg, targetedGroup.getSignAlg().AsCBOR());
        	    if (targetedGroup.getSignParams().size() != 0)
        	        myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_params, targetedGroup.getSignParams());
        	}
        	if (targetedGroup.getMode() != Constants.GROUP_OSCORE_GROUP_MODE_ONLY) {
        	    // The pairwise mode is used
        	    myMap.Add(OSCOREInputMaterialObjectParameters.alg, targetedGroup.getAlg().AsCBOR());
        	    myMap.Add(GroupOSCOREInputMaterialObjectParameters.ecdh_alg, targetedGroup.getEcdhAlg().AsCBOR());
        	    if (targetedGroup.getEcdhParams().size() != 0)
        	        myMap.Add(GroupOSCOREInputMaterialObjectParameters.ecdh_params, targetedGroup.getEcdhParams());
        	}        	
        	joinResponse.Add(Constants.KEY, myMap);
        	
        	// If backward security has to be preserved:
        	//
        	// 1) The Epoch part of the Group ID should be incremented
        	// myGroup.incrementGroupIdEpoch();
        	//
        	// 2) The OSCORE group should be rekeyed

        	// The current version of the symmetric keying material
        	joinResponse.Add(Constants.NUM, CBORObject.FromObject(myGroup.getVersion()));
        	
        	// CBOR Value assigned to the "coap_group_oscore_app" profile.
        	joinResponse.Add(Constants.ACE_GROUPCOMM_PROFILE, CBORObject.FromObject(Constants.COAP_GROUP_OSCORE_APP));
        	
        	// Expiration time in seconds, after which the OSCORE Security Context
        	// derived from the 'k' parameter is not valid anymore.
        	joinResponse.Add(Constants.EXP, CBORObject.FromObject(1000000));
        	
        	if (providePublicKeys) {
        		CBORObject pubKeysArray = CBORObject.NewArray();
        	    CBORObject peerRoles = CBORObject.NewArray();
        	    CBORObject peerIdentifiers = CBORObject.NewArray();
        	    
        	    Map<CBORObject, CBORObject> publicKeys = myGroup.getPublicKeys();
        	    
        	    for (CBORObject sid : publicKeys.keySet()) {
        	        // This should never happen; silently ignore
        	        if (publicKeys.get(sid) == null)
        	            continue;

        	        byte[] peerSenderId = sid.GetByteString();
        	        // Skip the public key of the just-added joining node
        	        if ((senderId != null) && Arrays.equals(senderId, peerSenderId))
        	            continue;
        	        
        	        boolean includePublicKey = false;
        	        
        	        // Public keys of all group members are requested
        	        if (getPubKeys.equals(CBORObject.Null)) {
        	            includePublicKey = true;
        	        }
        	        // Only public keys of group members with certain roles are requested
        	        else {
        	            for (int i = 0; i < getPubKeys.get(1).size(); i++) {
        	                int filterRoles = getPubKeys.get(1).get(i).AsInt32();
        	                int memberRoles = myGroup.getGroupMemberRoles(peerSenderId);
        	                // The owner of this public key does not have all
        	                // its roles indicated in this AIF integer filter
        	                if (filterRoles != (filterRoles & memberRoles)) {
        	                    continue;
        	                }
        	                else {
        	                    includePublicKey = true;
        	                    break;
        	                }
        	            }
        	        }
        	        
        	        if (includePublicKey) {
        				pubKeysArray.Add(publicKeys.get(sid));
	        			peerRoles.Add(myGroup.getGroupMemberRoles(peerSenderId));
	        			peerIdentifiers.Add(peerSenderId);
        			}

        	    }
        	    
        	    joinResponse.Add(Constants.PUB_KEYS, pubKeysArray);
    			joinResponse.Add(Constants.PEER_ROLES, peerRoles);
    			joinResponse.Add(Constants.PEER_IDENTIFIERS, peerIdentifiers);
        	    
        	    // Debug:
        		// 1) Print 'kid' as equal to the Sender ID of the key owner
        		// 2) Print 'kty' of each public key
        		/*
        		for (int i = 0; i < coseKeySet.size(); i++) {
        			byte[] kid = coseKeySet.get(i).get(KeyKeys.KeyId.AsCBOR()).GetByteString();
        			for (int j = 0; j < kid.length; j++)
        				System.out.printf("0x%02X", kid[j]);
        			System.out.println("\n" + coseKeySet.get(i).get(KeyKeys.KeyType.AsCBOR()));
        		}
        		*/
        		
        	}
        	
        	// Group Policies
        	joinResponse.Add(Constants.GROUP_POLICIES, myGroup.getGroupPolicies());
        	
        	
        	// Public key of the Group Manager together with proof-of-possession evidence
        	byte[] kdcNonce = new byte[8];
        	new SecureRandom().nextBytes(kdcNonce);
        	joinResponse.Add(Constants.KDC_NONCE, kdcNonce);
        	
        	CBORObject publicKey = CBORObject.FromObject(targetedGroup.getGmPublicKey());
        	
        	joinResponse.Add(Constants.KDC_CRED, publicKey);

        	PrivateKey gmPrivKey;
        	try {
        	    gmPrivKey = targetedGroup.getGmKeyPair().AsPrivateKey();
        	} catch (CoseException e) {
        	    System.err.println("Error when computing the GM PoP evidence " + e.getMessage());
        	    exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
        	    				 "Error when computing the GM PoP evidence");
        	    return;
        	}
        	byte[] gmSignature = Util.computeSignature(signKeyCurve,gmPrivKey, kdcNonce);

        	if (gmSignature != null) {
        	    joinResponse.Add(Constants.KDC_CRED_VERIFY, gmSignature);
        	}
        	else {
        	    exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
        	    				 "Error when computing the GM PoP evidence");
        	    return;
        	}
        	
        	
        	byte[] responsePayload = joinResponse.EncodeToBytes();
        	String uriNodeResource = new String(rootGroupMembershipResource + "/" +
        										groupName + "/nodes/" + nodeName);
        	
        	Response coapJoinResponse = new Response(CoAP.ResponseCode.CREATED);
        	coapJoinResponse.setPayload(responsePayload);
        	coapJoinResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);
        	coapJoinResponse.getOptions().setLocationPath(uriNodeResource);

        	exchange.respond(coapJoinResponse);
        	
        }
    }
    
    /**
     * Definition of the Group OSCORE group-membership sub-resource /pub-key
     */
    public static class GroupOSCORESubResourcePubKey extends CoapResource {
    	
		/**
         * Constructor
         * @param resId  the resource identifier
         */
        public GroupOSCORESubResourcePubKey(String resId) {
            
            // set resource identifier
            super(resId);
            
            // set display name
            getAttributes().setTitle("Group OSCORE Group-Membership Sub-Resource \"pub-key\" " + resId);
            
        }

        @Override
        public void handleGET(CoapExchange exchange) {
        	System.out.println("GET request reached the GM");
        	
        	// Retrieve the entry for the target group, using the last path
        	// segment of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getParent().getName());
        	
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	String groupName = targetedGroup.getGroupName();
        	
        	// This should never happen if active groups are maintained properly
  	  		if (!groupName.equals(this.getParent().getName())) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
  				return;
  			}
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen, due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
        	
        	if (!targetedGroup.isGroupMember(subject)) {
        		
        		// The requester is not a current group member.
        		//
        		// This is still fine, as long as at least one Access Tokens
        		// of the requester allows also the role "Verifier" in this group
        		
        		// Check that at least one of the Access Tokens for
        		// this node allows (also) the Verifier role for this group
            	
        		int role = 1 << Constants.GROUP_OSCORE_VERIFIER;
        		boolean allowed = false;
            	int[] roleSetToken = getRolesFromToken(subject, groupName);
            	if (roleSetToken == null) {
            		exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
            						 "Error when retrieving allowed roles from Access Tokens");
            		return;
            	}
            	else {
            		for (int index = 0; index < roleSetToken.length; index++) {
            			if ((role & roleSetToken[index]) != 0) {
                			// 'scope' in this Access Token admits (also) the role "Verifier" for this group.
            				// This makes it fine for the requester.
            				allowed = true;
            				break;
            			}
            		}
            	}
            	
            	if (!allowed) {
            		exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
            						 "Operation not permitted to a non-member which is not a Verifier");
            		return;
            	}
            	
        	}
            
        	// Respond to the Public Key Request

        	CBORObject myResponse = CBORObject.NewMap();
        	CBORObject pubKeysArray = CBORObject.NewArray();
        	CBORObject peerRoles = CBORObject.NewArray();
        	CBORObject peerIdentifiers = CBORObject.NewArray();

        	Map<CBORObject, CBORObject> publicKeys = targetedGroup.getPublicKeys();

        	for (CBORObject sid : publicKeys.keySet()) {
        	    
        	    // This should never happen; silently ignore
        	    if (publicKeys.get(sid) == null)
        	        continue;
        	    
        	    byte[] peerSenderId = sid.GetByteString();
        	    // This should never happen; silently ignore
        	    if (peerSenderId == null)
        	        continue;
        	    
        	    pubKeysArray.Add(publicKeys.get(sid));
        	    peerRoles.Add(targetedGroup.getGroupMemberRoles(peerSenderId));
        	    peerIdentifiers.Add(peerSenderId);
        	    
        	}

        	myResponse.Add(Constants.NUM, CBORObject.FromObject(targetedGroup.getVersion()));
        	myResponse.Add(Constants.PUB_KEYS, pubKeysArray);
        	myResponse.Add(Constants.PEER_ROLES, peerRoles);
        	myResponse.Add(Constants.PEER_IDENTIFIERS, peerIdentifiers);

        	byte[] responsePayload = myResponse.EncodeToBytes();

        	Response coapResponse = new Response(CoAP.ResponseCode.CONTENT);
        	coapResponse.setPayload(responsePayload);
        	coapResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);

        	exchange.respond(coapResponse);

        }
        
        @Override
        public void handleFETCH(CoapExchange exchange) {
        	System.out.println("FETCH request reached the GM");
        	
        	// Retrieve the entry for the target group, using the last path segment
        	// of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getParent().getName());
        	
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	String groupName = targetedGroup.getGroupName();
        	
        	// This should never happen if active groups are maintained properly
  	  		if (!groupName.equals(this.getParent().getName())) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
  				return;
  			}
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen, due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
        	
        	if (!targetedGroup.isGroupMember(subject)) {
        		
        		// The requester is not a current group member.
        		//
        		// This is still fine, as long as at least one Access Tokens
        		// of the requester allows also the role "Verifier" in this group
        		
        		// Check that at least one of the Access Tokens for this node
        		// allows (also) the Verifier role for this group
            	
        		int role = 1 << Constants.GROUP_OSCORE_VERIFIER;
        		boolean allowed = false;
            	int[] roleSetToken = getRolesFromToken(subject, groupName);
            	if (roleSetToken == null) {
            		exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
            						 "Error when retrieving allowed roles from Access Tokens");
            		return;
            	}
            	else {
            		for (int index = 0; index < roleSetToken.length; index++) {
            			if ((role & roleSetToken[index]) != 0) {
                			// 'scope' in this Access Token admits (also) the role "Verifier" for this group.
            				// This makes it fine for the requester.
            				allowed = true;
            				break;
            			}
            		}
            	}
            	
            	if (!allowed) {
            		exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
            						 "Operation not permitted to a non-member which is not a Verifier");
            		return;
            	}
            	
        	}
        	        	
        	byte[] requestPayload = exchange.getRequestPayload();
        	
        	if(requestPayload == null) {
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
        						 "A payload must be present");
        		return;
        	}
        	
        	CBORObject requestCBOR = CBORObject.DecodeFromBytes(requestPayload);
			
        	boolean valid = true;
		    
        	// The payload of the request must be a CBOR Map
        	if (!requestCBOR.getType().equals(CBORType.Map)) {
        		valid = false;
        		
        	}

        	// The CBOR Map must include exactly one element, i.e. 'get_pub_keys'
        	if ((requestCBOR.size() != 1) || (!requestCBOR.ContainsKey(Constants.GET_PUB_KEYS))) {
        		valid = false;
        		
        	}

        	// Invalid format of 'get_pub_keys'
    		if (!valid) {
				exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
								 "Invalid format of 'get_pub_keys'");
	    		return;
    		}
    		
        	// Retrieve 'get_pub_keys'
        	// This parameter must be a CBOR array or the CBOR simple value Null
        	CBORObject getPubKeys = requestCBOR.get(CBORObject.FromObject((Constants.GET_PUB_KEYS)));
        	
    	    // Invalid format of 'get_pub_keys'
    	    if (!getPubKeys.getType().equals(CBORType.Array) && !getPubKeys.equals(CBORObject.Null)) {
				exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
								 "Invalid format of 'get_pub_keys'");
	    		return;
    	    }
    			    
    	    if (getPubKeys.getType().equals(CBORType.Array)) {
    	    
	    		// 'get_pub_keys' must include exactly two elements, both of which CBOR arrays
	    		if ( getPubKeys.size() != 3 ||
	    			!getPubKeys.get(0).getType().equals(CBORType.Boolean) ||
	    			!getPubKeys.get(1).getType().equals(CBORType.Array) ||
	    			!getPubKeys.get(2).getType().equals(CBORType.Array)) {
	    			
	    			valid = false;
	        		
	    		}
	
	    		// Invalid format of 'get_pub_keys'
	    		if (valid && getPubKeys.get(1).size() == 0 && getPubKeys.get(2).size() == 0) {
	    			valid = false;
	    		}
	    		
	    		// Invalid format of 'get_pub_keys'
	    		if (getPubKeys.get(0).AsBoolean() == false && getPubKeys.get(2).size() == 0) {
	    			valid = false;
	    		}
	    		
	    		// Invalid format of 'get_pub_keys'
	    		if (valid) {
					for (int i = 0; i < getPubKeys.get(1).size(); i++) {
						// Possible elements of the first array have to be all integers and
						// express a valid combination of roles encoded in the AIF data model
						if (!getPubKeys.get(1).get(i).getType().equals(CBORType.Integer) ||
							!validRoleCombinations.contains(getPubKeys.get(1).get(i).AsInt32())) {
								valid = false;
								break;
								
						}
					}
	    		}
	    		
	    		// Invalid format of 'get_pub_keys'
	    		if (valid) {
					for (int i = 0; i < getPubKeys.get(2).size(); i++) {
						// Possible elements of the second array have to be all
						// byte strings, specifying Sender IDs of other group members
						if (!getPubKeys.get(2).get(i).getType().equals(CBORType.ByteString)) {
							valid = false;
							break;
							
						}			
					}
	    		}
				
	    		// Invalid format of 'get_pub_keys'
	    		if (!valid) {
					exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
									 "Invalid format of 'get_pub_keys'");
		    		return;
	    		}
    		
    	    }
    		
    		
    	    // Respond to the Public Key Request

    	    CBORObject myResponse = CBORObject.NewMap();
    	    CBORObject pubKeysArray = CBORObject.NewArray();
    	    CBORObject peerRoles = CBORObject.NewArray();
    	    CBORObject peerIdentifiers = CBORObject.NewArray();
    	    Set<Integer> requestedRoles = new HashSet<Integer>();
    	    Set<ByteBuffer> requestedSenderIDs = new HashSet<ByteBuffer>();

    	    Map<CBORObject, CBORObject> publicKeys = targetedGroup.getPublicKeys();

    	    // Provide the public keys of all the group members
    	    if (getPubKeys.equals(CBORObject.Null)) {
    	        
    	        for (CBORObject sid : publicKeys.keySet()) {
    	            
    	            // This should never happen; silently ignore
    	            if (publicKeys.get(sid) == null)
    	                continue;
    	            
    	            byte[] memberSenderId = sid.GetByteString();
    	            // This should never happen; silently ignore
    	            if (memberSenderId == null)
    	                continue;

    	            int memberRoles = targetedGroup.getGroupMemberRoles(memberSenderId);
    	            
    	            pubKeysArray.Add(publicKeys.get(sid));
    	            peerRoles.Add(memberRoles);
    	            peerIdentifiers.Add(memberSenderId);
    	            
    	        }
    	        
    	    }
    	    // Provide the public keys based on the specified filtering
    	    else {

    	        // Retrieve the inclusion flag
    	        boolean inclusionFlag = getPubKeys.get(0).getType().equals(CBORType.Boolean);
    	        
    	        // Retrieve and store the combination of roles specified in the request
    	        for (int i = 0; i < getPubKeys.get(1).size(); i++) {
    	            requestedRoles.add((getPubKeys.get(1).get(i).AsInt32()));
    	        }
    	        
    	        // Retrieve and store the Sender IDs specified in the request
    	        for (int i = 0; i < getPubKeys.get(2).size(); i++) {
    	            byte[] myArray = getPubKeys.get(2).get(i).GetByteString();
    	            ByteBuffer myBuffer = ByteBuffer.wrap(myArray);
    	            requestedSenderIDs.add(myBuffer);
    	        }

    	        for (CBORObject sid : publicKeys.keySet()) {
    	            
    	            // This should never happen; silently ignore
    	            if (publicKeys.get(sid) == null)
    	                continue;
    	            
    	            byte[] memberSenderId = sid.GetByteString();
    	            // This should never happen; silently ignore
    	            if (memberSenderId == null)
    	                continue;

    	            int memberRoles = targetedGroup.getGroupMemberRoles(memberSenderId);
    	            
    	            boolean include = false;
    	            
    	            for (Integer filter : requestedRoles) {
    	                int filterRoles = filter.intValue();
    	                
    	                // The role(s) of the key owner match with the role filter
    	                if (filterRoles == (filterRoles & memberRoles)) {
    	                    
    	                    // This public key has to be included anyway,
    	                    // regardless the Sender ID of the key owner
    	                    if (inclusionFlag) {
    	                        include = true;
    	                    }
    	                    // This public key has to be included only if the Sender ID
    	                    // of the key owner is not in the node identifier filter
    	                    else if (!requestedSenderIDs.contains(ByteBuffer.wrap(memberSenderId))) {
    	                        include = true;
    	                    }
    	                    // Stop going through the role filter anyway;
    	                    // this public key has not to be included
    	                    break;
    	                }	
    	            }
    	            
    	            if(!include) {
    	                // This public has to be included if the Sender ID of the key owner is in the node identifier filter
    	                if (inclusionFlag && requestedSenderIDs.contains(ByteBuffer.wrap(memberSenderId))) {
    	                    include = true;
    	                }
    	                // This public has to be included if the Sender ID of the key owner is not in the node identifier filter
    	                else if (!inclusionFlag && !requestedSenderIDs.contains(ByteBuffer.wrap(memberSenderId))) {
    	                    include = true;
    	                }
    	            }
    	            
    	            if (include) {
    	                
    	            	pubKeysArray.Add(publicKeys.get(sid));
    	                peerRoles.Add(memberRoles);
    	                peerIdentifiers.Add(memberSenderId);
    	                
    	            }
    	            
    	        }
    	    }

    	    myResponse.Add(Constants.NUM, CBORObject.FromObject(targetedGroup.getVersion()));
    	    myResponse.Add(Constants.PUB_KEYS, pubKeysArray);
			myResponse.Add(Constants.PEER_ROLES, peerRoles);
			myResponse.Add(Constants.PEER_IDENTIFIERS, peerIdentifiers);
        	
        	byte[] responsePayload = myResponse.EncodeToBytes();
        	
        	Response coapResponse = new Response(CoAP.ResponseCode.CONTENT);
        	coapResponse.setPayload(responsePayload);
        	coapResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);

        	exchange.respond(coapResponse);

        }
        
    }
    
    /**
     * Definition of the Group OSCORE group-membership sub-resource /gm-pub-key
     */
    public static class GroupOSCORESubResourceGmPubKey extends CoapResource {
    	
		/**
         * Constructor
         * @param resId  the resource identifier
         */
        public GroupOSCORESubResourceGmPubKey(String resId) {
            
            // set resource identifier
            super(resId);
            
            // set display name
            getAttributes().setTitle("Group OSCORE Group-Membership Sub-Resource \"gm-pub-key\" " + resId);
            
        }

        @Override
        public void handleGET(CoapExchange exchange) {
        	System.out.println("GET request reached the GM");
        	
        	// Retrieve the entry for the target group, using the last path segment
        	// of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getParent().getName());
        	
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	String groupName = targetedGroup.getGroupName();
        	
        	// This should never happen if active groups are maintained properly
  	  		if (!groupName.equals(this.getParent().getName())) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
  				return;
  			}
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen,
            	// due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
        	
        	if (!targetedGroup.isGroupMember(subject)) {
        		
        		// The requester is not a current group member.
        		//
        		// This is still fine, as long as at least one Access Tokens
        		// of the requester allows also the role "Verifier" in this group
        		
        		// Check that at least one of the Access Tokens for this node allows (also) the Verifier role for this group
            	
        		int role = 1 << Constants.GROUP_OSCORE_VERIFIER;
        		boolean allowed = false;
            	int[] roleSetToken = getRolesFromToken(subject, groupName);
            	if (roleSetToken == null) {
            		exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
            						 "Error when retrieving allowed roles from Access Tokens");
            		return;
            	}
            	else {
            		for (int index = 0; index < roleSetToken.length; index++) {
            			if ((role & roleSetToken[index]) != 0) {
                			// 'scope' in this Access Token admits (also) the role "Verifier" for this group.
            				// This makes it fine for the requester.
            				allowed = true;
            				break;
            			}
            		}
            	}
            	
            	if (!allowed) {
            		exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
            						 "Operation not permitted to a non-member which is not a Verifier");
            		return;
            	}
            	
        	}
            
        	// Respond to the Group Manager Public Key Request
            
        	CBORObject myResponse = CBORObject.NewMap();
    		
    		// Public key of the Group Manager together with proof-of-possession evidence
        	byte[] kdcNonce = new byte[8];
        	new SecureRandom().nextBytes(kdcNonce);
        	myResponse.Add(Constants.KDC_NONCE, kdcNonce);
        	
        	CBORObject publicKey = CBORObject.FromObject(targetedGroup.getGmPublicKey());
        	
        	myResponse.Add(Constants.KDC_CRED, publicKey);
        	
        	PrivateKey gmPrivKey;
			try {
				gmPrivKey = targetedGroup.getGmKeyPair().AsPrivateKey();
			} catch (CoseException e) {
				System.err.println("Error when computing the GM PoP evidence " + e.getMessage());
				exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
								 "Error when computing the GM PoP evidence");
        		return;
			}
			int signKeyCurve = 0;
			if (targetedGroup.getGmKeyPair().get(KeyKeys.KeyType).AsInt32() == KeyKeys.KeyType_EC2.AsInt32()) {
				signKeyCurve = targetedGroup.getGmKeyPair().get(KeyKeys.EC2_Curve).AsInt32();
			}
			if (targetedGroup.getGmKeyPair().get(KeyKeys.KeyType).AsInt32() == KeyKeys.KeyType_OKP.AsInt32()) {
				signKeyCurve = targetedGroup.getGmKeyPair().get(KeyKeys.OKP_Curve).AsInt32();
			}
			
        	byte[] gmSignature = Util.computeSignature(signKeyCurve, gmPrivKey, kdcNonce);

        	if (gmSignature != null) {
        		myResponse.Add(Constants.KDC_CRED_VERIFY, gmSignature);
        	}
        	else {
				exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
								 "Error when computing the GM PoP evidence");
        		return;
        	}

        	byte[] responsePayload = myResponse.EncodeToBytes();
        	
        	Response coapResponse = new Response(CoAP.ResponseCode.CONTENT);
        	coapResponse.setPayload(responsePayload);
        	coapResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);

        	exchange.respond(coapResponse);

        }
    
    }
    
    /**
     * Definition of the Group OSCORE group-membership sub-resource /verif-data
     */
	 public static class GroupOSCORESubResourceVerifData extends CoapResource {
	     
	     /**
	         * Constructor
	         * @param resId  the resource identifier
	         */
	     public GroupOSCORESubResourceVerifData(String resId) {
	         
	         // set resource identifier
	         super(resId);
	         
	         // set display name
	         getAttributes().setTitle("Group OSCORE Group-Membership Sub-Resource \"verif-data\" " + resId);
	         
	     }
	
	     @Override
	     public void handleGET(CoapExchange exchange) {
	         System.out.println("GET request reached the GM");
	         
	         // Retrieve the entry for the target group, using the last path segment
	         // of the URI path as the name of the OSCORE group
	         GroupInfo targetedGroup = activeGroups.get(this.getParent().getName());
	         
	         // This should never happen if active groups are maintained properly
	         if (targetedGroup == null) {
	             exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
	            		 		  "Error when retrieving material for the OSCORE group");
	             return;
	         }
	         
	         String groupName = targetedGroup.getGroupName();
	         
	         // This should never happen if active groups are maintained properly
	         if (!groupName.equals(this.getParent().getName())) {
	             exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
	            		 		  "Error when retrieving material for the OSCORE group");
	             return;
	         }
	         
	         String subject = null;
	         Request request = exchange.advanced().getCurrentRequest();
	         
	         try {
	             subject = CoapReq.getInstance(request).getSenderId();
	         } catch (AceException e) {
	             System.err.println("Error while retrieving the client identity: " + e.getMessage());
	         }
	         if (subject == null) {
	             // At this point, this should not really happen, due to the earlier check at the Token Repository
	             exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
	            		 		  "Unauthenticated client tried to get access");
	             return;
	         }
	         
	         boolean allowed = false;
	         if (!targetedGroup.isGroupMember(subject)) {
	             
	             // The requester is not a current group member.
	             
	             // Check that at least one of the Access Tokens for this node allows (also) the Verifier role for this group
	             
	             int role = 1 << Constants.GROUP_OSCORE_VERIFIER;
	             int[] roleSetToken = getRolesFromToken(subject, groupName);
	             if (roleSetToken == null) {
	                 exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
	                		 		  "Error when retrieving allowed roles from Access Tokens");
	                 return;
	             }
	             else {
	                 for (int index = 0; index < roleSetToken.length; index++) {
	                     if ((role & roleSetToken[index]) != 0) {
	                         // 'scope' in this Access Token admits (also) the role "Verifier" for this group.
	                    	 // This makes it fine for the requester.
	                         allowed = true;
	                         break;
	                     }
	                 }
	             }
	             
	         }
	         if (!allowed) {
	             exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
	            		 		  "Operation permitted only to a non-member acting as a Verifier");
	             return;
	         }
	         
	         // Respond to the Public Key Request
	         
	         CBORObject myResponse = CBORObject.NewMap();
	         
	         // Key Type Value assigned to the Group_OSCORE_Input_Material object.
	         myResponse.Add(Constants.GKTY, CBORObject.FromObject(Constants.GROUP_OSCORE_INPUT_MATERIAL_OBJECT));
	         
	         // This map is filled as the Group_OSCORE_Input_Material object,
	         // as defined in draft-ace-key-groupcomm-oscore
	         CBORObject myMap = CBORObject.NewMap();
	         
	         // Fill the 'key' parameter
	         // Note that no Sender ID is included
	         myMap.Add(OSCOREInputMaterialObjectParameters.hkdf, targetedGroup.getHkdf().AsCBOR());
	         myMap.Add(OSCOREInputMaterialObjectParameters.contextId, targetedGroup.getGroupId());
	         myMap.Add(GroupOSCOREInputMaterialObjectParameters.pub_key_enc, targetedGroup.getPubKeyEnc());
	         if (targetedGroup.getMode() != Constants.GROUP_OSCORE_PAIRWISE_MODE_ONLY) {
	             // The group mode is used
	             myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_enc_alg, targetedGroup.getSignEncAlg().AsCBOR());
	             myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_alg, targetedGroup.getSignAlg().AsCBOR());
	             if (targetedGroup.getSignParams().size() != 0)
	                 myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_params, targetedGroup.getSignParams());
	         }
	         if (targetedGroup.getMode() != Constants.GROUP_OSCORE_GROUP_MODE_ONLY) {
	             // The pairwise mode is used
	             myMap.Add(OSCOREInputMaterialObjectParameters.alg, targetedGroup.getAlg().AsCBOR());
	             myMap.Add(GroupOSCOREInputMaterialObjectParameters.ecdh_alg, targetedGroup.getEcdhAlg().AsCBOR());
	             if (targetedGroup.getEcdhParams().size() != 0)
	                 myMap.Add(GroupOSCOREInputMaterialObjectParameters.ecdh_params, targetedGroup.getEcdhParams());
	         }
	         
	         myResponse.Add(Constants.KEY, myMap);
	         
	         myResponse.Add(Constants.NUM, CBORObject.FromObject(targetedGroup.getVersion()));
	         
	         // CBOR Value assigned to the coap_group_oscore profile.
	         myResponse.Add(Constants.ACE_GROUPCOMM_PROFILE, CBORObject.FromObject(Constants.COAP_GROUP_OSCORE_APP));
	         
	         // Expiration time in seconds, after which the OSCORE Security Context
	         // derived from the 'k' parameter is not valid anymore.
	         myResponse.Add(Constants.EXP, CBORObject.FromObject(1000000));
	
	         myResponse.Add(Constants.GROUP_KEY_ENC, targetedGroup.getGroupEncryptionKey());
	         
	
	         byte[] responsePayload = myResponse.EncodeToBytes();
	         
	         Response coapResponse = new Response(CoAP.ResponseCode.CONTENT);
	         coapResponse.setPayload(responsePayload);
	         coapResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);
	
	         exchange.respond(coapResponse);
	
	     }
	
	 }
    
    /**
     * Definition of the Group OSCORE group-membership sub-resource /num
     */
    public static class GroupOSCORESubResourceNum extends CoapResource {
    	
		/**
         * Constructor
         * @param resId  the resource identifier
         */
        public GroupOSCORESubResourceNum(String resId) {
            
            // set resource identifier
            super(resId);
            
            // set display name
            getAttributes().setTitle("Group OSCORE Group-Membership Sub-Resource \"num\" " + resId);
            
        }

        @Override
        public void handleGET(CoapExchange exchange) {
        	System.out.println("GET request reached the GM");
        	
        	// Retrieve the entry for the target group, using the last path segment
        	// of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getParent().getName());
        	
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	String groupName = targetedGroup.getGroupName();
        	
        	// This should never happen if active groups are maintained properly
  	  		if (!groupName.equals(this.getParent().getName())) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
  				return;
  			}
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen, due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
        	
        	if (!targetedGroup.isGroupMember(subject)) {	
        		// The requester is not a current group member.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to group members");
        		return;
        	}
            	
        	// Respond to the Version Request
            
        	CBORObject myResponse = CBORObject.FromObject(targetedGroup.getVersion());
        	
        	byte[] responsePayload = myResponse.EncodeToBytes();
        	
        	Response coapResponse = new Response(CoAP.ResponseCode.CONTENT);
        	coapResponse.setPayload(responsePayload);
        	coapResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);

        	exchange.respond(coapResponse);

        }
        
    }
    
    /**
     * Definition of the Group OSCORE group-membership sub-resource /active
     */
    public static class GroupOSCORESubResourceActive extends CoapResource {
    	
		/**
         * Constructor
         * @param resId  the resource identifier
         */
        public GroupOSCORESubResourceActive(String resId) {
            
            // set resource identifier
            super(resId);
            
            // set display name
            getAttributes().setTitle("Group OSCORE Group-Membership Sub-Resource \"active\" " + resId);
            
        }

        @Override
        public void handleGET(CoapExchange exchange) {
        	System.out.println("GET request reached the GM");
        	
        	// Retrieve the entry for the target group, using the last path segment
        	// of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getParent().getName());
        	
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	String groupName = targetedGroup.getGroupName();
        	
        	// This should never happen if active groups are maintained properly
  	  		if (!groupName.equals(this.getParent().getName())) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
  				return;
  			}
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen, due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
        	
        	if (!targetedGroup.isGroupMember(subject)) {	
        		// The requester is not a current group member.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to group members");
        		return;
        	}
            	
        	// Respond to the Version Request
            
        	CBORObject myResponse = CBORObject.FromObject(targetedGroup.getStatus());
        	
        	byte[] responsePayload = myResponse.EncodeToBytes();
        	
        	Response coapResponse = new Response(CoAP.ResponseCode.CONTENT);
        	coapResponse.setPayload(responsePayload);
        	coapResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);

        	exchange.respond(coapResponse);

        }
        
    }
    
    /**
     * Definition of the Group OSCORE group-membership sub-resource /policies
     */
    public static class GroupOSCORESubResourcePolicies extends CoapResource {
    	
		/**
         * Constructor
         * @param resId  the resource identifier
         */
        public GroupOSCORESubResourcePolicies(String resId) {
            
            // set resource identifier
            super(resId);
            
            // set display name
            getAttributes().setTitle("Group OSCORE Group-Membership Sub-Resource \"policies\" " + resId);
            
        }

        @Override
        public void handleGET(CoapExchange exchange) {
        	System.out.println("GET request reached the GM");
        	
        	// Retrieve the entry for the target group, using the last path segment
        	// of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getParent().getName());
        	
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	String groupName = targetedGroup.getGroupName();
        	
        	// This should never happen if active groups are maintained properly
  	  		if (!groupName.equals(this.getParent().getName())) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
  				return;
  			}
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen,
            	// due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
        	
        	if (!targetedGroup.isGroupMember(subject)) {	
        		// The requester is not a current group member.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to group members");
        		return;
        	}
            	
        	// Respond to the Policies Request
            
        	CBORObject myResponse = null;
        	CBORObject groupPolicies = targetedGroup.getGroupPolicies();
        	
        	if (groupPolicies == null) {
            	// This should not happen for this Group Manager, since
        		// default policies apply if not specified when creating the group
        		myResponse = CBORObject.FromObject(new byte[0]);
        	}
        	else {
        		myResponse = CBORObject.NewMap();
        		myResponse.Add(Constants.GROUP_POLICIES, groupPolicies);
        	}
        	
        	byte[] responsePayload = myResponse.EncodeToBytes();
        	
        	Response coapResponse = new Response(CoAP.ResponseCode.CONTENT);
        	coapResponse.setPayload(responsePayload);
        	coapResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);

        	exchange.respond(coapResponse);

        }
        
    }
    
    /**
     * Definition of the Group OSCORE group-membership sub-resource /nodes
     * 
     * This resource has no handlers and is not directly accessed.
     * It acts as root resource to actual sub-resources for each group member.
     * 
     */
    public static class GroupOSCORESubResourceNodes extends CoapResource {
    	
		/**
         * Constructor
         * @param resId  the resource identifier
         */
        public GroupOSCORESubResourceNodes(String resId) {
            
            // set resource identifier
            super(resId);
            
            // set display name
            getAttributes().setTitle("Group OSCORE Group-Membership Sub-Resource \"nodes\" " + resId);
            
        }
        
    }
    
    /**
     * Definition of the Group OSCORE group-membership sub-resource /nodes/NODENAME
     * for the group members with node name "NODENAME"
     */
    public static class GroupOSCORESubResourceNodename extends CoapResource {
    	
		/**
         * Constructor
         * @param resId  the resource identifier
         */
        public GroupOSCORESubResourceNodename(String resId) {
            
            // set resource identifier
            super(resId);
            
            // set display name
            getAttributes().setTitle("Group OSCORE Group-Membership Sub-Resource \"nodes/NODENAME\" " + resId);
            
        }

        
        @Override
        public void handleGET(CoapExchange exchange) {
        	System.out.println("GET request reached the GM");
        	
        	// Retrieve the entry for the target group, using the last path segment
        	// of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getParent().getParent().getName());
        	
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	String groupName = targetedGroup.getGroupName();
        	
        	// This should never happen if active groups are maintained properly
  	  		if (!groupName.equals(this.getParent().getParent().getName())) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
  				return;
  			}
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen,
            	// due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
        	
        	if (!targetedGroup.isGroupMember(subject)) {
        		// The requester is not a current group member.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to group members");
        		return;
        	}
        	
        	if (!(targetedGroup.getGroupMemberName(subject)).equals(this.getName())) {
        		// The requester is not the group member associated to this sub-resource.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to the group member associated to this sub-resource");
        		return;
        	}
            	
        	// Respond to the Key Distribution Request
            
        	// Respond to the Key Distribution Request
            
        	CBORObject myResponse = CBORObject.NewMap();
        	
        	// Key Type Value assigned to the Group_OSCORE_Input_Material object.
        	myResponse.Add(Constants.GKTY, CBORObject.FromObject(Constants.GROUP_OSCORE_INPUT_MATERIAL_OBJECT));
        	
        	// This map is filled as the Group_OSCORE_Input_Material object,
        	// as defined in draft-ace-key-groupcomm-oscore
        	CBORObject myMap = CBORObject.NewMap();
        	
			byte[] senderId = hexStringToByteArray(targetedGroup.getGroupMemberName(subject));
        	
        	// Fill the 'key' parameter
        	if (senderId != null) {
        		// The joining node is not a monitor
        		myMap.Add(GroupOSCOREInputMaterialObjectParameters.group_SenderID, senderId);
        	}
        	myMap.Add(OSCOREInputMaterialObjectParameters.hkdf, targetedGroup.getHkdf().AsCBOR());
        	myMap.Add(OSCOREInputMaterialObjectParameters.salt, targetedGroup.getMasterSalt());
        	myMap.Add(OSCOREInputMaterialObjectParameters.ms, targetedGroup.getMasterSecret());
        	myMap.Add(OSCOREInputMaterialObjectParameters.contextId, targetedGroup.getGroupId());
        	myMap.Add(GroupOSCOREInputMaterialObjectParameters.pub_key_enc, targetedGroup.getPubKeyEnc());
        	if (targetedGroup.getMode() != Constants.GROUP_OSCORE_PAIRWISE_MODE_ONLY) {
        	    // The group mode is used
        	    myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_enc_alg, targetedGroup.getSignEncAlg().AsCBOR());
        	    myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_alg, targetedGroup.getSignAlg().AsCBOR());
        	    if (targetedGroup.getSignParams().size() != 0)
        	        myMap.Add(GroupOSCOREInputMaterialObjectParameters.sign_params, targetedGroup.getSignParams());
        	}
        	if (targetedGroup.getMode() != Constants.GROUP_OSCORE_GROUP_MODE_ONLY) {
        	    // The pairwise mode is used
        	    myMap.Add(OSCOREInputMaterialObjectParameters.alg, targetedGroup.getAlg().AsCBOR());
        	    myMap.Add(GroupOSCOREInputMaterialObjectParameters.ecdh_alg, targetedGroup.getEcdhAlg().AsCBOR());
        	    if (targetedGroup.getEcdhParams().size() != 0)
        	        myMap.Add(GroupOSCOREInputMaterialObjectParameters.ecdh_params, targetedGroup.getEcdhParams());
        	}        	
        	myResponse.Add(Constants.KEY, myMap);
        	
        	// The current version of the symmetric keying material
        	myResponse.Add(Constants.NUM, CBORObject.FromObject(targetedGroup.getVersion()));
        	
        	// CBOR Value assigned to the coap_group_oscore profile.
        	myResponse.Add(Constants.ACE_GROUPCOMM_PROFILE, CBORObject.FromObject(Constants.COAP_GROUP_OSCORE_APP));
        	
        	// Expiration time in seconds, after which the OSCORE Security Context
        	// derived from the 'k' parameter is not valid anymore.
        	myResponse.Add(Constants.EXP, CBORObject.FromObject(1000000));

        	byte[] responsePayload = myResponse.EncodeToBytes();
        	
        	Response coapResponse = new Response(CoAP.ResponseCode.CONTENT);
        	coapResponse.setPayload(responsePayload);
        	coapResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);

        	exchange.respond(coapResponse);
        	
        }
        
        @Override
        public void handlePUT(CoapExchange exchange) {
        	System.out.println("PUT request reached the GM");
        	
        	// Retrieve the entry for the target group, using the last path segment
        	// of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getParent().getParent().getName());
        	
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	String groupName = targetedGroup.getGroupName();
        	
        	// This should never happen if active groups are maintained properly
  	  		if (!groupName.equals(this.getParent().getParent().getName())) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
  				return;
  			}
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
        	if (request.getPayloadSize() != 0) {
        		// This request must not have a payload
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
        						 "This request must not have a payload");
        		return;
        	}
        	
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen, due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
        	
        	if (!targetedGroup.isGroupMember(subject)) {
        		// The requester is not a current group member.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to group members");
        		return;
        	}
        	
        	if (!(targetedGroup.getGroupMemberName(subject)).equals(this.getName())) {
        		// The requester is not the group member associated to this sub-resource.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to the group member associated to this sub-resource");
        		return;
        	}
        	
        	if (targetedGroup.getGroupMemberRoles((targetedGroup.getGroupMemberName(subject))) == 
        										  (1 << Constants.GROUP_OSCORE_MONITOR)) {
        		// The requester is a monitor, hence it is not supposed to have a Sender ID.
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
        						 "Operation not permitted to members that are only monitors");
        		return;
        	}
        	
        	
        	// Here the Group Manager simply assigns a new Sender ID to this group member.
        	// More generally, the Group Manager may alternatively or additionally rekey the whole OSCORE group 
        	// Note that the node name does not change.
        	
        	byte[] oldSenderId = targetedGroup.getGroupMemberSenderId(subject).GetByteString();
        	
        	byte[] senderId = targetedGroup.allocateSenderId();
        	
        	if (senderId == null) {
        		// All possible values are already in use for this OSCORE group
        		exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
        						 "No available Sender IDs in this OSCORE group");
        		return;
        	}
        	
        	int roles = targetedGroup.getGroupMemberRoles((targetedGroup.getGroupMemberName(subject)));
        	targetedGroup.setGroupMemberRoles(senderId, roles);
        	targetedGroup.setSenderIdToIdentity(subject, senderId);
        	
        	CBORObject publicKey = targetedGroup.getPublicKey(oldSenderId);
        	
        	// Store this client's public key under the new Sender ID
        	if (!targetedGroup.storePublicKey(senderId, publicKey)) {
        	    exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
        	    				 "Error when storing the public key");
        	    return;
        	}
        	// Delete this client's public key under the old Sender ID
        	targetedGroup.deletePublicKey(oldSenderId);
        	
        	
        	// Respond to the Key Renewal Request
        	
        	CBORObject myResponse = CBORObject.NewMap();
        	
        	// The new Sender ID assigned to the group member
        	myResponse.Add(Constants.GROUP_SENDER_ID, CBORObject.FromObject(senderId));
        	        	
        	byte[] responsePayload = myResponse.EncodeToBytes();
        	
        	Response coapResponse = new Response(CoAP.ResponseCode.CONTENT);
        	coapResponse.setPayload(responsePayload);
        	coapResponse.getOptions().setContentFormat(Constants.APPLICATION_ACE_GROUPCOMM_CBOR);

        	exchange.respond(coapResponse);
        	
        }
        
        
        @Override
        public void handleDELETE(CoapExchange exchange) {
        	System.out.println("DELETE request reached the GM");
        	
        	// Retrieve the entry for the target group, using the last path segment of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getParent().getParent().getName());
        	
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	String groupName = targetedGroup.getGroupName();
        	
        	// This should never happen if active groups are maintained properly
  	  		if (!groupName.equals(this.getParent().getParent().getName())) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
  				return;
  			}
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
        	
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen, due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
            
        	if (!targetedGroup.isGroupMember(subject)) {
        		// The requester is not a current group member.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to group members");
        		return;
        	}
        	
        	if (!(targetedGroup.getGroupMemberName(subject)).equals(this.getName())) {
        		// The requester is not the group member associated to this sub-resource.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to the group member associated to this sub-resource");
        		return;
        	}
            	
        	targetedGroup.removeGroupMemberBySubject(subject);
        	
        	// Respond to the Group Leaving Request
            
        	Response coapResponse = new Response(CoAP.ResponseCode.DELETED);

        	delete();
        	exchange.respond(coapResponse);
        	
        }
        
    }
    
    /**
     * Definition of the Group OSCORE group-membership sub-resource /nodes/NODENAME/pub-key
     * for the group members with node name "NODENAME"
     */
    public static class GroupOSCORESubResourceNodenamePubKey extends CoapResource {
    	
		/**
         * Constructor
         * @param resId  the resource identifier
         */
        public GroupOSCORESubResourceNodenamePubKey(String resId) {
            
            // set resource identifier
            super(resId);
            
            // set display name
            getAttributes().setTitle("Group OSCORE Group-Membership Sub-Resource \"nodes/NODENAME/pub-key\" " + resId);
            
        }

        @Override
        public void handlePOST(CoapExchange exchange) {
        	System.out.println("POST request reached the GM");
        	
        	// Retrieve the entry for the target group, using the last path segment
        	// of the URI path as the name of the OSCORE group
        	GroupInfo targetedGroup = activeGroups.get(this.getParent().getParent().getParent().getName());
        	
        	// This should never happen if active groups are maintained properly
        	if (targetedGroup == null) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
            	return;
        	}
        	
        	String groupName = targetedGroup.getGroupName();
        	
        	// This should never happen if active groups are maintained properly
  	  		if (!groupName.equals(this.getParent().getParent().getParent().getName())) {
            	exchange.respond(CoAP.ResponseCode.SERVICE_UNAVAILABLE,
            					 "Error when retrieving material for the OSCORE group");
  				return;
  			}
        	
        	String subject = null;
        	Request request = exchange.advanced().getCurrentRequest();
            
            try {
				subject = CoapReq.getInstance(request).getSenderId();
			} catch (AceException e) {
			    System.err.println("Error while retrieving the client identity: " + e.getMessage());
			}
            if (subject == null) {
            	// At this point, this should not really happen, due to the earlier check at the Token Repository
            	exchange.respond(CoAP.ResponseCode.UNAUTHORIZED,
            					 "Unauthenticated client tried to get access");
            	return;
            }
        	
        	if (!targetedGroup.isGroupMember(subject)) {
        		// The requester is not a current group member.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to group members");
        		return;
        	}
        	
        	if (!(targetedGroup.getGroupMemberName(subject)).equals(this.getParent().getName())) {
        		// The requester is not the group member associated to this sub-resource.
        		exchange.respond(CoAP.ResponseCode.FORBIDDEN,
        						 "Operation permitted only to the group member associated to this sub-resource");
        		return;
        	}
        	
        	if (targetedGroup.getGroupMemberRoles((targetedGroup.getGroupMemberName(subject))) ==
        										  (1 << Constants.GROUP_OSCORE_MONITOR)) {
        		// The requester is a monitor, hence it is not supposed to have a Sender ID.
        		exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
        						 "Operation not permitted to members that are only monitors");
        		return;
        	}
        	
        	byte[] requestPayload = exchange.getRequestPayload();
        	
        	if(requestPayload == null) {
        	    exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "A payload must be present");
        	    return;
        	}

        	CBORObject PublicKeyUpdateRequest = CBORObject.DecodeFromBytes(requestPayload);

        	// The payload of the Public Key Update Request must be a CBOR Map
        	if (!PublicKeyUpdateRequest.getType().equals(CBORType.Map)) {
        	    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
        	    				 "The payload must be a CBOR map");
        	    return;
        	}
        	
        	if (!PublicKeyUpdateRequest.ContainsKey(Constants.CLIENT_CRED)) {
        	    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
        	    				 "Missing parameter: " + "'client_cred'");
        	    return;
        	}
        	
        	if (!PublicKeyUpdateRequest.ContainsKey(Constants.CNONCE)) {
        	    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
        	    				 "Missing parameter: " + "'cnonce'");
        	    return;
        	}
        	
        	if (!PublicKeyUpdateRequest.ContainsKey(Constants.CLIENT_CRED_VERIFY)) {
        	    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
        	    				 "Missing parameter: " + "'client_cred_verify'");
        	    return;
        	}
        	
        	// Retrieve 'client_cred'
        	CBORObject clientCred = PublicKeyUpdateRequest.get(CBORObject.FromObject(Constants.CLIENT_CRED));
        	
			// client_cred cannot be Null
			if (clientCred == null) {
			    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
			    				 "The parameter 'client_cred' cannot be Null");
			    return;
			}
        	
			OneKey publicKey = null;
			boolean valid = false;
			
			switch(targetedGroup.getPubKeyEnc()) {
			    case Constants.COSE_HEADER_PARAM_CCS:
			        if (clientCred.getType() == CBORType.Map) {
			        	// Retrieve the public key from the CCS
			            publicKey = Util.ccsToOneKey(clientCred);
			            valid = true;
			        }
			        else {
			            Assert.fail("Invalid format of public key");
			        }
			        break;
			    case Constants.COSE_HEADER_PARAM_CWT:
			        if (clientCred.getType() == CBORType.Array) {
			            // Retrieve the public key from the CWT
			            // TODO
			        }
			        else {
			            Assert.fail("Invalid format of public key");
			        }
			        break;
			    case Constants.COSE_HEADER_PARAM_X5CHAIN:
			        // Retrieve the public key from the certificate
			        if (clientCred.getType() == CBORType.ByteString) {
			            // TODO
			        }
			        else {
			            Assert.fail("Invalid format of public key");
			        }
			        break;
			    default:
			        Assert.fail("Invalid format of public key");
			}
			if (publicKey == null ||  valid == false) {
	    	    exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "Invalid public key format");
			    return;
			}
        	
			// Sanity check on the type of public key        		
			if (targetedGroup.getSignAlg().equals(AlgorithmID.ECDSA_256) ||
			    targetedGroup.getSignAlg().equals(AlgorithmID.ECDSA_384) ||
				targetedGroup.getSignAlg().equals(AlgorithmID.ECDSA_512)) {
				
				// Invalid public key format
				if (!publicKey.get(KeyKeys.KeyType).
						equals(targetedGroup.getSignParams().get(0).get(0)) || // alg capability: key type
				    !publicKey.get(KeyKeys.KeyType).
				    	equals(targetedGroup.getSignParams().get(1).get(0)) || // key capability: key type
				    !publicKey.get(KeyKeys.EC2_Curve).
				    	equals(targetedGroup.getSignParams().get(1).get(1)))   // key capability: curve
				{ 
				        
				    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
				    		"Invalid public key for the algorithm and parameters used in the OSCORE group");
				    return;
				            
				}
			
			}
			
			if (targetedGroup.getSignAlg().equals(AlgorithmID.EDDSA)) {
			
				// Invalid public key format
				if (!publicKey.get(KeyKeys.KeyType).
						equals(targetedGroup.getSignParams().get(0).get(0)) || // alg capability: key type
				    !publicKey.get(KeyKeys.KeyType).
				    	equals(targetedGroup.getSignParams().get(1).get(0)) || // key capability: key type
				    !publicKey.get(KeyKeys.OKP_Curve).
				    	equals(targetedGroup.getSignParams().get(1).get(1)))   // key capability: curve
				{
				            
				    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
				    				 "Invalid public key for the algorithm and parameters used in the OSCORE group");
				    return;
				        
				}
			    
			}
        	
			// Retrieve the proof-of-possession nonce from the Client
			CBORObject cnonce = PublicKeyUpdateRequest.get(CBORObject.FromObject(Constants.CNONCE));

			// A client nonce must be included for proof-of-possession
			if (cnonce == null) {
			    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
			    				 "The parameter 'cnonce' cannot be Null");
			    return;
			}

			// The client nonce must be wrapped in a binary string
			if (!cnonce.getType().equals(CBORType.ByteString)) {
			    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
			    				 "The parameter 'cnonce' must be a CBOR byte string");
			    return;
			}

			
			
			// Check the proof-of-possession evidence over (scope | rsnonce | cnonce), using the Client's public key
			CBORObject clientPopEvidence = PublicKeyUpdateRequest.get(CBORObject.FromObject(Constants.CLIENT_CRED_VERIFY));

			// A client PoP evidence must be included
			if (clientPopEvidence == null) {
			    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
			    				 "The parameter 'client_cred_verify' cannot be Null");
			    return;
			}

			// The client PoP evidence must be wrapped in a binary string
			if (!clientPopEvidence.getType().equals(CBORType.ByteString)) {
			    exchange.respond(CoAP.ResponseCode.BAD_REQUEST,
			    				 "The parameter 'client_cred_verify' must be a CBOR byte string");
			    return;
			}

			byte[] rawClientPopEvidence = clientPopEvidence.GetByteString();
        	
			PublicKey pubKey = null;
			try {
			    pubKey = publicKey.AsPublicKey();
			} catch (CoseException e) {
			    System.out.println(e.getMessage());
			    exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
			    				 "Failed to use the Client's public key to verify the PoP evidence");
			    return;
			}
			if (pubKey == null) {
			    exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
			    				 "Failed to use the Client's public key to verify the PoP evidence");
			    return;
			}

			// Rebuild the original 'scope' from the Join Request
	        CBORObject cborArrayScope = CBORObject.NewArray();
	        int myRoles = targetedGroup.getGroupMemberRoles((targetedGroup.getGroupMemberName(subject)));
	        cborArrayScope.Add(groupName);
	        cborArrayScope.Add(myRoles);
	        byte[] scope = cborArrayScope.EncodeToBytes();

			// Retrieve the original 'rsnonce' specified in the Token POST response
			String rsNonceString = TokenRepository.getInstance().getRsnonce(subject);
            if(rsNonceString == null) {
            	// Return an error response, with a new nonce for PoP of the Client's private key
        	    CBORObject responseMap = CBORObject.NewMap();
                byte[] rsnonce = new byte[8];
                new SecureRandom().nextBytes(rsnonce);
                responseMap.Add(Constants.KDCCHALLENGE, rsnonce);
                TokenRepository.getInstance().setRsnonce(subject, Base64.getEncoder().encodeToString(rsnonce));
                byte[] responsePayload = responseMap.EncodeToBytes();
            	exchange.respond(CoAP.ResponseCode.BAD_REQUEST, responsePayload, Constants.APPLICATION_ACE_CBOR);
            	return;
            }
			byte[] rsnonce = Base64.getDecoder().decode(rsNonceString);
			
			int offset = 0;
			
			byte[] serializedScopeCBOR = CBORObject.FromObject(scope).EncodeToBytes();
			byte[] serializedGMNonceCBOR = CBORObject.FromObject(rsnonce).EncodeToBytes();
			byte[] serializedCNonceCBOR = cnonce.EncodeToBytes();
			byte[] popInput = new byte [serializedScopeCBOR.length +
			                            serializedGMNonceCBOR.length +
			                            serializedCNonceCBOR.length];
			System.arraycopy(serializedScopeCBOR, 0, popInput, offset, serializedScopeCBOR.length);
			offset += serializedScopeCBOR.length;
			System.arraycopy(serializedGMNonceCBOR, 0, popInput, offset, serializedGMNonceCBOR.length);
			offset += serializedGMNonceCBOR.length;
			System.arraycopy(serializedCNonceCBOR, 0, popInput, offset, serializedCNonceCBOR.length);

			// The group mode is used. The PoP evidence is a signature
			if (targetedGroup.getMode() != Constants.GROUP_OSCORE_PAIRWISE_MODE_ONLY) {
			    int signKeyCurve = 0;

			    if (publicKey.get(KeyKeys.KeyType).equals(org.eclipse.californium.cose.KeyKeys.KeyType_EC2))
			        signKeyCurve = publicKey.get(KeyKeys.EC2_Curve).AsInt32();
			    else if (publicKey.get(KeyKeys.KeyType).equals(org.eclipse.californium.cose.KeyKeys.KeyType_OKP))
			        signKeyCurve = publicKey.get(KeyKeys.OKP_Curve).AsInt32();

			    // This should never happen, due to the previous sanity checks
			    if (signKeyCurve == 0) {
			        exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
			        				 "Error when setting up the signature verification");
			        return;
			    }

			    // Invalid Client's PoP signature
			    if (!Util.verifySignature(signKeyCurve, pubKey, popInput, rawClientPopEvidence)) {
			    	exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "Invalid PoP Signature");
			        return;
			    }
			}
			// Only the pairwise mode is used. The PoP evidence is a MAC
			else {
			    // TODO
			}
			
			byte[] senderId = targetedGroup.getGroupMemberSenderId(subject).GetByteString();
			
			if (!targetedGroup.storePublicKey(senderId, clientCred)) {
			    exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR,
			    				 "Error when storing the public key");
			    return;
			}
			
        	// Respond to the Public Key Update Request     	
        	
        	Response coapResponse = new Response(CoAP.ResponseCode.CHANGED);
        	
        	exchange.respond(coapResponse);
        	
        }
        
    }
    
    
    private static OscoreAuthzInfoGroupOSCORE ai = null;
    
    private static CoapServer rs = null;
    
    private static CoapDeliverer dpd = null;
    
    private static Map<String, Map<String, Set<Short>>> myScopes = new HashMap<>();
    
    private static GroupOSCOREJoinValidator valid = null;
   
    
    /**
     * The CoAPs server for testing, run this before running the Junit tests.
     *  
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
    	
    	rand = new Random();
    	
        //Set logging for slf4/blah
        BasicConfigurator.configure();
    	
        //Set java.util.logging
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.FINEST);
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(Level.FINEST);
        }
    	
		new File(testpath + "tokens.json").delete();
    	
		// install needed cryptography providers
		try {
			org.eclipse.californium.oscore.InstallCryptoProviders.installProvider();
		} catch (Exception e) {
			System.err.println("Failed to install cryptography providers.");
			e.printStackTrace();
		}
    	
    	final String groupName1 = "aaaaaa570000";
    	
        //Set up DTLSProfileTokenRepository
        Set<Short> actions = new HashSet<>();
        actions.add(Constants.GET);
        Map<String, Set<Short>> myResource = new HashMap<>();
        myResource.put("helloWorld", actions);
        myScopes.put("r_helloWorld", myResource);
        
        Set<Short> actions2 = new HashSet<>();
        actions2.add(Constants.GET);
        Map<String, Set<Short>> myResource2 = new HashMap<>();
        myResource2.put("temp", actions2);
        myScopes.put("r_temp", myResource2);
        
        // Adding the group-membership resource, with group name "aaaaaa570000".
        Map<String, Set<Short>> myResource3 = new HashMap<>();
        Set<Short> actions3 = new HashSet<>();
        actions3.add(Constants.FETCH);
        myResource3.put(rootGroupMembershipResource, actions3);
        actions3 = new HashSet<>();
        actions3.add(Constants.GET);
        actions3.add(Constants.POST);
        myResource3.put(rootGroupMembershipResource + "/" + groupName1, actions3);
        actions3 = new HashSet<>();
        actions3.add(Constants.GET);
        actions3.add(Constants.FETCH);
        myResource3.put(rootGroupMembershipResource + "/" + groupName1 + "/pub-key", actions3);
        actions3 = new HashSet<>();
        actions3.add(Constants.GET);
        myResource3.put(rootGroupMembershipResource + "/" + groupName1 + "/gm-pub-key", actions3);
        myResource3.put(rootGroupMembershipResource + "/" + groupName1 + "/verif-data", actions3);
        myResource3.put(rootGroupMembershipResource + "/" + groupName1 + "/num", actions3);
        myResource3.put(rootGroupMembershipResource + "/" + groupName1 + "/active", actions3);
        myResource3.put(rootGroupMembershipResource + "/" + groupName1 + "/policies", actions3);
        myScopes.put(rootGroupMembershipResource + "/" + groupName1, myResource3);
        
        // Adding another group-membership resource, with group name "fBBBca570000".
        // There will NOT be a token enabling the access to this resource.
        Map<String, Set<Short>> myResource4 = new HashMap<>();
        Set<Short> actions4 = new HashSet<>();
        actions4.add(Constants.GET);
        actions4.add(Constants.POST);
        myResource4.put(rootGroupMembershipResource + "/" + "fBBBca570000", actions4);
        myScopes.put(rootGroupMembershipResource + "/", myResource4);
        
    	final String groupName2 = "bbbbbb570000";
        
        // Adding the group-membership resource, with group name "bbbbbb570000".
        Map<String, Set<Short>> myResource5 = new HashMap<>();
        Set<Short> actions5 = new HashSet<>();
        actions5.add(Constants.FETCH);
        myResource5.put(rootGroupMembershipResource, actions5);
        actions5 = new HashSet<>();
        actions5.add(Constants.GET);
        actions5.add(Constants.POST);
        myResource5.put(rootGroupMembershipResource + "/" + groupName2, actions5);
        actions5 = new HashSet<>();
        actions5.add(Constants.GET);
        actions5.add(Constants.FETCH);
        myResource5.put(rootGroupMembershipResource + "/" + groupName2 + "/pub-key", actions5);
        actions5 = new HashSet<>();
        actions5.add(Constants.GET);
        myResource5.put(rootGroupMembershipResource + "/" + groupName2 + "/gm-pub-key", actions5);
        myResource5.put(rootGroupMembershipResource + "/" + groupName2 + "/verif-data", actions5);
        myResource5.put(rootGroupMembershipResource + "/" + groupName2 + "/num", actions5);
        myResource5.put(rootGroupMembershipResource + "/" + groupName2 + "/active", actions5);
        myResource5.put(rootGroupMembershipResource + "/" + groupName2 + "/policies", actions5);
        myScopes.put(rootGroupMembershipResource + "/" + groupName2, myResource5);
        
        String rsId = "rs1";
        
        Set<String> auds = new HashSet<>();
        auds.add("aud1"); // Simple test audience
        auds.add("rs2"); // OSCORE Group Manager (This audience expects scopes as Byte Strings)
        valid = new GroupOSCOREJoinValidator(auds, myScopes, rootGroupMembershipResource);
        
        // Include this audience in the list of audiences recognized as OSCORE Group Managers 
        valid.setGMAudiences(Collections.singleton("rs2"));
        
        // Include the root group-membership resource for Group OSCORE.
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource));
        
        // For each OSCORE group, include the associated group-membership resource and its sub-resources
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName1));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName1 + "/pub-key"));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName1 + "/gm-pub-key"));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName1 + "/verif-data"));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName1 + "/num"));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName1 + "/active"));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName1 + "/policies"));
        
        // For each OSCORE group, include the associated group-membership resource and its sub-resources (2nd group)
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName2));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName2 + "/pub-key"));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName2 + "/gm-pub-key"));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName2 + "/verif-data"));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName2 + "/num"));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName2 + "/active"));
        valid.setJoinResources(Collections.singleton(rootGroupMembershipResource + "/" + groupName2 + "/policies"));
        
		String tokenFile = testpath + "tokens.json";
    	//Delete lingering old token files
    	new File(tokenFile).delete();
        
        //Set up COSE parameters
        COSEparams coseP = new COSEparams(MessageTag.Encrypt0, AlgorithmID.AES_CCM_16_64_128, AlgorithmID.Direct);
        CwtCryptoCtx ctx = CwtCryptoCtx.encrypt0(key128_token, coseP.getAlg().AsCBOR());

        // Set up the inner Authz-Info library
        ai = new OscoreAuthzInfoGroupOSCORE(Collections.singletonList("AS"), 
        	 new KissTime(), null, rsId, valid, ctx, tokenFile, valid, false);
        
        // Provide the authz-info endpoint with the set of active OSCORE groups
        ai.setActiveGroups(activeGroups);
      
        // The related test in TestDtlspClientGroupOSCORE still works with this server even with a single
        // AuthzInfoGroupOSCORE 'ai', but only because 'ai' is constructed with a null Introspection Handler.
        // 
        // If provided, a proper Introspection Handler would require to take care of multiple audiences,
        // rather than of a single RS as IntrospectionHandler4Tests does. This is already admitted in the
        // Java interface IntrospectionHandler.
      
        //Add a test token to authz-info
        byte[] key128 = key128_token;
        Map<Short, CBORObject> params = new HashMap<>(); 
        params.put(Constants.SCOPE, CBORObject.FromObject("r_temp"));
        params.put(Constants.AUD, CBORObject.FromObject("aud1"));
        params.put(Constants.CTI, CBORObject.FromObject("token1".getBytes(Constants.charset)));
        params.put(Constants.ISS, CBORObject.FromObject("AS"));

        OneKey key = new OneKey();
        key.add(KeyKeys.KeyType, KeyKeys.KeyType_Octet);
      
        byte[] kid  = new byte[] {0x01, 0x02, 0x03};
        CBORObject kidC = CBORObject.FromObject(kid);
        key.add(KeyKeys.KeyId, kidC);
        key.add(KeyKeys.Octet_K, CBORObject.FromObject(key128));

        CBORObject cnf = CBORObject.NewMap();
        cnf.Add(Constants.COSE_KEY_CBOR, key.AsCBOR());
        params.put(Constants.CNF, cnf);
        CWT token = new CWT(params);
        ai.processMessage(new LocalMessage(0, null, null, token.encode(ctx)));  
      
  	    AsRequestCreationHints asi 
  	    	= new AsRequestCreationHints("coaps://blah/authz-info/", null, false, false);
  	    Resource hello = new HelloWorldResource();
  	    Resource temp = new TempResource();
  	    Resource groupOSCORERootMembership = new GroupOSCORERootMembershipResource(rootGroupMembershipResource);
  	    Resource join = new GroupOSCOREJoinResource(groupName1);
  	    
        // Add the /pub-key sub-resource
        Resource pubKeySubResource = new GroupOSCORESubResourcePubKey("pub-key");
        join.add(pubKeySubResource);
        // Add the /gm-pub-key sub-resource
        Resource gmPubKeySubResource = new GroupOSCORESubResourcePubKey("gm-pub-key");
        join.add(gmPubKeySubResource);
        // Add the /verif-data sub-resource
        Resource verifDataSubResource = new GroupOSCORESubResourcePubKey("verif-data");
        join.add(verifDataSubResource);
        // Add the /num sub-resource
        Resource numSubResource = new GroupOSCORESubResourceNum("num");
        join.add(numSubResource);
  	    // Add the /active sub-resource
        Resource activeSubResource = new GroupOSCORESubResourceActive("active");
  	    join.add(activeSubResource);
  	    // Add the /policies sub-resource
        Resource policiesSubResource = new GroupOSCORESubResourcePolicies("policies");
  	    join.add(policiesSubResource);
        // Add the /nodes sub-resource, as root to actually accessible per-node sub-resources
        Resource nodesSubResource = new GroupOSCORESubResourceNodes("nodes");
  	    join.add(nodesSubResource);
  	    
  	    // For the second group
  	    Resource join2 = new GroupOSCOREJoinResource(groupName2);
  	    
        // Add the /pub-key sub-resource
        Resource pubKeySubResource2 = new GroupOSCORESubResourcePubKey("pub-key");
        join2.add(pubKeySubResource2);
        // Add the /gm-pub-key sub-resource
        Resource gmPubKeySubResource2 = new GroupOSCORESubResourcePubKey("gm-pub-key");
        join2.add(gmPubKeySubResource2);
        // Add the /verif-data sub-resource
        Resource verifDataSubResource2 = new GroupOSCORESubResourcePubKey("verif-data");
        join2.add(verifDataSubResource2);
        // Add the /num sub-resource
        Resource numSubResource2 = new GroupOSCORESubResourceNum("num");
        join2.add(numSubResource2);
  	    // Add the /active sub-resource
        Resource activeSubResource2 = new GroupOSCORESubResourceActive("active");
  	    join2.add(activeSubResource2);
  	    // Add the /policies sub-resource
        Resource policiesSubResource2 = new GroupOSCORESubResourcePolicies("policies");
  	    join2.add(policiesSubResource2);
        // Add the /nodes sub-resource, as root to actually accessible per-node sub-resources
        Resource nodesSubResource2 = new GroupOSCORESubResourceNodes("nodes");
  	    join2.add(nodesSubResource2);
  	    
  	    Resource authzInfo = new CoapAuthzInfo(ai);
      
  
    	// Create the OSCORE group
        final byte[] masterSecret = { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                					  (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
                					  (byte) 0x09, (byte) 0x0A, (byte) 0x0B, (byte) 0x0C,
                					  (byte) 0x0D, (byte) 0x0E, (byte) 0x0F, (byte) 0x10 };

        final byte[] masterSalt =   { (byte) 0x9e, (byte) 0x7c, (byte) 0xa9, (byte) 0x22,
                					  (byte) 0x23, (byte) 0x78, (byte) 0x63, (byte) 0x40 };

        final AlgorithmID hkdf = AlgorithmID.HKDF_HMAC_SHA_256;
        final int pubKeyEnc = Constants.COSE_HEADER_PARAM_CCS;
        
  	    // Uncomment to set ECDSA with curve P-256 for countersignatures
  	    // int signKeyCurve = KeyKeys.EC2_P256.AsInt32();
  	    
  	    // Uncomment to set EDDSA with curve Ed25519 for countersignatures
  	    int signKeyCurve = KeyKeys.OKP_Ed25519.AsInt32();
  	    
  	    // Uncomment to set curve P-256 for pairwise key derivation
  	    // int ecdhKeyCurve = KeyKeys.EC2_P256.AsInt32();
  	    
  	    // Uncomment to set curve X25519 for pairwise key derivation
  	    int ecdhKeyCurve = KeyKeys.OKP_X25519.AsInt32();
        
    	/*
    	// Generate a pair of asymmetric keys and print them in base 64 (whole version, then public only)
        
        OneKey testKey = null;
 		
 		if (signKeyCurve == KeyKeys.EC2_P256.AsInt32())
 			testKey = OneKey.generateKey(AlgorithmID.ECDSA_256);
    	
    	if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32())
    		testKey = OneKey.generateKey(AlgorithmID.EDDSA);
        
    	byte[] testKeyBytes = testKey.EncodeToBytes();
    	String testKeyBytesBase64 = Base64.getEncoder().encodeToString(testKeyBytes);
    	System.out.println(testKeyBytesBase64);
    	
    	OneKey testPublicKey = testKey.PublicKey();
    	byte[] testPublicKeyBytes = testPublicKey.EncodeToBytes();
    	String testPublicKeyBytesBase64 = Base64.getEncoder().encodeToString(testPublicKeyBytes);
    	System.out.println(testPublicKeyBytesBase64);
    	*/
        
  	    
        AlgorithmID signEncAlg = null;
        AlgorithmID signAlg = null;
        CBORObject signAlgCapabilities = null;
        CBORObject signKeyCapabilities = null;
        CBORObject signParams = null;
        
        AlgorithmID alg = null;
        AlgorithmID ecdhAlg = null;
        CBORObject ecdhAlgCapabilities = null;
        CBORObject ecdhKeyCapabilities = null;
        CBORObject ecdhParams = null;
        
        if (signKeyCurve == 0 && ecdhKeyCurve == 0) {
        	System.out.println("Both the signature key curve and the ECDH key curve are unspecified");
        	return;
        }
        int mode = Constants.GROUP_OSCORE_GROUP_PAIRWISE_MODE;
        if (signKeyCurve != 0 && ecdhKeyCurve == 0)
        	mode = Constants.GROUP_OSCORE_GROUP_MODE_ONLY;
        else if (signKeyCurve == 0 && ecdhKeyCurve != 0)
        	mode = Constants.GROUP_OSCORE_PAIRWISE_MODE_ONLY;
        
        
        if (mode != Constants.GROUP_OSCORE_PAIRWISE_MODE_ONLY) {
            signEncAlg = AlgorithmID.AES_CCM_16_64_128;
            signAlgCapabilities = CBORObject.NewArray();
            signKeyCapabilities = CBORObject.NewArray();
            signParams = CBORObject.NewArray();
        	
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
        }
    	
        if (mode != Constants.GROUP_OSCORE_GROUP_MODE_ONLY) {
	        alg = AlgorithmID.AES_CCM_16_64_128;
        	ecdhAlg = AlgorithmID.ECDH_SS_HKDF_256;
	        ecdhAlgCapabilities = CBORObject.NewArray();
	        ecdhKeyCapabilities = CBORObject.NewArray();
	        ecdhParams = CBORObject.NewArray();
	        
	        // ECDSA_256
	        if (ecdhKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
	        	ecdhAlgCapabilities.Add(KeyKeys.KeyType_EC2); // Key Type
	        	ecdhKeyCapabilities.Add(KeyKeys.KeyType_EC2); // Key Type
	        	ecdhKeyCapabilities.Add(KeyKeys.EC2_P256); // Curve
	        }
	        
	        // EDDSA (Ed25519)
	        if (ecdhKeyCurve == KeyKeys.OKP_X25519.AsInt32()) {
	        	ecdhAlgCapabilities.Add(KeyKeys.KeyType_OKP); // Key Type
	        	ecdhKeyCapabilities.Add(KeyKeys.KeyType_OKP); // Key Type
	        	ecdhKeyCapabilities.Add(KeyKeys.OKP_X25519); // Curve
	        }
	        
	    	ecdhParams.Add(ecdhAlgCapabilities);
	    	ecdhParams.Add(ecdhKeyCapabilities);
    	
        }
        
        
        // Prefix (4 byte) and Epoch (2 bytes)
        // All Group IDs have the same prefix size, but can have different Epoch sizes
        //
        // The current Group ID is: 0xfeedca57f05c, with Prefix 0xfeedca57 and current Epoch 0xf05c
        // aaaaaa570000
    	final byte[] groupIdPrefix = new byte[] { (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0x57 };
    	byte[] groupIdEpoch = new byte[] { (byte) 0xf0, (byte) 0x5c }; // Up to 4 bytes
    	
    	
    	// Set the asymmetric key pair and public key of the Group Manager
    	
    	// Serialization of the COSE Key including both private and public part
    	byte[] gmKeyPairBytes = null;
    	    	
    	// The asymmetric key pair and public key of the Group Manager (ECDSA_256)
    	if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
    		System.out.println("More config required.");
			gmKeyPairBytes = hexStringToByteArray(
					"a60102032620012158202236658ca675bb62d7b24623db0453a3b90533b7c3b221cc1c2c73c4e919d540225820770916bc4c97c3c46604f430b06170c7b3d6062633756628c31180fa3bb65a1b2358204a7b844a4c97ef91ed232aa564c9d5d373f2099647f9e9bd3fe6417a0d0f91ad");
    	}
    	    
    	// The asymmetric key pair and public key of the Group Manager (EDDSA - Ed25519)
    	if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
			gmKeyPairBytes = hexStringToByteArray(
					"a5010103272006215820c6ec665e817bd064340e7c24bb93a11e8ec0735ce48790f9c458f7fa340b8ca3235820d0a2ce11b2ba614b048903b72638ef4a3b0af56e1a60c6fb6706b0c1ad8a14fb");
    	}

    	OneKey gmKeyPair = null;
    	gmKeyPair = new OneKey(CBORObject.DecodeFromBytes(gmKeyPairBytes));
    	

    	// Serialization of the public key, according to the format used in the group
    	byte[] gmPublicKey = null;
    	
    	/*
    	// Build the public key according to the format used in the group
    	// Note: most likely, the result will NOT follow the required deterministic
    	//       encoding in byte lexicographic order, and it has to be adjusted offline
    	switch (pubKeyEnc) {
        case Constants.COSE_HEADER_PARAM_CCS:
            // A CCS including the public key
        	String subjectName = "";
            gmPublicKey = Util.oneKeyToCCS(gmKeyPair, subjectName);
            break;
        case Constants.COSE_HEADER_PARAM_CWT:
            // A CWT including the public key
            // TODO
            break;
        case Constants.COSE_HEADER_PARAM_X5CHAIN:
            // A certificate including the public key
            // TODO
            break;
    	}
    	*/
    	
    	
    	switch (pubKeyEnc) {
	        case Constants.COSE_HEADER_PARAM_CCS:
	            // A CCS including the public key
	        	if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
	        		System.out.println("More config required.");
				gmPublicKey = hexStringToByteArray(
						"A2026008A101A50102032620012158202236658CA675BB62D7B24623DB0453A3B90533B7C3B221CC1C2C73C4E919D540225820770916BC4C97C3C46604F430B06170C7B3D6062633756628C31180FA3BB65A1B");
	        	}
	        	if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
				gmPublicKey = hexStringToByteArray(
						"A2026008A101A4010103272006215820C6EC665E817BD064340E7C24BB93A11E8EC0735CE48790F9C458F7FA340B8CA3");
	        	}
	            break;
	        case Constants.COSE_HEADER_PARAM_CWT:
	            // A CWT including the public key
	            // TODO
	        	gmPublicKey = null;
	            break;
	        case Constants.COSE_HEADER_PARAM_X5CHAIN:
	            // A certificate including the public key
	            // TODO
	        	gmPublicKey = null;
	            break;
    	}
    	
    	GroupInfo myGroup = new GroupInfo(groupName1,
						                  masterSecret,
						                  masterSalt,
						                  groupIdPrefixSize,
						                  groupIdPrefix,
						                  groupIdEpoch.length,
						                  Util.bytesToInt(groupIdEpoch),
						                  prefixMonitorNames,
						                  nodeNameSeparator,
						                  hkdf,
						                  pubKeyEnc,
						                  mode,
						                  signEncAlg,
						                  signAlg,
						                  signParams,
    			                          alg,
    			                          ecdhAlg,
    			                          ecdhParams,
    			                          null,
    			                          gmKeyPair,
    			                          gmPublicKey);
        
    	myGroup.setStatus(true);
    	
    	byte[] mySid;
    	String myName;
    	String mySubject;
    	OneKey myKey;
    	
    	
    	// Add a group member
    	mySid = idClient2;
    	if (!myGroup.allocateSenderId(mySid))
    		stop();
    	myName = myGroup.allocateNodeName(mySid);
    	mySubject = "clientX";
    	
    	int roles = 0;
    	roles = Util.addGroupOSCORERole(roles, Constants.GROUP_OSCORE_REQUESTER);
    	
    	if (!myGroup.addGroupMember(mySid, myName, roles, mySubject))
    		return;
    	
    	// Set the public key of the group member with Sender ID 0x52
    	
    	// The serialization of the COSE Key, including only the public part
    	byte[] coseKeyPub1 = null;
    	if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
    		System.out.println("More config required.");
			coseKeyPub1 = hexStringToByteArray(
					"a501020326200121582035f3656092e1269aaaee6262cd1c0d9d38ed78820803305bc8ea41702a50b3af2258205d31247c2959e7b7d3f62f79622a7082ff01325fc9549e61bb878c2264df4c4f");
    	}
    	if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
			coseKeyPub1 = hexStringToByteArray(
					"a401010327200621582077ec358c1d344e41ee0e87b8383d23a2099acd39bdf989ce45b52e887463389b");
    	}
    	
    	// Serialization of the public key, according to the format used in the group
    	byte[] pubKey1 = null;
    	
    	/*
    	// Build the public key according to the format used in the group
    	// Note: most likely, the result will NOT follow the required deterministic
    	//       encoding in byte lexicographic order, and it has to be adjusted offline
    	OneKey coseKeyPub1OneKey = null;
    	coseKeyPub1OneKey = new OneKey(CBORObject.DecodeFromBytes(coseKeyPub1));
    	switch (pubKeyEnc) {
        case Constants.COSE_HEADER_PARAM_CCS:
            // A CCS including the public key
        	String subjectName = "";
        	pubKey1 = Util.oneKeyToCCS(coseKeyPub1OneKey, subjectName);
            break;
        case Constants.COSE_HEADER_PARAM_CWT:
            // A CWT including the public key
            // TODO
        	pubKey1 = null;
            break;
        case Constants.COSE_HEADER_PARAM_X5CHAIN:
            // A certificate including the public key
            // TODO
        	pubKey1 = null;
            break;
    	}
    	*/

    	switch (pubKeyEnc) {
	        case Constants.COSE_HEADER_PARAM_CCS:
	            // A CCS including the public key
	        	if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
	        		System.out.println("More config required.");
	        		pubKey1 = hexStringToByteArray(
						"A2026008A101A501020326200121582035F3656092E1269AAAEE6262CD1C0D9D38ED78820803305BC8EA41702A50B3AF2258205D31247C2959E7B7D3F62F79622A7082FF01325FC9549E61BB878C2264DF4C4F");
	        	}
	        	if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
				pubKey1 = hexStringToByteArray(
						"A2026008A101A401010327200621582077EC358C1D344E41EE0E87B8383D23A2099ACD39BDF989CE45B52E887463389B");
	        	}
	            break;
	        case Constants.COSE_HEADER_PARAM_CWT:
	            // A CWT including the public key
	            // TODO
	        	pubKey1 = null;
	            break;
	        case Constants.COSE_HEADER_PARAM_X5CHAIN:
	            // A certificate including the public key
	            // TODO
	        	pubKey1 = null;
	            break;
    	}
    	
    	myGroup.storePublicKey(mySid, CBORObject.FromObject(pubKey1));
    	
    	// Add a group member
    	mySid = idClient3;
    	if (!myGroup.allocateSenderId(mySid))
    		stop();
    	myName = myGroup.allocateNodeName(mySid);
    	mySubject = "clientY";
    	
    	roles = 0;
    	roles = Util.addGroupOSCORERole(roles, Constants.GROUP_OSCORE_REQUESTER);
    	roles = Util.addGroupOSCORERole(roles, Constants.GROUP_OSCORE_RESPONDER);
    	
    	if (!myGroup.addGroupMember(mySid, myName, roles, mySubject))
    		return;
    	
    	// The serialization of the COSE Key, including only the public part
    	byte[] coseKeyPub2 = null;
    	if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
    		System.out.println("More config required.");
			coseKeyPub2 = hexStringToByteArray(
					"a50102032620012158209dfa6d63fd1515761460b7b02d54f8d7345819d2e5576c160d3148cc7886d5f122582076c81a0c1a872f1730c10317ab4f3616238fb23a08719e8b982b2d9321a2ef7d");
    	}
    	// Store the public key of the group member with Sender ID 0x52
    	if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
			coseKeyPub2 = hexStringToByteArray(
					"a4010103272006215820105b8c6a8c88019bf0c354592934130baa8007399cc2ac3be845884613d5ba2e");
    	}
    	
    	
    	// Serialization of the public key, according to the format used in the group
    	byte[] pubKey2 = null;
    	
    	/*
    	// Build the public key according to the format used in the group
    	// Note: most likely, the result will NOT follow the required deterministic
    	//       encoding in byte lexicographic order, and it has to be adjusted offline
    	OneKey coseKeyPub2OneKey = null;
    	coseKeyPub2OneKey = new OneKey(CBORObject.DecodeFromBytes(coseKeyPub2));
    	switch (pubKeyEnc) {
        case Constants.COSE_HEADER_PARAM_CCS:
            // A CCS including the public key
        	String subjectName = "";
        	pubKey2 = Util.oneKeyToCCS(coseKeyPub2OneKey, subjectName);
            break;
        case Constants.COSE_HEADER_PARAM_CWT:
            // A CWT including the public key
            // TODO
        	pubKey2 = null;
            break;
        case Constants.COSE_HEADER_PARAM_X5CHAIN:
            // A certificate including the public key
            // TODO
        	pubKey2 = null;
            break;
    	}
    	*/
    	
    	
    	switch (pubKeyEnc) {
	        case Constants.COSE_HEADER_PARAM_CCS:
	            // A CCS including the public key
	        	if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
	        		System.out.println("More config required.");
				    pubKey2 = hexStringToByteArray(
						"A2026008A101A50102032620012158209DFA6D63FD1515761460B7B02D54F8D7345819D2E5576C160D3148CC7886D5F122582076C81A0C1A872F1730C10317AB4F3616238FB23A08719E8B982B2D9321A2EF7D");
	        	}
	        	if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
				pubKey2 = hexStringToByteArray(
						"A2026008A101A4010103272006215820105B8C6A8C88019BF0C354592934130BAA8007399CC2AC3BE845884613D5BA2E");
	        	}
	            break;
	        case Constants.COSE_HEADER_PARAM_CWT:
	            // A CWT including the public key
	            // TODO
	        	pubKey2 = null;
	            break;
	        case Constants.COSE_HEADER_PARAM_X5CHAIN:
	            // A certificate including the public key
	            // TODO
	        	pubKey2 = null;
	            break;
    	}
    	
    	// ======= Create group for the second group this GM manages ()

    	final byte[] groupIdPrefix2 = new byte[] { (byte) 0xbb, (byte) 0xbb, (byte) 0xbb, (byte) 0x57 };
    	byte[] groupIdEpoch2 = new byte[] { (byte) 0xf0, (byte) 0x3a }; // Up to 4 bytes
    	GroupInfo myGroup2 = new GroupInfo(groupName2,
    							                  masterSecret,
    							                  masterSalt,
    							                  groupIdPrefixSize,
    							                  groupIdPrefix2,
    							                  groupIdEpoch2.length,
    							                  Util.bytesToInt(groupIdEpoch2),
    							                  prefixMonitorNames,
    							                  nodeNameSeparator,
    							                  hkdf,
    							                  pubKeyEnc,
    							                  mode,
    							                  signEncAlg,
    							                  signAlg,
    							                  signParams,
    	    			                          alg,
    	    			                          ecdhAlg,
    	    			                          ecdhParams,
    	    			                          null,
    	    			                          gmKeyPair,
    	    			                          gmPublicKey);
    	        
    	myGroup2.setStatus(true);
    	
    	// Add a group member
    	mySid = idClient2;
    	if (!myGroup2.allocateSenderId(mySid))
    		stop();
    	myName = myGroup2.allocateNodeName(mySid);
    	mySubject = "clientX";
    	
    	roles = 0;
    	roles = Util.addGroupOSCORERole(roles, Constants.GROUP_OSCORE_REQUESTER);
    	
    	if (!myGroup2.addGroupMember(mySid, myName, roles, mySubject))
    		return;
    	
    	/*
    	// Build the public key according to the format used in the group
    	// Note: most likely, the result will NOT follow the required deterministic
    	//       encoding in byte lexicographic order, and it has to be adjusted offline
    	OneKey coseKeyPub1OneKey = null;
    	coseKeyPub1OneKey = new OneKey(CBORObject.DecodeFromBytes(coseKeyPub1));
    	switch (pubKeyEnc) {
        case Constants.COSE_HEADER_PARAM_CCS:
            // A CCS including the public key
        	String subjectName = "";
        	pubKey1 = Util.oneKeyToCCS(coseKeyPub1OneKey, subjectName);
            break;
        case Constants.COSE_HEADER_PARAM_CWT:
            // A CWT including the public key
            // TODO
        	pubKey1 = null;
            break;
        case Constants.COSE_HEADER_PARAM_X5CHAIN:
            // A certificate including the public key
            // TODO
        	pubKey1 = null;
            break;
    	}
    	*/

    	switch (pubKeyEnc) {
	        case Constants.COSE_HEADER_PARAM_CCS:
	            // A CCS including the public key
	        	if (signKeyCurve == KeyKeys.EC2_P256.AsInt32()) {
	        		System.out.println("More config required.");
	        		pubKey1 = hexStringToByteArray(
						"A2026008A101A501020326200121582035F3656092E1269AAAEE6262CD1C0D9D38ED78820803305BC8EA41702A50B3AF2258205D31247C2959E7B7D3F62F79622A7082FF01325FC9549E61BB878C2264DF4C4F");
	        	}
	        	if (signKeyCurve == KeyKeys.OKP_Ed25519.AsInt32()) {
				pubKey1 = hexStringToByteArray(
						"A2026008A101A401010327200621582077EC358C1D344E41EE0E87B8383D23A2099ACD39BDF989CE45B52E887463389B");
	        	}
	            break;
	        case Constants.COSE_HEADER_PARAM_CWT:
	            // A CWT including the public key
	            // TODO
	        	pubKey1 = null;
	            break;
	        case Constants.COSE_HEADER_PARAM_X5CHAIN:
	            // A certificate including the public key
	            // TODO
	        	pubKey1 = null;
	            break;
    	}
    	
    	myGroup2.storePublicKey(mySid, CBORObject.FromObject(pubKey1));
    	
    	// Add a group member
    	mySid = idClient3;
    	if (!myGroup2.allocateSenderId(mySid))
    		stop();
    	myName = myGroup2.allocateNodeName(mySid);
    	mySubject = "clientY";
    	
    	roles = 0;
    	roles = Util.addGroupOSCORERole(roles, Constants.GROUP_OSCORE_REQUESTER);
    	roles = Util.addGroupOSCORERole(roles, Constants.GROUP_OSCORE_RESPONDER);
    	
    	if (!myGroup2.addGroupMember(mySid, myName, roles, mySubject))
    		return;

    	// ======= End of creating second group for this GM
    	
    	myGroup.storePublicKey(mySid, CBORObject.FromObject(pubKey2));
    	myGroup2.storePublicKey(mySid, CBORObject.FromObject(pubKey2));
    	
    	
    	// Add this OSCORE group to the set of active groups
    	// If the groupIdPrefix is 4 bytes in size, the map key can be a negative integer, but it is not a problem
    	activeGroups.put(groupName1, myGroup);
    	activeGroups.put(groupName2, myGroup2);
   	   
  	    
  	    rs = new CoapServer();
  	    rs.add(hello);
  	    rs.add(temp);
  	    rs.add(groupOSCORERootMembership);
  	    groupOSCORERootMembership.add(join);
  	    groupOSCORERootMembership.add(join2);
  	    rs.add(authzInfo);
      
  	    dpd = new CoapDeliverer(rs.getRoot(), null, asi); 

  	    rs.addEndpoint(new CoapEndpoint.Builder()
                .setCoapStackFactory(new OSCoreCoapStackFactory())
                .setPort(portNumberNoSec)
                .setCustomCoapStackArgument(OscoreCtxDbSingleton.getInstance())
                .build());

  	    rs.setMessageDeliverer(dpd);
  	    rs.start();
  	    System.out.println("Server starting");
    }

    /**
     * Stops the server
     * 
     * @throws IOException 
     * @throws AceException 
     */
    public static void stop() throws IOException, AceException {
        rs.stop();
        ai.close();
		new File(testpath + "tokens.json").delete();
    }

    /**
     * Return the role sets allowed to a subject in a group, based on all the Access Tokens for that subject
     * 
     * @param subject   Subject identity of the node
     * @param groupName   Group name of the OSCORE group
     * @return The sets of allowed roles for the subject in the specified group using the AIF data model,
     * 		   or null in case of no results
     */
    public static int[] getRolesFromToken(String subject, String groupName) {

    	Set<Integer> roleSets = new HashSet<Integer>();
    	
    	String kid = TokenRepository.getInstance().getKid(subject);
    	Set<String> ctis = TokenRepository.getInstance().getCtis(kid);
    	
    	// This should never happen at this point, since a valid Access Token
    	// has just made this request pass through 
    	if (ctis == null)
    		return null;
    	
    	for (String cti : ctis) { //All tokens linked to that pop key
    		
	        //Check if we have the claims for that cti
	        //Get the claims
            Map<Short, CBORObject> claims = TokenRepository.getInstance().getClaims(cti);
            if (claims == null || claims.isEmpty()) {
                //No claims found
        		// Move to the next Access Token for this 'kid'
                continue;
            }
            
	        //Check the scope
            CBORObject scope = claims.get(Constants.SCOPE);
            
        	// This should never happen, since a valid Access Token
            // has just reached a handler at the Group Manager
            if (scope == null) {
        		// Move to the next Access Token for this 'kid'
            	continue;
            }
            
            if (!scope.getType().equals(CBORType.ByteString)) {
        		// Move to the next Access Token for this 'kid'
            	continue;
            }
            
            byte[] rawScope = scope.GetByteString();
        	CBORObject cborScope = CBORObject.DecodeFromBytes(rawScope);
        	
        	if (!cborScope.getType().equals(CBORType.Array)) {
        		// Move to the next Access Token for this 'kid'
                continue;
            }

        	for (int entryIndex = 0; entryIndex < cborScope.size(); entryIndex++) {
            	
        		CBORObject scopeEntry = cborScope.get(entryIndex);
        		
        		if (!scopeEntry.getType().equals(CBORType.Array) || scopeEntry.size() != 2) {
        			// Move to the next Access Token for this 'kid'
                    break;
                }
	        	
	        	// Retrieve the Group ID of the OSCORE group
	        	String scopeStr;
	      	  	CBORObject scopeElement = scopeEntry.get(0);
	      	  	if (scopeElement.getType().equals(CBORType.TextString)) {
	      	  		scopeStr = scopeElement.AsString();
	      	  		if (!scopeStr.equals(groupName)) {
	      	  		    // Move to the next scope entry
	      	  			continue;
	      	  		}
	      	  	}
	      	  	else {
	    			// Move to the next Access Token for this 'kid'
	                break;
	      	  	}
	      	  	
	      	  	// Retrieve the role or list of roles
	      	  	scopeElement = scopeEntry.get(1);
	      	  	
	        	if (!scopeElement.getType().equals(CBORType.Integer)) {
      	  		    // Move to the next scope entry
      	  			continue;
	        	}
	        	
        		int roleSetToken = scopeElement.AsInt32();
        		
        		// According to the AIF-OSCORE-GROUPCOMM data model, a valid combination 
        		// of roles has to be a positive integer of even value (i.e., with last bit 0)
        		if (roleSetToken <= 0 || (roleSetToken % 2 == 1)) {
      	  		    // Move to the next scope entry
      	  			continue;
        		}

        		roleSets.add(roleSetToken);
        			        	
        	}
        	
    	}
    	    	
    	// No Access Token allows this node to have any role
    	// with respect to the specified group
    	if (roleSets.size() == 0) {
    		return null;
    	}
    	else {
    		int[] ret = new int[roleSets.size()];
    		
    		int index = 0;
    		for (Integer i : roleSets) {
    			ret[index] = i.intValue();
    			index++;
    		}
    		
    		return ret;
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
