package se.sics.prototype.support;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;

import org.eclipse.californium.cose.AlgorithmID;
import org.eclipse.californium.cose.CoseException;
import org.eclipse.californium.cose.KeyKeys;
import org.eclipse.californium.cose.OneKey;
import org.eclipse.californium.oscore.OSException;
import org.eclipse.californium.oscore.group.GroupCtx;
import org.eclipse.californium.oscore.group.MultiKey;
import org.junit.Assert;
import org.postgresql.core.Utils;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;

import se.sics.ace.Constants;
import se.sics.ace.oscore.GroupOSCOREInputMaterialObject;
import se.sics.ace.oscore.GroupOSCOREInputMaterialObjectParameters;

/**
 * Class to hold various utility methods.
 * 
 *
 */
public class Util {
	/**
     * Compute a signature, using the same algorithm and private key used in the OSCORE group to join
     * 
     * @param privKey  private key used to sign
     * @param dataToSign  content to sign

     */
    public static byte[] computeSignature(PrivateKey privKey, byte[] dataToSign, int countersignKeyCurve) {

        Signature mySignature = null;
        byte[] clientSignature = null;

        try {
            if (countersignKeyCurve == KeyKeys.EC2_P256.AsInt32())
                mySignature = Signature.getInstance("SHA256withECDSA");
            else if (countersignKeyCurve == KeyKeys.OKP_Ed25519.AsInt32())
                mySignature = Signature.getInstance("NonewithEdDSA", "EdDSA");
            else {
                // At the moment, only ECDSA (EC2_P256) and EDDSA (Ed25519) are supported
                Assert.fail("Unsupported signature algorithm");
            }

        }
        catch (NoSuchAlgorithmException e) {
            System.out.println(e.getMessage());
            Assert.fail("Unsupported signature algorithm");
        }
        catch (NoSuchProviderException e) {
            System.out.println(e.getMessage());
            Assert.fail("Unsopported security provider for signature computing");
        }

        try {
            if (mySignature != null)
                mySignature.initSign(privKey);
            else
                Assert.fail("Signature algorithm has not been initialized");
        }
        catch (InvalidKeyException e) {
            System.out.println(e.getMessage());
            Assert.fail("Invalid key excpetion - Invalid private key");
        }

        try {
            if (mySignature != null) {
                mySignature.update(dataToSign);
                clientSignature = mySignature.sign();
            }
        } catch (SignatureException e) {
            System.out.println(e.getMessage());
            Assert.fail("Failed signature computation");
        }

        return clientSignature;

    }
    
    /**
     * Parse a received Group OSCORE join response and print the information in it.
     * 
     * @param joinResponse the join response
     */
    public static void printJoinResponse(CBORObject joinResponse) {
        
        //Parse the join response generally

        System.out.println();
        System.out.println("Join response contents: ");

		System.out.print("KID: ");
		System.out.println(joinResponse.get(CBORObject.FromObject(Constants.KID)));

        System.out.print("KEY: ");
        System.out.println(joinResponse.get(CBORObject.FromObject(Constants.KEY)));

        System.out.print("PROFILE: ");
        System.out.println(joinResponse.get(CBORObject.FromObject(Constants.PROFILE)));

        System.out.print("EXP: ");
        System.out.println(joinResponse.get(CBORObject.FromObject(Constants.EXP)));

        System.out.print("PUB_KEYS: ");
        System.out.println(joinResponse.get(CBORObject.FromObject(Constants.PUB_KEYS)));

        System.out.print("NUM: ");
        System.out.println(joinResponse.get(CBORObject.FromObject(Constants.NUM)));

        //Parse the KEY parameter

        CBORObject keyMap = joinResponse.get(CBORObject.FromObject(Constants.KEY));
        
        System.out.println();
        System.out.println("KEY map contents: ");

        System.out.print("ms: ");
		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ms)));

//      System.out.print("clientId: ");
//		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.id)));

        System.out.print("hkdf: ");
		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.hkdf)));

        System.out.print("alg: ");
		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.alg)));

        System.out.print("salt: ");
		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.salt)));

        System.out.print("contextId: ");
		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.contextId)));

		// System.out.print("rpl: "); //FIXME
		// System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.rpl)));


		System.out.print("ecdh_alg: ");
		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ecdh_alg)));

		System.out.print("ecdh_params: ");
		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ecdh_params)));

		System.out.print("group_SenderID: ");
		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.group_SenderID)));

		System.out.print("pub_key_enc: ");
		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.pub_key_enc)));

        //Parse the PUB_KEYS parameter

        System.out.println();
        System.out.println("PUB_KEYS contents: ");

        if(joinResponse.ContainsKey(CBORObject.FromObject(Constants.PUB_KEYS))) {
	        CBORObject coseKeySetArray = joinResponse.get(CBORObject.FromObject(Constants.PUB_KEYS));
	
	        for(int i = 0 ; i < coseKeySetArray.size() ; i++) {
	
	            CBORObject key_param = coseKeySetArray.get(i);
	
	            System.out.println("Key " + i + ": " + key_param.toString());
	        }
        }
    }
    
    /**
     * Generate a Group OSCORE Security context from material
     * received in a Join response.
     * 
     * @param joinResponse holds the information in the Join response
     * 
     * @throws CoseException on context generation failure
     */
	public static GroupCtx generateGroupOSCOREContext(CBORObject joinResponse, MultiKey clientKey) throws CoseException {
		
		int replayWindow = 32;
        byte[] gmPubKey = joinResponse.get(CBORObject.FromObject(Constants.KDC_CRED)).GetByteString();
        
        //Parse the KEY parameter

        CBORObject keyMap = joinResponse.get(CBORObject.FromObject(Constants.KEY));
        
        byte[] ms = keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ms)).GetByteString();
        byte[] salt = keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.salt)).GetByteString();
        byte[] sid = keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.group_SenderID)).GetByteString();
        byte[] idContext = keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.contextId)).GetByteString();
        
        AlgorithmID alg = AlgorithmID.AES_CCM_16_64_128;
    	AlgorithmID kdf = AlgorithmID.HKDF_HMAC_SHA_256;
    	AlgorithmID algCountersign = AlgorithmID.EDDSA;
	
        GroupCtx commonCtx = new GroupCtx(ms, salt, alg, kdf, idContext, algCountersign, gmPubKey);
  
		
        try {
        	System.out.println("Adding Sender CTX for: " + Utils.toHexString(sid) + " " + clientKey.getCoseKey().AsCBOR().toString());
        	commonCtx.addSenderCtxCcs(sid, clientKey);
		} catch (OSException e) {
			System.err.println("Error: Failed to build Sender CTX for client.");
			e.printStackTrace();
		}

        //Parse public keys and add recipient contexts
        if(joinResponse.ContainsKey(CBORObject.FromObject(Constants.PUB_KEYS))) {
	        CBORObject coseKeySetArray = joinResponse.get(CBORObject.FromObject(Constants.PUB_KEYS));
	
	        for(int i = 0 ; i < coseKeySetArray.size() ; i++) {
	
	            CBORObject key_param = coseKeySetArray.get(i);
	
//	            System.out.println("Key " + i + ": " + key_param.toString());
//	            
	            CBORObject parsedKey = CBORObject.DecodeFromBytes(key_param.GetByteString());
//	            
//	            System.out.println("Parsed key: " + parsedKey.ToJSONString());
//	            
//	            CBORObject furtherParsedKey = parsedKey.get(8);
//	            
//	            System.out.println("Further parsed key: " + furtherParsedKey.ToJSONString());
//
//	            CBORObject finalParsedKey = parsedKey.get(1);
//
//	            System.out.println("Final parsed key: " + finalParsedKey.ToJSONString());

	            
	            // Now build COSE OneKey from the key data
	            // EDDSA (Ed25519)
	            // byte[] recipientX = finalParsedKey.get(-2).GetByteString();
	            // OneKey recipientKey = buildCurve25519OneKey(null, recipientX);

	            
	            byte[] recipientId = joinResponse.get(CBORObject.FromObject(Constants.PEER_IDENTIFIERS)).get(i).GetByteString();
	            MultiKey recipientKey = new MultiKey(key_param.GetByteString());
	            try {
	            	System.out.println("Adding Recipient CTX for: " + Utils.toHexString(recipientId) + " " + parsedKey.toString());
	            	commonCtx.addRecipientCtxCcs(recipientId, replayWindow, recipientKey);
				} catch (OSException e) {
					System.err.println("Error: Failed to add Recipient CTX");
					e.printStackTrace();
				}
	        }
        }

        
        
        
        return commonCtx;
        
//        // ctx.addRecipientCtxCcs(recipientId, replayWindow, otherEndpointPubKey);
        
        
//        
//        System.out.print("hkdf: ");
//		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.hkdf)));
//
//        System.out.print("alg: ");
//		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.alg)));
//
//
//		System.out.print("ecdh_alg: ");
//		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ecdh_alg)));
//
//		System.out.print("ecdh_params: ");
//		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.ecdh_params)));
//
//		System.out.print("group_SenderID: ");
//		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.group_SenderID)));
//
//		System.out.print("pub_key_enc: ");
//		System.out.println(keyMap.get(CBORObject.FromObject(GroupOSCOREInputMaterialObjectParameters.pub_key_enc)));

		
	}


	/**
	 * Build a COSE OneKey from raw byte arrays containing the public and
	 * private keys. Considers EdDSA with the curve Curve25519. Will not fill
	 * the Java private and public key in the COSE key. So can only be used for
	 * shared secret calculation.
	 * 
	 * @param privateKey the private key bytes (private scalar here)
	 * @param publicKey the public key bytes
	 * FIXME: Remove?
	 * @return a OneKey representing the input material
	 */
	static OneKey buildCurve25519OneKey(byte[] privateKey, byte[] publicKey) {
		byte[] rgbX = publicKey;
		byte[] rgbD = privateKey;

		OneKey key = new OneKey();
		
		key.add(KeyKeys.KeyType.AsCBOR(), KeyKeys.KeyType_OKP);
		key.add(KeyKeys.OKP_Curve.AsCBOR(), KeyKeys.OKP_X25519);
		key.add(KeyKeys.OKP_X.AsCBOR(), CBORObject.FromObject(rgbX));
		
		if (privateKey != null)
			key.add(KeyKeys.OKP_D.AsCBOR(), CBORObject.FromObject(rgbD));

		return key;
	}
	
    /**
     * Generate a Group OSCORE Security context from material
     * received in a Join response.
     * 
     * @deprecated
     * @param contextObject holds the information in the Join response
     * @param CBORObject coseKeySetArray holds information about public keys from the Join response
     * @param groupKeyPair the public and private COSE key of the client (in base64 encoding) 
     * 
     * @throws CoseException 
     */
	public static GroupCtx generateGroupOSCOREContextOld(GroupOSCOREInputMaterialObject contextObject,
			CBORObject coseKeySetArray, String groupKeyPair) throws CoseException {
        //Defining variables to hold the information before derivation

        //Algorithm
        AlgorithmID algo = null;
		CBORObject alg_param = contextObject.getParam(GroupOSCOREInputMaterialObjectParameters.alg);
        if(alg_param.getType() == CBORType.TextString) {
            algo = AlgorithmID.valueOf(alg_param.AsString());
        } else if(alg_param.getType() == CBORType.Number) {
            algo = AlgorithmID.FromCBOR(alg_param);
        }

        //KDF
        AlgorithmID kdf = null;
		CBORObject kdf_param = contextObject.getParam(GroupOSCOREInputMaterialObjectParameters.hkdf);
        if(kdf_param.getType() == CBORType.TextString) {
            kdf = AlgorithmID.valueOf(kdf_param.AsString());
        } else if(kdf_param.getType() == CBORType.Number) {
            kdf = AlgorithmID.FromCBOR(kdf_param);
        }

		// Algorithm for the countersignature
		AlgorithmID alg_countersign = null;
		CBORObject alg_countersign_param = contextObject.getParam(GroupOSCOREInputMaterialObjectParameters.sign_alg);
		if (alg_countersign_param.getType() == CBORType.TextString) {
			alg_countersign = AlgorithmID.valueOf(alg_countersign_param.AsString());
		} else if (alg_countersign_param.getType() == CBORType.Number) {
			alg_countersign = AlgorithmID.FromCBOR(alg_countersign_param);
		}
		//
		// //Parameter for the countersignature
		// Integer par_countersign = null;
		// CBORObject par_countersign_param =
		// contextObject.getParam(GroupOSCOREInputMaterialObjectParameters.cs_params);
		// if(par_countersign_param.getType() == CBORType.Map) {
		// par_countersign =
		// par_countersign_param.get(KeyKeys.OKP_Curve.AsCBOR()).AsInt32();
		// //TODO: Change like this in other places too?
		// } else {
		// System.err.println("Unknown par_countersign value!");
		// }

        //Master secret
		CBORObject master_secret_param = contextObject.getParam(GroupOSCOREInputMaterialObjectParameters.ms);
        byte[] master_secret = null;
        if(master_secret_param.getType() == CBORType.ByteString) {
            master_secret = master_secret_param.GetByteString();
        }

        //Master salt
		CBORObject master_salt_param = contextObject.getParam(GroupOSCOREInputMaterialObjectParameters.salt);
        byte[] master_salt = null;
        if(master_salt_param.getType() == CBORType.ByteString) {
            master_salt = master_salt_param.GetByteString();
        }

        //Sender ID
        byte[] sid = null;
		CBORObject sid_param = contextObject.getParam(GroupOSCOREInputMaterialObjectParameters.id);
        if(sid_param.getType() == CBORType.ByteString) {
            sid = sid_param.GetByteString();
        }

        //Group ID / Context ID
		CBORObject group_identifier_param = contextObject.getParam(GroupOSCOREInputMaterialObjectParameters.contextId);
        byte[] group_identifier = null;
        if(group_identifier_param.getType() == CBORType.ByteString) {
            group_identifier = group_identifier_param.GetByteString();
        }

		// RPL (replay window information) //FIXME
		// CBORObject rpl_param =
		// contextObject.getParam(GroupOSCOREInputMaterialObjectParameters.rpl);
        int rpl = 32; //Default value
		// if(rpl_param != null && rpl_param.getType() == CBORType.Number) {
		// rpl = rpl_param.AsInt32();
		// }

        //Set up private & public keys for sender (not from response but set by client)
        String sid_private_key_string = groupKeyPair;
        OneKey sid_private_key;
        sid_private_key = new OneKey(CBORObject.DecodeFromBytes(Base64.getDecoder().decode(sid_private_key_string)));

        //Now derive the actual context

		/*
		 * public GroupCtx(byte[] masterSecret, byte[] masterSalt, AlgorithmID
		 * aeadAlg, AlgorithmID hkdfAlg, byte[] idContext, AlgorithmID algSign,
		 * byte[] gmPublicKey) {
		 */

		GroupCtx groupOscoreCtx = null;
		groupOscoreCtx = new GroupCtx(master_secret, master_salt, algo, kdf, group_identifier, alg_countersign,
				new byte[0]); // FIXME GM Key

		try {
			groupOscoreCtx.addSenderCtx(sid, sid_private_key);
		} catch (OSException e1) {
			// TODO Auto-generated catch block
			System.err.println("FAILED TO ADD SENDER CTX!");
			e1.printStackTrace();
		}

        Assert.assertNotNull(groupOscoreCtx);

        //Finally add the recipient contexts from the coseKeySetArray
        for(int i = 0 ; i < coseKeySetArray.size() ; i++) {

            CBORObject key_param = coseKeySetArray.get(i);

            byte[] rid = null;
            CBORObject rid_param = key_param.get(KeyKeys.KeyId.AsCBOR());
            if(rid_param.getType() == CBORType.ByteString) {
                rid = rid_param.GetByteString();
            }

            OneKey recipient_key = new OneKey(key_param);

			try {
				groupOscoreCtx.addRecipientCtx(rid, rpl, recipient_key);
			} catch (OSException e) {
				// TODO Auto-generated catch block
				System.err.println("FAILED TO ADD RECIPIENT CTX!");
				e.printStackTrace();
			}
        }
        //Assert.assertEquals(groupOscoreCtx.getRecipientContexts().size(), 2);
        //System.out.println("Generated Group OSCORE Context:");
        //Utility.printContextInfo(groupOscoreCtx);

        return groupOscoreCtx;
    }
	

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
